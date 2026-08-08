package com.example.familyphotoframe.util

import org.junit.Assert.assertEquals
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
}
