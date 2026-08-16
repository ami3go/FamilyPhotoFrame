package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.domain.engine.CollageLayout
import com.example.familyphotoframe.domain.engine.CollageCandidateMeta
import com.example.familyphotoframe.domain.engine.CollageDecision
import com.example.familyphotoframe.domain.engine.DecodeColorChoice
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.domain.engine.DecodeFailureStage
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.domain.engine.PhotoOrientation
import com.example.familyphotoframe.domain.engine.PortraitCollagePolicy
import com.example.familyphotoframe.ui.render.ImageBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/** Prepared-presentation models and decode pipeline; performs no Compose rendering. */
internal sealed interface PreparedSlide {
    val anchor: DisplayPhoto
    val photos: List<DisplayPhoto>
    val dataSources: List<String?>
    val decodedBytes: Long
    /**
     * App-created, quarter-resolution layer prepared only for Soft Focus Fade.
     * Once handed to Compose it must not be recycled immediately: a queued display list may
     * still draw it after composition disposal. API 21–25 retirement uses a delayed reclaimer.
     */
    val transitionBlurredBitmap: Bitmap?

    data class Single(
        override val anchor: DisplayPhoto,
        val model: Any,
        val bitmap: Bitmap,
        val dataSource: String?,
        /** Non-null only for the no-companion portrait fallback. */
        val aspectOverride: AspectMode? = null,
        override val transitionBlurredBitmap: Bitmap? = null,
    ) : PreparedSlide {
        override val photos: List<DisplayPhoto> = listOf(anchor)
        override val dataSources: List<String?> = listOf(dataSource)
        override val decodedBytes: Long = bitmap.safeAllocationBytes() +
            (transitionBlurredBitmap?.safeAllocationBytes() ?: 0L)
    }

    data class Collage(
        override val anchor: DisplayPhoto,
        val tiles: List<PreparedTile>,
        val layout: CollageLayout,
        override val transitionBlurredBitmap: Bitmap? = null,
    ) : PreparedSlide {
        override val photos: List<DisplayPhoto> = tiles.map { it.photo }
        override val dataSources: List<String?> = tiles.map { it.dataSource }
        override val decodedBytes: Long = tiles.sumOf { it.bitmap.safeAllocationBytes() } +
            (transitionBlurredBitmap?.safeAllocationBytes() ?: 0L)
    }
}

private data class ResolvedPhoto(
    val photo: DisplayPhoto,
    val model: Any,
    val localThumbnailCacheEligible: Boolean = false,
)
internal data class PreparedTile(
    val photo: DisplayPhoto,
    val model: Any,
    val bitmap: Bitmap,
    val dataSource: String?,
)

private sealed interface ResolvePhotoResult {
    data class Ready(val photo: ResolvedPhoto) : ResolvePhotoResult
    data class Failed(val failure: DecodeFailure) : ResolvePhotoResult
}

private sealed interface DecodePhotoResult {
    data class Ready(val tile: PreparedTile) : DecodePhotoResult
    data class Failed(val failure: DecodeFailure) : DecodePhotoResult
}

internal sealed interface PrepareSlideResult {
    data class Ready(val slide: PreparedSlide) : PrepareSlideResult
    data class Failed(val failure: DecodeFailure) : PrepareSlideResult
}

private fun DecodeColorChoice.toBitmapConfig(): Bitmap.Config = when (this) {
    DecodeColorChoice.ARGB_8888 -> Bitmap.Config.ARGB_8888
    DecodeColorChoice.RGB_565 -> Bitmap.Config.RGB_565
}

private fun Bitmap.safeAllocationBytes(): Long = runCatching { allocationByteCount.toLong() }
    .getOrElse { byteCount.toLong() }

private fun PreparedSlide.withTransitionBlur(bitmap: Bitmap?): PreparedSlide = when (this) {
    is PreparedSlide.Single -> copy(transitionBlurredBitmap = bitmap)
    is PreparedSlide.Collage -> copy(transitionBlurredBitmap = bitmap)
}

