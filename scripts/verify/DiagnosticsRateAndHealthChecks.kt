import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

fun runDiagnosticsRateAndHealthChecks() {
    println("-- diagnostic aggregation and rate limits --")
    run {
        val clock = AtomicLong(1_000L)
        val log = DiagnosticsLog(nowMs = clock::get)
        val brightness = mapOf(
            "level" to "42",
            "action" to "DIM_ONLY",
            "mode" to "MANUAL",
            "periodToken" to diagnosticToken("day", "period"),
        )
        repeat(10) { log.log(DiagnosticsLog.Category.ENGINE, "BRIGHTNESS_LEVEL_APPLIED", "", brightness) }
        check("unchanged brightness logs once", 1, log.snapshot().count { it.code == "BRIGHTNESS_LEVEL_APPLIED" })
        clock.addAndGet(DiagnosticRateController.BRIGHTNESS_HEARTBEAT_MS)
        log.log(DiagnosticsLog.Category.ENGINE, "BRIGHTNESS_LEVEL_APPLIED", "", brightness)
        check("brightness heartbeat after 15 minutes", 1, log.snapshot().count { it.code == "BRIGHTNESS_UNCHANGED_HEARTBEAT" })
        log.log(
            DiagnosticsLog.Category.ENGINE,
            "BRIGHTNESS_LEVEL_APPLIED",
            "",
            brightness + ("level" to "43"),
        )
        check("brightness change emits immediately", 2, log.snapshot().count { it.code == "BRIGHTNESS_LEVEL_APPLIED" })

        val decode = mapOf(
            "sourceKind" to "SMB", "stage" to "DECODE", "format" to "JPG",
            "errorClass" to "IOException", "permanent" to "false",
        )
        repeat(10) { log.log(DiagnosticsLog.Category.DECODE, "DECODE_FAILED", "", decode) }
        check("first three identical decode errors retained", 3, log.snapshot().count { it.code == "DECODE_FAILED" })
        clock.addAndGet(DiagnosticRateController.SUMMARY_INTERVAL_MS)
        log.log(DiagnosticsLog.Category.DECODE, "DECODE_FAILED", "", decode)
        val decodeSummary = log.snapshot().last { it.code == "DECODE_FAILURE_SUMMARY" }
        check("decode summary emitted each active minute", "11", decodeSummary.fields["count"])
        log.log(
            DiagnosticsLog.Category.DECODE,
            "DECODE_FAILED",
            "",
            decode + ("format" to "PNG"),
        )
        check("different decode signature keeps first error", 4, log.snapshot().count { it.code == "DECODE_FAILED" })

        repeat(20) { log.log(DiagnosticsLog.Category.APP, "WEB_PREVIEW_CACHE_HIT", "revision" to "1") }
        check("preview hits do not emit individually", 0, log.snapshot().count { it.code == "WEB_PREVIEW_CACHE_HIT" })
        clock.addAndGet(DiagnosticRateController.SUMMARY_INTERVAL_MS)
        log.log(DiagnosticsLog.Category.APP, "WEB_PREVIEW_CACHE_HIT", "revision" to "1")
        check("preview hits aggregate", "21", log.snapshot().last { it.code == "PREVIEW_HIT_SUMMARY" }.fields["hits"])

        val operation = DiagnosticContext(operationId = "scan-rate")
        val progress = mapOf("sourceKind" to "SMB", "progressBucket" to "10")
        log.logEvent("SCAN_PROGRESS", progress, operation)
        log.logEvent("SCAN_PROGRESS", progress, operation)
        log.logEvent("SCAN_PROGRESS", progress + ("progressBucket" to "20"), operation)
        check("scan bucket logged once", 2, log.snapshot().count { it.code == "SCAN_PROGRESS" })

        repeat(200) { index ->
            log.log(
                DiagnosticsLog.Category.DECODE,
                "DECODE_FAILED",
                "",
                decode + mapOf("format" to "F$index", "errorCode" to "E$index"),
            )
        }
        check("aggregation state remains bounded", true, log.rateStateSize() <= 67)
    }

    println("-- writer failure, recovery, and health snapshot --")
    run {
        val root = java.nio.file.Files.createTempDirectory("fpf-health").toFile()
        val standard = FileDiagnosticsSink(File(root, "standard"), maxBytes = 512, keepGenerations = 2)
        val bulk = FileDiagnosticsSink(File(root, "bulk"), maxBytes = 512, keepGenerations = 2)
        val attempts = AtomicInteger(0)
        val log = DiagnosticsLog(durableAppend = { target, line ->
            if (attempts.getAndIncrement() == 0) throw java.io.IOException("forced")
            target.append(line)
        })
        log.attachSink(standard, bulk, "health-session")
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.flushDurable()
        check("sink failure event held in memory", true, log.snapshot().any { it.code == "DIAGNOSTICS_SINK_FAILED" })
        check("sink failure class exposed", "IOException", log.healthSnapshot().standard.lastAppendErrorClass)
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.flushDurable()
        check("sink recovery event held in memory", true, log.snapshot().any { it.code == "DIAGNOSTICS_SINK_RECOVERED" })
        val health = log.healthSnapshot()
        check("sink health recovers", "", health.standard.lastAppendErrorClass)
        check("write time exposed", true, health.lastSuccessfulWriteEpochMs > 0L)
        check("flush time exposed", true, health.lastSuccessfulFlushEpochMs > 0L)
        check("stream sequence exposed", true, health.standard.newestKnownSequence > 0L)
        log.updateCrashEnvelopeHealth(true, 321)
        check("crash envelope presence exposed", true, log.healthSnapshot().crashEnvelopePresent)
        check("crash envelope size exposed", 321, log.healthSnapshot().crashEnvelopeBytes)
        log.detachSink()
        root.deleteRecursively()
    }

    println("-- real file-sink failures reach writer health --")
    run {
        val root = java.nio.file.Files.createTempDirectory("fpf-health-real-sink").toFile()
        val notDirectory = File(root, "blocked").apply { writeText("file") }
        val log = DiagnosticsLog()
        log.attachSink(
            FileDiagnosticsSink(notDirectory),
            FileDiagnosticsSink(File(root, "bulk")),
            "real-sink-session",
        )
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.flushDurable()
        check("real sink error class exposed", true, log.healthSnapshot().standard.lastAppendErrorClass.isNotEmpty())
        check("real sink failure marker retained", true, log.snapshot().any { it.code == "DIAGNOSTICS_SINK_FAILED" })
        log.detachSink()
        root.deleteRecursively()
    }

    println("-- queue saturation and flush timeout --")
    run {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val root = java.nio.file.Files.createTempDirectory("fpf-health-queue").toFile()
        val log = DiagnosticsLog(
            writerQueueCapacity = 1,
            durableAppend = { _, _ -> entered.countDown(); release.await(5, TimeUnit.SECONDS) },
        )
        log.attachSink(
            FileDiagnosticsSink(File(root, "standard")),
            FileDiagnosticsSink(File(root, "bulk")),
            "queue-session",
        )
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        check("writer entered blocking append", true, entered.await(2, TimeUnit.SECONDS))
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        repeat(3) { log.log(DiagnosticsLog.Category.APP, "APP_CREATE") }
        check("flush timeout reported", false, log.flushDurable(10L))
        check("flush timeout event in memory", true, log.snapshot().any { it.code == "DIAGNOSTICS_FLUSH_TIMEOUT" })
        check("all failed enqueue attempts counted", 5L, log.totalDroppedEvents())
        check("drop delta exact", 5L, log.healthSnapshot().droppedSinceLastReport)
        release.countDown()
        log.flushDurable()
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.flushDurable()
        val overflow = log.snapshot().last { it.code == "DIAGNOSTICS_QUEUE_OVERFLOW" }
        check("overflow reports lost caller events", "3", overflow.fields["dropped"])
        log.detachSink()
        root.deleteRecursively()
    }

    println("-- 100,000-event bounded endurance --")
    run {
        val root = java.nio.file.Files.createTempDirectory("fpf-health-endurance").toFile()
        val standard = FileDiagnosticsSink(File(root, "standard"), maxBytes = 2L * 1024, keepGenerations = 2)
        val bulk = FileDiagnosticsSink(File(root, "bulk"), maxBytes = 2L * 1024, keepGenerations = 2)
        val log = DiagnosticsLog(
            capacity = 128,
            writerQueueCapacity = 8_192,
            durableAppend = { target, line -> if (target === standard) target.append(line) },
        )
        log.attachSink(standard, bulk, "endurance-session")
        log.log(DiagnosticsLog.Category.APP, "SESSION_START")
        repeat(100_000) { index ->
            log.log(
                DiagnosticsLog.Category.ENGINE,
                "SLIDE_SELECTED",
                "count" to index.toString(),
            )
            if (index % 10_000 == 9_999) log.flushDurable()
        }
        log.flushDurable()
        val standardText = standard.openRetainedStream().bufferedReader().use { it.readText() }
        check("bulk endurance cannot rotate session evidence", true, standardText.contains("SESSION_START"))
        check("in-memory ring remains bounded", true, log.snapshot(1_000).size <= 128)
        check("rate maps remain bounded after endurance", true, log.rateStateSize() <= 67)
        check("retained test streams remain bounded", true, log.durableBytes() <= 6L * 2L * 1024L)
        log.detachSink()
        root.deleteRecursively()
    }

    println("-- concurrent export, clear, and shutdown flush --")
    run {
        val root = java.nio.file.Files.createTempDirectory("fpf-health-concurrent").toFile()
        val log = DiagnosticsLog(writerQueueCapacity = 4_096)
        log.attachSink(
            FileDiagnosticsSink(File(root, "standard"), maxBytes = 1_024, keepGenerations = 2),
            FileDiagnosticsSink(File(root, "bulk"), maxBytes = 1_024, keepGenerations = 2),
            "concurrent-session",
        )
        repeat(500) { log.log(DiagnosticsLog.Category.APP, "APP_CREATE") }
        log.flushDurable()
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val export = Thread {
            runCatching { log.openDurableBundle().use { it.copyTo(java.io.ByteArrayOutputStream()) } }
                .exceptionOrNull()?.let(errors::add)
        }
        val clear = Thread { runCatching { log.clearDurable() }.exceptionOrNull()?.let(errors::add) }
        export.start()
        clear.start()
        export.join()
        clear.join()
        check("concurrent export and clear are safe", true, errors.isEmpty())
        log.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        log.detachSink()
        check("detach performs shutdown flush", 0, log.writerQueueDepth())
        root.deleteRecursively()
    }
}
