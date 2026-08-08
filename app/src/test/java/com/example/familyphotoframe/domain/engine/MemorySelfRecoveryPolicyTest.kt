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
}
