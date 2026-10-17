package com.example.familyphotoframe.ui.slideshow.viewmodel

import android.net.Uri
import android.os.Build
import com.example.familyphotoframe.data.cache.MediaCache
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.SourceId
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.domain.engine.DecodeFailureStage
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.domain.engine.PortraitCollagePolicy
import com.example.familyphotoframe.domain.engine.toDisplayPhoto
import com.example.familyphotoframe.ui.slideshow.ModelResolutionPriority
import com.example.familyphotoframe.ui.slideshow.ModelResolutionRequest
import com.example.familyphotoframe.ui.slideshow.NOT_CACHED_REASON
import com.example.familyphotoframe.ui.slideshow.PhotoModelResolution
import com.example.familyphotoframe.ui.slideshow.PreparationTransferState
import com.example.familyphotoframe.ui.slideshow.PreparationTransferUpdate
import com.example.familyphotoframe.ui.slideshow.REMOTE_COLLAGE_PROBE_TIMEOUT_MS
import com.example.familyphotoframe.ui.slideshow.RemoteImageBoundsProbe
import com.example.familyphotoframe.ui.slideshow.RemoteSourceRegistry
import com.example.familyphotoframe.util.ImageFormatSupport
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * How playback reaches photo bytes and collage companions: resolving a decodable Coil
 * model (through MediaCache for remote sources), probing remote image bounds for the
 * collage optimizer, and choosing collage candidates for an anchor photo.
 *
 * The cached-only flags are owned by the source pipeline and read live on every call.
 */
