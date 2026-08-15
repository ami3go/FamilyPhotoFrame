package com.example.familyphotoframe.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessStartClassifierTest {
    @Test fun firstObservationHasNoPriorClassification() {
        val result = ProcessStartClassifier.classify(null, null, 1_000_000L, 100_000L)
        assertEquals(ProcessStartKind.FIRST_OBSERVATION, result.kind)
    }

    @Test fun increasingUptimeWithStableBootEpochIsProcessRestart() {
        val result = ProcessStartClassifier.classify(
            previousElapsedRealtimeMs = 500_000L,
            previousEstimatedBootEpochMs = 1_000_000L,
            currentWallClockMs = 2_000_000L,
            currentElapsedRealtimeMs = 1_000_000L,
        )
        assertEquals(ProcessStartKind.PROCESS_RESTART_SAME_BOOT, result.kind)
    }

    @Test fun elapsedRealtimeResetIsDeviceReboot() {
        val result = ProcessStartClassifier.classify(
            previousElapsedRealtimeMs = 20_000_000L,
            previousEstimatedBootEpochMs = 1_000_000L,
            currentWallClockMs = 21_400_000L,
            currentElapsedRealtimeMs = 400_000L,
        )
        assertEquals(ProcessStartKind.DEVICE_REBOOT, result.kind)
    }

    @Test fun changedBootEpochDetectsRebootEvenWhenNewUptimeExceedsOldMarker() {
        val result = ProcessStartClassifier.classify(
            previousElapsedRealtimeMs = 60_000L,
            previousEstimatedBootEpochMs = 1_000_000L,
            currentWallClockMs = 3_000_000L,
            currentElapsedRealtimeMs = 500_000L,
        )
        assertEquals(ProcessStartKind.DEVICE_REBOOT, result.kind)
    }
}
