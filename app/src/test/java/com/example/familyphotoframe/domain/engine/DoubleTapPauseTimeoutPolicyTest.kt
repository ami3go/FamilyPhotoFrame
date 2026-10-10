package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapPauseTimeoutPolicyTest {
    @Test
    fun canonicalOptionsIncludeNeverAndRequestedMinuteChoices() {
        assertEquals(listOf(0, 1, 5, 10, 15, 30, 60), DoubleTapPauseTimeoutPolicy.optionsMinutes)
        assertEquals(5, DoubleTapPauseTimeoutPolicy.normalize(7))
        assertEquals(15 * 60_000L, DoubleTapPauseTimeoutPolicy.delayMillis(15))
    }

    @Test
    fun onlyDoubleTapPauseArmsTimer() {
        val state = DoubleTapPauseTimeoutState()

        assertNull(state.onToggle(nowPaused = true, source = "key", configuredMinutes = 5))
        assertNull(state.onToggle(nowPaused = true, source = "double_tap", configuredMinutes = 0))
        assertNull(state.onToggle(nowPaused = false, source = "double_tap", configuredMinutes = 5))
        assertNotNull(state.onToggle(nowPaused = true, source = "double_tap", configuredMinutes = 5))
    }

    @Test
    fun manualToggleInvalidatesElapsedTimerGeneration() {
        val state = DoubleTapPauseTimeoutState()
        val first = state.onToggle(true, "double_tap", 1)!!
        state.onToggle(nowPaused = false, source = "key", configuredMinutes = 1)

        assertFalse(state.markElapsed(first.generation))
        assertFalse(state.consumePending(first.generation, paused = true))
    }

    @Test
    fun currentElapsedTimerResumesExactlyOnce() {
        val state = DoubleTapPauseTimeoutState()
        val arm = state.onToggle(true, "double_tap", 10)!!

        assertTrue(state.markElapsed(arm.generation))
        assertTrue(state.consumePending(arm.generation, paused = true))
        assertFalse(state.consumePending(arm.generation, paused = true))
    }
}
