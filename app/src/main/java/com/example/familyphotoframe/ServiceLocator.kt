package com.example.familyphotoframe

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import coil.ImageLoader
import coil.memory.MemoryCache
import com.example.familyphotoframe.data.cache.LocalThumbnailCache
import com.example.familyphotoframe.data.cache.MediaCache
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.DiagnosticIdentityHasher
import com.example.familyphotoframe.data.diagnostics.DiagnosticIdentityKeyStore
import com.example.familyphotoframe.data.diagnostics.DiagnosticRuntimeState
import com.example.familyphotoframe.data.diagnostics.FileDiagnosticsSink
import com.example.familyphotoframe.data.diagnostics.PersistentRuntimeBreadcrumbs
import com.example.familyphotoframe.data.diagnostics.ProcfsResourceSampler
import com.example.familyphotoframe.data.diagnostics.RuntimeResourceTracker
import com.example.familyphotoframe.data.diagnostics.RuntimeSampler
import com.example.familyphotoframe.data.diagnostics.SharedPreferencesRuntimeBreadcrumbStorage
import com.example.familyphotoframe.data.diagnostics.BatteryTelemetry
import com.example.familyphotoframe.data.diagnostics.BitmapLifecycleTracker
import com.example.familyphotoframe.data.index.ContentHashBackfiller
import com.example.familyphotoframe.data.index.ExifBackfiller
import com.example.familyphotoframe.data.index.Indexer
import com.example.familyphotoframe.data.secret.KeystoreSecretStore
import com.example.familyphotoframe.data.settings.SettingsRepository
import com.example.familyphotoframe.data.source.AppPrivateFallbackSource
import com.example.familyphotoframe.data.source.LocalUploadPhotoSource
import com.example.familyphotoframe.data.source.BuiltInSourceIds
import com.example.familyphotoframe.data.source.SafPhotoSource
import com.example.familyphotoframe.data.source.SmbConnection
import com.example.familyphotoframe.data.source.SmbCredentials
import com.example.familyphotoframe.data.source.SmbPhotoSource
import com.example.familyphotoframe.data.source.SourceId
import com.example.familyphotoframe.data.source.SynologyConnection
import com.example.familyphotoframe.data.source.WebDavConnection
import com.example.familyphotoframe.data.source.WebDavCredentials
import com.example.familyphotoframe.data.source.WebDavPhotoSource
import com.example.familyphotoframe.data.source.SynologyCredentials
import com.example.familyphotoframe.data.source.SynologyFileStationSource
import com.example.familyphotoframe.domain.engine.SlideshowEngine
import com.example.familyphotoframe.domain.engine.PlaybackMemoryGuard
import com.example.familyphotoframe.slideshow.shuffle.FolderBalancedShuffleCoordinator
import com.example.familyphotoframe.slideshow.shuffle.ShuffleEligibilityProvider
import com.example.familyphotoframe.slideshow.shuffle.ShuffleRepository
import com.example.familyphotoframe.data.weather.OpenMeteoProvider
import com.example.familyphotoframe.data.weather.WeatherRepository
import com.example.familyphotoframe.web.WebServerController
import com.example.familyphotoframe.web.RememberedBrowserManager
import com.example.familyphotoframe.web.WebUploadManager
import com.example.familyphotoframe.maintenance.FactoryResetCoordinator
import com.example.familyphotoframe.util.AppDispatchers
import com.example.familyphotoframe.util.DefaultAppDispatchers
import com.example.familyphotoframe.domain.engine.DeviceMemoryTier
import com.example.familyphotoframe.domain.engine.DeviceMemoryTierPolicy
import com.example.familyphotoframe.util.ImageFormatSupport
import com.example.familyphotoframe.util.ImageMemoryBudget
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * Manual dependency container (spec §3: "Manual DI for Phase 0–1; Hilt later").
 * One instance lives on the [App]; everything is constructed lazily and shared.
 * Keeping wiring explicit here means there is exactly one place that decides how
 * sources, the index and the engine are connected.
 *
 * Source identity is fixed and stable so Room rows and engine selection always
 * agree (the engine's activeSourceId must equal the sourceId the indexer wrote):
 *  - SAF folder  -> "local_saf"
 *  - bundled demo -> "fallback"
 */
class ServiceLocator(private val appContext: Context) {

    val dispatchers: AppDispatchers = DefaultAppDispatchers

    private val activityManager: ActivityManager? by lazy {
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    /** Runtime playback capability; indexing remains device-independent. */
    val allowHeifPlayback: Boolean = ImageFormatSupport.supportsPlatformHeif(Build.VERSION.SDK_INT)

    /**
     * Decided once from signals that are all available before the first decode, so an
     * old tablet starts economical instead of discovering it should have been. Read by
     * the image cache budget, the diagnostics ring and the slideshow's decode sizing.
     */
    val memoryTier: DeviceMemoryTier by lazy {
        val totalRamBytes = activityManager?.let { manager ->
            runCatching {
                ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
            }.getOrDefault(0L)
        } ?: 0L
        DeviceMemoryTierPolicy.tier(
            lowRamFlagged = activityManager?.isLowRamDevice == true,
            heapMaxBytes = Runtime.getRuntime().maxMemory(),
            totalRamBytes = totalRamBytes,
        )
    }

    val diagnostics: DiagnosticsLog by lazy {
        DiagnosticsLog(capacity = DeviceMemoryTierPolicy.diagnosticsRingCapacity(memoryTier))
    }

    /** Lock-free crash/ANR context populated by normal runtime owners. */
    val diagnosticRuntimeState: DiagnosticRuntimeState = DiagnosticRuntimeState()

    /**
     * Process-wide memory guard shared by Application and slideshow UI. The PSS budget uses the
     * ordinary memory class rather than `Runtime.maxMemory()`, so an optional large heap cannot
     * make a 100 MiB-class API-22 device look roomy.
     */
    val playbackMemoryGuard: PlaybackMemoryGuard by lazy {
        val ordinaryBudgetBytes = activityManager?.memoryClass
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(BYTES_PER_MIB)
            ?: Runtime.getRuntime().maxMemory().coerceAtLeast(0L)
        PlaybackMemoryGuard(
            lowMemoryTier = memoryTier.isLow,
            processMemoryBudgetBytes = ordinaryBudgetBytes,
        )
    }

    /** Native/provider ownership counters shared by SMB, cache and runtime sampling. */
    val runtimeResourceTracker: RuntimeResourceTracker = RuntimeResourceTracker()

    /** Bitmap lifetime counters. This tracker retains no Bitmap references. */
    val bitmapLifecycleTracker: BitmapLifecycleTracker = BitmapLifecycleTracker()

    private val procfsResourceSampler: ProcfsResourceSampler = ProcfsResourceSampler()

    /** Last presentation stage retained across an abrupt same-boot process restart. */
    val runtimeBreadcrumbs: PersistentRuntimeBreadcrumbs = PersistentRuntimeBreadcrumbs(
        SharedPreferencesRuntimeBreadcrumbStorage(appContext),
    )

    /**
     * Durable diagnostics file, in app-private storage so no permission is needed and
     * nothing is world-readable. Attached to [diagnostics] by [App] at startup.
     */
    val diagnosticsSink: FileDiagnosticsSink by lazy {
        FileDiagnosticsSink(File(appContext.filesDir, "diagnostics"))
    }

    /**
     * High-volume slide stream, kept apart from the evidence stream above so it can never
     * rotate the gate evidence away. Given a larger budget because it is the only stream
     * that realistically ages out during a long run.
     */
    val diagnosticsBulkSink: FileDiagnosticsSink by lazy {
        FileDiagnosticsSink(
            File(appContext.filesDir, "diagnostics-slides"),
            maxBytes = 4L * 1024 * 1024,
            keepGenerations = 2,
        )
    }

    private val batteryTelemetry: BatteryTelemetry by lazy { BatteryTelemetry(appContext) }

    val runtimeSampler: RuntimeSampler by lazy {
        RuntimeSampler(
            diagnostics = diagnostics,
            extraFields = {
                val cache = imageLoader.memoryCache
                val runtime = Runtime.getRuntime()
                val processMemory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                val pssKb = processMemory.totalPss.toLong().coerceAtLeast(0L)
                val dalvikPssKb = processMemory.dalvikPss.toLong().coerceAtLeast(0L)
                val nativePssKb = processMemory.nativePss.toLong().coerceAtLeast(0L)
                val otherPssKb = processMemory.otherPss.toLong().coerceAtLeast(0L)
                val nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L
                val imageCacheKb = (cache?.size ?: 0).toLong() / 1024L
                val bitmapInventory = diagnosticRuntimeState.snapshot().bitmaps
                val memoryProtection = playbackMemoryGuard.snapshot()
                val procfs = procfsResourceSampler.sample()
                val resources = runtimeResourceTracker.snapshot()
                val bitmapLifecycle = bitmapLifecycleTracker.snapshot()
                val sampleElapsedMs = android.os.SystemClock.elapsedRealtime()
                val oldestPendingDisposalAgeMs = bitmapInventory
                    .oldestPendingDisposalStartedElapsedMs
                    .takeIf { bitmapInventory.pendingDisposals > 0 && it > 0L }
                    ?.let { (sampleElapsedMs - it).coerceAtLeast(0L) }
                    ?: 0L
                val systemMemory = activityManager?.let { manager ->
                    runCatching { ActivityManager.MemoryInfo().also(manager::getMemoryInfo) }.getOrNull()
                }
                val processBudgetBytes = memoryProtection.processMemoryBudgetBytes
                val sampledProcessPressurePercent = diagnosticPercent(
                    numerator = pssKb * 1024L,
                    denominator = processBudgetBytes,
                )
                val sampledSystemHeadroomPercent = diagnosticPercent(
                    numerator = systemMemory?.availMem ?: 0L,
                    denominator = systemMemory?.threshold ?: 0L,
                )
                diagnosticRuntimeState.updateMemory(
                    DiagnosticRuntimeState.Memory(
                        heapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
                        heapMaxKb = runtime.maxMemory() / 1024L,
                        nativeHeapKb = nativeHeapKb,
                        pssKb = pssKb,
                        imageCacheKb = imageCacheKb,
                        sampledAtEpochMs = System.currentTimeMillis(),
                        dalvikPssKb = dalvikPssKb,
                        nativePssKb = nativePssKb,
                        otherPssKb = otherPssKb,
                        systemAvailMemKb = systemMemory?.availMem?.div(1024L) ?: 0L,
                        systemThresholdKb = systemMemory?.threshold?.div(1024L) ?: 0L,
                        systemLowMemory = systemMemory?.lowMemory == true,
                        openFdCount = procfs.openFileDescriptorCount ?: -1,
                        threadCount = procfs.threadCount ?: -1,
                        activeSmbContexts = resources.activeSmbContexts,
                        oldestSmbContextAgeMs = resources.oldestSmbContextAgeMs,
                        peakSmbContexts = resources.peakSmbContexts,
                        smbContextsCreated = resources.smbContextsCreated,
                        smbContextsClosed = resources.smbContextsClosed,
                        smbContextTrackingSaturated = resources.smbContextTrackingSaturated,
                        activeSmbStreams = resources.activeSmbStreams,
                        activeMediaTransfers = resources.activeMediaTransfers,
                        oldestSmbStreamAgeMs = resources.oldestSmbStreamAgeMs,
                        oldestMediaTransferAgeMs = resources.oldestMediaTransferAgeMs,
                        peakSmbStreams = resources.peakSmbStreams,
                        smbStreamsOpened = resources.smbStreamsOpened,
                        smbStreamsClosed = resources.smbStreamsClosed,
                        smbTrackingSaturated = resources.smbTrackingSaturated,
                        peakMediaTransfers = resources.peakMediaTransfers,
                        mediaTransfersStarted = resources.mediaTransfersStarted,
                        mediaTransfersFinished = resources.mediaTransfersFinished,
                        mediaTrackingSaturated = resources.mediaTrackingSaturated,
                        bitmapTrackedAllocations = bitmapLifecycle.allocations,
                        bitmapTrackedReleases = bitmapLifecycle.releases,
                        bitmapTrackedAllocatedBytes = bitmapLifecycle.allocatedBytes,
                        bitmapTrackedReleasedBytes = bitmapLifecycle.releasedBytes,
                        bitmapTrackedActiveCount = bitmapLifecycle.activeCount,
                        bitmapTrackedActiveBytes = bitmapLifecycle.activeBytes,
                        bitmapTrackedPeakCount = bitmapLifecycle.peakActiveCount,
                        bitmapTrackedPeakBytes = bitmapLifecycle.peakActiveBytes,
                        bitmapDecodedAllocations = bitmapLifecycle.decodedAllocations,
                        bitmapDecodedActiveCount = bitmapLifecycle.decodedActiveCount,
                        bitmapDecodedActiveBytes = bitmapLifecycle.decodedActiveBytes,
                        bitmapGeneratedAllocations = bitmapLifecycle.generatedAllocations,
                        bitmapGeneratedActiveCount = bitmapLifecycle.generatedActiveCount,
                        bitmapGeneratedActiveBytes = bitmapLifecycle.generatedActiveBytes,
                        bitmapTemporaryAllocations = bitmapLifecycle.temporaryAllocations,
                        bitmapTemporaryActiveCount = bitmapLifecycle.temporaryActiveCount,
                        bitmapTemporaryActiveBytes = bitmapLifecycle.temporaryActiveBytes,
                        bitmapReleaseUnderflowCount = bitmapLifecycle.releaseUnderflowCount,
                    )
                )
                linkedMapOf(
                    "pssKb" to pssKb.toString(),
                    "dalvikPssKb" to dalvikPssKb.toString(),
                    "nativePssKb" to nativePssKb.toString(),
                    "otherPssKb" to otherPssKb.toString(),
                    "nativeHeapKb" to nativeHeapKb.toString(),
                    "imageCacheKb" to imageCacheKb.toString(),
                    "imageCacheMaxKb" to ((cache?.maxSize ?: 0).toLong() / 1024L).toString(),
                    "preparedSlideCount" to bitmapInventory.preparedSlideCount.toString(),
                    "renderedSlideCount" to bitmapInventory.renderedSlideCount.toString(),
                    "decodedBitmapCount" to bitmapInventory.decodedBitmapCount.toString(),
                    "appBitmapCount" to bitmapInventory.appBitmapCount.toString(),
                    "activeDecodedBytes" to bitmapInventory.activeDecodedBytes.toString(),
                    "pendingDisposals" to bitmapInventory.pendingDisposals.toString(),
                    "oldestPendingDisposalAgeMs" to
                        oldestPendingDisposalAgeMs.toString(),
                    "memoryProtectionLevel" to memoryProtection.level.name,
                    "pressurePercent" to memoryProtection.pressurePercent.toString(),
                    "processMemoryBudgetKb" to (processBudgetBytes / 1024L).toString(),
                    "processPressurePercent" to sampledProcessPressurePercent.toString(),
                    "systemHeadroomPercent" to sampledSystemHeadroomPercent.toString(),
                    "memoryPressureSource" to memoryProtection.pressureSource.name,
                    "economyBaseline" to memoryProtection.lowMemoryTier.toString(),
                    "nativeGrowthKb" to (memoryProtection.nativeGrowthBytes / 1024L).toString(),
                    "nativeGrowthRateKbPerMin" to
                        memoryProtection.nativeGrowthRateKbPerMin.toString(),
                    "nativeGrowthStreak" to memoryProtection.nativeGrowthStreak.toString(),
                    "renderTimeoutWindowCount" to
                        memoryProtection.renderTimeoutWindowCount.toString(),
                    "renderTimeoutTotal" to memoryProtection.totalRenderTimeoutCount.toString(),
                    "externalCriticalRemainingMs" to
                        memoryProtection.externalCriticalRemainingMs(sampleElapsedMs).toString(),
                    "externalGuardedRemainingMs" to
                        memoryProtection.externalGuardedRemainingMs(sampleElapsedMs).toString(),
                    "oomCount" to memoryProtection.totalOomCount.toString(),
                    "smbActiveContexts" to resources.activeSmbContexts.toString(),
                    "smbPeakContexts" to resources.peakSmbContexts.toString(),
                    "smbContextsCreated" to resources.smbContextsCreated.toString(),
                    "smbContextsClosed" to resources.smbContextsClosed.toString(),
                    "smbOldestContextAgeMs" to resources.oldestSmbContextAgeMs.toString(),
                    "smbContextTrackingSaturated" to
                        resources.smbContextTrackingSaturated.toString(),
                    "smbActiveStreams" to resources.activeSmbStreams.toString(),
                    "smbPeakStreams" to resources.peakSmbStreams.toString(),
                    "smbStreamsOpened" to resources.smbStreamsOpened.toString(),
                    "smbStreamsClosed" to resources.smbStreamsClosed.toString(),
                    "smbOldestStreamAgeMs" to resources.oldestSmbStreamAgeMs.toString(),
                    "smbTrackingSaturated" to resources.smbTrackingSaturated.toString(),
                    "mediaActiveTransfers" to resources.activeMediaTransfers.toString(),
                    "mediaPeakTransfers" to resources.peakMediaTransfers.toString(),
                    "mediaTransfersStarted" to resources.mediaTransfersStarted.toString(),
                    "mediaTransfersFinished" to resources.mediaTransfersFinished.toString(),
                    "mediaOldestTransferAgeMs" to resources.oldestMediaTransferAgeMs.toString(),
                    "mediaTrackingSaturated" to resources.mediaTrackingSaturated.toString(),
                    "bitmapTrackedAllocations" to bitmapLifecycle.allocations.toString(),
                    "bitmapTrackedReleases" to bitmapLifecycle.releases.toString(),
                    "bitmapTrackedAllocatedBytes" to bitmapLifecycle.allocatedBytes.toString(),
                    "bitmapTrackedReleasedBytes" to bitmapLifecycle.releasedBytes.toString(),
                    "bitmapTrackedActiveCount" to bitmapLifecycle.activeCount.toString(),
                    "bitmapTrackedActiveBytes" to bitmapLifecycle.activeBytes.toString(),
                    "bitmapTrackedPeakCount" to bitmapLifecycle.peakActiveCount.toString(),
                    "bitmapTrackedPeakBytes" to bitmapLifecycle.peakActiveBytes.toString(),
                    "bitmapDecodedAllocations" to bitmapLifecycle.decodedAllocations.toString(),
                    "bitmapDecodedActiveCount" to bitmapLifecycle.decodedActiveCount.toString(),
                    "bitmapDecodedActiveBytes" to bitmapLifecycle.decodedActiveBytes.toString(),
                    "bitmapGeneratedAllocations" to bitmapLifecycle.generatedAllocations.toString(),
                    "bitmapGeneratedActiveCount" to bitmapLifecycle.generatedActiveCount.toString(),
                    "bitmapGeneratedActiveBytes" to bitmapLifecycle.generatedActiveBytes.toString(),
                    "bitmapTemporaryAllocations" to bitmapLifecycle.temporaryAllocations.toString(),
                    "bitmapTemporaryActiveCount" to bitmapLifecycle.temporaryActiveCount.toString(),
                    "bitmapTemporaryActiveBytes" to bitmapLifecycle.temporaryActiveBytes.toString(),
                    "bitmapReleaseUnderflowCount" to
                        bitmapLifecycle.releaseUnderflowCount.toString(),
                ).apply {
                    procfs.openFileDescriptorCount?.let { put("openFdCount", it.toString()) }
                    procfs.threadCount?.let { put("threadCount", it.toString()) }
                    systemMemory?.let {
                        put("systemAvailMemKb", (it.availMem / 1024L).toString())
                        put("systemThresholdKb", (it.threshold / 1024L).toString())
                        put("systemLowMemory", it.lowMemory.toString())
                    }
                    putAll(batteryTelemetry.fields())
                }
            },
        )
    }

    private val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val photoDao: PhotoDao by lazy { database.photoDao() }

    val secretStore: KeystoreSecretStore by lazy {
        KeystoreSecretStore(appContext, database.secretDao(), dispatchers.io)
    }

    val rememberedBrowsers: RememberedBrowserManager by lazy {
        RememberedBrowserManager(
            database.rememberedBrowserDao(), secretStore, settings, diagnostics,
        )
    }

    val mediaCache: MediaCache by lazy {
        MediaCache(
            appContext, database.cacheIndexDao(), dispatchers.io,
            // Adapter rather than making PhotoDao implement the interface directly:
            // Room generates the DAO, and the cache should only be handed the three
            // writes it actually needs rather than the whole photo index.
            photoIndex = object : MediaCache.PhotoCacheIndexWriter {
                override suspend fun setCacheKey(stableId: String, cacheKey: String?) =
                    photoDao.setCacheKey(stableId, cacheKey)

                override suspend fun clearCacheKey(cacheKey: String) =
                    photoDao.clearCacheKey(cacheKey)

                override suspend fun clearAllCacheKeys() = photoDao.clearAllCacheKeys()
            },
            resourceTracker = runtimeResourceTracker,
        )
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** Persistent thumbnail cache for local (SAF/fallback) photos; see docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md. */
    val localThumbnailCache: LocalThumbnailCache by lazy {
        LocalThumbnailCache(
            appContext, database.localThumbnailCacheDao(), dispatchers.io,
            enabledProvider = { settings.settings.first().localThumbnailCache.enabled },
            maxBytesProvider = {
                LocalThumbnailCache.clampMaxBytes(
                    settings.settings.first().localThumbnailCache.maxBytes,
                    appContext,
                    database.localThumbnailCacheDao().totalSizeBytes(),
                )
            },
        )
    }

    val indexer: Indexer by lazy { Indexer(photoDao, diagnostics) }

    /** Fills in EXIF for photos indexed without it (remote sources); see ExifScanPolicy. */
    val exifBackfiller: ExifBackfiller by lazy { ExifBackfiller(photoDao, diagnostics) }

    /** Exact duplicate identity, populated asynchronously and never on the render path. */
    val contentHashBackfiller: ContentHashBackfiller by lazy {
        ContentHashBackfiller(photoDao, diagnostics, dispatchers.io)
    }

    val shuffleRepository: ShuffleRepository by lazy {
        ShuffleRepository(database, diagnostics)
    }

    val shuffleEligibilityProvider: ShuffleEligibilityProvider by lazy {
        ShuffleEligibilityProvider(photoDao, allowHeifPlayback)
    }

    val folderBalancedShuffle: FolderBalancedShuffleCoordinator by lazy {
        FolderBalancedShuffleCoordinator(shuffleRepository, shuffleEligibilityProvider)
    }

    val engine: SlideshowEngine by lazy {
        SlideshowEngine(
            photoDao,
            diagnostics,
            folderBalancedShuffle,
            allowHeif = allowHeifPlayback,
            onRenderAckTimeout = {
                playbackMemoryGuard.recordRenderAckTimeout(
                    android.os.SystemClock.elapsedRealtime(),
                )
            },
        )
    }

    /** Weather overlay data (spec §11); inert unless enabled in settings. */
    val weather: WeatherRepository by lazy {
        WeatherRepository(
            diagnostics = diagnostics,
            providerFactory = { settings, apiKey ->
                OpenMeteoProvider(settings.endpointBaseUrl, apiKey, dispatchers.io)
            },
        )
    }

    val webUploadManager: WebUploadManager by lazy {
        WebUploadManager(localUploadSource, settings, indexer, diagnostics, dispatchers.io)
    }

    /** Embedded web setup server (spec §15); off unless enabled in settings. */
    val webServer: WebServerController by lazy {
        WebServerController(
            settings, photoDao, engine, diagnostics, appContext, webUploadManager, rememberedBrowsers,
            allowHeifPlayback, diagnosticRuntimeState, localThumbnailCache, memoryTier.isLow,
        )
    }

    /** Awaited, application-lifetime maintenance; never owned by an Activity/ViewModel. */
    val factoryResetCoordinator: FactoryResetCoordinator by lazy {
        FactoryResetCoordinator(
            database = database,
            settings = settings,
            mediaCache = mediaCache,
            uploadManager = webUploadManager,
            diagnostics = diagnostics,
            io = dispatchers.io,
            clearMemoryCache = { imageLoader.memoryCache?.clear() },
            clearPreview = { webServer.clearPreview() },
            rotateDiagnosticIdentityKey = {
                DiagnosticIdentityHasher.install(DiagnosticIdentityKeyStore(appContext).rotate())
            },
        )
    }

    val fallbackSource: AppPrivateFallbackSource by lazy {
        AppPrivateFallbackSource(SourceId(SOURCE_FALLBACK), appContext, dispatchers.io)
    }

    val localUploadSource: LocalUploadPhotoSource by lazy {
        LocalUploadPhotoSource(SourceId(SOURCE_LOCAL_UPLOADS), appContext, dispatchers.io)
    }

    /** Build a SAF source for a freshly granted/persisted tree URI. */
    fun safSource(treeUri: Uri): SafPhotoSource =
        SafPhotoSource(SourceId(SOURCE_LOCAL_SAF), treeUri, appContext, dispatchers.io)

    /** Build an SMB source from connection settings and resolved credentials. */
    fun smbSource(conn: SmbConnection, credentials: SmbCredentials): SmbPhotoSource =
        SmbPhotoSource(
            SourceId(SOURCE_SMB), conn, credentials, dispatchers.io, runtimeResourceTracker,
        )

    /** Build a Synology File Station source from connection settings and credentials. */
    fun synologySource(conn: SynologyConnection, credentials: SynologyCredentials): SynologyFileStationSource =
        SynologyFileStationSource(SourceId(SOURCE_SYNOLOGY), conn, credentials, dispatchers.io)

    fun webDavSource(conn: WebDavConnection, credentials: WebDavCredentials): WebDavPhotoSource =
        WebDavPhotoSource(SourceId(SOURCE_WEBDAV), conn, credentials, dispatchers.io)

    /**
     * Coil loader tuned for a frame: a small memory cache (only current+next are
     * ever shown — spec §10.2) and crossfade handled by the composable, not Coil.
     */
    val imageLoader: ImageLoader by lazy {
        val cacheBytes = ImageMemoryBudget.bytesForHeap(
            maxHeapBytes = Runtime.getRuntime().maxMemory(),
            lowMemoryTier = memoryTier.isLow,
        )
        ImageLoader.Builder(appContext)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizeBytes(cacheBytes)
                    .build()
            }
            .build()
    }

    companion object {
        private const val BYTES_PER_MIB = 1024L * 1024L
        const val SOURCE_LOCAL_SAF = BuiltInSourceIds.LOCAL_SAF
        const val SOURCE_FALLBACK = BuiltInSourceIds.FALLBACK
        const val SOURCE_SMB = BuiltInSourceIds.SMB
        const val SOURCE_SYNOLOGY = BuiltInSourceIds.SYNOLOGY
        const val SOURCE_WEBDAV = BuiltInSourceIds.WEBDAV
        const val SOURCE_LOCAL_UPLOADS = BuiltInSourceIds.LOCAL_UPLOADS
    }
}

private fun diagnosticPercent(numerator: Long, denominator: Long): Int {
    if (denominator <= 0L) return 0
    return ((numerator.coerceAtLeast(0L).toDouble() / denominator.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 999)
}
