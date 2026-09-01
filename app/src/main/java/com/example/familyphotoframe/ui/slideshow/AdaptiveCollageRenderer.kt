package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.familyphotoframe.data.settings.CollageGap
import kotlin.math.roundToInt

/** Static renderer for adaptive mixed-orientation and user-selected layouts. */
@Composable
internal fun PreparedAdaptiveCollage(
    prepared: PreparedSlide.Collage,
    state: SlideshowUiState,
    contentAlpha: Float = 1f,
) {
    val settings = state.portraitCollage
    val gapDp = when (settings.gap) {
        CollageGap.NONE -> 0.dp
        CollageGap.SMALL -> 4.dp
        CollageGap.MEDIUM -> 8.dp
        CollageGap.LARGE -> 16.dp
    }
    val opacity = contentAlpha.coerceIn(0f, 1f)
    val background = settings.background.toComposeColor(state.backgroundColorArgb).let { color ->
        color.copy(alpha = color.alpha * opacity)
    }
    val shape = RoundedCornerShape(settings.cornerRadiusDpClamped.dp)
    val contentScale = settings.scaleMode.toContentScale()
    val alignment = settings.alignment.toComposeAlignment()
    Layout(
        modifier = Modifier.fillMaxSize().background(background),
        content = {
            prepared.tiles.forEach { tile ->
                val imageBitmap = remember(tile.bitmap) { tile.bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = contentScale,
                    alignment = alignment,
                    alpha = opacity,
                    modifier = Modifier.background(background, shape).clip(shape),
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val destinations = collageDestinationRects(prepared.layout,width.toFloat(),height.toFloat(),gapDp.roundToPx().toFloat())
        val placeables = measurables.mapIndexed { index, measurable ->
            val rect=destinations.getOrNull(index)
            measurable.measure(Constraints.fixed(rect?.width()?.roundToInt()?.coerceAtLeast(1)?:1,rect?.height()?.roundToInt()?.coerceAtLeast(1)?:1))
        }
        layout(width,height) {
            placeables.forEachIndexed { index, placeable ->
                val rect=destinations.getOrNull(index)?:return@forEachIndexed
                placeable.place(rect.left.roundToInt(),rect.top.roundToInt())
            }
        }
    }
}
