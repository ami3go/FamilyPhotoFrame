package com.example.familyphotoframe.data.diagnostics

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.Instant
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded schema-v2 diagnostic pipeline.
 *
 * Callers only create an entry and attempt one bounded queue offer. File access remains
 * on the daemon writer. Category, severity, stream and permitted fields come from
 * [DiagnosticEventCatalog], so every in-memory and durable surface sees the same record.
 */
class DiagnosticsLog(
    private val capacity: Int = 1000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    writerQueueCapacity: Int = DEFAULT_WRITER_QUEUE_CAPACITY,
    private val durableAppend: (FileDiagnosticsSink, String) -> Unit = { target, line ->
        target.append(line)
    },
) {

    enum class Category { APP, ENGINE, SOURCE, SCAN, CACHE, DECODE, MEMORY, LIFECYCLE }

    data class Entry(
        val atEpochMs: Long,
        val category: Category,
        val code: String,
        /** Deprecated for variable data. Kept for old in-process callers only. */
        val message: String = "",
        val fields: Map<String, String> = emptyMap(),
        val sessionId: String = "",
        val schemaVersion: Int = SCHEMA_VERSION,
        val sequence: Long = 0L,
        val elapsedRealtimeMs: Long = 0L,
        val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        val origin: DiagnosticOrigin = DiagnosticOrigin.INTERNAL,
        val operationId: String? = null,
        val parentOperationId: String? = null,
    ) {
        companion object { const val SCHEMA_VERSION = 2 }
    }

    private val buffer = ArrayDeque<Entry>(capacity.coerceAtLeast(1))
    private val lock = Any()
    private val droppedDurableWrites = AtomicLong(0L)
    private val totalDroppedDurableWrites = AtomicLong(0L)
    private val droppedSinceHealthReport = AtomicLong(0L)
    private val fieldsDropped = AtomicLong(0L)
    private val sequence = AtomicLong(0L)
    private val lastElapsed = AtomicLong(0L)
    private val rateController = DiagnosticRateController(nowMs = nowMs)

    private data class StreamTracker(
        var oldestSessionId: String = "",
        var oldestSequence: Long = 0L,
        var newestSessionId: String = "",
        var newestSequence: Long = 0L,
        var lastErrorClass: String = "",
        var lastWriteEpochMs: Long = 0L,
    )

    private val healthLock = Any()
    private var standardTracker = StreamTracker()
    private var bulkTracker = StreamTracker()
    @Volatile private var lastSuccessfulFlushEpochMs: Long = 0L
    @Volatile private var lastFlushTimeoutMs: Long = 0L
    @Volatile private var crashEnvelopePresent: Boolean = false
    @Volatile private var crashEnvelopeBytes: Int = 0

    /** Shared bounded registry; crash capture reads its immutable snapshot. */
    val operations = DiagnosticOperationTracker(elapsedRealtimeMs = elapsedRealtimeMs)

    @Volatile private var sink: FileDiagnosticsSink? = null
    @Volatile private var bulkSink: FileDiagnosticsSink? = null

    private val writer = AsyncDurableWriter(
        writerQueueCapacity,
        durableAppend,
        nowMs,
        ::onWriterResult,
    )

    @Volatile var sessionId: String = ""
        private set

    fun attachSink(sink: FileDiagnosticsSink, bulkSink: FileDiagnosticsSink, sessionId: String) {
        this.sink = sink
        this.bulkSink = bulkSink
        this.sessionId = sessionId.take(40)
        sequence.set(0L)
        lastElapsed.set(0L)
        synchronized(healthLock) {
            standardTracker = StreamTracker()
            bulkTracker = StreamTracker()
        }
        operations.attachSession(this.sessionId)
    }

    fun detachSink() {
        flushDurable()
        sink = null
        bulkSink = null
    }

    fun log(category: Category, code: String, message: String = "") =
        log(category, code, message, emptyMap())

    fun log(category: Category, code: String, message: String, fields: Map<String, String>) {
        record(category, code, message, fields, DiagnosticContext())
    }

    /** Convenience for the common structured-fields case. */
    fun log(category: Category, code: String, vararg fields: Pair<String, String>) =
        log(category, code, "", fields.toMap())

    /** Preferred schema-v2 API for correlated and explicitly originated events. */
    fun logEvent(
        code: String,
        fields: Map<String, String> = emptyMap(),
        context: DiagnosticContext = DiagnosticContext(),
        fixedMessage: String = "",
    ): Entry = record(null, code, fixedMessage, fields, context)

    private fun record(
        callerCategory: Category?,
        requestedCode: String,
        message: String,
        fields: Map<String, String>,
        context: DiagnosticContext,
    ): Entry {
        emitQueueOverflowIfNeeded()
        val requestedSpec = DiagnosticEventCatalog.find(requestedCode)
        val spec = requestedSpec ?: DiagnosticEventCatalog.require("DIAGNOSTICS_UNKNOWN_EVENT")
        val candidateFields = if (requestedSpec == null) {
            mapOf("unknownCode" to requestedCode.filter { it.isLetterOrDigit() || it == '_' }.take(64))
        } else {
            fields
        }
        val initial = DiagnosticsJsonl.sanitizeForEvent(spec, candidateFields, message)
        fieldsDropped.addAndGet(initial.dropped.toLong())
        val decision = rateController.evaluate(spec, initial.fields, context)
        val effectiveSpec = if (decision.code == spec.code) spec
            else DiagnosticEventCatalog.require(decision.code)
        val sanitized = if (effectiveSpec === spec) {
            initial.copy(fields = decision.fields)
        } else {
            DiagnosticsJsonl.sanitizeForEvent(effectiveSpec, decision.fields, "").also {
                fieldsDropped.addAndGet(it.dropped.toLong())
            }
        }
        if (!decision.emit) {
            return Entry(
                atEpochMs = nowMs(),
                category = effectiveSpec.category,
                code = effectiveSpec.code,
                fields = sanitized.fields,
                sessionId = sessionId,
                sequence = 0L,
                elapsedRealtimeMs = lastElapsed.get(),
                severity = effectiveSpec.severity,
                origin = context.origin,
                operationId = context.operationId,
                parentOperationId = context.parentOperationId,
            )
        }
        val entry = createEntry(
            spec = effectiveSpec,
            message = sanitized.message,
            fields = sanitized.fields,
            context = context,
        )

        // A category mismatch is counted as a dropped contract field rather than stored
        // as variable text. Static release verification identifies the exact call site.
        if (callerCategory != null && callerCategory != effectiveSpec.category) fieldsDropped.incrementAndGet()

        addToBuffer(entry)
        if (!enqueue(entry, effectiveSpec.stream)) {
            droppedDurableWrites.incrementAndGet()
            totalDroppedDurableWrites.incrementAndGet()
            droppedSinceHealthReport.incrementAndGet()
        }
        return entry
    }

    private fun createEntry(
        spec: DiagnosticEventSpec,
        message: String,
        fields: Map<String, String>,
        context: DiagnosticContext,
    ): Entry = Entry(
        atEpochMs = nowMs(),
        category = spec.category,
        code = spec.code,
        message = message,
        fields = fields,
        sessionId = sessionId,
        sequence = sequence.incrementAndGet(),
        elapsedRealtimeMs = monotonicElapsed(),
        severity = spec.severity,
        origin = context.origin,
        operationId = context.operationId,
        parentOperationId = context.parentOperationId,
    )

    private fun monotonicElapsed(): Long {
        while (true) {
            val previous = lastElapsed.get()
            val candidate = maxOf(previous, elapsedRealtimeMs())
            if (lastElapsed.compareAndSet(previous, candidate)) return candidate
        }
    }

    private fun addToBuffer(entry: Entry) {
        synchronized(lock) {
            if (buffer.size >= capacity.coerceAtLeast(1)) buffer.pollFirst()
            buffer.addLast(entry)
        }
    }

    private fun enqueue(entry: Entry, stream: DiagnosticStream): Boolean {
        val target = when (stream) {
            DiagnosticStream.STANDARD -> sink
            DiagnosticStream.BULK -> bulkSink
        } ?: return true
        return writer.enqueue(
            target,
            DiagnosticsJsonl.encode(entry),
            DurableWriteMetadata(stream, entry.sessionId, entry.sequence),
        )
    }

    private fun onWriterResult(result: DurableWriteResult) {
        var healthCode: String? = null
        synchronized(healthLock) {
            val tracker = if (result.metadata.stream == DiagnosticStream.STANDARD) {
                standardTracker
            } else {
                bulkTracker
            }
            val previousError = tracker.lastErrorClass
            if (result.errorClass == null) {
                tracker.lastWriteEpochMs = result.atEpochMs
                if (tracker.oldestSequence == 0L) {
                    tracker.oldestSessionId = result.metadata.sessionId
                    tracker.oldestSequence = result.metadata.sequence
                }
                tracker.newestSessionId = result.metadata.sessionId
                tracker.newestSequence = result.metadata.sequence
                tracker.lastErrorClass = ""
                if (previousError.isNotEmpty()) healthCode = "DIAGNOSTICS_SINK_RECOVERED"
            } else {
                tracker.lastErrorClass = result.errorClass
                if (previousError != result.errorClass) healthCode = "DIAGNOSTICS_SINK_FAILED"
            }
        }
        healthCode?.let { code ->
            addHealthMarker(
                code,
                mapOf(
                    "stream" to result.metadata.stream.name,
                    "errorClass" to result.errorClass.orEmpty(),
                    "sinkStatus" to (result.errorClass ?: "OK"),
                ),
            )
        }
    }

    /** Add health evidence in memory only; never recurse through a failing sink. */
    private fun addHealthMarker(code: String, fields: Map<String, String>) {
        val spec = DiagnosticEventCatalog.require(code)
        val sanitized = DiagnosticsJsonl.sanitizeForEvent(spec, fields, "")
        fieldsDropped.addAndGet(sanitized.dropped.toLong())
        addToBuffer(
            createEntry(
                spec,
                "",
                sanitized.fields,
                DiagnosticContext(origin = DiagnosticOrigin.INTERNAL),
            ),
        )
    }

    private fun emitQueueOverflowIfNeeded() {
        val dropped = droppedDurableWrites.getAndSet(0L)
        if (dropped <= 0L) return
        val spec = DiagnosticEventCatalog.require("DIAGNOSTICS_QUEUE_OVERFLOW")
        val marker = createEntry(
            spec = spec,
            message = "",
            fields = mapOf("dropped" to dropped.toString(), "droppedTotal" to totalDroppedDurableWrites.get().toString()),
            context = DiagnosticContext(origin = DiagnosticOrigin.INTERNAL),
        )
        addToBuffer(marker)
        if (!enqueue(marker, spec.stream)) {
            droppedDurableWrites.addAndGet(dropped)
            totalDroppedDurableWrites.incrementAndGet()
            droppedSinceHealthReport.incrementAndGet()
        }
    }

    /**
     * Stream a valid JSONL support bundle without materializing the approximately 18 MiB
     * retention budget. Consumers merge event records by (sessionId, sequence); bounded
     * metadata and explicit stream boundaries describe gaps and writer health.
     */
    fun openDurableBundle(context: DiagnosticsBundleContext = DiagnosticsBundleContext()): InputStream {
        emitQueueOverflowIfNeeded()
        val flushSucceeded = flushDurable()
        val health = healthSnapshot()
        val durableStreamsAttached = sink != null || bulkSink != null
        val incomplete = !flushSucceeded || !durableStreamsAttached || health.droppedTotal > 0L ||
            health.crashEnvelopePresent || health.standard.lastAppendErrorClass.isNotEmpty() ||
            health.bulk.lastAppendErrorClass.isNotEmpty() || health.standard.rotations > 0L ||
            health.bulk.rotations > 0L || health.standard.retainedGenerations > 1 ||
            health.bulk.retainedGenerations > 1
        val streams = arrayListOf<InputStream>(
            ByteArrayInputStream(
                DiagnosticsBundleJson.prelude(
                    context = context,
                    health = health,
                    generatedAtEpochMs = nowMs(),
                    sessionId = sessionId,
                    flushSucceeded = flushSucceeded,
                    durableStreamsAttached = durableStreamsAttached,
                ),
            ),
        )
        sink?.let { streams += it.openRetainedStream() }
        streams += ByteArrayInputStream(
            (DiagnosticsBundleJson.boundary("standard", "end") +
                DiagnosticsBundleJson.boundary("bulk", "start")).toByteArray(Charsets.UTF_8),
        )
        bulkSink?.let { streams += it.openRetainedStream() }
        if (!durableStreamsAttached) {
            val memoryJsonl = snapshot(capacity).joinToString(separator = "\n", postfix = "\n") {
                DiagnosticsJsonl.encode(it)
            }
            streams += ByteArrayInputStream(memoryJsonl.toByteArray(Charsets.UTF_8))
        }
        streams += ByteArrayInputStream(
            (DiagnosticsBundleJson.boundary("bulk", "end") +
                DiagnosticsBundleJson.end(incomplete)).toByteArray(Charsets.UTF_8),
        )
        return SequenceInputStream(Collections.enumeration(streams))
    }

    fun flushDurable(timeoutMs: Long = 5_000L): Boolean {
        val success = writer.flush(timeoutMs)
        if (success) {
            lastSuccessfulFlushEpochMs = nowMs()
            lastFlushTimeoutMs = 0L
        } else {
            lastFlushTimeoutMs = timeoutMs.coerceAtLeast(1L)
            addHealthMarker(
                "DIAGNOSTICS_FLUSH_TIMEOUT",
                mapOf("flushTimeoutMs" to lastFlushTimeoutMs.toString()),
            )
        }
        return success
    }

    fun durableBytes(): Long = (sink?.totalBytes() ?: 0L) + (bulkSink?.totalBytes() ?: 0L)

    fun snapshot(last: Int = capacity): List<Entry> = synchronized(lock) {
        val all = buffer.toList()
        if (all.size <= last) all else all.subList(all.size - last, all.size)
    }

    fun totalDroppedEvents(): Long = totalDroppedDurableWrites.get()
    fun totalDroppedFields(): Long = fieldsDropped.get()
    fun lastSequence(): Long = sequence.get()
    fun writerQueueDepth(): Int = writer.queueDepth()
    fun writerQueueCapacity(): Int = writer.queueCapacity()
    fun rateStateSize(): Int = rateController.stateSize()

    fun updateCrashEnvelopeHealth(present: Boolean, sizeBytes: Int) {
        crashEnvelopePresent = present
        crashEnvelopeBytes = sizeBytes.coerceAtLeast(0)
    }

    fun healthSnapshot(resetDropDelta: Boolean = false): DiagnosticsHealthSnapshot {
        val standardSink = sink?.snapshot()
        val bulkSinkSnapshot = bulkSink?.snapshot()
        val trackers = synchronized(healthLock) { standardTracker.copy() to bulkTracker.copy() }
        val dropDelta = if (resetDropDelta) droppedSinceHealthReport.getAndSet(0L)
            else droppedSinceHealthReport.get()
        fun stream(
            sinkSnapshot: FileDiagnosticsSink.Snapshot?,
            tracker: StreamTracker,
        ) = DiagnosticsHealthSnapshot.Stream(
            retainedBytes = sinkSnapshot?.retainedBytes ?: 0L,
            retainedGenerations = sinkSnapshot?.retainedGenerations ?: 0,
            rotations = sinkSnapshot?.rotations ?: 0L,
            oldestKnownSessionId = tracker.oldestSessionId,
            oldestKnownSequence = tracker.oldestSequence,
            newestKnownSessionId = tracker.newestSessionId,
            newestKnownSequence = tracker.newestSequence,
            lastSuccessfulWriteEpochMs = maxOf(
                tracker.lastWriteEpochMs,
                sinkSnapshot?.lastSuccessfulWriteEpochMs ?: 0L,
            ),
            lastAppendErrorClass = tracker.lastErrorClass.ifEmpty {
                sinkSnapshot?.lastAppendErrorClass.orEmpty()
            },
        )
        val standard = stream(standardSink, trackers.first)
        val bulk = stream(bulkSinkSnapshot, trackers.second)
        return DiagnosticsHealthSnapshot(
            queueCapacity = writerQueueCapacity(),
            queueDepth = writerQueueDepth(),
            droppedTotal = totalDroppedEvents(),
            droppedSinceLastReport = dropDelta,
            fieldsDropped = totalDroppedFields(),
            standard = standard,
            bulk = bulk,
            lastSuccessfulWriteEpochMs = maxOf(
                standard.lastSuccessfulWriteEpochMs,
                bulk.lastSuccessfulWriteEpochMs,
            ),
            lastSuccessfulFlushEpochMs = lastSuccessfulFlushEpochMs,
            lastFlushTimeoutMs = lastFlushTimeoutMs,
            crashEnvelopePresent = crashEnvelopePresent,
            crashEnvelopeBytes = crashEnvelopeBytes,
            rateStateEntries = rateStateSize(),
        )
    }

    fun renderRedacted(last: Int = 100): String = buildString {
        val health = healthSnapshot()
        appendLine("# Family Photo Frame diagnostics (redacted schema v2)")
        appendLine("# generated: ${Instant.now()}")
        appendLine("# session: $sessionId")
        appendLine(
            "# writer: queue ${health.queueDepth}/${health.queueCapacity}; " +
                "dropped events ${health.droppedTotal}; dropped fields ${health.fieldsDropped}",
        )
        appendLine(
            "# retention: standard ${health.standard.retainedBytes} B/${health.standard.retainedGenerations} files; " +
                "bulk ${health.bulk.retainedBytes} B/${health.bulk.retainedGenerations} files; " +
                "rotations ${health.standard.rotations + health.bulk.rotations}",
        )
        appendLine(
            "# sink errors: standard ${health.standard.lastAppendErrorClass.ifEmpty { "none" }}; " +
                "bulk ${health.bulk.lastAppendErrorClass.ifEmpty { "none" }}; " +
                "last flush ${health.lastSuccessfulFlushEpochMs}",
        )
        appendLine()
        snapshot(last).forEach { e ->
            appendLine(
                "${Instant.ofEpochMilli(e.atEpochMs)} #${e.sequence} [${e.severity}/${e.category}] " +
                    "${e.code} origin=${e.origin} session=${e.sessionId} elapsed=${e.elapsedRealtimeMs} " +
                    "op=${e.operationId ?: "-"} parent=${e.parentOperationId ?: "-"} ${e.fields}",
            )
        }
    }

    fun clear() = synchronized(lock) { buffer.clear() }

    fun clearDurable() {
        flushDurable()
        sink?.clear()
        bulkSink?.clear()
        droppedDurableWrites.set(0L)
        totalDroppedDurableWrites.set(0L)
        droppedSinceHealthReport.set(0L)
        fieldsDropped.set(0L)
        rateController.clear()
        clear()
    }

    private companion object {
        const val DEFAULT_WRITER_QUEUE_CAPACITY = 1024
    }
}

