package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.domain.engine.CollageLayout
import kotlin.math.max

/** Pixel-space rectangle shared by bitmap preparation, previews, and Compose rendering. */
internal data class CollageTileRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

/**
 * Deterministic geometry for the deliberately small layout set. The gap is inserted
 * between adjacent tiles; the outer edge always remains flush with the display.
 */
internal fun collageTileRects(
    layout: CollageLayout,
    width: Float,
    height: Float,
    gap: Float,
): List<CollageTileRect> {
    val w = max(1f, width)
    val h = max(1f, height)
    val g = gap.coerceIn(0f, minOf(w, h) / 3f)
    return when (layout) {
        CollageLayout.TWO_COLUMNS -> {
            val tileW = ((w - g) / 2f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, tileW, h),
                CollageTileRect(tileW + g, 0f, w, h),
            )
        }
        CollageLayout.TWO_ROWS -> {
            val tileH = ((h - g) / 2f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, w, tileH),
                CollageTileRect(0f, tileH + g, w, h),
            )
        }
        CollageLayout.THREE_COLUMNS -> {
            val tileW = ((w - 2f * g) / 3f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, tileW, h),
                CollageTileRect(tileW + g, 0f, tileW * 2f + g, h),
                CollageTileRect(tileW * 2f + g * 2f, 0f, w, h),
            )
        }
        CollageLayout.FULL_LEFT_STACK_RIGHT -> {
            val leftW = ((w - g) / 2f).coerceAtLeast(1f)
            val stackH = ((h - g) / 2f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, leftW, h),
                CollageTileRect(leftW + g, 0f, w, stackH),
                CollageTileRect(leftW + g, stackH + g, w, h),
            )
        }
        CollageLayout.STACK_LEFT_FULL_RIGHT -> {
            val leftW = ((w - g) / 2f).coerceAtLeast(1f)
            val stackH = ((h - g) / 2f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, leftW, stackH),
                CollageTileRect(0f, stackH + g, leftW, h),
                CollageTileRect(leftW + g, 0f, w, h),
            )
        }
        CollageLayout.FULL_TOP_SPLIT_BOTTOM -> {
            val topH = ((h - g) / 2f).coerceAtLeast(1f)
            val bottomW = ((w - g) / 2f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, w, topH),
                CollageTileRect(0f, topH + g, bottomW, h),
                CollageTileRect(bottomW + g, topH + g, w, h),
            )
        }
        CollageLayout.SPLIT_TOP_FULL_BOTTOM -> {
            val topH = ((h - g) / 2f).coerceAtLeast(1f)
            val topW = ((w - g) / 2f).coerceAtLeast(1f)
            listOf(
                CollageTileRect(0f, 0f, topW, topH),
                CollageTileRect(topW + g, 0f, w, topH),
                CollageTileRect(0f, topH + g, w, h),
            )
        }
    }
}
