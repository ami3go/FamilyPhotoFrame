package com.example.familyphotoframe.ui.render

/**
 * One frame of a Ken Burns move: a uniform zoom plus a pan expressed as a fraction of
 * the view size (so the caller multiplies by pixel width/height).
 */
data class KenBurnsFrame(
    val scale: Float,
    val translateXFraction: Float,
    val translateYFraction: Float,
)

/**
 * Ken Burns pan/zoom geometry (spec §4 Phase 2).
 *
 * Deliberately pure arithmetic with no Compose or Android types: the caller reads it
 * inside a `graphicsLayer` lambda so the animation is GPU-composited and never
 * triggers per-frame recomposition (spec §10.3, §2.2).
 *
 * The pan is bounded by the zoom: at any progress the translation stays within
 * `(scale - 1) / 2` of the view, so the image always covers the viewport and no blank
 * edge can appear mid-move.
 */
object KenBurns {

    const val DEFAULT_START_SCALE = 1.0f

    /** Kept small on purpose: a slow, subtle drift reads as premium; big moves look cheap and cost fill rate. */
    const val DEFAULT_END_SCALE = 1.08f

    /** Four diagonal directions; the seed picks one so a photo always moves the same way. */
    private val DIRECTIONS = arrayOf(
        1f to 1f,
        1f to -1f,
        -1f to 1f,
        -1f to -1f,
    )

    fun frameFor(
        seed: Long,
        progress: Float,
        startScale: Float = DEFAULT_START_SCALE,
        endScale: Float = DEFAULT_END_SCALE,
    ): KenBurnsFrame {
        val p = progress.coerceIn(0f, 1f)
        val scale = startScale + (endScale - startScale) * p
        // Largest pan that still keeps the scaled image covering the viewport.
        val maxPan = ((scale - 1f) / 2f).coerceAtLeast(0f)
        val (dirX, dirY) = DIRECTIONS[directionIndex(seed)]
        return KenBurnsFrame(
            scale = scale,
            translateXFraction = dirX * maxPan * p,
            translateYFraction = dirY * maxPan * p,
        )
    }

    /** Stable per-photo direction; mixes the seed so adjacent ids don't all move alike. */
    fun directionIndex(seed: Long): Int {
        var x = seed * -7046029254386353131L
        x = x xor (x ushr 32)
        return ((x ushr 3).toInt() and 0x7FFFFFFF) % DIRECTIONS.size
    }
}
