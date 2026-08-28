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
    fun reusesVisiblePresentation(
        currentPhotoId: Long?,
        lastRenderedPhotoId: Long?,
        selectedPhotoId: Long,
    ): Boolean = currentPhotoId == selectedPhotoId && lastRenderedPhotoId == selectedPhotoId
}
