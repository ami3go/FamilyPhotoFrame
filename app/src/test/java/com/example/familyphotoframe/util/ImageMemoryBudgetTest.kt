package com.example.familyphotoframe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMemoryBudgetTest {
    @Test fun lowMemoryFrameUsesTenPercentOfHeap() {
        assertEquals(10 * 1024 * 1024, ImageMemoryBudget.bytesForHeap(100L * 1024L * 1024L))
    }

    @Test fun cacheIsBoundedAtBothEnds() {
        assertEquals(8 * 1024 * 1024, ImageMemoryBudget.bytesForHeap(32L * 1024L * 1024L))
        assertEquals(16 * 1024 * 1024, ImageMemoryBudget.bytesForHeap(512L * 1024L * 1024L))
        assertEquals(8 * 1024 * 1024, ImageMemoryBudget.bytesForHeap(0L))
    }

    /**
     * Slide decodes bypass this cache entirely, so on a frame with little heap the
     * ceiling would mostly be reserved for settings thumbnails that re-decode cheaply.
     */
    @Test fun lowTierFramesReserveLess() {
        val heap = 96L * 1024L * 1024L
        val standard = ImageMemoryBudget.bytesForHeap(heap, lowMemoryTier = false)
        val low = ImageMemoryBudget.bytesForHeap(heap, lowMemoryTier = true)
        assertTrue(low < standard)
        // Half the proportion of the same heap, and inside the low-tier bounds.
        assertEquals(standard / 2, low)
        assertTrue(low >= ImageMemoryBudget.LOW_TIER_MIN_BYTES.toInt())
        assertTrue(low <= ImageMemoryBudget.LOW_TIER_MAX_BYTES.toInt())
    }

    @Test fun theLowTierBudgetIsBoundedAtBothEndsToo() {
        assertEquals(
            ImageMemoryBudget.LOW_TIER_MIN_BYTES.toInt(),
            ImageMemoryBudget.bytesForHeap(16L * 1024L * 1024L, lowMemoryTier = true),
        )
        assertEquals(
            ImageMemoryBudget.LOW_TIER_MAX_BYTES.toInt(),
            ImageMemoryBudget.bytesForHeap(512L * 1024L * 1024L, lowMemoryTier = true),
        )
        assertEquals(
            ImageMemoryBudget.LOW_TIER_MIN_BYTES.toInt(),
            ImageMemoryBudget.bytesForHeap(0L, lowMemoryTier = true),
        )
    }
}
