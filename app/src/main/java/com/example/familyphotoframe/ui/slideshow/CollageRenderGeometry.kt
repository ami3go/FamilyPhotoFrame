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