internal class PlaybackPhotoAccess(
    host: SlideshowFeatureHost,
    private val remoteSources: RemoteSourceRegistry,
    private val isRemotePrimaryCachedOnly: () -> Boolean,
    private val currentRemotePrimarySourceId: () -> String?,
    private val maxFailures: () -> Int,
) {
    private val services = host.services
    private val _state = host.state
    private val engine = services.engine
    private val nativeAllocationStageTracker = services.nativeAllocationStageTracker
    private val remotePrimaryCachedOnly: Boolean get() = isRemotePrimaryCachedOnly()
    private val remotePrimarySourceId: String? get() = currentRemotePrimarySourceId()

    /**
     * Resolve a Coil model for a photo. Local content URIs and files resolve
     * immediately; remote items (SMB and Synology) are fetched through MediaCache to a
     * verified local file, so Coil never sees a network URL or NAS-relative token.
     * Returns either a ready model or a structured failure; the UI records the failure and advances.
     */
    internal suspend fun resolveModel(
        display: DisplayPhoto,
        request: ModelResolutionRequest = ModelResolutionRequest(
            ModelResolutionPriority.BACKGROUND_PRELOAD,
        ),
        onPreparationSubstage: (String) -> Unit = {},
        onPreparationTransferUpdate: (PreparationTransferUpdate) -> Unit = {},
    ): PhotoModelResolution {
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
        // Do not persist these routine markers.  The selected presentation's bounded
        // in-memory trace retains the last one, so a watchdog event can distinguish a
        // cache lock, stream open, transfer, verification, or commit without rotating
        // the diagnostic bundle during a normal soak.
        val onMediaCacheStage: (MediaCache.ResolveStage) -> Unit = { stage ->
            onPreparationSubstage("MEDIA_CACHE_${stage.name}")
        }
        val onMediaCacheTransfer: (MediaCache.TransferTelemetry) -> Unit = { telemetry ->
            onPreparationTransferUpdate(
                PreparationTransferUpdate(
                    state = when (telemetry.state) {
                        MediaCache.TransferTelemetryState.STARTED -> PreparationTransferState.STARTED
                        MediaCache.TransferTelemetryState.PROGRESS -> PreparationTransferState.PROGRESS
                        MediaCache.TransferTelemetryState.SELECTED_DEADLINE ->
                            PreparationTransferState.SELECTED_DEADLINE
                        MediaCache.TransferTelemetryState.STREAM_CLOSE_REQUESTED ->
                            PreparationTransferState.STREAM_CLOSE_REQUESTED
                        MediaCache.TransferTelemetryState.TRANSFER_SLOT_RELEASED ->
                            PreparationTransferState.TRANSFER_SLOT_RELEASED
                    },
                    copiedBytes = telemetry.copiedBytes,
                    expectedBytes = telemetry.expectedBytes,
                    deadlineMs = telemetry.deadlineMs,
                    streamCloseSucceeded = telemetry.streamCloseSucceeded,
                )
            )
        }
        val mediaTransferPriority = when (request.priority) {
            ModelResolutionPriority.SELECTED_PRESENTATION ->
                com.example.familyphotoframe.data.cache.MediaTransferPriority.SELECTED_PRESENTATION
            ModelResolutionPriority.BACKGROUND_PRELOAD ->
                com.example.familyphotoframe.data.cache.MediaTransferPriority.BACKGROUND_PRELOAD
        }
        if (request.cacheOnly) {
            // A presentation that is already due must not start NAS transfers for optional
            // collage companions inside its selection budget. A miss is not a photo fault.
            return when (val cached = services.mediaCache.resolveIfCached(item, onMediaCacheStage)) {
                is MediaCache.ResolveResult.Ready -> PhotoModelResolution.Ready(cached.file)
                is MediaCache.ResolveResult.Failed -> PhotoModelResolution.Failed(
                    DecodeFailure(
                        photoId = display.id,
                        sourceId = display.sourceId,
                        fileExtension = extension,
                        mimeType = display.mimeType,
                        stage = DecodeFailureStage.CACHE_LOOKUP,
                        reason = NOT_CACHED_REASON,
                        sourceLevelFailure = true,
                    )
                )
            }
        }
        val cacheResult = if (remotePrimaryCachedOnly && display.sourceId == remotePrimarySourceId) {
            services.mediaCache.resolveIfCached(item, onMediaCacheStage)
        } else {
            val src = remoteSources.active(display.sourceId)
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
            services.mediaCache.resolve(
                item = item,
                source = src,
                protectedKeys = protectedKeys,
                priority = mediaTransferPriority,
                transferDeadlineMonotonicMs = request.selectedTransferDeadlineMonotonicMs,
                onStage = onMediaCacheStage,
                onTransferTelemetry = onMediaCacheTransfer,
            )
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

        suspend fun trackedProbe(block: suspend () -> Pair<Int, Int>?): Pair<Int, Int>? {
            val operation = nativeAllocationStageTracker.start(
                com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker.Stage.BOUNDS_PROBE
            )
            return try {
                block().also { dimensions ->
                    operation.finish(
                        if (dimensions != null) {
                            com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker.Outcome.COMPLETED
                        } else {
                            com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker.Outcome.FAILED
                        }
                    )
                }
            } catch (timeout: TimeoutCancellationException) {
                operation.finish(
                    com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker.Outcome.TIMED_OUT
                )
                null
            } catch (cancelled: CancellationException) {
                operation.finish(
                    com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker.Outcome.CANCELLED
                )
                throw cancelled
            } catch (error: Throwable) {
                operation.finish(
                    com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker.Outcome.FAILED
                )
                throw error
            }
        }

        when (val cached = services.mediaCache.resolveIfCached(item)) {
            is MediaCache.ResolveResult.Ready -> {
                val dimensions = trackedProbe {
                    withContext(Dispatchers.IO) {
                        try {
                            RemoteImageBoundsProbe.decode(cached.file.inputStream())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                return persist(dimensions)
            }
            is MediaCache.ResolveResult.Failed -> Unit
        }

        val source = remoteSources.active(display.sourceId) ?: return null
        val dimensions = trackedProbe {
            withTimeout(REMOTE_COLLAGE_PROBE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    try {
                        val stream = source.openStream(
                            item,
                            OpenOptions(
                                timeoutMs = REMOTE_COLLAGE_PROBE_TIMEOUT_MS,
                                preferOriginal = false,
                                purpose = com.example.familyphotoframe.data.source.OpenPurpose.COLLAGE_BOUNDS,
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
        // The folder-balanced turn reserved ahead for the preloaded next presentation.
        val nextReservedMatches = !reservedMatches &&
            engineState.nextReservedCandidateIds.firstOrNull() == anchor.id
        val historyMatches = engineState.historyPhotoIds.firstOrNull() == anchor.id
        val orderedIds = when {
            reservedMatches -> engineState.reservedCandidateIds.drop(1)
            nextReservedMatches -> engineState.nextReservedCandidateIds.drop(1)
            historyMatches -> engineState.historyPhotoIds.drop(1)
            else -> emptyList()
        }.take(cap)
        if (reservedMatches || nextReservedMatches || historyMatches) {
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

    private fun currentMaxFailures(): Int = maxFailures()
}
