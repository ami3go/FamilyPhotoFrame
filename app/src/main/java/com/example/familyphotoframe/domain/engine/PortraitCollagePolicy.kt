package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.PortraitCollageMode
import kotlin.math.abs

/** Pure orientation and layout scoring used by portrait-collage playback. */
enum class PhotoOrientation { PORTRAIT, LANDSCAPE, SQUARE_OR_UNKNOWN }

enum class CollageLayout { TWO_COLUMNS, THREE_COLUMNS }

object PortraitCollagePolicy {
    private const val PORTRAIT_MAX_RATIO = 0.90f
    private const val LANDSCAPE_MIN_RATIO = 1.10f

    /** EXIF orientations 5–8 rotate the stored pixel axes by 90 degrees. */
    fun effectiveDimensions(width: Int?, height: Int?, exifOrientation: Int): Pair<Int, Int>? {
        val w = width?.takeIf { it > 0 } ?: return null
        val h = height?.takeIf { it > 0 } ?: return null
        return if (exifOrientation in 5..8) h to w else w to h
    }

    fun classify(width: Int?, height: Int?, exifOrientation: Int = 0): PhotoOrientation {
        val (w, h) = effectiveDimensions(width, height, exifOrientation)
            ?: return PhotoOrientation.SQUARE_OR_UNKNOWN
        return classify(w, h)
    }

    fun classify(width: Int, height: Int): PhotoOrientation {
        if (width <= 0 || height <= 0) return PhotoOrientation.SQUARE_OR_UNKNOWN
        val ratio = width.toFloat() / height.toFloat()
        return when {
            ratio <= PORTRAIT_MAX_RATIO -> PhotoOrientation.PORTRAIT
            ratio >= LANDSCAPE_MIN_RATIO -> PhotoOrientation.LANDSCAPE
            else -> PhotoOrientation.SQUARE_OR_UNKNOWN
        }
    }

    fun aspectRatio(width: Int, height: Int): Float =
        if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f

    /**
     * Pick the equal-column layout with the lowest average centre-crop loss.
     * A small complexity penalty prevents three columns winning on imperceptible gains.
     */
    fun chooseLayout(
        mode: PortraitCollageMode,
        screenWidth: Int,
        screenHeight: Int,
        portraitRatios: List<Float>,
        maxPhotos: Int,
    ): CollageLayout? {
        val available = portraitRatios.size.coerceAtMost(maxPhotos.coerceIn(2, 3))
        if (available < 2 || mode == PortraitCollageMode.OFF) return null
        if (available == 2 || maxPhotos <= 2 || mode == PortraitCollageMode.ALWAYS_TWO) {
            return CollageLayout.TWO_COLUMNS
        }
        if (mode == PortraitCollageMode.PREFER_THREE) return CollageLayout.THREE_COLUMNS

        val screenRatio = aspectRatio(screenWidth, screenHeight)
        val two = cropLoss(portraitRatios.take(2), screenRatio / 2f)
        val three = cropLoss(portraitRatios.take(3), screenRatio / 3f) + 0.025f
        return if (three + 0.01f < two) CollageLayout.THREE_COLUMNS else CollageLayout.TWO_COLUMNS
    }

    private fun cropLoss(ratios: List<Float>, tileRatio: Float): Float {
        if (ratios.isEmpty() || tileRatio <= 0f) return Float.MAX_VALUE
        return ratios.map { ratio ->
            val r = ratio.coerceAtLeast(0.01f)
            val retained = if (r >= tileRatio) tileRatio / r else r / tileRatio
            1f - retained.coerceIn(0f, 1f)
        }.average().toFloat()
    }

    /** Candidate ordering: avoid recent repeats first, then keep capture times coherent. */
    fun candidateScore(anchorTime: Long, candidate: DisplayPhoto, now: Long): Double {
        val candidateTime = candidate.dateTakenEpochMs ?: candidate.fileModifiedEpochMs
        val timeDistanceHours = abs(candidateTime - anchorTime).toDouble() / 3_600_000.0
        val recentPenalty = candidate.lastShownAtEpochMs?.let { shown ->
            val ageHours = (now - shown).coerceAtLeast(0L).toDouble() / 3_600_000.0
            if (ageHours < 24.0) (24.0 - ageHours) * 5.0 else 0.0
        } ?: 0.0
        return recentPenalty + timeDistanceHours.coerceAtMost(24.0 * 30.0)
    }
}