private suspend fun createTransitionBlur(
    slide: PreparedSlide,
    targetW: Int,
    targetH: Int,
    collageGap: CollageGap,
    colorChoice: DecodeColorChoice,
    onOutOfMemory: () -> Unit,
): Bitmap? = withContext(Dispatchers.Default) {
    try {
        // Quarter-size dimensions retain one sixteenth of full-screen pixels while
        // remaining smooth after the prepared blur is enlarged by the GPU.
        val scale = SOFT_FOCUS_DIMENSION_SCALE
        val width = (targetW.coerceAtLeast(1) * scale).roundToInt().coerceAtLeast(1)
        val height = (targetH.coerceAtLeast(1) * scale).roundToInt().coerceAtLeast(1)
        val source = Bitmap.createBitmap(width, height, colorChoice.toBitmapConfig())
        try {
            val canvas = android.graphics.Canvas(source)
            canvas.drawColor(android.graphics.Color.BLACK)
            val paint = android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG,
            )

            fun drawCrop(bitmap: Bitmap, destination: android.graphics.RectF) {
                val sourceAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                val destinationAspect = destination.width() / destination.height().coerceAtLeast(1f)
                val sourceRect = if (sourceAspect > destinationAspect) {
                    val cropWidth = (bitmap.height * destinationAspect)
                        .roundToInt().coerceIn(1, bitmap.width)
                    val left = (bitmap.width - cropWidth) / 2
                    android.graphics.Rect(left, 0, left + cropWidth, bitmap.height)
                } else {
                    val cropHeight = (bitmap.width / destinationAspect)
                        .roundToInt().coerceIn(1, bitmap.height)
                    val top = (bitmap.height - cropHeight) / 2
                    android.graphics.Rect(0, top, bitmap.width, top + cropHeight)
                }
                canvas.drawBitmap(bitmap, sourceRect, destination, paint)
            }

            when (slide) {
                is PreparedSlide.Single -> drawCrop(
                    slide.bitmap,
                    android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()),
                )
                is PreparedSlide.Collage -> {
                    val gapPx = when (collageGap) {
                        CollageGap.NONE -> 0f
                        CollageGap.SMALL -> 4f * scale
                        CollageGap.MEDIUM -> 8f * scale
        CollageGap.LARGE -> 16f * scale
                    }
                    val destinations = collageDestinationRects(
                        slide.layout, width.toFloat(), height.toFloat(), gapPx,
                    )
                    slide.tiles.zip(destinations).forEach { (tile, destination) ->
                        drawCrop(tile.bitmap, destination)
                    }
                }
            }

            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            val blurredPixels = ImageBlur.boxBlur(pixels, width, height)
            Bitmap.createBitmap(blurredPixels, width, height, colorChoice.toBitmapConfig())
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        onOutOfMemory()
        null
    } catch (_: Exception) {
        null
    }
}

internal const val SOFT_FOCUS_DIMENSION_SCALE = 0.25f

private fun decodeFailure(
    photo: DisplayPhoto,
    stage: DecodeFailureStage,
    exceptionClass: String? = null,
    reason: String? = null,
) = DecodeFailure(
    photoId = photo.id,
    sourceId = photo.sourceId,
    fileExtension = photo.fileName.substringAfterLast('.', "").lowercase(),
    mimeType = photo.mimeType,
    stage = stage,
    exceptionClass = exceptionClass,
    reason = reason,
)

private suspend fun resolvePhoto(
    photo: DisplayPhoto,
    resolveModel: suspend (DisplayPhoto) -> PhotoModelResolution,
): ResolvePhotoResult = try {
    when (val resolution = resolveModel(photo)) {
        is PhotoModelResolution.Ready -> ResolvePhotoResult.Ready(
            ResolvedPhoto(photo, resolution.model, resolution.localThumbnailCacheEligible)
        )
        is PhotoModelResolution.Failed -> ResolvePhotoResult.Failed(resolution.failure)
    }
} catch (c: CancellationException) {
    throw c
} catch (_: OutOfMemoryError) {
    ResolvePhotoResult.Failed(
        decodeFailure(
            photo,
            DecodeFailureStage.UNKNOWN,
            exceptionClass = "OutOfMemoryError",
            reason = "java_heap_exhausted_during_model_resolution",
        )
    )
} catch (e: Exception) {
    ResolvePhotoResult.Failed(
        decodeFailure(photo, DecodeFailureStage.UNKNOWN, e.javaClass.simpleName)
    )
}

