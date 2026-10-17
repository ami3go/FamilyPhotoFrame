package com.example.familyphotoframe.ui.slideshow

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.familyphotoframe.R
import com.example.familyphotoframe.ServiceLocator
import com.example.familyphotoframe.data.cache.MediaCache
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.DiagnosticsBundleContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticOperationTracker
import com.example.familyphotoframe.data.diagnostics.SourceRefreshTrigger
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.web.DiagnosticsDownloadNaming
import com.example.familyphotoframe.data.index.IndexProgress
import com.example.familyphotoframe.data.index.ScanDiagnosticContext
import com.example.familyphotoframe.data.settings.ActiveSource
import com.example.familyphotoframe.data.settings.PlaybackInterval
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.BuildConfig
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.ScheduleSettings
import com.example.familyphotoframe.data.settings.SlideshowPlaylist
import com.example.familyphotoframe.data.settings.PlaylistSettings
import com.example.familyphotoframe.data.settings.OnThisDaySettings
import com.example.familyphotoframe.data.settings.PlaylistScheduleRule
import com.example.familyphotoframe.domain.schedule.RescanSchedule
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import com.example.familyphotoframe.domain.schedule.PlaylistSchedule
import com.example.familyphotoframe.domain.schedule.PlaylistOverrideWakePolicy
import com.example.familyphotoframe.domain.schedule.BrightnessPolicy
import com.example.familyphotoframe.domain.schedule.OnThisDaySchedule
import com.example.familyphotoframe.domain.onthisday.OnThisDaySelection
import com.example.familyphotoframe.data.settings.BrightnessAutomationSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.BrightnessPeriod
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.data.settings.MotionMode
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.DecodeColorDepth
import com.example.familyphotoframe.data.settings.DecodeResolution
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitCollageSettings
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.data.settings.OverlayPosition
import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import com.example.familyphotoframe.data.settings.ConfigTransfer
import com.example.familyphotoframe.data.settings.CredentialPolicy
import com.example.familyphotoframe.data.settings.ImportResult
import com.example.familyphotoframe.data.settings.PortableBundle
import com.example.familyphotoframe.data.settings.BoundedTextInput
import com.example.familyphotoframe.data.settings.ImportTooLargeException
import com.example.familyphotoframe.data.weather.TemperatureUnits
import com.example.familyphotoframe.data.weather.WeatherDisplay
import com.example.familyphotoframe.data.weather.WeatherPresentation
import com.example.familyphotoframe.data.db.FolderSummary
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.data.settings.FilterSettings
import com.example.familyphotoframe.data.settings.SmbSettings
import com.example.familyphotoframe.data.settings.SourceRuntimeSignature
import com.example.familyphotoframe.data.settings.WebDavSettings
import com.example.familyphotoframe.data.settings.UnreachablePolicy
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.domain.engine.EngineUiModel
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.WebDavApi
import com.example.familyphotoframe.data.source.WebDavConnection
import com.example.familyphotoframe.data.source.WebDavCredentials
import com.example.familyphotoframe.data.source.ScanOptions
import com.example.familyphotoframe.data.settings.SynologySettings
import com.example.familyphotoframe.data.source.SmbConnection
import com.example.familyphotoframe.data.source.SmbCredentials
import com.example.familyphotoframe.data.source.SourceHealth
import com.example.familyphotoframe.data.source.SourceId
import com.example.familyphotoframe.data.source.CertPinning
import com.example.familyphotoframe.perf.FrameStats
import com.example.familyphotoframe.perf.FrameStatsCollector
import com.example.familyphotoframe.ui.slideshow.transition.TransitionEvent
import com.example.familyphotoframe.ui.slideshow.viewmodel.ConfigTransferActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.SlideshowFeatureHost
import com.example.familyphotoframe.ui.slideshow.viewmodel.SourceSettingsActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.LocalThumbnailCacheActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.WebControlActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.AutoRescanScheduler
import com.example.familyphotoframe.ui.slideshow.viewmodel.BrightnessActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.OnThisDayActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.PlaylistActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.PhotoLibraryActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.PresentationReporter
import com.example.familyphotoframe.ui.slideshow.viewmodel.diagnosticSourceKind
import com.example.familyphotoframe.ui.slideshow.viewmodel.PlaybackPhotoAccess
import com.example.familyphotoframe.ui.slideshow.viewmodel.WeatherActions
import com.example.familyphotoframe.ui.slideshow.viewmodel.healthErrorCode
import com.example.familyphotoframe.ui.slideshow.viewmodel.healthStateCode
import com.example.familyphotoframe.data.source.SynologyApi
import com.example.familyphotoframe.data.source.SynologyConnection
import com.example.familyphotoframe.data.source.SynologyCredentials
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.domain.engine.DecodeFailureStage
import com.example.familyphotoframe.domain.engine.PERMANENT_DECODE_FAILURE_COUNT
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.domain.engine.HostLifecycleGate
import com.example.familyphotoframe.domain.engine.PortraitCollagePolicy
import com.example.familyphotoframe.domain.engine.toDisplayPhoto
import com.example.familyphotoframe.domain.engine.RecoveryPolicy
import com.example.familyphotoframe.domain.engine.SourceRecoveryCoordinator
import com.example.familyphotoframe.domain.engine.SourcePoolPolicy
import com.example.familyphotoframe.domain.engine.SourceStatusPolicy
import com.example.familyphotoframe.util.ImageFormatSupport
import com.example.familyphotoframe.web.WebServerController
import com.example.familyphotoframe.web.WebPreviewCaptureRequest
import com.example.familyphotoframe.web.WebPreviewFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the slideshow's runtime wiring (spec §3 MVVM + unidirectional state):
 * observes settings, resolves the active [PhotoSource], drives the [com.example
 * .familyphotoframe.data.index.Indexer] into Room, configures the
 * [com.example.familyphotoframe.domain.engine.SlideshowEngine], and exposes one
 * immutable [SlideshowUiState]. The UI only sends intents back here; it never does
 * I/O or scheduling itself.
 *
 * Source identity is fixed (see [ServiceLocator]); the sourceIds the indexer writes
 * must match the primary/fallback ids handed to the engine, or selection returns
 * nothing. Remote primaries are health-monitored and toggled in/out by [startSourceRecovery].
 */
