import java.io.File

/**
 * End-to-end exercise of the real logging pipeline:
 *
 *     DiagnosticsLog -> DiagnosticsJsonl -> FileDiagnosticsSink -> rotating files
 *
 * Unit assertions already cover the encoder, and the analyser has been tested against
 * hand-written JSON. Neither proves the two agree. This simulates a full soak run through
 * the actual production classes and writes a real bundle, which the harness then feeds to
 * the real `analyze-diagnostics.py`. If the writer and the analyser ever disagree about
 * the format, that costs someone a 24-hour test run, so it is worth proving here.
 *
 * The simulated clock advances in the same pattern as the gate procedure: two NAS
 * outages, two reboots on different API levels, slides every 30s, heap samples every 5m.
 */
object DiagnosticsEndToEnd {

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = File(args.getOrElse(0) { "./e2e-out" })
        outDir.deleteRecursively()
        outDir.mkdirs()

        var clock = 1_700_000_000_000L
        // Deliberately small so rotation is exercised rather than assumed; a real device
        // uses 2 MB.
        // Both streams get a deliberately tiny budget so rotation is exercised, not
        // assumed. The point of the split is that rotating the slide stream must not cost
        // us the evidence stream.
        // Budget in KB, so the harness can run both a realistic device configuration and a
        // deliberately starved one.
        val budgetKb = args.getOrNull(1)?.toLongOrNull() ?: 2048L
        val events = FileDiagnosticsSink(File(outDir, "events"), maxBytes = budgetKb * 1024, keepGenerations = 2)
        val slides = FileDiagnosticsSink(File(outDir, "slides"), maxBytes = budgetKb * 2 * 1024, keepGenerations = 2)
        // The simulation emits 26 hours in milliseconds; its larger queue prevents that
        // synthetic burst from testing overload instead of the intended rotation path.
        val log = DiagnosticsLog(capacity = 500, nowMs = { clock }, writerQueueCapacity = 8192)

        fun session(sdk: Int, boot: Boolean) {
            log.attachSink(events, slides, "s" + (clock % 100000).toString())
            log.log(
                DiagnosticsLog.Category.APP, "SESSION_START", "",
                mapOf("appVersion" to "0.9.0", "sdkInt" to sdk.toString(), "deviceModel" to "TestFrame"),
            )
            if (boot) {
                log.log(
                    DiagnosticsLog.Category.APP, "BOOT_AUTOSTART", "",
                    mapOf("sdkInt" to sdk.toString(), "deviceModel" to "TestFrame"),
                )
            }
        }

        session(sdk = 30, boot = false)
        log.log(
            DiagnosticsLog.Category.ENGINE, "POOL_CONFIGURED", "",
            mapOf("primaryCount" to "2", "fallbackCount" to "1", "intervalMs" to "30000"),
        )

        val rng = java.util.Random(11)
        var lastPhoto = -1L
        var slideCount = 0
        // 26 hours at 30s per slide.
        for (i in 0 until 26 * 120) {
            clock += 30_000
            var id = (rng.nextInt(400) + 1).toLong()
            while (id == lastPhoto) id = (rng.nextInt(400) + 1).toLong()
            lastPhoto = id
            log.log(
                DiagnosticsLog.Category.ENGINE, "SLIDE_RENDERED", "",
                mapOf(
                    "presentationToken" to "presentation_${id.toString(16).padStart(24, '0')}",
                    "sourceKind" to if (rng.nextBoolean()) "LOCAL_SAF" else "SMB",
                    "folderToken" to "folder_00112233445566778899aabb",
                    "selectionMode" to "SHUFFLE_NO_REPEAT",
                ),
            )
            slideCount++

            if (i % 10 == 0) {
                // Mild, realistic heap movement: well under the 5% gate.
                val used = 42_000 + (i % 40) * 12
                log.log(
                    DiagnosticsLog.Category.MEMORY, "HEAP_SAMPLE", "",
                    mapOf("heapUsedKb" to used.toString(), "heapMaxKb" to "196608",
                          "uptimeSec" to (i * 30).toString()),
                )
            }

            // Two NAS outages, each recovering.
            if (i == 400 || i == 1800) {
                log.log(DiagnosticsLog.Category.SOURCE, "SOURCE_UNAVAILABLE", "sourceKind" to "SMB")
                log.log(
                    DiagnosticsLog.Category.SOURCE,
                    "SOURCE_STALE_CACHE_ACTIVE",
                    "sourceKind" to "SMB",
                    "cached" to "137",
                )
            }
            if (i == 470 || i == 1900) {
                log.log(DiagnosticsLog.Category.SOURCE, "SOURCE_RECOVERED", "sourceKind" to "SMB")
            }
            // Two reboots, the second on a different API level.
            if (i == 1200) { clock += 60_000; session(sdk = 30, boot = true) }
            if (i == 2400) { clock += 60_000; session(sdk = 34, boot = true) }
        }

        val out = File(outDir, "bundle.jsonl")
        log.openDurableBundle().use { input -> out.outputStream().use(input::copyTo) }

        println("slides simulated : $slideCount")
        println("event bytes      : " + events.totalBytes())
        println("slide bytes      : " + slides.totalBytes())
        println("slide rotation   : " + File(File(outDir, "slides"), "diagnostics.1.jsonl").exists())
        println("event rotation   : " + File(File(outDir, "events"), "diagnostics.1.jsonl").exists())
        println("sink error       : " + (events.lastError ?: slides.lastError ?: "none"))
        println("bundle           : " + out.absolutePath)
        println("bundle lines     : " + out.useLines { it.count() })
    }
}
