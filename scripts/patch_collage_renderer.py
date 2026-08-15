#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def rd(p): return (R/p).read_text(encoding='utf-8')
def wr(p,s):
    q=R/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def rep(s,a,b,label):
    if s.count(a)!=1: raise RuntimeError(f'{label}: {s.count(a)} matches')
    return s.replace(a,b,1)

wr('app/src/main/java/com/example/familyphotoframe/ui/slideshow/CollageVisualOptions.kt','''package com.example.familyphotoframe.ui.slideshow

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageScaleMode

internal fun CollageScaleMode.toContentScale(): ContentScale = when (this) {
    CollageScaleMode.CROP -> ContentScale.Crop
    CollageScaleMode.FIT -> ContentScale.Fit
}
internal fun CollageAlignment.toComposeAlignment(): Alignment = when (this) {
    CollageAlignment.CENTER -> Alignment.Center
    CollageAlignment.TOP -> Alignment.TopCenter
    CollageAlignment.BOTTOM -> Alignment.BottomCenter
    CollageAlignment.LEFT -> Alignment.CenterStart
    CollageAlignment.RIGHT -> Alignment.CenterEnd
}
internal fun CollageBackground.toArgb(appBackgroundColorArgb: Long): Int = when (this) {
    CollageBackground.APP_BACKGROUND -> appBackgroundColorArgb.toInt()
    CollageBackground.BLACK -> android.graphics.Color.BLACK
    CollageBackground.WHITE -> android.graphics.Color.WHITE
}
internal fun CollageBackground.toComposeColor(appBackgroundColorArgb: Long): Color = Color(toArgb(appBackgroundColorArgb))
''')

wr('app/src/main/java/com/example/familyphotoframe/ui/slideshow/AdaptiveCollageRenderer.kt','''package com.example.familyphotoframe.ui.slideshow

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
internal fun PreparedAdaptiveCollage(prepared: PreparedSlide.Collage, state: SlideshowUiState) {
    val settings = state.portraitCollage
    val gapDp = when (settings.gap) {
        CollageGap.NONE -> 0.dp
        CollageGap.SMALL -> 4.dp
        CollageGap.MEDIUM -> 8.dp
        CollageGap.LARGE -> 16.dp
    }
    val background = settings.background.toComposeColor(state.backgroundColorArgb)
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
''')

p='app/src/main/java/com/example/familyphotoframe/ui/slideshow/PreparedSlideRenderer.kt'; s=rd(p)
s=rep(s,'            PreparedAdaptiveCollage(prepared, state.portraitCollage.gap)\n','            PreparedAdaptiveCollage(prepared, state)\n','adaptive call'); wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt'; s=rd(p)
s=rep(s,'import androidx.compose.foundation.shape.CircleShape\n','import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\n','shape import')
s=rep(s,'import androidx.compose.ui.draw.clipToBounds\n','import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.clipToBounds\n','clip import')
s=rep(s,'import com.example.familyphotoframe.data.settings.CollageGap\n','import com.example.familyphotoframe.data.settings.CollageGap\nimport com.example.familyphotoframe.data.settings.CollageScaleMode\n','scale import')
s=rep(s,'''            portraitFallback = state.portraitCollage.fallback,
            collageGap = state.portraitCollage.gap,
            prepareSoftFocusBlur = softFocusNeeded,''','''            portraitFallback = state.portraitCollage.fallback,
            collageGap = state.portraitCollage.gap,
            collageOrientationFilter = state.portraitCollage.orientationFilter,
            collageLayoutPreference = state.portraitCollage.layoutPreference,
            prepareSoftFocusBlur = softFocusNeeded,''','prep wiring')
# Preview effect should rebuild for every collage visual option.
s=rep(s,'''        state.portraitCollage.gap,
        targetW,''','''        state.portraitCollage,
        targetW,''','preview key')
s=rep(s,'''            collageGap = state.portraitCollage.gap,
            transition = activeTransition.effect.storageValue,''','''            collageGap = state.portraitCollage.gap,
            collageScaleMode = state.portraitCollage.scaleMode,
            collageAlignment = state.portraitCollage.alignment,
            collageBackground = state.portraitCollage.background,
            collageCornerRadiusDp = state.portraitCollage.cornerRadiusDpClamped,
            transition = activeTransition.effect.storageValue,''','preview wiring')
s=rep(s,'''        CollageGap.MEDIUM -> 8.dp
    }

    // Task §1: motion is defined for the three-equal-portrait-panel layout only.''','''        CollageGap.MEDIUM -> 8.dp
        CollageGap.LARGE -> 16.dp
    }
    val tileScale = state.portraitCollage.scaleMode.toContentScale()
    val tileAlignment = state.portraitCollage.alignment.toComposeAlignment()
    val tileBackground = state.portraitCollage.background.toComposeColor(state.backgroundColorArgb)
    val tileShape = RoundedCornerShape(state.portraitCollage.cornerRadiusDpClamped.dp)

    // Task §1: motion is defined for the three-equal-portrait-panel layout only.''','equal visual options')
s=rep(s,'''    val profile = if (!threePanel) {
        PanelMotionProfile.OFF
    } else {''','''    val profile = if (!threePanel || state.portraitCollage.scaleMode == CollageScaleMode.FIT) {
        PanelMotionProfile.OFF
    } else {''','fit motion guard')
s=rep(s,'''            profile == PanelMotionProfile.OFF -> {
                fallbackReason = "motion_disabled"
                null
            }''','''            state.portraitCollage.scaleMode == CollageScaleMode.FIT -> {
                fallbackReason = "fit_scale_mode"
                null
            }
            profile == PanelMotionProfile.OFF -> {
                fallbackReason = "motion_disabled"
                null
            }''','fit motion reason')
s=rep(s,'''    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(gapDp),
    ) {''','''    Row(
        modifier = Modifier.fillMaxSize().background(tileBackground),
        horizontalArrangement = Arrangement.spacedBy(gapDp),
    ) {''','row background')
s=rep(s,'''                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Clip to the panel so transformed pixels never bleed into a neighbour.
                    .clipToBounds()''','''                contentScale = tileScale,
                alignment = tileAlignment,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(tileBackground, tileShape)
                    .clip(tileShape)
                    // Clip to the panel so transformed pixels never bleed into a neighbour.
                    .clipToBounds()''','tile rendering')
wr(p,s)
print('collage renderer patch applied')
