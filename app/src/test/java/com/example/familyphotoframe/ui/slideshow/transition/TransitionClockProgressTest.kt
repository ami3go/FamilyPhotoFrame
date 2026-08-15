package com.example.familyphotoframe.ui.slideshow.transition

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionClockProgressTest {
    @Test fun progressUsesRealElapsedTimeAndClamps() {
        assertEquals(0f, TransitionTiming.progressForElapsedNanos(-1L, 900), 0.0001f)
        assertEquals(0.5f, TransitionTiming.progressForElapsedNanos(450_000_000L, 900), 0.0001f)
        assertEquals(1f, TransitionTiming.progressForElapsedNanos(900_000_000L, 900), 0.0001f)
        assertEquals(1f, TransitionTiming.progressForElapsedNanos(2_000_000_000L, 900), 0.0001f)
    }
}
