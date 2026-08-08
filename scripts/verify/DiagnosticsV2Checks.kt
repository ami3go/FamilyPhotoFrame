import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

fun runDiagnosticsV2Checks() {
    println("-- diagnostics schema v2 ordering --")
    run {
        val log = DiagnosticsLog(
            capacity = 2_500,
            nowMs = { 1_700_000_000_000L },
            writerQueueCapacity = 4_096,
        )
        val root = java.nio.file.Files.createTempDirectory("fpf-diag-v2").toFile()
        log.attachSink(
            FileDiagnosticsSink(java.io.File(root, "standard")),
            FileDiagnosticsSink(java.io.File(root, "bulk")),
            "concurrent-session",
        )
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) { worker ->
            Thread {
                start.await()
                repeat(200) { index ->
                    log.log(
                        DiagnosticsLog.Category.APP,
                        "APP_CREATE",
                        "count" to (worker * 200 + index).toString(),
                    )
                }
                done.countDown()
            }.start()
        }
        start.countDown()
        done.await()
        val entries = log.snapshot(2_000)
        check("all concurrent entries retained", 1_600, entries.size)
        check("sequence unique", 1_600, entries.map { it.sequence }.toSet().size)
        check("sequence covers complete range", (1L..1_600L).toList(), entries.map { it.sequence }.sorted())
        check(
            "equal wall clock is deterministically ordered",
            true,
            entries.sortedBy { it.sequence }.zipWithNext().all { (a, b) -> a.sequence < b.sequence },
        )
        log.detachSink()
        root.deleteRecursively()
    }

    println("-- monotonic clock and schema fields --")
    run {
        val clockValues = Collections.synchronizedList(mutableListOf(20L, 15L, 30L))
        val log = DiagnosticsLog(capacity = 10, elapsedRealtimeMs = {
            synchronized(clockValues) { if (clockValues.isEmpty()) 30L else clockValues.removeAt(0) }
        })
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        val entries = log.snapshot()
        check("monotonic time never moves backwards", listOf(20L, 20L, 30L), entries.map { it.elapsedRealtimeMs })
        val line = DiagnosticsJsonl.encode(entries.last())
        check("operation id encoded as null", true, line.contains("\"operationId\":null"))
        check("fields object always present", true, line.contains("\"fields\":{}"))
    }

    println("-- bounded operation registry --")
    run {
        val elapsed = AtomicLong(100L)
        val tracker = DiagnosticOperationTracker(capacity = 2, elapsedRealtimeMs = elapsed::get, abandonAfterMs = 50L)
        tracker.attachSession("session-A")
        val first = tracker.start("refresh", DiagnosticOrigin.ANDROID_UI)
        val second = tracker.start("scan", DiagnosticOrigin.INTERNAL, first.operationId)
        val third = tracker.start("health", DiagnosticOrigin.RECOVERY, first.operationId)
        check("registry remains bounded", 2, tracker.snapshot().size)
        check("oldest operation evicted", false, tracker.snapshot().any { it.id == first.operationId })
        tracker.update(second.operationId, "running")
        check("stage normalized", "RUNNING", tracker.snapshot().first { it.id == second.operationId }.stage)
        tracker.finish(second.operationId)
        check("terminal operation removed", false, tracker.snapshot().any { it.id == second.operationId })
        elapsed.set(1_000L)
        check("abandoned operation expired", 1, tracker.expireAbandoned())
        check("registry empty after expiry", 0, tracker.snapshot().size)
        check("operation ids are non-secret", true, third.operationId.matches(Regex("[a-z0-9-]+")))
    }

    println("-- catalog contract --")
    run {
        val specs = DiagnosticEventCatalog.all
        check("catalog codes unique", specs.size, specs.map { it.code }.toSet().size)
        check("every event has a field allowlist", true, specs.all { it.permittedFields.isNotEmpty() })
        check(
            "source operations require correlation",
            true,
            DiagnosticEventCatalog.require("SOURCE_REFRESH_REQUESTED").operationRequired,
        )
        check(
            "slide events use bulk stream",
            DiagnosticStream.BULK,
            DiagnosticEventCatalog.require("SLIDE_SELECTED").stream,
        )
        check(
            "scan aborts are explicit errors",
            DiagnosticSeverity.ERROR,
            DiagnosticEventCatalog.require("SCAN_ABORTED").severity,
        )
        check(
            "expected supersession is informational",
            DiagnosticSeverity.INFO,
            DiagnosticEventCatalog.require("SCAN_SUPERSEDED").severity,
        )
        val requiredTriggers = setOf(
            "INITIAL_SETTINGS_LOAD", "FIRST_RUN_CONFIGURATION", "REBUILD_ANDROID_UI",
            "REBUILD_WEB_UI", "SOURCE_SETTINGS_CHANGED", "CREDENTIAL_UPDATED",
            "CONFIG_IMPORTED", "ENCRYPTED_BUNDLE_IMPORTED", "FILTERS_CHANGED",
            "SCHEDULED_RESCAN", "RECOVERY_PROMOTION", "PERIODIC_HEALTH_MONITOR",
            "LOCAL_UPLOAD_CHANGED",
        )
        check("all source refresh triggers registered", requiredTriggers, SourceRefreshTrigger.entries.map { it.name }.toSet())
        val log = DiagnosticsLog()
        val unknown = log.logEvent("NOT_A_REGISTERED_EVENT")
        check("unknown event is bounded and visible", "DIAGNOSTICS_UNKNOWN_EVENT", unknown.code)
    }
}
