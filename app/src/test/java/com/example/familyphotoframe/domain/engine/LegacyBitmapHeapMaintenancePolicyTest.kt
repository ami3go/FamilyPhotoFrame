package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyBitmapHeapMaintenancePolicyTest {
    private val mib = 1024L * 1024L

    @Test fun sustainedLegacyBitmapChurnRequestsOneGcAtSafeBoundary() {
        var state = LegacyBitmapHeapMaintenanceState()
        state = sample(state, now = 0L, heapMiB = 24, releasedMiB = 0).state
        state = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.BASELINE_START_MS,
            heapMiB = 24,
            releasedMiB = 80,
        ).state
        state = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS,
            heapMiB = 29,
            releasedMiB = 320,
        ).state
        state = sample(state, now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 60_000L,
            heapMiB = 30, releasedMiB = 330).state
        val request = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 120_000L,
            heapMiB = 31,
            releasedMiB = 340,
        )

        assertEquals(LegacyBitmapHeapMaintenanceAction.REQUEST_GC, request.action)
        assertEquals(7 * mib, request.heapGrowthBytes)
        assertEquals(340 * mib, request.retiredBytesSinceRequest)
    }

    @Test fun requestWaitsForRetirementBoundaryWithoutLosingHighEvidence() {
        var state = seededHighState()
        val blocked = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 120_000L,
            heapMiB = 31,
            releasedMiB = 340,
            pendingDisposals = 1,
        )
        state = blocked.state
        val safe = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 180_000L,
            heapMiB = 31,
            releasedMiB = 350,
        )

        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, blocked.action)
        assertEquals(LegacyBitmapHeapMaintenanceAction.REQUEST_GC, safe.action)
    }

    @Test fun requestsAreLimitedToOncePerHour() {
        val first = sample(
            seededHighState(),
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 120_000L,
            heapMiB = 31,
            releasedMiB = 340,
        )
        var state = first.state
        repeat(3) { index ->
            state = sample(
                state,
                now = first.state.lastRequestElapsedMs + (index + 1L) * 60_000L,
                heapMiB = 32,
                releasedMiB = 700,
            ).state
        }
        val limited = sample(
            state,
            now = first.state.lastRequestElapsedMs + 59L * 60_000L,
            heapMiB = 32,
            releasedMiB = 700,
        )
        val allowed = sample(
            limited.state,
            now = first.state.lastRequestElapsedMs + 60L * 60_000L,
            heapMiB = 32,
            releasedMiB = 700,
        )

        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, limited.action)
        assertEquals(LegacyBitmapHeapMaintenanceAction.REQUEST_GC, allowed.action)
    }

    @Test fun modernStandardAndOomRecoveryPathsRemainUntouched() {
        val state = seededHighState()
        val modern = sample(state, now = 3L * 60L * 60_000L, heapMiB = 32,
            releasedMiB = 700, sdkInt = 26)
        val standard = sample(state, now = 3L * 60L * 60_000L, heapMiB = 32,
            releasedMiB = 700, lowMemoryTier = false)
        val oom = sample(state, now = 3L * 60L * 60_000L, heapMiB = 32,
            releasedMiB = 700, oomCount = 1)

        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, modern.action)
        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, standard.action)
        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, oom.action)
    }

    private fun seededHighState(): LegacyBitmapHeapMaintenanceState {
        var state = LegacyBitmapHeapMaintenanceState()
        state = sample(state, now = 0L, heapMiB = 24, releasedMiB = 0).state
        state = sample(state, now = LegacyBitmapHeapMaintenancePolicy.BASELINE_START_MS,
            heapMiB = 24, releasedMiB = 80).state
        state = sample(state, now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS,
            heapMiB = 29, releasedMiB = 320).state
        return sample(state, now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 60_000L,
            heapMiB = 30, releasedMiB = 330).state
    }

    private fun sample(
        state: LegacyBitmapHeapMaintenanceState,
        now: Long,
        heapMiB: Long,
        releasedMiB: Long,
        pendingDisposals: Int = 0,
        sdkInt: Int = 22,
        lowMemoryTier: Boolean = true,
        oomCount: Long = 0L,
    ): LegacyBitmapHeapMaintenanceDecision = LegacyBitmapHeapMaintenancePolicy.evaluate(
        previous = state,
        sdkInt = sdkInt,
        lowMemoryTier = lowMemoryTier,
        memoryLevel = PlaybackMemoryLevel.NORMAL,
        oomCount = oomCount,
        nowElapsedMs = now,
        heapUsedBytes = heapMiB * mib,
        heapMaxBytes = 100 * mib,
        bitmapReleasedBytes = releasedMiB * mib,
        activeBitmapCount = 2,
        activeBitmapBytes = 2 * mib,
        pendingDisposals = pendingDisposals,
        activeMediaTransfers = 0,
    )
}
