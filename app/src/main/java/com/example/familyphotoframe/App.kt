package com.example.familyphotoframe

import android.app.Application
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import android.os.Looper
import com.example.familyphotoframe.data.diagnostics.CrashCaptureContext
import com.example.familyphotoframe.data.diagnostics.CrashEnvelope
import com.example.familyphotoframe.data.diagnostics.CrashEnvelopeFactory
import com.example.familyphotoframe.data.diagnostics.CrashEnvelopeStore
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticIdentityHasher
import com.example.familyphotoframe.data.diagnostics.DiagnosticIdentityKeyStore
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.MainThreadStallWatchdog
import com.example.familyphotoframe.data.diagnostics.ProcessExitInspector
import com.example.familyphotoframe.data.diagnostics.ProcessStartClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.example.familyphotoframe.data.diagnostics.RuntimeSampler
import com.example.familyphotoframe.domain.engine.PlaybackMemoryState
import com.example.familyphotoframe.domain.engine.MemorySelfRecoveryAction
import com.example.familyphotoframe.domain.engine.MemorySelfRecoveryPolicy
import com.example.familyphotoframe.domain.engine.MemorySelfRecoveryState
import com.example.familyphotoframe.domain.engine.TrimMemoryPolicy
import com.example.familyphotoframe.domain.engine.TrimMemoryResponse
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Application entry point. Owns the [ServiceLocator] and, in debug builds, installs
 * a strict StrictMode policy (Contract Rule 2): any disk or network access on the
 * main thread is logged and flagged. The Phase 0 acceptance run (spec §22.1) asserts
 * there are no StrictMode violations during normal operation.
 */
class App : Application() {

    lateinit var services: ServiceLocator
        private set

