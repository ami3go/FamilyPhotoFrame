package com.example.familyphotoframe.data.diagnostics

import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.IdentityHashMap
import java.util.zip.CRC32

/** Fixed, privacy-safe evidence persisted synchronously when async JSONL may be lost. */
data class CrashEnvelope(
    val kind: Kind,
    val atEpochMs: Long,
    val elapsedRealtimeMs: Long,
    val previousSessionId: String,
    val lastSequence: Long,
    val appVersion: String,
    val versionCode: Long,
    val sdkInt: Int,
    val exceptionClass: String,
    val rootCauseClass: String,
    val threadName: String,
    val mainThread: Boolean,
    val frames: List<Frame>,
    val heapUsedKb: Long,
    val heapMaxKb: Long,
    val nativeHeapKb: Long,
    val pssKb: Long,
    val imageCacheKb: Long,
    val surface: String,
    val engineState: String,
    val presentationToken: String,
    val sourceKind: String,
    val layout: String,
    val transitionCode: String,
    val activeOperationId: String,
    val activeOperationStage: String,
    val queueDepth: Int,
    val droppedCount: Long,
    val sinkErrorClass: String,
    val stallDurationMs: Long = 0L,
    val preparedSlideCount: Int = 0,
    val renderedSlideCount: Int = 0,
    val decodedBitmapCount: Int = 0,
    val appBitmapCount: Int = 0,
    val activeDecodedBytes: Long = 0L,
    val pendingDisposals: Int = 0,
    val memoryProtectionLevel: String = "NORMAL",
    val oomCount: Long = 0L,
) {
    enum class Kind { CRASH, ANR }

    data class Frame(
        val className: String,
        val methodName: String,
        val sourceFile: String,
        val lineNumber: Int,
    )
}

data class CrashCaptureContext(
    val atEpochMs: Long,
    val elapsedRealtimeMs: Long,
    val sessionId: String,
    val lastSequence: Long,
    val appVersion: String,
    val versionCode: Long,
    val sdkInt: Int,
    val runtime: DiagnosticRuntimeState.Snapshot,
    val activeOperation: DiagnosticOperationTracker.Operation?,
    val queueDepth: Int,
    val droppedCount: Long,
    val sinkErrorClass: String,
)

/** Bounded Throwable-to-envelope conversion shared by the handler and JVM tests. */
object CrashEnvelopeFactory {
    fun crash(
        throwable: Throwable,
        thread: Thread,
        mainThread: Boolean,
        context: CrashCaptureContext,
    ): CrashEnvelope {
        val chain = boundedCauseChain(throwable)
        val allFrames = chain.flatMap { it.stackTrace.asList() }
        val preferred = allFrames.filter { it.className.startsWith(APP_PACKAGE) }
            .ifEmpty { allFrames }
            .take(MAX_FRAMES)
            .map(::frame)
        return base(
            kind = CrashEnvelope.Kind.CRASH,
            context = context,
            exceptionClass = throwable.javaClass.simpleName,
            rootCauseClass = chain.lastOrNull()?.javaClass?.simpleName.orEmpty(),
            threadName = thread.name,
            mainThread = mainThread,
            frames = preferred,
            stallDurationMs = 0L,
        )
    }

    fun anr(
        mainThread: Thread,
        stallDurationMs: Long,
        context: CrashCaptureContext,
    ): CrashEnvelope = base(
        kind = CrashEnvelope.Kind.ANR,
        context = context,
        exceptionClass = "",
        rootCauseClass = "",
        threadName = mainThread.name,
        mainThread = true,
        frames = mainThread.stackTrace.take(MAX_FRAMES).map(::frame),
        stallDurationMs = stallDurationMs.coerceAtLeast(0L),
    )

