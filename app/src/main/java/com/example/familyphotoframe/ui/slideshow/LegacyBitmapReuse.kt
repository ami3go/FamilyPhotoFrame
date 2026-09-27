package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Request marker understood only by [LegacyBitmapReuseDecoder]. */
internal data class LegacyBitmapDecodeRequest(val exifOrientation: Int)

internal const val LEGACY_BITMAP_DECODE_PARAMETER =
    "com.example.familyphotoframe.legacyBitmapDecode"

internal data class LegacyBitmapPoolSnapshot(
    val count: Int,
    val bytes: Long,
    val hits: Long,
    val misses: Long,
    val offers: Long,
    val evictions: Long,
)

/**
 * Small process-local pool for fully retired slide bitmaps on API 21-25.
 *
 * The V80 evidence showed balanced logical ownership but nearly four thousand fresh
 * BitmapFactory allocations in one soak. Old ART kept their Dalvik pages committed even
 * after every bitmap was retired. Reusing the pixel storage after the existing display-list
 * grace period removes that allocation churn without retaining a slide, photo, drawable,
 * Compose object, or source identity.
 */
internal class LegacyBitmapReusePool(
    private val enabled: Boolean,
    private val maxCount: Int = DEFAULT_MAX_COUNT,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val lock = Any()
    private val bitmaps = ArrayList<Bitmap>()
    private var pooledBytes = 0L
    private var hits = 0L
    private var misses = 0L
    private var offers = 0L
    private var evictions = 0L

    fun take(minAllocationBytes: Long, config: Bitmap.Config): Bitmap? = synchronized(lock) {
        if (!enabled) return@synchronized null
        var bestIndex = -1
        var bestBytes = Long.MAX_VALUE
        bitmaps.forEachIndexed { index, bitmap ->
            val bytes = bitmap.reuseAllocationBytes()
            if (!bitmap.isRecycled && bitmap.isMutable && bitmap.config == config &&
                bytes >= minAllocationBytes && bytes < bestBytes
            ) {
                bestIndex = index
                bestBytes = bytes
            }
        }
        if (bestIndex < 0) {
            misses++
            null
        } else {
            val bitmap = bitmaps.removeAt(bestIndex)
            pooledBytes = (pooledBytes - bestBytes).coerceAtLeast(0L)
            hits++
            bitmap
        }
    }

    /** Returns true when the pool took ownership; false means the caller must recycle. */
    fun offer(bitmap: Bitmap): Boolean = synchronized(lock) {
        if (!enabled || bitmap.isRecycled || !bitmap.isMutable) return@synchronized false
        if (bitmaps.any { it === bitmap }) return@synchronized true
        val bytes = bitmap.reuseAllocationBytes()
        if (bytes <= 0L || bytes > maxBytes) return@synchronized false
        offers++
        bitmaps += bitmap
        pooledBytes += bytes
        trimLocked()
        bitmaps.any { it === bitmap }
    }

    fun snapshot(): LegacyBitmapPoolSnapshot = synchronized(lock) {
        LegacyBitmapPoolSnapshot(
            count = bitmaps.size,
            bytes = pooledBytes,
            hits = hits,
            misses = misses,
            offers = offers,
            evictions = evictions,
        )
    }

    private fun trimLocked() {
        while (bitmaps.size > maxCount || pooledBytes > maxBytes) {
            // A larger allocation can satisfy every request that a smaller allocation of
            // the same config can satisfy. Keeping FIFO order therefore discards the most
            // reusable buffers whenever varying photo dimensions fill the byte budget. The
            // V80 build-73 soak made that failure mode visible: nearly every pool miss was
            // paired with an eviction even though the pool stayed at its fixed 4 MiB cap.
            // Prefer the smallest buffer dominated by another buffer of the same config.
            // Fall back to the oldest entry only when every retained config/size is unique.
            val dominated = bitmaps.indices.filter { candidateIndex ->
                val candidate = bitmaps[candidateIndex]
                val candidateBytes = candidate.reuseAllocationBytes()
                bitmaps.indices.any { otherIndex ->
                    otherIndex != candidateIndex &&
                        bitmaps[otherIndex].config == candidate.config &&
                        bitmaps[otherIndex].reuseAllocationBytes() >= candidateBytes
                }
            }
            val removalIndex = dominated.minByOrNull { index ->
                bitmaps[index].reuseAllocationBytes()
            } ?: 0
            val removed = bitmaps.removeAt(removalIndex)
            pooledBytes = (pooledBytes - removed.reuseAllocationBytes()).coerceAtLeast(0L)
            evictions++
            if (!removed.isRecycled) runCatching { removed.recycle() }
        }
    }

    private companion object {
        const val DEFAULT_MAX_COUNT = 6
        const val DEFAULT_MAX_BYTES = 4L * 1024L * 1024L
    }
}

