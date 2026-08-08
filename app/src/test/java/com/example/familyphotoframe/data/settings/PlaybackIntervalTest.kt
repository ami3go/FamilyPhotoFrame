package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackIntervalTest {
    @Test
    fun clamp_enforcesThreeToSixHundredSecondRange() {
        assertEquals(PlaybackInterval.MIN_SECONDS, PlaybackInterval.clamp(-1))
        assertEquals(15, PlaybackInterval.clamp(15))
        assertEquals(PlaybackInterval.MAX_SECONDS, PlaybackInterval.clamp(9_999))
    }

    @Test
    fun fiveSecondButtonsAdjustAndRespectBounds() {
        assertEquals(10, PlaybackInterval.adjust(15, -PlaybackInterval.BUTTON_STEP_SECONDS))
        assertEquals(20, PlaybackInterval.adjust(15, PlaybackInterval.BUTTON_STEP_SECONDS))
        assertEquals(PlaybackInterval.MIN_SECONDS, PlaybackInterval.adjust(3, -5))
        assertEquals(PlaybackInterval.MIN_SECONDS, PlaybackInterval.adjust(4, -5))
        assertEquals(PlaybackInterval.MAX_SECONDS, PlaybackInterval.adjust(598, 5))
    }
}
