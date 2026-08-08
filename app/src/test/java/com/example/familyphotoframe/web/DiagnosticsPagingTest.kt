package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticSeverity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsPagingTest {
    private fun event(sequence: Long, category: DiagnosticsLog.Category = DiagnosticsLog.Category.APP) =
        DiagnosticsLog.Entry(
            atEpochMs = sequence,
            category = category,
            code = if (category == DiagnosticsLog.Category.SCAN) "SCAN_COMPLETED" else "APP_CREATE",
            fields = if (category == DiagnosticsLog.Category.SCAN) mapOf("trigger" to "REBUILD_WEB_UI") else emptyMap(),
            sessionId = "session",
            sequence = sequence,
            elapsedRealtimeMs = sequence,
            severity = DiagnosticSeverity.INFO,
            origin = DiagnosticOrigin.WEB_UI,
        )

    @Test fun cursorRemainsStableWhenNewTailArrives() {
        val original = (1L..8L).map(::event)
        val first = DiagnosticsPager.page(original, DiagnosticsQuery(limit = 3))
        assertEquals(listOf(6L, 7L, 8L), first.events.map { it.sequence })
        val older = DiagnosticsPager.page(original + event(9L), DiagnosticsQuery(first.nextCursor, 3))
        assertEquals(listOf(3L, 4L, 5L), older.events.map { it.sequence })
        assertTrue(older.hasMore)
    }

    @Test fun filtersAndExpiredCursorAreExplicit() {
        val events = (1L..8L).map { event(it, if (it % 2L == 0L) DiagnosticsLog.Category.SCAN else DiagnosticsLog.Category.APP) }
        val filtered = DiagnosticsPager.page(events, DiagnosticsQuery(limit = 20, category = "SCAN", trigger = "REBUILD_WEB_UI"))
        assertEquals(listOf(2L, 4L, 6L, 8L), filtered.events.map { it.sequence })
        assertFalse(filtered.cursorExpired)
        assertTrue(DiagnosticsPager.page(events, DiagnosticsQuery(cursor = "missing~1")).cursorExpired)
    }

    @Test fun downloadNameUsesUtcMilliseconds() {
        assertEquals(
            "FamilyPhotoFrame-diagnostics-19700101T000000000Z.jsonl",
            DiagnosticsDownloadNaming.fileName(0L),
        )
    }
}
