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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
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
    val budgetBytes: Long,
    val hits: Long,
    val misses: Long,
    val offers: Long,
    val evictions: Long,
    val rejectedOffers: Long,
    val reuseRejects: Long,
    val adaptiveTrims: Long,
    val pressureTrims: Long,
    val canonicalAllocations: Long,
    val canonicalAllocationFailures: Long,
    val pressureConstrained: Boolean,
    val arenaAllocatedSlots: Int,
    val arenaActiveSlots: Int,
    val arenaWaits: Long,
    val arenaOversizedRequests: Long,
    val arenaStandardSlots: Int,
    val arenaLargeSlots: Int,
    val requestBuckets: String,
    val hitBuckets: String,
    val missBuckets: String,
    val evictionBuckets: String,
)

/**
 * Fixed process-local decoded-pixel arena for API 21-25.
 *
 * A buffer can be FREE in [bitmaps] or leased to one PreparedSlide/decode operation. The
 * total of both states can never exceed six. A decoder that arrives while every compatible
 * slot is leased suspends until the existing display-list grace period returns one; it never
 * creates a seventh exact-size bitmap. Four 2 MiB standard slots and two 4 MiB large slots
 * cover the observed full-screen/collage workload without retaining a photo, drawable,
 * Compose object, or source identity.
 */
