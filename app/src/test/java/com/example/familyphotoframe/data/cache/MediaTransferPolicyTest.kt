package com.example.familyphotoframe.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransferPolicyTest {
    @Test fun remoteCopiesUseAnSmbSizedBuffer() {
        assertEquals(64 * 1024, MediaTransferPolicy.REMOTE_COPY_BUFFER_BYTES)
    }

    @Test fun selectedTransferLeavesCancellationTimeBeforeThePreparationWatchdog() {
        assertEquals(58_000L, MediaTransferPolicy.deadlineMs(MediaTransferPriority.SELECTED_PRESENTATION))
        assertTrue(
            MediaTransferPolicy.deadlineMs(MediaTransferPriority.SELECTED_PRESENTATION) < 60_000L,
        )
    }

    @Test fun backgroundPreloadKeepsTheEstablishedLongerBudget() {
        assertEquals(
            120_000L,
            MediaTransferPolicy.deadlineMs(MediaTransferPriority.BACKGROUND_PRELOAD),
        )
    }
}
