package com.example.familyphotoframe.data.cache

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableStreamCopyTest {
    @Test fun cancellationClosesAndUnblocksABlockingSource() = runBlocking {
        val enteredRead = CountDownLatch(1)
        val releasedRead = CountDownLatch(1)
        val source = object : InputStream() {
            @Volatile var closed = false

            override fun read(): Int {
                enteredRead.countDown()
                releasedRead.await()
                return -1
            }

            override fun close() {
                closed = true
                releasedRead.countDown()
            }
        }
        var cancellationCopiedBytes = -1L
        var cancellationCloseSucceeded = false
        val job = launch(Dispatchers.IO) {
            source.copyToCancellable(
                ByteArrayOutputStream(),
                maxBytes = 1_024,
                minimumUsableBytes = 0,
                usableBytes = { Long.MAX_VALUE },
                bufferSize = 32,
                onCancellationClose = { copiedBytes, closeSucceeded ->
                    cancellationCopiedBytes = copiedBytes
                    cancellationCloseSucceeded = closeSucceeded
                },
            )
        }

        assertTrue(enteredRead.await(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(source.closed)
        assertEquals(0L, cancellationCopiedBytes)
        assertTrue(cancellationCloseSucceeded)
    }

    @Test(expected = TransferLimitExceededException::class)
    fun transferLimitStopsOversizedInput() {
        runBlocking {
            ByteArray(64).inputStream().copyToCancellable(
                ByteArrayOutputStream(),
                maxBytes = 32,
                minimumUsableBytes = 0,
                usableBytes = { Long.MAX_VALUE },
                bufferSize = 16,
            )
        }
    }

    @Test fun progressReportsTheFinalCopiedByteCountWithoutPerReadNoise() = runBlocking {
        val updates = mutableListOf<Long>()
        val copied = ByteArray(64).inputStream().copyToCancellable(
            ByteArrayOutputStream(),
            maxBytes = 128,
            minimumUsableBytes = 0,
            usableBytes = { Long.MAX_VALUE },
            bufferSize = 16,
            onProgress = { updates += it },
        )

        assertEquals(64L, copied)
        assertEquals(listOf(64L), updates)
    }
}
