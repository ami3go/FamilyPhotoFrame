package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderAckRecoveryPolicyTest {

    @Test fun aCancelledSamePhotoAttemptCannotAcknowledgeItsReplacement() {
        assertFalse(RenderAckRecoveryPolicy.isCurrentAttempt(12L, 13L))
    }

    @Test fun callbacksFromTheCurrentSelectionAttemptAreAccepted() {
        assertTrue(RenderAckRecoveryPolicy.isCurrentAttempt(13L, 13L))
    }

    @Test fun sameCommittedPhotoDoesNotRequireAnUnobservableSecondRender() {
        assertTrue(
            RenderAckRecoveryPolicy.reusesVisiblePresentation(
                currentPhotoId = 41L,
                lastRenderedPhotoId = 41L,
                selectedPhotoId = 41L,
            )
        )
    }

    @Test fun aNewPhotoStillRequiresTheUiAcknowledgement() {
        assertFalse(
            RenderAckRecoveryPolicy.reusesVisiblePresentation(
                currentPhotoId = 41L,
                lastRenderedPhotoId = 41L,
                selectedPhotoId = 42L,
            )
        )
    }

    @Test fun anUnacknowledgedCurrentPhotoIsNeverTreatedAsVisible() {
        assertFalse(
            RenderAckRecoveryPolicy.reusesVisiblePresentation(
                currentPhotoId = 41L,
                lastRenderedPhotoId = 40L,
                selectedPhotoId = 41L,
            )
        )
    }
}
