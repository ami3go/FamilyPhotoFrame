package com.example.familyphotoframe.data.diagnostics

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * One privacy-safe, app-private breadcrumb describing the latest presentation operation.
 *
 * Ordinary updates may use an asynchronous Android preferences write. The one-minute
 * runtime marker and every severe memory callback call [flush], making the current value
 * durable without synchronously rewriting preferences for every slideshow frame.
 */
class PersistentRuntimeBreadcrumbs(
    private val storage: Storage,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    data class Breadcrumb(
        val sequence: Long,
        val sessionId: String,
        val operation: String,
        val stage: String,
        val active: Boolean,
        val presentationToken: String,
        val sourceKind: String,
        val updatedAtEpochMs: Long,
        val updatedElapsedRealtimeMs: Long,
    )

    interface Storage {
        fun read(): Breadcrumb?
        fun write(value: Breadcrumb, synchronous: Boolean): Boolean
    }

    private val initial = storage.read()
    private val current = AtomicReference(initial)
    private val nextSequence = AtomicLong(initial?.sequence?.coerceAtLeast(0L) ?: 0L)
    private val mutationLock = Any()
    @Volatile private var sessionId: String = "nosession"

    /** Persisted value from the previous process, read before this process records work. */
    fun persisted(): Breadcrumb? = initial

    fun attachSession(value: String) {
        sessionId = value.filter { it.isLetterOrDigit() || it == '-' }.take(64)
            .ifEmpty { "session" }
    }

    fun record(
        operation: String,
        stage: String,
        active: Boolean,
        presentationToken: String = "",
        sourceKind: String = "NONE",
    ): Breadcrumb = synchronized(mutationLock) {
        val breadcrumb = Breadcrumb(
            sequence = nextSequence.incrementAndGet(),
            sessionId = sessionId,
            operation = safeCode(operation, "UNKNOWN"),
            stage = safeCode(stage, "UNKNOWN"),
            active = active,
            presentationToken = presentationToken.takeIf(String::isNotBlank)?.let {
                DiagnosticPrivacyPolicy.protect("presentationToken", it).value
            }.orEmpty().take(80),
            sourceKind = safeCode(sourceKind, "NONE"),
            updatedAtEpochMs = nowEpochMs().coerceAtLeast(0L),
            updatedElapsedRealtimeMs = elapsedRealtimeMs().coerceAtLeast(0L),
        )
        current.set(breadcrumb)
        storage.write(breadcrumb, synchronous = false)
        breadcrumb
    }

    /** Synchronously persist the latest in-memory breadcrumb at a high-value boundary. */
    fun flush(): Boolean = synchronized(mutationLock) {
        current.get()?.let { storage.write(it, synchronous = true) } ?: true
    }

    fun snapshot(): Breadcrumb? = current.get()

    private fun safeCode(value: String, fallback: String): String =
        value.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(48)
            .ifEmpty { fallback }
}
