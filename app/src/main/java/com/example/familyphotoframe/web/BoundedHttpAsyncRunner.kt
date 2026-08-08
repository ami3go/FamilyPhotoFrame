package com.example.familyphotoframe.web

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Implemented by a client handler that can send a terminal 503 before closing. */
internal interface ServiceUnavailableConnection {
    fun rejectServiceUnavailable()
}

/**
 * Bounds NanoHTTPD's connection workers and accepted-socket queue.
 *
 * NanoHTTPD's default runner creates one thread per accepted connection. On the
 * 100-MiB API-22 target, thread stacks alone can exhaust the process. This runner
 * admits at most [workerCount] active connections plus [queueCapacity] queued sockets
 * and rejects excess connections without creating another thread. [WebConfigServer]
 * closes every connection after its response because NanoHTTPD otherwise pins a worker
 * to each idle HTTP keep-alive socket and can starve queued browser asset/API requests.
 */
internal class BoundedHttpAsyncRunner(
    private val workerCount: Int = DEFAULT_WORKERS,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val onRejected: () -> Unit = {},
) : NanoHTTPD.AsyncRunner {
    private val closed = AtomicBoolean(false)
    private val threadSequence = AtomicInteger(0)
    private val running = ConcurrentHashMap<NanoHTTPD.ClientHandler, Boolean>()
    private val executor = ThreadPoolExecutor(
        workerCount,
        workerCount,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(queueCapacity),
        ThreadFactory { task ->
            Thread(task, "FamilyPhotoFrame Web ${threadSequence.incrementAndGet()}").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    init {
        require(workerCount > 0) { "workerCount must be positive" }
        require(queueCapacity > 0) { "queueCapacity must be positive" }
    }

    override fun exec(code: NanoHTTPD.ClientHandler) {
        if (closed.get()) {
            code.close()
            return
        }
        running[code] = true
        try {
            executor.execute(code)
        } catch (_: RejectedExecutionException) {
            running.remove(code)
            if (closed.get()) code.close() else reject(code)
        }
    }

    override fun closed(clientHandler: NanoHTTPD.ClientHandler) {
        running.remove(clientHandler)
    }

    override fun closeAll() {
        if (!closed.compareAndSet(false, true)) return
        running.keys.toList().forEach { handler -> runCatching { handler.close() } }
        executor.shutdownNow()
        running.clear()
    }

    private fun reject(code: NanoHTTPD.ClientHandler) {
        (code as? ServiceUnavailableConnection)?.rejectServiceUnavailable() ?: code.close()
        onRejected()
    }

    internal fun activeOrQueuedConnections(): Int = running.size

    companion object {
        const val DEFAULT_WORKERS = 4
        const val DEFAULT_QUEUE_CAPACITY = 8
    }
}
