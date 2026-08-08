package com.example.familyphotoframe.web

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedHttpAsyncRunnerTest {

    @Test fun saturatedRunnerRejectsWithoutExceedingWorkerAndQueueBounds() {
        val rejected = AtomicInteger(0)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val runner = BoundedHttpAsyncRunner(workerCount = 1, queueCapacity = 1)
        val server = HandlerFactory()

        val first = server.handler(
            runBlock = {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            },
            rejectBlock = { rejected.incrementAndGet() },
        )
        val second = server.handler({}, { rejected.incrementAndGet() })
        val third = server.handler({}, { rejected.incrementAndGet() })

        try {
            runner.exec(first)
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            runner.exec(second)
            runner.exec(third)

            assertEquals(1, rejected.get())
            assertEquals(2, runner.activeOrQueuedConnections())
        } finally {
            releaseFirst.countDown()
            runner.closeAll()
        }
    }

    private class HandlerFactory : NanoHTTPD(0) {
        override fun serve(session: IHTTPSession): Response = newFixedLengthResponse("")

        fun handler(runBlock: () -> Unit, rejectBlock: () -> Unit): ClientHandler =
            object : ClientHandler(ByteArrayInputStream(ByteArray(0)), Socket()),
                ServiceUnavailableConnection {
                override fun run() = runBlock()
                override fun rejectServiceUnavailable() = rejectBlock()
            }
    }
}
