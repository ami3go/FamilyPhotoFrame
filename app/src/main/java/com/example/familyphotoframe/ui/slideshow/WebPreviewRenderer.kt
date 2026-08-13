package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.ui.render.ImageBlur
import com.example.familyphotoframe.web.WebPreviewFrame
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Builds one bounded web-preview JPEG from an already-owned prepared presentation. */
internal suspend fun createWebPreviewFrame(
    slide: PreparedSlide,
    aspectMode: AspectMode,
    backgroundColorArgb: Long,
    collageGap: CollageGap,
    transition: String,
    targetW: Int,
    targetH: Int,
    onOutOfMemory: () -> Unit,
): WebPreviewFrame? = withContext(Dispatchers.Default) {
    var output: Bitmap? = null
    try {
        val sourceW = targetW.coerceAtLeast(1)
        val sourceH = targetH.coerceAtLeast(1)
        var width = minOf(960, sourceW)
        var height = (width.toLong() * sourceH / sourceW).toInt().coerceAtLeast(1)
        if (height > 540) {
            height = 540
            width = (height.toLong() * sourceW / sourceH).toInt().coerceAtLeast(1)
        }

        val previewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output = previewBitmap
        val canvas = android.graphics.Canvas(previewBitmap)
        canvas.drawColor(backgroundColorArgb.toInt())
        val paint = android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG,
        )

        fun sourceCrop(bitmap: Bitmap, destination: android.graphics.RectF): android.graphics.Rect {
            val sourceAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            val destinationAspect = destination.width() / destination.height().coerceAtLeast(1f)
            return if (sourceAspect > destinationAspect) {
                val cropWidth = (bitmap.height * destinationAspect).roundToInt().coerceIn(1, bitmap.width)
                val left = (bitmap.width - cropWidth) / 2
                android.graphics.Rect(left, 0, left + cropWidth, bitmap.height)
            } else {
                val cropHeight = (bitmap.width / destinationAspect).roundToInt().coerceIn(1, bitmap.height)
                val top = (bitmap.height - cropHeight) / 2
                android.graphics.Rect(0, top, bitmap.width, top + cropHeight)
            }
        }

        fun drawCrop(bitmap: Bitmap, destination: android.graphics.RectF) {
            canvas.drawBitmap(bitmap, sourceCrop(bitmap, destination), destination, paint)
        }

        fun drawFit(bitmap: Bitmap, destination: android.graphics.RectF) {
            val scale = minOf(
                destination.width() / bitmap.width.coerceAtLeast(1),
                destination.height() / bitmap.height.coerceAtLeast(1),
            )
            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale
            val left = destination.left + (destination.width() - drawW) / 2f
            val top = destination.top + (destination.height() - drawH) / 2f
            canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + drawW, top + drawH), paint)
        }

        fun drawPreviewBlur(bitmap: Bitmap, destination: android.graphics.RectF) {
            val scale = minOf(1f, 180f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
            val smallW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val smallH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
            try {
                val pixels = IntArray(smallW * smallH)
                small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
                val blurredPixels = ImageBlur.boxBlur(pixels, smallW, smallH)
                val blurred = Bitmap.createBitmap(blurredPixels, smallW, smallH, Bitmap.Config.ARGB_8888)
                try {
                    drawCrop(blurred, destination)
                    canvas.drawColor(android.graphics.Color.argb(50, 0, 0, 0))
                } finally {
                    if (!blurred.isRecycled) blurred.recycle()
                }
            } finally {
                if (small !== bitmap && !small.isRecycled) small.recycle()
            }
        }

        val full = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
        when (slide) {
            is PreparedSlide.Single -> when (slide.aspectOverride ?: aspectMode) {
                AspectMode.FILL_CROP -> drawCrop(slide.bitmap, full)
                AspectMode.FIT_COLOR -> drawFit(slide.bitmap, full)
                AspectMode.FIT_BLUR -> {
                    drawPreviewBlur(slide.bitmap, full)
                    drawFit(slide.bitmap, full)
                }
            }
            is PreparedSlide.Collage -> {
                val gapPx = when (collageGap) {
                    CollageGap.NONE -> 0f
                    CollageGap.SMALL -> 4f
                    CollageGap.MEDIUM -> 8f
                }
                val rects = collageTileRects(
                    layout = slide.layout,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    gap = gapPx,
                )
                slide.tiles.zip(rects).forEach { (tile, rect) ->
                    drawCrop(
                        tile.bitmap,
                        android.graphics.RectF(rect.left, rect.top, rect.right, rect.bottom),
                    )
                }
            }
        }

        val bytes = ByteArrayOutputStream().use { stream ->
            if (!previewBitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)) return@withContext null
            stream.toByteArray()
        }
        val photoIds = slide.photos.map { it.id }
        val type = when (slide) {
            is PreparedSlide.Single -> if (slide.aspectOverride != null) "portrait_fallback" else "single"
            is PreparedSlide.Collage -> "collage_${slide.tiles.size}"
        }
        val revisionKey = buildString {
            append(slide.anchor.id).append('|')
            append(photoIds.joinToString(",")).append('|')
            append((slide as? PreparedSlide.Collage)?.layout?.name.orEmpty()).append('|')
            append(aspectMode.name).append('|')
            append(backgroundColorArgb).append('|')
            append(collageGap.name).append('|')
            append(width).append('x').append(height)
        }
        WebPreviewFrame(
            revision = Integer.toHexString(revisionKey.hashCode()),
            jpeg = bytes,
            presentationId = slide.anchor.id,
            type = type,
            photoIds = photoIds,
            fileName = slide.anchor.fileName,
            sourceId = slide.anchor.sourceId,
            folder = slide.anchor.folderName,
            transition = transition,
            committedAtEpochMs = System.currentTimeMillis(),
            width = width,
            height = height,
        )
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        onOutOfMemory()
        null
    } catch (_: Exception) {
        null
    } finally {
        output?.takeIf { !it.isRecycled }?.recycle()
    }
}
