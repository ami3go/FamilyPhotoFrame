import java.io.File

fun runDiagnosticsPagingAndBundleChecks() {
    println("-- stable diagnostics paging and streamed bundle --")
    check(
        "diagnostics filename uses millisecond UTC timestamp",
        "FamilyPhotoFrame-diagnostics-19700101T000000000Z.jsonl",
        DiagnosticsDownloadNaming.fileName(0L),
    )
    val entries = (1L..8L).map { sequence ->
        DiagnosticsLog.Entry(
            atEpochMs = 1_000L + sequence,
            category = if (sequence % 2L == 0L) DiagnosticsLog.Category.SCAN else DiagnosticsLog.Category.APP,
            code = if (sequence % 2L == 0L) "SCAN_COMPLETED" else "APP_CREATE",
            fields = if (sequence % 2L == 0L) mapOf("trigger" to "REBUILD_WEB_UI") else emptyMap(),
            sessionId = "session-a",
            sequence = sequence,
            elapsedRealtimeMs = sequence,
            severity = if (sequence == 8L) DiagnosticSeverity.ERROR else DiagnosticSeverity.INFO,
            origin = DiagnosticOrigin.WEB_UI,
            operationId = if (sequence % 2L == 0L) "operation-1" else null,
        )
    }
    val newest = DiagnosticsPager.page(entries, DiagnosticsQuery(limit = 3))
    check("initial page returns newest stable window", listOf(6L, 7L, 8L), newest.events.map { it.sequence })
    val withNewTail = entries + entries.last().copy(atEpochMs = 2_000L, sequence = 9L)
    val older = DiagnosticsPager.page(withNewTail, DiagnosticsQuery(cursor = newest.nextCursor, limit = 3))
    check("new tail does not move older cursor", listOf(3L, 4L, 5L), older.events.map { it.sequence })
    val filtered = DiagnosticsPager.page(entries, DiagnosticsQuery(limit = 20, category = "SCAN", trigger = "REBUILD_WEB_UI"))
    check("structured filters are applied server side", listOf(2L, 4L, 6L, 8L), filtered.events.map { it.sequence })
    val expired = DiagnosticsPager.page(entries.drop(3), DiagnosticsQuery(cursor = "session-a~2", limit = 3))
    check("rotated cursor is explicit", true, expired.cursorExpired)

    val root = java.nio.file.Files.createTempDirectory("fpf-bundle").toFile()
    val log = DiagnosticsLog()
    log.attachSink(
        FileDiagnosticsSink(File(root, "standard")),
        FileDiagnosticsSink(File(root, "bulk")),
        "bundle-session",
    )
    log.log(DiagnosticsLog.Category.APP, "SESSION_START")
    log.log(DiagnosticsLog.Category.ENGINE, "SLIDE_RENDERED", "presentationToken" to diagnosticToken("photo", "presentation"))
    val context = DiagnosticsBundleContext(
        appVersion = "test",
        versionCode = 29,
        buildType = "test",
        sdkInt = 22,
        deviceModel = "frame",
        abi = "x86",
        sourceKind = "SMB",
        indexedCount = 28_133,
        runtime = DiagnosticRuntimeState.Snapshot(
            memory = DiagnosticRuntimeState.Memory(heapUsedKb = 42_000, heapMaxKb = 100_000),
            playback = DiagnosticRuntimeState.Playback(surface = "SLIDESHOW", engineState = "RUNNING"),
        ),
    )
    val text = log.openDurableBundle(context).bufferedReader().use { it.readText() }
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    check("bundle contains no comment records", true, lines.none { it.startsWith("#") })
    check("bundle metadata is machine readable", true, lines.first().contains("\"recordType\":\"bundleMetadata\""))
    check("bundle contains runtime snapshot", true, text.contains("\"recordType\":\"runtimeSnapshot\""))
    check("bundle contains health and retention", true, text.contains("\"recordType\":\"diagnosticsHealth\"") && text.contains("\"retainedBytes\""))
    check("bundle identifies both stream boundaries", true, text.contains("\"stream\":\"STANDARD\"") && text.contains("\"stream\":\"BULK\""))
    check("bundle streams both event classes", true, text.contains("SESSION_START") && text.contains("SLIDE_RENDERED"))
    check("bundle closes with integrity marker", true, lines.last().contains("\"recordType\":\"bundleEnd\""))
    log.detachSink()
    root.deleteRecursively()
}
