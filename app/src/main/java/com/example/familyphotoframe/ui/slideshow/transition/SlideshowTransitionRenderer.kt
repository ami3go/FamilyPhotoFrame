package com.example.familyphotoframe.ui.slideshow.transition

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.example.familyphotoframe.data.settings.TransitionMode
import kotlin.math.max

/**
 * Renders two already-prepared presentations. This layer performs no I/O, decoding,
 * selection, or persistence. The complete collage/single presentation is transformed
 * as one visual unit, so all collage tiles remain synchronized.
 */
@Composable
internal fun <T> SlideshowTransitionRenderer(
    outgoing: T?,
    incoming: T,
    transition: ResolvedTransition,
    progress: Float,
    render: @Composable (T) -> Unit,
    renderWithDirectAlpha: (@Composable (T, Float) -> Unit)? = null,
    renderBlurred: (@Composable (T) -> Unit)? = null,
) {
    val p = progress.coerceIn(0f, 1f)
    when (transition.effect) {
        TransitionMode.CROSSFADE -> {
            if (renderWithDirectAlpha == null) {
                StandardRenderer(
                    outgoing = outgoing,
                    incoming = incoming,
                    frame = transitionFrame(TransitionMode.CROSSFADE, p),
                    render = render,
                )
            } else {
                DirectAlphaCrossfadeRenderer(
                    outgoing = outgoing,
                    incoming = incoming,
                    frame = transitionFrame(TransitionMode.CROSSFADE, p),
                    render = renderWithDirectAlpha,
                )
            }
        }
        TransitionMode.SOFT_REVEAL -> SoftRevealRenderer(outgoing, incoming, p, render)
        TransitionMode.SOFT_FOCUS_FADE -> {
            if (renderBlurred == null) {
                StandardRenderer(
                    outgoing = outgoing,
                    incoming = incoming,
                    frame = transitionFrame(TransitionMode.SOFT_DISSOLVE, p),
                    render = render,
                )
            } else {
                SoftFocusRenderer(outgoing, incoming, p, render, renderBlurred)
            }
        }
        else -> StandardRenderer(
            outgoing = outgoing,
            incoming = incoming,
            frame = transitionFrame(transition.effect, p, transition.direction),
            render = render,
        )
    }
}

/**
 * Crossfade without a full-screen [graphicsLayer]. On API 22 even modulated layer alpha
 * leaves a viewport-sized HWUI layer in the native cache. Passing alpha to each image
 * draw keeps the same opacity curve while letting the bitmap painter modulate its paint.
 */
@Composable
private fun <T> DirectAlphaCrossfadeRenderer(
    outgoing: T?,
    incoming: T,
    frame: TransitionFrame,
    render: @Composable (T, Float) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (outgoing != null) render(outgoing, frame.outgoing.alpha.coerceIn(0f, 1f))
        render(incoming, frame.incoming.alpha.coerceIn(0f, 1f))
    }
}

internal fun usesDirectContentAlpha(effect: TransitionMode): Boolean =
    effect == TransitionMode.CROSSFADE

@Composable
private fun <T> StandardRenderer(
    outgoing: T?,
    incoming: T,
    frame: TransitionFrame,
    render: @Composable (T) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (outgoing != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .presentationTransform(frame.outgoing),
            ) { render(outgoing) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .presentationTransform(frame.incoming),
        ) { render(incoming) }
    }
}

@Composable
private fun <T> SoftRevealRenderer(
    outgoing: T?,
    incoming: T,
    progress: Float,
    render: @Composable (T) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (outgoing != null) Box(Modifier.fillMaxSize()) { render(outgoing) }
        Box(
            Modifier
                .fillMaxSize()
                .softRevealMask(progress),
        ) { render(incoming) }
    }
}

@Composable
private fun <T> SoftFocusRenderer(
    outgoing: T?,
    incoming: T,
    progress: Float,
    render: @Composable (T) -> Unit,
    renderBlurred: @Composable (T) -> Unit,
) {
    val frame = softFocusFrame(progress)

    Box(Modifier.fillMaxSize()) {
        if (outgoing != null) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = frame.outgoingSharp }) { render(outgoing) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = frame.outgoingBlurred }) {
                renderBlurred(outgoing)
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = frame.incomingBlurred }) {
            renderBlurred(incoming)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = frame.incomingSharp }) { render(incoming) }
    }
}

