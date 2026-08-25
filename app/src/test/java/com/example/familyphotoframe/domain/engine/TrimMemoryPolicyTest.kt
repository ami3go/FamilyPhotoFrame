package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimMemoryPolicyTest {

    @Test fun onlyRunningCriticalRequestsSevereProtection() {
        assertEquals(
            TrimMemoryResponse.SEVERE_PRESSURE,
            TrimMemoryPolicy.classify(TrimMemoryPolicy.TRIM_MEMORY_RUNNING_CRITICAL),
        )
    }

    @Test fun runningLowRequestsConstrainedProtection() {
        assertEquals(
            TrimMemoryResponse.RUNNING_PRESSURE,
            TrimMemoryPolicy.classify(TrimMemoryPolicy.TRIM_MEMORY_RUNNING_LOW),
        )
    }

    /**
     * The regression this policy exists for: the frame received COMPLETE within a second
     * of every launch at 7–8% heap use, and the old `level >= MODERATE` reading opened the
     * circuit breaker, disabling collages for the whole ten-minute recovery window.
     */
    @Test fun backgroundLruLevelsNeverDegradePlayback() {
        listOf(
            TrimMemoryPolicy.TRIM_MEMORY_UI_HIDDEN,
            TrimMemoryPolicy.TRIM_MEMORY_BACKGROUND,
            TrimMemoryPolicy.TRIM_MEMORY_MODERATE,
            TrimMemoryPolicy.TRIM_MEMORY_COMPLETE,
        ).forEach { level ->
            assertEquals(
                "level $level describes LRU position, not this frame's heap",
                TrimMemoryResponse.BACKGROUND_ONLY,
                TrimMemoryPolicy.classify(level),
            )
        }
    }

    @Test fun aBackgroundLevelOutranksNoRunningLevelNumerically() {
        // Guards against reintroducing an ordered comparison: COMPLETE is numerically
        // larger than RUNNING_CRITICAL yet means strictly less about heap pressure.
        assertTrue(TrimMemoryPolicy.TRIM_MEMORY_COMPLETE > TrimMemoryPolicy.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(
            TrimMemoryResponse.BACKGROUND_ONLY,
            TrimMemoryPolicy.classify(TrimMemoryPolicy.TRIM_MEMORY_COMPLETE),
        )
    }

    @Test fun runningModerateIsAdvisoryOnly() {
        assertEquals(
            TrimMemoryResponse.NONE,
            TrimMemoryPolicy.classify(TrimMemoryPolicy.TRIM_MEMORY_RUNNING_MODERATE),
        )
    }

    @Test fun unknownAndNegativeLevelsAreInert() {
        assertEquals(TrimMemoryResponse.NONE, TrimMemoryPolicy.classify(0))
        assertEquals(TrimMemoryResponse.NONE, TrimMemoryPolicy.classify(-1))
    }

    @Test fun aFutureLevelAboveTheBackgroundSeriesStaysBackgroundOnly() {
        assertEquals(TrimMemoryResponse.BACKGROUND_ONLY, TrimMemoryPolicy.classify(100))
        assertEquals(TrimMemoryResponse.BACKGROUND_ONLY, TrimMemoryPolicy.classify(Int.MAX_VALUE))
    }

    @Test fun everyActionableLevelStillDropsTheImageCache() {
        listOf(
            TrimMemoryPolicy.TRIM_MEMORY_RUNNING_LOW,
            TrimMemoryPolicy.TRIM_MEMORY_RUNNING_CRITICAL,
            TrimMemoryPolicy.TRIM_MEMORY_UI_HIDDEN,
            TrimMemoryPolicy.TRIM_MEMORY_COMPLETE,
        ).forEach { level ->
            assertTrue("level $level should still free the image cache", TrimMemoryPolicy.shouldClearImageCache(level))
        }
        assertFalse(TrimMemoryPolicy.shouldClearImageCache(TrimMemoryPolicy.TRIM_MEMORY_RUNNING_MODERATE))
    }

    @Test fun theConstantsMatchThePlatform() {
        // Duplicated from ComponentCallbacks2 so the policy stays a pure JVM object;
        // pin them so a typo cannot silently reshuffle the two series.
        assertEquals(5, TrimMemoryPolicy.TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals(10, TrimMemoryPolicy.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(15, TrimMemoryPolicy.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(20, TrimMemoryPolicy.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(40, TrimMemoryPolicy.TRIM_MEMORY_BACKGROUND)
        assertEquals(60, TrimMemoryPolicy.TRIM_MEMORY_MODERATE)
        assertEquals(80, TrimMemoryPolicy.TRIM_MEMORY_COMPLETE)
    }
}
