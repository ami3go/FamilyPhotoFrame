package com.example.familyphotoframe.ui.slideshow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAnchorPresentationPolicyTest {
    @Test fun slowSelectedAnchorPrefersImmediateSingleFrame() {
        assertTrue(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.SELECTED_PRESENTATION,
                anchorTransferDurationMs = SelectedAnchorPresentationPolicy.FAST_TRANSFER_MAX_MS + 1,
                selectedDeadlineReached = false,
            )
        )
    }

    @Test fun fastSelectedAnchorMayBuildCollage() {
        assertFalse(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.SELECTED_PRESENTATION,
                anchorTransferDurationMs = SelectedAnchorPresentationPolicy.FAST_TRANSFER_MAX_MS,
                selectedDeadlineReached = false,
            )
        )
    }

    @Test fun deadlineReachedSelectedAnchorPrefersImmediateSingleFrame() {
        assertTrue(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.SELECTED_PRESENTATION,
                anchorTransferDurationMs = null,
                selectedDeadlineReached = true,
            )
        )
    }

    @Test fun cachedSelectedAnchorMayStillBuildCollage() {
        assertFalse(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.SELECTED_PRESENTATION,
                anchorTransferDurationMs = null,
                selectedDeadlineReached = false,
            )
        )
    }

    @Test fun backgroundPreloadMayStillBuildCollageAfterTransfer() {
        assertFalse(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.BACKGROUND_PRELOAD,
                anchorTransferDurationMs = 30_000L,
                selectedDeadlineReached = true,
            )
        )
    }
}
