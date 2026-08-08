import java.io.File

fun runPrivacyChecks() {
    println("-- installation-specific diagnostic identities --")
    val keyA = ByteArray(32) { (it + 1).toByte() }
    val keyB = ByteArray(32) { (it + 7).toByte() }
    val first = DiagnosticIdentityHasher(keyA).token("folder", "Alice Family")
    val again = DiagnosticIdentityHasher(keyA).token("folder", "Alice Family")
    val otherInstall = DiagnosticIdentityHasher(keyB).token("folder", "Alice Family")
    check("HMAC identity stable for installation", first, again)
    check("HMAC identity differs across installations", false, first == otherInstall)
    check("HMAC token has typed 12-byte suffix", true, first.matches(Regex("folder_[0-9a-f]{24}")))
    DiagnosticIdentityHasher.install(keyA)

    val privateValues = listOf(
        "Alice and Bob Family Album",
        "/storage/emulated/0/DCIM/Alice Birthday.jpg",
        "C:\\Users\\Alice\\Pictures\\Family.heic",
        "\\\\NASBOX\\FamilyPhotos\\Alice.png",
        "content://media/external/images/42",
        "file:///sdcard/Alice.jpg",
        "smb://alice:secret@nasbox/Family/Alice.jpg",
        "https://nas.local/remote.php/dav/files/alice/Family?token=secret",
        "username=alice password=hunter2 otp=123456",
        "Authorization=Bearer abc123 Cookie=session-secret",
        "192.168.1.42",
        "2001:db8::1428:57ab",
        "nasbox.local",
        "51.507351,-0.127758",
        "GPS=51.507351,-0.127758 EXIF Artist=Alice",
        "Alice%20Family%2FBirthday.jpg",
        "Семейный альбом Алисы",
        "Alice\u0000Family",
    )

    println("-- adversarial values at all common boundaries --")
    val root = java.nio.file.Files.createTempDirectory("fpf-privacy").toFile()
    val standardDir = File(root, "standard")
    val bulkDir = File(root, "bulk")
    val log = DiagnosticsLog(capacity = 500, writerQueueCapacity = 4_096)
    log.attachSink(
        FileDiagnosticsSink(standardDir, maxBytes = 512, keepGenerations = 2),
        FileDiagnosticsSink(bulkDir, maxBytes = 512, keepGenerations = 2),
        "privacy-session",
    )
    privateValues.forEachIndexed { index, value ->
        log.logEvent(
            "SOURCE_UNAVAILABLE",
            mapOf("sourceKind" to "SMB", "reason" to value, "password" to value),
            DiagnosticContext(operationId = "privacy-$index"),
            fixedMessage = value,
        )
    }
    log.flushDurable()
    val memory = log.snapshot().joinToString("\n") { it.toString() }
    val onDevice = log.renderRedacted(500)
    val bundle = log.openDurableBundle().bufferedReader().use { it.readText() }
    val rotated = root.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
    for (seed in privateValues) {
        check("private fixture absent from memory: ${seed.take(18)}", false, memory.contains(seed))
        check("private fixture absent from view: ${seed.take(18)}", false, onDevice.contains(seed))
        check("private fixture absent from bundle: ${seed.take(18)}", false, bundle.contains(seed))
        check("private fixture absent from rotated files: ${seed.take(18)}", false, rotated.contains(seed))
    }
    check("privacy transformations counted", true, log.totalDroppedFields() >= privateValues.size * 2L)
    check("safe category survives privacy policy", true, bundle.contains("\"sourceKind\":\"SMB\""))

    println("-- crash envelope privacy boundary --")
    val runtime = DiagnosticRuntimeState().apply {
        updatePlayback {
            it.copy(
                presentationToken = privateValues[0],
                sourceKind = privateValues[10],
                layout = "SINGLE",
                transitionCode = "FADE",
            )
        }
    }
    val throwable = IllegalStateException("never persisted message").apply {
        stackTrace = arrayOf(
            StackTraceElement("com.example.familyphotoframe.Player", "render", "Alice Birthday.jpg", 42),
        )
    }
    var stored: String? = null
    val store = CrashEnvelopeStore(object : CrashEnvelopeStore.Storage {
        override fun read(): String? = stored
        override fun write(value: String): Boolean { stored = value; return true }
        override fun clear(): Boolean { stored = null; return true }
    })
    val envelope = CrashEnvelopeFactory.crash(
        throwable,
        Thread.currentThread().apply { name = privateValues[1] },
        true,
        CrashCaptureContext(
            atEpochMs = 1L,
            elapsedRealtimeMs = 2L,
            sessionId = "abcdef12",
            lastSequence = 3L,
            appVersion = "test",
            versionCode = 4L,
            sdkInt = 22,
            runtime = runtime.snapshot(),
            activeOperation = null,
            queueDepth = 0,
            droppedCount = 0,
            sinkErrorClass = "",
        ),
    )
    check("crash envelope stored", true, store.write(envelope))
    for (seed in privateValues) {
        check("private fixture absent from crash envelope: ${seed.take(18)}", false, stored.orEmpty().contains(seed))
    }
    check("image filename excluded from stack evidence", false, stored.orEmpty().contains("Alice Birthday.jpg"))

    log.detachSink()
    root.deleteRecursively()
}
