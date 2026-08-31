package com.example.familyphotoframe.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOverrideWakePolicyTest {

    @Test fun timedOverrideWakesAtItsOwnExpiryInsteadOfTheWatcherCadence() {
        assertEquals(
            5 * 60_000L,
            PlaylistOverrideWakePolicy.nextWakeDelayMs(
                nowEpochMs = 1_000L,
                scheduleDelayMs = 15 * 60_000L,
                overrideUntilEpochMs = 301_000L,
            )
        )
    }

    @Test fun ordinaryScheduleBoundaryStillWinsWhenItIsSooner() {
        assertEquals(
            60_000L,
            PlaylistOverrideWakePolicy.nextWakeDelayMs(
                nowEpochMs = 1_000L,
                scheduleDelayMs = 60_000L,
                overrideUntilEpochMs = 301_000L,
            )
        )
    }

    @Test fun expiredAndPermanentOverridesDoNotBusyLoop() {
        assertEquals(
            60_000L,
            PlaylistOverrideWakePolicy.nextWakeDelayMs(1_000L, 60_000L, 1_000L)
        )
        assertEquals(
            60_000L,
            PlaylistOverrideWakePolicy.nextWakeDelayMs(1_000L, 60_000L, Long.MAX_VALUE)
        )
        assertEquals(
            1_000L,
            PlaylistOverrideWakePolicy.nextWakeDelayMs(1_000L, 60_000L, 1_001L)
        )
    }
}
