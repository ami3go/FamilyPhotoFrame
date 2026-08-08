package com.example.familyphotoframe.domain.randomize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The no-repeat guarantee is the whole reason this class exists, so it is asserted over
 * complete cycles rather than sampled. Mirrors scripts/verify/PlaybackQueueChecks.kt,
 * which runs in the offline harness when Gradle is unavailable.
 */
class PlaybackQueueTest {

    @Test fun shuffleCycleShowsEveryPhotoExactlyOnce() {
        val q = PlaybackQueue()
        val pool = (1L..25L).toList()
        q.sync(pool, shuffleMode = true, random = Random(4))

        val cycle = (1..25).mapNotNull { q.next(Random(it)) }

        assertEquals(25, cycle.size)
        assertEquals(pool.toSet(), cycle.toSet())
        assertEquals("no photo repeated within a cycle", 25, cycle.toSet().size)
    }

    @Test fun shuffleDoesNotReplayTheSamePhotoAcrossTheCycleSeam() {
        val q = PlaybackQueue()
        q.sync((1L..8L).toList(), shuffleMode = true, random = Random(12))
        val first = (1..8).mapNotNull { q.next(Random(5)) }
        val second = (1..8).mapNotNull { q.next(Random(6)) }

        assertEquals((1L..8L).toSet(), second.toSet())
        assertNotEquals(first.last(), second.first())
        assertEquals(1, q.cyclesCompleted)
    }

    @Test fun sequentialModePlaysTheOrderTheDaoSupplied() {
        val q = PlaybackQueue()
        val ordered = listOf(9L, 7L, 5L, 3L)
        q.sync(ordered, shuffleMode = false, random = Random(1))

        assertEquals(ordered, (1..4).mapNotNull { q.next(Random(1)) })
        assertEquals("wraps around", 9L, q.next(Random(1)))
    }

    @Test fun rescanKeepsProgressInsteadOfRestartingTheCycle() {
        val q = PlaybackQueue()
        q.sync(listOf(1L, 2L, 3L, 4L), shuffleMode = false, random = Random(1))
        assertEquals(listOf(1L, 2L), listOf(q.next(), q.next()))

        // Rescan: 3 disappeared, 5 and 6 are new.
        q.sync(listOf(1L, 2L, 4L, 5L, 6L), shuffleMode = false, random = Random(1))
        val rest = (1..3).mapNotNull { q.next() }

        assertTrue("already-shown photos are not repeated", rest.none { it == 1L || it == 2L })
        assertTrue("removed photo is gone", 3L !in rest)
        assertEquals(setOf(4L, 5L, 6L), rest.toSet())
    }

    @Test fun emptyPoolYieldsNull() {
        val q = PlaybackQueue()
        q.sync(emptyList(), shuffleMode = true, random = Random(1))
        assertNull(q.next(Random(1)))
        assertTrue(q.isEmpty())
    }

    @Test fun singlePhotoPoolRepeatsThatPhoto() {
        val q = PlaybackQueue()
        q.sync(listOf(77L), shuffleMode = true, random = Random(1))
        assertEquals(listOf(77L, 77L), listOf(q.next(Random(1)), q.next(Random(1))))
    }

    @Test fun collageMembersAreConsumedFromTheCurrentCycle() {
        val q = PlaybackQueue()
        q.sync(listOf(1L, 2L, 3L, 4L, 5L), shuffleMode = false, random = Random(1))
        assertEquals(1L, q.next())

        q.consume(listOf(2L, 3L))

        assertEquals(listOf(4L, 5L), listOf(q.next(), q.next()))
    }

    /** Regression: reset() once left the pool intact, so the next sync() short-circuited. */
    @Test fun resetRebuildsOnTheNextSync() {
        val q = PlaybackQueue()
        q.sync(listOf(1L, 2L, 3L), shuffleMode = false, random = Random(1))
        q.next(); q.next()

        q.reset()
        q.sync(listOf(1L, 2L, 3L), shuffleMode = false, random = Random(1))

        assertEquals(1L, q.next())
        assertEquals(0, q.cyclesCompleted)
    }
}
