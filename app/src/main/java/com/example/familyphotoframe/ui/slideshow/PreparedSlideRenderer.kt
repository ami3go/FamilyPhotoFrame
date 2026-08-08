package com.example.familyphotoframe.ui.slideshow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.MotionMode
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.domain.engine.PlaybackMemoryState
import com.example.familyphotoframe.ui.render.KenBurns
import com.example.familyphotoframe.ui.render.PanelMotionStore

/** Rendering-only helpers kept out of the transition/preparation coordinator. */
@Composable
internal fun PreparedTransitionBlur(prepared: PreparedSlide) {
    prepared.transitionBlurredBitmap?.takeIf { !it.isRecycled }?.let { bitmap ->
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun PreparedPhotoFrame(
    prepared: PreparedSlide,
    state: SlideshowUiState,
    imageLoader: ImageLoader,
    allowDisplayMotion: Boolean,
    motionStore: PanelMotionStore,
    memoryProtection: PlaybackMemoryState,
    reclaimer: LegacyBitmapReclaimer,
    onRecoverableOom: (DisplayPhoto, String) -> Unit,
    onMotionDiagnostic: (List<Long>, String) -> Unit,
) {
    when (prepared) {
        is PreparedSlide.Single -> PreparedSinglePhoto(
            prepared,
            state,
            imageLoader,
            allowDisplayMotion,
            memoryProtection,
            reclaimer,
            onRecoverableOom,
        )
        is PreparedSlide.Collage -> PreparedCollage(
            prepared,
            state.portraitCollage.gap,
            state,
            allowDisplayMotion,
            motionStore,
            onMotionDiagnostic,
        )
    }
}

@Composable
private fun PreparedSinglePhoto(
    prepared: PreparedSlide.Single,
    state: SlideshowUiState,
    imageLoader: ImageLoader,
    allowDisplayMotion: Boolean,
    memoryProtection: PlaybackMemoryState,
    reclaimer: LegacyBitmapReclaimer,
    onRecoverableOom: (DisplayPhoto, String) -> Unit,
) {
    val photo = prepared.anchor
    // Display-time motion is separate from a transition's transform; suspend it while the
    // transition owns the frame so transforms never compound.
    val motionEnabled = allowDisplayMotion && state.motion == MotionMode.KEN_BURNS
    val progress = remember(photo.id) { Animatable(0f) }
    LaunchedEffect(photo.id, motionEnabled, state.intervalSecondsForUi) {
        if (motionEnabled) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = state.intervalSecondsForUi.coerceIn(3, 600) * 1000,
                    easing = LinearEasing,
                ),
            )
        }
    }

    val aspectMode = prepared.aspectOverride ?: state.aspectMode
    Box(Modifier.fillMaxSize()) {
        if (aspectMode == AspectMode.FIT_BLUR && memoryProtection.allowBlurredBackdrop) {
            BlurredBackdrop(
                model = prepared.model,
                imageLoader = imageLoader,
                photoId = photo.id,
                reclaimer = reclaimer,
                onOutOfMemory = { onRecoverableOom(photo, "blurred_backdrop_allocation") },
            )
        }
        val imageBitmap = remember(prepared.bitmap) { prepared.bitmap.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = when (aspectMode) {
                AspectMode.FIT_COLOR, AspectMode.FIT_BLUR -> ContentScale.Fit
                AspectMode.FILL_CROP -> ContentScale.Crop
            },
            modifier = Modifier.fillMaxSize().then(
                if (!motionEnabled) Modifier else Modifier.graphicsLayer {
                    val frame = KenBurns.frameFor(photo.id, progress.value)
                    scaleX = frame.scale
                    scaleY = frame.scale
                    translationX = frame.translateXFraction * size.width
                    translationY = frame.translateYFraction * size.height
                }
            ),
        )
    }
}
