package com.example.familyphotoframe.domain.engine

/**
 * Small, side-effect-free rules for an On This Day interlude.
 *
 * An interlude is an explicit, finite photo pool. Preloading the next item would
 * consume a member before it becomes visible, and wrapping a completed pool would
 * continually re-select the final image. Both behaviours are particularly harmful on
 * constrained frames because they create avoidable selection and render work.
 */
internal object OnThisDayPlaybackPolicy {

    /** Explicit interludes must consume photos only when they are actually selected. */
    fun shouldPreloadNext(isOnThisDay: Boolean): Boolean = !isOnThisDay

    /** The selected item closes the interlude's unique pool once nothing remains. */
    fun isTerminalPick(isOnThisDay: Boolean, remainingAfterPick: Int): Boolean =
        isOnThisDay && remainingAfterPick == 0

    /** Hold only after the final item has been acknowledged as visibly rendered. */
    fun shouldHoldVisibleFrame(
        isOnThisDay: Boolean,
        terminalPhotoId: Long?,
        renderedPhotoId: Long?,
    ): Boolean = isOnThisDay && terminalPhotoId != null && terminalPhotoId == renderedPhotoId
}