internal class LegacyBitmapReusePool(
    private val enabled: Boolean,
    private val standardSlotCount: Int = DEFAULT_STANDARD_SLOT_COUNT,
    private val largeSlotCount: Int = DEFAULT_LARGE_SLOT_COUNT,
    private val standardSlotBytes: Long = DEFAULT_STANDARD_SLOT_BYTES,
    private val largeSlotBytes: Long = DEFAULT_LARGE_SLOT_BYTES,
) {
    private enum class SlotClass { STANDARD, LARGE }

    private val lock = Any()
    private val bitmaps = ArrayList<Bitmap>()
    private val ownedSlots = java.util.IdentityHashMap<Bitmap, SlotClass>()
    private val available = Channel<Unit>(capacity = Channel.CONFLATED)
    private var pooledBytes = 0L
    private var hits = 0L
    private var misses = 0L
    private var offers = 0L
    private var evictions = 0L
    private var rejectedOffers = 0L
    private var reuseRejects = 0L
    private var adaptiveTrims = 0L
    private var pressureTrims = 0L
    private var canonicalAllocations = 0L
    private var canonicalAllocationFailures = 0L
    private var pressureConstrained = false
    private var standardSlotsAllocated = 0
    private var largeSlotsAllocated = 0
    private var arenaWaits = 0L
    private var arenaOversizedRequests = 0L
    private var totalRequests = 0L
    private var requestsSinceDemandDecay = 0
    private val recentDemand = LongArray(TELEMETRY_BUCKET_COUNT)
    private val requestBuckets = LongArray(TELEMETRY_BUCKET_COUNT)
    private val hitBuckets = LongArray(TELEMETRY_BUCKET_COUNT)
    private val missBuckets = LongArray(TELEMETRY_BUCKET_COUNT)
    private val evictionBuckets = LongArray(TELEMETRY_BUCKET_COUNT)

    fun take(minAllocationBytes: Long, config: Bitmap.Config): Bitmap? = synchronized(lock) {
        takeLocked(minAllocationBytes, config, recordRequest = true)
    }

    private fun takeLocked(
        minAllocationBytes: Long,
        config: Bitmap.Config,
        recordRequest: Boolean,
    ): Bitmap? {
        if (!enabled) return null
        val requestBucket = telemetryBucket(minAllocationBytes, config)
        if (recordRequest) recordDemandLocked(requestBucket)
        var bestIndex = -1
        var bestBytes = Long.MAX_VALUE
        bitmaps.forEachIndexed { index, bitmap ->
            val bytes = bitmap.reuseAllocationBytes()
            if (!bitmap.isRecycled && bitmap.isMutable &&
                bytes >= minAllocationBytes && bytes < bestBytes
            ) {
                bestIndex = index
                bestBytes = bytes
            }
        }
        return if (bestIndex < 0) {
            if (recordRequest) {
                misses++
                missBuckets[requestBucket]++
            }
            null
        } else {
            val bitmap = bitmaps.removeAt(bestIndex)
            pooledBytes = (pooledBytes - bestBytes).coerceAtLeast(0L)
            if (recordRequest) {
                hits++
                hitBuckets[requestBucket]++
            }
            bitmap
        }
    }

    /**
     * Lease a stable-capacity arena slot. Once all six slots exist, this function can only
     * return one of those identities. Waiting is cancellation-safe and does not block a
     * thread; the preparation watchdog remains authoritative if display retirement stalls.
     */
    suspend fun acquire(minAllocationBytes: Long, config: Bitmap.Config): Bitmap? {
        if (!enabled || minAllocationBytes <= 0L) return null
        var recorded = false
        var waited = false
        while (true) {
            var reservation: SlotClass? = null
            val reused = synchronized(lock) {
                val taken = takeLocked(
                    minAllocationBytes = minAllocationBytes,
                    config = config,
                    recordRequest = !recorded,
                )
                recorded = true
                if (taken != null) return@synchronized taken
                reservation = reserveSlotLocked(minAllocationBytes)
                if (reservation == null && minAllocationBytes > largeSlotBytes) {
                    arenaOversizedRequests++
                } else if (reservation == null && !waited) {
                    arenaWaits++
                    waited = true
                }
                null
            }
            if (reused != null) {
                if (reused.config == config || reconfigureSlot(reused, config)) return reused
                // A vendor bitmap implementation refused a legal API-19+ config change.
                // Return the identity and fail this preparation without allocating around
                // the arena; a later request using its original config can still reuse it.
                offer(reused)
                synchronized(lock) { canonicalAllocationFailures++ }
                return null
            }
            if (reservation == null) {
                if (minAllocationBytes > largeSlotBytes) return null
                available.receive()
                continue
            }
            val slotClass = checkNotNull(reservation)
            val capacity = if (slotClass == SlotClass.STANDARD) {
                standardSlotBytes
            } else {
                largeSlotBytes
            }
            val created = createSlot(capacity, config)
            synchronized(lock) {
                if (created == null) {
                    releaseReservationLocked(slotClass)
                    canonicalAllocationFailures++
                    available.trySend(Unit)
                } else {
                    ownedSlots[created] = slotClass
                    canonicalAllocations++
                }
            }
            return created
        }
    }

    /** Return a leased arena slot. Foreign/exact-size bitmaps are never admitted. */
    fun offer(bitmap: Bitmap): Boolean = synchronized(lock) {
        if (!enabled || bitmap.isRecycled || !bitmap.isMutable || ownedSlots[bitmap] == null) {
            rejectedOffers++
            return@synchronized false
        }
        if (bitmaps.any { it === bitmap }) return@synchronized true
        val bytes = bitmap.reuseAllocationBytes()
        if (bytes <= 0L) {
            rejectedOffers++
            return@synchronized false
        }
        offers++
        bitmaps += bitmap
        pooledBytes += bytes
        available.trySend(Unit)
        true
    }

    fun snapshot(): LegacyBitmapPoolSnapshot = synchronized(lock) {
        LegacyBitmapPoolSnapshot(
            count = bitmaps.size,
            bytes = pooledBytes,
            budgetBytes = arenaBudgetBytes(),
            hits = hits,
            misses = misses,
            offers = offers,
            evictions = evictions,
            rejectedOffers = rejectedOffers,
            reuseRejects = reuseRejects,
            adaptiveTrims = adaptiveTrims,
            pressureTrims = pressureTrims,
            canonicalAllocations = canonicalAllocations,
            canonicalAllocationFailures = canonicalAllocationFailures,
            pressureConstrained = pressureConstrained,
            arenaAllocatedSlots = ownedSlots.size,
            arenaActiveSlots = (ownedSlots.size - bitmaps.size).coerceAtLeast(0),
            arenaWaits = arenaWaits,
            arenaOversizedRequests = arenaOversizedRequests,
            arenaStandardSlots = standardSlotsAllocated,
            arenaLargeSlots = largeSlotsAllocated,
            requestBuckets = requestBuckets.joinToString("+"),
            hitBuckets = hitBuckets.joinToString("+"),
            missBuckets = missBuckets.joinToString("+"),
            evictionBuckets = evictionBuckets.joinToString("+"),
        )
    }

    fun recordDecoderReuseRejection() = synchronized(lock) {
        reuseRejects++
    }

    /**
     * Pressure is sticky telemetry for this fixed arena. Unlike the former elastic pool,
     * there is no recovery expansion and no free-slot eviction/reallocation cycle.
     */
    fun setMemoryPressureConstrained(constrained: Boolean) = synchronized(lock) {
        if (!enabled) return@synchronized
        if (constrained) pressureConstrained = true
    }

    private fun reserveSlotLocked(minAllocationBytes: Long): SlotClass? {
        if (minAllocationBytes <= standardSlotBytes &&
            standardSlotsAllocated < standardSlotCount
        ) {
            standardSlotsAllocated++
            return SlotClass.STANDARD
        }
        if (minAllocationBytes <= largeSlotBytes && largeSlotsAllocated < largeSlotCount) {
            largeSlotsAllocated++
            return SlotClass.LARGE
        }
        return null
    }

    private fun releaseReservationLocked(slotClass: SlotClass) {
        if (slotClass == SlotClass.STANDARD) {
            standardSlotsAllocated = (standardSlotsAllocated - 1).coerceAtLeast(0)
        } else {
            largeSlotsAllocated = (largeSlotsAllocated - 1).coerceAtLeast(0)
        }
    }

    private fun createSlot(capacityBytes: Long, config: Bitmap.Config): Bitmap? {
        val bytesPerPixel = config.bytesPerPixel().coerceAtLeast(1)
        val pixels = capacityBytes / bytesPerPixel
        val width = minOf(CANONICAL_ROW_PIXELS.toLong(), pixels).toInt().coerceAtLeast(1)
        val height = (pixels / width).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createBitmap(width, height, config) }.getOrNull()
    }

    private fun reconfigureSlot(bitmap: Bitmap, config: Bitmap.Config): Boolean {
        val capacityBytes = bitmap.reuseAllocationBytes()
        val bytesPerPixel = config.bytesPerPixel().coerceAtLeast(1)
        val pixels = capacityBytes / bytesPerPixel
        val width = minOf(CANONICAL_ROW_PIXELS.toLong(), pixels).toInt().coerceAtLeast(1)
        val height = (pixels / width).toInt().coerceAtLeast(1)
        return runCatching { bitmap.reconfigure(width, height, config) }.isSuccess
    }

    private fun arenaBudgetBytes(): Long =
        standardSlotCount * standardSlotBytes + largeSlotCount * largeSlotBytes

    private fun recordDemandLocked(bucket: Int) {
        if (requestsSinceDemandDecay >= DEMAND_DECAY_INTERVAL) {
            recentDemand.indices.forEach { index ->
                recentDemand[index] = (recentDemand[index] + 1L) / 2L
            }
            requestsSinceDemandDecay = 0
        }
        recentDemand[bucket]++
        requestBuckets[bucket]++
        totalRequests++
        requestsSinceDemandDecay++
    }

    private fun telemetryBucket(bytes: Long, config: Bitmap.Config?): Int {
        val sizeBucket = reusableSizeBucket(bytes)
        return bitmapConfigIndex(config) * BUCKETS_PER_CONFIG + sizeBucket
    }

    private fun reusableSizeBucket(bytes: Long): Int = if (bytes <= 0L) {
        0
    } else {
        ((bytes - 1L) / SIZE_BUCKET_BYTES)
            .coerceAtMost(OVERFLOW_SIZE_BUCKET.toLong())
            .toInt()
    }

    private fun bitmapConfigIndex(config: Bitmap.Config?): Int =
        if (config == Bitmap.Config.RGB_565) 0 else 1

    private companion object {
        const val DEFAULT_STANDARD_SLOT_COUNT = 4
        const val DEFAULT_LARGE_SLOT_COUNT = 2
        const val DEFAULT_STANDARD_SLOT_BYTES = 2L * 1024L * 1024L
        const val DEFAULT_LARGE_SLOT_BYTES = 4L * 1024L * 1024L
        const val SIZE_BUCKET_BYTES = 512L * 1024L
        const val REUSABLE_SIZE_BUCKET_COUNT = 8
        const val OVERFLOW_SIZE_BUCKET = REUSABLE_SIZE_BUCKET_COUNT
        const val BUCKETS_PER_CONFIG = REUSABLE_SIZE_BUCKET_COUNT + 1
        const val CONFIG_COUNT = 2
        const val TELEMETRY_BUCKET_COUNT = CONFIG_COUNT * BUCKETS_PER_CONFIG
        const val DEMAND_DECAY_INTERVAL = 256
        const val CANONICAL_ROW_PIXELS = 1024
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

            // Reserve the actual density-scaled decoder output rather than the larger
            // pre-density intermediate. The arena class ceiling supplies 0.5-2 MiB of
            // rounding headroom, while avoiding false 4+ MiB requests for a 1 MiB tile.
            val sampledRawWidth = ceil(sourceWidth.toDouble() / sample).toLong().coerceAtLeast(1L)
            val sampledRawHeight = ceil(sourceHeight.toDouble() / sample).toLong().coerceAtLeast(1L)
            val allocationScale = densityScale.coerceAtLeast(MIN_OUTPUT_SCALE)
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
        private const val MIN_OUTPUT_SCALE = 0.01
    }
}