internal data class LegacyBitmapDecodeSizing(
    val sampleSize: Int,
    val densityScale: Double,
    val requiredAllocationBytes: Long,
) {
    companion object {
        fun calculate(
            sourceWidth: Int,
            sourceHeight: Int,
            targetWidth: Int,
            targetHeight: Int,
            rotated: Boolean,
            scale: Scale,
            bytesPerPixel: Int,
        ): LegacyBitmapDecodeSizing {
            val effectiveWidth = if (rotated) sourceHeight else sourceWidth
            val effectiveHeight = if (rotated) sourceWidth else sourceHeight
            val widthRatio = effectiveWidth.toDouble() / targetWidth.coerceAtLeast(1)
            val heightRatio = effectiveHeight.toDouble() / targetHeight.coerceAtLeast(1)
            val limitingRatio = if (scale == Scale.FILL) {
                min(widthRatio, heightRatio)
            } else {
                max(widthRatio, heightRatio)
            }
            var sample = 1
            while (sample <= limitingRatio / 2.0 && sample <= Int.MAX_VALUE / 2) sample *= 2

            val sampledEffectiveWidth = ceil(effectiveWidth.toDouble() / sample).coerceAtLeast(1.0)
            val sampledEffectiveHeight = ceil(effectiveHeight.toDouble() / sample).coerceAtLeast(1.0)
            val widthScale = targetWidth.coerceAtLeast(1) / sampledEffectiveWidth
            val heightScale = targetHeight.coerceAtLeast(1) / sampledEffectiveHeight
            val densityScale = if (scale == Scale.FILL) max(widthScale, heightScale)
            else min(widthScale, heightScale)

            // A candidate at the sampled (pre-density) size is always large enough for
            // BitmapFactory's scaled output and avoids device-specific rounding failures.
            val sampledRawWidth = ceil(sourceWidth.toDouble() / sample).toLong().coerceAtLeast(1L)
            val sampledRawHeight = ceil(sourceHeight.toDouble() / sample).toLong().coerceAtLeast(1L)
            val allocationScale = densityScale.coerceAtLeast(1.0)
            val required = ceil(sampledRawWidth * allocationScale).toLong()
                .coerceAtMost(MAX_DIMENSION_FOR_SAFE_MULTIPLY)
                .times(
                    ceil(sampledRawHeight * allocationScale).toLong()
                        .coerceAtMost(MAX_DIMENSION_FOR_SAFE_MULTIPLY)
                )
                .times(bytesPerPixel.coerceAtLeast(1).toLong())
            return LegacyBitmapDecodeSizing(sample, densityScale, required)
        }

        private const val MAX_DIMENSION_FOR_SAFE_MULTIPLY = 1_000_000L
    }
}

