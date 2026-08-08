/** Durable diagnostics are shared with support, so sink redaction is a security gate. */
fun runDiagnosticsJsonlChecks() {
    fun entry(code: String, msg: String = "", fields: Map<String, String> = emptyMap()) =
        DiagnosticsLog.Entry(1700000000000L, DiagnosticsLog.Category.SOURCE, code, msg, fields, "sess1234")

    println("-- encoding --")
    run {
        val line = DiagnosticsJsonl.encode(
            entry("SOURCE_UNAVAILABLE", fields = mapOf("sourceKind" to "SMB")),
        )
        check("starts as one JSON object", true, line.startsWith("{") && line.endsWith("}"))
        check("no embedded newline", false, line.contains("\n"))
        check("carries schema", true, line.contains("\"schemaVersion\":2"))
        check("carries timestamp", true, line.contains("\"atEpochMs\":1700000000000"))
        check("carries session", true, line.contains("\"sessionId\":\"sess1234\""))
        check("carries nested field", true, line.contains("\"fields\":{\"sourceKind\":\"SMB\"}"))
        check("carries explicit severity", true, line.contains("\"severity\":\"WARN\""))
    }

    println("-- redaction --")
    run {
        val secrets = mapOf(
            "password" to "hunter2", "token" to "abc", "apikey" to "k",
            "gps" to "51.5,0.1", "path" to "/storage/emulated/0/DCIM/private",
            "sourceKind" to "SMB",
        )
        val line = DiagnosticsJsonl.encode(entry("SOURCE_UNAVAILABLE", fields = secrets))
        check("password dropped", false, line.contains("hunter2"))
        check("token dropped", false, line.contains("\"token\""))
        check("api key dropped", false, line.contains("\"apikey\""))
        check("gps dropped", false, line.contains("51.5"))
        check("private path dropped", false, line.contains("emulated"))
        check("safe field kept", true, line.contains("\"sourceKind\":\"SMB\""))
    }

    println("-- hostile input cannot break the format --")
    run {
        val hostile = "line\nbreak\"and\\slash"
        val line = DiagnosticsJsonl.encode(
            entry("SOURCE_UNAVAILABLE", fields = mapOf("reason" to hostile)),
        )
        check("still single line", false, line.contains("\n"))
        check("hostile raw value absent", false, line.contains("break"))
        check("allowed key retained", true, line.contains("\"reason\""))
        check("hostile value tokenized", true, Regex("reason_[0-9a-f]{24}").containsMatchIn(line))
    }
    run {
        val line = DiagnosticsJsonl.encode(
            entry("SOURCE_UNAVAILABLE", fields = mapOf("reason" to "x".repeat(500))),
        )
        check("long value bounded", true, line.length < 700)
    }
    run {
        val line = DiagnosticsJsonl.encode(
            entry("SOURCE_UNAVAILABLE", fields = mapOf("reason" to "a\u0007b")),
        )
        check("control value removed", false, line.contains("a\\u0007b"))
    }
}
