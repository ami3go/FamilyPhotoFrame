package com.example.familyphotoframe.data.cache

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
): Long = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { close() } }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    var bytesUntilSpaceCheck = 0L
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
            bytesUntilSpaceCheck -= count
        }
        if (continuation.isActive) continuation.resume(copied)
    } catch (error: Throwable) {
        if (!continuation.isCancelled) continuation.resumeWithException(error)
    }
}

private const val SPACE_CHECK_INTERVAL_BYTES = 1024L * 1024L