/** BitmapFactory decoder selected only for marked slideshow requests on the legacy tier. */
internal class LegacyBitmapReuseDecoder private constructor(
    private val sourceResult: SourceResult,
    private val options: Options,
    private val request: LegacyBitmapDecodeRequest,
    private val pool: LegacyBitmapReusePool,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        // withContext has prompt cancellation. Keep the final arena lease here until the
        // DecodeResult is actually delivered to Coil so cancellation on the dispatcher return
        // hop cannot permanently remove one of the six fixed identities from the arena.
        val undelivered = AtomicReference<Bitmap?>(null)
        try {
            val result = withContext(Dispatchers.IO) {
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

                val candidate = checkNotNull(pool.acquire(sizing.requiredAllocationBytes, config)) {
                    "legacy bitmap arena cannot represent requested output"
                }
                var owned: Bitmap? = candidate
                try {
                    val decoded = try {
                        decodeFile(file.absolutePath, config, sizing, candidate)
                    } catch (error: IllegalArgumentException) {
                        // Never fall back to an exact-size seventh allocation. A transient
                        // decode failure keeps the current presentation visible and leaves
                        // the arena bounded.
                        pool.recordDecoderReuseRejection()
                        throw error
                    }
                    if (decoded !== candidate) {
                        pool.offer(candidate)
                        owned = decoded
                    }

                    // orient assumes ownership of decoded and returns the one surviving lease.
                    // It returns every lease itself if rotation fails or is cancelled.
                    owned = null
                    val oriented = orient(decoded, exifOrientation, pool, config)
                    owned = oriented
                    oriented.setDensity(options.context.resources.displayMetrics.densityDpi)
                    undelivered.set(oriented)
                    DecodeResult(
                        drawable = BitmapDrawable(options.context.resources, oriented),
                        isSampled = sizing.sampleSize > 1 || sizing.densityScale != 1.0,
                    )
                } catch (error: Throwable) {
                    owned?.let { bitmap ->
                        if (!pool.offer(bitmap) && !bitmap.isRecycled) bitmap.recycle()
                    }
                    throw error
                }
            }
            undelivered.set(null)
            return result
        } finally {
            undelivered.getAndSet(null)?.let { bitmap ->
                if (!pool.offer(bitmap) && !bitmap.isRecycled) bitmap.recycle()
            }
        }
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

private suspend fun orient(
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
    var output: Bitmap? = null
    return try {
        output = checkNotNull(pool.acquire(requiredBytes, config)) {
            "legacy bitmap arena cannot represent oriented output"
        }
        val target = checkNotNull(output)
        target.reconfigure(width, height, config)
        target.eraseColor(Color.TRANSPARENT)
        Canvas(target).drawBitmap(
            source,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        if (!pool.offer(source) && !source.isRecycled) source.recycle()
        target
    } catch (error: Throwable) {
        output?.let { bitmap ->
            if (!pool.offer(bitmap) && !bitmap.isRecycled) bitmap.recycle()
        }
        if (!pool.offer(source) && !source.isRecycled) source.recycle()
        throw error
    }
}