    private fun base(
        kind: CrashEnvelope.Kind,
        context: CrashCaptureContext,
        exceptionClass: String,
        rootCauseClass: String,
        threadName: String,
        mainThread: Boolean,
        frames: List<CrashEnvelope.Frame>,
        stallDurationMs: Long,
    ): CrashEnvelope {
        val memory = context.runtime.memory
        val playback = context.runtime.playback
        val bitmaps = context.runtime.bitmaps
        return CrashEnvelope(
            kind = kind,
            atEpochMs = context.atEpochMs,
            elapsedRealtimeMs = context.elapsedRealtimeMs,
            previousSessionId = context.sessionId,
            lastSequence = context.lastSequence,
            appVersion = context.appVersion,
            versionCode = context.versionCode,
            sdkInt = context.sdkInt,
            exceptionClass = DiagnosticPrivacyPolicy.protect("exceptionClass", exceptionClass).value,
            rootCauseClass = DiagnosticPrivacyPolicy.protect("rootCauseClass", rootCauseClass).value,
            threadName = DiagnosticPrivacyPolicy.protect("threadName", threadName).value,
            mainThread = mainThread,
            frames = frames,
            heapUsedKb = memory.heapUsedKb,
            heapMaxKb = memory.heapMaxKb,
            nativeHeapKb = memory.nativeHeapKb,
            pssKb = memory.pssKb,
            imageCacheKb = memory.imageCacheKb,
            surface = playback.surface,
            engineState = playback.engineState,
            presentationToken = playback.presentationToken,
            sourceKind = playback.sourceKind,
            layout = playback.layout,
            transitionCode = playback.transitionCode,
            activeOperationId = context.activeOperation?.id.orEmpty(),
            activeOperationStage = context.activeOperation?.stage.orEmpty(),
            queueDepth = context.queueDepth.coerceAtLeast(0),
            droppedCount = context.droppedCount.coerceAtLeast(0L),
            sinkErrorClass = context.sinkErrorClass,
            stallDurationMs = stallDurationMs,
            preparedSlideCount = bitmaps.preparedSlideCount,
            renderedSlideCount = bitmaps.renderedSlideCount,
            decodedBitmapCount = bitmaps.decodedBitmapCount,
            appBitmapCount = bitmaps.appBitmapCount,
            activeDecodedBytes = bitmaps.activeDecodedBytes,
            pendingDisposals = bitmaps.pendingDisposals,
            memoryProtectionLevel = bitmaps.memoryProtectionLevel,
            oomCount = bitmaps.oomCount,
        )
    }

    private fun boundedCauseChain(root: Throwable): List<Throwable> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val result = ArrayList<Throwable>(MAX_CAUSES)
        var current: Throwable? = root
        while (current != null && result.size < MAX_CAUSES && seen.add(current)) {
            result += current
            current = current.cause
        }
        return result
    }

    private fun frame(value: StackTraceElement): CrashEnvelope.Frame = CrashEnvelope.Frame(
        className = value.className,
        methodName = value.methodName,
        sourceFile = value.fileName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.endsWith(".kt") || it.endsWith(".java") }
            .orEmpty(),
        lineNumber = value.lineNumber,
    )

    private const val APP_PACKAGE = "com.example.familyphotoframe"
    private const val MAX_CAUSES = 8
    private const val MAX_FRAMES = 8
}

/**
 * Small synchronous store. Android supplies a committed-private-preferences backend;
 * JVM tests use an in-memory backend. The checksum distinguishes a torn write.
 */
class CrashEnvelopeStore(private val storage: Storage) {
    interface Storage {
        fun read(): String?
        fun write(value: String): Boolean
        fun clear(): Boolean
    }

    sealed interface ReadResult {
        data object Missing : ReadResult
        data class Valid(
            val envelope: CrashEnvelope,
            val sizeBytes: Int,
            internal val storageId: String,
        ) : ReadResult
        data class Corrupt(
            val reason: String,
            val sizeBytes: Int,
            internal val storageId: String,
        ) : ReadResult
    }

    private val lock = Any()

    fun write(envelope: CrashEnvelope): Boolean = synchronized(lock) {
        val body = encodeBody(envelope)
        val checksum = checksum(body)
        val encoded = "$body\nchecksum=$checksum"
        if (encoded.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) return@synchronized false
        storage.write(encoded)
    }

