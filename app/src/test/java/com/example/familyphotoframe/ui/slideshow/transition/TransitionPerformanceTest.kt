package com.example.familyphotoframe.ui.slideshow.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionPerformanceTest {
    @Test
    fun samplerCountsSlowAndMaximumFrames() {
        val sampler = TransitionFrameSampler()
        sampler.record(0L)
        sampler.record(16_000_000L)
        sampler.record(56_000_000L)
        sampler.record(72_000_000L)
        val summary = sampler.summary()
        assertEquals(4, summary.frameCount)
        assertEquals(1, summary.slowFrameCount)
        assertEquals(40L, summary.maximumFrameMs)
        assertTrue(summary.isPerformanceWarning)
    }

    @Test
    fun controllerEntersAfterThreeOfFiveAndExitsAfterCooldown() {
        val controller = TransitionPerformanceController()
        val bad = TransitionFrameMetrics(10, 3, 50_000_000L)
        val good = TransitionFrameMetrics(10, 0, 16_000_000L)
        val samples = listOf(bad, good, bad, good, bad)
        samples.dropLast(1).forEach {
            assertEquals(TransitionPerformanceStateChange.NONE, controller.record(it).stateChange)
        }
        assertEquals(
            TransitionPerformanceStateChange.ENTERED_LOW_PERFORMANCE,
            controller.record(samples.last()).stateChange,
        )
        assertTrue(controller.isLowPerformanceMode)
        repeat(19) { controller.record(good) }
        assertTrue(controller.isLowPerformanceMode)
        assertEquals(
            TransitionPerformanceStateChange.EXITED_LOW_PERFORMANCE,
            controller.record(good).stateChange,
        )
        assertFalse(controller.isLowPerformanceMode)
    }
}
