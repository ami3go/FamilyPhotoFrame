package com.example.familyphotoframe.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventCatalogTest {
    @Test fun onThisDayEvents_areRegisteredAsEngineEvents() {
        val skipped = DiagnosticEventCatalog.find("ON_THIS_DAY_SKIPPED_EMPTY")
        val triggered = DiagnosticEventCatalog.find("ON_THIS_DAY_TRIGGERED")
        val exhausted = DiagnosticEventCatalog.find("ON_THIS_DAY_POOL_EXHAUSTED")
        assertNotNull(skipped)
        assertNotNull(triggered)
        assertNotNull(exhausted)
        assertEquals(DiagnosticsLog.Category.ENGINE, skipped?.category)
        assertEquals(DiagnosticsLog.Category.ENGINE, triggered?.category)
        assertEquals(DiagnosticsLog.Category.ENGINE, exhausted?.category)
    }

    @Test fun onThisDayTriggered_preservesStructuredFields() {
        val triggered = DiagnosticEventCatalog.require("ON_THIS_DAY_TRIGGERED")
        assertTrue("years" in triggered.permittedFields)
        assertTrue("preview" in triggered.permittedFields)
    }

    @Test fun onThisDayCompletion_preservesPoolProgress() {
        val exhausted = DiagnosticEventCatalog.require("ON_THIS_DAY_POOL_EXHAUSTED")
        assertTrue("photoToken" in exhausted.permittedFields)
        assertTrue("poolSize" in exhausted.permittedFields)
        assertTrue("remaining" in exhausted.permittedFields)
    }

    @Test fun collageSelectionEvaluation_isRegisteredWithOptimizerFields() {
        val spec = DiagnosticEventCatalog.require("COLLAGE_SELECTION_EVALUATED")
        assertEquals(DiagnosticsLog.Category.ENGINE, spec.category)
        setOf(
            "candidateCount", "sameFolderCandidateCount", "portraitCandidateCount",
            "squareCandidateCount", "landscapeCandidateCount",
            "evaluatedTwoPhotoCombinations", "evaluatedThreePhotoCombinations",
            "evaluatedLayoutCount", "folderTier", "orientationTier", "screenCoverage",
            "averageCropLoss", "maximumCropLoss", "timeDistanceScore", "recentPenalty",
            "decisionReason",
        ).forEach { field -> assertTrue(field in spec.permittedFields) }
    }

    /**
     * An unregistered code is silently rewritten to DIAGNOSTICS_UNKNOWN_EVENT, which would
     * make early-playback starts invisible in a diagnostics bundle — the one place they can
     * be confirmed after the fact.
     */
    @Test fun earlyPlaybackStart_isRegisteredWithItsStructuredFields() {
        val early = DiagnosticEventCatalog.require("SOURCE_EARLY_PLAYBACK_STARTED")
        assertEquals(DiagnosticsLog.Category.SOURCE, early.category)
        assertTrue("sourceKind" in early.permittedFields)
        assertTrue("found" in early.permittedFields)
        assertTrue("poolSize" in early.permittedFields)
    }

    @Test fun renderAcknowledgementTelemetry_preservesTimeoutAndStageFields() {
        val timeout = DiagnosticEventCatalog.require("RENDER_ACK_TIMEOUT")
        val stage = DiagnosticEventCatalog.require("PRESENTATION_STAGE")
        assertEquals(DiagnosticsLog.Category.ENGINE, stage.category)
        assertTrue("photoToken" in stage.permittedFields)
        assertTrue("stage" in stage.permittedFields)
        assertTrue("lastPresentationStage" in timeout.permittedFields)
        assertTrue("selectionAgeMs" in timeout.permittedFields)
        assertTrue("stageAgeMs" in timeout.permittedFields)
    }
}
