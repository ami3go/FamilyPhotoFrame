package com.example.familyphotoframe.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventCatalogTest {
    @Test fun onThisDayEvents_areRegisteredAsEngineEvents() {
        val skipped = DiagnosticEventCatalog.find("ON_THIS_DAY_SKIPPED_EMPTY")
        val triggered = DiagnosticEventCatalog.find("ON_THIS_DAY_TRIGGERED")
        assertNotNull(skipped)
        assertNotNull(triggered)
        assertEquals(DiagnosticsLog.Category.ENGINE, skipped?.category)
        assertEquals(DiagnosticsLog.Category.ENGINE, triggered?.category)
    }

    @Test fun onThisDayTriggered_preservesStructuredFields() {
        val triggered = DiagnosticEventCatalog.require("ON_THIS_DAY_TRIGGERED")
        assertTrue("years" in triggered.permittedFields)
        assertTrue("preview" in triggered.permittedFields)
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
}