class SlideshowViewModel(
    private val services: ServiceLocator,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowUiState())
    val state: StateFlow<SlideshowUiState> = _state.asStateFlow()
    val memoryProtection = services.playbackMemoryGuard.state
    val webPreviewCaptureRequest: StateFlow<WebPreviewCaptureRequest?> =
        services.webServer.previewCaptureRequest
    internal val bitmapLifecycleTracker = services.bitmapLifecycleTracker
    internal val nativeAllocationStageTracker = services.nativeAllocationStageTracker
    private val _hostActive = MutableStateFlow(false)
    val hostActive: StateFlow<Boolean> = _hostActive.asStateFlow()
    private val _hostGeneration = MutableStateFlow(0L)
    val hostGeneration: StateFlow<Long> = _hostGeneration.asStateFlow()
    private val hostLifecycleGate = HostLifecycleGate()
    private var engineOwnerToken: Long = 0L
    private var webControlsOwnerToken: Long = 0L

    fun onHostStarted() {
        _hostGeneration.value = hostLifecycleGate.start()
        _hostActive.value = true
        engine.setHostActive(engineOwnerToken, true)
    }

    fun onHostStopped() {
        // Invalidate the generation before publishing the inactive state. A decoder that
        // ignores cancellation can therefore never race onStop and commit a stale result.
        _hostGeneration.value = hostLifecycleGate.stop()
        _hostActive.value = false
        engine.setHostActive(engineOwnerToken, false)
    }

    fun hostPlaybackToken(): Long? = hostLifecycleGate.tokenIfActive()

    fun isHostPlaybackTokenCurrent(token: Long): Boolean = hostLifecycleGate.isCurrent(token)

    /** Activity collects this to launch the system folder picker (it owns the launcher). */
    private val _pickFolderRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pickFolderRequests: SharedFlow<Unit> = _pickFolderRequests.asSharedFlow()

    /** A document the Activity must open or create on our behalf (spec §7.0). */
    enum class FileOp { EXPORT_CONFIG, IMPORT_CONFIG, EXPORT_SUPPORT, EXPORT_ENCRYPTED, IMPORT_ENCRYPTED }
    data class FileRequest(val op: FileOp, val suggestedName: String)

    private val _fileRequests = MutableSharedFlow<FileRequest>(extraBufferCapacity = 1)
    val fileRequests: SharedFlow<FileRequest> = _fileRequests.asSharedFlow()

    private val featureHost = SlideshowFeatureHost(services, appContext, viewModelScope, _state)
    private val configTransferActions = ConfigTransferActions(featureHost, _fileRequests) { trigger, transform ->
        updateSettingsAndRefresh(trigger, transform = transform)
    }
    private val sourceSettingsActions = SourceSettingsActions(
        featureHost,
        updateSettingsAndRefresh = { trigger, transform ->
            updateSettingsAndRefresh(trigger, transform = transform)
        },
        setPendingSynologyOtp = { pendingSynologyOtp = it },
    )
    private val webControlActions = WebControlActions(featureHost)
    private val thumbnailCacheActions = LocalThumbnailCacheActions(featureHost)
    private val playlistActions = PlaylistActions(featureHost)
    private val brightnessActions = BrightnessActions(featureHost)
    private val autoRescan = AutoRescanScheduler(featureHost) { trigger, origin, awaitCompletion ->
        requestSourceRefresh(trigger = trigger, origin = origin, awaitCompletion = awaitCompletion)
    }
    private val onThisDayActions = OnThisDayActions(featureHost) { currentPlaybackSourceIds() }
    private val weatherActions = WeatherActions(featureHost) { lastSettings }
    private val presentationReporter = PresentationReporter(featureHost)
    private val photoLibraryActions = PhotoLibraryActions(featureHost) { currentPlaybackSourceIds() }

    private val engine = services.engine
    private val diagnostics = services.diagnostics

    /**
     * Source activation is driven by a separate latest-wins stream. A failed Synology
     * health check must not keep the settings collector blocked when the user switches
     * to SMB, and every manual rebuild must target the latest persisted settings.
     */
    private data class SourceApplyRequest(
        val settings: AppSettings,
        val signature: String,
        val refreshToken: Long,
        val trigger: SourceRefreshTrigger,
        val operation: DiagnosticOperationTracker.Handle,
        val configRevision: String,
        val startedElapsedMs: Long,
        val terminalLogged: AtomicBoolean = AtomicBoolean(false),
        val supersededByOperationId: AtomicReference<String?> = AtomicReference(null),
    )

    private val sourceRefreshSequence = AtomicLong(0L)
    private val sourceRefreshRequestLock = Mutex()
    private val sourceApplyRequests = MutableStateFlow<SourceApplyRequest?>(null)
    private val sourceRefreshCompleted = MutableStateFlow(-1L)
    private var lastObservedSourceSignature: String? = null
    private data class ExplicitSourceChange(
        val trigger: SourceRefreshTrigger,
        val origin: DiagnosticOrigin,
    )
    private val explicitSourceChange = AtomicReference<ExplicitSourceChange?>(null)
    private val sourceSettingsMutationLock = Mutex()
    private val sourceRequestEnqueueLock = Any()
    @Volatile private var activeSourceRequest: SourceApplyRequest? = null
    private val runtimeContextLogged = AtomicBoolean(false)

    /** One physical scan per source/configuration; concurrent rescan requests await it. */
    private val scanFlightsLock = Mutex()
    private val scanExecutionLock = Mutex()
    private data class ScanResult(
        val total: Int,
        val errors: Int,
        val found: Int,
        val exifMisses: Int,
        val reconciled: Boolean,
        val completionState: String,
    )
    private data class ScanFlight(
        val deferred: Deferred<ScanResult>,
        val operationId: String,
        val parentOperationId: String,
        val sourceKind: String,
        val trigger: SourceRefreshTrigger,
        val configRevision: String,
    )
    private val scanFlights = mutableMapOf<String, ScanFlight>()
    /** One cancellable low-priority content-hash indexer per source. */
    private val contentHashJobs = mutableMapOf<String, Job>()

    /**
     * Remote playback and background-backfill sources. One registry owns promotion,
     * loser cleanup, and teardown so credential suspensions cannot strand duplicates.
     */
    private val remoteSources = RemoteSourceRegistry()
    private val photoAccess = PlaybackPhotoAccess(
        featureHost,
        remoteSources = remoteSources,
        isRemotePrimaryCachedOnly = { remotePrimaryCachedOnly },
        currentRemotePrimarySourceId = { remotePrimarySourceId },
        maxFailures = { currentMaxFailures() },
    )

    /**
     * True while a remote primary is unreachable and the frame is playing its already
     * cached photos instead of the bundled samples (spec §9.3 `on_unreachable`).
     * [remotePrimarySourceId] scopes it so a stale flag can never affect a different
     * source that becomes active later.
     *
     * Only ever set when the cached-only source is the *whole* primary pool. With a
     * healthy co-primary still playing there is no need to fall back to stale bytes, so
     * an unreachable source is simply dropped from the pool instead.
     */
    @Volatile private var remotePrimaryCachedOnly: Boolean = false
    @Volatile private var remotePrimarySourceId: String? = null

    /**
     * Source ids currently believed healthy and therefore feeding the primary pool.
     * Ordered so the engine's pool is stable between reconfigurations.
     */
    private val primaryPoolIds = linkedSetOf<String>()
    /** Configured sources currently unavailable; folder shuffle defers them once per cycle. */
    private val unavailablePoolIds = linkedSetOf<String>()
    /** Sources whose bounded recovery backoff has reached its terminal interval. */
    private val exhaustedUnavailablePoolIds = linkedSetOf<String>()

    /** Latest stored source configuration, so status rows can be republished off-collector. */
    @Volatile private var currentSourceConfig: ActiveSource = ActiveSource()
    /** Indexed row count per source id, refreshed with the health summary. */
    @Volatile private var indexedPhotosBySource: Map<String, Int> = emptyMap()

    /** The source the user actually selected; owns the `on_unreachable` decision. */
    private var chosenSlot: ActivatedSlot? = null

    /** One recovery loop per remote primary; all cancelled together on a source change. */
    private val recoveryJobs = mutableListOf<kotlinx.coroutines.Job>()
    private data class RecoveryRuntime(
        val coordinator: SourceRecoveryCoordinator,
        val wake: Channel<Unit> = Channel(Channel.CONFLATED),
    )
    /** Shared by playback failures and recovery loops; the playback pool stays authoritative. */
    private val recoveryRuntimes = mutableMapOf<String, RecoveryRuntime>()
    private var sourceApplyJob: Job? = null
    private var settingsCollectorJob: Job? = null
    private var healthJob: kotlinx.coroutines.Job? = null
    /** Starts playback from a partially built index; see [startEarlyPlaybackWatcher]. */
    private var earlyPlaybackJob: Job? = null
    private var activePlaylistSourceFilter: Set<String> = emptySet()

    /**
     * 2FA one-time code for the next Synology login. Held in memory only and cleared
     * after use: the code is valid for seconds, so persisting it would be pointless as
     * well as a secret-handling risk (Contract Rule 5).
     */
    @Volatile private var pendingSynologyOtp: String? = null

    /** §22.4 performance-budget measurement; idle and free until explicitly started. */
    private val frameStats = FrameStatsCollector()
    private var frameStatsJob: Job? = null

    init {
        // Engine drives photo content; mirror its model into our state.
        viewModelScope.launch {
            engine.ui.collect { model ->
                _state.update { it.copy(engine = model) }
                onDisplayedPhotoChanged(model)
                presentationReporter.publishDiagnosticPlayback(_state.value)
            }
        }
        // Single source of truth for config; react to changes.
        settingsCollectorJob = viewModelScope.launch {
            services.settings.settings.collect { s -> onSettings(s) }
        }
        // Source I/O is intentionally not performed inside the settings collector.
        // collectLatest cancels an obsolete failed health check or scan as soon as a
        // newer source configuration arrives (for example Synology -> SMB).
        sourceApplyJob = viewModelScope.launch {
            sourceApplyRequests
                .filterNotNull()
                .distinctUntilChangedBy { it.signature to it.refreshToken }
                .collectLatest(::applySourceRequest)
        }
        engineOwnerToken = engine.start(viewModelScope)
        healthJob = viewModelScope.launch {
            while (isActive) {
                refreshHealth()
                delay(60_000L)
            }
        }

        // Weather updates the overlay when a new snapshot lands, and is re-evaluated
        // periodically so a snapshot can age into "stale" and then out of view.
        viewModelScope.launch {
            services.weather.snapshot.collect { weatherActions.refreshWeatherText() }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(WEATHER_RECHECK_MS)
                weatherActions.refreshWeatherText()
            }
        }
    }

    override fun onCleared() {
        // No replacement follows ViewModel destruction, so leave backfill construction
        // blocked permanently before asking its jobs to stop.
        remoteSources.beginReplacement()
        recoveryRuntimes.values.forEach { it.wake.close() }
        recoveryRuntimes.clear()
        contentHashJobs.values.forEach { it.cancel() }
        contentHashJobs.clear()
        // The sampler re-posts itself every frame, so leaving it running would keep this
        // collector (and its sample buffer) reachable from the Choreographer queue for the
        // life of the process, with a fresh one added on each recreation.
        frameStats.stop()
        frameStatsJob?.cancel()
        // onCleared cannot suspend, so this can only request cancellation, not await it.
        cancelSourceConsumingJobs()
        engine.stop(engineOwnerToken)
        // Remote sessions outlive the ViewModel unless released here: on a configuration
        // change the replacement instance builds its own, and the old transports would
        // stay registered with nothing left to close them.
        releaseResolvedSources()
        services.webServer.clearControls(webControlsOwnerToken)
        super.onCleared()
    }

    /** Web-API operations that need the running slideshow (spec §15.4). */
    private val webControls = object : WebServerController.FrameControls {
        override suspend fun rescan(): String? {
            requestSourceRefresh(
                trigger = SourceRefreshTrigger.REBUILD_WEB_UI,
                origin = DiagnosticOrigin.WEB_UI,
                awaitCompletion = true,
            )
            return null
        }

        override suspend fun testSavedSource(): String = sourceSettingsActions.testSavedSource()

        override suspend fun previewFolderOnce(folderKey: String): String? =
            photoLibraryActions.previewFolderOnceByKey(folderKey)

        override suspend fun useFolderInActivePlaylist(folderKey: String): String? =
            photoLibraryActions.useFolderInActivePlaylistByKey(folderKey)

        override suspend fun clearMediaCache(): String? {
            services.mediaCache.clear()
            services.imageLoader.memoryCache?.clear()
            services.diagnostics.log(DiagnosticsLog.Category.CACHE, "WEB_CACHE_CLEARED")
            return null
        }

        override suspend fun clearLocalThumbnailCache(): String? {
            this@SlideshowViewModel.cleanLocalThumbnailCache()
            return null
        }

        override suspend fun previewOnThisDay(): String? =
            onThisDayActions.triggerOnThisDay(preview = true)

        override suspend fun rebuildLocalThumbnailCache(): String? {
            this@SlideshowViewModel.rebuildLocalThumbnailCache()
            return null
        }

        override suspend fun restartApplication(): String? {
            scheduleApplicationRestart()
            return null
        }

        // Qualified: an unqualified call would bind to this override, not the ViewModel's.
        override suspend fun capturePerformanceSample(): String? {
            this@SlideshowViewModel.capturePerformanceSample()
            return null
        }

        override suspend fun factoryReset(): String? {
            services.webServer.beginTerminalMaintenance()
            // From this point the operation is terminal. Finish (or fail and restart)
            // even if the browser closes the request socket midway through the reset.
            return withContext(NonCancellable) {
                try {
                    quiesceForFactoryReset()
                    services.factoryResetCoordinator.reset()
                    // Keep this request alive long enough to deliver its terminal success;
                    // the process restart then destroys all in-memory web sessions.
                    services.webServer.clearControls(webControlsOwnerToken)
                    scheduleApplicationRestart()
                    null
                } catch (error: Exception) {
                    // The ViewModel has already been quiesced, and DataStore/cache work
                    // cannot share Room's transaction. Restart into the persisted state
                    // instead of attempting to resume a potentially partial runtime.
                    services.webServer.clearControls(webControlsOwnerToken)
                    scheduleApplicationRestart()
                    "Factory reset failed at a protected boundary: " +
                        error.javaClass.simpleName.ifBlank { "UNKNOWN" } +
                        ". The app will restart."
                }
            }
        }
    }

    private fun scheduleApplicationRestart() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                ?.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
            if (launch != null) appContext.startActivity(launch)
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 700L)
    }

    /** Stop every producer before persistent rows and caches are removed. */
    private suspend fun quiesceForFactoryReset() {
        settingsCollectorJob?.cancelAndJoin()
        settingsCollectorJob = null
        sourceApplyJob?.cancelAndJoin()
        sourceApplyJob = null
        sourceApplyRequests.value = null
        // The process will restart after reset; no new metadata source may be retained
        // between the final registry release and that restart.
        remoteSources.beginReplacement()

        val obsoleteRecoveryJobs = recoveryJobs.toList()
        recoveryJobs.clear()
        obsoleteRecoveryJobs.forEach { it.cancel() }
        obsoleteRecoveryJobs.forEach { it.join() }
        recoveryRuntimes.values.forEach { it.wake.close() }
        recoveryRuntimes.clear()

        cancelObsoleteIndexWork("factory-reset")
        val backgroundJobs = listOfNotNull(
            autoRescan.rescanScheduleJob,
            playlistActions.playlistScheduleJob,
            brightnessActions.brightnessJob,
            healthJob,
            exifJob,
            contentHashJob,
        )
        backgroundJobs.forEach { it.cancel() }
        backgroundJobs.forEach { it.join() }
        autoRescan.rescanScheduleJob = null
        playlistActions.playlistScheduleJob = null
        brightnessActions.brightnessJob = null
        healthJob = null
        exifJob = null
        contentHashJob = null
        frameStats.stop()
        frameStatsJob?.cancelAndJoin()
        frameStatsJob = null

        engine.setAsleep(true)
        engine.configure(
            primaryIds = emptyList(),
            fallbackIds = emptyList(),
            intervalSeconds = currentIntervalSeconds(),
            maxFailures = currentMaxFailures(),
        )
        cancelAndJoinSourceConsumingJobs()
        releaseResolvedSources()
        primaryPoolIds.clear()
        unavailablePoolIds.clear()
        exhaustedUnavailablePoolIds.clear()
    }

    // Declared after [webControls] so the property is initialized before it is read
    // (Kotlin runs init blocks and property initializers in declaration order).
    init {
        webControlsOwnerToken = services.webServer.installControls(webControls)
    }

    private suspend fun onSettings(s: AppSettings) {
        val previousSettings = lastSettings
        if (playlistActions.playlistScheduleConfig(lastSettings?.playlists) !=
            playlistActions.playlistScheduleConfig(s.playlists)
        ) {
            playlistActions.restartPlaylistScheduleWatcher()
        }
        if (lastSettings?.brightnessAutomation != s.brightnessAutomation) {
            brightnessActions.restartBrightnessWatcher(s.brightnessAutomation)
        }
        // Deliberately compares only the configuration fields. Including
        // lastAutoRescanAtEpochMs would make the watcher cancel itself the instant it
        // recorded a run — mid-scan.
        if (autoRescan.rescanConfigSig(lastSettings?.schedule) != autoRescan.rescanConfigSig(s.schedule)) {
            autoRescan.restartRescanWatcher(s.schedule)
        }
        if (onThisDayActions.onThisDayConfigSig(lastSettings?.onThisDay) !=
            onThisDayActions.onThisDayConfigSig(s.onThisDay)
        ) {
            onThisDayActions.restartOnThisDayWatcher(s.onThisDay)
        }
        if (lastSettings?.weather != s.weather) weatherActions.restartWeather(s.weather)
        val playlist = s.playlists.activePlaylist()
        activePlaylistSourceFilter = playlist.sourceIds
        val effectiveSelection = playlist.selectionMode ?: s.selectionMode
        val effectiveFavorites = playlist.favoritesOnly || s.favoritesOnly
        val effectiveFolders = if (playlist.folderNames.isNotEmpty()) playlist.folderNames else s.selectedFolders
        val effectiveInterval = playlist.intervalSeconds ?: s.intervalSecondsClamped
        val effectiveTransitionSelection = playlist.transitionSelectionMode ?: s.transitionSelectionMode
        val effectiveTransition = playlist.transition ?: s.transition
        val effectiveCollage = playlist.collageMode?.let { s.portraitCollage.copy(mode = it) } ?: s.portraitCollage
        lastSettings = s
        currentSourceConfig = s.source
        _state.update {
            it.copy(
                aspectMode = s.aspectMode,
                transitionSelectionMode = effectiveTransitionSelection,
                transition = effectiveTransition,
                transitionReduceMotion = s.transitionReduceMotion,
                motion = s.motion,
                portraitCollage = effectiveCollage,
                overlays = s.overlays,
                backgroundColorArgb = s.backgroundColorArgb,
                transitionDurationMs = s.transitionDurationMs,
                intervalSecondsForUi = effectiveInterval,
                smb = s.source.smb,
                synology = s.source.synology,
                webdav = s.source.webdav,
                showPerformanceOverlay = s.showPerformanceOverlay,
                decodeColorDepth = s.decodeColorDepth,
                cachePlaybackPool = s.cachePlaybackPool,
                decodeResolution = s.decodeResolution,
                nativeMemoryHilMode = s.nativeMemoryHilMode,
                memoryTier = services.memoryTier,
                autoStartOnBoot = s.autoStartOnBoot,
                showOverLockScreen = s.showOverLockScreen,
                doubleTapPauseTimeoutMinutes = s.doubleTapPauseTimeoutMinutes,
                batteryProtectionEnabled = s.batteryProtectionEnabled,
                stopLeakingGpsService = s.stopLeakingGpsService,
                web = s.web,
                schedule = s.schedule,
                weather = s.weather,
                filters = s.filters,
                selectionMode = effectiveSelection,
                favoritesOnly = effectiveFavorites,
                alsoPlay = s.source.alsoPlay,
                selectedFolders = effectiveFolders,
                playlists = s.playlists.playlists,
                activePlaylistId = playlist.id,
                activePlaylistName = playlist.name,
                playlistScheduleEnabled = s.playlists.scheduleEnabled,
                playlistScheduleRules = s.playlists.scheduleRules,
                brightnessAutomation = s.brightnessAutomation,
                webUpload = s.webUpload,
                localThumbnailCache = s.localThumbnailCache,
                onThisDay = s.onThisDay,
                configurableKinds = ActiveSourceKind.entries
                    .filter { it != s.source.kind && isConfigured(it, s.source) }
                    .toSet(),
                activeSourceKind = s.source.kind,
                sourceStatuses = buildSourceStatuses(s.source, it.stalePlayback),
                onUnreachable = s.onUnreachable,
                webPin = services.webServer.visiblePin(),
                webUrl = services.webServer.url(),
            )
        }
        presentationReporter.publishDiagnosticPlayback(_state.value, transitionOverride = effectiveTransition.name)
        services.setNativeMemoryHilMode(s.nativeMemoryHilMode)
        engine.setDiagnosticHold(s.nativeMemoryHilMode.holdPlayback)
        if (previousSettings?.nativeMemoryHilMode != s.nativeMemoryHilMode) {
            diagnostics.log(
                DiagnosticsLog.Category.MEMORY,
                "NATIVE_HIL_MODE_CHANGED",
                "mode" to s.nativeMemoryHilMode.name,
                "previousMode" to (previousSettings?.nativeMemoryHilMode?.name ?: "UNKNOWN"),
            )
        }
        engine.setCachePlaybackPool(s.cachePlaybackPool)
        engine.setTiming(effectiveInterval, s.temporarilySuppressAfterDecodeFailures)
        engine.setDoubleTapPauseTimeoutMinutes(s.doubleTapPauseTimeoutMinutes)
        // Applied without a reselect: the pools are unchanged, so the photo on screen
        // stays up and only the *next* pick follows the new rule.
        engine.setPlayback(effectiveSelection, effectiveFavorites, effectiveFolders.toList())
        engine.setShuffleContext(
            activePlaylistId = playlist.id,
            collageLookahead = if (effectiveCollage.mode == PortraitCollageMode.OFF) 0 else 12,
            unavailableSourceIds = playlistUnavailableSourceIds(),
            exhaustedUnavailableSourceIds = playlistExhaustedSourceIds(),
        )

        val observedSignature = SourceRuntimeSignature.of(s)
        val previousObserved = lastObservedSourceSignature
        if (previousObserved == null || previousObserved != observedSignature) {
            lastObservedSourceSignature = observedSignature
            // Explicit source-changing actions own their operation and trigger. Their
            // DataStore echo updates UI state but must not create a second generic
            // SOURCE_SETTINGS_CHANGED operation racing the requested one.
            if (explicitSourceChange.get() == null) {
                enqueueSourceApplication(
                    settings = s,
                    refreshToken = sourceRefreshSequence.incrementAndGet(),
                    trigger = if (previousObserved == null) {
                        SourceRefreshTrigger.INITIAL_SETTINGS_LOAD
                    } else {
                        SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
                    },
                    origin = DiagnosticOrigin.APP,
                )
            }
        }
    }

    private fun enqueueSourceApplication(
        settings: AppSettings,
        refreshToken: Long,
        trigger: SourceRefreshTrigger,
        origin: DiagnosticOrigin,
    ): SourceApplyRequest {
        val signature = SourceRuntimeSignature.of(settings)
        val operation = diagnostics.operations.start("SOURCE_REFRESH", origin)
        val request = SourceApplyRequest(
            settings = settings,
            signature = signature,
            refreshToken = refreshToken,
            trigger = trigger,
            operation = operation,
            configRevision = diagnosticToken(signature, "config"),
            startedElapsedMs = SystemClock.elapsedRealtime(),
        )
        val requestFields = sourceRequestFields(request, stage = "REQUESTED")
        diagnostics.logEvent(
            "SOURCE_REFRESH_REQUESTED",
            requestFields,
            request.operation.context(),
        )
        diagnostics.logEvent(
            "SOURCE_APPLY_QUEUED",
            sourceRequestFields(request, stage = "QUEUED"),
            request.operation.context(),
        )

        synchronized(sourceRequestEnqueueLock) {
            val queued = sourceApplyRequests.value
            if (queued != null && !queued.terminalLogged.get()) {
                if (queued.refreshToken > refreshToken) {
                    diagnostics.logEvent(
                        "SOURCE_APPLY_COALESCED",
                        sourceRequestFields(request, stage = "IGNORED_OLDER") + mapOf(
                            "coalescedWithOperationId" to queued.operation.operationId,
                        ),
                        request.operation.context(),
                    )
                    finishSourceRefresh(request, "SOURCE_REFRESH_CANCELLED", "OLDER_REQUEST")
                    return request
                }
                queued.supersededByOperationId.compareAndSet(null, operation.operationId)
                diagnostics.logEvent(
                    "SOURCE_APPLY_SUPERSEDED",
                    sourceRequestFields(queued, stage = "SUPERSEDED") + mapOf(
                        "supersededByOperationId" to operation.operationId,
                    ),
                    queued.operation.context(),
                )
                if (activeSourceRequest !== queued) {
                    finishSourceRefresh(queued, "SOURCE_REFRESH_CANCELLED", "SUPERSEDED_BEFORE_START")
                }
            }
            sourceApplyRequests.value = request
        }
        return request
    }

    /**
     * Force a rebuild using the repository's current value, never [lastSettings].
     *
     * The distinction matters immediately after a source switch: the UI snapshot can
     * still describe a timed-out Synology request while DataStore already contains the
     * new SMB configuration. The monotonically increasing token also makes a rebuild
     * observable when the non-secret settings (and therefore their signature) did not
     * change, such as after replacing a password under the same credential reference.
     */
    private suspend fun requestSourceRefresh(
        trigger: SourceRefreshTrigger,
        origin: DiagnosticOrigin,
        awaitCompletion: Boolean = false,
    ): Long {
        val token = sourceRefreshRequestLock.withLock {
            val current = services.settings.settings.first()
            // Prevent a delayed DataStore observation of this same snapshot from
            // enqueueing a second, generic source operation after the explicit one.
            lastObservedSourceSignature = SourceRuntimeSignature.of(current)
            sourceRefreshSequence.incrementAndGet().also { refreshToken ->
                val request = enqueueSourceApplication(current, refreshToken, trigger, origin)
                if (trigger == SourceRefreshTrigger.REBUILD_ANDROID_UI ||
                    trigger == SourceRefreshTrigger.REBUILD_WEB_UI
                ) {
                    diagnostics.logEvent(
                        "REBUILD_REQUESTED",
                        sourceRequestFields(request, stage = "REQUESTED"),
                        request.operation.context(),
                    )
                }
            }
        }
        if (awaitCompletion) {
            sourceRefreshCompleted.first { completed -> completed >= token }
        }
        return token
    }

    /**
     * Persist a source-affecting change and emit exactly one explicitly-triggered
     * refresh. The marker covers both possible DataStore orderings: observer first or
     * requester first. The mutex keeps two UI mutations from stealing each other's
     * trigger metadata.
     */
    private suspend fun updateSettingsAndRefresh(
        trigger: SourceRefreshTrigger,
        origin: DiagnosticOrigin = DiagnosticOrigin.ANDROID_UI,
        transform: (AppSettings) -> AppSettings,
    ) = sourceSettingsMutationLock.withLock {
        val marker = ExplicitSourceChange(trigger, origin)
        explicitSourceChange.set(marker)
        try {
            services.settings.update(transform)
            requestSourceRefresh(trigger, origin)
        } finally {
            explicitSourceChange.compareAndSet(marker, null)
        }
    }

    /** Latest-wins source activation target collected by the dedicated source stream. */
    private suspend fun applySourceRequest(request: SourceApplyRequest) {
        activeSourceRequest = request
        diagnostics.operations.update(request.operation.operationId, "APPLY_STARTED")
        diagnostics.logEvent(
            "SOURCE_APPLY_STARTED",
            sourceRequestFields(request, stage = "APPLY_STARTED"),
            request.operation.context(),
        )
        val replacementGeneration = remoteSources.beginReplacement()
        try {
            // applySource replaces every resolved source instance even for a manual
            // rebuild whose settings signature is unchanged. Its scans and background
            // hash pass own the old instance, so they must unwind before it is closed.
            cancelObsoleteIndexWork(request.operation.operationId)

            // applySource and its helpers use the current settings for scan filters,
            // playlist restrictions, failure limits, and fallback policy. Pin them to
            // the same repository snapshot that produced this request.
            lastSettings = request.settings
            val activeKinds = request.settings.source.let { listOf(it.kind) + it.alsoPlay }
            if (ActiveSourceKind.SYNOLOGY !in activeKinds) pendingSynologyOtp = null
            applySource(request.settings.source, request)
            diagnostics.logEvent(
                "SOURCE_POOL_CONFIGURED",
                sourceRequestFields(request, stage = "POOL_CONFIGURED") + mapOf(
                    "poolSize" to primaryPoolIds.size.toString(),
                    "outcome" to "CONFIGURED",
                ),
                request.operation.context(),
            )
            logRuntimeContextReady(request)
            finishSourceRefresh(request, "SOURCE_REFRESH_COMPLETED", "COMPLETED")
        } catch (cancelled: CancellationException) {
            finishSourceRefresh(
                request,
                "SOURCE_REFRESH_CANCELLED",
                if (request.supersededByOperationId.get() != null) "SUPERSEDED" else "CANCELLED",
            )
            throw cancelled
        } catch (error: Exception) {
            diagnostics.logEvent(
                "SOURCE_APPLY_FAILED",
                sourceRequestFields(request, stage = "FAILED") + mapOf(
                    "errorClass" to error.javaClass.simpleName,
                    "errorCode" to "SOURCE_APPLY_EXCEPTION",
                    "outcome" to "FAILED",
                ),
                request.operation.context(),
            )
            finishSourceRefresh(request, "SOURCE_REFRESH_FAILED", "FAILED", error.javaClass.simpleName)
            _state.update {
                it.copy(
                    indexingFound = null,
                    transientNotice = appContext.getString(R.string.msg_source_apply_failed),
                )
            }
        } finally {
            remoteSources.finishReplacement(replacementGeneration)
            if (activeSourceRequest === request) activeSourceRequest = null
            synchronized(sourceRequestEnqueueLock) {
                if (sourceApplyRequests.value === request) sourceApplyRequests.value = null
            }
            sourceRefreshCompleted.update { completed -> maxOf(completed, request.refreshToken) }
        }
    }

    private fun sourceRequestFields(request: SourceApplyRequest, stage: String): Map<String, String> = mapOf(
        "sourceKind" to request.settings.source.kind.name,
        "trigger" to request.trigger.name,
        "configRevision" to request.configRevision,
        "refreshToken" to request.refreshToken.toString(),
        "stage" to stage,
        "durationMs" to (SystemClock.elapsedRealtime() - request.startedElapsedMs).coerceAtLeast(0L).toString(),
    )

    /** Convert ordinary source errors to a fallback without swallowing latest-wins cancellation. */
    private suspend fun <T> cancellableOrDefault(default: T, block: suspend () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            default
        }

    private suspend fun logRuntimeContextReady(request: SourceApplyRequest) {
        if (!runtimeContextLogged.compareAndSet(false, true)) return
        val indexedCount = runCatching { services.photoDao.count() }.getOrElse {
            runtimeContextLogged.set(false)
            return
        }
        diagnostics.logEvent(
            "RUNTIME_CONTEXT_READY",
            mapOf(
                "sourceKind" to request.settings.source.kind.name,
                "configRevision" to request.configRevision,
                "indexedCount" to indexedCount.toString(),
                "engineState" to engine.ui.value.state.name,
                "stage" to "READY",
            ),
            request.operation.context(),
        )
    }

    private fun finishSourceRefresh(
        request: SourceApplyRequest,
        terminalCode: String,
        outcome: String,
        errorClass: String? = null,
    ) {
        if (!request.terminalLogged.compareAndSet(false, true)) return
        val fields = sourceRequestFields(request, stage = "TERMINAL") + buildMap {
            put("outcome", outcome)
            request.supersededByOperationId.get()?.let { put("supersededByOperationId", it) }
            errorClass?.let {
                put("errorClass", it)
                put("errorCode", "SOURCE_REFRESH_EXCEPTION")
            }
        }
        diagnostics.logEvent(terminalCode, fields, request.operation.context())
        diagnostics.operations.finish(request.operation.operationId)
        sourceRefreshCompleted.update { completed -> maxOf(completed, request.refreshToken) }
    }

    /**
     * A source replacement must not retain work that owns the previous source instance.
     * This includes same-signature rebuilds: [applySource] always creates a fresh remote
     * source, so coalescing with the old instance would hand the replacement request a
     * scan result whose follow-up hash pass can no longer open that source.
     */
    private suspend fun cancelObsoleteIndexWork(replacementOperationId: String) {
        val obsoleteFlights = scanFlightsLock.withLock {
            scanFlights.values.toSet().also { scanFlights.clear() }
        }
        obsoleteFlights.forEach { flight ->
            diagnostics.logEvent(
                "SCAN_SUPERSEDED",
                mapOf(
                    "sourceKind" to flight.sourceKind,
                    "trigger" to flight.trigger.name,
                    "configRevision" to flight.configRevision,
                    "supersededByOperationId" to replacementOperationId,
                    "cancellationReason" to "SOURCE_INSTANCE_REPLACED",
                    "completionState" to "SUPERSEDED",
                ),
                DiagnosticContext(
                    origin = DiagnosticOrigin.INTERNAL,
                    operationId = flight.operationId,
                    parentOperationId = flight.parentOperationId,
                ),
            )
            flight.deferred.cancel(CancellationException("SOURCE_INSTANCE_REPLACED"))
        }
        obsoleteFlights.forEach { flight ->
            try {
                flight.deferred.await()
            } catch (_: CancellationException) {
                // Expected: this scan belongs to the superseded source configuration.
            } catch (_: Exception) {
                // The replacement activation reports its own result; an obsolete scan's
                // failure must not prevent the new source from starting.
            } finally {
                // Covers a deferred cancelled before [performIndex] entered its finally.
                diagnostics.operations.finish(flight.operationId)
            }
        }
        val obsoleteHashJobs = contentHashJobs.values.toList()
        contentHashJobs.clear()
        obsoleteHashJobs.forEach { it.cancel() }
        obsoleteHashJobs.forEach { it.join() }
    }

    /**
     * Outcome of bringing one configured source online.
     *
     * [healthy] means the source answered its health check and has been indexed, so its
     * photos may join the primary pool. An unhealthy slot still returns a result so a
     * recovery loop can be started for it.
     */
    private data class ActivatedSlot(
        val sourceId: String,
        val label: String,
        val healthy: Boolean,
        val remote: PhotoSource? = null,
        val healthTimeoutMs: Long = 8_000,
    )

    /**
     * Resolve every configured source, health-check and index them, then configure the
     * engine with the merged primary pool (spec §9.3).
     *
     * The pool is the union of the healthy sources. An unhealthy source is dropped from
     * the pool rather than blanking the frame; only when *nothing* is healthy does the
     * `on_unreachable` policy come into play, because stale cached bytes are a
     * consolation prize that is pointless while another source is still serving live
     * photos at full quality.
     */
    private suspend fun applySource(source: ActiveSource, request: SourceApplyRequest) {
        // A watcher from a superseded apply must never configure the engine for a source
        // this apply is about to replace.
        earlyPlaybackJob?.cancelAndJoin()
        earlyPlaybackJob = null
        val obsoleteRecoveryJobs = recoveryJobs.toList()
        recoveryJobs.clear()
        obsoleteRecoveryJobs.forEach { it.cancel() }
        obsoleteRecoveryJobs.forEach { it.join() }
        recoveryRuntimes.values.forEach { it.wake.close() }
        recoveryRuntimes.clear()
        cancelAndJoinSourceConsumingJobs()
        releaseResolvedSources()
        primaryPoolIds.clear()
        unavailablePoolIds.clear()
        exhaustedUnavailablePoolIds.clear()
        chosenSlot = null
        remotePrimaryCachedOnly = false
        remotePrimarySourceId = null

        expireStaleDecodeSuppression()

        val localUploadCount = cancellableOrDefault(0) {
            indexSource(services.localUploadSource, ServiceLocator.SOURCE_LOCAL_UPLOADS, request)
        }
        if (localUploadCount > 0) primaryPoolIds.add(ServiceLocator.SOURCE_LOCAL_UPLOADS)

        if (source.kind == ActiveSourceKind.NONE) {
            if (localUploadCount > 0) {
                _state.update { it.copy(surface = Surface.Playing, indexingFound = null) }
                engine.configure(
                    playlistFilteredIds(primaryPoolIds.toList()), emptyList(),
                    currentIntervalSeconds(), currentMaxFailures(),
                )
            } else {
                _state.update { it.copy(surface = Surface.FirstRun, indexingFound = null) }
                engine.configure(emptyList(), emptyList(), currentIntervalSeconds(), currentMaxFailures())
            }
            return
        }

        // Samples as the chosen source, with nothing merged in, is its own case: there is
        // no fallback pool behind it because it *is* the fallback pool.
        val extras = source.alsoPlay.filter {
            it != ActiveSourceKind.NONE && it != ActiveSourceKind.SAMPLES && it != source.kind
        }
        if (source.kind == ActiveSourceKind.SAMPLES && extras.isEmpty()) {
            services.fallbackSource.ensureMaterialized()
            val total = indexSource(services.fallbackSource, ServiceLocator.SOURCE_FALLBACK, request)
            val pool = (listOf(ServiceLocator.SOURCE_FALLBACK) + primaryPoolIds).distinct()
            val filtered = playlistFilteredIds(pool)
            _state.update { it.copy(surface = if (total == 0 && localUploadCount == 0) Surface.EmptyIndex else Surface.Playing) }
            engine.configure(filtered, emptyList(), currentIntervalSeconds(), currentMaxFailures())
            return
        }

        ensureFallbackIndexed(request)

        val kinds = (listOf(source.kind) + extras)
            .distinct()
            .filter { it != ActiveSourceKind.NONE && it != ActiveSourceKind.SAMPLES }

        val slots = mutableListOf<ActivatedSlot>()
        for (kind in kinds) {
            // A co-primary that is merely misconfigured must not take down the whole
            // frame, so only the *chosen* source can send us to the first-run surface.
            val slot = activateSlot(kind, source, isChosen = kind == source.kind, request = request)
            if (slot == null) {
                if (kind == source.kind) return   // activateSlot already chose a surface
                continue
            }
            slots.add(slot)
        }

        if (slots.isEmpty()) { fallBackToFirstRun(); return }

        val chosenId = sourceIdFor(source.kind)
        chosenSlot = slots.firstOrNull { it.sourceId == chosenId } ?: slots.first()
        primaryPoolIds.addAll(slots.filter { it.healthy }.map { it.sourceId })
        unavailablePoolIds.addAll(slots.filterNot { it.healthy }.map { it.sourceId })
        // Preserve the app-managed local upload library when it was indexed above.
        // initialPlan(slots) cannot see that built-in source, so plan from the complete
        // merged pool instead.
        applyPlan(SourcePoolPolicy.planFor(primaryPoolIds, chosenSlot?.sourceId))

        for (slot in slots) {
            val remote = slot.remote ?: continue
            startSourceRecovery(
                remote, slot.sourceId, slot.label,
                initiallyPrimary = slot.healthy, healthTimeoutMs = slot.healthTimeoutMs,
            )
        }
    }

    /** Stable source id for a configured kind; samples/none have no primary id. */
    private fun sourceIdFor(kind: ActiveSourceKind): String? = when (kind) {
        ActiveSourceKind.LOCAL_SAF -> ServiceLocator.SOURCE_LOCAL_SAF
        ActiveSourceKind.SMB -> ServiceLocator.SOURCE_SMB
        ActiveSourceKind.SYNOLOGY -> ServiceLocator.SOURCE_SYNOLOGY
        ActiveSourceKind.WEBDAV -> ServiceLocator.SOURCE_WEBDAV
        ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> null
    }

    /**
     * Bring one configured source online: build it, health-check it, and index it if it
     * answered. Returns null when the slot cannot be used at all.
     *
     * [isChosen] distinguishes the source the user selected from one merged in behind
     * it. Only the chosen source may move the UI to an error or first-run surface; a
     * broken co-primary is logged and skipped, because taking the frame off a working
     * source would be a worse outcome than quietly playing fewer photos.
     */
    @Suppress("DEPRECATION")
    private fun hasConnectedNetwork(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return manager?.activeNetworkInfo?.isConnected == true
    }

    /**
     * Android can launch the frame several seconds before Wi-Fi/Ethernet is usable after
     * boot. Do not turn that platform race into a false SMB/WebDAV/Synology host failure.
     * Manual tests and user-initiated source changes stay immediate; only the initial
     * settings application receives this bounded network-ready grace period.
     */
    private suspend fun awaitInitialRemoteNetwork(
        request: SourceApplyRequest,
        kind: ActiveSourceKind,
    ) {
        if (request.trigger != SourceRefreshTrigger.INITIAL_SETTINGS_LOAD) return
        if (kind !in setOf(ActiveSourceKind.SMB, ActiveSourceKind.SYNOLOGY, ActiveSourceKind.WEBDAV)) return
        if (hasConnectedNetwork()) return

        val started = SystemClock.elapsedRealtime()
        diagnostics.logEvent(
            "SOURCE_HEALTH_DEFERRED_NETWORK",
            sourceRequestFields(request, "WAITING_FOR_NETWORK") + mapOf(
                "sourceKind" to kind.name,
                "maxWaitMs" to INITIAL_REMOTE_NETWORK_WAIT_MS.toString(),
            ),
            request.operation.context(),
        )
        while (viewModelScope.isActive && !hasConnectedNetwork() &&
            SystemClock.elapsedRealtime() - started < INITIAL_REMOTE_NETWORK_WAIT_MS
        ) {
            delay(INITIAL_REMOTE_NETWORK_POLL_MS)
        }
        diagnostics.logEvent(
            "SOURCE_HEALTH_NETWORK_GATE_RELEASED",
            sourceRequestFields(request, "NETWORK_GATE_RELEASED") + mapOf(
                "sourceKind" to kind.name,
                "networkConnected" to hasConnectedNetwork().toString(),
                "waitedMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
            ),
            request.operation.context(),
        )
    }

    private suspend fun activateSlot(
        kind: ActiveSourceKind,
        source: ActiveSource,
        isChosen: Boolean,
        request: SourceApplyRequest,
    ): ActivatedSlot? = when (kind) {
        ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> null

        ActiveSourceKind.LOCAL_SAF -> {
            val uriStr = source.treeUri
            if (uriStr.isNullOrBlank()) {
                if (isChosen) fallBackToFirstRun()
                null
            } else {
                val saf = services.safSource(Uri.parse(uriStr))
                val health = healthCheckWithDiagnostics(saf, kind, 5_000, request)
                if (health is SourceHealth.Ok) {
                    startEarlyPlaybackWatcher(ServiceLocator.SOURCE_LOCAL_SAF, "LOCAL_SAF")
                    indexSource(saf, ServiceLocator.SOURCE_LOCAL_SAF, request)
                    ActivatedSlot(ServiceLocator.SOURCE_LOCAL_SAF, "SAF", healthy = true)
                } else {
                    // A revoked SAF grant needs the user, so it is reported rather than
                    // retried; there is no recovery loop that can re-grant permission.
                    if (isChosen) {
                        when (health) {
                            is SourceHealth.NeedsPermission ->
                                recover(R.string.msg_permission_revoked, "PERMISSION_REVOKED", request)
                            is SourceHealth.Missing ->
                                recover(R.string.msg_folder_missing, "FOLDER_MISSING", request)
                            is SourceHealth.ProviderError -> {
                                diagnostics.log(
                                    DiagnosticsLog.Category.SOURCE,
                                    "SAF_PROVIDER_ERROR",
                                    "sourceKind" to "LOCAL_SAF",
                                    "errorCode" to healthErrorCode(health),
                                )
                                recover(R.string.msg_provider_error, "PROVIDER_ERROR", request)
                            }
                            else -> recover(R.string.msg_provider_error, "UNAVAILABLE", request)
                        }
                    } else {
                        diagnostics.log(
                            DiagnosticsLog.Category.SOURCE,
                            "SAF_SKIPPED",
                            "sourceKind" to "LOCAL_SAF",
                            "errorCode" to healthErrorCode(health),
                        )
                    }
                    null
                }
            }
        }

        ActiveSourceKind.SMB -> {
            awaitInitialRemoteNetwork(request, kind)
            val smb = source.smb
            if (smb == null || smb.host.isBlank() || smb.share.isBlank()) {
                if (isChosen) fallBackToFirstRun()
                null
            } else {
                val password = services.secretStore.reveal(smb.credentialRef) ?: ""
                val src = services.smbSource(
                    SmbConnection(smb.host, smb.share, smb.path),
                    SmbCredentials(smb.domain, smb.user, password),
                )
                remoteSources.promote(ServiceLocator.SOURCE_SMB, src)
                val healthy = healthCheckWithDiagnostics(src, kind, 8_000, request) is SourceHealth.Ok
                if (healthy) {
                    startEarlyPlaybackWatcher(ServiceLocator.SOURCE_SMB, "SMB")
                    indexSource(src, ServiceLocator.SOURCE_SMB, request)
                } else diagnostics.logEvent(
                    "SOURCE_UNAVAILABLE",
                    sourceRequestFields(request, "HEALTH_FAILED") + mapOf(
                        "sourceKind" to "SMB",
                        "outcome" to "UNAVAILABLE",
                        "errorCode" to "HEALTH_CHECK_FAILED",
                    ),
                    request.operation.context(),
                )
                ActivatedSlot(ServiceLocator.SOURCE_SMB, "SMB", healthy, src)
            }
        }

        ActiveSourceKind.SYNOLOGY -> {
            awaitInitialRemoteNetwork(request, kind)
            val syn = source.synology
            if (syn == null || syn.baseUrl.isBlank()) {
                if (isChosen) fallBackToFirstRun()
                null
            } else {
                val password = services.secretStore.reveal(syn.credentialRef) ?: ""
                val src = services.synologySource(
                    SynologyConnection(
                        baseUrl = syn.baseUrl,
                        folderPath = syn.folderPath,
                        useThumbnails = syn.useThumbnails,
                        thumbnailSize = syn.thumbnailSize,
                        pinnedCertSha256 = syn.pinnedCertSha256,
                    ),
                    // The one-time code is only ever held in memory, for this login.
                    SynologyCredentials(syn.user, password, pendingSynologyOtp),
                )
                remoteSources.promote(ServiceLocator.SOURCE_SYNOLOGY, src)
                pendingSynologyOtp = null
                val health = healthCheckWithDiagnostics(src, kind, 10_000, request)
                val healthy = health is SourceHealth.Ok
                if (healthy) {
                    startEarlyPlaybackWatcher(ServiceLocator.SOURCE_SYNOLOGY, "SYNOLOGY")
                    indexSource(src, ServiceLocator.SOURCE_SYNOLOGY, request)
                } else {
                    diagnostics.log(
                        DiagnosticsLog.Category.SOURCE,
                        "SYNOLOGY_UNAVAILABLE",
                        "sourceKind" to "SYNOLOGY",
                        "errorCode" to healthErrorCode(health),
                    )
                    diagnostics.logEvent(
                        "SOURCE_UNAVAILABLE",
                        sourceRequestFields(request, "HEALTH_FAILED") + mapOf(
                            "sourceKind" to "SYNOLOGY",
                            "outcome" to "UNAVAILABLE",
                            "errorCode" to healthErrorCode(health),
                        ),
                        request.operation.context(),
                    )
                }
                ActivatedSlot(ServiceLocator.SOURCE_SYNOLOGY, "SYNOLOGY", healthy, src, healthTimeoutMs = 10_000)
            }
        }

        ActiveSourceKind.WEBDAV -> {
            awaitInitialRemoteNetwork(request, kind)
            val dav = source.webdav
            if (dav == null || dav.baseUrl.isBlank()) {
                if (isChosen) fallBackToFirstRun()
                null
            } else {
                val password = services.secretStore.reveal(dav.credentialRef) ?: ""
                val src = services.webDavSource(
                    WebDavConnection(
                        baseUrl = dav.baseUrl,
                        rootPath = dav.rootPath,
                        folderPath = dav.folderPath,
                        pinnedCertSha256 = dav.pinnedCertSha256,
                    ),
                    WebDavCredentials(dav.user, password),
                )
                remoteSources.promote(ServiceLocator.SOURCE_WEBDAV, src)
                val health = healthCheckWithDiagnostics(src, kind, 10_000, request)
                val healthy = health is SourceHealth.Ok
                if (healthy) {
                    startEarlyPlaybackWatcher(ServiceLocator.SOURCE_WEBDAV, "WEBDAV")
                    indexSource(src, ServiceLocator.SOURCE_WEBDAV, request)
                } else {
                    diagnostics.log(
                        DiagnosticsLog.Category.SOURCE,
                        "WEBDAV_UNAVAILABLE",
                        "sourceKind" to "WEBDAV",
                        "errorCode" to healthErrorCode(health),
                    )
                    diagnostics.logEvent(
                        "SOURCE_UNAVAILABLE",
                        sourceRequestFields(request, "HEALTH_FAILED") + mapOf(
                            "sourceKind" to "WEBDAV",
                            "outcome" to "UNAVAILABLE",
                            "errorCode" to healthErrorCode(health),
                        ),
                        request.operation.context(),
                    )
                }
                ActivatedSlot(ServiceLocator.SOURCE_WEBDAV, "WEBDAV", healthy, src, healthTimeoutMs = 10_000)
            }
        }
    }

    private suspend fun ensureFallbackIndexed(request: SourceApplyRequest) {
        services.fallbackSource.ensureMaterialized()
        indexSource(services.fallbackSource, ServiceLocator.SOURCE_FALLBACK, request)
    }

    private suspend fun healthCheckWithDiagnostics(
        source: PhotoSource,
        kind: ActiveSourceKind,
        timeoutMs: Long,
        request: SourceApplyRequest,
    ): SourceHealth = healthCheckWithDiagnostics(
        source = source,
        sourceKind = kind.name,
        timeoutMs = timeoutMs,
        trigger = request.trigger,
        configRevision = request.configRevision,
        parentOperation = request.operation,
    )

    private suspend fun healthCheckWithDiagnostics(
        source: PhotoSource,
        sourceKind: String,
        timeoutMs: Long,
        trigger: SourceRefreshTrigger,
        configRevision: String,
        parentOperation: DiagnosticOperationTracker.Handle?,
    ): SourceHealth {
        val operation = diagnostics.operations.start(
            type = "SOURCE_HEALTH",
            origin = parentOperation?.origin ?: DiagnosticOrigin.RECOVERY,
            parentOperationId = parentOperation?.operationId,
        )
        val started = SystemClock.elapsedRealtime()
        val base = mapOf(
            "sourceKind" to sourceKind,
            "trigger" to trigger.name,
            "configRevision" to configRevision,
            "timeoutMs" to timeoutMs.toString(),
            "stage" to "HEALTH_CHECK",
        )
        diagnostics.logEvent("SOURCE_HEALTH_CHECK_STARTED", base, operation.context())
        diagnostics.operations.update(operation.operationId, "RUNNING")
        try {
            val health = source.healthCheck(timeoutMs)
            val fields = base + mapOf(
                "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                "healthState" to healthStateCode(health),
                "outcome" to if (health is SourceHealth.Ok) "HEALTHY" else "UNAVAILABLE",
                "errorCode" to healthErrorCode(health),
            ) + ((health as? SourceHealth.ProviderError)?.let { mapOf("errorClass" to it.detail.take(80)) } ?: emptyMap())
            diagnostics.logEvent(
                if (health is SourceHealth.Ok) "SOURCE_HEALTH_CHECK_COMPLETED"
                else "SOURCE_HEALTH_CHECK_FAILED",
                fields,
                operation.context(),
            )
            return health
        } catch (cancelled: CancellationException) {
            diagnostics.logEvent(
                "SOURCE_HEALTH_CHECK_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "outcome" to "CANCELLED",
                    "errorCode" to "CANCELLED",
                    "cancellationReason" to "PARENT_CANCELLED",
                ),
                operation.context(),
            )
            throw cancelled
        } catch (error: Exception) {
            diagnostics.logEvent(
                "SOURCE_HEALTH_CHECK_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "outcome" to "FAILED",
                    "errorClass" to error.javaClass.simpleName,
                    "errorCode" to "HEALTH_CHECK_EXCEPTION",
                ),
                operation.context(),
            )
            throw error
        } finally {
            diagnostics.operations.finish(operation.operationId)
        }
    }

    /**
     * Rebuild the engine's primary pool from [primaryPoolIds], the set of sources
     * currently believed healthy.
     *
     * Called on every promote/demote instead of setting the pool to the one source that
     * changed — with several primaries merged, "this source recovered" must not silently
     * evict the others. When the set empties, the `on_unreachable` policy takes over for
     * the source the user actually chose.
     *
     * Mutations to [primaryPoolIds] all happen from `viewModelScope` coroutines, which
     * resume on the main thread, so the set needs no additional synchronization.
     */
    private suspend fun reconfigurePool() {
        applyPlan(SourcePoolPolicy.planFor(primaryPoolIds, chosenSlot?.sourceId))
    }

    private fun playlistUnavailableSourceIds(except: String? = null): Set<String> =
        unavailablePoolIds.asSequence()
            .filter { sourceId -> activePlaylistSourceFilter.isEmpty() || sourceId in activePlaylistSourceFilter }
            .filterNot { it == except }
            .toSet()

    private fun playlistExhaustedSourceIds(except: String? = null): Set<String> =
        exhaustedUnavailablePoolIds.asSequence()
            .filter { sourceId -> activePlaylistSourceFilter.isEmpty() || sourceId in activePlaylistSourceFilter }
            .filterNot { it == except }
            .toSet()

    /**
     * Start playing before a slow enumeration finishes.
     *
     * Enumerating a large SMB/WebDAV/Synology tree can take minutes, and `Indexer` writes
     * to Room in batches as it goes, so playable rows exist long before the scan ends.
     * Without this, the frame stays blank for that whole time even though it already has
     * photos it could show. On a restart it is even more pronounced: the *previous* index
     * is still in Room (stale rows are only reconciled away when the scan reaches
     * `Finished`), so the frame can resume essentially immediately.
     *
     * Deliberately started only after the source's health check passed. Configuring the
     * engine against a source that turns out to be unreachable would let it burn real
     * decode failures on photos whose bytes cannot be fetched, and
     * `decodeFailureCount` quarantines a photo after [currentMaxFailures] of those.
     *
     * The watcher is superseded by the authoritative [applyPlan] at the end of the apply,
     * which reconfigures from the fully indexed pool.
     */
    private fun startEarlyPlaybackWatcher(sourceId: String, label: String) {
        earlyPlaybackJob?.cancel()
        earlyPlaybackJob = viewModelScope.launch {
            val available = services.photoDao.displayableCountFlow(
                sourceId,
                currentMaxFailures(),
                if (services.allowHeifPlayback) 1 else 0,
            ).first { it >= EARLY_PLAYBACK_MIN_PHOTOS }

            // primaryPoolIds already holds any local upload library indexed earlier in
            // this apply; include it so early playback uses the same merged pool shape
            // the final configuration will.
            val primary = playlistFilteredIds((primaryPoolIds + sourceId).toList())
            if (primary.isEmpty()) return@launch
            val fallback =
                if (activePlaylistSourceFilter.isEmpty()) listOf(ServiceLocator.SOURCE_FALLBACK) else emptyList()
            _state.update { it.copy(surface = Surface.Playing, stalePlayback = false) }
            engine.configure(
                primary, fallback,
                currentIntervalSeconds(), currentMaxFailures(), primaryCachedOnly = false,
                unavailableSourceIds = playlistUnavailableSourceIds(),
                exhaustedUnavailableSourceIds = playlistExhaustedSourceIds(),
            )
            diagnostics.logEvent(
                "SOURCE_EARLY_PLAYBACK_STARTED",
                mapOf(
                    "sourceKind" to label,
                    "found" to available.toString(),
                    "poolSize" to primary.size.toString(),
                ),
            )
        }
    }

    /**
     * Carry out a [SourcePoolPolicy.Plan]. The decision of *what* to play is made by the
     * pure policy and unit-tested; this function only performs the resulting I/O.
     */
    private suspend fun applyPlan(plan: SourcePoolPolicy.Plan) {
        // Join rather than merely cancel: both run on the main dispatcher, but the
        // watcher could otherwise be resumed past its await and reconfigure the engine
        // from a partial pool *after* this authoritative configuration.
        earlyPlaybackJob?.cancelAndJoin()
        earlyPlaybackJob = null
        when (plan) {
            is SourcePoolPolicy.Plan.Play -> {
                remotePrimaryCachedOnly = false
                _state.update {
                    it.copy(surface = Surface.Playing, indexingFound = null, stalePlayback = false)
                }
                val primary = playlistFilteredIds(plan.primaryIds)
                val fallback = if (activePlaylistSourceFilter.isEmpty()) listOf(ServiceLocator.SOURCE_FALLBACK) else emptyList()
                engine.configure(
                    primary, fallback,
                    currentIntervalSeconds(), currentMaxFailures(), primaryCachedOnly = false,
                    unavailableSourceIds = playlistUnavailableSourceIds(),
                    exhaustedUnavailableSourceIds = playlistExhaustedSourceIds(),
                )
            }
            is SourcePoolPolicy.Plan.Unreachable -> {
                val label = chosenSlot?.label ?: plan.sourceId.uppercase()
                configureForUnreachable(plan.sourceId, label)
            }
            SourcePoolPolicy.Plan.NothingConfigured -> {
                remotePrimaryCachedOnly = false
                _state.update { it.copy(surface = Surface.Playing, stalePlayback = false) }
                engine.configure(
                    emptyList(), listOf(ServiceLocator.SOURCE_FALLBACK),
                    currentIntervalSeconds(), currentMaxFailures(), primaryCachedOnly = false,
                    unavailableSourceIds = playlistUnavailableSourceIds(),
                    exhaustedUnavailableSourceIds = playlistExhaustedSourceIds(),
                )
            }
        }
        // Single funnel for every pool change, so the source indicator never lags behind
        // an activation, a recovery promotion, or a demotion to cached playback.
        publishSourceStatuses()
    }

    /**
     * Configure playback for a remote primary that is currently unreachable
     * (spec §9.3 `on_unreachable`).
     *
     * With [UnreachablePolicy.STALE_CACHE] the frame keeps showing the family's own
     * photos — the ones already in `MediaCache` — instead of visibly dropping to stock
     * sample images during a router reboot or NAS restart. The cached count is checked
     * first because promising stale playback with a cold cache would leave the frame
     * with nothing to show at all; in that case this falls through to the samples,
     * which is exactly the previous behaviour.
     */
    private suspend fun configureForUnreachable(sourceId: String, label: String) {
        remotePrimarySourceId = sourceId
        val policy = lastSettings?.onUnreachable ?: UnreachablePolicy.FALLBACK_SAMPLES
        val cached = if (policy == UnreachablePolicy.STALE_CACHE) {
            cancellableOrDefault(0) {
                services.photoDao.cachedCount(
                    listOf(sourceId),
                    currentMaxFailures(),
                    if (services.allowHeifPlayback) 1 else 0,
                )
            }
        } else 0

        if (cached > 0) {
            remotePrimaryCachedOnly = true
            _state.update { it.copy(surface = Surface.Playing, indexingFound = null, stalePlayback = true) }
            engine.configure(
                listOf(sourceId), listOf(ServiceLocator.SOURCE_FALLBACK),
                currentIntervalSeconds(), currentMaxFailures(), primaryCachedOnly = true,
                unavailableSourceIds = playlistUnavailableSourceIds(except = sourceId),
                exhaustedUnavailableSourceIds = playlistExhaustedSourceIds(except = sourceId),
            )
            diagnostics.log(
                DiagnosticsLog.Category.SOURCE,
                "SOURCE_STALE_CACHE_ACTIVE",
                "sourceKind" to label,
                "cached" to cached.toString(),
            )
        } else {
            remotePrimaryCachedOnly = false
            _state.update { it.copy(surface = Surface.Playing, indexingFound = null, stalePlayback = false) }
            engine.configure(
                emptyList(), listOf(ServiceLocator.SOURCE_FALLBACK),
                currentIntervalSeconds(), currentMaxFailures(), primaryCachedOnly = false,
                unavailableSourceIds = playlistUnavailableSourceIds(),
                exhaustedUnavailableSourceIds = playlistExhaustedSourceIds(),
            )
            diagnostics.log(
                DiagnosticsLog.Category.SOURCE,
                "SOURCE_FALLBACK_ACTIVE",
                "sourceKind" to label,
            )
        }
    }

    /**
     * Stream a source into Room; concurrent requests for the same source/configuration
     * share one job. This prevents a web rescan, scheduled rescan, and recovery promotion
     * from enumerating the same NAS tree simultaneously.
     */
    private suspend fun indexSource(
        source: PhotoSource,
        sourceId: String,
        request: SourceApplyRequest,
    ): Int {
        val scanKey = buildString {
            append(sourceId)
            append('|').append(request.signature)
            append('|').append(request.settings.filters)
        }
        val scanOperation = diagnostics.operations.start(
            type = "SCAN",
            origin = request.operation.origin,
            parentOperationId = request.operation.operationId,
        )
        var created = false
        val flight = scanFlightsLock.withLock {
            scanFlights[scanKey] ?: run {
                val deferred = viewModelScope.async {
                    // Room reconciliation and NAS enumeration are intentionally single-flight
                    // across every source/configuration. Different requests queue instead of
                    // competing for SMB sockets, decoder buffers, and index transactions.
                    scanExecutionLock.withLock { performIndex(source, request, scanOperation) }
                }
                val createdFlight = ScanFlight(
                    deferred = deferred,
                    operationId = scanOperation.operationId,
                    parentOperationId = request.operation.operationId,
                    sourceKind = diagnosticSourceKind(source),
                    trigger = request.trigger,
                    configRevision = request.configRevision,
                )
                scanFlights[scanKey] = createdFlight
                created = true
                deferred.invokeOnCompletion {
                    viewModelScope.launch {
                        scanFlightsLock.withLock {
                            if (scanFlights[scanKey]?.deferred === deferred) scanFlights.remove(scanKey)
                        }
                    }
                }
                createdFlight
            }
        }
        if (!created) {
            val base = scanRequestFields(source, request)
            diagnostics.logEvent(
                "SCAN_COALESCED",
                base + mapOf(
                    "coalescedWithOperationId" to flight.operationId,
                    "scanOwnerOperationId" to flight.operationId,
                    "completionState" to "COALESCED",
                ),
                scanOperation.context(),
            )
            return try {
                val result = flight.deferred.await()
                diagnostics.logEvent(
                    "SCAN_COMPLETED",
                    base + mapOf(
                        "total" to result.total.toString(),
                        "found" to result.found.toString(),
                        "errors" to result.errors.toString(),
                        "exifMisses" to result.exifMisses.toString(),
                        "completionState" to "COALESCED",
                        "reconciled" to result.reconciled.toString(),
                        "scanOwnerOperationId" to flight.operationId,
                    ),
                    scanOperation.context(),
                )
                result.total
            } catch (cancelled: CancellationException) {
                diagnostics.logEvent(
                    "SCAN_ABORTED",
                    base + mapOf(
                        "completionState" to "CANCELLED",
                        "cancellationReason" to if (
                            cancelled.message == "SOURCE_INSTANCE_REPLACED"
                        ) "SOURCE_INSTANCE_REPLACED" else "CALLER_CANCELLED",
                        "reconciled" to "false",
                        "scanOwnerOperationId" to flight.operationId,
                    ),
                    scanOperation.context(),
                )
                throw cancelled
            } finally {
                diagnostics.operations.finish(scanOperation.operationId)
            }
        }
        return flight.deferred.await().total
    }

    private suspend fun performIndex(
        source: PhotoSource,
        request: SourceApplyRequest,
        scanOperation: DiagnosticOperationTracker.Handle,
    ): ScanResult {
        _state.update {
            it.copy(
                surface = if (it.surface == Surface.FirstRun) it.surface else Surface.Playing,
                indexingFound = 0,
            )
        }
        var total = 0
        var scanErrors = 0
        var found = 0
        var exifMisses = 0
        var reconciled = false
        var completionState = "UNKNOWN"
        try {
            services.indexer.index(
                source,
                currentScanOptions(),
                ScanDiagnosticContext(
                    sourceKind = diagnosticSourceKind(source),
                    trigger = request.trigger,
                    configRevision = request.configRevision,
                    context = scanOperation.context(),
                ),
            ).collect { progress ->
                when (progress) {
                    is IndexProgress.Scanning -> _state.update { it.copy(indexingFound = progress.found) }
                    is IndexProgress.Completed -> {
                        total = progress.total
                        scanErrors = progress.errors
                        found = progress.found
                        exifMisses = progress.exifMisses
                        reconciled = progress.reconciled
                        completionState = progress.completionState
                    }
                }
            }
            if (scanErrors > 0) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_scan_incomplete))
                }
            } else {
                scheduleContentHashBackfill(source)
            }
            return ScanResult(
                total = total,
                errors = scanErrors,
                found = found,
                exifMisses = exifMisses,
                reconciled = reconciled,
                completionState = completionState,
            )
        } finally {
            _state.update { it.copy(indexingFound = null) }
            diagnostics.operations.finish(scanOperation.operationId)
        }
    }

    private fun scanRequestFields(source: PhotoSource, request: SourceApplyRequest): Map<String, String> = mapOf(
        "sourceKind" to diagnosticSourceKind(source),
        "trigger" to request.trigger.name,
        "configRevision" to request.configRevision,
    )

    /**
     * Hashing is outside the scan and render critical paths. Repeated scans replace the
     * previous source job, while recovery starts a fresh bounded pass after connectivity
     * returns. The backfiller stops on a fully failed batch to avoid retry storms.
     */
    private suspend fun scheduleContentHashBackfill(source: PhotoSource) {
        // Removing a job from the map without joining it makes it invisible to the next
        // source teardown while it may still own an SMB stream. Drain the prior pass
        // before publishing its replacement as the sole tracked job for this source.
        val previous = contentHashJobs.remove(source.id.value)
        previous?.cancel()
        if (previous != null) withContext(NonCancellable) { previous.join() }
        currentCoroutineContext().ensureActive()
        contentHashJobs[source.id.value] = viewModelScope.launch {
            var totalIndexed = 0
            var totalFailed = 0
            var remaining: Boolean
            do {
                val result = services.contentHashBackfiller.backfillPending(source, maxBatches = 32)
                totalIndexed += result.indexed
                totalFailed += result.failed
                remaining = result.remainingMayExist
                if (result.indexed > 0) {
                    // New hashes can collapse in-folder duplicates. Reconcile after every
                    // bounded pass without clearing queue state or the visible slide.
                    engine.reconcileShuffle()
                }
                if (remaining && result.indexed > 0) delay(1_000L)
                // A pass with no success means the source is unavailable or repeatedly
                // unreadable; source recovery schedules a fresh job later.
                if (remaining && result.indexed == 0) break
            } while (remaining && isActive)
            diagnostics.log(
                DiagnosticsLog.Category.SCAN, "CONTENT_HASH_BACKGROUND_PASS",
                "sourceToken" to diagnosticToken(source.id.value, "source"),
                "indexed" to totalIndexed.toString(),
                "failed" to totalFailed.toString(),
                "remaining" to remaining.toString(),
            )
        }
    }

    /** Scan filters from settings, falling back to the built-in defaults (spec §20). */
    private fun currentScanOptions(): ScanOptions {
        val f = lastSettings?.filters ?: return ScanOptions()
        // An empty include list would match everything; treat "user cleared the field"
        // as "use the defaults" rather than silently indexing every file on the NAS.
        val includes = f.cleanIncludes.ifEmpty { ScanOptions().includeGlobs }
        return ScanOptions(
            includeSubfolders = f.includeSubfolders,
            includeGlobs = includes,
            excludeGlobs = f.cleanExcludes,
            excludeFolders = f.cleanExcludeFolders,
        )
    }

    /**
     * SMB recovery loop (spec §9.5): backoff health checks that toggle the NAS primary
     * in and out and un-suppress + reindex it on recovery. Fallback samples play while
     * it is down. Cancelled when the source changes.
     */
    /**
     * Recovery loop (spec §9.5) for any remote primary: backoff health checks that swap
     * the source in and out of the engine's primary pool, un-suppressing and reindexing
     * it on recovery, while the bundled samples keep playing throughout.
     *
     * Source-agnostic on purpose — SMB and Synology differ only in their id, health-check
     * budget and diagnostics label, and a frame that runs unattended for weeks needs
     * exactly the same behaviour from both. Cancelled whenever the source changes.
     */
    private fun startSourceRecovery(
        src: PhotoSource,
        sourceId: String,
        label: String,
        initiallyPrimary: Boolean,
        healthTimeoutMs: Long = 8_000,
    ) {
        val runtime = RecoveryRuntime(SourceRecoveryCoordinator(initiallyPrimary))
        recoveryRuntimes.remove(sourceId)?.wake?.close()
        recoveryRuntimes[sourceId] = runtime
        val job = viewModelScope.launch {
            // Timing and transitions live in RecoveryPolicy, which is unit-tested; this
            // loop owns only the I/O. Keeping a second copy of the schedule here is
            // exactly how the tested version and the running version drift apart.
            var recoveryOperation: DiagnosticOperationTracker.Handle? = null
            try {
              while (isActive) {
                val settingsSnapshot = lastSettings ?: services.settings.settings.first()
                val signature = SourceRuntimeSignature.of(settingsSnapshot)
                val configRevision = diagnosticToken(signature, "config")
                val check = runtime.coordinator.beginCheck(
                    actualPrimaryActive = sourceId in primaryPoolIds && sourceId !in unavailablePoolIds,
                )
                if (!check.state.primaryActive && recoveryOperation == null) {
                    recoveryOperation = diagnostics.operations.start(
                        "SOURCE_RECOVERY", DiagnosticOrigin.RECOVERY,
                    ).also { started ->
                        diagnostics.logEvent(
                            "SOURCE_RECOVERY_STARTED",
                            mapOf(
                                "sourceKind" to label,
                                "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                "configRevision" to configRevision,
                                "stage" to "BACKOFF",
                            ),
                            started.context(),
                        )
                    }
                }
                val healthTrigger = if (check.state.primaryActive) {
                    SourceRefreshTrigger.PERIODIC_HEALTH_MONITOR
                } else {
                    SourceRefreshTrigger.RECOVERY_PROMOTION
                }
                val healthy = healthCheckWithDiagnostics(
                    source = src,
                    sourceKind = label,
                    timeoutMs = healthTimeoutMs,
                    trigger = healthTrigger,
                    configRevision = configRevision,
                    parentOperation = recoveryOperation,
                ) is SourceHealth.Ok
                val decision = runtime.coordinator.decide(
                    check,
                    healthy,
                    jitterMs = (0..RecoveryPolicy.MAX_JITTER_MS).random(),
                )
                val step = decision.step
                var nextWaitMs = step.waitMs
                if (decision.superseded) {
                    diagnostics.logEvent(
                        "SOURCE_HEALTH_RESULT_SUPERSEDED",
                        mapOf(
                            "sourceKind" to label,
                            "sourceToken" to diagnosticToken(sourceId, "source"),
                            "trigger" to "PLAYBACK_READ_FAILURE",
                            "configRevision" to configRevision,
                            "stage" to "BACKOFF",
                        ),
                        recoveryOperation?.context()
                            ?: DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
                    )
                }
                when (step.action) {
                    RecoveryPolicy.Action.PROMOTE -> {
                        val operation = recoveryOperation ?: diagnostics.operations.start(
                            "SOURCE_RECOVERY", DiagnosticOrigin.RECOVERY,
                        ).also { started ->
                            diagnostics.logEvent(
                                "SOURCE_RECOVERY_STARTED",
                                mapOf(
                                    "sourceKind" to label,
                                    "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                    "configRevision" to configRevision,
                                    "stage" to "PROMOTING",
                                ),
                                started.context(),
                            )
                        }
                        recoveryOperation = operation
                        try {
                            services.photoDao.clearSuppression(sourceId)
                            val recoveryRequest = SourceApplyRequest(
                                settings = settingsSnapshot,
                                signature = signature,
                                refreshToken = sourceRefreshSequence.get(),
                                trigger = SourceRefreshTrigger.RECOVERY_PROMOTION,
                                operation = operation,
                                configRevision = configRevision,
                                startedElapsedMs = SystemClock.elapsedRealtime(),
                            )
                            indexSource(src, sourceId, recoveryRequest)
                            if (runtime.coordinator.promotionStillValid(check)) {
                                // Rejoin the merged pool rather than replacing it: other
                                // primaries may have been playing happily throughout.
                                val promoted = SourcePoolPolicy.afterPromote(primaryPoolIds, sourceId)
                                primaryPoolIds.clear(); primaryPoolIds.addAll(promoted)
                                unavailablePoolIds.remove(sourceId)
                                exhaustedUnavailablePoolIds.remove(sourceId)
                                reconfigurePool()
                                diagnostics.logEvent(
                                    "SOURCE_RECOVERED",
                                    mapOf(
                                        "sourceKind" to label,
                                        "sourceToken" to diagnosticToken(sourceId, "source"),
                                        "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                        "configRevision" to configRevision,
                                    ),
                                    operation.context(),
                                )
                            } else {
                                diagnostics.logEvent(
                                    "SOURCE_RECOVERY_PROMOTION_ABORTED",
                                    mapOf(
                                        "sourceKind" to label,
                                        "sourceToken" to diagnosticToken(sourceId, "source"),
                                        "trigger" to "PLAYBACK_READ_FAILURE",
                                        "configRevision" to configRevision,
                                        "stage" to "BACKOFF",
                                        "outcome" to "SUPERSEDED",
                                    ),
                                    operation.context(),
                                )
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            // A successful probe is not a successful recovery until the
                            // source has also re-indexed. Keep it demoted and retry promptly.
                            runtime.coordinator.markPlaybackUnavailable()
                            primaryPoolIds.remove(sourceId)
                            unavailablePoolIds.add(sourceId)
                            nextWaitMs = RecoveryPolicy.waitMs(healthy = false, attempt = 0)
                            diagnostics.logEvent(
                                "SOURCE_RECOVERY_PROMOTION_ABORTED",
                                mapOf(
                                    "sourceKind" to label,
                                    "sourceToken" to diagnosticToken(sourceId, "source"),
                                    "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                    "configRevision" to configRevision,
                                    "stage" to "BACKOFF",
                                    "outcome" to "FAILED",
                                    "reason" to "RECOVERY_INDEX_FAILED",
                                    "errorClass" to error.javaClass.simpleName.ifBlank { "UNKNOWN" },
                                    "errorCode" to "RECOVERY_INDEX_FAILED",
                                    "waitMs" to nextWaitMs.toString(),
                                ),
                                operation.context(),
                            )
                        } finally {
                            diagnostics.operations.finish(operation.operationId)
                            recoveryOperation = null
                        }
                    }
                    RecoveryPolicy.Action.DEMOTE -> {
                        val operation = recoveryOperation ?: diagnostics.operations.start(
                            "SOURCE_RECOVERY", DiagnosticOrigin.RECOVERY,
                        ).also { started ->
                            diagnostics.logEvent(
                                "SOURCE_RECOVERY_STARTED",
                                mapOf(
                                    "sourceKind" to label,
                                    "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                    "configRevision" to configRevision,
                                    "stage" to "BACKOFF",
                                ),
                                started.context(),
                            )
                        }
                        recoveryOperation = operation
                        val demoted = SourcePoolPolicy.afterDemote(primaryPoolIds, sourceId)
                        primaryPoolIds.clear(); primaryPoolIds.addAll(demoted)
                        unavailablePoolIds.add(sourceId)
                        exhaustedUnavailablePoolIds.remove(sourceId)
                        reconfigurePool()
                        diagnostics.logEvent(
                            "SOURCE_UNAVAILABLE",
                            mapOf(
                                "sourceKind" to label,
                                "sourceToken" to diagnosticToken(sourceId, "source"),
                                "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                "configRevision" to configRevision,
                            ),
                            operation.context(),
                        )
                        diagnostics.logEvent(
                            "SOURCE_BACKOFF",
                            mapOf(
                                "sourceKind" to label,
                                "sourceToken" to diagnosticToken(sourceId, "source"),
                                "waitMs" to step.waitMs.toString(),
                                "attempt" to step.state.attempt.toString(),
                                "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                "configRevision" to configRevision,
                            ),
                            operation.context(),
                        )
                    }
                    RecoveryPolicy.Action.NONE -> {
                        if (!healthy && step.state.attempt >= RecoveryPolicy.BACKOFF_SECONDS.lastIndex &&
                            exhaustedUnavailablePoolIds.add(sourceId)
                        ) {
                            reconfigurePool()
                            val operation = recoveryOperation
                            diagnostics.logEvent(
                                "SOURCE_BACKOFF_EXHAUSTED",
                                mapOf(
                                    "sourceKind" to label,
                                    "sourceToken" to diagnosticToken(sourceId, "source"),
                                    "attempt" to step.state.attempt.toString(),
                                    "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                                    "configRevision" to configRevision,
                                ),
                                operation?.context() ?: DiagnosticContext(origin = DiagnosticOrigin.RECOVERY),
                            )
                        }
                    }
                }
                // A playback read failure wakes a healthy ten-minute monitor immediately;
                // otherwise the pure policy's bounded delay remains authoritative.
                withTimeoutOrNull(nextWaitMs) { runtime.wake.receive() }
              }
            } finally {
                recoveryOperation?.let { operation ->
                    diagnostics.logEvent(
                        "SOURCE_RECOVERY_CANCELLED",
                        mapOf(
                            "sourceKind" to label,
                            "trigger" to SourceRefreshTrigger.RECOVERY_PROMOTION.name,
                            "stage" to "TERMINAL",
                            "outcome" to "CANCELLED",
                            "cancellationReason" to "RECOVERY_JOB_CANCELLED",
                        ),
                        operation.context(),
                    )
                    diagnostics.operations.finish(operation.operationId)
                }
                if (recoveryRuntimes[sourceId] === runtime) recoveryRuntimes.remove(sourceId)
                runtime.wake.close()
            }
        }
        recoveryJobs.add(job)
    }

    private fun recover(messageRes: Int, reason: String, request: SourceApplyRequest) {
        diagnostics.logEvent(
            "SOURCE_RECOVERY_REQUIRED",
            sourceRequestFields(request, "RECOVERY_REQUIRED") + mapOf(
                "sourceKind" to "LOCAL_SAF",
                "reason" to reason,
                "outcome" to "UNAVAILABLE",
            ),
            request.operation.context(),
        )
        _state.update {
            it.copy(surface = Surface.Recovery(appContext.getString(messageRes)), indexingFound = null)
        }
    }

    private fun fallBackToFirstRun() {
        _state.update { it.copy(surface = Surface.FirstRun, indexingFound = null) }
    }

    // ---- Intents from the UI -------------------------------------------------

    fun requestPickFolder() { _pickFolderRequests.tryEmit(Unit) }

    // ---- configuration backup / restore (spec §7.0) ----

    fun requestExportConfig() = configTransferActions.requestExportConfig()

    fun requestImportConfig() = configTransferActions.requestImportConfig()

    /**
     * Export an encrypted bundle that DOES include the NAS password (spec §14.4).
     * The passphrase is held in memory only for the duration of the file operation and
     * is never persisted, logged, or placed in UI state.
     */
    fun requestExportEncrypted(passphrase: String) = configTransferActions.requestExportEncrypted(passphrase)

    fun requestImportEncrypted(passphrase: String) = configTransferActions.requestImportEncrypted(passphrase)

    fun requestExportSupportBundle() = configTransferActions.requestExportSupportBundle()

    /** Write the chosen document. [uri] is null when the user cancelled. */
    fun onExportTargetChosen(uri: Uri?, op: FileOp) = configTransferActions.onExportTargetChosen(uri, op)

    /** Read and apply a previously exported configuration (plain or encrypted). */
    fun onImportSourceChosen(uri: Uri?, op: FileOp = FileOp.IMPORT_CONFIG) =
        configTransferActions.onImportSourceChosen(uri, op)

    /** Called by the Activity after the SAF picker returns (uri == null on cancel). */
    fun onFolderPicked(uri: Uri?, displayName: String?) {
        if (uri == null) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_picker_cancelled)) }
            return
        }
        viewModelScope.launch {
            updateSettingsAndRefresh(SourceRefreshTrigger.FIRST_RUN_CONFIGURATION) { current ->
                // copy(), not a fresh ActiveSource: the SMB/Synology/WebDAV blocks and
                // alsoPlay must survive choosing a local folder, or merging a NAS back in
                // would silently need reconfiguring from scratch.
                current.copy(
                    source = current.source.copy(
                        kind = ActiveSourceKind.LOCAL_SAF,
                        treeUri = uri.toString(),
                        displayName = displayName ?: "",
                    )
                )
            }
        }
    }

    fun useSamples() {
        viewModelScope.launch {
            updateSettingsAndRefresh(SourceRefreshTrigger.FIRST_RUN_CONFIGURATION) {
                it.copy(source = it.source.copy(kind = ActiveSourceKind.SAMPLES, displayName = ""))
            }
        }
    }

    /** Resolve a Coil model for a photo; see [PlaybackPhotoAccess.resolveModel]. */
    internal suspend fun resolveModel(
        display: DisplayPhoto,
        request: ModelResolutionRequest = ModelResolutionRequest(
            ModelResolutionPriority.BACKGROUND_PRELOAD,
        ),
        onPreparationSubstage: (String) -> Unit = {},
        onPreparationTransferUpdate: (PreparationTransferUpdate) -> Unit = {},
    ): PhotoModelResolution =
        photoAccess.resolveModel(display, request, onPreparationSubstage, onPreparationTransferUpdate)

    /** Header-only dimension probe for a collage candidate; see [PlaybackPhotoAccess]. */
    suspend fun probeRemoteCollageDimensions(display: DisplayPhoto): Pair<Int, Int>? =
        photoAccess.probeRemoteCollageDimensions(display)

    /** The bounded collage optimizer pool for [anchor], in deterministic order. */
    suspend fun portraitCollageCandidates(anchor: DisplayPhoto, limit: Int = 12): List<DisplayPhoto> =
        photoAccess.portraitCollageCandidates(anchor, limit)

    /** Save an SMB source (password encrypted via SecretStore) and make it active. */
    fun saveSmb(host: String, share: String, path: String, user: String, domain: String, password: String) =
        sourceSettingsActions.saveSmb(host, share, path, user, domain, password)

    /** Test an SMB connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testSmb(host: String, share: String, path: String, user: String, domain: String, password: String) =
        sourceSettingsActions.testSmb(host, share, path, user, domain, password)

    fun clearSmbTestResult() = sourceSettingsActions.clearSmbTestResult()

    // ---- Synology File Station (ROADMAP.md network photo-app sources) ----

    /**
     * Save and activate a Synology source. [otpCode] is the optional 2FA one-time code;
     * it is kept in memory for the immediately following login only and never persisted.
     */
    fun saveSynology(
        baseUrl: String,
        folderPath: String,
        user: String,
        password: String,
        otpCode: String,
        useThumbnails: Boolean,
        pinnedCertSha256: String? = null,
    ) = sourceSettingsActions.saveSynology(
        baseUrl, folderPath, user, password, otpCode, useThumbnails, pinnedCertSha256,
    )

    /** Test a Synology connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testSynology(baseUrl: String, folderPath: String, user: String, password: String, otpCode: String) =
        sourceSettingsActions.testSynology(baseUrl, folderPath, user, password, otpCode)

    /** Populates [SlideshowUiState.synologyCertFingerprint]; null clears any previous offer. */
    fun probeSynologyCertificate(baseUrl: String) = sourceSettingsActions.probeSynologyCertificate(baseUrl)

    /** Records a certificate fingerprint the user has explicitly approved. */
    fun trustSynologyCertificate(fingerprint: String) = sourceSettingsActions.trustSynologyCertificate(fingerprint)

    /** Removes a previously approved certificate, returning to strict platform validation. */
    fun clearSynologyCertificate() = sourceSettingsActions.clearSynologyCertificate()

    // ---- WebDAV / Nextcloud (ROADMAP.md other candidate sources) ----------------

    /** Save and activate a WebDAV source; an empty [password] keeps the stored secret. */
    fun saveWebDav(
        baseUrl: String,
        rootPath: String,
        folderPath: String,
        user: String,
        password: String,
        pinnedCertSha256: String? = null,
    ) = sourceSettingsActions.saveWebDav(
        baseUrl, rootPath, folderPath, user, password, pinnedCertSha256,
    )

    /** Test a WebDAV connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testWebDav(baseUrl: String, rootPath: String, folderPath: String, user: String, password: String) =
        sourceSettingsActions.testWebDav(baseUrl, rootPath, folderPath, user, password)

    /** Force a re-scan of the current source without changing it. */
    fun rebuildIndex() {
        viewModelScope.launch {
            _state.update {
                it.copy(transientNotice = appContext.getString(R.string.msg_rebuild_started))
            }
            requestSourceRefresh(
                SourceRefreshTrigger.REBUILD_ANDROID_UI,
                DiagnosticOrigin.ANDROID_UI,
            )
        }
    }

    // ---- merged primary pool (spec §9.3) ---------------------------------------

    /** Add or remove a co-primary source, so several sources can feed one slideshow. */
    fun setAlsoPlay(kind: ActiveSourceKind, enabled: Boolean) = sourceSettingsActions.setAlsoPlay(kind, enabled)

    private fun isConfigured(kind: ActiveSourceKind, source: ActiveSource): Boolean =
        SourceStatusPolicy.isConfigured(kind, source)

    /** Promote an already-configured source to primary without retyping its connection. */
    fun setPrimarySource(kind: ActiveSourceKind) = sourceSettingsActions.setPrimarySource(kind)

    /** One bounded COUNT per built-in source; runs on the health cadence, never per frame. */
    private suspend fun refreshIndexedPhotoCounts() {
        val ids = SourceStatusPolicy.orderedKinds.mapNotNull(::sourceIdFor) +
            ServiceLocator.SOURCE_FALLBACK
        indexedPhotosBySource = ids.distinct().associateWith { id ->
            runCatching { services.photoDao.countForSource(id) }.getOrDefault(0)
        }
        publishSourceStatuses()
    }

    private fun buildSourceStatuses(source: ActiveSource, stalePlayback: Boolean) =
        SourceStatusPolicy.statuses(
            source = source,
            unavailableSourceIds = unavailablePoolIds.toSet(),
            stalePlayback = stalePlayback,
            indexedPhotos = indexedPhotosBySource,
            sourceIdFor = ::sourceIdFor,
            fallbackSourceId = ServiceLocator.SOURCE_FALLBACK,
        )

    /**
     * Republish the source indicator after pool membership changes outside the settings
     * collector — a health-loop demotion, a recovery promotion, or a fresh activation.
     */
    private fun publishSourceStatuses() {
        _state.update {
            it.copy(sourceStatuses = buildSourceStatuses(currentSourceConfig, it.stalePlayback))
        }
    }

    // ---- automatic index refresh (spec §20) -------------------------------------

    fun setAutoRescanEnabled(enabled: Boolean) = autoRescan.setAutoRescanEnabled(enabled)
    fun setAutoRescanAt(text: String) = autoRescan.setAutoRescanAt(text)
    fun setAutoRescanDay(isoDay: Int, selected: Boolean) =
        autoRescan.setAutoRescanDay(isoDay, selected)
    fun setAutoRescanDays(days: Set<Int>) = autoRescan.setAutoRescanDays(days)

    fun setIntervalSeconds(seconds: Int) {
        viewModelScope.launch { services.settings.update { it.copy(intervalSeconds = PlaybackInterval.clamp(seconds)) } }
    }

    // ---- playback selection + curation (spec §9.4, §9.6) -----------------------

    /**
     * Update scan filters. A filter change alters what *should* be indexed, so the
     * source is re-applied (rescan + reconcile) rather than waiting for the next
     * incidental rescan — otherwise the user changes a filter and nothing happens.
     */
    fun setFilters(filters: FilterSettings) {
        viewModelScope.launch {
            updateSettingsAndRefresh(SourceRefreshTrigger.FILTERS_CHANGED) {
                it.copy(filters = filters)
            }
        }
    }

    fun setSelectionMode(mode: SelectionMode) {
        viewModelScope.launch { services.settings.update { it.copy(selectionMode = mode) } }
    }

    fun resetActiveShuffleProgress() {
        engine.resetActiveShuffle(clearHistory = false)
    }

    fun resetAllShuffleProgress(clearHistory: Boolean) {
        engine.resetAllShuffle(clearHistory)
    }

    fun setOnUnreachable(policy: UnreachablePolicy) {
        viewModelScope.launch { services.settings.update { it.copy(onUnreachable = policy) } }
    }

    // ---- folder ("album") selection (spec §9.4) --------------------------------

    suspend fun availableFolders() = photoLibraryActions.availableFolders()
    fun setSelectedFolders(folderKeys: Set<String>) =
        photoLibraryActions.setSelectedFolders(folderKeys)
    fun previewFolderOnce(folder: FolderSummary) = photoLibraryActions.previewFolderOnce(folder)
    fun useFolderInActivePlaylist(folder: FolderSummary) =
        photoLibraryActions.useFolderInActivePlaylist(folder)
    fun clearFolderSelection() = photoLibraryActions.clearFolderSelection()
    fun setFavoritesOnly(enabled: Boolean) = photoLibraryActions.setFavoritesOnly(enabled)
    fun toggleFavorite() = photoLibraryActions.toggleFavorite()
    fun setInteractionHold(held: Boolean) = photoLibraryActions.setInteractionHold(held)
    fun setFavoriteForPhotos(ids: List<Long>, favorite: Boolean) =
        photoLibraryActions.setFavoriteForPhotos(ids, favorite)
    fun hidePhotos(ids: List<Long>) = photoLibraryActions.hidePhotos(ids)
    fun undoLastHide() = photoLibraryActions.undoLastHide()
    fun clearHideUndo() = photoLibraryActions.clearHideUndo()
    fun hideCurrentPhoto() = photoLibraryActions.hideCurrentPhoto()
    fun unhideAllPhotos() = photoLibraryActions.unhideAllPhotos()

    fun setSurfaceObscured(obscured: Boolean) =
        engine.setSurfaceObscured(engineOwnerToken, obscured)

    fun onVisiblePresentationChanged(photos: List<com.example.familyphotoframe.domain.engine.DisplayPhoto>) {
        _state.update { current ->
            if (current.visiblePresentationPhotos.map { it.id } == photos.map { it.id }) current
            else current.copy(visiblePresentationPhotos = photos)
        }
        presentationReporter.publishDiagnosticPlayback(
            _state.value,
            layoutOverride = when (photos.size) {
                0 -> "NONE"
                1 -> "SINGLE"
                else -> "COLLAGE_${photos.size.coerceAtMost(3)}"
            },
        )
    }

    /** Source ids currently feeding playback, used for curation counts. */
    private fun currentPlaybackSourceIds(): List<String> {
        val source = lastSettings?.source ?: return listOf(ServiceLocator.SOURCE_FALLBACK)
        // Must include co-primaries: with a merged pool, counting favourites or listing
        // folders for only the chosen kind would silently ignore half the library.
        val ids = ((listOf(source.kind) + source.alsoPlay).distinct().mapNotNull { sourceIdFor(it) } +
            ServiceLocator.SOURCE_LOCAL_UPLOADS).distinct()
        return ids.ifEmpty { listOf(ServiceLocator.SOURCE_FALLBACK) }
    }

    // ---- playlists and local-time switching ---------------------------------------

    fun playPlaylist(id: String, overrideMinutes: Int? = null) =
        playlistActions.playPlaylist(id, overrideMinutes)
    fun createPlaylist(name: String, favoritesOnly: Boolean = false, localUploadsOnly: Boolean = false) =
        playlistActions.createPlaylist(name, favoritesOnly, localUploadsOnly)
    fun deletePlaylist(id: String) = playlistActions.deletePlaylist(id)
    fun setPlaylistScheduleEnabled(enabled: Boolean) =
        playlistActions.setPlaylistScheduleEnabled(enabled)
    fun addPlaylistScheduleRule(
        name: String,
        playlistId: String,
        startTime: String,
        endTime: String,
        days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
        priority: Int = 0,
    ) = playlistActions.addPlaylistScheduleRule(name, playlistId, startTime, endTime, days, priority)
    fun deletePlaylistScheduleRule(id: String) = playlistActions.deletePlaylistScheduleRule(id)
    fun cancelPlaylistOverride() = playlistActions.cancelPlaylistOverride()

    private fun playlistFilteredIds(ids: List<String>): List<String> {
        if (activePlaylistSourceFilter.isEmpty()) return ids.distinct()
        return ids.filter { it in activePlaylistSourceFilter }.distinct()
    }

    fun setAspectMode(mode: AspectMode) {
        viewModelScope.launch { services.settings.update { it.copy(aspectMode = mode) } }
    }

    fun setTransitionSelectionMode(mode: TransitionSelectionMode) {
        viewModelScope.launch { services.settings.update { it.copy(transitionSelectionMode = mode) } }
    }

    fun setTransition(mode: TransitionMode) {
        viewModelScope.launch { services.settings.update { it.copy(transition = mode) } }
    }

    fun setTransitionDurationMs(durationMs: Int) {
        viewModelScope.launch {
            services.settings.update { it.copy(transitionDurationMs = durationMs.coerceIn(300, 2_000)) }
        }
    }

    fun setTransitionReduceMotion(enabled: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(transitionReduceMotion = enabled) } }
    }

    fun setMotion(mode: MotionMode) {
        viewModelScope.launch { services.settings.update { it.copy(motion = mode) } }
    }

    fun setPortraitCollageMode(mode: PortraitCollageMode) {
        viewModelScope.launch {
            services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(mode = mode)) }
        }
    }

    fun setPortraitCollageMaxPhotos(maxPhotos: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(portraitCollage = it.portraitCollage.copy(maxPhotos = maxPhotos.coerceIn(2, 3)))
            }
        }
    }

    /** Task §12: enable or disable subtle motion on three-portrait-photo frames. */
    fun setAnimateThreePhotoFrames(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(portraitCollage = it.portraitCollage.copy(animateThreePhotoFrames = enabled))
            }
        }
    }

    /**
     * Task §16: record how a three-photo frame was animated.
     *
     * Debug builds only. The entry is emitted once per slide, never per animation frame,
     * but one entry per collage slide would still crowd out the scan and source history
     * that the diagnostics ring buffer exists to preserve on a real frame.
     */
    fun logThreePhotoMotion(photoIds: List<Long>, detail: String) {
        if (!BuildConfig.DEBUG) return
        services.diagnostics.log(
            DiagnosticsLog.Category.ENGINE,
            "PANEL_MOTION",
            "motionMode" to detail.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(40),
            "presentationToken" to diagnosticToken(photoIds.joinToString(","), "presentation"),
            "members" to photoIds.size.toString(),
        )
    }

    fun setPortraitFallback(fallback: PortraitFallback) {
        viewModelScope.launch {
            services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(fallback = fallback)) }
        }
    }

    fun setCollageGap(gap: CollageGap) {
        viewModelScope.launch {
            services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(gap = gap)) }
        }
    }

    fun setCollageOrientationFilter(filter: CollageOrientationFilter) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(orientationFilter = filter)) } }
    fun setCollageFillWithOtherOrientations(enabled: Boolean) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(fillWithOtherOrientations = enabled)) } }
    fun setCollageLayoutPreference(preference: CollageLayoutPreference) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(layoutPreference = preference)) } }
    fun setCollageScaleMode(mode: CollageScaleMode) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(scaleMode = mode)) } }
    fun setCollageAlignment(alignment: CollageAlignment) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(alignment = alignment)) } }
    fun setCollageBackground(background: CollageBackground) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(background = background)) } }
    fun setCollageCornerRadiusDp(radiusDp: Int) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(cornerRadiusDp = radiusDp.coerceIn(0, 32))) } }
    fun resetPortraitCollageSettings() = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = PortraitCollageSettings()) } }

    // ---- display-time EXIF backfill (Phase 2 increment 8) ----

    /** Id of the photo the last backfill was launched for, to avoid duplicate work. */
    @Volatile private var lastExifPhotoId: Long? = null
    private var exifJob: Job? = null
    @Volatile private var lastHashPhotoId: Long? = null
    private var contentHashJob: Job? = null
    private val sourceConsumerJobsLock = Any()
    /** Includes cancelled jobs until they have actually finished unwinding. */
    private val sourceConsumerJobs = mutableSetOf<Job>()

    /**
     * When the displayed photo changes, clear any stale backfilled EXIF and, if this
     * photo was indexed without EXIF, read it now. Also warms the preloaded [next] photo
     * so it is usually already stamped by the time it reaches the screen.
     *
     * Failures are silent by design: a photo simply shows no date/caption/location
     * overlay, exactly as if it had no EXIF.
     */
    private fun onDisplayedPhotoChanged(model: EngineUiModel) {
        val current = model.current ?: return
        if (current.id == lastExifPhotoId) return
        lastExifPhotoId = current.id

        // The previous photo's backfilled EXIF must not leak onto this one.
        _state.update { it.copy(currentPhotoExif = null) }

        contentHashJob?.cancel()
        if (lastHashPhotoId != current.id) {
            lastHashPhotoId = current.id
            contentHashJob = launchSourceConsumer {
                val hash = services.contentHashBackfiller.backfill(current.id, ::resolveSourceById)
                if (hash != null) engine.reconcileShuffle()
            }
        }

        exifJob?.cancel()
        exifJob = launchSourceConsumer {
            val exif = services.exifBackfiller.backfill(current.id, ::resolveSourceById)
            // Only publish if this photo is still the one on screen — the slideshow may
            // have advanced while the read was in flight.
            if (exif != null && engine.ui.value.current?.id == current.id) {
                _state.update { it.copy(currentPhotoExif = exif) }
            }
            // Warm the next photo so its overlay is ready when it appears.
            model.next?.let { services.exifBackfiller.backfill(it.id, ::resolveSourceById) }
        }
    }

    /** Tracks superseded per-slide jobs until completion, not just until replacement. */
    private fun launchSourceConsumer(block: suspend () -> Unit): Job {
        val job = viewModelScope.launch { block() }
        synchronized(sourceConsumerJobsLock) { sourceConsumerJobs += job }
        job.invokeOnCompletion {
            synchronized(sourceConsumerJobsLock) { sourceConsumerJobs -= job }
        }
        return job
    }

    /**
     * Returns the [PhotoSource] that owns a photo, reusing an existing session.
     *
     * This runs per displayed photo (EXIF and content-hash backfill), so it must not build
     * a source each time: [SmbPhotoSource] owns a `CIFSContext` with its own transport pool
     * and buffer cache, and one per photo exhausted the API 22 heap in about fourteen hours.
     * Prefer the active playback source, then a previously built backfill source, and only
     * construct — and retain — as a last resort.
     */
    private suspend fun resolveSourceById(sourceId: SourceId): PhotoSource? = try {
        remoteSources.resolved(sourceId.value)
            ?: buildSourceById(sourceId)?.let { built ->
                // Building suspends (settings read, secret reveal), so another backfill
                // or an activation may win before this resumes. The registry rechecks
                // atomically and closes this instance if it lost.
                remoteSources.retainBackfill(sourceId.value, built)
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Constructs a source from current settings. Callers cache; see [resolveSourceById]. */
    private suspend fun buildSourceById(sourceId: SourceId): PhotoSource? = try {
        when (sourceId.value) {
            ServiceLocator.SOURCE_FALLBACK -> services.fallbackSource
            ServiceLocator.SOURCE_LOCAL_SAF ->
                services.settings.settings.first().source.treeUri
                    ?.let { services.safSource(Uri.parse(it)) }
            ServiceLocator.SOURCE_SMB -> {
                val smb = services.settings.settings.first().source.smb
                if (smb == null) null else {
                    val password = services.secretStore.reveal(smb.credentialRef) ?: ""
                    services.smbSource(
                        SmbConnection(smb.host, smb.share, smb.path),
                        SmbCredentials(smb.domain, smb.user, password),
                    )
                }
            }
            ServiceLocator.SOURCE_SYNOLOGY -> {
                val syn = services.settings.settings.first().source.synology
                if (syn == null) null else {
                    val password = services.secretStore.reveal(syn.credentialRef) ?: ""
                    services.synologySource(
                        SynologyConnection(
                            baseUrl = syn.baseUrl,
                            folderPath = syn.folderPath,
                            useThumbnails = syn.useThumbnails,
                            thumbnailSize = syn.thumbnailSize,
                            pinnedCertSha256 = syn.pinnedCertSha256,
                        ),
                        SynologyCredentials(syn.user, password),
                    )
                }
            }
            else -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * Cancels the per-slide EXIF and content-hash jobs that can build a backfill source,
     * then waits for their registry updates to finish before the registry is cleared.
     * Scan flights and the per-source background hash jobs are drained separately by
     * [cancelObsoleteIndexWork]. Display/cache reads are not ViewModel-owned jobs;
     * [SmbPhotoSource] protects those with operation-scoped CIFS context leases.
     */
    private suspend fun cancelAndJoinSourceConsumingJobs() {
        val jobs = synchronized(sourceConsumerJobsLock) {
            sourceConsumerJobs.toList().also { sourceConsumerJobs.clear() }
        }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
        exifJob = null
        contentHashJob = null
    }

    /**
     * [onCleared] cannot suspend, so it can only request cancellation here, not wait
     * for it — best-effort, same as the rest of that teardown path.
     */
    private fun cancelSourceConsumingJobs() {
        val jobs = synchronized(sourceConsumerJobsLock) {
            sourceConsumerJobs.toList().also { sourceConsumerJobs.clear() }
        }
        jobs.forEach { it.cancel() }
        exifJob = null
        contentHashJob = null
    }

    /**
     * Drops every resolved source and releases the transport each one owns.
     *
     * Called wherever the source set is replaced or playback stops. Dropping registry
     * references alone is not enough: an unclosed `CIFSContext` keeps its buffers
     * reachable, so the heap never recovers. Registry release is best-effort and closes
     * every distinct source it owned.
     */
    private fun releaseResolvedSources() {
        remoteSources.releaseAll()
    }

    /**
     * Turns the §22.4 frame-timing readout on or off. Starting/stopping the collector is
     * driven from here rather than from Compose so the sampler's lifetime is tied to the
     * setting, not to whether a particular screen happens to be composed.
     */
    fun setShowPerformanceOverlay(show: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(showPerformanceOverlay = show) } }
        frameStatsJob?.cancel()
        if (show) {
            frameStats.start()
            frameStatsJob = viewModelScope.launch {
                // Refresh the text about twice a second: often enough to watch a
                // transition, rarely enough that the readout is not itself the load.
                while (isActive) {
                    _state.update { it.copy(performanceReadout = frameStats.formatted()) }
                    delay(500)
                }
            }
        } else {
            frameStats.stop()
            _state.update { it.copy(performanceReadout = null) }
        }
    }

    /** Writes the current frame-timing summary into diagnostics so it lands in a support bundle. */
    fun capturePerformanceSample() {
        val summary = frameStats.summary()
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PERF_SAMPLE",
            "frameCount" to summary.frameCount.toString(),
            "p50Ms" to (summary.p50IntervalNs / 1_000_000L).toString(),
            "p95Ms" to (summary.p95IntervalNs / 1_000_000L).toString(),
            "p99Ms" to (summary.p99IntervalNs / 1_000_000L).toString(),
            "maxMs" to (summary.worstIntervalNs / 1_000_000L).toString(),
            "slowFrames" to summary.jankFrames.toString(),
            "frozenFrames" to summary.severeJankFrames.toString(),
            "performanceClass" to when {
                summary.frameCount < FrameStats.MIN_SAMPLES -> "SAMPLING"
                summary.passesBudget -> "PASS"
                else -> "FAIL"
            },
        )
    }

    fun setShowClock(show: Boolean) = updateOverlays { it.copy(clockShow = show) }
    fun setShowDate(show: Boolean) = updateOverlays { it.copy(dateShow = show) }
    fun setShowFolder(show: Boolean) = updateOverlays { it.copy(folderShow = show) }
    fun setClock24h(value: Boolean) = updateOverlays { it.copy(clock24h = value) }

    // ---- photo date / caption / location overlays (Phase 2 increment 5, spec §11) ----
    fun setShowPhotoDate(show: Boolean) = updateOverlays { it.copy(photoDateShow = show) }
    fun setShowCaption(show: Boolean) = updateOverlays { it.copy(captionShow = show) }
    fun setShowLocation(show: Boolean) = updateOverlays { it.copy(locationShow = show) }

    // ---- overlay 9-grid positions (Phase 2 increment 6: position pickers) ----
    fun setClockPosition(p: OverlayPosition) = updateOverlays { it.copy(clockPosition = p) }
    fun setDatePosition(p: OverlayPosition) = updateOverlays { it.copy(datePosition = p) }
    fun setFolderPosition(p: OverlayPosition) = updateOverlays { it.copy(folderPosition = p) }
    fun setWeatherPosition(p: OverlayPosition) = updateOverlays { it.copy(weatherPosition = p) }
    fun setPhotoDatePosition(p: OverlayPosition) = updateOverlays { it.copy(photoDatePosition = p) }
    fun setCaptionPosition(p: OverlayPosition) = updateOverlays { it.copy(captionPosition = p) }
    fun setLocationPosition(p: OverlayPosition) = updateOverlays { it.copy(locationPosition = p) }

    /** Text opacity shared by every overlay (Phase 2 increment 7). Floored above zero — an
     *  overlay at 0 looks broken/missing rather than intentionally hidden; use the toggle
     *  for that instead. */
    fun setOverlayOpacity(value: Float) = updateOverlays { it.copy(opacity = value.coerceIn(0.1f, 1f)) }

    // ---- weather overlay (spec §11) ----

    /** Whether a successfully fetched weather reading is drawn as an on-screen overlay. */
    fun setWeatherOverlayShow(value: Boolean) = updateOverlays { it.copy(weatherShow = value) }
    fun setWeatherEnabled(value: Boolean) = weatherActions.setWeatherEnabled(value)
    fun setWeatherLocation(latitudeText: String, longitudeText: String) =
        weatherActions.setWeatherLocation(latitudeText, longitudeText)
    fun setWeatherUnits(units: TemperatureUnits) = weatherActions.setWeatherUnits(units)
    fun setWeatherEndpoint(url: String) = weatherActions.setWeatherEndpoint(url)
    fun setWeatherApiKey(key: String) = weatherActions.setWeatherApiKey(key)

    // ---- screen brightness and night mode ----

    fun setNightBrightness(value: Float) = brightnessActions.setNightBrightness(value)
    fun setBrightnessMode(mode: BrightnessMode) = brightnessActions.setBrightnessMode(mode)
    fun previewBrightness(value: Float?) = brightnessActions.previewBrightness(value)
    fun setManualBrightness(value: Float) = brightnessActions.setManualBrightness(value)
    fun setBrightnessPeriod(index: Int, startTime: String, brightness: Float, action: NightAction) =
        brightnessActions.setBrightnessPeriod(index, startTime, brightness, action)
    fun temporaryWake() = brightnessActions.temporaryWake()
    fun onAmbientLight(lux: Float?, available: Boolean) =
        brightnessActions.onAmbientLight(lux, available)

    // ---- "On this day" memory interlude (docs/FPF-FEAT-ON-THIS-DAY-001.md) --------

    fun setOnThisDayEnabled(enabled: Boolean) = onThisDayActions.setOnThisDayEnabled(enabled)
    fun setOnThisDayTimesPerDay(timesPerDay: Int) =
        onThisDayActions.setOnThisDayTimesPerDay(timesPerDay)
    fun setOnThisDayDurationMinutes(minutes: Int) =
        onThisDayActions.setOnThisDayDurationMinutes(minutes)
    fun setOnThisDayMinYearsAgo(years: Int) = onThisDayActions.setOnThisDayMinYearsAgo(years)
    fun previewOnThisDay() = onThisDayActions.previewOnThisDay()

    fun setWebUploadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(webUpload = it.webUpload.copy(enabled = enabled)) }
        }
    }

    fun setWebUploadDuplicatePolicy(policy: com.example.familyphotoframe.data.settings.UploadDuplicatePolicy) {
        viewModelScope.launch {
            services.settings.update { it.copy(webUpload = it.webUpload.copy(duplicatePolicy = policy)) }
        }
    }

    fun setWebUploadAllowWhilePlaying(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(webUpload = it.webUpload.copy(allowWhilePlaying = enabled)) }
        }
    }

    fun setWebUploadMaxFileMiB(mebibytes: Int) {
        val bytes = mebibytes.coerceIn(1, 500).toLong() * 1024L * 1024L
        viewModelScope.launch {
            services.settings.update { it.copy(webUpload = it.webUpload.copy(maxFileBytes = bytes)) }
        }
    }

    private suspend fun refreshHealth() {
        refreshIndexedPhotoCounts()
        val total = runCatching { services.photoDao.count() }.getOrDefault(0)
        val heifFlag = if (services.allowHeifPlayback) 1 else 0
        val eligible = runCatching {
            services.photoDao.eligibleCount(currentMaxFailures(), heifFlag)
        }.getOrDefault(0)
        val hidden = runCatching { services.photoDao.hiddenCount() }.getOrDefault(0)
        val favorites = runCatching { services.photoDao.favoriteCountAll() }.getOrDefault(0)
        val failed = runCatching {
            services.photoDao.decodeFailedCount(currentMaxFailures())
        }.getOrDefault(0)
        val unsupported = runCatching {
            services.photoDao.unsupportedFormatCount(currentMaxFailures(), heifFlag)
        }.getOrDefault(0)
        val local = runCatching { services.photoDao.countForSource(ServiceLocator.SOURCE_LOCAL_UPLOADS) }.getOrDefault(0)
        val free = services.localUploadSource.directory.usableSpace.coerceAtLeast(0L)
        val shuffle = engine.ui.value.shuffleProgress
        val recommendations = buildList {
            if (eligible == 0) add("No playable photos are available — check the photo source or upload photos.")
            if (free < 250L * 1024L * 1024L) add("Storage is critically low — remove old local uploads.")
            else if (free < 1024L * 1024L * 1024L) add("Storage is almost full — review local uploads.")
            if (failed > 0) add("$failed photos could not be decoded — review diagnostics.")
            if (shuffle.unavailableSourceCount > 0) {
                add("${shuffle.unavailableSourceCount} photo source is unavailable. The slideshow is continuing with healthy sources.")
            }
            if (shuffle.foldersSkipped > 0) {
                add("${shuffle.foldersSkipped} folder turns were skipped in the current shuffle cycle.")
            }
            if (shuffle.quarantinedPhotos > 0) {
                add("${shuffle.quarantinedPhotos} photos are quarantined after repeated preparation failures.")
            }
            if (_state.value.stalePlayback) add("The network photo source is offline; cached photos are playing.")
        }
        val level = when {
            eligible == 0 || free < 250L * 1024L * 1024L -> "CRITICAL"
            recommendations.isNotEmpty() -> "WARNING"
            else -> "OK"
        }
        _state.update {
            it.copy(health = FrameHealthSummary(
                level = level,
                headline = when (level) {
                    "OK" -> "Everything is working normally"
                    "WARNING" -> "Attention recommended"
                    else -> "Action required"
                },
                totalPhotos = total,
                eligiblePhotos = eligible,
                hiddenPhotos = hidden,
                favoritePhotos = favorites,
                failedPhotos = failed,
                unsupportedPhotos = unsupported,
                localUploadPhotos = local,
                freeStorageBytes = free,
                recommendations = recommendations,
            ))
        }
    }

    fun setDecodeColorDepth(depth: DecodeColorDepth) {
        viewModelScope.launch { services.settings.update { it.copy(decodeColorDepth = depth) } }
    }

    fun setDecodeResolution(resolution: DecodeResolution) {
        viewModelScope.launch { services.settings.update { it.copy(decodeResolution = resolution) } }
    }

    fun setNativeMemoryHilMode(mode: com.example.familyphotoframe.data.settings.NativeMemoryHilMode) {
        viewModelScope.launch { services.settings.update { it.copy(nativeMemoryHilMode = mode) } }
    }

    fun setCachePlaybackPool(enabled: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(cachePlaybackPool = enabled) } }
    }

    fun setAutoStartOnBoot(value: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(autoStartOnBoot = value) } }
    }

    fun setShowOverLockScreen(value: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(showOverLockScreen = value) } }
    }

    fun setDoubleTapPauseTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            services.settings.update { it.copy(doubleTapPauseTimeoutMinutes = minutes) }
        }
    }

    fun setBatteryProtectionEnabled(value: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(batteryProtectionEnabled = value) } }
    }

    fun setStopLeakingGpsService(value: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(stopLeakingGpsService = value) } }
    }

    private fun updateOverlays(transform: (com.example.familyphotoframe.data.settings.OverlaySettings) -> com.example.familyphotoframe.data.settings.OverlaySettings) {
        viewModelScope.launch { services.settings.update { it.copy(overlays = transform(it.overlays)) } }
    }

    // ---- Web control server and remembered browsers (spec §15) ----

    fun setWebEnabled(value: Boolean) = webControlActions.setWebEnabled(value)
    fun refreshRememberedBrowsers() = webControlActions.refreshRememberedBrowsers()
    fun setRememberedBrowsersEnabled(enabled: Boolean) =
        webControlActions.setRememberedBrowsersEnabled(enabled)
    fun setRememberedAllowForever(allowed: Boolean) = webControlActions.setRememberedAllowForever(allowed)
    fun setRememberedDefaultExpiry(mode: com.example.familyphotoframe.data.settings.RememberExpiryMode) =
        webControlActions.setRememberedDefaultExpiry(mode)
    fun setRememberedMaximumBrowsers(count: Int) = webControlActions.setRememberedMaximumBrowsers(count)
    fun setRememberedMaximumExpiryDays(days: Int) = webControlActions.setRememberedMaximumExpiryDays(days)
    fun revokeRememberedBrowser(id: String) = webControlActions.revokeRememberedBrowser(id)
    fun revokeAllRememberedBrowsers() = webControlActions.revokeAllRememberedBrowsers()
    fun setWebPort(port: Int) = webControlActions.setWebPort(port)
    fun regenerateWebPin(keepRememberedBrowsers: Boolean = false) =
        webControlActions.regenerateWebPin(keepRememberedBrowsers)
    fun refreshWebInfo() = webControlActions.refreshWebInfo()

    // ---- Local thumbnail cache (docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md) ----

    fun setLocalThumbnailCacheEnabled(enabled: Boolean) =
        thumbnailCacheActions.setLocalThumbnailCacheEnabled(enabled)
    fun setLocalThumbnailCacheMaxGiB(gib: Int) =
        thumbnailCacheActions.setLocalThumbnailCacheMaxGiB(gib)
    fun refreshLocalThumbnailCacheInfo() = thumbnailCacheActions.refreshLocalThumbnailCacheInfo()
    fun cleanLocalThumbnailCache() = thumbnailCacheActions.cleanLocalThumbnailCache()
    fun rebuildLocalThumbnailCache() = thumbnailCacheActions.rebuildLocalThumbnailCache()

    // ---- D-pad / transport ---------------------------------------------------

    fun onNext() = engine.next()
    fun onPrevious() = engine.previous()
    fun onTogglePause(source: String = "unknown") = engine.togglePause(source)
    fun onShowInfo() = _state.update { it.copy(showInfo = true) }
    fun onHideInfo() = _state.update { it.copy(showInfo = false) }
    fun onDecodeFailure(failure: DecodeFailure) {
        if (failure.exceptionClass == "OutOfMemoryError") {
            // Heap exhaustion is a process condition, not evidence that this photo is
            // defective. Keep the current frame and reservation intact; the memory guard
            // retries the same selected photo only after cooldown and a low-heap sample.
            presentationReporter.recoverFromDecodeOom(failure)
            return
        }
        if (failure.sourceLevelFailure) {
            viewModelScope.launch {
                markPlaybackSourceUnavailable(failure.sourceId)
                engine.reportDecodeFailure(failure)
            }
        } else {
            engine.reportDecodeFailure(failure)
        }
    }

    /**
     * Releases rows suppressed by failures old enough to be stale, on every source apply.
     *
     * A frame that loses its NAS overnight comes back to photos still marked as failures,
     * and nothing else clears them automatically: a rescan preserves the count for
     * unchanged files, and the recovery promotion that calls `clearSuppression` only runs
     * if the demote and the promote happen in the same process. A restart in between —
     * which is exactly what a frame does — skips it, so the share returns healthy and the
     * frame still shows nothing.
     */
    private suspend fun expireStaleDecodeSuppression() {
        val cutoff = System.currentTimeMillis() - DECODE_SUPPRESSION_TTL_MS
        val released = cancellableOrDefault(0) {
            services.photoDao.expireDecodeSuppression(
                olderThanEpochMs = cutoff,
                permanentCount = PERMANENT_DECODE_FAILURE_COUNT,
            )
        }
        if (released > 0) {
            diagnostics.logEvent(
                "DECODE_SUPPRESSION_EXPIRED",
                mapOf("count" to released.toString(), "durationMs" to DECODE_SUPPRESSION_TTL_MS.toString()),
                DiagnosticContext(origin = DiagnosticOrigin.APP),
            )
        }
    }

    private suspend fun markPlaybackSourceUnavailable(sourceId: String) {
        if (!remoteSources.containsActive(sourceId)) return
        val newlyUnavailable = unavailablePoolIds.add(sourceId)
        recoveryRuntimes[sourceId]?.let { runtime ->
            runtime.coordinator.markPlaybackUnavailable()
            runtime.wake.trySend(Unit)
        }
        if (!newlyUnavailable) return
        val operation = diagnostics.operations.start("SOURCE_RECOVERY", DiagnosticOrigin.APP)
        val sourceKind = diagnosticSourceKind(sourceId)
        val configRevision = diagnosticToken(
            SourceRuntimeSignature.of(lastSettings ?: services.settings.settings.first()),
            "config",
        )
        val fields = mapOf(
            "sourceKind" to sourceKind,
            "sourceToken" to diagnosticToken(sourceId, "source"),
            "trigger" to "PLAYBACK_READ_FAILURE",
            "configRevision" to configRevision,
        )
        try {
            diagnostics.logEvent(
                "SOURCE_RECOVERY_STARTED",
                fields + mapOf("stage" to "DEMOTING"),
                operation.context(),
            )
            primaryPoolIds.remove(sourceId)
            exhaustedUnavailablePoolIds.remove(sourceId)
            reconfigurePool()
            diagnostics.logEvent(
                "SOURCE_UNAVAILABLE",
                fields + mapOf("outcome" to "DEMOTED", "reason" to "PLAYBACK_READ_FAILURE"),
                operation.context(),
            )
            diagnostics.logEvent(
                "SOURCE_BACKOFF",
                fields + mapOf(
                    "waitMs" to (RecoveryPolicy.BACKOFF_SECONDS.first() * 1_000L).toString(),
                    "attempt" to "0",
                    "stage" to "BACKOFF",
                ),
                operation.context(),
            )
        } finally {
            diagnostics.operations.finish(operation.operationId)
        }
    }

    // ---- renderer callbacks: presentation stages, memory, diagnostics ----

    fun onCollageCandidateFailure(failure: DecodeFailure) =
        presentationReporter.onCollageCandidateFailure(failure)
    fun onRecoverablePresentationOom(photo: DisplayPhoto, reason: String) =
        presentationReporter.onRecoverablePresentationOom(photo, reason)
    internal fun onBitmapInventory(
        inventory: PreparedBitmapInventory,
        protection: com.example.familyphotoframe.domain.engine.PlaybackMemoryState,
    ) = presentationReporter.onBitmapInventory(inventory, protection)
    fun onMemoryCleanup(pendingDisposals: Int, heapBeforeKb: Long, heapAfterKb: Long) =
        presentationReporter.onMemoryCleanup(pendingDisposals, heapBeforeKb, heapAfterKb)
    suspend fun onPrepared(anchorId: Long, memberIds: List<Long>, layout: String?) =
        presentationReporter.onPrepared(anchorId, memberIds, layout)
    fun onPresentationStage(anchorId: Long, stage: String, active: Boolean) =
        presentationReporter.onPresentationStage(anchorId, stage, active)
    internal fun onPreparationAttemptStage(
        anchorId: Long,
        selectionGeneration: Long,
        stage: String,
        active: Boolean,
    ) = presentationReporter.onPreparationAttemptStage(anchorId, selectionGeneration, stage, active)
    fun onPreparationSubstage(anchorId: Long, selectionGeneration: Long, substage: String) =
        presentationReporter.onPreparationSubstage(anchorId, selectionGeneration, substage)
    internal fun onPreparationTransferUpdate(
        anchorId: Long,
        selectionGeneration: Long,
        update: PreparationTransferUpdate,
    ) = presentationReporter.onPreparationTransferUpdate(anchorId, selectionGeneration, update)
    fun onPreparationCancelled(anchorId: Long, selectionGeneration: Long) =
        presentationReporter.onPreparationCancelled(anchorId, selectionGeneration)
    fun onPreparationWatchdogTimeout(anchorId: Long, selectionGeneration: Long) =
        presentationReporter.onPreparationWatchdogTimeout(anchorId, selectionGeneration)
    fun onRendered(
        anchorId: Long,
        memberIds: List<Long>,
        dataSources: List<String?>,
        layout: String?,
    ) = presentationReporter.onRendered(anchorId, memberIds, dataSources, layout)
    fun onWebPreviewReady(requestId: Long, frame: WebPreviewFrame) =
        presentationReporter.onWebPreviewReady(requestId, frame)
    fun onWebPreviewFailed(requestId: Long, reason: String) =
        presentationReporter.onWebPreviewFailed(requestId, reason)
    fun onTransitionEvent(event: TransitionEvent) = presentationReporter.onTransitionEvent(event)
    fun onCollageEvent(
        event: String,
        anchorId: Long,
        photoIds: List<Long> = emptyList(),
        layout: String? = null,
        prepareMs: Long? = null,
        decodedBytes: Long? = null,
        reason: String? = null,
        details: Map<String, String> = emptyMap(),
    ) = presentationReporter.onCollageEvent(event, anchorId, photoIds, layout, prepareMs, decodedBytes, reason, details)

    fun clearTransientNotice() = _state.update { it.copy(transientNotice = null) }

    private companion object {

        /**
         * How long a decode suppression survives before it is released and the photo is
         * given another chance.
         *
         * Twenty-four hours is chosen so an outage lasting a night heals by the next day
         * without a genuinely broken file being retried every few minutes: a file that is
         * really unreadable simply fails its way back to the threshold. Formats this device
         * cannot decode at all are marked permanent instead and are never expired.
         */
        const val DECODE_SUPPRESSION_TTL_MS = 24L * 60L * 60L * 1_000L
        /**
         * How many displayable rows must exist before playback starts from a still-running
         * scan. Small enough that a first-ever scan starts showing photos within seconds,
         * large enough that the shuffle has something to choose from rather than looping
         * one or two images.
         */
        const val EARLY_PLAYBACK_MIN_PHOTOS = 10

        /** Cap on a single wait so clock/timezone changes are noticed promptly. */
        const val MAX_SCHEDULE_WAIT_MINUTES = 15
        const val WEATHER_RECHECK_MS = 5 * 60_000L
        const val INITIAL_REMOTE_NETWORK_WAIT_MS = 60_000L
        const val INITIAL_REMOTE_NETWORK_POLL_MS = 500L
    }

    private var lastSettings: AppSettings? = null
    private fun currentIntervalSeconds(): Int {
        val settings = lastSettings ?: return 15
        return settings.playlists.activePlaylist().intervalSeconds ?: settings.intervalSecondsClamped
    }
    private fun currentMaxFailures(): Int = lastSettings?.temporarilySuppressAfterDecodeFailures ?: 3

    class Factory(
        private val services: ServiceLocator,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SlideshowViewModel(services, appContext) as T
    }
}
