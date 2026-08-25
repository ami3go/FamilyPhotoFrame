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

    @Test fun severeSystemPressureDegradesPlaybackWithoutBlockingSelectedDecode() {
        val baseline = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap / 2L, heap, 1_000L,
        )
        val critical = PlaybackMemoryPolicy.systemPressure(
            baseline,
            nowElapsedMs = 2_000L,
            severe = true,
        )
        val repeated = PlaybackMemoryPolicy.systemPressure(
            critical,
            nowElapsedMs = 3_000L,
            severe = true,
        )

        assertEquals(PlaybackMemoryLevel.CRITICAL, critical.level)
        assertTrue(critical.allowSelectedDecode)
        assertFalse(critical.allowNextPreload)
        assertEquals(PlaybackMemoryPressureSource.PLATFORM_CALLBACK, critical.pressureSource)
        assertEquals(critical.decisionVersion, repeated.decisionVersion)
        assertTrue(repeated.externalCriticalUntilElapsedMs > critical.externalCriticalUntilElapsedMs)
    }

    @Test fun externalCriticalPressureDecaysToGuardedThenNormalWithoutChurn() {
        val baseline = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap / 2L, heap, 1_000L,
        )
        val signalled = PlaybackMemoryPolicy.systemPressure(baseline, 2_000L, severe = true)
        val low = PlaybackMemoryPolicy.sample(signalled, heap / 2L, heap, 3_000L)
        val criticalHeld = PlaybackMemoryPolicy.sample(
            low,
            heap / 2L,
            heap,
            2_000L + PlaybackMemoryPolicy.EXTERNAL_CRITICAL_HOLD_MS - 1L,
        )
        val guarded = PlaybackMemoryPolicy.sample(
            criticalHeld,
            heap / 2L,
            heap,
            2_000L + PlaybackMemoryPolicy.EXTERNAL_CRITICAL_HOLD_MS,
        )
        val normal = PlaybackMemoryPolicy.sample(
            guarded,
            heap / 2L,
            heap,
            2_000L + PlaybackMemoryPolicy.EXTERNAL_GUARDED_HOLD_MS,
        )

        assertEquals(PlaybackMemoryLevel.CRITICAL, criticalHeld.level)
        assertEquals(PlaybackMemoryLevel.GUARDED, guarded.level)
        assertEquals(PlaybackMemoryLevel.NORMAL, normal.level)
    }

    @Test fun lowMemoryCallbackStormChangesThePreparationDecisionOnlyOnce() {
        val baseline = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap / 5L, heap, 1_000L,
        )
        var state = baseline
        var now = 1_000L
        repeat(45) {
            now += 30_000L
            state = PlaybackMemoryPolicy.systemPressure(state, now, severe = true)
            state = PlaybackMemoryPolicy.sample(state, heap / 5L, heap, now + 10_000L)
        }

        assertEquals(PlaybackMemoryLevel.CRITICAL, state.level)
        assertEquals(baseline.decisionVersion + 1L, state.decisionVersion)
        assertTrue(state.allowSelectedDecode)
    }

    @Test fun freshPssAndSystemHeadroomCanDriveProtectionWhileJavaHeapIsLow() {
        val budget = 100L * 1024L * 1024L
        val baseline = PlaybackMemoryState(processMemoryBudgetBytes = budget)
        val pssCritical = PlaybackMemoryPolicy.sample(
            previous = baseline,
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = 1_000L,
            processPssBytes = budget * 125L / 100L,
            systemAvailBytes = 500L * 1024L * 1024L,
            systemThresholdBytes = 100L * 1024L * 1024L,
            systemLowMemory = false,
        )
        val systemCritical = PlaybackMemoryPolicy.sample(
            previous = baseline,
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = 1_000L,
            processPssBytes = budget / 2L,
            systemAvailBytes = 100L * 1024L * 1024L,
            systemThresholdBytes = 100L * 1024L * 1024L,
            systemLowMemory = false,
        )
        val systemGuarded = PlaybackMemoryPolicy.sample(
            previous = baseline,
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = 1_000L,
            processPssBytes = budget / 2L,
            systemAvailBytes = 200L * 1024L * 1024L,
            systemThresholdBytes = 100L * 1024L * 1024L,
            systemLowMemory = false,
        )

        assertEquals(PlaybackMemoryLevel.CRITICAL, pssCritical.level)
        assertEquals(PlaybackMemoryPressureSource.PROCESS_PSS, pssCritical.pressureSource)
        assertEquals(125, pssCritical.processPressurePercent)
        assertEquals(PlaybackMemoryLevel.CRITICAL, systemCritical.level)
        assertEquals(PlaybackMemoryPressureSource.SYSTEM_HEADROOM, systemCritical.pressureSource)
        assertEquals(PlaybackMemoryLevel.GUARDED, systemGuarded.level)
    }

    @Test fun repeatedRenderTimeoutsDegradeWithoutOpeningTheDecodeCircuit() {
        var state = PlaybackMemoryPolicy.sample(
            PlaybackMemoryState(), heap / 2L, heap, 1_000L,
        )
        repeat(PlaybackMemoryPolicy.RENDER_TIMEOUT_GUARDED_COUNT) { index ->
            state = PlaybackMemoryPolicy.renderAckTimeout(state, 10_000L + index * 10_000L)
        }
        state = PlaybackMemoryPolicy.sample(state, heap / 2L, heap, 45_000L)
        assertEquals(PlaybackMemoryLevel.GUARDED, state.level)
        assertEquals(PlaybackMemoryPressureSource.RENDER_ACK_TIMEOUTS, state.pressureSource)
        assertTrue(state.allowSelectedDecode)
        val guardedUntil = state.externalGuardedUntilElapsedMs
        state = PlaybackMemoryPolicy.sample(state, heap / 2L, heap, 46_000L)
        assertEquals(guardedUntil, state.externalGuardedUntilElapsedMs)

        repeat(
            PlaybackMemoryPolicy.RENDER_TIMEOUT_CRITICAL_COUNT -
                PlaybackMemoryPolicy.RENDER_TIMEOUT_GUARDED_COUNT
        ) { index ->
            state = PlaybackMemoryPolicy.renderAckTimeout(state, 50_000L + index * 10_000L)
        }
        state = PlaybackMemoryPolicy.sample(state, heap / 2L, heap, 85_000L)
        assertEquals(PlaybackMemoryLevel.CRITICAL, state.level)
        assertEquals(PlaybackMemoryPressureSource.RENDER_ACK_TIMEOUTS, state.pressureSource)
        assertTrue(state.allowSelectedDecode)
    }

    @Test fun overdueOrExcessTransfersDriveStableProtection() {
        val overdue = PlaybackMemoryPolicy.sample(
            previous = PlaybackMemoryState(),
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = 1_000L,
            activeMediaTransfers = 1,
            oldestMediaTransferAgeMs = PlaybackMemoryPolicy.TRANSFER_GUARDED_AGE_MS,
        )
        val excess = PlaybackMemoryPolicy.sample(
            previous = PlaybackMemoryState(),
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = 1_000L,
            activeMediaTransfers = PlaybackMemoryPolicy.MAX_EXPECTED_ACTIVE_TRANSFERS + 1,
            oldestMediaTransferAgeMs = 0L,
        )

        assertEquals(PlaybackMemoryLevel.GUARDED, overdue.level)
        assertEquals(PlaybackMemoryPressureSource.OVERDUE_TRANSFER, overdue.pressureSource)
        assertEquals(PlaybackMemoryLevel.CRITICAL, excess.level)
        assertTrue(excess.allowSelectedDecode)
    }

    @Test fun repeatedNativePssGrowthWindowsEscalateAndPlateauResetsTheStreak() {
        val baseNative = 40L * 1024L * 1024L
        var now = 1_000L
        var state = PlaybackMemoryPolicy.sample(
            previous = PlaybackMemoryState(),
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = now,
            nativePssBytes = baseNative,
        )
        repeat(PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK) { index ->
            now += PlaybackMemoryPolicy.NATIVE_GROWTH_MIN_WINDOW_MS
            state = PlaybackMemoryPolicy.sample(
                previous = state,
                heapUsedBytes = heap / 2L,
                heapMaxBytes = heap,
                nowElapsedMs = now,
                nativePssBytes = baseNative + (index.toLong() + 1L) * 1024L * 1024L,
            )
        }

        assertEquals(PlaybackMemoryLevel.CRITICAL, state.level)
        assertEquals(PlaybackMemoryPressureSource.NATIVE_PSS_GROWTH, state.pressureSource)
        assertEquals(PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK, state.nativeGrowthStreak)
        assertTrue(state.nativeGrowthRateKbPerMin > 0)

        state = PlaybackMemoryPolicy.sample(
            previous = state,
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = now + PlaybackMemoryPolicy.NATIVE_GROWTH_MAX_WINDOW_MS,
            nativePssBytes = state.nativePssBytes,
        )
        assertEquals(0, state.nativeGrowthStreak)
    }

    @Test fun lowMemoryTierStartsWithABoundedEconomyProfile() {
        val state = PlaybackMemoryState(lowMemoryTier = true)

        assertEquals(PlaybackMemoryLevel.NORMAL, state.level)
        assertFalse(state.allowNextPreload)
        assertFalse(state.allowSoftFocus)
        assertFalse(state.allowBlurredBackdrop)
        assertFalse(state.allowWebPreview)
        assertTrue(state.forceSimpleTransition)
        assertEquals(2, state.maxCollagePhotos)
        assertTrue(state.decodeScale < 1f)
        assertTrue(state.allowSelectedDecode)
    }
}