    fun read(): ReadResult = synchronized(lock) {
        val raw = storage.read() ?: return@synchronized ReadResult.Missing
        val size = raw.toByteArray(StandardCharsets.UTF_8).size
        val storageId = checksum(raw)
        if (size > MAX_BYTES) return@synchronized ReadResult.Corrupt("OVERSIZED", size, storageId)
        val marker = raw.lastIndexOf("\nchecksum=")
        if (marker <= 0) return@synchronized ReadResult.Corrupt("MISSING_CHECKSUM", size, storageId)
        val body = raw.substring(0, marker)
        val expected = raw.substring(marker + "\nchecksum=".length)
        if (expected != checksum(body)) {
            return@synchronized ReadResult.Corrupt("CHECKSUM_MISMATCH", size, storageId)
        }
        runCatching { decodeBody(body) }
            .fold(
                onSuccess = { ReadResult.Valid(it, size, storageId) },
                onFailure = { ReadResult.Corrupt("INVALID_SCHEMA", size, storageId) },
            )
    }

    fun clear(): Boolean = synchronized(lock) { storage.clear() }

    /** Clear only the exact bytes that were recovered; never erase newer evidence. */
    fun clearIfUnchanged(result: ReadResult): Boolean = synchronized(lock) {
        val expected = when (result) {
            ReadResult.Missing -> return@synchronized false
            is ReadResult.Valid -> result.storageId
            is ReadResult.Corrupt -> result.storageId
        }
        val current = storage.read() ?: return@synchronized false
        if (checksum(current) == expected) storage.clear() else false
    }

    fun clearIf(kind: CrashEnvelope.Kind, sessionId: String): Boolean = synchronized(lock) {
        val existing = read() as? ReadResult.Valid ?: return false
        if (existing.envelope.kind == kind && existing.envelope.previousSessionId == sessionId) {
            clear()
        } else false
    }

    fun sizeBytes(): Int = synchronized(lock) {
        storage.read()?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0
    }

    private fun encodeBody(e: CrashEnvelope): String = buildString {
        appendLine(HEADER)
        field("kind", e.kind.name)
        field("atEpochMs", e.atEpochMs)
        field("elapsedRealtimeMs", e.elapsedRealtimeMs)
        field("previousSessionId", e.previousSessionId)
        field("lastSequence", e.lastSequence)
        field("appVersion", e.appVersion)
        field("versionCode", e.versionCode)
        field("sdkInt", e.sdkInt)
        field("exceptionClass", e.exceptionClass)
        field("rootCauseClass", e.rootCauseClass)
        field("threadName", e.threadName)
        field("mainThread", e.mainThread)
        field("heapUsedKb", e.heapUsedKb)
        field("heapMaxKb", e.heapMaxKb)
        field("nativeHeapKb", e.nativeHeapKb)
        field("pssKb", e.pssKb)
        field("imageCacheKb", e.imageCacheKb)
        field("surface", e.surface)
        field("engineState", e.engineState)
        field("presentationToken", e.presentationToken)
        field("sourceKind", e.sourceKind)
        field("layout", e.layout)
        field("transitionCode", e.transitionCode)
        field("activeOperationId", e.activeOperationId)
        field("activeOperationStage", e.activeOperationStage)
        field("queueDepth", e.queueDepth)
        field("droppedCount", e.droppedCount)
        field("sinkErrorClass", e.sinkErrorClass)
        field("stallDurationMs", e.stallDurationMs)
        field("preparedSlideCount", e.preparedSlideCount)
        field("renderedSlideCount", e.renderedSlideCount)
        field("decodedBitmapCount", e.decodedBitmapCount)
        field("appBitmapCount", e.appBitmapCount)
        field("activeDecodedBytes", e.activeDecodedBytes)
        field("pendingDisposals", e.pendingDisposals)
        field("memoryProtectionLevel", e.memoryProtectionLevel)
        field("oomCount", e.oomCount)
        e.frames.take(MAX_FRAMES).forEach { frame ->
            append("frame=")
            append(safe(frame.className, 120)).append('|')
            append(safe(frame.methodName, 80)).append('|')
            append(safe(frame.sourceFile, 80)).append('|')
            append(frame.lineNumber.coerceAtLeast(-1))
            append('\n')
        }
    }.trimEnd()