/** BitmapFactory decoder selected only for marked slideshow requests on the legacy tier. */
internal class LegacyBitmapReuseDecoder private constructor(
    private val sourceResult: SourceResult,
    private val options: Options,
    private val request: LegacyBitmapDecodeRequest,
    private val pool: LegacyBitmapReusePool,
) : Decoder {

    override suspend fun decode(): DecodeResult = withContext(Dispatchers.IO) {
        val file = sourceResult.source.file().toFile()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "BitmapFactory could not read image bounds"
        }

        val config = options.config.toReusableSoftwareConfig()
        val targetWidth = (options.size.width as? Dimension.Pixels)?.px ?: bounds.outWidth
        val targetHeight = (options.size.height as? Dimension.Pixels)?.px ?: bounds.outHeight
        val exifOrientation = request.exifOrientation.takeIf { it in 1..8 }
            ?: runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotated = exifOrientation in 5..8
        val sizing = LegacyBitmapDecodeSizing.calculate(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            rotated = rotated,
            scale = options.scale,
            bytesPerPixel = config.bytesPerPixel(),
        )

        var candidate = pool.take(sizing.requiredAllocationBytes, config)
        val decoded = try {
            decodeFile(file.absolutePath, config, sizing, candidate)
        } catch (_: IllegalArgumentException) {
            // A few vendor decoders impose stricter inBitmap rules than API 19. Return
            // the untouched candidate and retry once without reuse rather than failing a slide.
            candidate?.let(pool::offer)
            candidate = null
            decodeFile(file.absolutePath, config, sizing, null)
        }
        if (candidate != null && decoded !== candidate) pool.offer(candidate!!)

        val oriented = orient(decoded, exifOrientation, pool, config)
        oriented.setDensity(options.context.resources.displayMetrics.densityDpi)
        DecodeResult(
            drawable = BitmapDrawable(options.context.resources, oriented),
            isSampled = sizing.sampleSize > 1 || sizing.densityScale != 1.0,
        )
    }

    private fun decodeFile(
        path: String,
        config: Bitmap.Config,
        sizing: LegacyBitmapDecodeSizing,
        reuse: Bitmap?,
    ): Bitmap {
        val densityScale = sizing.densityScale.coerceIn(MIN_DENSITY_SCALE, MAX_DENSITY_SCALE)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sizing.sampleSize
            inPreferredConfig = config
            inMutable = true
            inPremultiplied = options.premultipliedAlpha
            if (densityScale != 1.0) {
                inScaled = true
                inDensity = DENSITY_BASE
                inTargetDensity = (DENSITY_BASE * densityScale).roundToInt().coerceAtLeast(1)
            } else {
                inScaled = false
            }
            inBitmap = reuse
        }
        return checkNotNull(BitmapFactory.decodeFile(path, decodeOptions)) {
            "BitmapFactory returned a null bitmap"
        }
    }

    class Factory(
        private val enabled: Boolean,
        private val pool: LegacyBitmapReusePool,
    ) : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!enabled || Build.VERSION.SDK_INT !in 21..25) return null
            val request = options.parameters.value<LegacyBitmapDecodeRequest>(
                LEGACY_BITMAP_DECODE_PARAMETER,
            ) ?: return null
            return LegacyBitmapReuseDecoder(result, options, request, pool)
        }
    }

    private companion object {
        const val DENSITY_BASE = 10_000
        const val MIN_DENSITY_SCALE = 0.01
        const val MAX_DENSITY_SCALE = 100.0
    }
}

private fun Bitmap.Config.toReusableSoftwareConfig(): Bitmap.Config = when (this) {
    Bitmap.Config.RGB_565 -> Bitmap.Config.RGB_565
    else -> Bitmap.Config.ARGB_8888
}

private fun Bitmap.Config.bytesPerPixel(): Int = when (this) {
    Bitmap.Config.RGB_565, Bitmap.Config.ARGB_4444 -> 2
    Bitmap.Config.ALPHA_8 -> 1
    else -> 4
}

private fun Bitmap.reuseAllocationBytes(): Long =
    runCatching { allocationByteCount.toLong() }.getOrElse { byteCount.toLong() }

private fun orient(
    source: Bitmap,
    orientation: Int,
    pool: LegacyBitmapReusePool,
    config: Bitmap.Config,
): Bitmap {
    if (orientation !in 2..8) return source
    val matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }
            8 -> setRotate(-90f)
        }
    }
    val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
    matrix.mapRect(bounds)
    matrix.postTranslate(-bounds.left, -bounds.top)
    val width = bounds.width().roundToInt().coerceAtLeast(1)
    val height = bounds.height().roundToInt().coerceAtLeast(1)
    val requiredBytes = width.toLong() * height.toLong() * config.bytesPerPixel()
    val candidate = pool.take(requiredBytes, config)
    val output = candidate?.let { reusable ->
        runCatching { reusable.reconfigure(width, height, config) }
            .fold(
                onSuccess = { reusable },
                onFailure = {
                    pool.offer(reusable)
                    null
                },
            )
    } ?: Bitmap.createBitmap(width, height, config)
    output.eraseColor(Color.TRANSPARENT)
    Canvas(output).drawBitmap(
        source,
        matrix,
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    if (!pool.offer(source) && !source.isRecycled) source.recycle()
    return output
}
