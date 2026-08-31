package com.example.familyphotoframe.ui.slideshow

/**
 * A freshly transferred visible anchor should become a frame immediately. Optional collage
 * enrichment can still run for cached/preloaded anchors, but must not extend a slow selected
 * SMB transfer into another long series of candidate probes and companion downloads.
 */
internal object SelectedAnchorPresentationPolicy {
    fun shouldPreferSingle(
        priority: ModelResolutionPriority,
        anchorTransferObserved: Boolean,
    ): Boolean = priority == ModelResolutionPriority.SELECTED_PRESENTATION && anchorTransferObserved
}
