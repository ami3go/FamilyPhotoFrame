package com.example.familyphotoframe.slideshow.shuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleCycleGeneratorTest {
    @Test fun generatedCycle_containsEveryItemExactlyOnce() {
        val input = (1..100).map { "folder-$it" }
        val order = ShuffleCycleGenerator.generate(input, previousLast = null, FixedSeedShuffleRandom(42))
        assertEquals(input.size, order.size)
        assertEquals(input.toSet(), order.toSet())
    }

    @Test fun boundaryRepeat_isAvoidedWhenPossible() {
        val input = listOf("a", "b", "c")
        repeat(20) { seed ->
            val order = ShuffleCycleGenerator.generate(input, "a", FixedSeedShuffleRandom(seed.toLong()))
            assertNotEquals("a", order.first())
        }
    }

    @Test fun oneItemCycle_isPredictable() {
        assertEquals(listOf("only"), ShuffleCycleGenerator.generate(listOf("only"), "only", FixedSeedShuffleRandom(1)))
    }

    @Test fun mergeRemaining_isIdempotentWhenNothingIsNew() {
        val current = listOf("b", "a", "c")
        val once = ShuffleCycleGenerator.mergeRemaining(current, emptyList(), FixedSeedShuffleRandom(7))
        val twice = ShuffleCycleGenerator.mergeRemaining(once, emptyList(), FixedSeedShuffleRandom(8))
        assertEquals(current, once)
        assertEquals(once, twice)
    }

    @Test fun mergeRemaining_insertsNewItemsOnceWithoutReorderingExisting() {
        val existing = listOf("a", "b", "c")
        val merged = ShuffleCycleGenerator.mergeRemaining(existing, listOf("d", "e", "d"), FixedSeedShuffleRandom(4))
        assertEquals(5, merged.size)
        assertEquals(5, merged.toSet().size)
        assertTrue(merged.indexOf("a") < merged.indexOf("b"))
        assertTrue(merged.indexOf("b") < merged.indexOf("c"))
    }
}
