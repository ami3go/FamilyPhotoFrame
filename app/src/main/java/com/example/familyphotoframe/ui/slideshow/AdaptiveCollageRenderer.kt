package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.familyphotoframe.data.settings.CollageGap
import kotlin.math.roundToInt

/** Static renderer for adaptive mixed-orientation layouts. */
@Composable
internal fun PreparedAdaptiveCollage(
    prepared: PreparedSlide.Collage,
    gap: CollageGap,
) {
    val gapDp = when (gap) {
        CollageGap.NONE -> 0.dp
        CollageGap.SMALL -> 4.dp
        CollageGap.MEDIUM -> 8.dp
    }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            prepared.tiles.forEach { tile ->
                val imageBitmap = remember(tile.bitmap) { tile.bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val destinations = collageDestinationRects(
            prepared.layout,
            width.toFloat(),
            height.toFloat(),
            gapDp.roundToPx().toFloat(),
        )
        val placeables = measurables.mapIndexed { index, measurable ->
            val rect = destinations.getOrNull(index)
            val tileWidth = rect?.width()?.roundToInt()?.coerceAtLeast(1) ?: 1
            val tileHeight = rect?.height()?.roundToInt()?.coerceAtLeast(1) ?: 1
            measurable.measure(Constraints.fixed(tileWidth, tileHeight))
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val rect = destinations.getOrNull(index) ?: return@forEachIndexed
                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}
