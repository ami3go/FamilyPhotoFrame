package com.example.familyphotoframe.data.cache

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TransferLimitExceededException(limitBytes: Long) :
    IOException("remote item exceeds cache transfer limit ($limitBytes bytes)")

internal class CacheStorageReserveException(reserveBytes: Long) :
    IOException("cache write would consume reserved free space ($reserveBytes bytes)")

/**
 * Copies blocking source bytes while making coroutine cancellation actively close the
 * source stream. Closing from the cancellation handler unblocks socket-backed reads on
 * transports that do not respond to thread interruption (notably java.net and jCIFS).
 */
internal suspend fun InputStream.copyToCancellable(
    output: OutputStream,
    maxBytes: Long,
    minimumUsableBytes: Long,
    usableBytes: () -> Long,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    /** Called at a bounded cadence; it must not retain the supplied transfer bytes. */
    onProgress: (copiedBytes: Long) -> Unit = {},
    /** Invoked after cancellation asks the source stream to close. */
    onCancellationClose: (copiedBytes: Long, closeSucceeded: Boolean) -> Unit = { _, _ -> },
): Long = suspendCancellableCoroutine { continuation ->
    val copiedForCancellation = AtomicLong(0L)
    continuation.invokeOnCancellation {
        val closeSucceeded = runCatching { close() }.isSuccess
        runCatching { onCancellationClose(copiedForCancellation.get(), closeSucceeded) }
    }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    var bytesUntilSpaceCheck = 0L
    var lastProgressBytes = 0L
    var lastProgressAtMs = elapsedNowMs()
    try {
        while (continuation.isActive) {
            if (bytesUntilSpaceCheck <= 0L) {
                if (usableBytes() <= minimumUsableBytes) {
                    throw CacheStorageReserveException(minimumUsableBytes)
                }
                bytesUntilSpaceCheck = SPACE_CHECK_INTERVAL_BYTES
            }
            val count = read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (copied > maxBytes - count) throw TransferLimitExceededException(maxBytes)
            output.write(buffer, 0, count)
            copied += count
            copiedForCancellation.set(copied)
            bytesUntilSpaceCheck -= count
            val now = elapsedNowMs()
            if (copied - lastProgressBytes >= PROGRESS_REPORT_INTERVAL_BYTES ||
                now - lastProgressAtMs >= PROGRESS_REPORT_INTERVAL_MS
            ) {
                onProgress(copied)
                lastProgressBytes = copied
                lastProgressAtMs = now
            }
        }
        if (continuation.isActive) {
            if (copied != lastProgressBytes) onProgress(copied)
            continuation.resume(copied)
        }
    } catch (error: Throwable) {
        if (!continuation.isCancelled) continuation.resumeWithException(error)
    }
}

private const val SPACE_CHECK_INTERVAL_BYTES = 1024L * 1024L
private const val PROGRESS_REPORT_INTERVAL_BYTES = 1024L * 1024L
private const val PROGRESS_REPORT_INTERVAL_MS = 1_000L

private fun elapsedNowMs(): Long = System.nanoTime() / 1_000_000L
