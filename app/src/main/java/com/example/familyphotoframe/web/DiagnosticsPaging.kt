package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog

/** Stable diagnostics query shared by the HTTP route, controller, and JVM contracts. */
data class DiagnosticsQuery(
    val cursor: String? = null,
    val limit: Int = 100,
    val severity: String? = null,
    val category: String? = null,
    val sessionId: String? = null,
    val code: String? = null,
    val trigger: String? = null,
    val operationId: String? = null,
    val origin: String? = null,
    val search: String? = null,
)

data class DiagnosticsPage(
    val events: List<DiagnosticsLog.Entry>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val cursorExpired: Boolean,
)

/** Cursor paging remains deterministic even while new events arrive at the tail. */
object DiagnosticsPager {
    fun cursor(event: DiagnosticsLog.Entry): String =
        event.sessionId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40) + "~" + event.sequence

    fun page(events: List<DiagnosticsLog.Entry>, query: DiagnosticsQuery): DiagnosticsPage {
        val ordered = events.sortedWith(
            compareBy<DiagnosticsLog.Entry> { it.atEpochMs }
                .thenBy { it.sessionId }
                .thenBy { it.sequence },
        )
        val beforeIndex = query.cursor?.let { raw ->
            ordered.indexOfFirst { cursor(it) == raw }.takeIf { it >= 0 }
        }
        if (query.cursor != null && beforeIndex == null) {
            return DiagnosticsPage(emptyList(), null, false, cursorExpired = true)
        }
        val candidates = ordered.subList(0, beforeIndex ?: ordered.size).filter { matches(it, query) }
        val limit = query.limit.coerceIn(1, 200)
        val from = (candidates.size - limit).coerceAtLeast(0)
        val page = candidates.subList(from, candidates.size)
        return DiagnosticsPage(
            events = page,
            nextCursor = page.firstOrNull()?.let(::cursor),
            hasMore = from > 0,
            cursorExpired = false,
        )
    }

    private fun matches(event: DiagnosticsLog.Entry, query: DiagnosticsQuery): Boolean {
        fun equalsFilter(actual: String, expected: String?): Boolean =
            expected.isNullOrBlank() || actual.equals(expected, ignoreCase = true)
        if (!equalsFilter(event.severity.name, query.severity)) return false
        if (!equalsFilter(event.category.name, query.category)) return false
        if (!equalsFilter(event.sessionId, query.sessionId)) return false
        if (!equalsFilter(event.code, query.code)) return false
        if (!equalsFilter(event.fields["trigger"].orEmpty(), query.trigger)) return false
        if (!equalsFilter(event.origin.name, query.origin)) return false
        if (!query.operationId.isNullOrBlank() &&
            event.operationId != query.operationId && event.parentOperationId != query.operationId
        ) return false
        val search = query.search?.trim()?.lowercase().orEmpty()
        if (search.isNotEmpty()) {
            val haystack = buildString {
                append(event.code).append(' ').append(event.message).append(' ')
                append(event.fields.entries.joinToString(" ") { "${it.key}=${it.value}" })
                append(' ').append(event.operationId.orEmpty())
            }.lowercase()
            if (!haystack.contains(search)) return false
        }
        return true
    }
}
