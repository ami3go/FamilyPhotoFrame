package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageScaleMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.example.familyphotoframe.ui.render.PanelMotionProfile
import com.example.familyphotoframe.ui.render.PanelMotionStore
import com.example.familyphotoframe.ui.render.PortraitPanelMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

@Composable
internal fun PreparedCollage(
    prepared: PreparedSlide.Collage,
    gap: CollageGap,
    state: SlideshowUiState,
    allowDisplayMotion: Boolean,
    motionStore: PanelMotionStore,
    onMotionDiagnostic: (List<Long>, String) -> Unit,
) {
    val gapDp = when (gap) {
        CollageGap.NONE -> 0.dp
        CollageGap.SMALL -> 4.dp
        CollageGap.MEDIUM -> 8.dp
        CollageGap.LARGE -> 16.dp
    }
    val tileScale = state.portraitCollage.scaleMode.toContentScale()
    val tileAlignment = state.portraitCollage.alignment.toComposeAlignment()
    val tileBackground = state.portraitCollage.background.toComposeColor(state.backgroundColorArgb)
    val tileShape = RoundedCornerShape(state.portraitCollage.cornerRadiusDpClamped.dp)

    // Task §1: motion is defined for the three-equal-portrait-panel layout only.
    val threePanel = usesPanelMotionRenderer(prepared.layout, prepared.tiles.size)
    val animationScale = rememberSystemAnimationScale()
    val profile = if (!threePanel || state.portraitCollage.scaleMode == CollageScaleMode.FIT) {
        PanelMotionProfile.OFF
    } else {
        PortraitPanelMotion.profileFor(state.portraitCollage.animateThreePhotoFrames, animationScale)
    }

    // Seeded on the anchor so a given frame always animates identically (task §7) and the
    // paths survive recomposition and activity recreation (task §11).
    val slideId = prepared.anchor.id
    val durationMillis = state.intervalSecondsForUi.coerceIn(3, 600) * 1000
    var fallbackReason: String? = null
    val entry = motionStore.entryFor(slideId, "$profile|$durationMillis") {
        when {
            !threePanel -> {
                fallbackReason = "not_three_panel_layout"
                null
            }
            state.portraitCollage.scaleMode == CollageScaleMode.FIT -> {
                fallbackReason = "fit_scale_mode"
                null
            }
            profile == PanelMotionProfile.OFF -> {
                fallbackReason = "motion_disabled"
                null
            }
            else -> runCatching { PortraitPanelMotion.pathsForFrame(slideId, durationMillis, profile) }
                .onFailure { fallbackReason = "path_generation_failed: ${it.javaClass.simpleName}" }
                .getOrNull()   // Task §15: a failed path falls back to a static frame.
        }
    }
    val paths = entry.paths

    // Task §16: one entry per frame describing panel assignment, presets and limits.
    // compareAndSet ensures this fires exactly once across all composition slots that
    // simultaneously render this slide (committed frame, outgoing frame in the transition
    // renderer, and any recomposition of the same slot). See PanelMotionStore.Entry.described.
    LaunchedEffect(slideId, paths) {
        if (entry.described.compareAndSet(false, true)) {
            onMotionDiagnostic(
                prepared.photos.map { it.id },
                if (paths == null) {
                    "static fallback: ${fallbackReason ?: "unknown"}"
                } else {
                    PortraitPanelMotion.describeFrame(paths)
                },
            )
        }
    }

    /**
     * Drive the motion only while this slide is the live frame (task §10 step 7-9).
     *
     * ## How this satisfies task §11's four manual-navigation requirements
     *
     * - **Cancel the active animation safely**: `animating` going false — paused, off
     *   screen, or `allowDisplayMotion=false` while a transition owns the frame — changes
     *   this effect's key, which cancels the running [Animatable.animateTo] coroutine.
     *   This is Compose's ordinary effect cancellation; nothing custom is needed.
     * - **Preserve the current transform until the transition begins**: cancellation only
     *   stops the *coroutine*. [entry]'s `progress` value lives in [PanelMotionStore], not
     *   in the coroutine, so it is untouched by the cancellation and the `graphicsLayer`
     *   below keeps reading whatever value it stopped at.
     * - **Release references to the outgoing frame**: handled separately by
     *   [PanelMotionStore.retain], called once per composition from the slide list that is
     *   actually on screen.
     * - **Start new motion only after the next frame is ready**: paths are built as soon
     *   as a slide is first composed, even while it is only a transition preview with
     *   `allowDisplayMotion=false` — but the coroutine that actually advances `progress`
     *   only runs once `animating` is true, which requires the slide to be the committed
     *   frame. Pre-building paths early is what keeps the first visible frame free of a
     *   generation stall; not animating until committed is what task §10 step 7 requires.
     *
     * ## Screen off, backgrounding, and activity recreation (task §11, §14)
     *
     * No extra handling is added for these: `setContent` installs a lifecycle-aware
     * `Recomposer` that cancels running effects — this one included — once the activity
     * drops below `STARTED`, and Android delivers no animation frame callbacks to a window
     * that is not visible regardless. Both stop CPU/GPU work without this code needing to
     * poll visibility itself. On resume the effect restarts and reads the same "remaining"
     * calculation below, so motion continues from where it paused rather than restarting.
     */
    val animating = allowDisplayMotion && paths != null && !state.engine.paused
    LaunchedEffect(slideId, animating, durationMillis) {
        if (animating) {
            onMotionDiagnostic(prepared.photos.map { it.id }, "animation start: slide=$slideId")
            val remaining = ((1f - entry.progress.value) * durationMillis).toInt().coerceAtLeast(0)
            try {
                entry.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remaining, easing = LinearEasing),
                )
                onMotionDiagnostic(prepared.photos.map { it.id }, "animation completion: slide=$slideId")
            } catch (e: CancellationException) {
                onMotionDiagnostic(prepared.photos.map { it.id }, "animation cancellation: slide=$slideId")
                throw e
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().background(tileBackground),
        horizontalArrangement = Arrangement.spacedBy(gapDp),
    ) {
        prepared.tiles.forEachIndexed { index, tile ->
            val imageBitmap = remember(tile.bitmap) { tile.bitmap.asImageBitmap() }
            val path = paths?.getOrNull(index)
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = tileScale,
                alignment = tileAlignment,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(tileBackground, tileShape)
                    .clip(tileShape)
                    // Clip to the panel so transformed pixels never bleed into a neighbour.
                    .clipToBounds()
                    .then(
                        if (path == null) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer {
                                // Deferred read: the transform updates on the render thread
                                // without recomposing or allocating per frame (task §14).
                                val frame = PortraitPanelMotion.frameAt(path, entry.progress.value)
                                scaleX = frame.scale
                                scaleY = frame.scale
                                translationX = frame.translateXFraction * size.width
                                translationY = frame.translateYFraction * size.height
                            }
                        }
                    ),
            )
        }
    }
}

/**
 * System animator duration scale (task §13), or -1 when it cannot be read.
 *
 * Read once per composition rather than observed: the frame lasts seconds and the user
 * changing this mid-slide does not warrant a listener.
 */
@Composable
private fun rememberSystemAnimationScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(-1f)
    }
}
