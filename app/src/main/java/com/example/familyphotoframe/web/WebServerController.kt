package com.example.familyphotoframe.web

import android.app.ActivityManager
import android.content.Context
import com.example.familyphotoframe.BuildConfig
import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.diagnostics.BatteryTelemetry
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.DiagnosticsBundleContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticRuntimeState
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.data.settings.CredentialPolicy
import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.ConfigTransfer
import com.example.familyphotoframe.data.settings.ImportResult
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.MotionMode
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import com.example.familyphotoframe.data.settings.OverlayPosition
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.data.settings.SettingsRepository
import com.example.familyphotoframe.data.settings.UnreachablePolicy
import com.example.familyphotoframe.data.settings.SlideshowPlaylist
import com.example.familyphotoframe.data.settings.PlaylistScheduleRule
import com.example.familyphotoframe.data.settings.PlaylistSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.data.settings.UploadDuplicatePolicy
import com.example.familyphotoframe.data.source.BuiltInSourceIds
import com.example.familyphotoframe.data.source.SynologyApi
import com.example.familyphotoframe.domain.engine.SlideshowEngine
import com.example.familyphotoframe.domain.engine.SourceStatusPolicy
import com.example.familyphotoframe.domain.engine.EngineState
import com.example.familyphotoframe.domain.schedule.RescanSchedule
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.net.Inet4Address
import java.net.NetworkInterface
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the embedded web server's lifecycle (spec §15.1). The server is **off by
 * default** and only started when the user enables it; it binds to a single
 * private-LAN IPv4 address so it is never offered on a public interface
 * (spec §15.5). Enabling it arms a fresh pairing PIN for the device screen.
 *
 * Actions that need the running slideshow (rescan, source test) are delegated to a
 * [FrameControls] registered by the ViewModel; when the UI is not active those
 * routes report that the frame is not running rather than failing obscurely.
 */
