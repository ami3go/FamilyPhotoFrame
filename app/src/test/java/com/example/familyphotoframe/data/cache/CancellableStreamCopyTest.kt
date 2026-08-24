package com.example.familyphotoframe.data.cache

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val job = launch(Dispatchers.IO) {
            source.copyToCancellable(
                ByteArrayOutputStream(),
                maxBytes = 1_024,
                minimumUsableBytes = 0,
                usableBytes = { Long.MAX_VALUE },
                bufferSize = 32,
            )
        }

        assertTrue(enteredRead.await(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(source.closed)
    }

    @Test(expected = TransferLimitExceededException::class)
    fun transferLimitStopsOversizedInput() = runBlocking {
        ByteArray(64).inputStream().copyToCancellable(
            ByteArrayOutputStream(),
            maxBytes = 32,
            minimumUsableBytes = 0,
            usableBytes = { Long.MAX_VALUE },
            bufferSize = 16,
        )
    }
}
