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
    private val _hostActive = MutableStateFlow(false)
    val hostActive: StateFlow<Boolean> = _hostActive.asStateFlow()
    private val _hostGeneration = MutableStateFlow(0L)
    val hostGeneration: StateFlow<Long> = _hostGeneration.asStateFlow()
    private val hostLifecycleGate = HostLifecycleGate()
    private val webPinRegenerationInFlight = AtomicBoolean(false)

    fun onHostStarted() {
        _hostGeneration.value = hostLifecycleGate.start()
        _hostActive.value = true
    }

    fun onHostStopped() {
        // Invalidate the generation before publishing the inactive state. A decoder that
        // ignores cancellation can therefore never race onStop and commit a stale result.
        _hostGeneration.value = hostLifecycleGate.stop()
        _hostActive.value = false
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
    private var lastRequestedSourceSignature: String? = null
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
     * Remote sources currently active, keyed by source id, for `MediaCache` routing.
     *
     * A map rather than a single reference because the primary pool may now merge
     * several sources (e.g. a local folder plus a NAS): [resolveModel] has to hand
     * `MediaCache` the source that actually owns the photo being displayed.
     */
    private val activeRemoteSources = mutableMapOf<String, PhotoSource>()

    /**
     * Sources built for background backfill when the photo's source is not the active
     * playback source. Kept separate from [activeRemoteSources] because membership there
     * means "this source is serving playback" — [markPlaybackSourceUnavailable] keys off
     * it — while these exist only so EXIF/hash reads do not build a fresh session each
     * time. Both maps are released together by [releaseResolvedSources].
     */
    private val backfillSources = mutableMapOf<String, PhotoSource>()

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
    private var rescanScheduleJob: kotlinx.coroutines.Job? = null
    private var playlistScheduleJob: kotlinx.coroutines.Job? = null
    private var brightnessJob: kotlinx.coroutines.Job? = null
    private var healthJob: kotlinx.coroutines.Job? = null
    private var onThisDayJob: kotlinx.coroutines.Job? = null
    /** Starts playback from a partially built index; see [startEarlyPlaybackWatcher]. */
    private var earlyPlaybackJob: Job? = null
    @Volatile private var ambientLux: Float? = null
    @Volatile private var ambientSensorAvailable: Boolean = false
    private var activePlaylistSourceFilter: Set<String> = emptySet()

    /** Memory-only: held between the user typing it and the SAF callback completing. */
    @Volatile private var pendingPassphrase: String? = null

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
                publishDiagnosticPlayback(_state.value)
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
        engine.start(viewModelScope)
        healthJob = viewModelScope.launch {
            while (isActive) {
                refreshHealth()
                delay(60_000L)
            }
        }

        // Weather updates the overlay when a new snapshot lands, and is re-evaluated
        // periodically so a snapshot can age into "stale" and then out of view.
        viewModelScope.launch {
            services.weather.snapshot.collect { refreshWeatherText() }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(WEATHER_RECHECK_MS)
                refreshWeatherText()
            }
        }
    }

    override fun onCleared() {
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
        // Remote sessions outlive the ViewModel unless released here: on a configuration
        // change the replacement instance builds its own, and the old transports would
        // stay registered with nothing left to close them.
        releaseResolvedSources()
        services.webServer.controls = null
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

        override suspend fun testSavedSource(): String {
            val s = services.settings.settings.first()
            val configRevision = diagnosticToken(SourceRuntimeSignature.of(s), "config")
            return when (s.source.kind) {
                ActiveSourceKind.SMB -> {
                    val smb = s.source.smb ?: return appContext.getString(R.string.msg_smb_missing)
                    val password = services.secretStore.reveal(smb.credentialRef) ?: ""
                    val src = services.smbSource(
                        SmbConnection(smb.host, smb.share, smb.path),
                        SmbCredentials(smb.domain, smb.user, password),
                    )
                    // One-shot test: release the session instead of pooling it.
                    try {
                        appContext.getString(smbHealthMessageRes(sourceTestWithDiagnostics(
                            src, "SMB", 8_000, DiagnosticOrigin.WEB_UI, configRevision,
                        )))
                    } finally {
                        runCatching { src.close() }
                    }
                }
                ActiveSourceKind.SYNOLOGY -> {
                    val syn = s.source.synology ?: return appContext.getString(R.string.msg_syn_error)
                    val password = services.secretStore.reveal(syn.credentialRef) ?: ""
                    val src = services.synologySource(
                        SynologyConnection(
                            baseUrl = syn.baseUrl,
                            folderPath = syn.folderPath,
                            useThumbnails = syn.useThumbnails,
                            thumbnailSize = syn.thumbnailSize,
                            pinnedCertSha256 = syn.pinnedCertSha256,
                        ),
                        SynologyCredentials(syn.user, password),
                    )
                    appContext.getString(synologyHealthMessageRes(sourceTestWithDiagnostics(
                        src, "SYNOLOGY", 10_000, DiagnosticOrigin.WEB_UI, configRevision,
                    )))
                }
                ActiveSourceKind.LOCAL_SAF -> {
                    val uri = s.source.treeUri ?: return appContext.getString(R.string.msg_folder_missing)
                    val saf = services.safSource(Uri.parse(uri))
                    when (sourceTestWithDiagnostics(
                        saf, "LOCAL_SAF", 5_000, DiagnosticOrigin.WEB_UI, configRevision,
                    )) {
                        is SourceHealth.Ok -> appContext.getString(R.string.web_source_ok)
                        is SourceHealth.NeedsPermission -> appContext.getString(R.string.msg_permission_revoked)
                        is SourceHealth.Missing -> appContext.getString(R.string.msg_folder_missing)
                        else -> appContext.getString(R.string.msg_provider_error)
                    }
                }
                ActiveSourceKind.WEBDAV -> {
                    val dav = s.source.webdav ?: return appContext.getString(R.string.msg_dav_error)
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
                    appContext.getString(webDavHealthMessageRes(sourceTestWithDiagnostics(
                        src, "WEBDAV", 10_000, DiagnosticOrigin.WEB_UI, configRevision,
                    )))
                }
                ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES ->
                    appContext.getString(R.string.web_source_samples)
            }
        }

        override suspend fun previewFolderOnce(folderKey: String): String? =
            previewFolderOnceByKey(folderKey)

        override suspend fun useFolderInActivePlaylist(folderKey: String): String? =
            useFolderInActivePlaylistByKey(folderKey)

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
            this@SlideshowViewModel.triggerOnThisDay(preview = true)

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
                    services.webServer.controls = null
                    scheduleApplicationRestart()
                    null
                } catch (error: Exception) {
                    // The ViewModel has already been quiesced, and DataStore/cache work
                    // cannot share Room's transaction. Restart into the persisted state
                    // instead of attempting to resume a potentially partial runtime.
                    services.webServer.controls = null
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

        val obsoleteRecoveryJobs = recoveryJobs.toList()
        recoveryJobs.clear()
        obsoleteRecoveryJobs.forEach { it.cancel() }
        obsoleteRecoveryJobs.forEach { it.join() }
        recoveryRuntimes.values.forEach { it.wake.close() }
        recoveryRuntimes.clear()

        cancelObsoleteIndexWork("factory-reset")
        val backgroundJobs = listOfNotNull(
            rescanScheduleJob,
            playlistScheduleJob,
            brightnessJob,
            healthJob,
            exifJob,
            contentHashJob,
        )
        backgroundJobs.forEach { it.cancel() }
        backgroundJobs.forEach { it.join() }
        rescanScheduleJob = null
        playlistScheduleJob = null
        brightnessJob = null
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
        services.webServer.controls = webControls
    }

    private suspend fun onSettings(s: AppSettings) {
        if (playlistScheduleConfig(lastSettings?.playlists) != playlistScheduleConfig(s.playlists)) {
            restartPlaylistScheduleWatcher()
        }
        if (lastSettings?.brightnessAutomation != s.brightnessAutomation) {
            restartBrightnessWatcher(s.brightnessAutomation)
        }
        // Deliberately compares only the configuration fields. Including
        // lastAutoRescanAtEpochMs would make the watcher cancel itself the instant it
        // recorded a run — mid-scan.
        if (rescanConfigSig(lastSettings?.schedule) != rescanConfigSig(s.schedule)) {
            restartRescanWatcher(s.schedule)
        }
        if (onThisDayConfigSig(lastSettings?.onThisDay) != onThisDayConfigSig(s.onThisDay)) {
            restartOnThisDayWatcher(s.onThisDay)
        }
        if (lastSettings?.weather != s.weather) restartWeather(s.weather)
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
                memoryTier = services.memoryTier,
                autoStartOnBoot = s.autoStartOnBoot,
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
        publishDiagnosticPlayback(_state.value, transitionOverride = effectiveTransition.name)
        engine.setCachePlaybackPool(s.cachePlaybackPool)
        engine.setTiming(effectiveInterval, s.temporarilySuppressAfterDecodeFailures)
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
        val sourceChanged = lastRequestedSourceSignature?.let { it != request.signature } == true
        lastRequestedSourceSignature = request.signature
        if (sourceChanged) cancelObsoleteIndexWork(request.operation.operationId)

        // applySource and its helpers use the current settings for scan filters, playlist
        // source restrictions, failure limits, and fallback policy. Pin them to the same
        // repository snapshot that produced this request.
        lastSettings = request.settings
        val activeKinds = request.settings.source.let { listOf(it.kind) + it.alsoPlay }
        if (ActiveSourceKind.SYNOLOGY !in activeKinds) pendingSynologyOtp = null
        try {
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
     * A source/configuration switch must not sit behind an obsolete long NAS scan.
     * Same-signature rebuild requests retain the existing single-flight job and coalesce.
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
                    "cancellationReason" to "SOURCE_CONFIGURATION_CHANGED",
                    "completionState" to "SUPERSEDED",
                ),
                DiagnosticContext(
                    origin = DiagnosticOrigin.INTERNAL,
                    operationId = flight.operationId,
                    parentOperationId = flight.parentOperationId,
                ),
            )
            flight.deferred.cancel(CancellationException("SOURCE_CONFIGURATION_CHANGED"))
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
                // A backfill build for this same source may already be open (see
                // resolveSourceById). Promoting to active always builds fresh — current
                // settings win over whatever the backfill source was built with — so the
                // stale entry must be closed here, or it leaks as a second, orphaned,
                // never-closed session that nothing but the next full teardown reaches.
                backfillSources.remove(ServiceLocator.SOURCE_SMB)?.let { runCatching { it.close() } }
                val password = services.secretStore.reveal(smb.credentialRef) ?: ""
                val src = services.smbSource(
                    SmbConnection(smb.host, smb.share, smb.path),
                    SmbCredentials(smb.domain, smb.user, password),
                )
                activeRemoteSources[ServiceLocator.SOURCE_SMB] = src
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
                // See the matching comment in the SMB branch above.
                backfillSources.remove(ServiceLocator.SOURCE_SYNOLOGY)?.let { runCatching { it.close() } }
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
                activeRemoteSources[ServiceLocator.SOURCE_SYNOLOGY] = src
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
                // See the matching comment in the SMB branch above.
                backfillSources.remove(ServiceLocator.SOURCE_WEBDAV)?.let { runCatching { it.close() } }
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
                activeRemoteSources[ServiceLocator.SOURCE_WEBDAV] = src
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
            )
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

    private fun healthStateCode(health: SourceHealth): String = when (health) {
        is SourceHealth.Ok -> "OK"
        is SourceHealth.NeedsPermission -> "NEEDS_PERMISSION"
        is SourceHealth.Missing -> "MISSING"
        is SourceHealth.ProviderError -> "PROVIDER_ERROR"
        is SourceHealth.Unavailable -> "UNAVAILABLE"
    }

    private fun healthErrorCode(health: SourceHealth): String = when (health) {
        is SourceHealth.Ok -> "NONE"
        is SourceHealth.NeedsPermission -> "AUTH_OR_PERMISSION_REQUIRED"
        is SourceHealth.Missing -> "SOURCE_MISSING"
        is SourceHealth.Unavailable -> "HOST_UNAVAILABLE"
        is SourceHealth.ProviderError -> when (health.detail) {
            "two_factor_required" -> "TWO_FACTOR_REQUIRED"
            "auth_failed" -> "AUTH_FAILED"
            "CertUntrusted" -> "CERT_UNTRUSTED"
            "HostUnreachable" -> "HOST_UNAVAILABLE"
            "not_webdav" -> "NOT_WEBDAV"
            else -> "PROVIDER_ERROR"
        }
    }

    private suspend fun sourceTestWithDiagnostics(
        source: PhotoSource,
        sourceKind: String,
        timeoutMs: Long,
        origin: DiagnosticOrigin,
        configRevision: String,
    ): SourceHealth {
        val operation = diagnostics.operations.start("SOURCE_TEST", origin)
        val started = SystemClock.elapsedRealtime()
        val base = mapOf(
            "sourceKind" to sourceKind,
            "trigger" to "SOURCE_TEST",
            "configRevision" to configRevision,
            "timeoutMs" to timeoutMs.toString(),
            "stage" to "TESTING",
        )
        diagnostics.logEvent("SOURCE_TEST_STARTED", base, operation.context())
        try {
            val health = source.healthCheck(timeoutMs)
            diagnostics.logEvent(
                if (health is SourceHealth.Ok) "SOURCE_TEST_COMPLETED" else "SOURCE_TEST_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "healthState" to healthStateCode(health),
                    "outcome" to if (health is SourceHealth.Ok) "HEALTHY" else "UNAVAILABLE",
                    "errorCode" to healthErrorCode(health),
                ),
                operation.context(),
            )
            return health
        } catch (cancelled: CancellationException) {
            diagnostics.logEvent(
                "SOURCE_TEST_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "outcome" to "CANCELLED",
                    "errorCode" to "CANCELLED",
                    "cancellationReason" to "CALLER_CANCELLED",
                ),
                operation.context(),
            )
            throw cancelled
        } catch (error: Exception) {
            diagnostics.logEvent(
                "SOURCE_TEST_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "outcome" to "FAILED",
                    "errorClass" to error.javaClass.simpleName,
                    "errorCode" to "SOURCE_TEST_EXCEPTION",
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
                            cancelled.message == "SOURCE_CONFIGURATION_CHANGED"
                        ) "SOURCE_CONFIGURATION_CHANGED" else "CALLER_CANCELLED",
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

    private fun diagnosticSourceKind(source: PhotoSource): String = when (source.type) {
        com.example.familyphotoframe.data.source.SourceType.LOCAL_SAF_FOLDER -> "LOCAL_SAF"
        com.example.familyphotoframe.data.source.SourceType.APP_PRIVATE_BUILTIN -> "SAMPLES"
        com.example.familyphotoframe.data.source.SourceType.APP_PRIVATE_UPLOADS -> "LOCAL_UPLOADS"
        com.example.familyphotoframe.data.source.SourceType.SMB_SOURCE -> "SMB"
        com.example.familyphotoframe.data.source.SourceType.SYNOLOGY_FILE_STATION -> "SYNOLOGY"
        com.example.familyphotoframe.data.source.SourceType.WEBDAV -> "WEBDAV"
    }

    private fun diagnosticSourceKind(sourceId: String): String = when (sourceId) {
        ServiceLocator.SOURCE_LOCAL_SAF -> "LOCAL_SAF"
        ServiceLocator.SOURCE_LOCAL_UPLOADS -> "LOCAL_UPLOADS"
        ServiceLocator.SOURCE_SMB -> "SMB"
        ServiceLocator.SOURCE_SYNOLOGY -> "SYNOLOGY"
        ServiceLocator.SOURCE_WEBDAV -> "WEBDAV"
        ServiceLocator.SOURCE_FALLBACK -> "SAMPLES"
        else -> "UNKNOWN"
    }


    /**
     * Hashing is outside the scan and render critical paths. Repeated scans replace the
     * previous source job, while recovery starts a fresh bounded pass after connectivity
     * returns. The backfiller stops on a fully failed batch to avoid retry storms.
     */
    private fun scheduleContentHashBackfill(source: PhotoSource) {
        contentHashJobs.remove(source.id.value)?.cancel()
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

    fun requestExportConfig() {
        _fileRequests.tryEmit(
            FileRequest(FileOp.EXPORT_CONFIG, ConfigTransfer.suggestedFileName(dateStamp()))
        )
    }

    fun requestImportConfig() {
        _fileRequests.tryEmit(FileRequest(FileOp.IMPORT_CONFIG, ""))
    }

    /**
     * Export an encrypted bundle that DOES include the NAS password (spec §14.4).
     * The passphrase is held in memory only for the duration of the file operation and
     * is never persisted, logged, or placed in UI state.
     */
    fun requestExportEncrypted(passphrase: String) {
        if (passphrase.isBlank()) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_passphrase_required)) }
            return
        }
        pendingPassphrase = passphrase
        _fileRequests.tryEmit(
            FileRequest(FileOp.EXPORT_ENCRYPTED, PortableBundle.suggestedFileName(dateStamp()))
        )
    }

    fun requestImportEncrypted(passphrase: String) {
        if (passphrase.isBlank()) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_passphrase_required)) }
            return
        }
        pendingPassphrase = passphrase
        _fileRequests.tryEmit(FileRequest(FileOp.IMPORT_ENCRYPTED, ""))
    }

    fun requestExportSupportBundle() {
        _fileRequests.tryEmit(
            FileRequest(FileOp.EXPORT_SUPPORT, DiagnosticsDownloadNaming.fileName(System.currentTimeMillis()))
        )
    }

    /** Write the chosen document. [uri] is null when the user cancelled. */
    fun onExportTargetChosen(uri: Uri?, op: FileOp) {
        if (uri == null) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_picker_cancelled)) }
            return
        }
        viewModelScope.launch {
            if (op == FileOp.EXPORT_SUPPORT) {
                val current = services.settings.settings.first()
                val context = DiagnosticsBundleContext(
                    appVersion = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    buildType = BuildConfig.BUILD_TYPE,
                    sdkInt = Build.VERSION.SDK_INT,
                    deviceModel = Build.MODEL,
                    abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                    sourceKind = current.source.kind.name,
                    indexedCount = services.photoDao.count().toLong(),
                    runtime = services.diagnosticRuntimeState.snapshot(),
                )
                val ok = withContext(services.dispatchers.io) {
                    runCatching {
                        appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            diagnostics.openDurableBundle(context).use { input -> input.copyTo(output) }
                        } ?: error("no output stream")
                    }.isSuccess
                }
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    if (ok) "CONFIG_EXPORTED" else "CONFIG_EXPORT_FAILED",
                    "format" to "diagnostics-jsonl",
                )
                _state.update {
                    it.copy(transientNotice = appContext.getString(if (ok) R.string.msg_export_ok else R.string.msg_export_failed))
                }
                return@launch
            }
            val text = when (op) {
                FileOp.EXPORT_CONFIG -> ConfigTransfer.export(
                    settings = services.settings.settings.first(),
                    appVersion = BuildConfig.VERSION_NAME,
                    nowEpochMs = System.currentTimeMillis(),
                )
                FileOp.EXPORT_ENCRYPTED -> sealEncryptedBundle()
                else -> null
            }
            if (text == null) {
                pendingPassphrase = null
                _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_export_failed)) }
                return@launch
            }
            val ok = withContext(services.dispatchers.io) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("no output stream")
                }.isSuccess
            }
            pendingPassphrase = null
            val msg = if (ok) R.string.msg_export_ok else R.string.msg_export_failed
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                if (ok) "CONFIG_EXPORTED" else "CONFIG_EXPORT_FAILED",
                op.name,
            )
            _state.update { it.copy(transientNotice = appContext.getString(msg)) }
        }
    }

    /** Read and apply a previously exported configuration (plain or encrypted). */
    fun onImportSourceChosen(uri: Uri?, op: FileOp = FileOp.IMPORT_CONFIG) {
        if (uri == null) {
            pendingPassphrase = null
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_picker_cancelled)) }
            return
        }
        viewModelScope.launch {
            val readResult = withContext(services.dispatchers.io) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use {
                        BoundedTextInput.readUtf8(it)
                    } ?: throw IOException("no input stream")
                }
            }
            val text = readResult.getOrElse { error ->
                pendingPassphrase = null
                val oversized = error is ImportTooLargeException
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    if (oversized) "CONFIG_IMPORT_TOO_LARGE" else "CONFIG_IMPORT_READ_FAILED",
                    error.javaClass.simpleName,
                )
                _state.update {
                    it.copy(
                        transientNotice = appContext.getString(
                            if (oversized) R.string.msg_import_too_large else R.string.msg_import_read_failed,
                        )
                    )
                }
                return@launch
            }
            if (op == FileOp.IMPORT_ENCRYPTED) {
                applyEncryptedBundle(text)
                return@launch
            }
            when (val parsed = withContext(services.dispatchers.default) { ConfigTransfer.parse(text) }) {
                is ImportResult.Failed -> {
                    diagnostics.log(
                        DiagnosticsLog.Category.APP,
                        "CONFIG_IMPORT_REJECTED",
                        "reason" to parsed.reason.name,
                    )
                    _state.update {
                        it.copy(transientNotice = appContext.getString(importFailureRes(parsed.reason)))
                    }
                }
                is ImportResult.Ok -> {
                    var needsPassword = false
                    updateSettingsAndRefresh(SourceRefreshTrigger.CONFIG_IMPORTED) { current ->
                        val merged = ConfigTransfer.merge(current, parsed.bundle.settings)
                        needsPassword = ConfigTransfer.needsPasswordReentry(merged)
                        merged
                    }
                    diagnostics.log(DiagnosticsLog.Category.APP, "CONFIG_IMPORTED", "")
                    val msg = if (needsPassword) R.string.msg_import_ok_needs_password else R.string.msg_import_ok
                    _state.update { it.copy(transientNotice = appContext.getString(msg)) }
                }
            }
        }
    }

    /** Build the encrypted bundle, reading all portable secrets back out of the Keystore. */
    private suspend fun sealEncryptedBundle(): String? {
        val passphrase = pendingPassphrase ?: return null
        val settings = services.settings.settings.first()
        suspend fun reveal(ref: String?): String? =
            ref?.takeIf { it.isNotBlank() }?.let { services.secretStore.reveal(it) }
        val payload = PortableBundle.Payload(
            settings = settings,
            smbPassword = reveal(settings.source.smb?.credentialRef),
            synologyPassword = reveal(settings.source.synology?.credentialRef),
            webDavPassword = reveal(settings.source.webdav?.credentialRef),
            weatherApiKey = reveal(settings.weather.apiKeyRef),
        )
        return withContext(services.dispatchers.default) {
            PortableBundle.seal(
                payload = payload,
                passphrase = passphrase,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }

    /** Decrypt and apply a portable bundle, re-sealing its secrets under this device's Keystore. */
    private suspend fun applyEncryptedBundle(text: String) {
        val passphrase = pendingPassphrase
        pendingPassphrase = null
        if (passphrase == null) return

        when (val opened = withContext(services.dispatchers.default) {
            PortableBundle.open(text, passphrase)
        }) {
            is PortableBundle.OpenResult.Failed -> {
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    "BUNDLE_IMPORT_REJECTED",
                    "reason" to opened.reason.name,
                )
                _state.update {
                    it.copy(transientNotice = appContext.getString(bundleFailureRes(opened.reason)))
                }
            }
            is PortableBundle.OpenResult.Ok -> {
                val payload = opened.payload
                val imported = payload.settings
                val smb = imported.source.smb
                val syn = imported.source.synology
                val dav = imported.source.webdav

                val smbRef = if (smb != null && !payload.smbPassword.isNullOrEmpty()) {
                    CredentialPolicy.smbRef(smb).also {
                        services.secretStore.store(it, "smb_password", payload.smbPassword)
                    }
                } else ""
                val synRef = if (syn != null && !payload.synologyPassword.isNullOrEmpty()) {
                    CredentialPolicy.synologyRef(syn).also {
                        services.secretStore.store(it, "synology_password", payload.synologyPassword)
                    }
                } else ""
                val davRef = if (dav != null && !payload.webDavPassword.isNullOrEmpty()) {
                    CredentialPolicy.webDavRef(dav).also {
                        services.secretStore.store(it, "webdav_password", payload.webDavPassword)
                    }
                } else ""
                val weatherRef = if (!payload.weatherApiKey.isNullOrEmpty()) {
                    CredentialPolicy.WEATHER_API_KEY_REF.also {
                        services.secretStore.store(it, "weather_api_key", payload.weatherApiKey)
                    }
                } else ""

                updateSettingsAndRefresh(SourceRefreshTrigger.ENCRYPTED_BUNDLE_IMPORTED) {
                    imported.copy(
                        source = imported.source.copy(
                            smb = smb?.copy(credentialRef = smbRef),
                            synology = syn?.copy(credentialRef = synRef),
                            webdav = dav?.copy(credentialRef = davRef),
                        ),
                        weather = imported.weather.copy(apiKeyRef = weatherRef),
                    )
                }
                diagnostics.log(DiagnosticsLog.Category.APP, "BUNDLE_IMPORTED", "")
                _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_bundle_import_ok)) }
            }
        }
    }

    private fun bundleFailureRes(reason: PortableBundle.OpenResult.Reason): Int = when (reason) {
        PortableBundle.OpenResult.Reason.WRONG_PASSPHRASE_OR_CORRUPT -> R.string.msg_bundle_wrong_passphrase
        PortableBundle.OpenResult.Reason.TOO_NEW -> R.string.msg_import_too_new
        PortableBundle.OpenResult.Reason.EMPTY_PASSPHRASE -> R.string.msg_passphrase_required
        PortableBundle.OpenResult.Reason.TOO_LARGE -> R.string.msg_import_too_large
        PortableBundle.OpenResult.Reason.UNSAFE_PARAMETERS,
        PortableBundle.OpenResult.Reason.UNSUPPORTED_ALGORITHM -> R.string.msg_bundle_unsafe
        else -> R.string.msg_bundle_invalid
    }

    private fun importFailureRes(reason: ImportResult.Reason): Int = when (reason) {
        ImportResult.Reason.TOO_NEW -> R.string.msg_import_too_new
        ImportResult.Reason.TOO_LARGE -> R.string.msg_import_too_large
        ImportResult.Reason.EMPTY, ImportResult.Reason.NOT_JSON,
        ImportResult.Reason.NOT_A_FRAME_CONFIG -> R.string.msg_import_invalid
    }

    private fun dateStamp(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d%02d%02d".format(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

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

    /**
     * Resolve a Coil model for a photo. Local content URIs and files resolve
     * immediately; remote items (SMB and Synology) are fetched through MediaCache to a
     * verified local file, so Coil never sees a network URL or NAS-relative token.
     * Returns either a ready model or a structured failure; the UI records the failure and advances.
     */
    suspend fun resolveModel(display: DisplayPhoto): PhotoModelResolution {
        val extension = ImageFormatSupport.extension(display.fileName)
        if (!ImageFormatSupport.isPlatformDecodable(
                display.fileName, display.mimeType, Build.VERSION.SDK_INT,
            )
        ) {
            return PhotoModelResolution.Failed(
                DecodeFailure(
                    photoId = display.id,
                    sourceId = display.sourceId,
                    fileExtension = extension,
                    mimeType = display.mimeType,
                    stage = DecodeFailureStage.CAPABILITY,
                    permanent = true,
                    reason = "platform_heif_decoder_unavailable",
                )
            )
        }

        if (display.isContentUri) {
            return PhotoModelResolution.Ready(Uri.parse(display.openToken), localThumbnailCacheEligible = true)
        }
        if (!display.needsCache) {
            return PhotoModelResolution.Ready(File(display.openToken), localThumbnailCacheEligible = true)
        }

        val item = PhotoItem(
            stableId = display.stableId,
            sourceId = SourceId(display.sourceId),
            normalizedPath = display.normalizedPath,
            folderName = display.folderName,
            fileName = display.fileName,
            mimeType = display.mimeType,
            sizeBytes = display.sizeBytes,
            fileModifiedEpochMs = display.fileModifiedEpochMs,
            openToken = display.openToken,
        )
        val cacheResult = if (remotePrimaryCachedOnly && display.sourceId == remotePrimarySourceId) {
            services.mediaCache.resolveIfCached(item)
        } else {
            val src = activeRemoteSources[display.sourceId]
                ?: return PhotoModelResolution.Failed(
                    DecodeFailure(
                        photoId = display.id,
                        sourceId = display.sourceId,
                        fileExtension = extension,
                        mimeType = display.mimeType,
                        stage = DecodeFailureStage.CACHE_LOOKUP,
                        reason = "source_not_active",
                    )
                )
            val protectedKeys = setOfNotNull(
                _state.value.engine.current?.stableId,
                _state.value.engine.next?.stableId,
            ).filter { it.isNotEmpty() }.toSet()
            services.mediaCache.resolve(item, src, protectedKeys)
        }

        return when (cacheResult) {
            is MediaCache.ResolveResult.Ready -> PhotoModelResolution.Ready(cacheResult.file)
            is MediaCache.ResolveResult.Failed -> PhotoModelResolution.Failed(
                DecodeFailure(
                    photoId = display.id,
                    sourceId = display.sourceId,
                    fileExtension = extension,
                    mimeType = display.mimeType,
                    stage = when (cacheResult.stage) {
                        MediaCache.FailureStage.CACHE_LOOKUP -> DecodeFailureStage.CACHE_LOOKUP
                        MediaCache.FailureStage.SOURCE_READ -> DecodeFailureStage.SOURCE_READ
                        MediaCache.FailureStage.VERIFY_DECODE -> DecodeFailureStage.VERIFY_DECODE
                        MediaCache.FailureStage.CACHE_COMMIT -> DecodeFailureStage.CACHE_COMMIT
                        MediaCache.FailureStage.UNKNOWN -> DecodeFailureStage.UNKNOWN
                    },
                    exceptionClass = cacheResult.exceptionClass,
                    permanent = cacheResult.stage == MediaCache.FailureStage.VERIFY_DECODE,
                    reason = if (cacheResult.stage == MediaCache.FailureStage.VERIFY_DECODE) {
                        "cached_bytes_not_decodable"
                    } else null,
                    sourceLevelFailure = cacheResult.sourceLevelFailure,
                )
            )
        }
    }

    /**
     * Read only enough bytes to discover dimensions for an optimizer candidate.
     *
     * This deliberately bypasses [resolveModel] for a remote cache miss: resolving a
     * candidate materializes and verifies the complete NAS original, which made collage
     * ranking take tens of seconds. Successful bounds are persisted so subsequent
     * presentations stay metadata-only.
     */
    suspend fun probeRemoteCollageDimensions(display: DisplayPhoto): Pair<Int, Int>? {
        if (!display.needsCache) return null
        val item = PhotoItem(
            stableId = display.stableId,
            sourceId = SourceId(display.sourceId),
            normalizedPath = display.normalizedPath,
            folderName = display.folderName,
            fileName = display.fileName,
            mimeType = display.mimeType,
            sizeBytes = display.sizeBytes,
            fileModifiedEpochMs = display.fileModifiedEpochMs,
            openToken = display.openToken,
        )

        suspend fun persist(dimensions: Pair<Int, Int>?): Pair<Int, Int>? {
            val valid = dimensions?.takeIf { it.first > 0 && it.second > 0 } ?: return null
            runCatching {
                services.photoDao.updateDimensionsIfMissing(display.id, valid.first, valid.second)
            }
            return valid
        }

        when (val cached = services.mediaCache.resolveIfCached(item)) {
            is MediaCache.ResolveResult.Ready -> {
                val dimensions = withContext(Dispatchers.IO) {
                    try {
                        RemoteImageBoundsProbe.decode(cached.file.inputStream())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }
                return persist(dimensions)
            }
            is MediaCache.ResolveResult.Failed -> Unit
        }

        val source = activeRemoteSources[display.sourceId] ?: return null
        val dimensions = withTimeoutOrNull(REMOTE_COLLAGE_PROBE_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val stream = source.openStream(
                        item,
                        OpenOptions(
                            timeoutMs = REMOTE_COLLAGE_PROBE_TIMEOUT_MS,
                            preferOriginal = false,
                        ),
                    )
                    RemoteImageBoundsProbe.decode(stream)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
        return persist(dimensions)
    }

    /**
     * Return the bounded optimizer pool in deterministic order.
     *
     * Folder-balanced reservations/history remain absolute: this method never invents an
     * unreserved companion. Other selection modes may add source-local candidates from
     * other folders after the exact anchor folder so the optimizer can apply the task's
     * same-folder > orientation > visual-fit priority.
     */
    suspend fun portraitCollageCandidates(anchor: DisplayPhoto, limit: Int = 12): List<DisplayPhoto> {
        val cap = limit.coerceIn(1, 12)
        val engineState = engine.ui.value
        val reservedMatches = engineState.reservedCandidateIds.firstOrNull() == anchor.id
        val historyMatches = engineState.historyPhotoIds.firstOrNull() == anchor.id
        val orderedIds = when {
            reservedMatches -> engineState.reservedCandidateIds.drop(1)
            historyMatches -> engineState.historyPhotoIds.drop(1)
            else -> emptyList()
        }.take(cap)
        if (reservedMatches || historyMatches) {
            if (orderedIds.isEmpty()) return emptyList()
            val byId = services.photoDao.byIds(orderedIds).associateBy { it.id }
            // Do not add or substitute anything here: only reservation/history members
            // can be committed by folder-balanced shuffle.
            return orderedIds.mapNotNull(byId::get).map { it.toDisplayPhoto() }
        }
        if (_state.value.selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE) {
            // A missing reservation at a folder-cycle boundary is a single presentation,
            // never permission to consume an arbitrary photo from another folder.
            return emptyList()
        }

        val favoritesFlag = if (_state.value.favoritesOnly) 1 else 0
        val cachedFlag = if (remotePrimaryCachedOnly && anchor.sourceId == remotePrimarySourceId) 1 else 0
        val anchorTime = anchor.dateTakenEpochMs ?: anchor.fileModifiedEpochMs
        val now = System.currentTimeMillis()
        val anchorParent = anchor.normalizedPath.substringBeforeLast('/', "")
        fun parent(photo: DisplayPhoto) = photo.normalizedPath.substringBeforeLast('/', "")
        fun orientationTier(photo: DisplayPhoto): Int = when (
            PortraitCollagePolicy.classify(photo.width, photo.height, photo.exifOrientation)
        ) {
            com.example.familyphotoframe.domain.engine.PhotoOrientation.PORTRAIT -> 0
            com.example.familyphotoframe.domain.engine.PhotoOrientation.SQUARE_OR_UNKNOWN -> 1
            com.example.familyphotoframe.domain.engine.PhotoOrientation.LANDSCAPE -> 2
        }

        return runCatching {
            val sameFolder = services.photoDao.collageCandidates(
                sourceId = anchor.sourceId,
                folderName = anchor.folderName,
                anchorId = anchor.id,
                anchorTime = anchorTime,
                maxFailures = currentMaxFailures(),
                favoritesOnly = favoritesFlag,
                cachedOnly = cachedFlag,
                allowHeif = if (services.allowHeifPlayback) 1 else 0,
                limit = (cap * 4).coerceAtMost(64),
            ).map { it.toDisplayPhoto() }
                .filter { parent(it) == anchorParent }

            val sourceWide = services.photoDao.collageCandidatesAcrossSource(
                sourceId = anchor.sourceId,
                anchorId = anchor.id,
                anchorTime = anchorTime,
                maxFailures = currentMaxFailures(),
                favoritesOnly = favoritesFlag,
                cachedOnly = cachedFlag,
                allowHeif = if (services.allowHeifPlayback) 1 else 0,
                limit = (cap * 8).coerceAtMost(128),
            ).map { it.toDisplayPhoto() }

            (sameFolder + sourceWide).asSequence()
                .filter { it.id != anchor.id }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<DisplayPhoto> { if (parent(it) == anchorParent) 0 else 1 }
                        .thenBy(::orientationTier)
                        .thenBy { PortraitCollagePolicy.candidateScore(anchorTime, it, now) }
                        .thenBy { it.id },
                )
                .take(cap)
                .toList()
        }.getOrDefault(emptyList())
    }

    /** Save an SMB source (password encrypted via SecretStore) and make it active. */
    fun saveSmb(host: String, share: String, path: String, user: String, domain: String, password: String) {
        viewModelScope.launch {
            val draft = SmbSettings(
                host = host.trim(),
                share = share.trim().trim('/'),
                path = path.trim().trim('/'),
                user = user.trim(),
                domain = domain.trim(),
            )
            val current = services.settings.settings.first()
            val existing = current.source.smb
            val retainedRef = existing?.credentialRef.orEmpty()
                .takeIf { it.isNotBlank() && CredentialPolicy.sameSmbScope(existing, draft) }
            val ref = retainedRef ?: CredentialPolicy.smbRef(draft)
            val resolvedPassword = password.takeIf { it.isNotEmpty() }
                ?: retainedRef?.let { services.secretStore.reveal(it) }
            if (resolvedPassword == null && draft.user.isNotBlank()) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_password_required))
                }
                return@launch
            }
            val savedRef = if (resolvedPassword == null) "" else ref // anonymous/guest SMB
            if (resolvedPassword != null && (password.isNotEmpty() || retainedRef == null)) {
                services.secretStore.store(ref, "smb_password", resolvedPassword)
            }
            val trigger = if (password.isNotEmpty()) {
                SourceRefreshTrigger.CREDENTIAL_UPDATED
            } else {
                SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
            }
            updateSettingsAndRefresh(trigger) {
                it.copy(
                    source = it.source.copy(
                        kind = ActiveSourceKind.SMB,
                        displayName = "${draft.host}/${draft.share}",
                        smb = draft.copy(credentialRef = savedRef),
                    )
                )
            }
        }
    }

    /** Test an SMB connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testSmb(host: String, share: String, path: String, user: String, domain: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(smbTestResult = appContext.getString(R.string.msg_smb_testing)) }
            val draft = SmbSettings(
                host = host.trim(),
                share = share.trim().trim('/'),
                path = path.trim().trim('/'),
                user = user.trim(),
                domain = domain.trim(),
            )
            val existing = services.settings.settings.first().source.smb
            val effectivePassword = password.takeIf { it.isNotEmpty() }
                ?: existing?.credentialRef
                    ?.takeIf { it.isNotBlank() && CredentialPolicy.sameSmbScope(existing, draft) }
                    ?.let { services.secretStore.reveal(it) }
                    .orEmpty()
            val src = services.smbSource(
                SmbConnection(draft.host, draft.share, draft.path),
                SmbCredentials(draft.domain, draft.user, effectivePassword),
            )
            // A connection test is one-shot: close the session rather than leaving it
            // pooled, or every press of Test leaks a context.
            val res = try {
                smbHealthMessageRes(sourceTestWithDiagnostics(
                    src,
                    "SMB",
                    8_000,
                    DiagnosticOrigin.ANDROID_UI,
                    diagnosticToken(draft.toString(), "config"),
                ))
            } finally {
                runCatching { src.close() }
            }
            _state.update { it.copy(smbTestResult = appContext.getString(res)) }
        }
    }

    /** User-facing message for an SMB [SourceHealth] (shared by activate + test). */
    private fun smbHealthMessageRes(health: SourceHealth): Int = when (health) {
        is SourceHealth.Ok -> R.string.msg_smb_ok
        is SourceHealth.NeedsPermission -> R.string.msg_smb_auth
        is SourceHealth.Missing -> R.string.msg_smb_missing
        is SourceHealth.Unavailable -> R.string.msg_smb_unreachable
        is SourceHealth.ProviderError -> R.string.msg_smb_error
    }

    fun clearSmbTestResult() = _state.update { it.copy(smbTestResult = null) }

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
    ) {
        viewModelScope.launch {
            val current = services.settings.settings.first()
            val existing = current.source.synology
            val host = SynologyApi.normalizeBaseUrl(baseUrl)
            val draft = SynologySettings(
                baseUrl = host,
                folderPath = folderPath.trim().ifBlank { "/photo" },
                user = user.trim(),
                useThumbnails = useThumbnails,
                thumbnailSize = existing?.thumbnailSize ?: "large",
                pinnedCertSha256 = if (CredentialPolicy.sameSynologyHost(existing, SynologySettings(baseUrl = host))) {
                    pinnedCertSha256 ?: existing?.pinnedCertSha256
                } else {
                    pinnedCertSha256?.takeIf(CertPinning::isValidSha256)
                },
            )
            val retainedRef = existing?.credentialRef.orEmpty()
                .takeIf { it.isNotBlank() && CredentialPolicy.sameSynologyScope(existing, draft) }
            val ref = retainedRef ?: CredentialPolicy.synologyRef(draft)
            val resolvedPassword = password.takeIf { it.isNotEmpty() }
                ?: retainedRef?.let { services.secretStore.reveal(it) }
            if (resolvedPassword.isNullOrEmpty()) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_password_required))
                }
                return@launch
            }
            if (password.isNotEmpty() || retainedRef == null) {
                services.secretStore.store(ref, "synology_password", resolvedPassword)
            }
            pendingSynologyOtp = otpCode.trim().ifBlank { null }
            val trigger = if (password.isNotEmpty()) {
                SourceRefreshTrigger.CREDENTIAL_UPDATED
            } else {
                SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
            }
            updateSettingsAndRefresh(trigger) {
                it.copy(
                    source = it.source.copy(
                        kind = ActiveSourceKind.SYNOLOGY,
                        displayName = host,
                        synology = draft.copy(credentialRef = ref),
                    )
                )
            }
        }
    }

    /** Test a Synology connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testSynology(
        baseUrl: String,
        folderPath: String,
        user: String,
        password: String,
        otpCode: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(smbTestResult = appContext.getString(R.string.msg_syn_testing)) }
            val draft = SynologySettings(
                baseUrl = SynologyApi.normalizeBaseUrl(baseUrl),
                folderPath = folderPath.trim().ifBlank { "/photo" },
                user = user.trim(),
            )
            val existing = services.settings.settings.first().source.synology
            val effectivePassword = password.takeIf { it.isNotEmpty() }
                ?: existing?.credentialRef
                    ?.takeIf { it.isNotBlank() && CredentialPolicy.sameSynologyScope(existing, draft) }
                    ?.let { services.secretStore.reveal(it) }
                    .orEmpty()
            val src = services.synologySource(
                SynologyConnection(baseUrl = draft.baseUrl, folderPath = draft.folderPath),
                SynologyCredentials(draft.user, effectivePassword, otpCode.trim().ifBlank { null }),
            )
            val res = synologyHealthMessageRes(sourceTestWithDiagnostics(
                src,
                "SYNOLOGY",
                10_000,
                DiagnosticOrigin.ANDROID_UI,
                diagnosticToken(draft.toString(), "config"),
            ))
            _state.update { it.copy(smbTestResult = appContext.getString(res)) }
        }
    }

    /**
     * Looks up the certificate the NAS presents so the user can approve it. Populates
     * [SlideshowUiState.synologyCertFingerprint]; null clears any previous offer.
     */
    fun probeSynologyCertificate(baseUrl: String) {
        viewModelScope.launch {
            val src = services.synologySource(
                SynologyConnection(baseUrl = SynologyApi.normalizeBaseUrl(baseUrl)),
                SynologyCredentials("", ""),
            )
            _state.update { it.copy(synologyCertFingerprint = src.probeCertificateFingerprint()) }
        }
    }

    /**
     * Records a certificate fingerprint the user has explicitly approved. Deliberately a
     * separate, explicit action rather than something the test/save flow does silently —
     * approving a certificate is a security decision and must be a deliberate one.
     */
    fun trustSynologyCertificate(fingerprint: String) {
        if (!CertPinning.isValidSha256(fingerprint)) return
        viewModelScope.launch {
            services.settings.update { s ->
                val syn = s.source.synology ?: return@update s
                s.copy(source = s.source.copy(synology = syn.copy(pinnedCertSha256 = fingerprint)))
            }
            _state.update { it.copy(synologyCertFingerprint = null) }
            diagnostics.log(DiagnosticsLog.Category.SOURCE, "SYNOLOGY_CERT_PINNED")
        }
    }

    /** Removes a previously approved certificate, returning to strict platform validation. */
    fun clearSynologyCertificate() {
        viewModelScope.launch {
            services.settings.update { s ->
                val syn = s.source.synology ?: return@update s
                s.copy(source = s.source.copy(synology = syn.copy(pinnedCertSha256 = null)))
            }
            diagnostics.log(DiagnosticsLog.Category.SOURCE, "SYNOLOGY_CERT_UNPINNED")
        }
    }

    /** User-facing message for a Synology [SourceHealth]; 2FA gets its own hint. */
    private fun synologyHealthMessageRes(health: SourceHealth): Int = when (health) {
        is SourceHealth.Ok -> R.string.msg_syn_ok
        is SourceHealth.NeedsPermission -> R.string.msg_syn_permission
        is SourceHealth.Missing -> R.string.msg_syn_folder_missing
        is SourceHealth.Unavailable -> R.string.msg_syn_unreachable
        is SourceHealth.ProviderError -> when (health.detail) {
            "two_factor_required" -> R.string.msg_syn_2fa
            "auth_failed" -> R.string.msg_syn_auth
            "CertUntrusted" -> R.string.msg_syn_cert
            "HostUnreachable" -> R.string.msg_syn_unreachable
            else -> R.string.msg_syn_error
        }
    }

    // ---- WebDAV / Nextcloud (ROADMAP.md other candidate sources) ----------------

    /**
     * Save and activate a WebDAV source. An empty [password] keeps the stored secret
     * when the host and account are unchanged, exactly as for SMB and Synology.
     *
     * When [rootPath] is blank the standard Nextcloud endpoint for [user] is assumed,
     * which is right for the overwhelmingly common case and still overridable for a
     * plain Apache mount.
     */
    fun saveWebDav(
        baseUrl: String,
        rootPath: String,
        folderPath: String,
        user: String,
        password: String,
        pinnedCertSha256: String? = null,
    ) {
        viewModelScope.launch {
            val current = services.settings.settings.first()
            val existing = current.source.webdav
            val host = WebDavApi.normalizeBaseUrl(baseUrl)
            val trimmedUser = user.trim()
            val draft = WebDavSettings(
                baseUrl = host,
                rootPath = rootPath.trim().ifBlank { WebDavApi.nextcloudFilesRoot(trimmedUser) },
                folderPath = WebDavApi.normalizePath(folderPath),
                user = trimmedUser,
                pinnedCertSha256 = if (CredentialPolicy.sameWebDavHost(existing, WebDavSettings(baseUrl = host))) {
                    pinnedCertSha256 ?: existing?.pinnedCertSha256
                } else {
                    pinnedCertSha256?.takeIf(CertPinning::isValidSha256)
                },
            )
            val retainedRef = existing?.credentialRef.orEmpty()
                .takeIf { it.isNotBlank() && CredentialPolicy.sameWebDavScope(existing, draft) }
            val ref = retainedRef ?: CredentialPolicy.webDavRef(draft)
            val resolvedPassword = password.takeIf { it.isNotEmpty() }
                ?: retainedRef?.let { services.secretStore.reveal(it) }
            if (resolvedPassword.isNullOrEmpty()) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_password_required))
                }
                return@launch
            }
            if (password.isNotEmpty() || retainedRef == null) {
                services.secretStore.store(ref, "webdav_password", resolvedPassword)
            }
            val trigger = if (password.isNotEmpty()) {
                SourceRefreshTrigger.CREDENTIAL_UPDATED
            } else {
                SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
            }
            updateSettingsAndRefresh(trigger) {
                it.copy(
                    source = it.source.copy(
                        kind = ActiveSourceKind.WEBDAV,
                        displayName = host,
                        webdav = draft.copy(credentialRef = ref),
                    )
                )
            }
        }
    }

    /** Test a WebDAV connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testWebDav(
        baseUrl: String,
        rootPath: String,
        folderPath: String,
        user: String,
        password: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(smbTestResult = appContext.getString(R.string.msg_dav_testing)) }
            val trimmedUser = user.trim()
            val draft = WebDavSettings(
                baseUrl = WebDavApi.normalizeBaseUrl(baseUrl),
                rootPath = rootPath.trim().ifBlank { WebDavApi.nextcloudFilesRoot(trimmedUser) },
                folderPath = WebDavApi.normalizePath(folderPath),
                user = trimmedUser,
            )
            val existing = services.settings.settings.first().source.webdav
            val effectivePassword = password.takeIf { it.isNotEmpty() }
                ?: existing?.credentialRef
                    ?.takeIf { it.isNotBlank() && CredentialPolicy.sameWebDavScope(existing, draft) }
                    ?.let { services.secretStore.reveal(it) }
                    .orEmpty()
            val src = services.webDavSource(
                WebDavConnection(
                    baseUrl = draft.baseUrl,
                    rootPath = draft.rootPath,
                    folderPath = draft.folderPath,
                    pinnedCertSha256 = existing?.pinnedCertSha256,
                ),
                WebDavCredentials(draft.user, effectivePassword),
            )
            val res = webDavHealthMessageRes(sourceTestWithDiagnostics(
                src,
                "WEBDAV",
                10_000,
                DiagnosticOrigin.ANDROID_UI,
                diagnosticToken(draft.toString(), "config"),
            ))
            _state.update { it.copy(smbTestResult = appContext.getString(res)) }
        }
    }

    /** User-facing message for a WebDAV [SourceHealth]; "not WebDAV" gets its own hint. */
    private fun webDavHealthMessageRes(health: SourceHealth): Int = when (health) {
        is SourceHealth.Ok -> R.string.msg_dav_ok
        is SourceHealth.NeedsPermission -> R.string.msg_dav_permission
        is SourceHealth.Missing -> R.string.msg_dav_folder_missing
        is SourceHealth.Unavailable -> R.string.msg_dav_unreachable
        is SourceHealth.ProviderError -> when (health.detail) {
            "auth_failed" -> R.string.msg_dav_auth
            "not_webdav" -> R.string.msg_dav_not_webdav
            "CertUntrusted" -> R.string.msg_dav_cert
            "HostUnreachable" -> R.string.msg_dav_unreachable
            else -> R.string.msg_dav_error
        }
    }

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

    /**
     * Add or remove a co-primary source, so a local folder and a NAS (or two NAS
     * protocols) can feed one slideshow.
     *
     * Refuses to merge in a kind that has no connection settings yet: it would silently
     * contribute nothing, and "I ticked it and nothing happened" is a worse outcome than
     * being told to configure it first.
     */
    fun setAlsoPlay(kind: ActiveSourceKind, enabled: Boolean) {
        viewModelScope.launch {
            val current = services.settings.settings.first().source
            if (enabled && !isConfigured(kind, current)) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_not_configured))
                }
                return@launch
            }
            services.settings.update { s ->
                val next = if (enabled) s.source.alsoPlay + kind else s.source.alsoPlay - kind
                // The chosen source is already a primary; listing it again would just
                // duplicate it in the pool.
                s.copy(source = s.source.copy(alsoPlay = next - s.source.kind))
            }
        }
    }

    private fun isConfigured(kind: ActiveSourceKind, source: ActiveSource): Boolean =
        SourceStatusPolicy.isConfigured(kind, source)

    /**
     * Promote an already-configured source to primary without retyping its connection.
     * Samples are allowed here too, which is what the "use samples" button does.
     */
    fun setPrimarySource(kind: ActiveSourceKind) {
        viewModelScope.launch {
            val current = services.settings.settings.first().source
            if (kind == current.kind) return@launch
            if (kind != ActiveSourceKind.SAMPLES && !isConfigured(kind, current)) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_not_configured))
                }
                return@launch
            }
            updateSettingsAndRefresh(SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED) { s ->
                s.copy(
                    source = s.source.copy(
                        kind = kind,
                        displayName = primaryDisplayName(kind, s.source),
                        // A primary listed again as a co-primary would duplicate the pool.
                        alsoPlay = s.source.alsoPlay - kind,
                    )
                )
            }
        }
    }

    private fun primaryDisplayName(kind: ActiveSourceKind, source: ActiveSource): String = when (kind) {
        // The SAF display name is captured when the folder is picked and cannot be rebuilt.
        ActiveSourceKind.LOCAL_SAF -> source.displayName
        ActiveSourceKind.SMB -> source.smb?.let { "${it.host}/${it.share}" }.orEmpty()
        ActiveSourceKind.SYNOLOGY -> source.synology?.baseUrl.orEmpty()
        ActiveSourceKind.WEBDAV -> source.webdav?.baseUrl.orEmpty()
        ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> ""
    }

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

    /**
     * Enabling with no weekdays selected would silently never run, so switching it on
     * seeds every day; the user can then deselect.
     */
    fun setAutoRescanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { s ->
                val days = RescanSchedule.parseDays(s.schedule.autoRescanDays)
                s.copy(
                    schedule = s.schedule.copy(
                        autoRescanEnabled = enabled,
                        autoRescanDays = if (enabled && days.isEmpty()) {
                            RescanSchedule.formatDays(RescanSchedule.everyDay())
                        } else {
                            s.schedule.autoRescanDays
                        },
                    )
                )
            }
        }
    }

    /** Accepts partial input while typing; only a valid `HH:mm` is stored. */
    fun setAutoRescanAt(text: String) {
        viewModelScope.launch {
            val minutes = SleepSchedule.parseMinutes(text) ?: return@launch
            services.settings.update {
                it.copy(schedule = it.schedule.copy(
                    autoRescanAt = SleepSchedule.formatMinutes(minutes),
                ))
            }
        }
    }

    fun setAutoRescanDay(isoDay: Int, selected: Boolean) {
        viewModelScope.launch {
            services.settings.update { s ->
                val days = RescanSchedule.parseDays(s.schedule.autoRescanDays)
                val next = if (selected) days + isoDay else days - isoDay
                s.copy(schedule = s.schedule.copy(autoRescanDays = RescanSchedule.formatDays(next)))
            }
        }
    }

    fun setAutoRescanDays(days: Set<Int>) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(schedule = it.schedule.copy(
                    autoRescanDays = RescanSchedule.formatDays(days),
                ))
            }
        }
    }

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

    /**
     * Turn favourites-only playback on or off.
     *
     * Refuses to switch on with an empty favourites set: the engine would find no
     * primary photo, silently drop to the sample pool, and look like a bug rather than
     * a setting. Telling the user why is cheaper than letting them debug it.
     */
    // ---- folder ("album") selection (spec §9.4) --------------------------------

    /**
     * Folders available to choose from, for the current playback sources.
     *
     * Read on demand rather than kept in UI state: the list only matters while the
     * picker is open, and it changes with every rescan.
     */
    suspend fun availableFolders(): List<FolderSummary> =
        runCatching { services.photoDao.folderSummaries(currentPlaybackSourceIds()) }
            .getOrDefault(emptyList())

    /** Replace the complete folder selection with exactly one DataStore transaction. */
    fun setSelectedFolders(folderKeys: Set<String>) {
        viewModelScope.launch { services.settings.setSelectedFolders(folderKeys) }
    }

    /** Show one temporary photo from an exact direct folder without mutating shuffle queues. */
    fun previewFolderOnce(folder: FolderSummary) {
        viewModelScope.launch { previewFolderOnceByKey(folder.selectionKey, folder) }
    }

    private suspend fun previewFolderOnceByKey(
        folderKey: String,
        knownFolder: FolderSummary? = null,
    ): String? {
        val folder = knownFolder ?: availableFolders().firstOrNull { it.selectionKey == folderKey }
            ?: return "Folder is not available"
        engine.previewFolderOnce(folder.selectionKey)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_PREVIEW_REQUESTED",
            "folderToken" to diagnosticToken(folder.selectionKey, "folder"),
        )
        _state.update {
            it.copy(transientNotice = appContext.getString(R.string.msg_folder_preview_once, folder.displayPath))
        }
        return null
    }

    /**
     * Restrict the active playlist to this exact source/direct-directory key. Built-in
     * playlists use the global folder selection; user playlists own their folder rules.
     * Either path changes eligibility and therefore produces a new shuffle scope.
     */
    fun useFolderInActivePlaylist(folder: FolderSummary) {
        viewModelScope.launch { useFolderInActivePlaylistByKey(folder.selectionKey, folder) }
    }

    private suspend fun useFolderInActivePlaylistByKey(
        folderKey: String,
        knownFolder: FolderSummary? = null,
    ): String? {
        val folder = knownFolder ?: availableFolders().firstOrNull { it.selectionKey == folderKey }
            ?: return "Folder is not available"
        services.settings.update { current ->
            val activeId = current.playlists.activePlaylistId
            if (activeId in PlaylistSettings.BUILT_IN_IDS) {
                current.copy(selectedFolders = setOf(folder.selectionKey))
            } else {
                val now = System.currentTimeMillis()
                current.copy(
                    playlists = current.playlists.copy(
                        playlists = current.playlists.playlists.map { playlist ->
                            if (playlist.id == activeId) {
                                playlist.copy(folderNames = setOf(folder.selectionKey), updatedAtEpochMs = now)
                            } else playlist
                        }
                    )
                )
            }
        }
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PLAYLIST_FOLDER_APPLIED",
            "playlistToken" to diagnosticToken(_state.value.activePlaylistId, "playlist"),
            "folderToken" to diagnosticToken(folder.selectionKey, "folder"),
        )
        _state.update {
            it.copy(transientNotice = appContext.getString(R.string.msg_folder_used_in_playlist, folder.displayPath))
        }
        return null
    }

    /** Clear the filter — play everything again. */
    fun clearFolderSelection() {
        setSelectedFolders(emptySet())
    }

    fun setFavoritesOnly(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val pools = currentPlaybackSourceIds()
                val favorites = runCatching { services.photoDao.favoriteCount(pools) }.getOrDefault(0)
                if (favorites == 0) {
                    _state.update {
                        it.copy(transientNotice = appContext.getString(R.string.msg_no_favorites))
                    }
                    return@launch
                }
            }
            services.settings.update { it.copy(favoritesOnly = enabled) }
        }
    }

    /** Star or un-star the photo on screen (D-pad, web, or touch). */
    fun toggleFavorite() = engine.toggleFavorite()

    fun setInteractionHold(held: Boolean) = engine.setInteractionHold(held)

    fun onVisiblePresentationChanged(photos: List<com.example.familyphotoframe.domain.engine.DisplayPhoto>) {
        _state.update { current ->
            if (current.visiblePresentationPhotos.map { it.id } == photos.map { it.id }) current
            else current.copy(visiblePresentationPhotos = photos)
        }
        publishDiagnosticPlayback(
            _state.value,
            layoutOverride = when (photos.size) {
                0 -> "NONE"
                1 -> "SINGLE"
                else -> "COLLAGE_${photos.size.coerceAtMost(3)}"
            },
        )
    }

    fun setFavoriteForPhotos(ids: List<Long>, favorite: Boolean) {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return
        viewModelScope.launch {
            services.photoDao.setFavorites(distinct, favorite)
            engine.reflectFavoriteState(distinct.toSet(), favorite)
            _state.update { state ->
                state.copy(
                    visiblePresentationPhotos = state.visiblePresentationPhotos.map { photo ->
                        if (photo.id in distinct) photo.copy(isFavorite = favorite) else photo
                    },
                    transientNotice = if (favorite) "Added to favorites" else "Removed from favorites",
                )
            }
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                if (favorite) "PHOTO_FAVORITE_ADDED" else "PHOTO_FAVORITE_REMOVED",
                "presentationToken" to diagnosticToken(distinct.joinToString(","), "presentation"),
                "members" to distinct.size.toString(),
            )
            if (!favorite && _state.value.favoritesOnly &&
                _state.value.engine.current?.id in distinct
            ) engine.next()
        }
    }

    fun hidePhotos(ids: List<Long>) {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return
        viewModelScope.launch {
            services.photoDao.setHiddenBatch(distinct, true)
            engine.invalidatePlaybackPool()
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTO_EXCLUDED",
                "presentationToken" to diagnosticToken(distinct.joinToString(","), "presentation"),
                "members" to distinct.size.toString(),
            )
            _state.update {
                it.copy(
                    undoHiddenPhotoIds = distinct,
                    transientNotice = if (distinct.size == 1) "Photo hidden from slideshow" else "${distinct.size} photos hidden from slideshow",
                )
            }
            engine.next()
        }
    }

    fun undoLastHide() {
        val ids = _state.value.undoHiddenPhotoIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            services.photoDao.setHiddenBatch(ids, false)
            engine.invalidatePlaybackPool()
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTO_EXCLUSION_UNDONE",
                "presentationToken" to diagnosticToken(ids.joinToString(","), "presentation"),
                "members" to ids.size.toString(),
            )
            _state.update { it.copy(undoHiddenPhotoIds = emptyList(), transientNotice = "Photos restored") }
        }
    }

    fun clearHideUndo() {
        _state.update { it.copy(undoHiddenPhotoIds = emptyList()) }
    }

    /** Hide the photo on screen from all future playback; undone by [unhideAllPhotos]. */
    fun hideCurrentPhoto() {
        engine.hideCurrent()
        _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_photo_hidden)) }
    }

    /** Restore every hidden photo — the escape hatch for an accidental hide. */
    fun unhideAllPhotos() {
        viewModelScope.launch {
            val hidden = runCatching { services.photoDao.hiddenCount() }.getOrDefault(0)
            services.photoDao.unhideAll()
            engine.invalidatePlaybackPool()
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTOS_UNHIDDEN",
                "count" to hidden.toString(),
            )
            _state.update {
                it.copy(transientNotice = appContext.getString(R.string.msg_photos_unhidden, hidden))
            }
            engine.next()
        }
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

    fun playPlaylist(id: String, overrideMinutes: Int? = null) {
        viewModelScope.launch {
            services.settings.update { current ->
                if (current.playlists.playlists.none { it.id == id && it.enabled }) return@update current
                val now = System.currentTimeMillis()
                val until = when {
                    overrideMinutes == null && current.playlists.scheduleEnabled ->
                        now + PlaylistSchedule.minutesUntilBoundary(current.playlists.scheduleRules, now) * 60_000L
                    overrideMinutes == null -> 0L
                    overrideMinutes < 0 -> Long.MAX_VALUE
                    else -> now + overrideMinutes.coerceIn(1, 24 * 60) * 60_000L
                }
                current.copy(
                    playlists = current.playlists.copy(
                        activePlaylistId = id,
                        manualOverrideUntilEpochMs = until,
                    )
                )
            }
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PLAYLIST_STARTED",
                "playlistToken" to diagnosticToken(id, "playlist"),
            )
        }
    }

    fun createPlaylist(name: String, favoritesOnly: Boolean = false, localUploadsOnly: Boolean = false) {
        val clean = name.trim().take(80)
        if (clean.isBlank()) return
        viewModelScope.launch {
            services.settings.update { current ->
                if (current.playlists.playlists.any { it.name.equals(clean, ignoreCase = true) }) return@update current
                val now = System.currentTimeMillis()
                val playlist = SlideshowPlaylist(
                    id = "user_${java.util.UUID.randomUUID()}",
                    name = clean,
                    favoritesOnly = favoritesOnly,
                    localUploadsOnly = localUploadsOnly,
                    sourceIds = if (localUploadsOnly) setOf(ServiceLocator.SOURCE_LOCAL_UPLOADS) else emptySet(),
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
    }

    fun deletePlaylist(id: String) {
        if (id in PlaylistSettings.BUILT_IN_IDS) return
        viewModelScope.launch {
            services.settings.update { current ->
                val remaining = current.playlists.playlists.filterNot { it.id == id }
                val active = if (current.playlists.activePlaylistId == id) PlaylistSettings.PLAYLIST_ALL
                    else current.playlists.activePlaylistId
                current.copy(
                    playlists = current.playlists.copy(
                        playlists = remaining,
                        activePlaylistId = active,
                        scheduleRules = current.playlists.scheduleRules.filterNot { it.playlistId == id },
                    )
                )
            }
            engine.deletePlaylistShuffleState(id)
        }
    }

    fun setPlaylistScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(playlists = it.playlists.copy(scheduleEnabled = enabled)) }
        }
    }

    fun addPlaylistScheduleRule(
        name: String,
        playlistId: String,
        startTime: String,
        endTime: String,
        days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
        priority: Int = 0,
    ) {
        if (SleepSchedule.parseMinutes(startTime) == null || SleepSchedule.parseMinutes(endTime) == null) return
        viewModelScope.launch {
            services.settings.update { current ->
                if (current.playlists.playlists.none { it.id == playlistId }) return@update current
                val rule = PlaylistScheduleRule(
                    id = "rule_${java.util.UUID.randomUUID()}",
                    name = name.trim().ifBlank { "Playlist schedule" }.take(80),
                    playlistId = playlistId,
                    daysOfWeek = days,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                current.copy(playlists = current.playlists.copy(scheduleRules = current.playlists.scheduleRules + rule))
            }
        }
    }

    fun deletePlaylistScheduleRule(id: String) {
        viewModelScope.launch {
            services.settings.update { current ->
                current.copy(playlists = current.playlists.copy(
                    scheduleRules = current.playlists.scheduleRules.filterNot { it.id == id }
                ))
            }
        }
    }

    fun cancelPlaylistOverride() {
        viewModelScope.launch {
            services.settings.update { it.copy(playlists = it.playlists.copy(manualOverrideUntilEpochMs = 0L)) }
        }
    }

    private fun playlistScheduleConfig(settings: PlaylistSettings?): String = settings?.let {
        "${it.scheduleEnabled}|${it.defaultPlaylistId}|${it.manualOverrideUntilEpochMs}|" +
            it.playlists.joinToString(",") { p -> "${p.id}:${p.enabled}" } + "|" +
            it.scheduleRules.joinToString(";") { r ->
                "${r.id}:${r.enabled}:${r.playlistId}:${r.daysOfWeek.sorted()}:${r.startTime}:${r.endTime}:" +
                    "${r.priority}:${r.startDateIso}:${r.endDateIso}:${r.updatedAtEpochMs}"
            }
    }.orEmpty()

    private fun restartPlaylistScheduleWatcher() {
        playlistScheduleJob?.cancel()
        playlistScheduleJob = viewModelScope.launch {
            while (isActive) {
                val current = services.settings.settings.first()
                val config = current.playlists
                val now = System.currentTimeMillis()
                val overrideActive = config.manualOverrideUntilEpochMs == Long.MAX_VALUE ||
                    config.manualOverrideUntilEpochMs > now
                if (!overrideActive) {
                    // An expired manual override (e.g. from On This Day, or a timed
                    // playlist activation) must revert regardless of whether day/time
                    // schedule switching is separately enabled — otherwise a frame with
                    // scheduling off (the default) stays parked on the override's
                    // playlist forever once its window passes.
                    val match = if (config.scheduleEnabled) {
                        PlaylistSchedule.activeRule(config.scheduleRules, now)
                    } else {
                        null
                    }
                    val target = match?.rule?.playlistId ?: config.defaultPlaylistId
                    if (config.playlists.any { it.id == target && it.enabled } && target != config.activePlaylistId) {
                        services.settings.update { latest ->
                            latest.copy(playlists = latest.playlists.copy(activePlaylistId = target))
                        }
                        diagnostics.log(
                            DiagnosticsLog.Category.ENGINE,
                            "PLAYLIST_SCHEDULE_SWITCHED",
                            "playlistId" to target,
                            "ruleId" to match?.rule?.id.orEmpty(),
                        )
                    }
                    _state.update { it.copy(activePlaylistRuleName = match?.rule?.name) }
                } else {
                    _state.update { it.copy(activePlaylistRuleName = null) }
                }
                val minutes = PlaylistSchedule.minutesUntilBoundary(config.scheduleRules, now)
                delay(minutes.coerceIn(1, 15).toLong() * 60_000L)
            }
        }
    }

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
            contentHashJob = viewModelScope.launch {
                val hash = services.contentHashBackfiller.backfill(current.id, ::resolveSourceById)
                if (hash != null) engine.reconcileShuffle()
            }
        }

        exifJob?.cancel()
        exifJob = viewModelScope.launch {
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
        resolvedSource(sourceId)
            ?: buildSourceById(sourceId)?.let { built ->
                // Building suspends (settings read, secret reveal), so the EXIF and hash
                // backfills can both miss above and both build one. Re-check afterwards
                // and keep a single session: the loser is closed here, because dropping
                // it unclosed is exactly the leak this method exists to prevent.
                val winner = resolvedSource(sourceId)
                if (winner != null) {
                    runCatching { built.close() }
                    winner
                } else {
                    backfillSources[sourceId.value] = built
                    built
                }
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun resolvedSource(sourceId: SourceId): PhotoSource? =
        activeRemoteSources[sourceId.value] ?: backfillSources[sourceId.value]

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
     * Cancels the EXIF and content-hash backfill jobs — the only jobs that call
     * [resolveSourceById] and then read from the returned source — and waits for them
     * to finish unwinding. Must run before [releaseResolvedSources], or a job's
     * in-flight read gets its source closed out from under it: the same mid-read-close
     * pattern that left jcifs `response_map` entries unreleased in
     * `RemoteImageBoundsProbe`, just reached through a different trigger.
     */
    private suspend fun cancelAndJoinSourceConsumingJobs() {
        exifJob?.cancel()
        contentHashJob?.cancel()
        exifJob?.join()
        contentHashJob?.join()
        exifJob = null
        contentHashJob = null
    }

    /**
     * [onCleared] cannot suspend, so it can only request cancellation here, not wait
     * for it — best-effort, same as the rest of that teardown path.
     */
    private fun cancelSourceConsumingJobs() {
        exifJob?.cancel()
        contentHashJob?.cancel()
    }

    /**
     * Drops every resolved source and releases the transport each one owns.
     *
     * Called wherever the source set is replaced or playback stops. Clearing the maps alone
     * is not enough: an unclosed `CIFSContext` keeps its buffers reachable, so the heap
     * never recovers. Closing is best-effort — a source that fails to close must not stop
     * the rest from being released, or abort the reconfigure that triggered this.
     */
    private fun releaseResolvedSources() {
        // The two maps are disjoint by construction, and closing twice is harmless, so
        // this deliberately does not deduplicate: skipping a close leaks, repeating one
        // does not.
        val resolved = activeRemoteSources.values.toList() + backfillSources.values.toList()
        activeRemoteSources.clear()
        backfillSources.clear()
        resolved.forEach { source -> runCatching { source.close() } }
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

    fun setWeatherEnabled(value: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(weather = it.weather.copy(enabled = value)) }
        }
    }

    /** Whether a successfully fetched weather reading is drawn as an on-screen overlay. */
    fun setWeatherOverlayShow(value: Boolean) = updateOverlays { it.copy(weatherShow = value) }

    /** Coordinates are typed by the user, so the app never needs a location permission. */
    fun setWeatherLocation(latitudeText: String, longitudeText: String) {
        val lat = latitudeText.trim().toDoubleOrNull()
        val lon = longitudeText.trim().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_weather_bad_location)) }
            return
        }
        viewModelScope.launch {
            services.settings.update {
                it.copy(weather = it.weather.copy(latitude = lat, longitude = lon))
            }
        }
    }

    fun setWeatherUnits(units: TemperatureUnits) {
        viewModelScope.launch {
            services.settings.update { it.copy(weather = it.weather.copy(units = units)) }
        }
    }

    fun setWeatherEndpoint(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) return
        viewModelScope.launch {
            services.settings.update { it.copy(weather = it.weather.copy(endpointBaseUrl = trimmed)) }
        }
    }

    /** The key is a secret: stored in the Keystore, never in the settings file. */
    fun setWeatherApiKey(key: String) {
        viewModelScope.launch {
            val ref = if (key.isBlank()) "" else WEATHER_KEY_REF
            if (key.isNotBlank()) services.secretStore.store(WEATHER_KEY_REF, "weather_api_key", key)
            else services.secretStore.forget(WEATHER_KEY_REF)
            services.settings.update { it.copy(weather = it.weather.copy(apiKeyRef = ref)) }
        }
    }

    private fun restartWeather(settings: com.example.familyphotoframe.data.settings.WeatherSettings) {
        viewModelScope.launch {
            val key = if (settings.apiKeyRef.isBlank()) "" else services.secretStore.reveal(settings.apiKeyRef).orEmpty()
            services.weather.restart(viewModelScope, settings, key)
            refreshWeatherText()
        }
    }

    /**
     * Recompute the overlay string. Weather is decoration, so this only ever reads the
     * last snapshot — it never waits on the network and cannot delay a slide.
     */
    private fun refreshWeatherText() {
        val settings = lastSettings?.weather ?: return
        val display = WeatherPresentation.resolve(
            snapshot = services.weather.snapshot.value,
            nowEpochMs = System.currentTimeMillis(),
            maxStaleMs = settings.maxStaleMs,
            units = settings.units,
            staleAfterMs = settings.staleAfterMs,
        )
        val text = (display as? WeatherDisplay.Visible)?.text
        _state.update { it.copy(weatherText = if (settings.enabled) text else null) }
    }


    fun setNightBrightness(value: Float) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(schedule = it.schedule.copy(brightnessNight = value.coerceIn(0.01f, 1f)))
            }
        }
    }

    /**
     * Re-evaluate the quiet-hours window and drive the engine and screen brightness.
     *
     * The state is derived from the wall clock each time rather than tracked with a
     * running timer, so it is correct after a reboot, process death, or a device that
     * was suspended across the transition (spec §22.4). Waits are capped so a clock
     * change or timezone shift is picked up within the cap rather than at the next
     * scheduled boundary.
     */
    /**
     * Watches for the scheduled automatic index refresh (spec §20).
     *
     * Polls the wall clock rather than sleeping until the exact moment, for the same
     * reason as the sleep watcher: a timer does not survive a reboot or a suspended
     * device, whereas re-deriving "is it due?" from the clock and the last-run timestamp
     * is correct however the device behaved in between.
     */
    private fun rescanConfigSig(schedule: ScheduleSettings?): String =
        schedule?.let { "${it.autoRescanEnabled}|${it.autoRescanAt}|${it.autoRescanDays}" } ?: ""

    private fun restartRescanWatcher(schedule: ScheduleSettings) {
        rescanScheduleJob?.cancel()
        val days = RescanSchedule.parseDays(schedule.autoRescanDays)
        val at = SleepSchedule.parseMinutes(schedule.autoRescanAt)
        if (!schedule.autoRescanEnabled || at == null || days.isEmpty()) return

        rescanScheduleJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val due = RescanSchedule.isDue(
                    nowMinutes = nowMinutes(),
                    nowDayOfWeek = java.time.LocalDate.now().dayOfWeek.value,
                    atMinutes = at,
                    days = days,
                    // Read fresh rather than captured: the watcher writes this value, and
                    // a captured copy would go stale the moment it fired once.
                    minutesSinceLastRun = RescanSchedule.minutesSince(
                        services.settings.settings.first()
                            .schedule.lastAutoRescanAtEpochMs.takeIf { it > 0L },
                        now,
                    ),
                )
                if (due) {
                    diagnostics.log(
                        DiagnosticsLog.Category.SCAN, "AUTO_RESCAN_START",
                        "at" to schedule.autoRescanAt,
                        "days" to RescanSchedule.formatDays(days),
                    )
                    // Persist before scanning: if the scan crashes or the device dies
                    // mid-way, the slot is still consumed rather than retried in a loop.
                    services.settings.update {
                        it.copy(schedule = it.schedule.copy(lastAutoRescanAtEpochMs = now))
                    }
                    try {
                        requestSourceRefresh(
                            trigger = SourceRefreshTrigger.SCHEDULED_RESCAN,
                            origin = DiagnosticOrigin.SCHEDULER,
                            awaitCompletion = true,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        diagnostics.log(
                            DiagnosticsLog.Category.SCAN, "AUTO_RESCAN_FAILED",
                            "error" to error.javaClass.simpleName,
                        )
                    }
                    diagnostics.log(DiagnosticsLog.Category.SCAN, "AUTO_RESCAN_DONE")
                    // Step past this slot so the same window cannot re-trigger.
                    delay(RESCAN_SETTLE_MS)
                } else {
                    delay(RESCAN_POLL_MS)
                }
            }
        }
    }

    // ---- "On this day" memory interlude (docs/FPF-FEAT-ON-THIS-DAY-001.md) --------

    fun setOnThisDayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(onThisDay = it.onThisDay.copy(enabled = enabled)) }
        }
    }

    fun setOnThisDayTimesPerDay(timesPerDay: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(onThisDay = it.onThisDay.copy(timesPerDay = timesPerDay.coerceIn(1, 12)))
            }
        }
    }

    fun setOnThisDayDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(onThisDay = it.onThisDay.copy(durationMinutes = minutes.coerceIn(1, 60)))
            }
        }
    }

    fun setOnThisDayMinYearsAgo(years: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(onThisDay = it.onThisDay.copy(minYearsAgo = years.coerceIn(0, 100)))
            }
        }
    }

    /** Deliberately excludes lastTriggeredEpochMs — see rescanConfigSig's own comment. */
    private fun onThisDayConfigSig(cfg: OnThisDaySettings?): String =
        cfg?.let { "${it.enabled}|${it.timesPerDay}|${it.durationMinutes}|${it.minYearsAgo}" }.orEmpty()

    private fun restartOnThisDayWatcher(cfg: OnThisDaySettings) {
        onThisDayJob?.cancel()
        if (!cfg.enabled) return
        onThisDayJob = viewModelScope.launch {
            while (isActive) {
                runCatching { maybeAutoTriggerOnThisDay() }
                delay(ON_THIS_DAY_POLL_MS)
            }
        }
    }

    private suspend fun maybeAutoTriggerOnThisDay() {
        val current = services.settings.settings.first()
        val cfg = current.onThisDay
        if (!cfg.enabled) return
        // Quiet hours: the slideshow itself is paused, so firing now would just be
        // consumed silently — wait for the next window after waking instead (§5.5).
        if (_state.value.asleep) return
        // Never preempts an active override (§0.3) — skip this occurrence entirely
        // rather than deferring it; the next window gets its own chance.
        val playlists = current.playlists
        val overrideActive = playlists.manualOverrideUntilEpochMs == Long.MAX_VALUE ||
            playlists.manualOverrideUntilEpochMs > System.currentTimeMillis()
        if (overrideActive) return
        val due = OnThisDaySchedule.isDue(
            nowMinutes = nowMinutes(),
            timesPerDay = cfg.timesPerDay,
            minutesSinceLastTrigger = RescanSchedule.minutesSince(
                cfg.lastTriggeredEpochMs.takeIf { it > 0L },
                System.currentTimeMillis(),
            ),
        )
        if (!due) return
        triggerOnThisDay(preview = false)
    }

    /**
     * On-demand trigger for an eventual Settings/web "Preview now" button (Phase 4 of
     * docs/FPF-FEAT-ON-THIS-DAY-001.md) — bypasses the enabled/schedule checks so it
     * works even while the feature is off, matching [previewFolderOnce]'s calling
     * convention (fire-and-forget from a button, notice surfaced via [transientNotice]).
     */
    fun previewOnThisDay() {
        viewModelScope.launch {
            triggerOnThisDay(preview = true)?.let { message ->
                _state.update { it.copy(transientNotice = message) }
            }
        }
    }

    /**
     * Assembles today's memory pool and activates it via the same bounded playlist
     * override that manual playlist activation already uses (`playPlaylist`) — the
     * existing `restartPlaylistScheduleWatcher` loop automatically reverts once the
     * override expires, so no separate revert logic is needed here.
     *
     * @param preview true for the on-demand "Preview now" action, which bypasses the
     * enabled/schedule checks that already ran (or didn't) before this is called.
     * @return null on success, or a message explaining why nothing happened.
     */
    private suspend fun triggerOnThisDay(preview: Boolean): String? {
        val current = services.settings.settings.first()
        val cfg = current.onThisDay
        if (!preview && !cfg.enabled) return "On this day is disabled"
        val today = java.time.LocalDate.now()
        val monthDay = "%02d-%02d".format(today.monthValue, today.dayOfMonth)
        val candidates = services.photoDao.onThisDayCandidates(
            sourceIds = currentPlaybackSourceIds(),
            monthDay = monthDay,
            maxFailures = current.temporarilySuppressAfterDecodeFailures,
            allowHeif = if (services.allowHeifPlayback) 1 else 0,
            limit = ON_THIS_DAY_CANDIDATE_LIMIT,
        )
        val selected = OnThisDaySelection.select(
            candidates = candidates,
            currentYear = today.year,
            minYearsAgo = cfg.minYearsAgo,
            maxYears = ON_THIS_DAY_MAX_YEARS,
        )
        if (selected.isEmpty()) {
            diagnostics.log(DiagnosticsLog.Category.ENGINE, "ON_THIS_DAY_SKIPPED_EMPTY")
            return if (preview) "No memories match today's date yet" else null
        }
        engine.setOnThisDayPool(selected.map { it.id })
        val now = System.currentTimeMillis()
        services.settings.update { latest ->
            latest.copy(
                playlists = latest.playlists.copy(
                    activePlaylistId = PlaylistSettings.PLAYLIST_ON_THIS_DAY,
                    manualOverrideUntilEpochMs = now + cfg.durationMinutes.coerceIn(1, 60) * 60_000L,
                ),
                onThisDay = latest.onThisDay.copy(lastTriggeredEpochMs = now),
            )
        }
        val years = selected.map {
            java.time.Instant.ofEpochMilli(it.dateTakenEpochMs).atZone(java.time.ZoneId.systemDefault()).year
        }
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "ON_THIS_DAY_TRIGGERED",
            "years" to years.size.toString(),
            "preview" to preview.toString(),
        )
        _state.update {
            it.copy(transientNotice = "Showing memories from " + years.joinToString(", "))
        }
        return null
    }

    private fun restartBrightnessWatcher(automation: BrightnessAutomationSettings) {
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val now = nowMinutes()
                val decision = BrightnessPolicy.decide(automation, nowMs, now)
                val brightness = applyAmbientBrightness(decision.brightness, automation)
                val asleep = decision.action == NightAction.PAUSE_SLIDESHOW ||
                    decision.action == NightAction.BLACK_SCREEN
                engine.setAsleep(asleep)
                _state.update {
                    it.copy(
                        asleep = asleep,
                        screenBrightness = brightness,
                        activeBrightnessPeriodId = decision.periodId,
                        activeNightAction = decision.action,
                        temporaryWakeActive = decision.temporaryWake,
                        blackScreen = decision.action == NightAction.BLACK_SCREEN && !decision.temporaryWake,
                        ambientLux = ambientLux,
                        ambientSensorAvailable = ambientSensorAvailable,
                    )
                }
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE,
                    "BRIGHTNESS_LEVEL_APPLIED",
                    "level" to (brightness * 100f).toInt().coerceIn(1, 100).toString(),
                    "action" to decision.action.name,
                    "mode" to automation.mode.name,
                    "periodToken" to diagnosticToken(decision.periodId.orEmpty(), "period"),
                )
                delay(30_000L)
            }
        }
    }

    private fun applyAmbientBrightness(base: Float, settings: BrightnessAutomationSettings): Float {
        if (settings.mode != BrightnessMode.AMBIENT && settings.mode != BrightnessMode.SCHEDULED_AMBIENT) {
            return base.coerceIn(0.01f, 1f)
        }
        val lux = ambientLux ?: return base.coerceIn(settings.ambientMinimum, settings.ambientMaximum)
        // Logarithmic response: useful from a dark room through ordinary indoor light,
        // with a hard min/max so sensor noise cannot flash the wall frame.
        val normalized = (kotlin.math.ln(1f + lux.coerceAtLeast(0f)) / kotlin.math.ln(1001f))
            .coerceIn(0f, 1f)
        val ambient = settings.ambientMinimum +
            (settings.ambientMaximum - settings.ambientMinimum) * normalized
        return minOf(base, ambient).coerceIn(0.01f, 1f)
    }

    private fun nowMinutes(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
    }

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

    fun setBrightnessMode(mode: BrightnessMode) {
        viewModelScope.launch {
            services.settings.update { it.copy(brightnessAutomation = it.brightnessAutomation.copy(mode = mode)) }
        }
    }

    fun setManualBrightness(value: Float) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(brightnessAutomation = it.brightnessAutomation.copy(
                    manualBrightness = value.coerceIn(0.01f, 1f),
                ))
            }
        }
    }

    fun setBrightnessPeriod(index: Int, startTime: String, brightness: Float, action: NightAction) {
        val startMinutes = SleepSchedule.parseMinutes(startTime) ?: return
        val normalizedStart = SleepSchedule.formatMinutes(startMinutes)
        viewModelScope.launch {
            services.settings.update { current ->
                val periods = current.brightnessAutomation.periods.toMutableList()
                if (index !in periods.indices) return@update current
                periods[index] = periods[index].copy(
                    startTime = normalizedStart,
                    brightness = brightness.coerceIn(0.01f, 1f),
                    action = action,
                )
                current.copy(brightnessAutomation = current.brightnessAutomation.copy(periods = periods))
            }
        }
    }

    fun temporaryWake() {
        viewModelScope.launch {
            services.settings.update { current ->
                val minutes = current.brightnessAutomation.temporaryWakeMinutes.coerceIn(1, 240)
                current.copy(brightnessAutomation = current.brightnessAutomation.copy(
                    temporaryWakeUntilEpochMs = System.currentTimeMillis() + minutes * 60_000L,
                ))
            }
            diagnostics.log(DiagnosticsLog.Category.APP, "TEMPORARY_WAKE_STARTED")
        }
    }

    fun onAmbientLight(lux: Float?, available: Boolean) {
        ambientSensorAvailable = available
        if (lux != null && lux.isFinite()) {
            val previous = ambientLux
            ambientLux = if (previous == null) lux.coerceAtLeast(0f)
                else previous * 0.8f + lux.coerceAtLeast(0f) * 0.2f
        }
        _state.update { it.copy(ambientLux = ambientLux, ambientSensorAvailable = available) }
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
            services.photoDao.failedOrUnsupportedCount(currentMaxFailures(), heifFlag)
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
            if (shuffle.lastRecoveryEpochMs != null) {
                add("An interrupted shuffle reservation was recovered without introducing a duplicate.")
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

    fun setCachePlaybackPool(enabled: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(cachePlaybackPool = enabled) } }
    }

    fun setAutoStartOnBoot(value: Boolean) {
        viewModelScope.launch { services.settings.update { it.copy(autoStartOnBoot = value) } }
    }

    private fun updateOverlays(transform: (com.example.familyphotoframe.data.settings.OverlaySettings) -> com.example.familyphotoframe.data.settings.OverlaySettings) {
        viewModelScope.launch { services.settings.update { it.copy(overlays = transform(it.overlays)) } }
    }

    // ---- D-pad / transport ---------------------------------------------------

    fun setWebEnabled(value: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(web = it.web.copy(enabled = value)) }
        }
    }


    fun refreshRememberedBrowsers() {
        viewModelScope.launch {
            val records = runCatching { services.rememberedBrowsers.list() }.getOrDefault(emptyList())
            _state.update { current ->
                current.copy(
                    rememberedBrowserRecords = records.map { row ->
                        RememberedBrowserUi(
                            id = row.id,
                            label = row.label,
                            browserSummary = row.browserSummary.orEmpty(),
                            osSummary = row.osSummary.orEmpty(),
                            createdAtEpochMs = row.createdAtEpochMs,
                            lastUsedAtEpochMs = row.lastUsedAtEpochMs,
                            expiresAtEpochMs = row.expiresAtEpochMs,
                            revoked = row.revokedAtEpochMs != null,
                        )
                    }
                )
            }
        }
    }

    private fun updateRememberedPolicy(
        transform: (com.example.familyphotoframe.data.settings.RememberedBrowserPolicy) ->
            com.example.familyphotoframe.data.settings.RememberedBrowserPolicy,
    ) {
        viewModelScope.launch {
            val current = services.rememberedBrowsers.policy()
            services.rememberedBrowsers.updatePolicy(transform(current).normalized())
            refreshRememberedBrowsers()
        }
    }

    fun setRememberedBrowsersEnabled(enabled: Boolean) =
        updateRememberedPolicy { it.copy(enabled = enabled) }

    fun setRememberedAllowForever(allowed: Boolean) =
        updateRememberedPolicy { it.copy(allowForever = allowed) }

    fun setRememberedDefaultExpiry(mode: com.example.familyphotoframe.data.settings.RememberExpiryMode) =
        updateRememberedPolicy { it.copy(defaultExpiry = mode) }

    fun setRememberedMaximumBrowsers(count: Int) =
        updateRememberedPolicy { it.copy(maxRememberedBrowsers = count.coerceIn(1, 32)) }

    fun setRememberedMaximumExpiryDays(days: Int) =
        updateRememberedPolicy {
            it.copy(maxExpirySeconds = days.coerceIn(1, 3650).toLong() * 24L * 60L * 60L)
        }

    fun revokeRememberedBrowser(id: String) {
        viewModelScope.launch {
            if (services.rememberedBrowsers.revoke(id)) {
                services.webServer.revokeRememberedBrowserSessions(id)
            }
            refreshRememberedBrowsers()
        }
    }

    fun revokeAllRememberedBrowsers() {
        viewModelScope.launch {
            services.rememberedBrowsers.revokeAll()
            services.webServer.revokeAllWebSessions()
            refreshRememberedBrowsers()
        }
    }

    fun setWebPort(port: Int) {
        viewModelScope.launch {
            services.settings.update { it.copy(web = it.web.copy(port = port)) }
        }
    }

    /** Invalidate web sessions and show a new pairing PIN (spec §15.2). */
    fun regenerateWebPin(keepRememberedBrowsers: Boolean = false) {
        // The PBKDF2 derivation is deliberately expensive. This intent is invoked from a
        // Compose button, so never do that work (or remembered-browser Room work) inline on
        // the main thread. The CAS also closes the brief window before Compose disables the
        // button, so repeated taps produce one new PIN rather than a queue of derivations.
        if (!webPinRegenerationInFlight.compareAndSet(false, true)) return
        _state.update { it.copy(webPinRegenerationInProgress = true) }
        viewModelScope.launch {
            try {
                val generated = try {
                    withContext(services.dispatchers.default) {
                        services.webServer.regeneratePin(
                            revokeRememberedBrowsers = !keepRememberedBrowsers,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                refreshWebInfo()
                refreshRememberedBrowsers()
                if (generated == null && services.webServer.url() != null) {
                    _state.update {
                        it.copy(transientNotice = "Couldn’t generate a new web PIN.")
                    }
                }
            } finally {
                webPinRegenerationInFlight.set(false)
                _state.update { it.copy(webPinRegenerationInProgress = false) }
            }
        }
    }

    /** Pull the current PIN/URL from the server into UI state. */
    fun refreshWebInfo() {
        _state.update {
            it.copy(
                webPin = services.webServer.visiblePin(),
                webUrl = services.webServer.url(),
                webQrUrl = services.webServer.qrPairingUrl(),
            )
        }
    }

    // ---- Local thumbnail cache (docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md) ----

    private var localThumbnailRebuildJob: Job? = null

    fun setLocalThumbnailCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(localThumbnailCache = it.localThumbnailCache.copy(enabled = enabled))
            }
        }
    }

    fun setLocalThumbnailCacheMaxGiB(gib: Int) {
        val bytes = gib.coerceAtLeast(1).toLong() * 1024L * 1024L * 1024L
        viewModelScope.launch {
            services.settings.update {
                it.copy(localThumbnailCache = it.localThumbnailCache.copy(maxBytes = bytes))
            }
        }
    }

    /** Populates the usage/effective-max display fields; call when the settings card opens. */
    fun refreshLocalThumbnailCacheInfo() {
        viewModelScope.launch {
            val usage = services.localThumbnailCache.currentSizeBytes()
            val requested = services.settings.settings.first().localThumbnailCache.maxBytes
            val effectiveMax = com.example.familyphotoframe.data.cache.LocalThumbnailCache
                .clampMaxBytes(requested, appContext)
            _state.update {
                it.copy(
                    localThumbnailCacheUsageBytes = usage,
                    localThumbnailCacheEffectiveMaxBytes = effectiveMax,
                )
            }
        }
    }

    fun cleanLocalThumbnailCache() {
        localThumbnailRebuildJob?.cancel()
        viewModelScope.launch {
            services.localThumbnailCache.clear()
            refreshLocalThumbnailCacheInfo()
            _state.update { it.copy(transientNotice = "Local photo cache cleared") }
        }
    }

    /**
     * Clears the cache, then eagerly walks the local photo index and populates it —
     * unlike [cleanLocalThumbnailCache], which just clears and lets the cache repopulate
     * lazily as photos are shown again. Paged and yields between photos so it competes
     * as little as possible with live playback decode, and pauses entirely rather than
     * fights the existing memory-pressure circuit breaker for the same budget.
     */
    fun rebuildLocalThumbnailCache() {
        localThumbnailRebuildJob?.cancel()
        localThumbnailRebuildJob = viewModelScope.launch {
            services.localThumbnailCache.clear()
            services.localThumbnailCache.rebuildInProgress = true
            services.localThumbnailCache.rebuildCount = 0
            _state.update {
                it.copy(localThumbnailCacheRebuildInProgress = true, localThumbnailCacheRebuildCount = 0)
            }
            try {
                val metrics = appContext.resources.displayMetrics
                val targetW = metrics.widthPixels.coerceAtLeast(1)
                val targetH = metrics.heightPixels.coerceAtLeast(1)
                var offset = 0
                var populated = 0
                while (isActive) {
                    if (!memoryProtection.value.allowNextPreload) {
                        delay(2_000)
                        continue
                    }
                    val batch = services.photoDao.localThumbnailRebuildCandidates(limit = 25, offset = offset)
                    if (batch.isEmpty()) break
                    offset += batch.size
                    for (candidate in batch) {
                        ensureActive()
                        if (!memoryProtection.value.allowNextPreload) break
                        val protectedKeys = setOfNotNull(
                            _state.value.engine.current?.stableId,
                            _state.value.engine.next?.stableId,
                        )
                        val model: Any = if (candidate.openToken.startsWith("content://")) {
                            Uri.parse(candidate.openToken)
                        } else {
                            File(candidate.openToken)
                        }
                        val bitmap = decodeForThumbnailRebuild(model, targetW, targetH)
                        if (bitmap != null) {
                            try {
                                services.localThumbnailCache.put(
                                    candidate.stableId, targetW, targetH, bitmap, protectedKeys,
                                )
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                            populated++
                            services.localThumbnailCache.rebuildCount = populated
                            _state.update { it.copy(localThumbnailCacheRebuildCount = populated) }
                        }
                        yield()
                    }
                }
            } finally {
                services.localThumbnailCache.rebuildInProgress = false
                _state.update { it.copy(localThumbnailCacheRebuildInProgress = false) }
                refreshLocalThumbnailCacheInfo()
            }
        }
    }

    private suspend fun decodeForThumbnailRebuild(model: Any, targetW: Int, targetH: Int): Bitmap? = try {
        val request = coil.request.ImageRequest.Builder(appContext)
            .data(model)
            .size(targetW, targetH)
            .allowHardware(false)
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            .build()
        when (val result = services.imageLoader.execute(request)) {
            is coil.request.SuccessResult ->
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            else -> null
        }
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }

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
            recoverFromDecodeOom(failure)
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
        if (sourceId !in activeRemoteSources) return
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

    fun onCollageCandidateFailure(failure: DecodeFailure) {
        if (failure.exceptionClass == "OutOfMemoryError") {
            // Do not count a healthy candidate as failed or consume its shuffle
            // reservation merely because the process heap was exhausted.
            recoverFromDecodeOom(failure)
            return
        }
        engine.reportCollageCandidateFailure(failure)
    }

    private fun recoverFromDecodeOom(failure: DecodeFailure) {
        if (failure.exceptionClass != "OutOfMemoryError") return
        recoverFromPresentationOom(failure.photoId, failure.reason ?: "decode_allocation")
    }

    fun onRecoverablePresentationOom(photo: DisplayPhoto, reason: String) {
        recoverFromPresentationOom(photo.id, reason)
    }

    private fun recoverFromPresentationOom(photoId: Long, trigger: String) {
        val previousProtection = services.playbackMemoryGuard.snapshot()
        val protection = services.playbackMemoryGuard.recordDecodeOom(SystemClock.elapsedRealtime())
        services.diagnosticRuntimeState.updateBitmapInventory(
            services.diagnosticRuntimeState.snapshot().bitmaps.copy(
                memoryProtectionLevel = protection.level.name,
                oomCount = protection.totalOomCount,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )
        val cache = services.imageLoader.memoryCache
        val before = cache?.size ?: 0
        cache?.clear()
        services.webServer.clearPreview()
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY,
            "DECODE_OOM_RECOVERY",
            "trigger" to trigger,
            "presentationToken" to diagnosticToken(photoId.toString(), "presentation"),
            "imageCacheBeforeKb" to (before / 1024L).toString(),
            "webPreviewCleared" to "true",
            "memoryProtectionLevel" to protection.level.name,
            "pressurePercent" to protection.pressurePercent.toString(),
            "circuitOpenMs" to protection.circuitRemainingMs(SystemClock.elapsedRealtime()).toString(),
            "preloadAllowed" to protection.allowNextPreload.toString(),
            "maxCollagePhotos" to protection.maxCollagePhotos.toString(),
            "targetScalePercent" to (protection.decodeScale * 100f).toInt().toString(),
            "oomCount" to protection.totalOomCount.toString(),
        )
        if (protection.level != previousProtection.level ||
            protection.decisionVersion != previousProtection.decisionVersion
        ) {
            services.diagnostics.log(
                DiagnosticsLog.Category.MEMORY,
                "MEMORY_PROTECTION_CHANGED",
                "trigger" to "decode_oom",
                "previousLevel" to previousProtection.level.name,
                "memoryProtectionLevel" to protection.level.name,
                "pressurePercent" to protection.pressurePercent.toString(),
                "preloadAllowed" to protection.allowNextPreload.toString(),
                "maxCollagePhotos" to protection.maxCollagePhotos.toString(),
                "targetScalePercent" to (protection.decodeScale * 100f).toInt().toString(),
                "oomCount" to protection.totalOomCount.toString(),
            )
        }
    }

    internal fun onBitmapInventory(
        inventory: PreparedBitmapInventory,
        protection: com.example.familyphotoframe.domain.engine.PlaybackMemoryState,
    ) {
        services.diagnosticRuntimeState.updateBitmapInventory(
            com.example.familyphotoframe.data.diagnostics.DiagnosticRuntimeState.BitmapInventory(
                preparedSlideCount = inventory.preparedSlideCount,
                renderedSlideCount = inventory.renderedSlideCount,
                decodedBitmapCount = inventory.decodedBitmapCount,
                appBitmapCount = inventory.appBitmapCount,
                activeDecodedBytes = inventory.activeDecodedBytes,
                pendingDisposals = inventory.pendingDisposals,
                memoryProtectionLevel = protection.level.name,
                oomCount = protection.totalOomCount,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    fun onMemoryCleanup(pendingDisposals: Int, heapBeforeKb: Long, heapAfterKb: Long) {
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY,
            "MEMORY_CLEANUP_REQUESTED",
            "trigger" to "decode_circuit_open",
            "pendingDisposals" to pendingDisposals.toString(),
            "gcRequested" to "true",
            "heapBeforeKb" to heapBeforeKb.toString(),
            "heapAfterKb" to heapAfterKb.toString(),
            "freedKb" to (heapBeforeKb - heapAfterKb).coerceAtLeast(0L).toString(),
            "memoryProtectionLevel" to services.playbackMemoryGuard.snapshot().level.name,
        )
    }
    suspend fun onPrepared(
        anchorId: Long,
        memberIds: List<Long>,
        layout: String?,
    ): Boolean = engine.commitPrepared(anchorId, memberIds, layout)

    fun onRendered(
        anchorId: Long,
        memberIds: List<Long>,
        dataSources: List<String?>,
        layout: String?,
    ) {
        engine.reportRendered(anchorId, memberIds, dataSources, layout)
        services.diagnosticRuntimeState.updatePlayback { current ->
            current.copy(
                presentationToken = diagnosticToken(anchorId.toString(), "presentation"),
                layout = layout ?: if (memberIds.size <= 1) "SINGLE" else "COLLAGE_${memberIds.size}",
            )
        }
    }

    fun shouldGenerateWebPreview(): Boolean = services.webServer.shouldGeneratePreview()

    fun onWebPreviewReady(frame: WebPreviewFrame) {
        services.webServer.publishPreview(frame)
    }

    fun onTransitionEvent(event: TransitionEvent) {
        services.diagnosticRuntimeState.updatePlayback { current ->
            current.copy(transitionCode = event.resolvedEffect.ifBlank { event.configuredEffect })
        }
        services.diagnostics.log(
            DiagnosticsLog.Category.ENGINE, event.code,
            "configuredMode" to event.configuredMode,
            "configuredEffect" to event.configuredEffect,
            "resolvedEffect" to event.resolvedEffect,
            "outgoingId" to (event.outgoingId?.toString() ?: ""),
            "incomingId" to event.incomingId.toString(),
            "durationMs" to event.durationMs.toString(),
            "actualDurationMs" to event.actualDurationMs?.toString().orEmpty(),
            "direction" to event.direction,
            "reason" to event.reason.orEmpty(),
            "fallbackUsed" to event.fallbackUsed.toString(),
            "frameCount" to event.frameCount?.toString().orEmpty(),
            "slowFrameCount" to event.slowFrameCount?.toString().orEmpty(),
            "maximumFrameMs" to event.maximumFrameMs?.toString().orEmpty(),
            "startLatencyMs" to event.startLatencyMs?.toString().orEmpty(),
            "preparedSlideCount" to event.preparedSlideCount?.toString().orEmpty(),
            "activeDecodedBytes" to event.activeDecodedBytes?.toString().orEmpty(),
        )
    }

    private fun publishDiagnosticPlayback(
        state: SlideshowUiState,
        layoutOverride: String? = null,
        transitionOverride: String? = null,
    ) {
        val currentPhoto = state.engine.current
        services.diagnosticRuntimeState.updatePlayback { previous ->
            previous.copy(
                surface = when (state.surface) {
                    Surface.Loading -> "LOADING"
                    Surface.FirstRun -> "FIRST_RUN"
                    is Surface.Recovery -> "RECOVERY"
                    Surface.EmptyIndex -> "EMPTY_INDEX"
                    Surface.Playing -> if (state.blackScreen) "BLACK_SCREEN" else "PLAYING"
                },
                engineState = state.engine.state.name,
                presentationToken = currentPhoto?.let {
                    diagnosticToken(it.stableId.ifBlank { it.id.toString() }, "presentation")
                }.orEmpty(),
                sourceKind = currentPhoto?.sourceId?.let(::diagnosticSourceKind) ?: "NONE",
                layout = layoutOverride ?: previous.layout,
                transitionCode = transitionOverride ?: previous.transitionCode,
            )
        }
    }

    fun onCollageEvent(
        event: String,
        anchorId: Long,
        photoIds: List<Long> = emptyList(),
        layout: String? = null,
        prepareMs: Long? = null,
        decodedBytes: Long? = null,
        reason: String? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        val fields = mapOf(
            "anchorToken" to diagnosticToken(anchorId.toString(), "photo"),
            "presentationToken" to diagnosticToken(photoIds.joinToString(","), "presentation"),
            "members" to photoIds.size.toString(),
            "layout" to layout.orEmpty(),
            "durationMs" to prepareMs?.toString().orEmpty(),
            "sizeBytes" to decodedBytes?.toString().orEmpty(),
            "reason" to reason.orEmpty(),
        ) + details
        services.diagnostics.log(DiagnosticsLog.Category.ENGINE, event, "", fields)
    }

    fun clearTransientNotice() = _state.update { it.copy(transientNotice = null) }

    private companion object {
        /**
         * Poll interval for the rescan schedule. One minute matches the granularity of a
         * "HH:mm" setting; anything finer would burn wakeups for no visible benefit.
         */
        const val RESCAN_POLL_MS = 60_000L

        /** Step clear of the slot just served so it cannot re-trigger. */
        const val RESCAN_SETTLE_MS = 5L * 60_000L

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

        /** "On this day" windows are short and frequent — poll more often than rescan. */
        const val ON_THIS_DAY_POLL_MS = 30_000L
        /** Safety cap on rows fetched before year-grouping; a real day rarely nears this. */
        const val ON_THIS_DAY_CANDIDATE_LIMIT = 500
        /** Matches docs/FPF-FEAT-ON-THIS-DAY-001.md §9's proposed default. */
        const val ON_THIS_DAY_MAX_YEARS = 6
        /** Cap on a single wait so clock/timezone changes are noticed promptly. */
        const val MAX_SCHEDULE_WAIT_MINUTES = 15
        const val WEATHER_RECHECK_MS = 5 * 60_000L
        const val INITIAL_REMOTE_NETWORK_WAIT_MS = 60_000L
        const val INITIAL_REMOTE_NETWORK_POLL_MS = 500L
        const val WEATHER_KEY_REF = CredentialPolicy.WEATHER_API_KEY_REF
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
