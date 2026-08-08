package com.example.familyphotoframe.slideshow.shuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAvailabilityTrackerTest {
    @Test fun backoffIsBoundedAndRecoveryClearsState() {
        val tracker = SourceAvailabilityTracker()
        val delays = mutableListOf<Long>()
        var now = 1_000L
        repeat(6) {
            val state = tracker.failure("nas", "offline", now)
            delays += state.retryAtEpochMs - now
            now += 1
        }
        assertEquals(listOf(30_000L, 120_000L, 300_000L, 900_000L, 900_000L, 900_000L), delays)
        assertTrue("nas" in tracker.unavailableSourceIds())
        assertFalse(tracker.shouldRetry("nas", now))
        tracker.recovered("nas")
        assertFalse("nas" in tracker.unavailableSourceIds())
    }
}