/** Single daemon writer with a bounded queue and explicit flush barriers. */
private class AsyncDurableWriter(
    queueCapacity: Int,
    private val append: (FileDiagnosticsSink, String) -> Unit,
    private val nowMs: () -> Long,
    private val onResult: (DurableWriteResult) -> Unit,
) {
    private sealed interface Command {
        data class Write(
            val sink: FileDiagnosticsSink,
            val line: String,
            val metadata: DurableWriteMetadata,
        ) : Command
        data class Barrier(val latch: CountDownLatch) : Command
    }

    private val queue = ArrayBlockingQueue<Command>(queueCapacity.coerceAtLeast(1))

    init {
        Thread({
            while (true) {
                when (val command = queue.take()) {
                    is Command.Write -> {
                        val thrown = runCatching { append(command.sink, command.line) }
                            .exceptionOrNull()?.javaClass?.simpleName
                        onResult(
                            DurableWriteResult(
                                command.metadata,
                                thrown ?: command.sink.lastError,
                                nowMs(),
                            ),
                        )
                    }
                    is Command.Barrier -> command.latch.countDown()
                }
            }
        }, "fpf-diagnostics-writer").apply {
            isDaemon = true
            start()
        }
    }

    fun enqueue(
        sink: FileDiagnosticsSink,
        line: String,
        metadata: DurableWriteMetadata,
    ): Boolean = queue.offer(Command.Write(sink, line, metadata))

    fun queueDepth(): Int = queue.size
    fun queueCapacity(): Int = queue.size + queue.remainingCapacity()

    fun flush(timeoutMs: Long = 5_000L): Boolean {
        val latch = CountDownLatch(1)
        return try {
            if (!queue.offer(Command.Barrier(latch), timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)) {
                false
            } else {
                latch.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}

private data class DurableWriteMetadata(
    val stream: DiagnosticStream,
    val sessionId: String,
    val sequence: Long,
)

private data class DurableWriteResult(
    val metadata: DurableWriteMetadata,
    val errorClass: String?,
    val atEpochMs: Long,
)

/**
 * Compatibility entry point backed by the installation-specific HMAC identity service.
 */
fun diagnosticToken(value: String, type: String = "id"): String =
    DiagnosticIdentityHasher.current().token(type, value)
