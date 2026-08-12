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
}
