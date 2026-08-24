package com.example.familyphotoframe.data.source

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gives a caller-owned blocking stream a hard lifetime deadline.
 *
 * Socket read timeouts only bound an individual idle read: a peer that keeps producing
 * small amounts of data can otherwise occupy a connection forever. The single shared
 * scheduler closes the underlying stream at the deadline, which also unblocks most
 * socket-backed reads. One daemon scheduler is shared process-wide, avoiding the much
 * larger failure mode of one timer thread per opened photo.
 */
internal class DeadlineInputStream(
    input: InputStream,
    timeoutMs: Long,
) : FilterInputStream(input) {
    private val closed = AtomicBoolean(false)
    private val timedOut = AtomicBoolean(false)
    private val deadline: ScheduledFuture<*> = scheduler.schedule(
        {
            timedOut.set(true)
            closeUnderlying()
        },
        timeoutMs.coerceAtLeast(1L),
        TimeUnit.MILLISECONDS,
    )

    override fun read(): Int = mapTimeout { super.read() }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        mapTimeout { super.read(buffer, offset, length) }

    override fun skip(byteCount: Long): Long = mapTimeout { super.skip(byteCount) }

    override fun close() {
        deadline.cancel(false)
        closeUnderlying()
    }

    private fun closeUnderlying() {
        if (closed.compareAndSet(false, true)) super.close()
    }

    private inline fun <T> mapTimeout(block: () -> T): T {
        if (timedOut.get()) throw timeout()
        return try {
            block()
        } catch (error: IOException) {
            if (timedOut.get()) throw timeout(error)
            throw error
        }
    }

    private fun timeout(cause: IOException? = null): SocketTimeoutException =
        SocketTimeoutException("stream lifetime exceeded").also { error ->
            if (cause != null) error.initCause(cause)
        }

    private companion object {
        private val scheduler = ScheduledThreadPoolExecutor(
            1,
            ThreadFactory { task ->
                Thread(task, "photo-stream-deadline").apply { isDaemon = true }
            },
        ).apply {
            removeOnCancelPolicy = true
            // Do not enable core-thread timeout here. A delayed task can outlive the
            // executor keep-alive, leaving no worker to fire it until another stream is
            // opened — exactly when the deadline is needed most.
        }
    }
}