/** Decode directly to the destination tile size; no full-resolution collage bitmaps. */
private suspend fun decodePhoto(
    context: android.content.Context,
    resolved: ResolvedPhoto,
    imageLoader: ImageLoader,
    targetW: Int,
    targetH: Int,
    colorChoice: DecodeColorChoice,
): DecodePhotoResult {
    return try {
        // Request construction is intentionally inside the OOM boundary. The API-22
        // field log proved the previous request-before-try ordering could bypass recovery.
        val request = ImageRequest.Builder(context)
            .data(resolved.model)
            .size(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
            .allowHardware(false)
            // Photographs are opaque, so ARGB_8888's alpha channel is a byte per pixel
            // of dead weight on the small heaps this runs on. See DecodeColorPolicy.
            .bitmapConfig(colorChoice.toBitmapConfig())
            // PreparedSlide exclusively owns this software bitmap until retirement.
            // Retaining the same allocation in Coil doubles steady-state heap pressure.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        when (val result = imageLoader.execute(request)) {
            is SuccessResult -> {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                    DecodePhotoResult.Failed(
                        decodeFailure(
                            resolved.photo,
                            DecodeFailureStage.IMAGE_LOADER,
                            exceptionClass = "NonBitmapDrawable",
                            reason = "decoded_drawable_is_not_bitmap",
                        )
                    )
                } else {
                    DecodePhotoResult.Ready(
                        PreparedTile(
                            photo = resolved.photo,
                            model = resolved.model,
                            bitmap = bitmap,
                            dataSource = result.dataSource.name,
                        )
                    )
                }
            }
            is ErrorResult -> DecodePhotoResult.Failed(
                decodeFailure(
                    resolved.photo,
                    DecodeFailureStage.IMAGE_LOADER,
                    result.throwable.javaClass.simpleName,
                )
            )
            else -> DecodePhotoResult.Failed(
                decodeFailure(
                    resolved.photo,
                    DecodeFailureStage.IMAGE_LOADER,
                    exceptionClass = "UnknownImageResult",
                )
            )
        }
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        DecodePhotoResult.Failed(
            decodeFailure(
                resolved.photo,
                DecodeFailureStage.IMAGE_LOADER,
                exceptionClass = "OutOfMemoryError",
                reason = "java_heap_exhausted",
            )
        )
    } catch (e: Exception) {
        DecodePhotoResult.Failed(
            decodeFailure(resolved.photo, DecodeFailureStage.IMAGE_LOADER, e.javaClass.simpleName)
        )
    }
}

private fun DisplayPhoto.metadataOrientation(): PhotoOrientation =
    PortraitCollagePolicy.classify(width, height, exifOrientation)

private fun PreparedTile.decodedOrientation(): PhotoOrientation =
    PortraitCollagePolicy.classify(bitmap.width, bitmap.height)

private fun PreparedTile.aspectRatio(): Float =
    PortraitCollagePolicy.aspectRatio(bitmap.width, bitmap.height)

private fun portraitFallbackAspect(fallback: PortraitFallback): AspectMode = when (fallback) {
    PortraitFallback.BLURRED_BACKGROUND -> AspectMode.FIT_BLUR
    PortraitFallback.SOLID_BACKGROUND -> AspectMode.FIT_COLOR
    PortraitFallback.CROP_TO_FILL -> AspectMode.FILL_CROP
}

/** One resolved candidate after a bounded orientation/aspect probe. */
private data class InspectedCollagePhoto(
    val photo: DisplayPhoto,
    /** Null when indexed metadata was sufficient and no source/model I/O was needed yet. */
    val resolved: ResolvedPhoto?,
    val meta: CollageCandidateMeta,
)

private fun DisplayPhoto.collageFolderKey(): String {
    val parent = normalizedPath.substringBeforeLast('/', "").ifBlank { folderName }
    return "$sourceId\u001f$parent"
}

private fun DisplayPhoto.collageCaptureTime(): Long = dateTakenEpochMs ?: fileModifiedEpochMs

private fun DisplayPhoto.metadataCollageMeta(order: Int): CollageCandidateMeta? {
    val (w, h) = PortraitCollagePolicy.effectiveDimensions(width, height, exifOrientation) ?: return null
    return CollageCandidateMeta(
        id = id,
        aspectRatio = PortraitCollagePolicy.aspectRatio(w, h),
        orientation = PortraitCollagePolicy.classify(w, h),
        folderKey = collageFolderKey(),
        captureTimeEpochMs = collageCaptureTime(),
        lastShownAtEpochMs = lastShownAtEpochMs,
        candidateOrder = order,
    )
}

private fun collageGapPixels(context: android.content.Context, gap: CollageGap): Float {
    val density = context.resources.displayMetrics.density.coerceAtLeast(0.5f)
    return when (gap) {
        CollageGap.NONE -> 0f
        CollageGap.SMALL -> 4f * density
        CollageGap.MEDIUM -> 8f * density
        CollageGap.LARGE -> 16f * density
    }
}

private fun Float.diagnostic4(): String = java.lang.String.format(java.util.Locale.US, "%.4f", this)
private fun Double.diagnostic2(): String = java.lang.String.format(java.util.Locale.US, "%.2f", this)

/**
 * Build either a single prepared photo or the best bounded two/three-photo collage.
 * Candidate probes are deliberately small; only the winning tile geometry is decoded at
 * display size. Folder-balanced reservations remain authoritative because this function
 * can choose only from [loadCollageCandidates]' ordered input.
 */