internal data class SoftFocusFrame(
    val outgoingSharp: Float,
    val outgoingBlurred: Float,
    val incomingBlurred: Float,
    val incomingSharp: Float,
) {
    val combinedAlpha: Float
        get() = outgoingSharp + outgoingBlurred + incomingBlurred + incomingSharp
}

internal fun softFocusFrame(rawProgress: Float): SoftFocusFrame {
    val p = FastOutSlowInEasing.transform(rawProgress.coerceIn(0f, 1f))
    return SoftFocusFrame(
        outgoingSharp = (1f - p * 2f).coerceIn(0f, 1f),
        outgoingBlurred = ((1f - p) * (p * 2f).coerceAtMost(1f)).coerceIn(0f, 1f),
        incomingBlurred = (p * ((1f - p) * 2f).coerceAtMost(1f)).coerceIn(0f, 1f),
        incomingSharp = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f),
    )
}

private fun Modifier.presentationTransform(transform: LayerTransform): Modifier =
    graphicsLayer {
        alpha = transform.alpha.coerceIn(0f, 1f)
        // Auto alpha creates a full-viewport offscreen buffer on the API 22 frame.
        // Crossfade HIL showed those native buffers accumulating even though bitmap,
        // decode, transition, FD, and thread ownership all balanced. Modulating alpha
        // into this simple presentation subtree preserves the fade without allocating
        // an offscreen layer for every outgoing and incoming frame.
        compositingStrategy = presentationCompositingStrategy(transform)
        scaleX = transform.scale
        scaleY = transform.scale
        translationX = transform.translationXFraction * size.width
        translationY = transform.translationYFraction * size.height
        clip = true
    }

internal fun presentationCompositingStrategy(transform: LayerTransform): CompositingStrategy =
    if (transform.alpha.coerceIn(0f, 1f) < 1f) {
        CompositingStrategy.ModulateAlpha
    } else {
        CompositingStrategy.Auto
    }

/**
 * API-23-compatible soft alpha mask. Offscreen compositing is bounded to the incoming
 * viewport and uses no runtime shader or per-frame bitmap allocation.
 */
