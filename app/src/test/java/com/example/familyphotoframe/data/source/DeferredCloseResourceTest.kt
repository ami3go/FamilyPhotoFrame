package com.example.familyphotoframe.data.source

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DeferredCloseResourceTest {
    @Test fun closeWaitsForEveryOutstandingLease() {
        val created = Any()
        val closeCount = AtomicInteger(0)
        val owner = DeferredCloseResource(factory = { created }) { closeCount.incrementAndGet() }
        val first = owner.acquire()
        val second = owner.acquire()

        assertSame(created, first.value)
        assertSame(created, second.value)
        owner.close()

        assertEquals(0, closeCount.get())
        assertThrows(IllegalStateException::class.java) { owner.acquire() }

        first.close()
        first.close()
        assertEquals(0, closeCount.get())

        second.close()
        owner.close()
        assertEquals(1, closeCount.get())
    }

    @Test fun closeBeforeFirstUseDoesNotCreateAResource() {
        val createCount = AtomicInteger(0)
        val closeCount = AtomicInteger(0)
        val owner = DeferredCloseResource(
            factory = {
                createCount.incrementAndGet()
                Any()
            },
            closer = { closeCount.incrementAndGet() },
        )

        owner.close()

        assertEquals(0, createCount.get())
        assertEquals(0, closeCount.get())
        assertThrows(IllegalStateException::class.java) { owner.acquire() }
    }

    @Test fun awaitClosedTracksTheFinalOutstandingLease() = runBlocking {
        val owner = DeferredCloseResource(factory = { Any() }) { }
        val lease = owner.acquire()

        owner.close()
        assertFalse(owner.awaitClosed(1))
        lease.close()

        assertTrue(owner.awaitClosed(100))
    }
}
