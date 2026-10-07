package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPoolProbePolicyTest {
    @Test
    fun `fallback-only configuration does not probe stale primary history`() {
        assertFalse(PlaybackPoolProbePolicy.shouldProbePrimary(configuredPrimarySourceCount = 0))
    }

    @Test
    fun `healthy or stale-cache primary remains eligible`() {
        assertTrue(PlaybackPoolProbePolicy.shouldProbePrimary(configuredPrimarySourceCount = 1))
        assertTrue(PlaybackPoolProbePolicy.shouldProbePrimary(configuredPrimarySourceCount = 3))
    }

    @Test
    fun `invalid negative count is treated as empty`() {
        assertFalse(PlaybackPoolProbePolicy.shouldProbePrimary(configuredPrimarySourceCount = -1))
    }
}
