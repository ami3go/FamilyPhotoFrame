package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySelfRecoveryPolicyTest {
    @Test fun pinnedPostOomHeapGetsGcThenOneRestartRequest() {
        val oomAt = 10_000L
        val start = MemorySelfRecoveryPolicy.evaluate(
            MemorySelfRecoveryState(), PlaybackMemoryLevel.CIRCUIT_OPEN,
            1L, oomAt, 99, oomAt,
        )
        val gc = MemorySelfRecoveryPolicy.evaluate(
            start.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            1L, oomAt, 99, oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS,
        )
        val restart = MemorySelfRecoveryPolicy.evaluate(
            gc.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            1L, oomAt, 99, oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS +
                MemorySelfRecoveryPolicy.POST_GC_VERIFY_MS,
        )
        val again = MemorySelfRecoveryPolicy.evaluate(
            restart.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            1L, oomAt, 99, oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS +
                MemorySelfRecoveryPolicy.POST_GC_VERIFY_MS + 10_000L,
        )

        assertEquals(MemorySelfRecoveryAction.NONE, start.action)
        assertEquals(MemorySelfRecoveryAction.REQUEST_GC, gc.action)
        assertEquals(MemorySelfRecoveryAction.RESTART_PROCESS, restart.action)
        assertEquals(MemorySelfRecoveryAction.NONE, again.action)
    }

    @Test fun recoveryOrStaleOomCancelsWatchdog() {
        val state = MemorySelfRecoveryState(highPressureSinceElapsedMs = 10_000L)
        val recovered = MemorySelfRecoveryPolicy.evaluate(
            state, PlaybackMemoryLevel.RECOVERY, 1L, 10_000L, 90, 80_000L,
        )
        val stale = MemorySelfRecoveryPolicy.evaluate(
            state, PlaybackMemoryLevel.CIRCUIT_OPEN, 1L, 10_000L, 99,
            10_000L + MemorySelfRecoveryPolicy.OOM_ELIGIBILITY_WINDOW_MS + 1L,
        )

        assertEquals(MemorySelfRecoveryState(), recovered.state)
        assertEquals(MemorySelfRecoveryAction.NONE, recovered.action)
        assertEquals(MemorySelfRecoveryAction.NONE, stale.action)
    }

    @Test fun gcIsRateLimitedAcrossSeparateOomRecoveryAttempts() {
        val firstOomAt = 10_000L
        val started = MemorySelfRecoveryPolicy.evaluate(
            MemorySelfRecoveryState(), PlaybackMemoryLevel.CIRCUIT_OPEN,
            1L, firstOomAt, 99, firstOomAt,
        )
        val firstGcAt = firstOomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS
        val firstGc = MemorySelfRecoveryPolicy.evaluate(
            started.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            1L, firstOomAt, 99, firstGcAt,
        )
        val reset = MemorySelfRecoveryPolicy.evaluate(
            firstGc.state, PlaybackMemoryLevel.RECOVERY,
            1L, firstOomAt, 50, firstGcAt + 10_000L,
        )
        val secondOomAt = firstGcAt + 30_000L
        val secondStarted = MemorySelfRecoveryPolicy.evaluate(
            reset.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            2L, secondOomAt, 99, secondOomAt,
        )
        val suppressed = MemorySelfRecoveryPolicy.evaluate(
            secondStarted.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            2L, secondOomAt, 99,
            secondOomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS,
        )
        val allowed = MemorySelfRecoveryPolicy.evaluate(
            suppressed.state, PlaybackMemoryLevel.CIRCUIT_OPEN,
            2L, secondOomAt, 99,
            firstGcAt + MemorySelfRecoveryPolicy.GC_MIN_INTERVAL_MS,
        )

        assertEquals(MemorySelfRecoveryAction.REQUEST_GC, firstGc.action)
        assertEquals(firstGcAt, reset.state.lastGcRequestedAtElapsedMs)
        assertEquals(MemorySelfRecoveryAction.NONE, suppressed.action)
        assertEquals(MemorySelfRecoveryAction.REQUEST_GC, allowed.action)
    }

    @Test fun sustainedNativeProcessPressureGetsOneGcThenControlledRestart() {
        val startedAt = 1_000L
        val start = MemorySelfRecoveryPolicy.evaluate(
            previous = MemorySelfRecoveryState(),
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            oomCount = 0L,
            lastOomElapsedMs = 0L,
            pressurePercent = 40,
            nowElapsedMs = startedAt,
            processPressurePercent = MemorySelfRecoveryPolicy.NATIVE_RESTART_PROCESS_PERCENT,
            nativeGrowthStreak = PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK,
            nativeCriticalLatched = true,
        )
        val gc = MemorySelfRecoveryPolicy.evaluate(
            previous = start.state,
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            oomCount = 0L,
            lastOomElapsedMs = 0L,
            pressurePercent = 40,
            nowElapsedMs = startedAt + MemorySelfRecoveryPolicy.NATIVE_SUSTAINED_PRESSURE_BEFORE_GC_MS,
            processPressurePercent = MemorySelfRecoveryPolicy.NATIVE_RESTART_PROCESS_PERCENT,
            nativeGrowthStreak = PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK,
            nativeCriticalLatched = true,
        )
        val restart = MemorySelfRecoveryPolicy.evaluate(
            previous = gc.state,
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            oomCount = 0L,
            lastOomElapsedMs = 0L,
            pressurePercent = 40,
            nowElapsedMs = startedAt + MemorySelfRecoveryPolicy.NATIVE_SUSTAINED_PRESSURE_BEFORE_GC_MS +
                MemorySelfRecoveryPolicy.NATIVE_POST_GC_VERIFY_MS,
            processPressurePercent = MemorySelfRecoveryPolicy.NATIVE_RESTART_PROCESS_PERCENT,
            nativeGrowthStreak = PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK,
            nativeCriticalLatched = true,
        )

        assertEquals(MemorySelfRecoveryAction.NONE, start.action)
        assertEquals(
            MemorySelfRecoveryTrigger.SUSTAINED_NATIVE_PROCESS_PRESSURE,
            start.trigger,
        )
        assertEquals(MemorySelfRecoveryAction.REQUEST_GC, gc.action)
        assertEquals(MemorySelfRecoveryAction.RESTART_PROCESS, restart.action)
    }

    @Test fun nativeRecoveryCancelsAsSoonAsGrowthPlateaus() {
        val active = MemorySelfRecoveryPolicy.evaluate(
            previous = MemorySelfRecoveryState(),
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            oomCount = 0L,
            lastOomElapsedMs = 0L,
            pressurePercent = 40,
            nowElapsedMs = 1_000L,
            processPressurePercent = 140,
            nativeGrowthStreak = PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK,
            nativeCriticalLatched = true,
        )
        val plateau = MemorySelfRecoveryPolicy.evaluate(
            previous = active.state,
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            oomCount = 0L,
            lastOomElapsedMs = 0L,
            pressurePercent = 40,
            nowElapsedMs = 10_000L,
            processPressurePercent = 140,
            nativeGrowthStreak = 0,
            nativeCriticalLatched = true,
        )

        assertEquals(MemorySelfRecoveryAction.NONE, plateau.action)
        assertEquals(MemorySelfRecoveryTrigger.NONE, plateau.trigger)
        assertEquals(MemorySelfRecoveryTrigger.NONE, plateau.state.trigger)
    }
}