    /** Application-lifetime scope for work that outlives any single screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastPressureClearAtMs: Long = 0L
    private var memorySelfRecoveryState = MemorySelfRecoveryState()
    private lateinit var crashEnvelopeStore: CrashEnvelopeStore
    private lateinit var processSessionId: String
    private var stallWatchdog: MainThreadStallWatchdog? = null

    override fun onCreate() {
        super.onCreate()
        DiagnosticIdentityHasher.install(DiagnosticIdentityKeyStore(this).loadOrCreate())
        if (BuildConfig.DEBUG) installStrictMode()
        services = ServiceLocator(this)
        seedRuntimeSnapshot()

        // Attach durable logging before anything else is recorded. A new session id per
        // process is what makes restarts — expected (reboot) or not (crash, low-memory
        // kill) — visible to later analysis; without it a 24h run looks like one
        // continuous stream even if the app died repeatedly.
        // Durable event identity uses (sessionId, sequence); retain the full 128-bit UUID
        // so fleet-scale or long-running restart history cannot collide on a 32-bit prefix.
        processSessionId = java.util.UUID.randomUUID().toString()
        crashEnvelopeStore = createCrashEnvelopeStore()
        val previousEnvelope = crashEnvelopeStore.read()
        services.diagnostics.attachSink(
            services.diagnosticsSink, services.diagnosticsBulkSink, processSessionId,
        )
        services.diagnostics.updateCrashEnvelopeHealth(
            previousEnvelope !is CrashEnvelopeStore.ReadResult.Missing,
            crashEnvelopeStore.sizeBytes(),
        )
        installCrashRecorder()
        val processEvidencePreferences = getSharedPreferences(PROCESS_START_PREFS, MODE_PRIVATE)
        val currentElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val currentWallClockMs = System.currentTimeMillis()
        val previousElapsedRealtimeMs = processEvidencePreferences
            .getLong(KEY_LAST_OBSERVED_ELAPSED_REALTIME_MS, -1L).takeIf { it >= 0L }
        val previousEstimatedBootEpochMs = processEvidencePreferences
            .getLong(KEY_LAST_ESTIMATED_BOOT_EPOCH_MS, -1L).takeIf { it >= 0L }
        val previousMarkerWallClockMs = processEvidencePreferences
            .getLong(KEY_LAST_MARKER_WALL_CLOCK_MS, -1L).takeIf { it >= 0L }
        val processStartClassification = ProcessStartClassifier.classify(
            previousElapsedRealtimeMs = previousElapsedRealtimeMs,
            previousEstimatedBootEpochMs = previousEstimatedBootEpochMs,
            currentWallClockMs = currentWallClockMs,
            currentElapsedRealtimeMs = currentElapsedRealtimeMs,
        )

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val imageCacheMaxBytes = com.example.familyphotoframe.util.ImageMemoryBudget.bytesForHeap(
            Runtime.getRuntime().maxMemory(),
        )
        services.diagnostics.logEvent(
            "SESSION_START",
            mapOf(
                "appVersion" to BuildConfig.VERSION_NAME,
                "versionCode" to BuildConfig.VERSION_CODE.toString(),
                "buildType" to BuildConfig.BUILD_TYPE,
                "sdkInt" to android.os.Build.VERSION.SDK_INT.toString(),
                "deviceModel" to "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}",
                "processId" to Process.myPid().toString(),
                "processStartKind" to processStartClassification.kind.name,
                "currentElapsedRealtimeMs" to currentElapsedRealtimeMs.toString(),
                "previousElapsedRealtimeMs" to previousElapsedRealtimeMs?.toString().orEmpty(),
                "estimatedBootEpochMs" to processStartClassification.estimatedBootEpochMs.toString(),
                "previousEstimatedBootEpochMs" to previousEstimatedBootEpochMs?.toString().orEmpty(),
                "bootEpochDeltaMs" to processStartClassification.bootEpochDeltaMs?.toString().orEmpty(),
                "previousMarkerAgeMs" to previousMarkerWallClockMs?.let {
                    (currentWallClockMs - it).coerceAtLeast(0L).toString()
                }.orEmpty(),
                "heapMaxKb" to (Runtime.getRuntime().maxMemory() / 1024L).toString(),
                "memoryClassMb" to activityManager.memoryClass.toString(),
                "lowRam" to activityManager.isLowRamDevice.toString(),
                "imageCacheMaxKb" to (imageCacheMaxBytes / 1024L).toString(),
                "retainedBytes" to services.diagnostics.durableBytes().toString(),
                "queueCapacity" to services.diagnostics.writerQueueCapacity().toString(),
                "queueDepth" to services.diagnostics.writerQueueDepth().toString(),
                "droppedTotal" to services.diagnostics.totalDroppedEvents().toString(),
                "sinkStatus" to (services.diagnosticsSink.lastError
                    ?: services.diagnosticsBulkSink.lastError ?: "OK"),
            ),
            DiagnosticContext(origin = DiagnosticOrigin.APP),
        )
        persistProcessRuntimeMarker(currentWallClockMs, currentElapsedRealtimeMs)
        services.diagnostics.logEvent(
            "APP_CREATE",
            mapOf("appVersion" to BuildConfig.VERSION_NAME),
            DiagnosticContext(origin = DiagnosticOrigin.APP),
        )
        startMainThreadWatchdog()
        appScope.launch {
            reportCompletedMemoryProcessRecovery()
            recoverPreviousEvidence(previousEnvelope)
            inspectPreviousProcessExit(previousEnvelope is CrashEnvelopeStore.ReadResult.Valid)
            migrateLegacyCrashMarker()
        }

        // Periodic heap/uptime samples for the §22.2 soak criteria. The sampler also
        // carries PSS/native/cache fields; a pressure guard clears only Coil's reusable
        // cache, never the currently rendered drawable.
        appScope.launch {
            var lastSampleAtMs = Long.MIN_VALUE
            while (isActive) {
                val elapsedMs = android.os.SystemClock.elapsedRealtime()
                val reading = if (
                    lastSampleAtMs == Long.MIN_VALUE ||
                    elapsedMs - lastSampleAtMs >= RuntimeSampler.DEFAULT_INTERVAL_MS
                ) {
                    lastSampleAtMs = elapsedMs
                    persistProcessRuntimeMarker(System.currentTimeMillis(), elapsedMs)
                    services.runtimeSampler.sample()
                } else {
                    services.runtimeSampler.reading()
                }
                val pressure = if (reading.maxBytes > 0L) {
                    reading.usedBytes.toDouble() / reading.maxBytes.toDouble()
                } else 0.0
                val previousProtection = services.playbackMemoryGuard.snapshot()
                val protection = services.playbackMemoryGuard.recordHeap(
                    heapUsedBytes = reading.usedBytes,
                    heapMaxBytes = reading.maxBytes,
                    nowElapsedMs = elapsedMs,
                )
                if (protection.level != previousProtection.level) {
                    onMemoryProtectionChanged(previousProtection, protection, "heap_sample")
                }
                evaluateMemorySelfRecovery(protection, elapsedMs)
                val now = System.currentTimeMillis()
                if (pressure >= HEAP_PRESSURE_RATIO &&
                    now - lastPressureClearAtMs >= PRESSURE_CLEAR_COOLDOWN_MS
                ) {
                    clearImageMemoryCache("heap_${(pressure * 100).toInt()}pct")
                    lastPressureClearAtMs = now
                }
                delay(RuntimeSampler.PRESSURE_CHECK_INTERVAL_MS)
            }
        }
        // Start/stop the embedded web server to match settings (spec §15.1).
        services.webServer.observe(appScope)
    }

    /** Persist a bounded crash envelope before delegating to the platform handler. */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                runCatching {
                    val context = crashCaptureContext()
                    val envelope = CrashEnvelopeFactory.crash(
                        throwable = throwable,
                        thread = thread,
                        mainThread = thread === Looper.getMainLooper().thread,
                        context = context,
                    )
                    val persisted = crashEnvelopeStore.write(envelope)
                    services.diagnostics.updateCrashEnvelopeHealth(
                        persisted,
                        if (persisted) crashEnvelopeStore.sizeBytes() else 0,
                    )
                    val active = context.activeOperation
                    services.diagnostics.logEvent(
                        "UNCAUGHT_EXCEPTION",
                        envelopeFields(envelope) + mapOf(
                            "checksumValid" to persisted.toString(),
                            "evidenceIncomplete" to (!persisted).toString(),
                        ),
                        DiagnosticContext(
                            origin = DiagnosticOrigin.SYSTEM,
                            operationId = active?.id,
                            parentOperationId = active?.parentOperationId,
                        ),
                    )
                }
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                }
            }
        }
    }

    private fun createCrashEnvelopeStore(): CrashEnvelopeStore {
        val preferences = getSharedPreferences(CRASH_ENVELOPE_PREFS, MODE_PRIVATE)
        return CrashEnvelopeStore(object : CrashEnvelopeStore.Storage {
            override fun read(): String? = preferences.getString(KEY_CRASH_ENVELOPE, null)
            override fun write(value: String): Boolean =
                preferences.edit().putString(KEY_CRASH_ENVELOPE, value).commit()
            override fun clear(): Boolean = preferences.edit().remove(KEY_CRASH_ENVELOPE).commit()
        })
    }

    private fun crashCaptureContext(): CrashCaptureContext = CrashCaptureContext(
        atEpochMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        sessionId = processSessionId,
        lastSequence = services.diagnostics.lastSequence(),
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        sdkInt = android.os.Build.VERSION.SDK_INT,
        runtime = services.diagnosticRuntimeState.snapshot(),
        activeOperation = services.diagnostics.operations.current(),
        queueDepth = services.diagnostics.writerQueueDepth(),
        droppedCount = services.diagnostics.totalDroppedEvents(),
        sinkErrorClass = services.diagnosticsSink.lastError
            ?: services.diagnosticsBulkSink.lastError.orEmpty(),
    )

    private fun envelopeFields(envelope: CrashEnvelope): Map<String, String> = mapOf(
        "exceptionClass" to envelope.exceptionClass,
        "rootCauseClass" to envelope.rootCauseClass,
        "threadName" to envelope.threadName,
        "mainThread" to envelope.mainThread.toString(),
        "crashOrigin" to envelope.frames.joinToString(">") {
            "${it.className}.${it.methodName}:${it.lineNumber}"
        }.take(480),
        "previousSessionId" to envelope.previousSessionId,
        "lastSequence" to envelope.lastSequence.toString(),
        "crashAtEpochMs" to envelope.atEpochMs.toString(),
        "crashElapsedRealtimeMs" to envelope.elapsedRealtimeMs.toString(),
        "heapUsedKb" to envelope.heapUsedKb.toString(),
        "heapMaxKb" to envelope.heapMaxKb.toString(),
        "nativeHeapKb" to envelope.nativeHeapKb.toString(),
        "pssKb" to envelope.pssKb.toString(),
        "imageCacheKb" to envelope.imageCacheKb.toString(),
        "surface" to envelope.surface,
        "engineState" to envelope.engineState,
        "presentationToken" to envelope.presentationToken,
        "sourceKind" to envelope.sourceKind,
        "layout" to envelope.layout,
        "transitionCode" to envelope.transitionCode,
        "activeOperationId" to envelope.activeOperationId,
        "activeOperationStage" to envelope.activeOperationStage,
        "queueDepth" to envelope.queueDepth.toString(),
        "droppedTotal" to envelope.droppedCount.toString(),
        "sinkStatus" to envelope.sinkErrorClass.ifEmpty { "OK" },
        "stallDurationMs" to envelope.stallDurationMs.toString(),
        "preparedSlideCount" to envelope.preparedSlideCount.toString(),
        "renderedSlideCount" to envelope.renderedSlideCount.toString(),
        "decodedBitmapCount" to envelope.decodedBitmapCount.toString(),
        "appBitmapCount" to envelope.appBitmapCount.toString(),
        "activeDecodedBytes" to envelope.activeDecodedBytes.toString(),
        "pendingDisposals" to envelope.pendingDisposals.toString(),
        "memoryProtectionLevel" to envelope.memoryProtectionLevel,
        "oomCount" to envelope.oomCount.toString(),
    )

    private fun recoverPreviousEvidence(result: CrashEnvelopeStore.ReadResult) {
        when (result) {
            CrashEnvelopeStore.ReadResult.Missing -> return
            is CrashEnvelopeStore.ReadResult.Corrupt -> {
                services.diagnostics.logEvent(
                    "PREVIOUS_CRASH_EVIDENCE",
                    mapOf(
                        "checksumValid" to "false",
                        "evidenceIncomplete" to "true",
                        "reason" to result.reason,
                        "crashEnvelopeBytes" to result.sizeBytes.toString(),
                    ),
                    DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                )
            }
            is CrashEnvelopeStore.ReadResult.Valid -> {
                services.diagnostics.logEvent(
                    if (result.envelope.kind == CrashEnvelope.Kind.CRASH) {
                        "PREVIOUS_CRASH_EVIDENCE"
                    } else {
                        "PREVIOUS_ANR_EVIDENCE"
                    },
                    envelopeFields(result.envelope) + mapOf(
                        "checksumValid" to "true",
                        "crashEnvelopeBytes" to result.sizeBytes.toString(),
                    ),
                    DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                )
            }
        }
        if (flushSucceeded() && crashEnvelopeStore.clearIfUnchanged(result)) {
            services.diagnostics.updateCrashEnvelopeHealth(false, 0)
        }
    }

    private fun inspectPreviousProcessExit(correlatedEnvelope: Boolean) {
        val inspector = ProcessExitInspector(this)
        val evidence = runCatching { inspector.inspect(correlatedEnvelope) }.getOrNull() ?: return
        services.diagnostics.logEvent(
            "PROCESS_EXIT_RECORDED",
            mapOf(
                "exitReasonCode" to evidence.reasonCode.toString(),
                "exitReason" to evidence.reasonName,
                "exitTimestampMs" to evidence.timestampMs.toString(),
                "importance" to evidence.importance.toString(),
                "pssKb" to evidence.pssKb.toString(),
                "rssKb" to evidence.rssKb.toString(),
                "descriptionCode" to evidence.descriptionCode,
                "correlatedEnvelope" to evidence.correlatedEnvelope.toString(),
            ),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
        if (flushSucceeded()) inspector.markReported(evidence)
    }

    private fun migrateLegacyCrashMarker() {
        val legacy = getSharedPreferences(LEGACY_CRASH_PREFS, MODE_PRIVATE)
        val exceptionClass = legacy.getString(KEY_PREVIOUS_EXCEPTION, null) ?: return
        services.diagnostics.logEvent(
            "PREVIOUS_UNCAUGHT_EXCEPTION",
            mapOf(
                "exceptionClass" to exceptionClass,
                "crashOrigin" to legacy.getString(KEY_PREVIOUS_ORIGIN, null).orEmpty(),
                "evidenceIncomplete" to "true",
            ),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
        if (flushSucceeded()) {
            legacy.edit().remove(KEY_PREVIOUS_EXCEPTION).remove(KEY_PREVIOUS_ORIGIN).commit()
        }
    }

    private fun startMainThreadWatchdog() {
        stallWatchdog = MainThreadStallWatchdog(
            diagnostics = services.diagnostics,
            captureEnvelope = { durationMs, mainThread ->
                val envelope = CrashEnvelopeFactory.anr(
                    mainThread = mainThread,
                    stallDurationMs = durationMs,
                    context = crashCaptureContext(),
                )
                val persisted = crashEnvelopeStore.write(envelope)
                services.diagnostics.updateCrashEnvelopeHealth(
                    persisted,
                    if (persisted) crashEnvelopeStore.sizeBytes() else 0,
                )
            },
            clearRecoveredEnvelope = {
                if (flushSucceeded()) {
                    if (crashEnvelopeStore.clearIf(CrashEnvelope.Kind.ANR, processSessionId)) {
                        services.diagnostics.updateCrashEnvelopeHealth(false, 0)
                    }
                }
            },
        ).also { it.start() }
    }

    private fun seedRuntimeSnapshot() {
        val runtime = Runtime.getRuntime()
        services.diagnosticRuntimeState.updateMemory(
            com.example.familyphotoframe.data.diagnostics.DiagnosticRuntimeState.Memory(
                heapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
                heapMaxKb = runtime.maxMemory() / 1024L,
                nativeHeapKb = android.os.Debug.getNativeHeapAllocatedSize() / 1024L,
                pssKb = android.os.Debug.getPss().toLong(),
                imageCacheKb = 0L,
                sampledAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    private fun persistProcessRuntimeMarker(wallClockMs: Long, elapsedRealtimeMs: Long) {
        getSharedPreferences(PROCESS_START_PREFS, MODE_PRIVATE).edit()
            .putLong(KEY_LAST_OBSERVED_ELAPSED_REALTIME_MS, elapsedRealtimeMs)
            .putLong(KEY_LAST_ESTIMATED_BOOT_EPOCH_MS, wallClockMs - elapsedRealtimeMs.coerceAtLeast(0L))
            .putLong(KEY_LAST_MARKER_WALL_CLOCK_MS, wallClockMs)
            .apply()
    }

    private fun flushSucceeded(): Boolean =
        services.diagnostics.flushDurable() &&
            services.diagnosticsSink.lastError == null &&
            services.diagnosticsBulkSink.lastError == null

    /**
     * The `RUNNING_*` levels are deprecated because Android 15+ no longer delivers them,
     * but `minSdk = 21` and the target frame hardware still does, so [TrimMemoryPolicy]
     * keeps reading them deliberately. It also keeps the background LRU levels away from
     * the playback guard — see the policy for why that distinction matters here.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val response = TrimMemoryPolicy.classify(level)
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY, "TRIM_MEMORY",
            "level" to level.toString(),
            "response" to response.name,
        )
        if (TrimMemoryPolicy.shouldClearImageCache(level)) {
            clearImageMemoryCache("trim_$level")
        }
        val severe = when (response) {
            TrimMemoryResponse.SEVERE_PRESSURE -> true
            TrimMemoryResponse.RUNNING_PRESSURE -> false
            TrimMemoryResponse.BACKGROUND_ONLY, TrimMemoryResponse.NONE -> return
        }
        val previous = services.playbackMemoryGuard.snapshot()
        val current = services.playbackMemoryGuard.recordSystemPressure(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            severe = severe,
        )
        if (current.level != previous.level) {
            onMemoryProtectionChanged(previous, current, "trim_$level", clearCaches = false)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        services.diagnostics.log(DiagnosticsLog.Category.MEMORY, "LOW_MEMORY")
        clearImageMemoryCache("low_memory")
        val previous = services.playbackMemoryGuard.snapshot()
        val current = services.playbackMemoryGuard.recordSystemPressure(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            severe = true,
        )
        if (current.level != previous.level) {
            onMemoryProtectionChanged(previous, current, "low_memory", clearCaches = false)
        }
    }

    private fun onMemoryProtectionChanged(
        previous: PlaybackMemoryState,
        current: PlaybackMemoryState,
        trigger: String,
        clearCaches: Boolean = true,
    ) {
        services.diagnosticRuntimeState.updateBitmapInventory(
            services.diagnosticRuntimeState.snapshot().bitmaps.copy(
                memoryProtectionLevel = current.level.name,
                oomCount = current.totalOomCount,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )
        if (clearCaches &&
            current.level != com.example.familyphotoframe.domain.engine.PlaybackMemoryLevel.NORMAL
        ) {
            clearImageMemoryCache("guard_${current.level.name.lowercase()}")
            lastPressureClearAtMs = System.currentTimeMillis()
        }
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY,
            "MEMORY_PROTECTION_CHANGED",
            "trigger" to trigger,
            "previousLevel" to previous.level.name,
            "memoryProtectionLevel" to current.level.name,
            "pressurePercent" to current.pressurePercent.toString(),
            "preloadAllowed" to current.allowNextPreload.toString(),
            "maxCollagePhotos" to current.maxCollagePhotos.toString(),
            "targetScalePercent" to (current.decodeScale * 100f).toInt().toString(),
            "oomCount" to current.totalOomCount.toString(),
        )
    }

    private fun clearImageMemoryCache(trigger: String) {
        val cache = services.imageLoader.memoryCache
        val before = cache?.size ?: 0
        cache?.clear()
        services.webServer.clearPreview()
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY, "IMAGE_CACHE_CLEARED",
            "trigger" to trigger,
            "beforeKb" to (before / 1024L).toString(),
            "webPreviewCleared" to "true",
        )
    }

    /**
     * API 21-25 are the field devices on which decode OOMs have left the Java heap
     * effectively pinned.  Give the normal circuit breaker a full minute to recover,
     * request one GC, then relaunch only if a later sample is still critically full.
     */
    private suspend fun evaluateMemorySelfRecovery(
        protection: PlaybackMemoryState,
        elapsedMs: Long,
    ) {
        if (Build.VERSION.SDK_INT !in LEGACY_RECOVERY_MIN_SDK..LEGACY_RECOVERY_MAX_SDK) {
            memorySelfRecoveryState = MemorySelfRecoveryState()
            return
        }
        val decision = MemorySelfRecoveryPolicy.evaluate(
            previous = memorySelfRecoveryState,
            memoryLevel = protection.level,
            oomCount = protection.totalOomCount,
            lastOomElapsedMs = protection.lastOomElapsedMs,
            pressurePercent = protection.pressurePercent,
            nowElapsedMs = elapsedMs,
        )
        memorySelfRecoveryState = decision.state
        when (decision.action) {
            MemorySelfRecoveryAction.NONE -> Unit
            MemorySelfRecoveryAction.REQUEST_GC -> {
                clearImageMemoryCache("self_recovery_gc")
                services.diagnostics.logEvent(
                    "MEMORY_SELF_RECOVERY_GC",
                    memoryRecoveryFields(protection) + mapOf(
                        "trigger" to "sustained_decode_oom_pressure",
                        "gcRequested" to "true",
                    ),
                    DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
                )
                Runtime.getRuntime().gc()
            }
            MemorySelfRecoveryAction.RESTART_PROCESS -> restartAfterPinnedHeap(protection)
        }
    }

    private suspend fun restartAfterPinnedHeap(protection: PlaybackMemoryState) {
        val preferences = getSharedPreferences(MEMORY_RECOVERY_PREFS, MODE_PRIVATE)
        val nowEpochMs = System.currentTimeMillis()
        val lastRestartEpochMs = preferences.getLong(KEY_LAST_MEMORY_RESTART_EPOCH_MS, 0L)
        val sinceLastRestartMs = nowEpochMs - lastRestartEpochMs
        if (lastRestartEpochMs > 0L &&
            sinceLastRestartMs >= 0L && sinceLastRestartMs < MEMORY_RESTART_MIN_INTERVAL_MS
        ) {
            services.diagnostics.logEvent(
                "MEMORY_PROCESS_RESTART_SUPPRESSED",
                memoryRecoveryFields(protection) + mapOf(
                    "reason" to "restart_rate_limit",
                    "durationMs" to (MEMORY_RESTART_MIN_INTERVAL_MS - sinceLastRestartMs).toString(),
                ),
                DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
            )
            return
        }

        val previousRestartEpochMs = lastRestartEpochMs
        val persisted = preferences.edit()
            .putLong(KEY_LAST_MEMORY_RESTART_EPOCH_MS, nowEpochMs)
            .putBoolean(KEY_MEMORY_RESTART_PENDING, true)
            .putInt(KEY_MEMORY_RESTART_PRESSURE_PERCENT, protection.pressurePercent)
            .putLong(KEY_MEMORY_RESTART_HEAP_USED_KB, protection.heapUsedBytes / 1024L)
            .putLong(KEY_MEMORY_RESTART_HEAP_MAX_KB, protection.heapMaxBytes / 1024L)
            .putLong(KEY_MEMORY_RESTART_OOM_COUNT, protection.totalOomCount)
            .commit()
        if (!persisted) {
            services.diagnostics.logEvent(
                "MEMORY_PROCESS_RESTART_FAILED",
                memoryRecoveryFields(protection) + mapOf(
                    "reason" to "restart_state_persist_failed",
                    "errorClass" to "SharedPreferencesCommitFailed",
                ),
                DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
            )
            return
        }

        val scheduleFailure = runCatching { scheduleLegacyProcessRelaunch() }.exceptionOrNull()
        if (scheduleFailure != null) {
            val restore = preferences.edit().remove(KEY_MEMORY_RESTART_PENDING)
            if (previousRestartEpochMs > 0L) {
                restore.putLong(KEY_LAST_MEMORY_RESTART_EPOCH_MS, previousRestartEpochMs)
            } else {
                restore.remove(KEY_LAST_MEMORY_RESTART_EPOCH_MS)
            }
            restore.commit()
            services.diagnostics.logEvent(
                "MEMORY_PROCESS_RESTART_FAILED",
                memoryRecoveryFields(protection) + mapOf(
                    "reason" to "alarm_schedule_failed",
                    "errorClass" to scheduleFailure.javaClass.simpleName,
                ),
                DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
            )
            return
        }

        services.diagnostics.logEvent(
            "MEMORY_PROCESS_RESTART_SCHEDULED",
            memoryRecoveryFields(protection) + mapOf(
                "reason" to "legacy_heap_pinned_after_decode_oom",
            ),
            DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
        )
        flushSucceeded()
        delay(MEMORY_RESTART_FLUSH_GRACE_MS)
        Process.killProcess(Process.myPid())
    }

    @Suppress("DEPRECATION")
    private fun scheduleLegacyProcessRelaunch() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingFlags = PendingIntent.FLAG_CANCEL_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            this,
            MEMORY_RECOVERY_REQUEST_CODE,
            intent,
            pendingFlags,
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + MEMORY_RESTART_RELAUNCH_DELAY_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun reportCompletedMemoryProcessRecovery() {
        val preferences = getSharedPreferences(MEMORY_RECOVERY_PREFS, MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_MEMORY_RESTART_PENDING, false)) return
        services.diagnostics.logEvent(
            "MEMORY_PROCESS_RECOVERY_COMPLETED",
            mapOf(
                "reason" to "alarm_relaunch",
                "pressurePercent" to preferences.getInt(KEY_MEMORY_RESTART_PRESSURE_PERCENT, 0).toString(),
                "heapUsedKb" to preferences.getLong(KEY_MEMORY_RESTART_HEAP_USED_KB, 0L).toString(),
                "heapMaxKb" to preferences.getLong(KEY_MEMORY_RESTART_HEAP_MAX_KB, 0L).toString(),
                "oomCount" to preferences.getLong(KEY_MEMORY_RESTART_OOM_COUNT, 0L).toString(),
            ),
            DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
        )
        preferences.edit()
            .remove(KEY_MEMORY_RESTART_PENDING)
            .remove(KEY_MEMORY_RESTART_PRESSURE_PERCENT)
            .remove(KEY_MEMORY_RESTART_HEAP_USED_KB)
            .remove(KEY_MEMORY_RESTART_HEAP_MAX_KB)
            .remove(KEY_MEMORY_RESTART_OOM_COUNT)
            .apply()
    }

    private fun memoryRecoveryFields(protection: PlaybackMemoryState): Map<String, String> = mapOf(
        "memoryProtectionLevel" to protection.level.name,
        "pressurePercent" to protection.pressurePercent.toString(),
        "heapUsedKb" to (protection.heapUsedBytes / 1024L).toString(),
        "heapMaxKb" to (protection.heapMaxBytes / 1024L).toString(),
        "oomCount" to protection.totalOomCount.toString(),
    )

    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }

    private companion object {
        const val HEAP_PRESSURE_RATIO = 0.75
        const val PROCESS_START_PREFS = "process_start_evidence"
        const val KEY_LAST_OBSERVED_ELAPSED_REALTIME_MS = "last_observed_elapsed_realtime_ms"
        const val KEY_LAST_ESTIMATED_BOOT_EPOCH_MS = "last_estimated_boot_epoch_ms"
        const val KEY_LAST_MARKER_WALL_CLOCK_MS = "last_marker_wall_clock_ms"
        const val PRESSURE_CLEAR_COOLDOWN_MS = 5L * 60_000L
        const val LEGACY_RECOVERY_MIN_SDK = 21
        const val LEGACY_RECOVERY_MAX_SDK = 25
        const val MEMORY_RESTART_MIN_INTERVAL_MS = 15L * 60_000L
        const val MEMORY_RESTART_RELAUNCH_DELAY_MS = 1_500L
        const val MEMORY_RESTART_FLUSH_GRACE_MS = 250L
        const val MEMORY_RECOVERY_REQUEST_CODE = 0x4D52
        const val MEMORY_RECOVERY_PREFS = "memory_process_recovery"
        const val KEY_LAST_MEMORY_RESTART_EPOCH_MS = "last_restart_epoch_ms"
        const val KEY_MEMORY_RESTART_PENDING = "restart_pending"
        const val KEY_MEMORY_RESTART_PRESSURE_PERCENT = "pressure_percent"
        const val KEY_MEMORY_RESTART_HEAP_USED_KB = "heap_used_kb"
        const val KEY_MEMORY_RESTART_HEAP_MAX_KB = "heap_max_kb"
        const val KEY_MEMORY_RESTART_OOM_COUNT = "oom_count"
        const val CRASH_ENVELOPE_PREFS = "diagnostics_crash_envelope"
        const val KEY_CRASH_ENVELOPE = "envelope"
        const val LEGACY_CRASH_PREFS = "process_diagnostics"
        const val KEY_PREVIOUS_EXCEPTION = "previous_exception_class"
        const val KEY_PREVIOUS_ORIGIN = "previous_exception_origin"
    }
}
