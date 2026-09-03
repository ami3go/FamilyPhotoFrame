package com.example.familyphotoframe.data.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Serializes payload-heavy remote reads per source and gives display work strict priority.
 *
 * jCIFS can otherwise retain several request buffers while a slow link services media,
 * preload and content-hash reads concurrently.  Lower-priority readers cooperatively check
 * [Lease.shouldYield] after each bounded read, close their stream and retry later.
 */
class RemoteTransferCoordinator {
    enum class Priority {
        SELECTED_MEDIA,
        PARTIAL_RESUME,
        PRELOAD,
        CONTENT_HASH,
    }

    class YieldException : CancellationException("remote_transfer_yielded_to_higher_priority")

    class Lease internal constructor(
        private val gate: Gate,
        private val priority: Priority,
    ) {
        fun shouldYield(): Boolean = gate.hasHigherWaiter(priority)

        fun throwIfYieldRequested() {
            if (shouldYield()) throw YieldException()
        }
    }

    internal class Gate {
        val permit = Semaphore(1)
        private val waiters = AtomicIntegerArray(Priority.entries.size)

        fun addWaiter(priority: Priority) = waiters.incrementAndGet(priority.ordinal)
        fun removeWaiter(priority: Priority) = waiters.decrementAndGet(priority.ordinal)
        fun hasHigherWaiter(priority: Priority): Boolean =
            (0 until priority.ordinal).any { waiters.get(it) > 0 }
    }

    private val gates = ConcurrentHashMap<String, Gate>()

    suspend fun <T> withPermit(
        sourceId: String,
        priority: Priority,
        block: suspend (Lease) -> T,
    ): T {
        val gate = gates.getOrPut(sourceId) { Gate() }
        gate.addWaiter(priority)
        var acquired = false
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                while (gate.hasHigherWaiter(priority)) {
                    delay(PRIORITY_POLL_MS)
                    currentCoroutineContext().ensureActive()
                }
                gate.permit.acquire()
                acquired = true
                // A selected request may have arrived while this lower-priority caller
                // was suspended inside acquire. Return the permit before doing any I/O.
                if (!gate.hasHigherWaiter(priority)) break
                gate.permit.release()
                acquired = false
            }
            gate.removeWaiter(priority)
            return block(Lease(gate, priority))
        } finally {
            // removeWaiter is required when cancellation happens before acquisition.
            if (!acquired) runCatching { gate.removeWaiter(priority) }
            if (acquired) gate.permit.release()
        }
    }

    private companion object {
        const val PRIORITY_POLL_MS = 25L
    }
}
