package com.example.familyphotoframe

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import coil.ImageLoader
import coil.memory.MemoryCache
import com.example.familyphotoframe.data.cache.MediaCache
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.DiagnosticIdentityHasher
import com.example.familyphotoframe.data.diagnostics.DiagnosticIdentityKeyStore
import com.example.familyphotoframe.data.diagnostics.DiagnosticRuntimeState
import com.example.familyphotoframe.data.diagnostics.FileDiagnosticsSink
import com.example.familyphotoframe.data.diagnostics.RuntimeSampler
import com.example.familyphotoframe.data.diagnostics.BatteryTelemetry
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
import com.example.familyphotoframe.util.ImageFormatSupport
import com.example.familyphotoframe.util.ImageMemoryBudget
import java.io.File

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

    /** Runtime playback capability; indexing remains device-independent. */
    val allowHeifPlayback: Boolean = ImageFormatSupport.supportsPlatformHeif(Build.VERSION.SDK_INT)

    val diagnostics: DiagnosticsLog by lazy { DiagnosticsLog() }

    /** Lock-free crash/ANR context populated by normal runtime owners. */
    val diagnosticRuntimeState: DiagnosticRuntimeState = DiagnosticRuntimeState()

    /** Process-wide low-memory circuit breaker shared by Application and slideshow UI. */
    val playbackMemoryGuard: PlaybackMemoryGuard = PlaybackMemoryGuard()

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
                val pssKb = Debug.getPss().toLong()
                val nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L
                val imageCacheKb = (cache?.size ?: 0).toLong() / 1024L
                val bitmapInventory = diagnosticRuntimeState.snapshot().bitmaps
                val memoryProtection = playbackMemoryGuard.snapshot()
                diagnosticRuntimeState.updateMemory(
                    DiagnosticRuntimeState.Memory(
                        heapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
                        heapMaxKb = runtime.maxMemory() / 1024L,
                        nativeHeapKb = nativeHeapKb,
                        pssKb = pssKb,
                        imageCacheKb = imageCacheKb,
                        sampledAtEpochMs = System.currentTimeMillis(),
                    )
                )
                linkedMapOf(
                    "pssKb" to pssKb.toString(),
                    "nativeHeapKb" to nativeHeapKb.toString(),
                    "imageCacheKb" to imageCacheKb.toString(),
                    "imageCacheMaxKb" to ((cache?.maxSize ?: 0).toLong() / 1024L).toString(),
                    "preparedSlideCount" to bitmapInventory.preparedSlideCount.toString(),
                    "renderedSlideCount" to bitmapInventory.renderedSlideCount.toString(),
                    "decodedBitmapCount" to bitmapInventory.decodedBitmapCount.toString(),
                    "appBitmapCount" to bitmapInventory.appBitmapCount.toString(),
                    "activeDecodedBytes" to bitmapInventory.activeDecodedBytes.toString(),
                    "pendingDisposals" to bitmapInventory.pendingDisposals.toString(),
                    "memoryProtectionLevel" to memoryProtection.level.name,
                    "pressurePercent" to memoryProtection.pressurePercent.toString(),
                    "oomCount" to memoryProtection.totalOomCount.toString(),
                ).apply {
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
        )
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

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
        SlideshowEngine(photoDao, diagnostics, folderBalancedShuffle, allowHeif = allowHeifPlayback)
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
            settings, photoDao, engine, diagnostics, webUploadManager, rememberedBrowsers,
            allowHeifPlayback, diagnosticRuntimeState,
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
        SmbPhotoSource(SourceId(SOURCE_SMB), conn, credentials, dispatchers.io)

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
        val cacheBytes = ImageMemoryBudget.bytesForHeap(Runtime.getRuntime().maxMemory())
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
        const val SOURCE_LOCAL_SAF = BuiltInSourceIds.LOCAL_SAF
        const val SOURCE_FALLBACK = BuiltInSourceIds.FALLBACK
        const val SOURCE_SMB = BuiltInSourceIds.SMB
        const val SOURCE_SYNOLOGY = BuiltInSourceIds.SYNOLOGY
        const val SOURCE_WEBDAV = BuiltInSourceIds.WEBDAV
        const val SOURCE_LOCAL_UPLOADS = BuiltInSourceIds.LOCAL_UPLOADS
    }
}
