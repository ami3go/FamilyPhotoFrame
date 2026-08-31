package com.example.familyphotoframe.data.source

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lazily owns a closeable resource whose destruction must not race an in-flight user.
 *
 * [close] permanently rejects new leases, but an already acquired lease keeps the
 * resource alive until that operation releases it. The expensive [closer] runs outside
 * the monitor so a transport shutdown cannot block unrelated lease bookkeeping.
 */
internal class DeferredCloseResource<T : Any>(
    private val factory: () -> T,
    private val closer: (T) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var resource: T? = null
    private var activeLeases = 0
    private var closeRequested = false
    private val fullyClosed = CompletableDeferred<Unit>()

    internal class Lease<T : Any>(
        val value: T,
        private val releaseAction: () -> Unit,
    ) : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) releaseAction()
        }
    }

    fun acquire(): Lease<T> {
        val value = synchronized(lock) {
            check(!closeRequested) { "Resource used after close()" }
            (resource ?: factory().also { resource = it }).also { activeLeases++ }
        }
        return Lease(value, ::releaseLease)
    }

    override fun close() {
        val closeResult = synchronized(lock) {
            if (closeRequested) return
            closeRequested = true
            takeResourceIfUnused().let { it to (activeLeases == 0) }
        }
        try {
            closeResult.first?.let(closer)
        } finally {
            if (closeResult.second) fullyClosed.complete(Unit)
        }
    }

    /** Waits without blocking a dispatcher thread for every lease and the closer. */
    suspend fun awaitClosed(timeoutMs: Long): Boolean {
        if (fullyClosed.isCompleted) return true
        if (timeoutMs <= 0L) return false
        return withTimeoutOrNull(timeoutMs) {
            fullyClosed.await()
            true
        } ?: false
    }

    private fun releaseLease() {
        val closeResult = synchronized(lock) {
            check(activeLeases > 0) { "Released more resource leases than were acquired" }
            activeLeases--
            val resource = if (closeRequested) takeResourceIfUnused() else null
            resource to (closeRequested && activeLeases == 0)
        }
        try {
            closeResult.first?.let(closer)
        } finally {
            if (closeResult.second) fullyClosed.complete(Unit)
        }
    }

    /** Must be called under [lock]. */
    private fun takeResourceIfUnused(): T? {
        if (activeLeases != 0) return null
        return resource.also { resource = null }
    }
}
