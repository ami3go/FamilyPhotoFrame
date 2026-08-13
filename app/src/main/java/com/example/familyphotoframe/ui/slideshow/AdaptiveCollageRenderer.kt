package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.domain.engine.CollageLayout
import com.example.familyphotoframe.domain.engine.PhotoOrientation
import com.example.familyphotoframe.domain.engine.PortraitCollagePolicy
import com.example.familyphotoframe.ui.render.PanelMotionStore
import kotlin.math.roundToInt

/**
 * Preserve the mature animated PPP renderer unchanged, while all mixed-orientation
 * presentations remain static. A mixed presentation may legitimately score best in
 * THREE_COLUMNS, so layout shape alone is not sufficient to decide whether motion is safe.
 */
@Composable
internal fun AdaptivePreparedCollage(
    prepared: PreparedSlide.Collage,
    gap: CollageGap,
    state: SlideshowUiState,
    allowDisplayMotion: Boolean,
    motionStore: PanelMotionStore,
    onMotionDiagnostic: (List<Long>, String) -> Unit,
) {
    val isAnimatedPpp = prepared.layout == CollageLayout.THREE_COLUMNS &&
        prepared.tiles.size == 3 &&
        prepared.tiles.all { tile ->
            PortraitCollagePolicy.classify(tile.bitmap.width, tile.bitmap.height) ==
                PhotoOrientation.PORTRAIT
        }
    if (isAnimatedPpp) {
        PreparedCollage(
            prepared = prepared,
            gap = gap,
            state = state,
            allowDisplayMotion = allowDisplayMotion,
            motionStore = motionStore,
            onMotionDiagnostic = onMotionDiagnostic,
        )
        return
    }

    val gapDp = when (gap) {
        CollageGap.NONE -> 0.dp
        CollageGap.SMALL -> 4.dp
        CollageGap.MEDIUM -> 8.dp
    }
    val density = LocalDensity.current
    val gapPx = with(density) { gapDp.toPx() }

    LaunchedEffect(prepared.anchor.id, prepared.layout) {
        onMotionDiagnostic(
            prepared.photos.map { it.id },
            "static fallback: adaptive_layout=${prepared.layout.name}",
        )
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
                    modifier = Modifier.clipToBounds(),
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val rects = collageTileRects(
            layout = prepared.layout,
            width = width.toFloat(),
            height = height.toFloat(),
            gap = gapPx,
        )
        val placeables = measurables.zip(rects).map { (measurable, rect) ->
            measurable.measure(
                Constraints.fixed(
                    width = rect.width.roundToInt().coerceAtLeast(1),
                    height = rect.height.roundToInt().coerceAtLeast(1),
                )
            )
        }
        layout(width, height) {
            placeables.zip(rects).forEach { (placeable, rect) ->
                placeable.placeRelative(
                    x = rect.left.roundToInt().coerceAtLeast(0),
                    y = rect.top.roundToInt().coerceAtLeast(0),
                )
            }
        }
    }
}
