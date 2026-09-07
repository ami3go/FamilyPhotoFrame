package com.example.familyphotoframe.ui.slideshow

/**
 * Keep genuinely slow selected transfers on the immediate-single path without treating every
 * remote cache miss as slow. Fast transfers still leave most of the selected-presentation budget
 * available for one bounded collage companion, which preserves the user's collage setting on a
 * healthy link. Cached and background-preloaded anchors remain eligible as before.
 */
internal object SelectedAnchorPresentationPolicy {
    const val FAST_TRANSFER_MAX_MS = 8_000L

    fun shouldPreferSingle(
        priority: ModelResolutionPriority,
        anchorTransferDurationMs: Long?,
        selectedDeadlineReached: Boolean,
    ): Boolean = priority == ModelResolutionPriority.SELECTED_PRESENTATION &&
        (selectedDeadlineReached ||
            (anchorTransferDurationMs != null && anchorTransferDurationMs > FAST_TRANSFER_MAX_MS))
}
