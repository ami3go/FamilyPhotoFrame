package com.example.familyphotoframe.data.source

import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadlineInputStreamTest {
    @Test fun deadlineActivelyClosesAnAbandonedStream() {
        val closed = CountDownLatch(1)
        val underlying = object : InputStream() {
            override fun read(): Int = -1
            override fun close() {
                closed.countDown()
            }
        }
        val stream = DeadlineInputStream(underlying, timeoutMs = 20)

        assertTrue(closed.await(2, TimeUnit.SECONDS))
        assertThrows(SocketTimeoutException::class.java) { stream.read() }
    }
}
