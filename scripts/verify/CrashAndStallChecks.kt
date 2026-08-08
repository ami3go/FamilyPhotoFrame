fun runCrashAndStallChecks() {
    println("-- crash envelope durability --")
    run {
        class MemoryStorage : CrashEnvelopeStore.Storage {
            var value: String? = null
            override fun read(): String? = value
            override fun write(value: String): Boolean { this.value = value; return true }
            override fun clear(): Boolean { value = null; return true }
        }

        val storage = MemoryStorage()
        val store = CrashEnvelopeStore(storage)
        val runtime = DiagnosticRuntimeState().apply {
            updateMemory(DiagnosticRuntimeState.Memory(10, 100, 4, 20, 8, 123))
            updatePlayback {
                it.copy(
                    surface = "playing", engineState = "playing_primary",
                    presentationToken = "presentation_abcdef", sourceKind = "smb",
                    layout = "three_columns", transitionCode = "crossfade",
                )
            }
        }
        val context = CrashCaptureContext(
            atEpochMs = 1_000,
            elapsedRealtimeMs = 900,
            sessionId = "session-a",
            lastSequence = 41,
            appVersion = "0.12.7-prerelease",
            versionCode = 27,
            sdkInt = 22,
            runtime = runtime.snapshot(),
            activeOperation = DiagnosticOperationTracker.Operation(
                "scan-session-4", "SCAN", DiagnosticOrigin.ANDROID_UI,
                "refresh-session-3", 100, "ENUMERATING", 800,
            ),
            queueDepth = 7,
            droppedCount = 2,
            sinkErrorClass = "IOException",
        )
        val secret = "password=SummerVacation42"
        val cause = IllegalStateException(secret).apply {
            stackTrace = Array(12) { index ->
                StackTraceElement(
                    "com.example.familyphotoframe.Renderer$index",
                    "draw$index",
                    "Renderer.kt",
                    100 + index,
                )
            }
        }
        val failure = RuntimeException("smb://private-host/family.jpg", cause)
        val envelope = CrashEnvelopeFactory.crash(failure, Thread.currentThread(), true, context)
        check("crash root cause retained", "IllegalStateException", envelope.rootCauseClass)
        check("crash stack bounded", 8, envelope.frames.size)
        check("active operation retained", "scan-session-4", envelope.activeOperationId)
        check("envelope write succeeds", true, store.write(envelope))
        check("envelope bounded to 16 KiB", true, store.sizeBytes() <= CrashEnvelopeStore.MAX_BYTES)
        check("exception message excluded", false, storage.value.orEmpty().contains(secret))
        check("private URI excluded", false, storage.value.orEmpty().contains("private-host"))
        val recovered = store.read() as CrashEnvelopeStore.ReadResult.Valid
        check("checksum-valid round trip", envelope, recovered.envelope)

        val oldResult = recovered
        val newer = envelope.copy(atEpochMs = 2_000, previousSessionId = "session-b")
        store.write(newer)
        check("old recovery cannot clear newer envelope", false, store.clearIfUnchanged(oldResult))
        check("newer envelope remains", "session-b", (store.read() as CrashEnvelopeStore.ReadResult.Valid).envelope.previousSessionId)

        storage.value = storage.value.orEmpty().replaceFirst("kind=CRASH", "kind=ANR")
        check("torn envelope detected", true, store.read() is CrashEnvelopeStore.ReadResult.Corrupt)
    }

    println("-- crash capture is independent of async writer health --")
    run {
        class MemoryStorage : CrashEnvelopeStore.Storage {
            var value: String? = null
            override fun read(): String? = value
            override fun write(value: String): Boolean { this.value = value; return true }
            override fun clear(): Boolean { value = null; return true }
        }
        val storage = MemoryStorage()
        val store = CrashEnvelopeStore(storage)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val root = java.nio.file.Files.createTempDirectory("fpf-crash-queue").toFile()
        val blocked = DiagnosticsLog(
            writerQueueCapacity = 1,
            durableAppend = { _, _ -> entered.countDown(); release.await() },
        )
        blocked.attachSink(
            FileDiagnosticsSink(java.io.File(root, "standard")),
            FileDiagnosticsSink(java.io.File(root, "bulk")),
            "blocked-session",
        )
        blocked.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        entered.await()
        blocked.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        blocked.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        val minimal = CrashEnvelope(
            CrashEnvelope.Kind.CRASH, 1, 1, "blocked-session", 3, "test", 1, 22,
            "RuntimeException", "RuntimeException", "main", true, emptyList(),
            1, 2, 0, 0, 0, "PLAYING", "PLAYING_PRIMARY", "", "SMB", "SINGLE",
            "CROSSFADE", "", "", blocked.writerQueueDepth(), blocked.totalDroppedEvents(), "",
        )
        check("full async queue cannot block crash store", true, store.write(minimal))
        release.countDown()
        blocked.flushDurable()
        blocked.detachSink()
        root.deleteRecursively()

        val failing = DiagnosticsLog(durableAppend = { _, _ -> throw java.io.IOException("disk") })
        val failingRoot = java.nio.file.Files.createTempDirectory("fpf-crash-sink").toFile()
        failing.attachSink(
            FileDiagnosticsSink(java.io.File(failingRoot, "standard")),
            FileDiagnosticsSink(java.io.File(failingRoot, "bulk")),
            "failing-session",
        )
        failing.log(DiagnosticsLog.Category.APP, "APP_CREATE")
        failing.flushDurable()
        check("failing async sink cannot block crash store", true, store.write(minimal.copy(atEpochMs = 2)))
        failing.detachSink()
        failingRoot.deleteRecursively()
    }

    println("-- main-thread stall state machine --")
    run {
        val detector = MainThreadStallDetector()
        check("healthy heartbeat is quiet", 0, detector.observe(2_000, 2_000, false).size)
        val started = detector.observe(6_000, 0, false).single()
        check("five-second stall starts", MainThreadStallDetector.Kind.STARTED, started.kind)
        check("first stall captures envelope", true, started.captureEnvelope)
        val escalated = detector.observe(11_000, 0, false).single()
        check("ten-second stall escalates", MainThreadStallDetector.Kind.ESCALATED, escalated.kind)
        check("capture cooldown applies", false, escalated.captureEnvelope)
        val recovered = detector.observe(12_000, 12_000, false).single()
        check("heartbeat recovery recorded", MainThreadStallDetector.Kind.RECOVERED, recovered.kind)

        detector.observe(20_000, 14_000, false)
        val suppressed = detector.observe(21_000, 14_000, true).single()
        check("debugger pause suppressed", MainThreadStallDetector.Kind.SUPPRESSED_BY_DEBUGGER, suppressed.kind)
        detector.observe(70_000, 64_000, false)
        val lateEscalation = detector.observe(75_000, 64_000, false).single()
        check("later stall escalates", MainThreadStallDetector.Kind.ESCALATED, lateEscalation.kind)
    }

    println("-- process-exit reason mapping --")
    run {
        check("Java crash reason", "CRASH", ProcessExitReasonMapper.name(4))
        check("ANR reason", "ANR", ProcessExitReasonMapper.name(6))
        check("low-memory reason", "LOW_MEMORY", ProcessExitReasonMapper.name(3))
        check("native crash reason", "CRASH_NATIVE", ProcessExitReasonMapper.name(5))
        check("user-requested reason", "USER_REQUESTED", ProcessExitReasonMapper.name(10))
        check("future reason remains unknown", "UNKNOWN", ProcessExitReasonMapper.name(999))
        check("raw description mapped", "LOW_MEMORY", ProcessExitReasonMapper.descriptionCode("killed after OOM"))
        check("raw description never returned", "PRESENT_UNCLASSIFIED", ProcessExitReasonMapper.descriptionCode("family-host"))
    }
}