private fun Modifier.softRevealMask(progress: Float): Modifier {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return graphicsLayer { alpha = 0f }
    if (p >= 1f) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val halfFeather = 0.05f
            val solid = (p - halfFeather).coerceIn(0f, 1f)
            val transparent = (p + halfFeather).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    solid to Color.Black,
                    transparent to Color.Transparent,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** Pure frame mapping, intentionally kept allocation-light and deterministic. */
internal fun transitionFrame(
    effect: TransitionMode,
    rawProgress: Float,
    direction: TransitionDirection = TransitionDirection.NONE,
): TransitionFrame {
    val p = rawProgress.coerceIn(0f, 1f)
    return when (effect) {
        TransitionMode.CROSSFADE -> {
            val incoming = FastOutSlowInEasing.transform(p)
            val outgoing = LinearOutSlowInEasing.transform(p)
            TransitionFrame(
                // Keep combined opacity at or above one so easing curves cannot create
                // a dark flash that exposes the application background.
                outgoing = LayerTransform(alpha = max(1f - incoming, 1f - outgoing)),
                incoming = LayerTransform(alpha = incoming),
            )
        }

        TransitionMode.SOFT_DISSOLVE -> {
            val incoming = LinearOutSlowInEasing.transform(p)
            val outgoingAlpha = if (p <= 0.75f) {
                1f - (p / 0.75f) * 0.65f
            } else {
                0.35f * (1f - ((p - 0.75f) / 0.25f))
            }
            TransitionFrame(
                outgoing = LayerTransform(alpha = max(outgoingAlpha, 1f - incoming)),
                incoming = LayerTransform(alpha = incoming),
            )
        }

        TransitionMode.GENTLE_ZOOM_IN -> {
            val eased = FastOutSlowInEasing.transform(p)
            val outgoingFade = LinearOutSlowInEasing.transform(p)
            TransitionFrame(
                outgoing = LayerTransform(alpha = max(1f - eased, 1f - outgoingFade)),
                incoming = LayerTransform(
                    alpha = eased,
                    scale = lerp(0.965f, 1f, eased),
                ),
            )
        }

        TransitionMode.GENTLE_ZOOM_OUT -> {
            val eased = FastOutSlowInEasing.transform(p)
            val outgoingFade = LinearOutSlowInEasing.transform(p)
            TransitionFrame(
                outgoing = LayerTransform(alpha = max(1f - eased, 1f - outgoingFade)),
                incoming = LayerTransform(
                    alpha = eased,
                    scale = lerp(1.04f, 1f, eased),
                ),
            )
        }

        TransitionMode.HORIZONTAL_GLIDE -> {
            val incoming = FastOutSlowInEasing.transform(p)
            val outgoing = FastOutLinearInEasing.transform(p)
            TransitionFrame(
                outgoing = LayerTransform(
                    alpha = max(lerp(1f, 0.35f, outgoing), 1f - incoming),
                    translationXFraction = lerp(0f, -0.04f, outgoing),
                ),
                incoming = LayerTransform(
                    alpha = lerp(0.15f, 1f, incoming),
                    translationXFraction = lerp(0.08f, 0f, incoming),
                ),
            )
        }

        TransitionMode.VERTICAL_GLIDE -> {
            val incoming = FastOutSlowInEasing.transform(p)
            val outgoing = FastOutLinearInEasing.transform(p)
            TransitionFrame(
                outgoing = LayerTransform(
                    alpha = max(lerp(1f, 0.35f, outgoing), 1f - incoming),
                    translationYFraction = lerp(0f, -0.03f, outgoing),
                ),
                incoming = LayerTransform(
                    alpha = lerp(0.15f, 1f, incoming),
                    translationYFraction = lerp(0.06f, 0f, incoming),
                ),
            )
        }

        TransitionMode.DEPTH_FADE -> {
            val incoming = FastOutSlowInEasing.transform(p)
            val outgoing = LinearOutSlowInEasing.transform(p)
            TransitionFrame(
                outgoing = LayerTransform(
                    alpha = max(lerp(1f, 0.2f, outgoing), 1f - incoming),
                    scale = lerp(1f, 0.97f, outgoing),
                ),
                incoming = LayerTransform(
                    alpha = incoming,
                    scale = lerp(1.02f, 1f, incoming),
                ),
            )
        }

        TransitionMode.KEN_BURNS_HANDOFF -> {
            val incoming = LinearOutSlowInEasing.transform(p)
            val outgoing = LinearEasing.transform(p)
            val vector = direction.vector()
            TransitionFrame(
                outgoing = LayerTransform(
                    alpha = max(lerp(1f, 0.25f, outgoing), 1f - incoming),
                    scale = lerp(1.02f, 1.04f, outgoing),
                    translationXFraction = vector.first * lerp(0f, 0.015f, outgoing),
                    translationYFraction = vector.second * lerp(0f, 0.015f, outgoing),
                ),
                incoming = LayerTransform(
                    alpha = incoming,
                    scale = lerp(1.04f, 1f, incoming),
                    translationXFraction = -vector.first * lerp(0.015f, 0f, incoming),
                    translationYFraction = -vector.second * lerp(0.015f, 0f, incoming),
                ),
            )
        }

        // Special renderers own their masks/blur layers; the pure frame remains a safe
        // Crossfade so invariants and callers that inspect frames still get valid values.
        TransitionMode.SOFT_REVEAL,
        TransitionMode.SOFT_FOCUS_FADE -> transitionFrame(TransitionMode.CROSSFADE, p, direction)

    }
}

private fun TransitionDirection.vector(): Pair<Float, Float> = when (this) {
    TransitionDirection.LEFT_TO_RIGHT -> 1f to 0f
    TransitionDirection.RIGHT_TO_LEFT -> -1f to 0f
    TransitionDirection.TOP_TO_BOTTOM -> 0f to 1f
    TransitionDirection.BOTTOM_TO_TOP -> 0f to -1f
    TransitionDirection.NONE -> 1f to 0f
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * LinearEasing.transform(progress.coerceIn(0f, 1f))
