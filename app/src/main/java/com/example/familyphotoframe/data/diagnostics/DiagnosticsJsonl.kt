package com.example.familyphotoframe.data.diagnostics

/** Schema-v2 JSONL encoder and durable-boundary field allowlist. */
object DiagnosticsJsonl {
    const val MAX_VALUE_LEN = 200
    private const val OBJECT_OPEN = "{"
    private const val OBJECT_CLOSE = "}"

    data class Sanitized(
        val fields: Map<String, String>,
        val message: String,
        val dropped: Int,
    )

    private val bannedKeys = setOf(
        "password", "pass", "secret", "token", "credential", "credentials",
        "apikey", "api_key", "otp", "pin", "cookie", "authorization", "auth",
        "gps", "lat", "lon", "latitude", "longitude", "path", "fullpath", "uri",
        "host", "hostname", "share", "root", "album", "username", "user",
    )

    fun encode(e: DiagnosticsLog.Entry): String {
        val spec = DiagnosticEventCatalog.find(e.code)
            ?: DiagnosticEventCatalog.require("DIAGNOSTICS_UNKNOWN_EVENT")
        val candidateFields = if (spec.code == e.code) e.fields else mapOf(
            "unknownCode" to e.code.filter { it.isLetterOrDigit() || it == '_' }.take(64),
        )
        val safe = sanitizeForEvent(spec, candidateFields, e.message)
        return buildString(256) {
            append(OBJECT_OPEN)
            rawField("schemaVersion", e.schemaVersion.toString()); append(',')
            rawField("sequence", e.sequence.toString()); append(',')
            rawField("atEpochMs", e.atEpochMs.toString()); append(',')
            rawField("elapsedRealtimeMs", e.elapsedRealtimeMs.toString()); append(',')
            stringField("sessionId", e.sessionId); append(',')
            stringField("severity", spec.severity.name); append(',')
            stringField("category", spec.category.name); append(',')
            stringField("code", spec.code); append(',')
            stringField("origin", e.origin.name); append(',')
            nullableStringField("operationId", e.operationId); append(',')
            nullableStringField("parentOperationId", e.parentOperationId); append(',')
            append("\"fields\":{")
            safe.fields.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                stringField(key, value)
            }
            append(OBJECT_CLOSE).append(OBJECT_CLOSE)
        }
    }

    /** Enforce the catalog allowlist and value policy before every diagnostics surface. */
    fun sanitizeForEvent(
        spec: DiagnosticEventSpec,
        fields: Map<String, String>,
        message: String,
    ): Sanitized {
        if (fields.isEmpty() && message.isEmpty()) return Sanitized(emptyMap(), "", 0)
        val out = LinkedHashMap<String, String>(fields.size)
        var dropped = 0
        for ((rawKey, rawValue) in fields) {
            val key = sanitizeKey(rawKey)
            if (key.lowercase() in bannedKeys || key !in spec.permittedFields) {
                dropped++
                continue
            }
            val protected = DiagnosticPrivacyPolicy.protect(key, rawValue)
            out[key] = redactValue(protected.value)
            if (protected.transformed) dropped++
        }
        val protectedMessage = DiagnosticPrivacyPolicy.protectMessage(message)
        if (protectedMessage.transformed) dropped++
        return Sanitized(out, protectedMessage.value, dropped)
    }

    /** Backward-compatible helper used by the existing pure security harness. */
    fun redact(fields: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((rawKey, value) in fields) {
            val key = sanitizeKey(rawKey)
            if (key.lowercase() !in bannedKeys) {
                out[key] = redactValue(DiagnosticPrivacyPolicy.protect(key, value).value)
            }
        }
        return out
    }

    fun redactValue(value: String): String =
        if (value.length <= MAX_VALUE_LEN) value else value.take(MAX_VALUE_LEN) + "\u2026"

    private fun sanitizeKey(key: String): String =
        key.filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "field" }

    private fun StringBuilder.rawField(key: String, value: String) {
        append('"').append(sanitizeKey(key)).append("\":").append(value)
    }

    private fun StringBuilder.stringField(key: String, value: String) {
        append('"').append(sanitizeKey(key)).append("\":")
        appendEscaped(value)
    }

    private fun StringBuilder.nullableStringField(key: String, value: String?) {
        append('"').append(sanitizeKey(key)).append("\":")
        if (value == null) append("null") else appendEscaped(value)
    }

    private fun StringBuilder.appendEscaped(value: String) {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' -> append(String.format("\\u%04x", char.code))
                else -> append(char)
            }
        }
        append('"')
    }
}
