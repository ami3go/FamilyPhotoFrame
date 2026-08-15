package com.example.familyphotoframe.ui.slideshow.transition

import com.example.familyphotoframe.data.settings.TransitionMode
import kotlin.math.roundToInt

/** Pure transition lifecycle used by the slideshow coordinator and diagnostics. */
sealed interface TransitionState {
    data object Idle : TransitionState
    data class Preparing(val currentPresentationId: Long?, val requestedPresentationId: Long) : TransitionState
    data class Ready(
        val outgoingPresentationId: Long?,
        val incomingPresentationId: Long,
        val effect: TransitionMode,
    ) : TransitionState
    data class Animating(
        val outgoingPresentationId: Long?,
        val incomingPresentationId: Long,
        val effect: TransitionMode,
        val progress: Float,
    ) : TransitionState
    data class Committed(val currentPresentationId: Long) : TransitionState
    data class Paused(val currentPresentationId: Long?) : TransitionState
}

object TransitionTiming {
    const val MIN_BASE_DURATION_MS = 300
    const val MAX_BASE_DURATION_MS = 2_000
    const val MAX_RESOLVED_DURATION_MS = 2_500
    const val COLD_START_DURATION_MS = 300

    fun clampBase(durationMs: Int): Int =
        durationMs.coerceIn(MIN_BASE_DURATION_MS, MAX_BASE_DURATION_MS)

    fun resolvedDurationMs(baseDurationMs: Int, effect: TransitionMode): Int =
        (clampBase(baseDurationMs) * effect.durationMultiplier)
            .roundToInt()
            .coerceIn(MIN_BASE_DURATION_MS, MAX_RESOLVED_DURATION_MS)

    /** Progress derived from real frame-clock time, independent of Android animator scale. */
    fun progressForElapsedNanos(elapsedNanos: Long, durationMs: Int): Float {
        val durationNanos = durationMs.coerceAtLeast(1).toLong() * 1_000_000L
        return (elapsedNanos.coerceAtLeast(0L).toDouble() / durationNanos.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }
}

/** Transform applied to one full prepared presentation at a particular progress value. */
data class LayerTransform(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
)

data class TransitionFrame(
    val outgoing: LayerTransform,
    val incoming: LayerTransform,
)

/** Structured diagnostics payload; avoids positional callback drift as phases add fields. */
data class TransitionEvent(
    val code: String,
    val configuredMode: String,
    val configuredEffect: String,
    val resolvedEffect: String,
    val outgoingId: Long?,
    val incomingId: Long,
    val durationMs: Int,
    /** Real elapsed animation time; configured duration remains in [durationMs]. */
    val actualDurationMs: Long? = null,
    val direction: String = TransitionDirection.NONE.name.lowercase(),
    val reason: String? = null,
    val fallbackUsed: Boolean = false,
    val frameCount: Int? = null,
    val slowFrameCount: Int? = null,
    val maximumFrameMs: Long? = null,
    /** Time from a fully prepared incoming slide to the first animation frame. */
    val startLatencyMs: Long? = null,
    /** Number of prepared presentations retained when the event was emitted. */
    val preparedSlideCount: Int? = null,
    /** Approximate decoded bytes used by outgoing + incoming prepared content. */
    val activeDecodedBytes: Long? = null,
)
