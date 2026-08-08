package com.example.familyphotoframe.data.diagnostics

import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Bounded active-operation registry used by source work and crash capture.
 *
 * Mutations use a short monitor; crash capture reads the immutable [snapshot] through an
 * [AtomicReference] and therefore never waits on a lock held by a failing coroutine.
 */
class DiagnosticOperationTracker(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val abandonAfterMs: Long = DEFAULT_ABANDON_AFTER_MS,
) {
    data class Operation(
        val id: String,
        val type: String,
        val origin: DiagnosticOrigin,
        val parentOperationId: String?,
        val startedElapsedMs: Long,
        val stage: String,
        val updatedElapsedMs: Long,
    )

    class Handle internal constructor(
        val operationId: String,
        val parentOperationId: String?,
        val origin: DiagnosticOrigin,
    ) {
        fun context(): DiagnosticContext = DiagnosticContext(origin, operationId, parentOperationId)
    }

    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val active = LinkedHashMap<String, Operation>()
    private val published = AtomicReference<List<Operation>>(emptyList())
    @Volatile private var sessionToken: String = "nosession"

    fun attachSession(sessionId: String) = synchronized(lock) {
        sessionToken = sessionId.lowercase().filter { it.isLetterOrDigit() }.take(12).ifEmpty { "session" }
        nextId.set(0L)
        active.clear()
        publishLocked()
    }

    fun start(
        type: String,
        origin: DiagnosticOrigin,
        parentOperationId: String? = null,
        initialStage: String = "QUEUED",
    ): Handle = synchronized(lock) {
        val now = elapsedRealtimeMs()
        expireLocked(now)
        while (active.size >= capacity.coerceAtLeast(1)) {
            active.remove(active.entries.first().key)
        }
        val safeType = type.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(24)
            .ifEmpty { "OP" }
        val id = "${safeType.lowercase()}-$sessionToken-${nextId.incrementAndGet()}"
        active[id] = Operation(
            id = id,
            type = safeType,
            origin = origin,
            parentOperationId = parentOperationId,
            startedElapsedMs = now,
            stage = safeStage(initialStage),
            updatedElapsedMs = now,
        )
        publishLocked()
        Handle(id, parentOperationId, origin)
    }

    fun update(operationId: String, stage: String): Operation? = synchronized(lock) {
        val old = active[operationId] ?: return@synchronized null
        val updated = old.copy(stage = safeStage(stage), updatedElapsedMs = elapsedRealtimeMs())
        active[operationId] = updated
        publishLocked()
        updated
    }

    fun finish(operationId: String): Operation? = synchronized(lock) {
        val removed = active.remove(operationId)
        publishLocked()
        removed
    }

    /** Lock-free and allocation-free for the caller. */
    fun snapshot(): List<Operation> = published.get()

    fun current(): Operation? = published.get().maxByOrNull { it.updatedElapsedMs }

    fun expireAbandoned(): Int = synchronized(lock) {
        val before = active.size
        expireLocked(elapsedRealtimeMs())
        publishLocked()
        before - active.size
    }

    private fun expireLocked(now: Long) {
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val operation = iterator.next().value
            if (now - operation.updatedElapsedMs > abandonAfterMs) iterator.remove()
        }
    }

    private fun publishLocked() {
        published.set(active.values.toList())
    }

    private fun safeStage(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(32).ifEmpty { "UNKNOWN" }

    private companion object {
        const val DEFAULT_CAPACITY = 64
        const val DEFAULT_ABANDON_AFTER_MS = 30L * 60_000L

    }
}