internal suspend fun prepareSlide(
    context: android.content.Context,
    photo: DisplayPhoto,
    imageLoader: ImageLoader,
    resolveModel: suspend (DisplayPhoto) -> PhotoModelResolution,
    loadCollageCandidates: suspend (DisplayPhoto, Int) -> List<DisplayPhoto>,
    probeRemoteDimensions: suspend (DisplayPhoto) -> Pair<Int, Int>?,
    collageMode: PortraitCollageMode,
    maxCollagePhotos: Int,
    configuredMaxCollagePhotos: Int = maxCollagePhotos,
    memoryMaxCollagePhotos: Int = maxCollagePhotos,
    portraitFallback: PortraitFallback,
    collageGap: CollageGap,
    collageOrientationFilter: CollageOrientationFilter = CollageOrientationFilter.ANY,
    collageFillWithOtherOrientations: Boolean = true,
    collageLayoutPreference: CollageLayoutPreference = CollageLayoutPreference.AUTO,
    prepareSoftFocusBlur: Boolean,
    allowBlurredBackground: Boolean,
    colorChoice: DecodeColorChoice = DecodeColorChoice.ARGB_8888,
    excludedIds: Set<Long>,
    targetW: Int,
    targetH: Int,
    localThumbnailCache: com.example.familyphotoframe.data.cache.LocalThumbnailCache? = null,
    localThumbnailCacheProtectedStableIds: Set<String> = emptySet(),
    onRecoverableOom: (DecodeFailure) -> Unit,
    onCollageCandidateFailure: (DecodeFailure) -> Unit,
    onCollageEvent: (
        event: String,
        anchorId: Long,
        photoIds: List<Long>,
        layout: String?,
        prepareMs: Long?,
        decodedBytes: Long?,
        reason: String?,
        details: Map<String, String>,
    ) -> Unit,
): PrepareSlideResult {
    val uncommitted = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())

    suspend fun decodeOwned(
        resolved: ResolvedPhoto,
        width: Int,
        height: Int,
    ): DecodePhotoResult = decodePhoto(context, resolved, imageLoader, width, height, colorChoice).also { result ->
        if (result is DecodePhotoResult.Ready) uncommitted += result.tile.bitmap
    }

    suspend fun decodeAnchorCacheable(resolved: ResolvedPhoto, width: Int, height: Int): DecodePhotoResult {
        if (localThumbnailCache == null || !resolved.localThumbnailCacheEligible) {
            return decodeOwned(resolved, width, height)
        }
        val cachedFile = localThumbnailCache.get(resolved.photo.stableId, width, height)
        if (cachedFile != null) {
            val fromCache = decodeOwned(resolved.copy(model = cachedFile), width, height)
            if (fromCache is DecodePhotoResult.Ready) return fromCache
        }
        val decoded = decodeOwned(resolved, width, height)
        if (decoded is DecodePhotoResult.Ready) {
            localThumbnailCache.put(
                resolved.photo.stableId,
                width,
                height,
                decoded.tile.bitmap,
                localThumbnailCacheProtectedStableIds,
            )
        }
        return decoded
    }

    fun releaseUncommitted() {
        uncommitted.toList().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
            uncommitted.remove(bitmap)
        }
    }

    fun discard(tile: PreparedTile) {
        if (uncommitted.remove(tile.bitmap) && !tile.bitmap.isRecycled) tile.bitmap.recycle()
    }

    fun emit(
        event: String,
        ids: List<Long> = listOf(photo.id),
        layout: String? = null,
        prepareMs: Long? = null,
        decodedBytes: Long? = null,
        reason: String? = null,
        details: Map<String, String> = emptyMap(),
    ) = onCollageEvent(event, photo.id, ids, layout, prepareMs, decodedBytes, reason, details)

    try {
        val startedAt = SystemClock.elapsedRealtime()

        suspend fun ready(
            slide: PreparedSlide,
            allowTransitionBlur: Boolean = true,
        ): PrepareSlideResult.Ready {
            val blur = if (prepareSoftFocusBlur && allowTransitionBlur) {
                createTransitionBlur(slide, targetW, targetH, collageGap, colorChoice) {
                    onRecoverableOom(
                        decodeFailure(
                            photo,
                            DecodeFailureStage.IMAGE_LOADER,
                            exceptionClass = "OutOfMemoryError",
                            reason = "transition_blur_allocation_failed",
                        )
                    )
                }
            } else null
            if (blur != null) uncommitted += blur
            val prepared = slide.withTransitionBlur(blur)
            prepared.allBitmaps().forEach(uncommitted::remove)
            return PrepareSlideResult.Ready(prepared)
        }

        val resolvedAnchor = when (val resolved = resolvePhoto(photo, resolveModel)) {
            is ResolvePhotoResult.Ready -> resolved.photo
            is ResolvePhotoResult.Failed -> return PrepareSlideResult.Failed(resolved.failure)
        }

        suspend fun prepareSingle(
            forcedAspect: AspectMode? = null,
            eventReason: String? = null,
            allowTransitionBlur: Boolean = true,
            eventDetails: Map<String, String> = emptyMap(),
        ): PrepareSlideResult {
            return when (val decoded = decodeAnchorCacheable(resolvedAnchor, targetW, targetH)) {
                is DecodePhotoResult.Ready -> {
                    val slide = PreparedSlide.Single(
                        photo,
                        resolvedAnchor.model,
                        decoded.tile.bitmap,
                        decoded.tile.dataSource,
                        aspectOverride = forcedAspect,
                    )
                    val result = ready(slide, allowTransitionBlur)
                    if (eventReason != null) {
                        emit(
                            "COLLAGE_FALLBACK_SINGLE",
                            prepareMs = SystemClock.elapsedRealtime() - startedAt,
                            decodedBytes = result.slide.decodedBytes,
                            reason = eventReason,
                            details = eventDetails,
                        )
                    }
                    result
                }
                is DecodePhotoResult.Failed -> PrepareSlideResult.Failed(decoded.failure)
            }
        }

        // Only the blurred backdrop costs an extra full-frame allocation, so it is the
        // only part of the user's choice memory pressure overrides.
        val requestedFallback = portraitFallbackAspect(portraitFallback)
        val allowedFallback = if (requestedFallback == AspectMode.FIT_BLUR && !allowBlurredBackground) {
            AspectMode.FIT_COLOR
        } else requestedFallback

        if (collageMode == PortraitCollageMode.OFF) return prepareSingle()
        if (maxCollagePhotos < 2) {
            return prepareSingle(
                forcedAspect = if (photo.metadataOrientation() == PhotoOrientation.PORTRAIT) allowedFallback else null,
                eventReason = "memory_guard_single_photo",
                allowTransitionBlur = false,
            )
        }

        suspend fun probeUnknown(
            photoToProbe: DisplayPhoto,
            resolved: ResolvedPhoto,
            order: Int,
        ): Pair<InspectedCollagePhoto?, DecodeFailure?> {
            val probe = decodeOwned(resolved, COLLAGE_PROBE_MAX_PX, COLLAGE_PROBE_MAX_PX)
            return when (probe) {
                is DecodePhotoResult.Ready -> {
                    val tile = probe.tile
                    val meta = CollageCandidateMeta(
                        id = photoToProbe.id,
                        aspectRatio = tile.aspectRatio(),
                        orientation = tile.decodedOrientation(),
                        folderKey = photoToProbe.collageFolderKey(),
                        captureTimeEpochMs = photoToProbe.collageCaptureTime(),
                        lastShownAtEpochMs = photoToProbe.lastShownAtEpochMs,
                        candidateOrder = order,
                    )
                    discard(tile)
                    InspectedCollagePhoto(photoToProbe, resolved, meta) to null
                }
                is DecodePhotoResult.Failed -> null to probe.failure
            }
        }

        val anchorInspected = photo.metadataCollageMeta(-1)?.let { metadata ->
            InspectedCollagePhoto(photo, resolvedAnchor, metadata)
        } ?: run {
            val (inspected, failure) = probeUnknown(photo, resolvedAnchor, -1)
            inspected ?: return PrepareSlideResult.Failed(
                failure ?: decodeFailure(photo, DecodeFailureStage.IMAGE_LOADER, reason = "anchor_probe_failed")
            )
        }

        emit("COLLAGE_PRELOAD_STARTED")
        val rawCandidates = loadCollageCandidates(photo, MAX_COLLAGE_CANDIDATES)
            .asSequence()
            .filter { it.id != photo.id && it.id !in excludedIds }
            .distinctBy { it.id }
            .take(MAX_COLLAGE_CANDIDATES)
            .toList()
        val inspectedCandidates = mutableListOf<InspectedCollagePhoto>()
        val remoteProbeQueue = mutableListOf<Pair<Int, DisplayPhoto>>()
        var metadataCandidateCount = 0
        var localProbeCount = 0
        var localProbeBudgetSkippedCount = 0
        var remoteProbeCount = 0
        var remoteProbeSuccessCount = 0
        var remoteProbeFailureCount = 0
        var remoteProbeBudgetSkippedCount = 0

        for ((index, candidate) in rawCandidates.withIndex()) {
            val metadata = candidate.metadataCollageMeta(index)
            when (CollageCandidateInspectionPolicy.decide(
                hasMetadata = metadata != null,
                needsRemoteCache = candidate.needsCache,
                localProbesUsed = localProbeCount,
                maxLocalProbes = MAX_LOCAL_COLLAGE_PROBES,
            )) {
                CollageCandidateInspectionAction.USE_METADATA -> {
                    // Indexed dimensions are enough for scoring. Defer source/model I/O
                    // until this candidate is actually chosen.
                    metadataCandidateCount++
                    inspectedCandidates += InspectedCollagePhoto(candidate, null, metadata!!)
                    continue
                }
                CollageCandidateInspectionAction.QUEUE_REMOTE_BOUNDED_PROBE -> {
                    // Never call resolveModel() here: that path intentionally materializes
                    // the complete NAS original. A later bounded header pass reads at most
                    // REMOTE_COLLAGE_PROBE_BYTE_LIMIT bytes instead.
                    remoteProbeQueue += index to candidate
                    continue
                }
                CollageCandidateInspectionAction.SKIP_LOCAL_PROBE_BUDGET -> {
                    localProbeBudgetSkippedCount++
                    continue
                }
                CollageCandidateInspectionAction.PROBE_LOCAL -> localProbeCount++
            }
            val resolved = when (val result = resolvePhoto(candidate, resolveModel)) {
                is ResolvePhotoResult.Ready -> result.photo
                is ResolvePhotoResult.Failed -> {
                    onCollageCandidateFailure(result.failure)
                    if (result.failure.exceptionClass == "OutOfMemoryError") {
                        return prepareSingle(
                            forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) AspectMode.FIT_COLOR else null,
                            eventReason = "candidate_model_oom",
                            allowTransitionBlur = false,
                        )
                    }
                    continue
                }
            }
            val (inspected, failure) = probeUnknown(candidate, resolved, index)
            if (inspected != null) {
                inspectedCandidates += inspected
            } else if (failure != null) {
                onCollageCandidateFailure(failure)
                if (failure.exceptionClass == "OutOfMemoryError") {
                    return prepareSingle(
                        forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) AspectMode.FIT_COLOR else null,
                        eventReason = "candidate_probe_oom",
                        allowTransitionBlur = false,
                    )
                }
            }
        }

        remoteProbeBudgetSkippedCount =
            (remoteProbeQueue.size - MAX_REMOTE_COLLAGE_PROBES).coerceAtLeast(0)
        for ((index, candidate) in remoteProbeQueue.take(MAX_REMOTE_COLLAGE_PROBES)) {
            remoteProbeCount++
            val dimensions = probeRemoteDimensions(candidate)
            val effective = dimensions?.let { (width, height) ->
                PortraitCollagePolicy.effectiveDimensions(width, height, candidate.exifOrientation)
            }
            if (effective == null) {
                remoteProbeFailureCount++
                continue
            }
            val (width, height) = effective
            remoteProbeSuccessCount++
            inspectedCandidates += InspectedCollagePhoto(
                photo = candidate,
                resolved = null,
                meta = CollageCandidateMeta(
                    id = candidate.id,
                    aspectRatio = PortraitCollagePolicy.aspectRatio(width, height),
                    orientation = PortraitCollagePolicy.classify(width, height),
                    folderKey = candidate.collageFolderKey(),
                    captureTimeEpochMs = candidate.collageCaptureTime(),
                    lastShownAtEpochMs = candidate.lastShownAtEpochMs,
                    candidateOrder = index,
                ),
            )
        }

        val mutableCandidates = inspectedCandidates.toMutableList()

        fun diagnosticsFor(decision: CollageDecision): Map<String, String> {
            val effectiveMaxPhotos = maxCollagePhotos.coerceIn(1, 3)
            val threePhotoSkipReason = when {
                decision.stats.evaluatedThreePhotoCombinations > 0 -> ""
                collageMode == PortraitCollageMode.ALWAYS_TWO -> "mode_always_two"
                configuredMaxCollagePhotos < 3 -> "configured_max_two"
                memoryMaxCollagePhotos < 3 -> "memory_guard"
                effectiveMaxPhotos < 3 -> "effective_max_two"
                decision.stats.candidateCount < 2 -> "insufficient_candidates"
                else -> "not_evaluated"
            }
            return mapOf(
            "collageMode" to collageMode.name,
            "collageOrientationFilter" to collageOrientationFilter.name,
            "collageFillWithOtherOrientations" to collageFillWithOtherOrientations.toString(),
            "collageLayoutPreference" to collageLayoutPreference.name,
            "configuredMaxCollagePhotos" to configuredMaxCollagePhotos.toString(),
            "memoryMaxCollagePhotos" to memoryMaxCollagePhotos.toString(),
            "effectiveMaxCollagePhotos" to effectiveMaxPhotos.toString(),
            "threePhotoEvaluationAllowed" to (
                collageMode != PortraitCollageMode.ALWAYS_TWO && effectiveMaxPhotos >= 3
            ).toString(),
            "threePhotoEvaluationPerformed" to (
                decision.stats.evaluatedThreePhotoCombinations > 0
            ).toString(),
            "threePhotoSkipReason" to threePhotoSkipReason,
            "rawCandidateCount" to rawCandidates.size.toString(),
            "metadataCandidateCount" to metadataCandidateCount.toString(),
            "localProbeCount" to localProbeCount.toString(),
            "localProbeBudgetSkippedCount" to localProbeBudgetSkippedCount.toString(),
            "remoteProbeCount" to remoteProbeCount.toString(),
            "remoteProbeSuccessCount" to remoteProbeSuccessCount.toString(),
            "remoteProbeFailureCount" to remoteProbeFailureCount.toString(),
            "remoteProbeBudgetSkippedCount" to remoteProbeBudgetSkippedCount.toString(),
            "remoteProbeByteLimit" to REMOTE_COLLAGE_PROBE_BYTE_LIMIT.toString(),
            "candidateCount" to decision.stats.candidateCount.toString(),
            "sameFolderCandidateCount" to decision.stats.sameFolderCandidateCount.toString(),
            "portraitCandidateCount" to decision.stats.portraitCandidateCount.toString(),
            "squareCandidateCount" to decision.stats.squareCandidateCount.toString(),
            "landscapeCandidateCount" to decision.stats.landscapeCandidateCount.toString(),
            "evaluatedTwoPhotoCombinations" to decision.stats.evaluatedTwoPhotoCombinations.toString(),
            "evaluatedThreePhotoCombinations" to decision.stats.evaluatedThreePhotoCombinations.toString(),
            "evaluatedLayoutCount" to decision.stats.evaluatedLayoutCount.toString(),
            "folderTier" to decision.folderTier.toString(),
            "orientationTier" to decision.orientationTier.toString(),
            "screenCoverage" to decision.screenCoverage.diagnostic4(),
            "averageCropLoss" to decision.averageCropLoss.diagnostic4(),
            "maximumCropLoss" to decision.maximumCropLoss.diagnostic4(),
            "timeDistanceScore" to decision.timeDistanceScore.diagnostic2(),
            "recentPenalty" to decision.recentPenalty.diagnostic2(),
            "decisionReason" to decision.decisionReason,
            )
        }

        while (true) {
            val decision = PortraitCollagePolicy.chooseBestPresentation(
                mode = collageMode,
                screenWidth = targetW,
                screenHeight = targetH,
                anchor = anchorInspected.meta,
                candidates = mutableCandidates.map { it.meta },
                maxPhotos = maxCollagePhotos.coerceIn(1, 3),
                nowEpochMs = System.currentTimeMillis(),
                orientationFilter = collageOrientationFilter,
                layoutPreference = collageLayoutPreference,
                fillWithOtherOrientations = collageFillWithOtherOrientations,
            )
            if (decision == null) {
                val fallbackMode = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) {
                    allowedFallback
                } else null
                // Why a frame stayed single is not answerable from the reason alone: an
                // anchor the filter excluded and an anchor with no companions left both
                // land here and want opposite fixes.
                return prepareSingle(
                    forcedAspect = fallbackMode,
                    eventReason = if (fallbackMode != requestedFallback && fallbackMode != null) {
                        "memory_guard_solid_background"
                    } else {
                        "insufficient_compatible_photos"
                    },
                    eventDetails = mapOf(
                        "anchorOrientation" to anchorInspected.meta.orientation.name,
                        "collageOrientationFilter" to collageOrientationFilter.name,
                        "candidateCount" to mutableCandidates.size.toString(),
                        "portraitCandidateCount" to
                            mutableCandidates.count { it.meta.orientation == PhotoOrientation.PORTRAIT }.toString(),
                        "landscapeCandidateCount" to
                            mutableCandidates.count { it.meta.orientation == PhotoOrientation.LANDSCAPE }.toString(),
                        "squareCandidateCount" to
                            mutableCandidates.count { it.meta.orientation == PhotoOrientation.SQUARE_OR_UNKNOWN }.toString(),
                    ),
                )
            }

            val inspectedById = buildMap<Long, InspectedCollagePhoto> {
                put(anchorInspected.meta.id, anchorInspected)
                mutableCandidates.forEach { put(it.meta.id, it) }
            }
            val destinations = collageDestinationRects(
                decision.layout,
                targetW.toFloat(),
                targetH.toFloat(),
                collageGapPixels(context, collageGap),
            )
            if (destinations.size != decision.photoIds.size) {
                return prepareSingle(
                    forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) allowedFallback else null,
                    eventReason = "layout_geometry_mismatch",
                )
            }

            val targetById = decision.photoIds.mapIndexed { index, id -> id to destinations[index] }.toMap()
            val decodedById = linkedMapOf<Long, PreparedTile>()
            var failedCandidateId: Long? = null
            var failureForRetry: DecodeFailure? = null
            val decodeOrder = decision.photoIds.sortedBy { if (it == photo.id) 0 else 1 }

            for (id in decodeOrder) {
                val inspected = inspectedById[id] ?: continue
                val resolved = inspected.resolved ?: when (val model = resolvePhoto(inspected.photo, resolveModel)) {
                    is ResolvePhotoResult.Ready -> model.photo
                    is ResolvePhotoResult.Failed -> {
                        if (id == photo.id) return PrepareSlideResult.Failed(model.failure)
                        onCollageCandidateFailure(model.failure)
                        if (model.failure.exceptionClass == "OutOfMemoryError") {
                            decodedById.values.forEach(::discard)
                            return prepareSingle(
                                forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) AspectMode.FIT_COLOR else null,
                                eventReason = "candidate_model_oom",
                                allowTransitionBlur = false,
                            )
                        }
                        failedCandidateId = id
                        failureForRetry = model.failure
                        break
                    }
                }
                val destination = targetById.getValue(id)
                val width = destination.width().roundToInt().coerceAtLeast(1)
                val height = destination.height().roundToInt().coerceAtLeast(1)
                when (val decoded = decodeOwned(resolved, width, height)) {
                    is DecodePhotoResult.Ready -> decodedById[id] = decoded.tile
                    is DecodePhotoResult.Failed -> {
                        if (id == photo.id) {
                            decodedById.values.forEach(::discard)
                            return PrepareSlideResult.Failed(decoded.failure)
                        }
                        onCollageCandidateFailure(decoded.failure)
                        if (decoded.failure.exceptionClass == "OutOfMemoryError") {
                            val anchorTile = decodedById.remove(photo.id)
                            decodedById.values.forEach(::discard)
                            if (anchorTile != null) {
                                val emergency = PreparedSlide.Single(
                                    anchor = photo,
                                    model = resolvedAnchor.model,
                                    bitmap = anchorTile.bitmap,
                                    dataSource = anchorTile.dataSource,
                                    aspectOverride = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) AspectMode.FIT_COLOR else null,
                                )
                                emergency.allBitmaps().forEach(uncommitted::remove)
                                emit(
                                    "COLLAGE_FALLBACK_SINGLE",
                                    prepareMs = SystemClock.elapsedRealtime() - startedAt,
                                    decodedBytes = emergency.decodedBytes,
                                    reason = "candidate_decode_oom",
                                )
                                return PrepareSlideResult.Ready(emergency)
                            }
                            return prepareSingle(
                                forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) AspectMode.FIT_COLOR else null,
                                eventReason = "candidate_decode_oom",
                                allowTransitionBlur = false,
                            )
                        }
                        failedCandidateId = id
                        failureForRetry = decoded.failure
                        break
                    }
                }
            }

            if (failedCandidateId != null) {
                decodedById.values.forEach(::discard)
                mutableCandidates.removeAll { it.meta.id == failedCandidateId }
                if (mutableCandidates.isEmpty()) {
                    return prepareSingle(
                        forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) allowedFallback else null,
                        eventReason = failureForRetry?.reason ?: "insufficient_compatible_photos",
                    )
                }
                continue
            }

            val chosen = decision.photoIds.mapNotNull(decodedById::get)
            if (chosen.size != decision.photoIds.size) {
                chosen.forEach(::discard)
                return prepareSingle(
                    forcedAspect = if (anchorInspected.meta.orientation == PhotoOrientation.PORTRAIT) allowedFallback else null,
                    eventReason = "selected_tile_missing",
                )
            }

            emit(
                event = "COLLAGE_SELECTION_EVALUATED",
                ids = decision.photoIds,
                layout = decision.layout.name,
                reason = decision.decisionReason,
                details = diagnosticsFor(decision),
            )
            if (decision.photoIds.size == 2 && maxCollagePhotos >= 3 && collageMode != PortraitCollageMode.ALWAYS_TWO) {
                emit(
                    "COLLAGE_DOWNGRADED",
                    ids = decision.photoIds,
                    layout = decision.layout.name,
                    reason = decision.decisionReason,
                )
            }
            val result = ready(PreparedSlide.Collage(photo, chosen, decision.layout))
            emit(
                "COLLAGE_READY",
                ids = result.slide.photos.map { it.id },
                layout = decision.layout.name,
                prepareMs = SystemClock.elapsedRealtime() - startedAt,
                decodedBytes = result.slide.decodedBytes,
                reason = decision.decisionReason,
            )
            return result
        }
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        return PrepareSlideResult.Failed(
            decodeFailure(
                photo,
                DecodeFailureStage.IMAGE_LOADER,
                exceptionClass = "OutOfMemoryError",
                reason = "java_heap_exhausted_during_slide_preparation",
            )
        )
    } catch (e: Exception) {
        return PrepareSlideResult.Failed(
            decodeFailure(photo, DecodeFailureStage.UNKNOWN, e.javaClass.simpleName)
        )
    } finally {
        releaseUncommitted()
    }
}

private const val MAX_COLLAGE_CANDIDATES = 12
private const val MAX_LOCAL_COLLAGE_PROBES = 4
private const val MAX_REMOTE_COLLAGE_PROBES = 4
private const val COLLAGE_PROBE_MAX_PX = 256
