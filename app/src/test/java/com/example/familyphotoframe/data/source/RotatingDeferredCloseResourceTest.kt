package com.example.familyphotoframe.data.source

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingDeferredCloseResourceTest {
    @Test fun invalidationPublishesFreshLazyGeneration() {
        val created = AtomicInteger()
        val closed = AtomicInteger()
        val owner = RotatingDeferredCloseResource(
            factory = { Any().also { created.incrementAndGet() } },
            closer = { closed.incrementAndGet() },
        )

        val first = owner.acquire()
        first.close()
        assertTrue(owner.invalidate(first))
        assertEquals(1, created.get())
        assertEquals(1, closed.get())

        val second = owner.acquire()
        assertNotSame(first.value, second.value)
        assertEquals(2, created.get())
        second.close()
        owner.close()
        assertEquals(2, closed.get())
    }

    @Test fun staleLeaseCannotInvalidateNewGeneration() {
        val closed = AtomicInteger()
        val owner = RotatingDeferredCloseResource(factory = { Any() }) {
            closed.incrementAndGet()
        }
        val first = owner.acquire()
        first.close()

        assertTrue(owner.invalidate(first))
        assertFalse(owner.invalidate(first))
        assertEquals(1, closed.get())

        val second = owner.acquire()
        second.close()
        owner.close()
        assertEquals(2, closed.get())
    }

    @Test fun invalidationDefersCloseUntilOutstandingLeaseReturns() {
        val closed = AtomicInteger()
        val owner = RotatingDeferredCloseResource(factory = { Any() }) {
            closed.incrementAndGet()
        }
        val first = owner.acquire()
        val sibling = owner.acquire()

        first.close()
        assertTrue(owner.invalidate(first))
        assertEquals(0, closed.get())

        sibling.close()
        assertEquals(1, closed.get())
        owner.close()
    }

    @Test fun finalCloseRejectsNewLeases() {
        val owner = RotatingDeferredCloseResource(factory = { Any() }, closer = {})
        val lease = owner.acquire()
        lease.close()
        owner.close()

        assertThrows(IllegalStateException::class.java) { owner.acquire() }
        assertFalse(owner.invalidate(lease))
    }
}
