package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderAckTimeoutPolicyTest {

    @Test fun selectionWithoutAPreparationStageIsDiagnosable() {
        assertEquals("PREPARATION_NOT_STARTED", RenderAckTimeoutPolicy.reasonFor(null))
        assertEquals("PREPARATION_NOT_STARTED", RenderAckTimeoutPolicy.reasonFor("SELECTED"))
    }

    @Test fun preparationAndTransitionGapsRemainDistinct() {
        assertEquals("PREPARATION_STALLED", RenderAckTimeoutPolicy.reasonFor("PREPARE_STARTED"))
        assertEquals("TRANSITION_NOT_STARTED", RenderAckTimeoutPolicy.reasonFor("PREPARED"))
        assertEquals("TRANSITION_STALLED", RenderAckTimeoutPolicy.reasonFor("TRANSITION_STARTED"))
    }

    @Test fun terminalStagesExplainWhyTheAcknowledgementWasNeverDelivered() {
        assertEquals("FAILURE_NOT_DELIVERED", RenderAckTimeoutPolicy.reasonFor("PREPARE_FAILED"))
        assertEquals("PRESENTATION_ABORTED", RenderAckTimeoutPolicy.reasonFor("TRANSITION_CANCELLED"))
        assertEquals("PRESENTATION_ABORTED", RenderAckTimeoutPolicy.reasonFor("ENGINE_COMMIT_REJECTED"))
        assertEquals("RENDER_CALLBACK_NOT_DELIVERED", RenderAckTimeoutPolicy.reasonFor("RENDERED"))
        assertEquals("RENDER_CALLBACK_NOT_DELIVERED", RenderAckTimeoutPolicy.reasonFor("TRANSITION_COMPLETED"))
    }
}
