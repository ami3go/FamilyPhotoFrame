package com.example.familyphotoframe.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMemoryTrendTrackerTest {
    @Test fun requiresOneHourBeforeReportingAttributionRate() {
        val tracker = NativeMemoryTrendTracker()
        val minute = 60_000L

        tracker.record(0L, 10_000L)
        tracker.record(15L * minute, 11_800L)
        tracker.record(30L * minute, 13_600L)
        tracker.record(45L * minute, 15_400L)
        val beforeWindow = tracker.currentTrend()
        assertFalse(beforeWindow.ready)
        assertEquals(0, beforeWindow.rateKbPerMin)

        val trend = tracker.record(60L * minute, 17_200L)
        assertTrue(trend.ready)
        assertEquals(5, trend.sampleCount)
        assertEquals(60L * minute, trend.observationMs)
        assertEquals(7_200L, trend.growthKb)
        assertEquals(120, trend.rateKbPerMin)
    }

    @Test fun keepsOnlyBoundedSixHourEvidence() {
        val tracker = NativeMemoryTrendTracker()
        val hour = 60L * 60_000L
        var trend = NativeMemoryTrendTracker.Trend()

        for (index in 0L..8L) {
            trend = tracker.record(
                elapsedMs = index * hour,
                nativePssKb = 10_000L + index * 1_024L,
            )
        }

        assertTrue(trend.ready)
        assertEquals(7, trend.sampleCount)
        assertEquals(6L * hour, trend.observationMs)
        assertEquals(6_144L, trend.growthKb)
        assertEquals(17, trend.rateKbPerMin)
    }

    @Test fun clockRollbackStartsANewTrendWindow() {
        val tracker = NativeMemoryTrendTracker(
            minObservationMs = 1L,
            maxObservationMs = 10_000L,
            maxSamples = 16,
        )

        tracker.record(1_000L, 1_000L)
        assertTrue(tracker.record(2_000L, 2_000L).ready)

        val reset = tracker.record(500L, 2_200L)
        assertFalse(reset.ready)
        assertEquals(1, reset.sampleCount)
        assertEquals(0L, reset.observationMs)
        assertEquals(2_200L, reset.baselinePssKb)
        assertEquals(2_200L, reset.currentPssKb)
        assertEquals(0L, reset.growthKb)
        assertEquals(0, reset.rateKbPerMin)
    }

    @Test fun regressionReportsPlateauDespiteEndpointNoise() {
        val tracker = NativeMemoryTrendTracker(
            minObservationMs = 4L * 60_000L,
            maxObservationMs = 60L * 60_000L,
            maxSamples = 32,
        )
        val minute = 60_000L
        val readings = listOf(10_000L, 10_120L, 9_940L, 10_080L, 9_990L)
        var trend = NativeMemoryTrendTracker.Trend()

        readings.forEachIndexed { index, pssKb ->
            trend = tracker.record(index.toLong() * minute, pssKb)
        }

        assertTrue(trend.ready)
        assertEquals(-10L, trend.growthKb)
        assertEquals(-6, trend.rateKbPerMin)
    }
}
