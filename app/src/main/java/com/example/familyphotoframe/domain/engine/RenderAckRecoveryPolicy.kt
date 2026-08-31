package com.example.familyphotoframe.domain.engine

/**
 * Decides whether selecting a photo needs a new UI render acknowledgement.
 *
 * A one-member explicit pool (notably "On this day") can legitimately choose the
 * already committed photo again. Compose has no observable input change in that case,
 * so waiting for another acknowledgement would manufacture a timeout even though the
 * photo is still on screen.
 */
internal object RenderAckRecoveryPolicy {
    private const val SELECTED_TRANSFER_DEADLINE = "SELECTED_DEADLINE"

    /** Reject callbacks from a cancelled attempt that selected the same photo again. */
    fun isCurrentAttempt(expectedGeneration: Long, actualGeneration: Long): Boolean =
        expectedGeneration == actualGeneration

    /**
     * A selected transfer that consumed its complete budget is an anchor failure for the
     * current shuffle cycle. Merely cancelling its reservation would make the same slow
     * anchor immediately eligible again and can trap playback in an endless retry loop.
     */
    fun shouldReleaseCancelledAnchor(mediaTransferState: String): Boolean =
        mediaTransferState == SELECTED_TRANSFER_DEADLINE

    fun reusesVisiblePresentation(
        currentPhotoId: Long?,
        lastRenderedPhotoId: Long?,
        selectedPhotoId: Long,
    ): Boolean = currentPhotoId == selectedPhotoId && lastRenderedPhotoId == selectedPhotoId
}
