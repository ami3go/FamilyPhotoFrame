package com.example.familyphotoframe.domain.engine

/** How much room the frame has to work in, decided once at startup. */
enum class DeviceMemoryTier {
    /** An old tablet: small heap, or a platform build that declares itself low-RAM. */
    LOW,
    /** Enough headroom for full-quality defaults. */
    STANDARD;

    val isLow: Boolean get() = this == LOW
}

/**
 * Classifies the device once, so the frame can *start* economical rather than discover
 * it should have been.
 *
 * Everything else in this package reacts: [PlaybackMemoryPolicy] steps playback down
 * after pressure appears, [DecodeColorPolicy] halves pixels once the heap is known to be
 * small. Those only ever fire after the frame is already in difficulty. The signals here
 * are all available before the first photo is decoded, which is the only point at which
 * a smaller baseline can be chosen for free.
 *
 * Pure and total: the caller reads the three Android values, this decides what they mean.
 */
object DeviceMemoryTierPolicy {

    /**
     * Devices sold as "2 GB" report somewhat less than 2 GiB of total RAM once the
     * kernel and reserved regions are subtracted, so the boundary sits below the round
     * number rather than on it.
     */
    const val LOW_TOTAL_RAM_BYTES = 2L * 1024L * 1024L * 1024L

    /**
     * @param lowRamFlagged `ActivityManager.isLowRamDevice`; a platform build saying so
     *   is authoritative and never second-guessed.
     * @param heapMaxBytes `Runtime.maxMemory()`. Non-positive means unknown.
     * @param totalRamBytes `ActivityManager.MemoryInfo.totalMem`. Non-positive means
     *   unknown — an unreadable value must not by itself demote a capable device.
     */
    fun tier(
        lowRamFlagged: Boolean,
        heapMaxBytes: Long,
        totalRamBytes: Long,
    ): DeviceMemoryTier = when {
        lowRamFlagged -> DeviceMemoryTier.LOW
        heapMaxBytes in 1L..PlaybackMemoryPolicy.LOW_HEAP_MAX_BYTES -> DeviceMemoryTier.LOW
        totalRamBytes in 1L..LOW_TOTAL_RAM_BYTES -> DeviceMemoryTier.LOW
        else -> DeviceMemoryTier.STANDARD
    }

    /**
     * Decoded megapixels a slide may use.
     *
     * Decoding at panel resolution is wasted work on a frame: a 1920×1200 tablet spends
     * 8.8 MiB per ARGB_8888 slide to resolve detail nobody standing across a room can
     * see. Capping the decode lets the GPU do the last bit of scaling instead, which it
     * does for free.
     */
    const val LOW_TIER_MAX_DECODE_PIXELS = 1_500_000

    /**
     * Scale to apply to a [width]×[height] decode request, never above 1. Returns the
     * exact ratio rather than a step, so the result stays proportional to the panel.
     */
    fun decodeScaleFor(tier: DeviceMemoryTier, width: Int, height: Int): Float {
        if (tier != DeviceMemoryTier.LOW || width <= 0 || height <= 0) return 1f
        val pixels = width.toLong() * height.toLong()
        if (pixels <= LOW_TIER_MAX_DECODE_PIXELS) return 1f
        // Area scales with the square of the linear scale.
        return kotlin.math.sqrt(LOW_TIER_MAX_DECODE_PIXELS.toDouble() / pixels.toDouble())
            .toFloat()
            .coerceIn(0.1f, 1f)
    }

    /** In-memory diagnostics ring size; the durable file keeps the full history either way. */
    fun diagnosticsRingCapacity(tier: DeviceMemoryTier): Int =
        if (tier.isLow) 400 else 1000
}
