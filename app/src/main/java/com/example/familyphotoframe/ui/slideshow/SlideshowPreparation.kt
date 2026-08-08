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
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.domain.engine.CollageLayout
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

private data class ResolvedPhoto(val photo: DisplayPhoto, val model: Any)
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
    onOutOfMemory: () -> Unit,
): Bitmap? = withContext(Dispatchers.Default) {
    try {
        // Quarter-size dimensions retain one sixteenth of full-screen pixels while
        // remaining smooth after the prepared blur is enlarged by the GPU.
        val scale = SOFT_FOCUS_DIMENSION_SCALE
        val width = (targetW.coerceAtLeast(1) * scale).roundToInt().coerceAtLeast(1)
        val height = (targetH.coerceAtLeast(1) * scale).roundToInt().coerceAtLeast(1)
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
                    }
                    val count = slide.tiles.size.coerceAtLeast(1)
                    val tileWidth = (width - gapPx * (count - 1)).coerceAtLeast(1f) / count
                    slide.tiles.forEachIndexed { index, tile ->
                        val left = index * (tileWidth + gapPx)
                        drawCrop(
                            tile.bitmap,
                            android.graphics.RectF(left, 0f, left + tileWidth, height.toFloat()),
                        )
                    }
                }
            }

            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            val blurredPixels = ImageBlur.boxBlur(pixels, width, height)
            Bitmap.createBitmap(blurredPixels, width, height, Bitmap.Config.ARGB_8888)
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
        is PhotoModelResolution.Ready -> ResolvePhotoResult.Ready(ResolvedPhoto(photo, resolution.model))
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
): DecodePhotoResult {
    return try {
        // Request construction is intentionally inside the OOM boundary. The API-22
        // field log proved the previous request-before-try ordering could bypass recovery.
        val request = ImageRequest.Builder(context)
            .data(resolved.model)
            .size(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
            .allowHardware(false)
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

/**
 * Build either a single prepared photo or a complete two/three-photo portrait collage.
 * The current slide remains visible while this function resolves and decodes every tile.
 */
internal suspend fun prepareSlide(
    context: android.content.Context,
    photo: DisplayPhoto,
    imageLoader: ImageLoader,
    resolveModel: suspend (DisplayPhoto) -> PhotoModelResolution,
    loadCollageCandidates: suspend (DisplayPhoto, Int) -> List<DisplayPhoto>,
    collageMode: PortraitCollageMode,
    maxCollagePhotos: Int,
    portraitFallback: PortraitFallback,
    collageGap: CollageGap,
    prepareSoftFocusBlur: Boolean,
    allowBlurredBackground: Boolean,
    excludedIds: Set<Long>,
    targetW: Int,
    targetH: Int,
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
    ) -> Unit,
): PrepareSlideResult {
    // Every decoded bitmap starts as an uncommitted temporary. Only bitmaps placed in
    // the returned PreparedSlide leave this set. This closes several API-22 leaks:
    // landscape orientation probes, rejected collage candidates, and a third tile that
    // the crop scorer downgrades to a two-photo portrait collage.
    val uncommitted = Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())

    suspend fun decodeOwned(
        resolved: ResolvedPhoto,
        width: Int,
        height: Int,
    ): DecodePhotoResult = decodePhoto(context, resolved, imageLoader, width, height).also { result ->
        if (result is DecodePhotoResult.Ready) uncommitted += result.tile.bitmap
    }

    fun releaseUncommitted() {
        uncommitted.toList().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
            uncommitted.remove(bitmap)
        }
    }

    fun discard(tile: PreparedTile) {
        if (uncommitted.remove(tile.bitmap) && !tile.bitmap.isRecycled) {
            tile.bitmap.recycle()
        }
    }

    return try {
        val startedAt = SystemClock.elapsedRealtime()

        suspend fun ready(slide: PreparedSlide): PrepareSlideResult.Ready {
            val blur = if (prepareSoftFocusBlur) {
                createTransitionBlur(slide, targetW, targetH, collageGap) {
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

        val knownOrientation = photo.metadataOrientation()
        val collageEnabled = collageMode != PortraitCollageMode.OFF
        if (!collageEnabled || knownOrientation == PhotoOrientation.LANDSCAPE) {
            return when (val decoded = decodeOwned(resolvedAnchor, targetW, targetH)) {
                is DecodePhotoResult.Ready -> ready(
                    PreparedSlide.Single(photo, resolvedAnchor.model, decoded.tile.bitmap, decoded.tile.dataSource)
                )
                is DecodePhotoResult.Failed -> PrepareSlideResult.Failed(decoded.failure)
            }
        }

        // Half-screen is sufficient for both a two-column tile and a fitted single portrait.
        // Three columns decode slightly larger than strictly necessary, which avoids a second
        // decode when the automatic scorer chooses two columns after inspecting three photos.
        val tileW = (targetW / 2).coerceAtLeast(1)
        val anchorTile = when (val decoded = decodeOwned(resolvedAnchor, tileW, targetH)) {
            is DecodePhotoResult.Ready -> decoded.tile
            is DecodePhotoResult.Failed -> return PrepareSlideResult.Failed(decoded.failure)
        }
        val actualAnchorOrientation = anchorTile.decodedOrientation()
        if (actualAnchorOrientation != PhotoOrientation.PORTRAIT) {
            // Metadata was unknown or misleading. Decode at full display size for normal
            // single-photo playback rather than stretching the half-size probe.
            discard(anchorTile)
            return when (val decoded = decodeOwned(resolvedAnchor, targetW, targetH)) {
                is DecodePhotoResult.Ready -> ready(
                    PreparedSlide.Single(photo, resolvedAnchor.model, decoded.tile.bitmap, decoded.tile.dataSource)
                )
                is DecodePhotoResult.Failed -> PrepareSlideResult.Failed(decoded.failure)
            }
        }

        onCollageEvent(
            "COLLAGE_PRELOAD_STARTED", photo.id, listOf(photo.id), null,
            null, null, null,
        )

        val wanted = maxCollagePhotos.coerceIn(2, 3)
        val candidatePool = loadCollageCandidates(photo, 16)
            .filter {
                it.id != photo.id && it.id !in excludedIds &&
                    it.sourceId == photo.sourceId && it.folderName == photo.folderName
            }
        val portraitTiles = mutableListOf(anchorTile)

        fun memoryFallback(reason: String): PrepareSlideResult.Ready {
            portraitTiles.drop(1).forEach(::discard)
            val emergency = PreparedSlide.Single(
                anchor = photo,
                model = resolvedAnchor.model,
                bitmap = anchorTile.bitmap,
                dataSource = anchorTile.dataSource,
                aspectOverride = AspectMode.FIT_COLOR,
            )
            emergency.allBitmaps().forEach(uncommitted::remove)
            val result = PrepareSlideResult.Ready(emergency)
            onCollageEvent(
                "COLLAGE_FALLBACK_SINGLE", photo.id, listOf(photo.id), null,
                SystemClock.elapsedRealtime() - startedAt, emergency.decodedBytes, reason,
            )
            return result
        }

        for (candidate in candidatePool) {
            if (portraitTiles.size >= wanted) break
            if (candidate.metadataOrientation() == PhotoOrientation.LANDSCAPE) continue
            val resolved = when (val result = resolvePhoto(candidate, resolveModel)) {
                is ResolvePhotoResult.Ready -> result.photo
                is ResolvePhotoResult.Failed -> {
                    onCollageCandidateFailure(result.failure)
                    if (result.failure.exceptionClass == "OutOfMemoryError") {
                        return memoryFallback("candidate_model_oom")
                    }
                    continue
                }
            }
            when (val decoded = decodeOwned(resolved, tileW, targetH)) {
                is DecodePhotoResult.Ready -> {
                    if (decoded.tile.decodedOrientation() == PhotoOrientation.PORTRAIT) {
                        portraitTiles += decoded.tile
                    } else {
                        discard(decoded.tile)
                    }
                }
                is DecodePhotoResult.Failed -> {
                    onCollageCandidateFailure(decoded.failure)
                    if (decoded.failure.exceptionClass == "OutOfMemoryError") {
                        return memoryFallback("candidate_decode_oom")
                    }
                }
            }
        }

        val layout = PortraitCollagePolicy.chooseLayout(
            mode = collageMode,
            screenWidth = targetW,
            screenHeight = targetH,
            portraitRatios = portraitTiles.map { it.aspectRatio() },
            maxPhotos = wanted,
        )

        if (layout == null) {
            val requestedFallback = portraitFallbackAspect(portraitFallback)
            val fallbackMode = if (
                requestedFallback == AspectMode.FIT_BLUR && !allowBlurredBackground
            ) AspectMode.FIT_COLOR else requestedFallback
            val fallbackTile = if (fallbackMode == AspectMode.FILL_CROP) {
                when (val decoded = decodeOwned(resolvedAnchor, targetW, targetH)) {
                    is DecodePhotoResult.Ready -> decoded.tile
                    is DecodePhotoResult.Failed -> anchorTile
                }
            } else anchorTile
            val preparedSlide = PreparedSlide.Single(
                anchor = photo,
                model = resolvedAnchor.model,
                bitmap = fallbackTile.bitmap,
                dataSource = fallbackTile.dataSource,
                aspectOverride = fallbackMode,
            )
            portraitTiles.filter { it.bitmap !== fallbackTile.bitmap }.forEach(::discard)
            val result = ready(preparedSlide)
            onCollageEvent(
                "COLLAGE_FALLBACK_SINGLE", photo.id, listOf(photo.id), null,
                SystemClock.elapsedRealtime() - startedAt, result.slide.decodedBytes,
                if (fallbackMode != requestedFallback) {
                    "memory_guard_solid_background"
                } else {
                    "insufficient_same_folder_portraits"
                },
            )
            return result
        }

        val count = if (layout == CollageLayout.THREE_COLUMNS) 3 else 2
        val chosen = portraitTiles.take(count)
        portraitTiles.drop(count).forEach(::discard)
        if (count < wanted && wanted == 3) {
            onCollageEvent(
                "COLLAGE_DOWNGRADED", photo.id, chosen.map { it.photo.id }, layout.name,
                null, null,
                if (portraitTiles.size < 3) "not_enough_compatible_portraits" else "lower_crop_loss",
            )
        }
        val result = ready(PreparedSlide.Collage(photo, chosen, layout))
        onCollageEvent(
            "COLLAGE_READY", photo.id, result.slide.photos.map { it.id }, layout.name,
            SystemClock.elapsedRealtime() - startedAt, result.slide.decodedBytes, null,
        )
        result
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        PrepareSlideResult.Failed(
            decodeFailure(
                photo,
                DecodeFailureStage.IMAGE_LOADER,
                exceptionClass = "OutOfMemoryError",
                reason = "java_heap_exhausted_during_slide_preparation",
            )
        )
    } catch (e: Exception) {
        PrepareSlideResult.Failed(
            decodeFailure(photo, DecodeFailureStage.UNKNOWN, e.javaClass.simpleName)
        )
    } finally {
        // Immediate recycle is safe here: these allocations were never handed to Compose.
        // Rendered allocations use LegacyBitmapReclaimer's delayed API-21–25 path instead.
        releaseUncommitted()
    }
}