    private fun StringBuilder.field(key: String, value: Any) {
        val protected = DiagnosticPrivacyPolicy.protect(key, value.toString()).value
        append(key).append('=').append(safe(protected, MAX_VALUE_CHARS)).append('\n')
    }

    private fun decodeBody(body: String): CrashEnvelope {
        val lines = body.lineSequence().toList()
        require(lines.firstOrNull() == HEADER)
        val fields = linkedMapOf<String, String>()
        val frames = arrayListOf<CrashEnvelope.Frame>()
        for (line in lines.drop(1)) {
            val separator = line.indexOf('=')
            require(separator > 0)
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            if (key == "frame") {
                val pieces = value.split('|')
                require(pieces.size == 4)
                frames += CrashEnvelope.Frame(pieces[0], pieces[1], pieces[2], pieces[3].toInt())
            } else {
                fields[key] = value
            }
        }
        fun value(name: String): String = fields[name] ?: error("missing $name")
        fun optionalLong(name: String): Long = fields[name]?.toLongOrNull() ?: 0L
        fun optionalInt(name: String): Int = fields[name]?.toIntOrNull() ?: 0
        return CrashEnvelope(
            kind = CrashEnvelope.Kind.valueOf(value("kind")),
            atEpochMs = value("atEpochMs").toLong(),
            elapsedRealtimeMs = value("elapsedRealtimeMs").toLong(),
            previousSessionId = value("previousSessionId"),
            lastSequence = value("lastSequence").toLong(),
            appVersion = value("appVersion"),
            versionCode = value("versionCode").toLong(),
            sdkInt = value("sdkInt").toInt(),
            exceptionClass = value("exceptionClass"),
            rootCauseClass = value("rootCauseClass"),
            threadName = value("threadName"),
            mainThread = value("mainThread").toBooleanStrict(),
            frames = frames.take(MAX_FRAMES),
            heapUsedKb = value("heapUsedKb").toLong(),
            heapMaxKb = value("heapMaxKb").toLong(),
            nativeHeapKb = value("nativeHeapKb").toLong(),
            pssKb = value("pssKb").toLong(),
            imageCacheKb = value("imageCacheKb").toLong(),
            surface = value("surface"),
            engineState = value("engineState"),
            presentationToken = value("presentationToken"),
            sourceKind = value("sourceKind"),
            layout = value("layout"),
            transitionCode = value("transitionCode"),
            activeOperationId = value("activeOperationId"),
            activeOperationStage = value("activeOperationStage"),
            queueDepth = value("queueDepth").toInt(),
            droppedCount = value("droppedCount").toLong(),
            sinkErrorClass = value("sinkErrorClass"),
            stallDurationMs = value("stallDurationMs").toLong(),
            preparedSlideCount = optionalInt("preparedSlideCount"),
            renderedSlideCount = optionalInt("renderedSlideCount"),
            decodedBitmapCount = optionalInt("decodedBitmapCount"),
            appBitmapCount = optionalInt("appBitmapCount"),
            activeDecodedBytes = optionalLong("activeDecodedBytes"),
            pendingDisposals = optionalInt("pendingDisposals"),
            memoryProtectionLevel = fields["memoryProtectionLevel"] ?: "NORMAL",
            oomCount = optionalLong("oomCount"),
        )
    }

    private fun safe(value: String, max: Int): String = value
        .filter { it.isLetterOrDigit() || it in SAFE_PUNCTUATION }
        .take(max)

    private fun checksum(body: String): String = CRC32().run {
        update(body.toByteArray(StandardCharsets.UTF_8))
        value.toString(16).padStart(8, '0')
    }

    companion object {
        const val MAX_BYTES = 16 * 1024
        private const val MAX_FRAMES = 8
        private const val MAX_VALUE_CHARS = 160
        private const val HEADER = "FPF_CRASH_ENVELOPE_V1"
        private val SAFE_PUNCTUATION = setOf('_', '-', '.', '$', ':')
    }
}
