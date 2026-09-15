package com.example.familyphotoframe.data.source

/**
 * A generation-aware owner for resources that must be replaced after a failed operation.
 *
 * A lease pins the generation it acquired. [invalidate] atomically publishes a fresh,
 * lazily-created generation and closes the failed one once all of its existing leases have
 * returned. This keeps new work away from a poisoned connection pool without tearing down an
 * unrelated in-flight stream.
 */
internal class RotatingDeferredCloseResource<T : Any>(
    private val factory: () -> T,
    private val closer: (T) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var current = newGeneration()
    private var closed = false

    internal class Lease<T : Any>(
        val value: T,
        internal val generation: DeferredCloseResource<T>,
        private val delegate: DeferredCloseResource.Lease<T>,
    ) : AutoCloseable {
        override fun close() = delegate.close()
    }

    fun acquire(): Lease<T> = synchronized(lock) {
        check(!closed) { "Resource used after close()" }
        val generation = current
        val delegate = generation.acquire()
        Lease(delegate.value, generation, delegate)
    }

    /**
     * Replace the generation used by [lease], if it is still current.
     *
     * The replacement is lazy: its expensive resource is not constructed until the next
     * [acquire]. Closing the old generation remains lease-aware through
     * [DeferredCloseResource].
     */
    fun invalidate(lease: Lease<T>): Boolean {
        val retired = synchronized(lock) {
            if (closed || current !== lease.generation) {
                null
            } else {
                current.also { current = newGeneration() }
            }
        }
        retired?.close()
        return retired != null
    }

    override fun close() {
        val retired = synchronized(lock) {
            if (closed) return
            closed = true
            current
        }
        retired.close()
    }

    private fun newGeneration(): DeferredCloseResource<T> =
        DeferredCloseResource(factory, closer)
}
