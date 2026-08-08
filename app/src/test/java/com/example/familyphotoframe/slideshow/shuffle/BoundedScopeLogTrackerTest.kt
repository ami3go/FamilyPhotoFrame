package com.example.familyphotoframe.slideshow.shuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedScopeLogTrackerTest {
    @Test fun duplicateMarksAreSuppressedAndDeletedKeysCanReturn() {
        val tracker = BoundedScopeLogTracker(4)
        assertTrue(tracker.mark("scope"))
        assertFalse(tracker.mark("scope"))
        tracker.forget("scope")
        assertTrue(tracker.mark("scope"))
    }

    @Test fun trackingRemainsBoundedAcrossHundredsOfScopes() {
        val tracker = BoundedScopeLogTracker(16)
        repeat(500) { tracker.mark("scope-$it") }
        assertEquals(16, tracker.size)
    }
}