class WebServerController(
    private val settings: SettingsRepository,
    private val photoDao: PhotoDao,
    private val engine: SlideshowEngine,
    private val diagnostics: DiagnosticsLog,
    private val context: Context,
    private val uploadManager: WebUploadManager? = null,
    private val rememberedBrowsers: RememberedBrowserManager? = null,
    private val allowHeif: Boolean = true,
    private val diagnosticRuntimeState: DiagnosticRuntimeState = DiagnosticRuntimeState(),
    private val localThumbnailCache: com.example.familyphotoframe.data.cache.LocalThumbnailCache? = null,
    /** Mirrors the tier line the frame's own Device page shows; see DeviceMemoryTierPolicy. */
    private val lowMemoryTier: Boolean = false,
) {
    /** Permission-free device snapshot; see BatteryTelemetry's own doc comment. */
    private val batteryTelemetry = BatteryTelemetry(context)

    /** Slideshow-dependent operations the web API can trigger. */
    interface FrameControls {
        suspend fun rescan(): String?
        suspend fun testSavedSource(): String
        suspend fun previewFolderOnce(folderKey: String): String? = "Folder preview is unavailable"
        suspend fun useFolderInActivePlaylist(folderKey: String): String? = "Playlist folder action is unavailable"
        suspend fun clearMediaCache(): String? = "Media cache control is unavailable"
        suspend fun clearLocalThumbnailCache(): String? = "Local photo cache control is unavailable"
        suspend fun rebuildLocalThumbnailCache(): String? = "Local photo cache control is unavailable"
        suspend fun previewOnThisDay(): String? = "On this day preview is unavailable"
        suspend fun restartApplication(): String? = "Application restart is unavailable"
        suspend fun factoryReset(): String? = "Factory reset is unavailable"

        /** Frame timing lives in the running UI, so this is only possible while it is up. */
        suspend fun capturePerformanceSample(): String? = "Performance capture needs the frame running"
    }

    var controls: FrameControls? = null

    @Volatile private var server: WebConfigServer? = null
    @Volatile private var security: WebSecurity? = null
    @Volatile private var boundUrl: String? = null
    private val previewStore = WebPreviewStore()
    private val startedAtElapsedMs = SystemClock.elapsedRealtime()
    @Volatile private var lastPort: Int = 8080
    @Volatile private var lastIdleTimeoutMs: Long = 30L * 60_000L
    @Volatile private var rollbackBackup: String? = null
    private val lifecycleLock = Any()
    private val lifecycleGeneration = AtomicLong(0L)
    @Volatile private var desiredEnabled: Boolean = false
    @Volatile private var terminalMaintenanceHold: Boolean = false
    private val settingsPatchApplier = WebSettingsPatchApplier(settings, photoDao)

    /** Publish one low-resolution preview after a presentation is committed. */
    fun publishPreview(frame: WebPreviewFrame) {
        if (!previewStore.update(frame)) return
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "WEB_PREVIEW_GENERATED",
            "revision" to frame.revision,
            "bytes" to frame.jpeg.size.toString(),
            "type" to frame.type,
        )
    }

    /**
     * Preview rendering is expensive on a 100 MB heap. Generate only while an
     * authenticated browser has made a request recently; an enabled but unused web
     * server must not create a second image pipeline for every slide.
     */
    fun shouldGeneratePreview(): Boolean =
        server != null && security?.hasRecentlyActiveSession(PREVIEW_ACTIVE_WINDOW_MS) == true

    fun clearPreview() = previewStore.clear()

    /** PIN to display on the frame, or null when the server is off/unpaired. */
    fun visiblePin(): String? = security?.visiblePin()

    /** e.g. `http://192.168.1.42:8080`, or null when the server is not running. */
    fun url(): String? = boundUrl

    /** Watch settings and start/stop the server to match (hot-applies port changes). */
    fun observe(scope: CoroutineScope) {
        scope.launch { runCatching { uploadManager?.cleanupStale() } }
        scope.launch {
            while (isActive) {
                runCatching { rememberedBrowsers?.purge() }
                delay(REMEMBERED_CLEANUP_INTERVAL_MS)
            }
        }
        scope.launch {
            settings.settings
                .map { Triple(it.web.enabled, it.web.portClamped, it.web.idleTimeoutMs) }
                .distinctUntilChanged()
                .collect { (enabled, port, idleMs) ->
                    lifecycleGeneration.incrementAndGet()
                    synchronized(lifecycleLock) {
                        desiredEnabled = enabled
                        lastPort = port
                        lastIdleTimeoutMs = idleMs
                        if (!terminalMaintenanceHold) applyDesiredStateLocked()
                    }
                }
        }
    }

    /** Caller must hold [lifecycleLock]. */
    private fun startLocked(port: Int, idleTimeoutMs: Long) {
        val host = privateLanAddress()
        if (host == null) {
            diagnostics.log(DiagnosticsLog.Category.APP, "WEB_NO_LAN")
            return
        }
        val sec = WebSecurity(idleTimeoutMs = idleTimeoutMs)
        val srv = WebConfigServer(host, port, sec, Backend(), rememberedBrowsers) { code, fields ->
            diagnostics.log(DiagnosticsLog.Category.APP, code, "", fields)
        }
        try {
            srv.start(SOCKET_TIMEOUT_MS, true)
            sec.regeneratePin()
            security = sec
            server = srv
            boundUrl = "http://$host:$port"
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                "WEB_STARTED",
                "port" to port.toString(),
                "bindCategory" to bindCategory(host),
            )
        } catch (e: Exception) {
            runCatching { srv.stop() }
            diagnostics.log(
                DiagnosticsLog.Category.APP, "WEB_START_FAILED",
                "errorClass" to e.javaClass.simpleName,
                "port" to port.toString(),
            )
            security = null
            boundUrl = null
        }
    }

    fun stop() {
        lifecycleGeneration.incrementAndGet()
        synchronized(lifecycleLock) {
            desiredEnabled = false
            stopLocked()
        }
    }

    /** Caller must hold [lifecycleLock]. */
    private fun stopLocked() {
        server?.let {
            runCatching { it.stop() }
            diagnostics.log(DiagnosticsLog.Category.APP, "WEB_STOPPED", "")
        }
        server = null
        security?.reset()
        security = null
        boundUrl = null
        previewStore.clear()
    }

    /** Caller must hold [lifecycleLock]; stop/start is one indivisible generation. */
    private fun applyDesiredStateLocked() {
        stopLocked()
        if (desiredEnabled) startLocked(lastPort, lastIdleTimeoutMs)
    }

    private fun restartServerSoon() {
        val generation = lifecycleGeneration.incrementAndGet()
        Handler(Looper.getMainLooper()).postDelayed({
            synchronized(lifecycleLock) {
                if (generation != lifecycleGeneration.get() || terminalMaintenanceHold || !desiredEnabled) {
                    return@synchronized
                }
                applyDesiredStateLocked()
            }
        }, 500L)
    }

    /** Keep the current request socket alive while an awaited terminal reset commits. */
    fun beginTerminalMaintenance() {
        lifecycleGeneration.incrementAndGet()
        synchronized(lifecycleLock) { terminalMaintenanceHold = true }
    }

    /**
     * Full pairing URL for the on-screen QR code, e.g.
     * `http://192.168.1.42:8080/pair?t=<one-time token>` (spec §15.3), or null when
     * the server is not running. Each call issues a fresh single-use token.
     */
    fun qrPairingUrl(): String? {
        val base = boundUrl ?: return null
        val token = security?.issueQrToken() ?: return null
        return "$base/pair?t=$token"
    }

    /** Invalidate all sessions and show a new PIN (spec §15.2 re-pairing). */
    fun regeneratePin(revokeRememberedBrowsers: Boolean = true): String? {
        if (revokeRememberedBrowsers) {
            runCatching { kotlinx.coroutines.runBlocking { rememberedBrowsers?.revokeAll() } }
        } else {
            diagnostics.log(DiagnosticsLog.Category.APP, "REMEMBERED_BROWSERS_KEPT_AFTER_PIN_RESET")
        }
        security?.revokeAllSessions()
        return security?.regeneratePin()
    }

    /** Close active sessions created from one revoked remembered-browser record. */
    fun revokeRememberedBrowserSessions(id: String) {
        security?.revokeSessionsForRememberedBrowser(id)
    }

    /** Close every active web session after owner-level trust revocation. */
    fun revokeAllWebSessions() {
        security?.revokeAllSessions()
    }

    /** First site-local IPv4 address; null if the device has no private network. */
    private fun privateLanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private fun bindCategory(host: String): String = when {
        ':' in host -> "PRIVATE_IPV6"
        host.startsWith("10.") -> "PRIVATE_IPV4_10"
        host.startsWith("192.168.") -> "PRIVATE_IPV4_192"
        host.startsWith("172.") -> "PRIVATE_IPV4_172"
        else -> "PRIVATE_NETWORK"
    }

    // ---------------- backend ----------------

    /** Per-source indexed counts with their capture time; see [indexedCountsPerSource]. */
    @Volatile private var cachedSourceCounts: Pair<Long, Map<String, Int>>? = null

    private inner class Backend : WebBackend {

        override suspend fun statusJson(): JsonObject {
            val s = settings.settings.first()
            val ui = engine.ui.value
            val sourceIds = sourceIdsFor(s)
            val runtime = Runtime.getRuntime()
            val heapUsed = runtime.totalMemory() - runtime.freeMemory()
            val preview = previewStore.snapshot()
            var indexedPhotos = 0
            for (sourceId in sourceIds) indexedPhotos += photoDao.countForSource(sourceId)
            val fallbackPhotos = photoDao.countForSource(BuiltInSourceIds.FALLBACK)
            val totalPhotos = photoDao.count()
            val heifFlag = if (allowHeif) 1 else 0
            val eligiblePhotos = photoDao.eligibleCount(3, heifFlag)
            val hiddenPhotos = photoDao.hiddenCount()
            val favoritePhotos = photoDao.favoriteCountAll()
            val failedPhotos = photoDao.failedOrUnsupportedCount(3, heifFlag)
            val localUploadPhotos = photoDao.countForSource("local_uploads")
            val freeStorageBytes = android.os.Environment.getDataDirectory().usableSpace.coerceAtLeast(0L)
            val totalStorageBytes = android.os.Environment.getDataDirectory().totalSpace.coerceAtLeast(0L)
            val memInfo = ActivityManager.MemoryInfo()
            runCatching {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memInfo)
            }
            val battery = batteryTelemetry.fields()
            val shuffle = ui.shuffleProgress
            val healthLevel = when {
                eligiblePhotos == 0 || freeStorageBytes < 250L * MB -> "CRITICAL"
                failedPhotos > 0 || freeStorageBytes < 1024L * MB ||
                    shuffle.foldersSkipped > 0 || shuffle.quarantinedPhotos > 0 ||
                    shuffle.unavailableSourceCount > 0 -> "WARNING"
                else -> "OK"
            }
            return buildJsonObject {
                put("online", true)
                put("deviceName", s.source.displayName.ifBlank { "FamilyPhotoFrame" })
                put("appVersion", BuildConfig.VERSION_NAME)
                put("buildType", BuildConfig.BUILD_TYPE)
                put("engineState", ui.state.name)
                put("paused", ui.paused)
                put("sourceKind", s.source.kind.name)
                put("sourceName", s.source.displayName)
                put("sources", sourceStatusJson(s))
                put("indexedPhotos", indexedPhotos)
                put("alsoPlay", s.source.alsoPlay.joinToString(",") { it.name })
                put("fallbackPhotos", fallbackPhotos)
                put("intervalSeconds", s.intervalSecondsClamped)
                put("autoStartOnBoot", s.autoStartOnBoot)
                put("androidSdk", android.os.Build.VERSION.SDK_INT)
                put("deviceModel", android.os.Build.MODEL)
                put("uptimeMs", SystemClock.elapsedRealtime() - startedAtElapsedMs)
                put("uptimeText", formatDuration(SystemClock.elapsedRealtime() - startedAtElapsedMs))
                put("heapUsedMb", heapUsed / MB)
                put("heapMaxMb", runtime.maxMemory() / MB)
                put("pssMb", Debug.getPss() / 1024)
                put("imageCacheMb", 0)
                put("memoryTier", if (lowMemoryTier) "LOW" else "STANDARD")
                put("localThumbnailCacheEnabled", s.localThumbnailCache.enabled)
                put("localThumbnailCacheUsageBytes", localThumbnailCache?.currentSizeBytes() ?: 0L)
                put("localThumbnailCacheMaxBytes", localThumbnailCache?.effectiveMaxBytes() ?: s.localThumbnailCache.maxBytes)
                put("localThumbnailCacheRebuildInProgress", localThumbnailCache?.rebuildInProgress ?: false)
                put("localThumbnailCacheRebuildCount", localThumbnailCache?.rebuildCount ?: 0)
                put("totalStorageBytes", totalStorageBytes)
                put("totalRamBytes", memInfo.totalMem)
                put("freeRamBytes", memInfo.availMem)
                put("batteryLevelPercent", battery["batteryLevelPct"]?.toIntOrNull() ?: -1)
                put("batteryStatus", battery["batteryStatus"] ?: "UNKNOWN")
                put("powerSource", battery["powerSource"] ?: "NONE")
                put("webUrl", boundUrl.orEmpty())
                put("previewAvailable", preview != null)
                put("previewRevision", preview?.revision.orEmpty())
                put("rollbackAvailable", rollbackBackup != null)
                put("healthLevel", healthLevel)
                put("healthHeadline", when (healthLevel) {
                    "OK" -> "Everything is working normally"
                    "WARNING" -> "Attention recommended"
                    else -> "Action required"
                })
                put("totalPhotos", totalPhotos)
                put("eligiblePhotos", eligiblePhotos)
                put("hiddenPhotos", hiddenPhotos)
                put("favoritePhotos", favoritePhotos)
                put("failedPhotos", failedPhotos)
                put("localUploadPhotos", localUploadPhotos)
                put("freeStorageBytes", freeStorageBytes)
                put("activePlaylistId", s.playlists.activePlaylistId)
                put("activePlaylistName", s.playlists.activePlaylist().name)
                put("playlistScheduleEnabled", s.playlists.scheduleEnabled)
                put("brightnessMode", s.brightnessAutomation.mode.name)
                put("activePlaybackOrder", s.selectionMode.name)
                put("shuffleScopeKey", shuffle.scopeKey)
                put("shuffleFolderCycle", shuffle.folderCycle)
                put("shuffleFolderResolved", shuffle.folderResolved)
                put("shuffleFolderTotal", shuffle.folderTotal)
                put("shuffleEligibleFolders", shuffle.eligibleFolderCount)
                put("shuffleFoldersPresented", shuffle.foldersPresented)
                put("shuffleFoldersPending", shuffle.foldersPending)
                put("shuffleFoldersSkipped", shuffle.foldersSkipped)
                put("shuffleFoldersRemoved", shuffle.foldersRemoved)
                put("shuffleCurrentFolder", shuffle.currentFolderKey?.substringAfter('\u001f').orEmpty())
                put("shufflePhotoCycle", shuffle.photoCycle)
                put("shufflePhotoResolved", shuffle.photoResolved)
                put("shufflePhotoTotal", shuffle.photoTotal)
                put("shufflePendingPhotos", shuffle.pendingPhotos)
                put("shuffleQuarantinedPhotos", shuffle.quarantinedPhotos)
                put("shuffleUnavailableSources", shuffle.unavailableSourceCount)
                put("shuffleReservationAgeMs", shuffle.activeReservationAgeMs ?: 0L)
                put("shuffleLastCommitEpochMs", shuffle.lastCommitEpochMs ?: 0L)
                put("shuffleLastReconciliationEpochMs", shuffle.lastReconciliationEpochMs ?: 0L)
                put("shuffleLastRecoveryEpochMs", shuffle.lastRecoveryEpochMs ?: 0L)
            }
        }

        /** Never includes credentials or credentialRef (spec §15.2, §22.3). */
        override suspend fun redactedConfigJson(): JsonObject {
            val current = settings.settings.first()
            return WebSettingsJson.redactedConfig(current, nextScheduleDescription(current))
        }

        override suspend fun applyConfig(patch: JsonObject): String? =
            settingsPatchApplier.apply(patch)

        /**
         * Invoke a slideshow-dependent control.
         *
         * [FrameControls] callbacks report success as `null` and failure as a message, so
         * the absent-controls case must be resolved *before* the result is inspected.
         * Writing `controls?.action() ?: "Frame is not running"` instead collapses a
         * successful null into the not-running message and reports every success as an
         * error.
         */
        private suspend fun withControls(block: suspend (FrameControls) -> String?): String? {
            val frameControls = controls ?: return "Frame is not running"
            return block(frameControls)
        }

        override suspend fun control(action: String): String? = when (action) {
            "next" -> { engine.next(); null }
            "prev", "previous" -> { engine.previous(); null }
            "pause" -> {
                if (!engine.ui.value.paused) engine.togglePause("web")
                null
            }
            "resume", "play" -> {
                if (engine.ui.value.paused) engine.togglePause("web")
                null
            }
            "restart_interval" -> { engine.restartInterval(); null }
            "rescan" -> withControls { it.rescan() }
            else -> "Unknown action"
        }

        override suspend fun testSavedSource(): String =
            controls?.testSavedSource() ?: "Frame is not running"

        override suspend fun settingsRevision(): Long = revisionOf(settings.settings.first())

        override suspend fun foldersJson(): JsonArray {
            val s = settings.settings.first()
            val selected = s.selectedFolders
            val folders = photoDao.folderSummaries(sourceIdsFor(s))
            return buildJsonArray {
                folders.forEach { folder ->
                    add(buildJsonObject {
                        put("key", folder.selectionKey)
                        put("sourceId", folder.sourceId)
                        put("path", folder.displayPath)
                        put("name", folder.name)
                        put("photoCount", folder.photoCount)
                        put(
                            "selected",
                            selected.isEmpty() || folder.selectionKey in selected || folder.name in selected,
                        )
                    })
                }
            }
        }

        override suspend fun folderAction(body: JsonObject): JsonObject {
            val action = body["action"]?.jsonPrimitive?.content?.trim().orEmpty()
            val folderKey = body["folderKey"]?.jsonPrimitive?.content?.trim().orEmpty()
            require(folderKey.isNotBlank()) { "Missing folder key" }
            val s = settings.settings.first()
            val valid = photoDao.folderSummaries(sourceIdsFor(s)).any { it.selectionKey == folderKey }
            require(valid) { "Folder is not available" }
            // Resolved once: these callbacks use null to mean success, so folding the
            // "no controls" case into the same elvis would mask every successful action.
            val frameControls = controls ?: throw IllegalArgumentException("Frame is not running")
            val error = when (action) {
                "preview_once" -> frameControls.previewFolderOnce(folderKey)
                "use_in_playlist" -> frameControls.useFolderInActivePlaylist(folderKey)
                else -> throw IllegalArgumentException("Unknown folder action")
            }
            require(error == null) { error ?: "Folder action failed" }
            return buildJsonObject {
                put("action", action)
                put("folderKey", folderKey)
            }
        }

        override suspend fun presentationJson(): JsonObject {
            val frame = previewStore.snapshot()
            if (frame != null) {
                diagnostics.log(DiagnosticsLog.Category.APP, "WEB_PREVIEW_CACHE_HIT", "revision" to frame.revision)
                return buildJsonObject {
                    put("presentationId", frame.presentationId)
                    put("type", frame.type)
                    put("photoIds", buildJsonArray { frame.photoIds.forEach(::add) })
                    put("fileName", frame.fileName)
                    put("sourceId", frame.sourceId)
                    put("folder", frame.folder)
                    put("transition", frame.transition)
                    put("committedAtEpochMs", frame.committedAtEpochMs)
                    put("previewRevision", frame.revision)
                    put("previewWidth", frame.width)
                    put("previewHeight", frame.height)
                }
            }
            val current = engine.ui.value.current ?: return buildJsonObject { }
            return buildJsonObject {
                put("presentationId", current.id)
                put("type", "preparing")
                put("photoIds", buildJsonArray { add(current.id) })
                put("fileName", current.fileName)
                put("sourceId", current.sourceId)
                put("folder", current.folderName)
                put("transition", "")
                put("committedAtEpochMs", 0L)
                put("previewRevision", "")
            }
        }

        override fun previewSnapshot(): WebPreviewFrame? = previewStore.snapshot()

        override suspend fun diagnosticsSummary(): JsonObject = diagnosticSummary(
            diagnostics.snapshot(1000),
            diagnostics.healthSnapshot(),
        )

        override suspend fun diagnosticsEvents(query: DiagnosticsQuery): JsonObject {
            val all = diagnostics.snapshot(1000)
            val page = DiagnosticsPager.page(all, query)
            val health = diagnostics.healthSnapshot()
            return buildJsonObject {
                put("events", buildJsonArray {
                    page.events.forEach { add(diagnosticEventJson(it)) }
                })
                page.nextCursor?.let { put("nextCursor", it) } ?: put("nextCursor", JsonNull)
                put("hasMore", page.hasMore)
                put("cursorExpired", page.cursorExpired)
                put("summary", diagnosticSummary(all, health))
                put("health", diagnosticHealthJson(health))
                put("warnings", diagnosticWarnings(health, page.cursorExpired))
                put("operationTimeline", diagnosticOperationTimeline(all))
                put("filterOptions", diagnosticFilterOptions(all))
            }
        }

        override suspend fun backupExport(): String = ConfigTransfer.export(
            settings = settings.settings.first(),
            appVersion = BuildConfig.VERSION_NAME,
            nowEpochMs = System.currentTimeMillis(),
        )

        override suspend fun backupValidate(text: String): JsonObject = when (val parsed = ConfigTransfer.parse(text)) {
            is ImportResult.Ok -> buildJsonObject {
                put("valid", true)
                put("appVersion", parsed.bundle.appVersion)
                put("exportedAtEpochMs", parsed.bundle.exportedAtEpochMs)
                put("bundleVersion", parsed.bundle.bundleVersion)
                put("sourceKind", parsed.bundle.settings.source.kind.name)
                put("selectedFolderCount", parsed.bundle.settings.selectedFolders.size)
            }
            is ImportResult.Failed -> throw IllegalArgumentException("Backup rejected: ${parsed.reason.name}")
        }

        override suspend fun backupImport(text: String): String? {
            val parsed = ConfigTransfer.parse(text)
            if (parsed !is ImportResult.Ok) return "Backup rejected: ${(parsed as ImportResult.Failed).reason.name}"
            val previous = backupExport()
            return try {
                settings.update { current -> ConfigTransfer.merge(current, parsed.bundle.settings).withCurrentDefaults() }
                rollbackBackup = previous
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    "WEB_BACKUP_IMPORTED",
                    "appVersion" to parsed.bundle.appVersion.take(40),
                )
                null
            } catch (e: Exception) {
                "Backup apply failed: ${e.javaClass.simpleName}"
            }
        }

        override suspend fun maintenance(action: String): String? {
            return when (action) {
            "clear_cache" -> withControls { it.clearMediaCache() }
            "clear_local_thumbnail_cache" -> withControls { it.clearLocalThumbnailCache() }
            "rebuild_local_thumbnail_cache" -> withControls { it.rebuildLocalThumbnailCache() }
            "preview_on_this_day" -> withControls { it.previewOnThisDay() }
            "clear_suppression" -> {
                for (sourceId in sourceIdsFor(settings.settings.first())) photoDao.clearSuppression(sourceId)
                diagnostics.log(DiagnosticsLog.Category.APP, "WEB_SUPPRESSION_CLEARED")
                null
            }
            "unhide_all" -> {
                photoDao.unhideAll()
                diagnostics.log(DiagnosticsLog.Category.APP, "WEB_UNHIDE_ALL")
                null
            }
            "reset_shuffle_active" -> { engine.resetActiveShuffle(clearHistory = false); null }
            "reset_shuffle_all" -> { engine.resetAllShuffle(clearHistory = false); null }
            "reset_shuffle_all_history" -> { engine.resetAllShuffle(clearHistory = true); null }
            "rollback_backup" -> {
                val backup = rollbackBackup ?: return "No rollback backup is available"
                val parsed = ConfigTransfer.parse(backup)
                if (parsed !is ImportResult.Ok) return "Rollback backup is invalid"
                settings.update { current -> ConfigTransfer.merge(current, parsed.bundle.settings).withCurrentDefaults() }
                rollbackBackup = null
                diagnostics.log(DiagnosticsLog.Category.APP, "WEB_BACKUP_ROLLED_BACK")
                null
            }
            "restart_web" -> {
                restartServerSoon()
                null
            }
            "restart_app" -> withControls { it.restartApplication() }
            "factory_reset" -> withControls { it.factoryReset() }
            "capture_performance_sample" -> withControls { it.capturePerformanceSample() }
                else -> "Unknown maintenance action"
            }
        }

        override suspend fun playlistAction(body: JsonObject): JsonObject {
            val action = body["action"]?.jsonPrimitive?.content.orEmpty()
            val id = body["id"]?.jsonPrimitive?.content.orEmpty()
            when (action) {
                "play" -> settings.update { current ->
                    require(current.playlists.playlists.any { it.id == id && it.enabled }) { "Unknown playlist" }
                    current.copy(playlists = current.playlists.copy(
                        activePlaylistId = id,
                        manualOverrideUntilEpochMs = body["overrideMinutes"]?.jsonPrimitive?.intOrNull?.let { minutes ->
                            if (minutes < 0) Long.MAX_VALUE else System.currentTimeMillis() + minutes.coerceIn(1, 1440) * 60_000L
                        } ?: if (current.playlists.scheduleEnabled) {
                            val now = System.currentTimeMillis()
                            now + com.example.familyphotoframe.domain.schedule.PlaylistSchedule
                                .minutesUntilBoundary(current.playlists.scheduleRules, now) * 60_000L
                        } else 0L,
                    ))
                }
                "create" -> {
                    val name = body["name"]?.jsonPrimitive?.content?.trim()?.take(80).orEmpty()
                    require(name.isNotBlank()) { "Playlist name is required" }
                    settings.update { current ->
                        require(current.playlists.playlists.none { it.name.equals(name, true) }) { "Playlist name already exists" }
                        val now = System.currentTimeMillis()
                        val playlist = SlideshowPlaylist(
                            id = "user_${UUID.randomUUID()}",
                            name = name,
                            favoritesOnly = body["favoritesOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
                            localUploadsOnly = body["localUploadsOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
                            sourceIds = if (body["localUploadsOnly"]?.jsonPrimitive?.booleanOrNull == true) setOf("local_uploads") else emptySet(),
                            folderNames = current.selectedFolders,
                            selectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE,
                            intervalSeconds = current.intervalSecondsClamped,
                            transitionSelectionMode = current.transitionSelectionMode,
                            transition = current.transition,
                            collageMode = current.portraitCollage.mode,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                        )
                        current.copy(playlists = current.playlists.copy(playlists = current.playlists.playlists + playlist))
                    }
                }
                "rename" -> {
                    require(id !in PlaylistSettings.BUILT_IN_IDS) { "Built-in playlists cannot be renamed" }
                    val name = body["name"]?.jsonPrimitive?.content?.trim()?.take(80).orEmpty()
                    require(name.isNotBlank()) { "Playlist name is required" }
                    settings.update { current ->
                        require(current.playlists.playlists.any { it.id == id }) { "Unknown playlist" }
                        require(current.playlists.playlists.none { it.id != id && it.name.equals(name, true) }) { "Playlist name already exists" }
                        current.copy(playlists = current.playlists.copy(
                            playlists = current.playlists.playlists.map {
                                if (it.id == id) it.copy(name = name, updatedAtEpochMs = System.currentTimeMillis()) else it
                            },
                        ))
                    }
                }
                "duplicate" -> settings.update { current ->
                    val original = current.playlists.playlists.firstOrNull { it.id == id }
                        ?: throw IllegalArgumentException("Unknown playlist")
                    val requested = body["name"]?.jsonPrimitive?.content?.trim()?.take(80).orEmpty()
                    val base = requested.ifBlank { "${original.name} copy" }
                    var name = base
                    var suffix = 2
                    while (current.playlists.playlists.any { it.name.equals(name, true) }) {
                        name = "$base $suffix".take(80)
                        suffix += 1
                    }
                    val now = System.currentTimeMillis()
                    val duplicate = original.copy(
                        id = "user_${UUID.randomUUID()}",
                        name = name,
                        enabled = true,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                    )
                    current.copy(playlists = current.playlists.copy(
                        playlists = current.playlists.playlists + duplicate,
                    ))
                }
                "toggle" -> {
                    require(id !in PlaylistSettings.BUILT_IN_IDS) { "Built-in playlists cannot be disabled" }
                    val enabled = body["enabled"]?.jsonPrimitive?.booleanOrNull
                        ?: throw IllegalArgumentException("Missing enabled value")
                    settings.update { current ->
                        require(current.playlists.playlists.any { it.id == id }) { "Unknown playlist" }
                        current.copy(playlists = current.playlists.copy(
                            playlists = current.playlists.playlists.map { if (it.id == id) it.copy(enabled = enabled) else it },
                            activePlaylistId = if (!enabled && current.playlists.activePlaylistId == id) PlaylistSettings.PLAYLIST_ALL else current.playlists.activePlaylistId,
                            defaultPlaylistId = if (!enabled && current.playlists.defaultPlaylistId == id) PlaylistSettings.PLAYLIST_ALL else current.playlists.defaultPlaylistId,
                        ))
                    }
                }
                "set_default" -> settings.update { current ->
                    require(current.playlists.playlists.any { it.id == id && it.enabled }) { "Unknown or disabled playlist" }
                    current.copy(playlists = current.playlists.copy(defaultPlaylistId = id))
                }
                "move" -> {
                    require(id !in PlaylistSettings.BUILT_IN_IDS) { "Built-in playlists cannot be reordered" }
                    val direction = body["direction"]?.jsonPrimitive?.intOrNull?.coerceIn(-1, 1)
                        ?: throw IllegalArgumentException("Missing direction")
                    require(direction != 0) { "Direction must be -1 or 1" }
                    settings.update { current ->
                        val builtIns = current.playlists.playlists.filter { it.id in PlaylistSettings.BUILT_IN_IDS }
                        val users = current.playlists.playlists.filterNot { it.id in PlaylistSettings.BUILT_IN_IDS }.toMutableList()
                        val from = users.indexOfFirst { it.id == id }
                        require(from >= 0) { "Unknown playlist" }
                        val to = (from + direction).coerceIn(0, users.lastIndex)
                        if (from != to) {
                            val item = users.removeAt(from)
                            users.add(to, item)
                        }
                        current.copy(playlists = current.playlists.copy(playlists = builtIns + users))
                    }
                }
                "delete" -> {
                    require(id !in PlaylistSettings.BUILT_IN_IDS) { "Built-in playlists cannot be deleted" }
                    settings.update { current ->
                        require(current.playlists.playlists.any { it.id == id }) { "Unknown playlist" }
                        current.copy(playlists = current.playlists.copy(
                            playlists = current.playlists.playlists.filterNot { it.id == id },
                            activePlaylistId = if (current.playlists.activePlaylistId == id) PlaylistSettings.PLAYLIST_ALL else current.playlists.activePlaylistId,
                            defaultPlaylistId = if (current.playlists.defaultPlaylistId == id) PlaylistSettings.PLAYLIST_ALL else current.playlists.defaultPlaylistId,
                            scheduleRules = current.playlists.scheduleRules.filterNot { it.playlistId == id },
                        ))
                    }
                    engine.deletePlaylistShuffleState(id)
                }
                else -> throw IllegalArgumentException("Unknown playlist action")
            }
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                "WEB_PLAYLIST_ACTION",
                "action" to action,
                "playlistToken" to diagnosticToken(id, "playlist"),
            )
            return redactedConfigJson()
        }

        override suspend fun playlistScheduleAction(body: JsonObject): JsonObject {
            val action = body["action"]?.jsonPrimitive?.content.orEmpty()
            when (action) {
                "toggle" -> {
                    val enabled = body["enabled"]?.jsonPrimitive?.booleanOrNull
                        ?: throw IllegalArgumentException("Missing enabled value")
                    settings.update { it.copy(playlists = it.playlists.copy(scheduleEnabled = enabled)) }
                }
                "add" -> {
                    val playlistId = body["playlistId"]?.jsonPrimitive?.content.orEmpty()
                    val start = body["startTime"]?.jsonPrimitive?.content.orEmpty()
                    val end = body["endTime"]?.jsonPrimitive?.content.orEmpty()
                    require(SleepSchedule.parseMinutes(start) != null && SleepSchedule.parseMinutes(end) != null) { "Times must use HH:mm" }
                    settings.update { current ->
                        require(current.playlists.playlists.any { it.id == playlistId }) { "Unknown playlist" }
                        val days = (body["daysOfWeek"] as? JsonArray)
                            ?.mapNotNull { it.jsonPrimitive.intOrNull }
                            ?.filter { it in 1..7 }
                            ?.toSet()
                            .orEmpty()
                            .ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
                        fun validatedDate(key: String): String? {
                            val value = body[key]?.jsonPrimitive?.content?.trim().orEmpty()
                            if (value.isBlank()) return null
                            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { "$key must be YYYY-MM-DD" }
                            val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }
                            require(runCatching { parser.parse(value) }.getOrNull() != null) { "$key is not a valid date" }
                            return value
                        }
                        val rule = PlaylistScheduleRule(
                            id = "rule_${UUID.randomUUID()}",
                            name = body["name"]?.jsonPrimitive?.content?.trim()?.take(80).orEmpty().ifBlank { "Playlist schedule" },
                            playlistId = playlistId,
                            enabled = body["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            daysOfWeek = days,
                            startTime = start,
                            endTime = end,
                            priority = body["priority"]?.jsonPrimitive?.intOrNull?.coerceIn(-1000, 1000) ?: 0,
                            startDateIso = validatedDate("startDate"),
                            endDateIso = validatedDate("endDate"),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                        require(rule.startDateIso == null || rule.endDateIso == null || rule.startDateIso <= rule.endDateIso) {
                            "Schedule start date must not be after end date"
                        }
                        current.copy(playlists = current.playlists.copy(scheduleRules = current.playlists.scheduleRules + rule))
                    }
                }
                "toggle_rule" -> {
                    val id = body["id"]?.jsonPrimitive?.content.orEmpty()
                    val enabled = body["enabled"]?.jsonPrimitive?.booleanOrNull
                        ?: throw IllegalArgumentException("Missing enabled value")
                    settings.update { current ->
                        require(current.playlists.scheduleRules.any { it.id == id }) { "Unknown schedule rule" }
                        current.copy(playlists = current.playlists.copy(
                            scheduleRules = current.playlists.scheduleRules.map { if (it.id == id) it.copy(enabled = enabled) else it },
                        ))
                    }
                }
                "delete" -> {
                    val id = body["id"]?.jsonPrimitive?.content.orEmpty()
                    settings.update { current ->
                        current.copy(playlists = current.playlists.copy(
                            scheduleRules = current.playlists.scheduleRules.filterNot { it.id == id },
                        ))
                    }
                }
                "cancel_override" -> settings.update {
                    it.copy(playlists = it.playlists.copy(manualOverrideUntilEpochMs = 0L))
                }
                else -> throw IllegalArgumentException("Unknown schedule action")
            }
            diagnostics.log(DiagnosticsLog.Category.APP, "WEB_PLAYLIST_SCHEDULE_ACTION", "action" to action)
            return redactedConfigJson()
        }

        /**
         * Upload endpoints are only reachable when a manager was wired in. Centralised so
         * every upload route reports the same message rather than repeating the literal.
         */
        private fun requireUploadManager(): WebUploadManager =
            uploadManager ?: throw IllegalArgumentException("Web upload is unavailable")

        override suspend fun uploadCreate(ownerToken: String, body: JsonObject): JsonObject {
            val manager = requireUploadManager()
            val uploadSettings = settings.settings.first().webUpload
            val playing = engine.ui.value.state == EngineState.PLAYING_PRIMARY ||
                engine.ui.value.state == EngineState.PLAYING_FALLBACK
            require(uploadSettings.allowWhilePlaying || !playing) {
                "Pause the slideshow before starting an upload, or enable uploads while playing"
            }
            val count = body["fileCount"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("Missing fileCount")
            val bytes = body["totalBytes"]?.jsonPrimitive?.longOrNull
                ?: throw IllegalArgumentException("Missing totalBytes")
            val policy = body["duplicatePolicy"]?.jsonPrimitive?.content
            return manager.create(ownerToken, count, bytes, policy)
        }

        override suspend fun uploadStatus(ownerToken: String, sessionId: String): JsonObject =
            requireUploadManager().status(ownerToken, sessionId)

        override suspend fun uploadFile(
            ownerToken: String,
            sessionId: String,
            clientId: String,
            encodedName: String,
            declaredSize: Long,
            input: InputStream,
        ): JsonObject = requireUploadManager()
            .upload(ownerToken, sessionId, clientId, encodedName, declaredSize, input)

        override suspend fun uploadComplete(ownerToken: String, sessionId: String): JsonObject =
            requireUploadManager().complete(ownerToken, sessionId)

        override suspend fun uploadCancel(ownerToken: String, sessionId: String): JsonObject =
            requireUploadManager().cancel(ownerToken, sessionId)

        /** Redacted export (spec §17.2): no secrets, no private paths, no GPS. */
        override suspend fun diagnosticsBundle(): InputStream {
            val current = settings.settings.first()
            return diagnostics.openDurableBundle(
                DiagnosticsBundleContext(
                    appVersion = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    buildType = BuildConfig.BUILD_TYPE,
                    sdkInt = android.os.Build.VERSION.SDK_INT,
                    deviceModel = android.os.Build.MODEL,
                    abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                    sourceKind = current.source.kind.name,
                    indexedCount = photoDao.count().toLong(),
                    runtime = diagnosticRuntimeState.snapshot(),
                ),
            )
        }

        override suspend fun clearDiagnostics() {
            diagnostics.clearDurable()
            diagnostics.log(DiagnosticsLog.Category.APP, "DIAGNOSTICS_CLEARED")
        }

        override suspend fun diagnosticsJson(): JsonObject {
            val s = settings.settings.first()
            val indexedFallback = photoDao.countForSource(BuiltInSourceIds.FALLBACK)
            val recent = diagnostics.snapshot(100)
            val health = diagnostics.healthSnapshot()
            return buildJsonObject {
                put("appVersion", BuildConfig.VERSION_NAME)
                put("androidSdk", android.os.Build.VERSION.SDK_INT)
                put("deviceModel", android.os.Build.MODEL)
                put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                put("heapUsedMb", (Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }) / (1024 * 1024))
                put("heapMaxMb", Runtime.getRuntime().maxMemory() / (1024 * 1024))
                put("sourceKind", s.source.kind.name)
                put("indexedFallback", indexedFallback)
                put("summary", diagnosticSummary(recent, health))
                put("health", diagnosticHealthJson(health))
                put("warnings", diagnosticWarnings(health, cursorExpired = false))
                put("events", buildJsonArray { recent.forEach { add(diagnosticEventJson(it)) } })
            }
        }
    }

    /**
     * Per-source role, reachability and indexed count — the same indicator the on-device
     * settings show, so a browser is not left guessing which source is actually playing.
     */
    private suspend fun sourceStatusJson(settings: AppSettings): JsonArray {
        val pool = engine.poolSnapshot
        val counts = indexedCountsPerSource()
        val statuses = SourceStatusPolicy.statuses(
            source = settings.source,
            unavailableSourceIds = pool.unavailableSourceIds,
            stalePlayback = pool.primaryCachedOnly,
            indexedPhotos = counts,
            sourceIdFor = ::sourceIdForKind,
            fallbackSourceId = BuiltInSourceIds.FALLBACK,
        )
        return buildJsonArray {
            statuses.forEach { status ->
                add(buildJsonObject {
                    put("kind", status.kind.name)
                    put("sourceId", status.sourceId ?: "")
                    put("role", status.role.name)
                    put("availability", status.availability.name)
                    put("detail", status.detail)
                    put("indexedPhotos", status.indexedPhotos)
                    put("canBecomePrimary", status.canBecomePrimary)
                    put("canAlsoPlay", status.canAlsoPlay)
                })
            }
        }
    }

    /**
     * Browsers poll `/api/status` every few seconds, and these are whole-table COUNTs.
     * Row counts move only when a scan writes, so a short cache keeps the indicator live
     * without adding five aggregate queries per poll on a low-powered frame.
     */
    private suspend fun indexedCountsPerSource(): Map<String, Int> {
        val now = SystemClock.elapsedRealtime()
        cachedSourceCounts?.let { (cachedAt, counts) ->
            if (now - cachedAt < SOURCE_COUNT_CACHE_MS) return counts
        }
        val counts = (SourceStatusPolicy.orderedKinds.mapNotNull(::sourceIdForKind) +
            BuiltInSourceIds.FALLBACK)
            .distinct()
            .associateWith { id -> runCatching { photoDao.countForSource(id) }.getOrDefault(0) }
        cachedSourceCounts = now to counts
        return counts
    }

    private fun sourceIdForKind(kind: ActiveSourceKind): String? = when (kind) {
        ActiveSourceKind.LOCAL_SAF -> BuiltInSourceIds.LOCAL_SAF
        ActiveSourceKind.SMB -> BuiltInSourceIds.SMB
        ActiveSourceKind.SYNOLOGY -> BuiltInSourceIds.SYNOLOGY
        ActiveSourceKind.WEBDAV -> BuiltInSourceIds.WEBDAV
        ActiveSourceKind.SAMPLES, ActiveSourceKind.NONE -> null
    }

    private fun sourceIdsFor(settings: AppSettings): List<String> {
        val kinds = buildSet {
            add(settings.source.kind)
            addAll(settings.source.alsoPlay)
        }
        return kinds.mapNotNull { kind ->
            when (kind) {
                ActiveSourceKind.LOCAL_SAF -> BuiltInSourceIds.LOCAL_SAF
                ActiveSourceKind.SMB -> BuiltInSourceIds.SMB
                ActiveSourceKind.SYNOLOGY -> BuiltInSourceIds.SYNOLOGY
                ActiveSourceKind.WEBDAV -> BuiltInSourceIds.WEBDAV
                ActiveSourceKind.SAMPLES -> BuiltInSourceIds.FALLBACK
                ActiveSourceKind.NONE -> null
            }
        }.ifEmpty { listOf(BuiltInSourceIds.FALLBACK) }
    }

    private fun revisionOf(settings: AppSettings): Long = settings.hashCode().toLong() and 0xFFFF_FFFFL

    private fun diagnosticEventJson(event: DiagnosticsLog.Entry): JsonObject = buildJsonObject {
        put("schemaVersion", event.schemaVersion)
        put("sequence", event.sequence)
        put("atEpochMs", event.atEpochMs)
        put("at", event.atEpochMs)
        put("elapsedRealtimeMs", event.elapsedRealtimeMs)
        put("sessionId", event.sessionId)
        put("severity", event.severity.name)
        put("category", event.category.name)
        put("code", event.code)
        put("origin", event.origin.name)
        event.operationId?.let { put("operationId", it) } ?: put("operationId", JsonNull)
        event.parentOperationId?.let { put("parentOperationId", it) } ?: put("parentOperationId", JsonNull)
        put("message", event.message)
        put("fields", buildJsonObject {
            event.fields.toSortedMap().forEach { (key, value) -> put(key, value) }
        })
    }

    private fun diagnosticHealthJson(
        health: com.example.familyphotoframe.data.diagnostics.DiagnosticsHealthSnapshot,
    ): JsonObject = buildJsonObject {
        fun stream(value: com.example.familyphotoframe.data.diagnostics.DiagnosticsHealthSnapshot.Stream) =
            buildJsonObject {
                put("retainedBytes", value.retainedBytes)
                put("retainedGenerations", value.retainedGenerations)
                put("rotations", value.rotations)
                put("oldestKnownSessionId", value.oldestKnownSessionId)
                put("oldestKnownSequence", value.oldestKnownSequence)
                put("newestKnownSessionId", value.newestKnownSessionId)
                put("newestKnownSequence", value.newestKnownSequence)
                put("lastSuccessfulWriteEpochMs", value.lastSuccessfulWriteEpochMs)
                put("lastAppendErrorClass", value.lastAppendErrorClass)
            }
        put("queueCapacity", health.queueCapacity)
        put("queueDepth", health.queueDepth)
        put("droppedTotal", health.droppedTotal)
        put("droppedSinceLastReport", health.droppedSinceLastReport)
        put("fieldsDropped", health.fieldsDropped)
        put("lastSuccessfulWriteEpochMs", health.lastSuccessfulWriteEpochMs)
        put("lastSuccessfulFlushEpochMs", health.lastSuccessfulFlushEpochMs)
        put("lastFlushTimeoutMs", health.lastFlushTimeoutMs)
        put("crashEnvelopePresent", health.crashEnvelopePresent)
        put("crashEnvelopeBytes", health.crashEnvelopeBytes)
        put("standard", stream(health.standard))
        put("bulk", stream(health.bulk))
    }

    private fun diagnosticWarnings(
        health: com.example.familyphotoframe.data.diagnostics.DiagnosticsHealthSnapshot,
        cursorExpired: Boolean,
    ): JsonArray = buildJsonArray {
        fun warning(code: String, detail: String) = add(buildJsonObject {
            put("code", code)
            put("severity", "WARN")
            put("detail", detail)
        })
        if (cursorExpired) warning("DIAGNOSTICS_CURSOR_EXPIRED", "Requested history is no longer retained")
        if (health.droppedTotal > 0L) warning("DIAGNOSTICS_EVENTS_DROPPED", "${health.droppedTotal} durable events were dropped")
        if (health.standard.lastAppendErrorClass.isNotEmpty()) warning("DIAGNOSTICS_STANDARD_SINK_FAILED", health.standard.lastAppendErrorClass)
        if (health.bulk.lastAppendErrorClass.isNotEmpty()) warning("DIAGNOSTICS_BULK_SINK_FAILED", health.bulk.lastAppendErrorClass)
        if (health.standard.rotations > 0L || health.bulk.rotations > 0L ||
            health.standard.retainedGenerations > 1 || health.bulk.retainedGenerations > 1
        ) warning("DIAGNOSTICS_HISTORY_ROTATED", "Older retained history may be incomplete")
        if (health.lastFlushTimeoutMs > 0L) warning("DIAGNOSTICS_FLUSH_INCOMPLETE", "Last flush timed out")
        if (health.crashEnvelopePresent) warning("CRASH_ENVELOPE_PENDING", "Crash evidence is present in protected recovery storage")
        if (android.os.Build.VERSION.SDK_INT < 30) warning("PROCESS_EXIT_EVIDENCE_UNSUPPORTED", "Detailed historical process-exit reasons require Android 11 or newer")
    }

    private fun diagnosticFilterOptions(events: List<DiagnosticsLog.Entry>): JsonObject = buildJsonObject {
        fun values(items: Iterable<String>) = buildJsonArray {
            items.filter { it.isNotBlank() }.distinct().sorted().take(200).forEach(::add)
        }
        put("severities", values(events.map { it.severity.name }))
        put("categories", values(events.map { it.category.name }))
        put("sessions", values(events.map { it.sessionId }))
        put("codes", values(events.map { it.code }))
        put("triggers", values(events.mapNotNull { it.fields["trigger"] }))
        put("operations", values(events.mapNotNull { it.operationId }))
        put("origins", values(events.map { it.origin.name }))
    }

    private fun diagnosticOperationTimeline(events: List<DiagnosticsLog.Entry>): JsonArray {
        val grouped = linkedMapOf<String, MutableList<DiagnosticsLog.Entry>>()
        events.sortedWith(compareBy<DiagnosticsLog.Entry> { it.atEpochMs }.thenBy { it.sequence })
            .forEach { event -> event.operationId?.let { grouped.getOrPut(it) { arrayListOf() } += event } }
        return buildJsonArray {
            grouped.entries.toList().takeLast(30).forEach { (operationId, items) ->
                val first = items.first()
                val last = items.last()
                val terminal = last.code.endsWith("_COMPLETED") || last.code.endsWith("_FAILED") ||
                    last.code.endsWith("_CANCELLED") || last.code in setOf(
                        "SCAN_ABORTED", "SOURCE_RECOVERED", "SOURCE_BACKOFF_EXHAUSTED",
                    )
                add(buildJsonObject {
                    put("operationId", operationId)
                    first.parentOperationId?.let { put("parentOperationId", it) } ?: put("parentOperationId", JsonNull)
                    put("origin", first.origin.name)
                    put("startedAtEpochMs", first.atEpochMs)
                    put("lastAtEpochMs", last.atEpochMs)
                    put("durationMs", (last.elapsedRealtimeMs - first.elapsedRealtimeMs).coerceAtLeast(0L))
                    put("trigger", first.fields["trigger"].orEmpty())
                    put("terminalCode", if (terminal) last.code else "")
                    put("incomplete", !terminal)
                    put("codes", buildJsonArray { items.map { it.code }.distinct().take(20).forEach(::add) })
                })
            }
        }
    }

    private fun diagnosticSummary(
        events: List<DiagnosticsLog.Entry>,
        health: com.example.familyphotoframe.data.diagnostics.DiagnosticsHealthSnapshot,
    ): JsonObject {
        fun count(vararg codes: String): Int = events.count { event -> event.code in codes }
        val sessions = events.filter { it.code == "SESSION_START" }
            .map { it.sessionId }.filter { it.isNotBlank() }.distinct()
        val scanFailures = count("SCAN_ABORTED", "SCAN_ERROR", "AUTO_RESCAN_FAILED")
        val sourceFailures = count(
            "SOURCE_REFRESH_FAILED", "SOURCE_TEST_FAILED", "SOURCE_HEALTH_CHECK_FAILED",
            "SOURCE_APPLY_FAILED", "SOURCE_BACKOFF_EXHAUSTED",
        )
        val sinkErrors = listOf(
            health.standard.lastAppendErrorClass,
            health.bulk.lastAppendErrorClass,
        ).count { it.isNotEmpty() }
        val retentionStatus = when {
            health.standard.rotations > 0L || health.bulk.rotations > 0L ||
                health.standard.retainedGenerations > 1 || health.bulk.retainedGenerations > 1 -> "PARTIAL_RETENTION"
            health.standard.retainedBytes + health.bulk.retainedBytes == 0L -> "EMPTY"
            else -> "COMPLETE"
        }
        val evidenceIncomplete = health.droppedTotal > 0L || sinkErrors > 0 ||
            health.lastFlushTimeoutMs > 0L || health.crashEnvelopePresent || retentionStatus == "PARTIAL_RETENTION"
        return buildJsonObject {
            put("decodeFailures", count("DECODE_FAILED"))
            put("unsupportedFiles", count("DECODE_UNSUPPORTED"))
            put("transitionFallbacks", count("TRANSITION_FALLBACK"))
            put("slowTransitions", count("TRANSITION_PERFORMANCE_WARNING", "TRANSITION_LOW_PERFORMANCE_ENTERED"))
            put("weatherFailures", count("WEATHER_FETCH_FAILED"))
            put("scanFailures", scanFailures)
            put("sourceFailures", sourceFailures)
            put("terminalFailures", scanFailures + sourceFailures)
            put("webErrors", count("WEB_ERROR", "WEB_START_FAILED"))
            put("sessions", sessions.size)
            put("processRestarts", (sessions.size - 1).coerceAtLeast(0))
            put("crashes", count("UNCAUGHT_EXCEPTION", "PREVIOUS_UNCAUGHT_EXCEPTION", "PREVIOUS_CRASH_EVIDENCE"))
            put("anrs", count("MAIN_THREAD_STALL_ESCALATED", "PREVIOUS_ANR_EVIDENCE"))
            put("processExits", count("PROCESS_EXIT_RECORDED"))
            put("processExitEvidenceSupported", android.os.Build.VERSION.SDK_INT >= 30)
            put("lowMemoryEvents", count("LOW_MEMORY", "DECODE_OOM_RECOVERY"))
            put("droppedEvents", health.droppedTotal)
            put("droppedFields", health.fieldsDropped)
            put("sinkErrors", sinkErrors)
            put("retainedBytes", health.standard.retainedBytes + health.bulk.retainedBytes)
            put("retainedGenerations", health.standard.retainedGenerations + health.bulk.retainedGenerations)
            put("rotations", health.standard.rotations + health.bulk.rotations)
            put("retentionStatus", retentionStatus)
            put("evidenceIncomplete", evidenceIncomplete)
            put("totalEvents", events.size)
        }
    }

    private fun nextScheduleDescription(settings: AppSettings): String = when {
        settings.brightnessAutomation.mode.name.startsWith("SCHEDULED") -> "Brightness schedule configured"
        settings.schedule.autoRescanEnabled -> "Rescan ${settings.schedule.autoRescanAt} on ${settings.schedule.autoRescanDays}"
        else -> "No scheduled action"
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes / 60L) % 24L
        val minutes = totalMinutes % 60L
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 30_000
        const val REMEMBERED_CLEANUP_INTERVAL_MS = 6L * 60L * 60_000L
        const val PREVIEW_ACTIVE_WINDOW_MS = 90_000L
        const val MB = 1024L * 1024L
        const val SOURCE_COUNT_CACHE_MS = 15_000L

        /** Every overlay-anchor field the web API accepts; validated as a group. */
    }
}
