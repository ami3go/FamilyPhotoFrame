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

    @Test fun requestRequiresSixtyFourMiBRetiredSincePreviousCollection() {
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
                now = first.state.lastRequestElapsedMs + (index + 1L) * 7L * 60_000L,
                heapMiB = 31,
                releasedMiB = if (index < 2) 403 else 404,
            ).state
        }
        val belowThreshold = sample(
            first.state.copy(consecutiveHighSamples = 3),
            now = first.state.lastRequestElapsedMs + 20L * 60_000L,
            heapMiB = 31,
            releasedMiB = 403,
        )
        val atThreshold = sample(
            state.copy(consecutiveHighSamples = 3),
            now = first.state.lastRequestElapsedMs + 21L * 60_000L,
            heapMiB = 31,
            releasedMiB = 404,
        )

        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, belowThreshold.action)
        assertEquals(LegacyBitmapHeapMaintenanceAction.REQUEST_GC, atThreshold.action)
    }

    @Test fun requestsAreLimitedToOncePerTwentyMinutes() {
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
            now = first.state.lastRequestElapsedMs + 19L * 60_000L,
            heapMiB = 32,
            releasedMiB = 700,
        )
        val allowed = sample(
            limited.state,
            now = first.state.lastRequestElapsedMs + 20L * 60_000L,
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

    @Test fun nativeGrowthCriticalLatchAllowsSafeManagedHeapMaintenance() {
        val request = sample(
            seededHighState().copy(consecutiveHighSamples = 3),
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 120_000L,
            heapMiB = 45,
            releasedMiB = 400,
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            pressureSource = PlaybackMemoryPressureSource.NATIVE_PSS_GROWTH,
        )

        assertEquals(LegacyBitmapHeapMaintenanceAction.REQUEST_GC, request.action)
    }

    @Test fun unrelatedOrHighOccupancyCriticalStateStillBlocksMaintenance() {
        val state = seededHighState().copy(consecutiveHighSamples = 3)
        val unrelated = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 120_000L,
            heapMiB = 45,
            releasedMiB = 400,
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            pressureSource = PlaybackMemoryPressureSource.PROCESS_PSS,
        )
        val highOccupancy = sample(
            state,
            now = LegacyBitmapHeapMaintenancePolicy.WARMUP_MS + 120_000L,
            heapMiB = 71,
            releasedMiB = 400,
            memoryLevel = PlaybackMemoryLevel.CRITICAL,
            pressureSource = PlaybackMemoryPressureSource.NATIVE_PSS_GROWTH,
        )

        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, unrelated.action)
        assertEquals(LegacyBitmapHeapMaintenanceAction.NONE, highOccupancy.action)
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
        memoryLevel: PlaybackMemoryLevel = PlaybackMemoryLevel.NORMAL,
        pressureSource: PlaybackMemoryPressureSource = PlaybackMemoryPressureSource.NONE,
    ): LegacyBitmapHeapMaintenanceDecision = LegacyBitmapHeapMaintenancePolicy.evaluate(
        previous = state,
        sdkInt = sdkInt,
        lowMemoryTier = lowMemoryTier,
        memoryLevel = memoryLevel,
        memoryPressureSource = pressureSource,
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
