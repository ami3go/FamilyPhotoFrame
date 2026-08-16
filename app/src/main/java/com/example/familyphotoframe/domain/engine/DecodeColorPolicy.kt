package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.DecodeColorDepth

/** Pixel format for a decoded slide. Mapped to `android.graphics.Bitmap.Config` at use. */
enum class DecodeColorChoice {
    /** Four bytes per pixel, including an alpha channel photographs never use. */
    ARGB_8888,
    /** Two bytes per pixel. Halves every slide bitmap; bands smooth gradients. */
    RGB_565,
}

/**
 * Chooses the decode pixel format.
 *
 * A 2 GB tablet typically offers a 96–128 MiB Java heap, and up to four slide graphs are
 * alive during a transition. At full screen resolution in ARGB_8888 that is a quarter of
 * such a heap spent on pixels whose alpha channel is uniformly opaque. Dropping to
 * RGB_565 halves it outright, which is more headroom than the whole reactive degradation
 * ladder can recover once pressure has already built.
 *
 * The cost is real, though — banding across a clear sky, and a transparent PNG flattened
 * onto black rather than the frame background — so [DecodeColorDepth.AUTO] spends full
 * colour wherever the heap can carry it and only economises where it cannot. Family
 * photos are JPEG or HEIC, both always opaque, so the alpha case is rare in practice and
 * "Full colour" remains available for anyone it affects.
 *
 * Pure and total, like [PlaybackMemoryPolicy] whose thresholds it reuses.
 */
object DecodeColorPolicy {

    /**
     * @param heapMaxBytes `Runtime.maxMemory()`; non-positive means "unknown", treated as
     *   roomy so an unreadable value never silently degrades a capable device.
     * @param level current guard level; anything past NORMAL means the frame is already
     *   economising and full colour is no longer the priority.
     */
    fun choose(
        preference: DecodeColorDepth,
        heapMaxBytes: Long,
        level: PlaybackMemoryLevel,
    ): DecodeColorChoice = when (preference) {
        DecodeColorDepth.FULL -> DecodeColorChoice.ARGB_8888
        DecodeColorDepth.LOW_MEMORY -> DecodeColorChoice.RGB_565
        DecodeColorDepth.AUTO -> when {
            heapMaxBytes in 1L..PlaybackMemoryPolicy.LOW_HEAP_MAX_BYTES -> DecodeColorChoice.RGB_565
            level != PlaybackMemoryLevel.NORMAL -> DecodeColorChoice.RGB_565
            else -> DecodeColorChoice.ARGB_8888
        }
    }

    /** Bytes one decoded pixel occupies; used for the settings estimate and diagnostics. */
    fun bytesPerPixel(choice: DecodeColorChoice): Int = when (choice) {
        DecodeColorChoice.ARGB_8888 -> 4
        DecodeColorChoice.RGB_565 -> 2
    }
}
