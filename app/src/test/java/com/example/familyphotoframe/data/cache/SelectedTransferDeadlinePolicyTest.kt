package com.example.familyphotoframe.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedTransferDeadlinePolicyTest {
    @Test fun extendsOnlyForFreshNearCompleteProgress() {
        assertEquals(
            2_000L,
            SelectedTransferDeadlinePolicy.extensionMs(
                copiedBytes = 9_000,
                expectedBytes = 10_000,
                lastProgressAgeMs = 500,
                recentBytesPerSecond = 500,
            ),
        )
    }

    @Test fun refusesSlowStaleUnknownOrEarlyTransfers() {
        assertEquals(0L, SelectedTransferDeadlinePolicy.extensionMs(7_999, 10_000, 100, 10_000))
        assertEquals(0L, SelectedTransferDeadlinePolicy.extensionMs(9_000, 10_000, 2_001, 10_000))
        assertEquals(0L, SelectedTransferDeadlinePolicy.extensionMs(9_000, 10_000, 100, 100))
        assertEquals(0L, SelectedTransferDeadlinePolicy.extensionMs(9_000, 0, 100, 10_000))
    }

    @Test fun neverExceedsPreparationWatchdogMargin() {
        val extension = SelectedTransferDeadlinePolicy.extensionMs(8_000, 10_000, 100, 250)
        assertEquals(8_000L, extension)
        assertEquals(
            66_000L,
            MediaTransferPolicy.SELECTED_PRESENTATION_DEADLINE_MS + extension,
        )
    }
}
