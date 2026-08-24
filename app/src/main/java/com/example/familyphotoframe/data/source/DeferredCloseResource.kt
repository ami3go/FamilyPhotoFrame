package com.example.familyphotoframe.data.source

import java.util.concurrent.atomic.AtomicBoolean

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
        val resourceToClose = synchronized(lock) {
            if (closeRequested) return
            closeRequested = true
            takeResourceIfUnused()
        }
        resourceToClose?.let(closer)
    }

    private fun releaseLease() {
        val resourceToClose = synchronized(lock) {
            check(activeLeases > 0) { "Released more resource leases than were acquired" }
            activeLeases--
            if (closeRequested) takeResourceIfUnused() else null
        }
        resourceToClose?.let(closer)
    }

    /** Must be called under [lock]. */
    private fun takeResourceIfUnused(): T? {
        if (activeLeases != 0) return null
        return resource.also { resource = null }
    }
}
