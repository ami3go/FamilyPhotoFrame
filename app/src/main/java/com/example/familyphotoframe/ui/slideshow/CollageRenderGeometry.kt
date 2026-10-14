package com.example.familyphotoframe.ui.slideshow

import android.graphics.RectF
import com.example.familyphotoframe.domain.engine.CollageLayout
import com.example.familyphotoframe.domain.engine.CollageLayoutGeometry

/** Convert normalized optimizer geometry into pixel destinations. */
internal fun collageDestinationRects(
    layout: CollageLayout,
    width: Float,
    height: Float,
    gapPx: Float,
): List<RectF> {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val halfGap = gapPx.coerceAtLeast(0f) / 2f
    return CollageLayoutGeometry.tiles(layout).map { tile ->
        val left = tile.left * safeWidth + if (tile.left > 0f) halfGap else 0f
        val top = tile.top * safeHeight + if (tile.top > 0f) halfGap else 0f
        val right = tile.right * safeWidth - if (tile.right < 1f) halfGap else 0f
        val bottom = tile.bottom * safeHeight - if (tile.bottom < 1f) halfGap else 0f
        val safeLeft = left.coerceIn(0f, (safeWidth - 1f).coerceAtLeast(0f))
        val safeTop = top.coerceIn(0f, (safeHeight - 1f).coerceAtLeast(0f))
        RectF(
            safeLeft,
            safeTop,
            right.coerceAtLeast(safeLeft + 1f).coerceAtMost(safeWidth),
            bottom.coerceAtLeast(safeTop + 1f).coerceAtMost(safeHeight),
        )
    }
}

/**
 * Decode box for a photo shown with crop-to-fill in a [tileWidth]×[tileHeight] tile.
 *
 * Decoding to the tile box with fit semantics leaves a photo whose shape differs from the
 * tile (a landscape in a tall column) far smaller than the tile, and the renderer then
 * stretches it 1.5–3× to fill. The returned box has the photo's aspect and covers the tile,
 * so a fit decode into it lands at, or just above, display resolution. Extreme shapes are
 * bounded to [maxAreaRatio] times the tile area; beyond that they accept some upscale.
 */
internal fun collageCoverDecodeSize(
    tileWidth: Int,
    tileHeight: Int,
    photoAspect: Float,
    maxAreaRatio: Float = COLLAGE_MAX_COVER_AREA_RATIO,
): Pair<Int, Int> {
    val w = tileWidth.coerceAtLeast(1)
    val h = tileHeight.coerceAtLeast(1)
    if (!photoAspect.isFinite() || photoAspect <= 0f) return w to h
    var coverW = maxOf(w.toFloat(), h * photoAspect)
    var coverH = maxOf(h.toFloat(), w / photoAspect)
    val ratio = (coverW * coverH) / (w.toFloat() * h.toFloat())
    if (ratio > maxAreaRatio) {
        val shrink = kotlin.math.sqrt(maxAreaRatio / ratio)
        coverW *= shrink
        coverH *= shrink
    }
    return kotlin.math.ceil(coverW).toInt().coerceAtLeast(1) to kotlin.math.ceil(coverH).toInt().coerceAtLeast(1)
}

internal const val COLLAGE_MAX_COVER_AREA_RATIO = 2f
