package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMemoryPolicyTest {
    // Above the low-heap tier and divisible cleanly at the percentage boundaries below.
    private val heap = 200L * 1024L * 1024L
    private val lowHeap = 100L * 1024L * 1024L

    @Test fun guardedModeStartsAtEightyFivePercentAndDisablesPreloadAndThreePhotoFrames() {
        val state = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap * 85L / 100L, heap, 1_000L,
        )
        assertEquals(PlaybackMemoryLevel.GUARDED, state.level)
        assertFalse(state.allowNextPreload)
        assertEquals(2, state.maxCollagePhotos)
        assertTrue(state.decodeScale < 1f)
    }

    @Test fun criticalModeUsesSinglePhotoPreparation() {
        val state = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap * 92L / 100L, heap, 1_000L,
        )
        assertEquals(PlaybackMemoryLevel.CRITICAL, state.level)
        assertEquals(1, state.maxCollagePhotos)
        assertFalse(state.allowSoftFocus)
        assertFalse(state.allowWebPreview)
    }

    @Test fun lowHeapDevicesEnterProtectionWithAbsoluteHeadroomRemaining() {
        val guarded = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), lowHeap * 75L / 100L, lowHeap, 1_000L,
        )
        val critical = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), lowHeap * 85L / 100L, lowHeap, 1_000L,
        )

        assertEquals(PlaybackMemoryLevel.GUARDED, guarded.level)
        assertFalse(guarded.allowNextPreload)
        assertEquals(2, guarded.maxCollagePhotos)
        assertEquals(PlaybackMemoryLevel.CRITICAL, critical.level)
        assertEquals(1, critical.maxCollagePhotos)
        assertFalse(critical.allowWebPreview)
    }

    @Test fun lowHeapThresholdDoesNotApplyAboveOneHundredTwentyEightMiB() {
        val mediumHeap = 129L * 1024L * 1024L
        val state = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), mediumHeap * 84L / 100L, mediumHeap, 1_000L,
        )
        assertEquals(PlaybackMemoryLevel.NORMAL, state.level)
    }

    @Test fun firstOomOpensCircuitAndRepeatedOomsBackOff() {
        val first = PlaybackMemoryPolicy.decodeOom(PlaybackMemoryState(), 10_000L)
        assertEquals(PlaybackMemoryLevel.CIRCUIT_OPEN, first.level)
        assertFalse(first.allowSelectedDecode)
        assertEquals(60_000L, first.circuitRemainingMs(10_000L))

        val second = PlaybackMemoryPolicy.decodeOom(first, 20_000L)
        assertEquals(120_000L, second.circuitRemainingMs(20_000L))
        assertEquals(2, second.oomStreak)
        assertEquals(2L, second.totalOomCount)
    }

    @Test fun circuitTransitionsToConservativeRecoveryBeforeNormal() {
        val opened = PlaybackMemoryPolicy.decodeOom(PlaybackMemoryState(), 10_000L)
        val criticallyFull = PlaybackMemoryPolicy.sample(
            opened, heap * 93L / 100L, heap, 80_000L,
        )
        assertEquals(PlaybackMemoryLevel.CIRCUIT_OPEN, criticallyFull.level)
        val afterCircuit = PlaybackMemoryPolicy.sample(
            opened, heap * 90L / 100L, heap, 80_000L,
        )
        assertEquals(PlaybackMemoryLevel.RECOVERY, afterCircuit.level)
        assertTrue(afterCircuit.allowSelectedDecode)
        assertFalse(afterCircuit.allowNextPreload)
        assertEquals(1, afterCircuit.maxCollagePhotos)

        val recovered = PlaybackMemoryPolicy.sample(
            afterCircuit, heap / 2L, heap, 700_001L,
        )
        assertEquals(PlaybackMemoryLevel.NORMAL, recovered.level)
        assertTrue(recovered.allowNextPreload)
        assertEquals(0, recovered.oomStreak)
    }

    @Test fun guardedModeHasExitHysteresis() {
        val guarded = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap * 86L / 100L, heap, 1_000L,
        )
        val stillGuarded = PlaybackMemoryPolicy.sample(
            guarded, heap * 80L / 100L, heap, 2_000L,
        )
        val normal = PlaybackMemoryPolicy.sample(
            stillGuarded, heap * 69L / 100L, heap, 3_000L,
        )
        val held = PlaybackMemoryPolicy.sample(
            normal, heap * 69L / 100L, heap, 3_000L + PlaybackMemoryPolicy.GUARDED_RECOVERY_HOLD_MS - 1L,
        )
        val recovered = PlaybackMemoryPolicy.sample(
            held, heap * 69L / 100L, heap, 3_000L + PlaybackMemoryPolicy.GUARDED_RECOVERY_HOLD_MS,
        )
        assertEquals(PlaybackMemoryLevel.GUARDED, stillGuarded.level)
        assertEquals(PlaybackMemoryLevel.GUARDED, normal.level)
        assertEquals(PlaybackMemoryLevel.GUARDED, held.level)
        assertEquals(PlaybackMemoryLevel.NORMAL, recovered.level)
    }

    @Test fun criticalRequiresFiveMinutesBelowEightyBeforeRelaxing() {
        val critical = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap * 93L / 100L, heap, 1_000L,
        )
        val low = PlaybackMemoryPolicy.sample(critical, heap * 75L / 100L, heap, 2_000L)
        val early = PlaybackMemoryPolicy.sample(
            low, heap * 75L / 100L, heap, 2_000L + PlaybackMemoryPolicy.CRITICAL_RECOVERY_HOLD_MS - 1L,
        )
        val relaxed = PlaybackMemoryPolicy.sample(
            early, heap * 75L / 100L, heap, 2_000L + PlaybackMemoryPolicy.CRITICAL_RECOVERY_HOLD_MS,
        )
        assertEquals(PlaybackMemoryLevel.CRITICAL, early.level)
        assertEquals(PlaybackMemoryLevel.GUARDED, relaxed.level)
        assertFalse(relaxed.allowNextPreload)
    }

    @Test fun runningLowSignalCannotDowngradeAnOpenOomCircuit() {
        val opened = PlaybackMemoryPolicy.decodeOom(PlaybackMemoryState(), 10_000L)
        val signalled = PlaybackMemoryPolicy.systemPressure(
            opened,
            nowElapsedMs = 20_000L,
            severe = false,
        )
        assertEquals(PlaybackMemoryLevel.CIRCUIT_OPEN, signalled.level)
        assertFalse(signalled.allowSelectedDecode)
        assertEquals(opened.circuitOpenUntilElapsedMs, signalled.circuitOpenUntilElapsedMs)
    }
}
