package com.example.familyphotoframe.ui.slideshow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAnchorPresentationPolicyTest {
    @Test fun freshlyTransferredSelectedAnchorPrefersImmediateSingleFrame() {
        assertTrue(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.SELECTED_PRESENTATION,
                anchorTransferObserved = true,
            )
        )
    }

    @Test fun cachedSelectedAnchorMayStillBuildCollage() {
        assertFalse(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.SELECTED_PRESENTATION,
                anchorTransferObserved = false,
            )
        )
    }

    @Test fun backgroundPreloadMayStillBuildCollageAfterTransfer() {
        assertFalse(
            SelectedAnchorPresentationPolicy.shouldPreferSingle(
                ModelResolutionPriority.BACKGROUND_PRELOAD,
                anchorTransferObserved = true,
            )
        )
    }
}
