package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderAckTimeoutPolicyTest {

    @Test fun boundedPreparationLeavesRoomForFinalRenderRecovery() {
        assertEquals(60_000L, RenderAckTimeoutPolicy.PREPARATION_WATCHDOG_TIMEOUT_MS)
        assertEquals(70_000L, RenderAckTimeoutPolicy.FINAL_RENDER_ACK_TIMEOUT_MS)
    }

    @Test fun selectionWithoutAPreparationStageIsDiagnosable() {
        assertEquals("PREPARATION_NOT_STARTED", RenderAckTimeoutPolicy.reasonFor(null))
        assertEquals("PREPARATION_NOT_STARTED", RenderAckTimeoutPolicy.reasonFor("SELECTED"))
    }

    @Test fun preparationAndTransitionGapsRemainDistinct() {
        assertEquals("PREPARATION_STALLED", RenderAckTimeoutPolicy.reasonFor("PREPARE_STARTED"))
        assertEquals("TRANSITION_NOT_STARTED", RenderAckTimeoutPolicy.reasonFor("PREPARED"))
        assertEquals("TRANSITION_STALLED", RenderAckTimeoutPolicy.reasonFor("TRANSITION_STARTED"))
    }

    @Test fun preparationWatchdog_onlyRecoversBeforePreparationCompletes() {
        assertEquals(true, RenderAckTimeoutPolicy.shouldRecoverPreparation(null))
        assertEquals(true, RenderAckTimeoutPolicy.shouldRecoverPreparation("PREPARE_STARTED"))
        assertEquals(
            false,
            RenderAckTimeoutPolicy.shouldRecoverPreparation("PREPARE_STARTED", "PREPARATION_READY"),
        )
        assertEquals(false, RenderAckTimeoutPolicy.shouldRecoverPreparation("PREPARED"))
        assertEquals(false, RenderAckTimeoutPolicy.shouldRecoverPreparation("TRANSITION_STARTED"))
    }

    @Test fun currentPreparationCancellation_recoversWithoutTreatingThePhotoAsFailed() {
        assertEquals(true, RenderAckTimeoutPolicy.shouldRecoverCancelledPreparation("PREPARE_STARTED"))
        assertEquals(true, RenderAckTimeoutPolicy.shouldRecoverCancelledPreparation("PREPARED"))
        assertEquals(false, RenderAckTimeoutPolicy.shouldRecoverCancelledPreparation("RENDERED"))
        assertEquals(false, RenderAckTimeoutPolicy.shouldRecoverCancelledPreparation("PREPARE_FAILED"))
    }

    @Test fun terminalStagesExplainWhyTheAcknowledgementWasNeverDelivered() {
        assertEquals("FAILURE_NOT_DELIVERED", RenderAckTimeoutPolicy.reasonFor("PREPARE_FAILED"))
        assertEquals("PRESENTATION_ABORTED", RenderAckTimeoutPolicy.reasonFor("TRANSITION_CANCELLED"))
        assertEquals("PRESENTATION_ABORTED", RenderAckTimeoutPolicy.reasonFor("ENGINE_COMMIT_REJECTED"))
        assertEquals("RENDER_CALLBACK_NOT_DELIVERED", RenderAckTimeoutPolicy.reasonFor("RENDERED"))
        assertEquals("RENDER_CALLBACK_NOT_DELIVERED", RenderAckTimeoutPolicy.reasonFor("TRANSITION_COMPLETED"))
    }
}
