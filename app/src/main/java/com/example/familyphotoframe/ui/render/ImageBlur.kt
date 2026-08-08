package com.example.familyphotoframe.ui.render

/**
 * Low-resolution box blur used for the `fit_blur` backdrop (spec §10.4).
 *
 * Contract Rule / spec §10.2 forbids full-resolution blur: the source bitmap must be
 * heavily downsampled first (see [MAX_DIMENSION_PX]) and then scaled back up by the
 * GPU, which is both cheaper and softer than blurring at display size. Old TV boxes
 * and weak vendor GPUs are an explicit target (spec §2.2), so this runs on a bitmap of
 * a few thousand pixels, not a few million.
 *
 * Implemented as two separable box passes with a running sum, so cost is O(pixels)
 * per pass and independent of radius. Pure Kotlin with no Android types, so the
 * arithmetic is unit-testable on the JVM.
 */
object ImageBlur {

    /** Longest edge of the downsampled bitmap fed into the blur (spec §10.2: ≤ 256 px). */
    const val MAX_DIMENSION_PX = 64

    /** Default blur radius in downsampled pixels. */
    const val DEFAULT_RADIUS = 6

    /**
     * Blur [pixels] (ARGB_8888, row-major, length == width * height) in place-safe
     * fashion, returning a new array. Alpha is preserved per pixel.
     */
    fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int = DEFAULT_RADIUS): IntArray {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(pixels.size == width * height) { "pixels must be width * height" }
        if (radius <= 0) return pixels.copyOf()

        val horizontal = blurPass(pixels, width, height, radius, horizontal = true)
        return blurPass(horizontal, width, height, radius, horizontal = false)
    }

    private fun blurPass(
        src: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean,
    ): IntArray {
        val out = IntArray(src.size)
        val lineLength = if (horizontal) width else height
        val lineCount = if (horizontal) height else width
        val effectiveRadius = radius.coerceAtMost(lineLength - 1).coerceAtLeast(0)
        if (effectiveRadius == 0) return src.copyOf()

        for (line in 0 until lineCount) {
            fun indexOf(offset: Int) = if (horizontal) line * width + offset else offset * width + line

            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            var count = 0

            // Prime the window over [0, radius].
            for (i in 0..effectiveRadius.coerceAtMost(lineLength - 1)) {
                val p = src[indexOf(i)]
                sumA += (p ushr 24) and 0xFF
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
                count++
            }

            for (offset in 0 until lineLength) {
                out[indexOf(offset)] =
                    ((sumA / count) shl 24) or
                        ((sumR / count) shl 16) or
                        ((sumG / count) shl 8) or
                        (sumB / count)

                // Slide the window: add the entering pixel, drop the leaving one.
                val entering = offset + effectiveRadius + 1
                if (entering < lineLength) {
                    val p = src[indexOf(entering)]
                    sumA += (p ushr 24) and 0xFF
                    sumR += (p ushr 16) and 0xFF
                    sumG += (p ushr 8) and 0xFF
                    sumB += p and 0xFF
                    count++
                }
                val leaving = offset - effectiveRadius
                if (leaving >= 0) {
                    val p = src[indexOf(leaving)]
                    sumA -= (p ushr 24) and 0xFF
                    sumR -= (p ushr 16) and 0xFF
                    sumG -= (p ushr 8) and 0xFF
                    sumB -= p and 0xFF
                    count--
                }
            }
        }
        return out
    }
}
