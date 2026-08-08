package com.example.familyphotoframe.ui.slideshow.transition

import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import kotlin.random.Random
import java.util.ArrayDeque

/** Direction shared by the Ken Burns handoff selector and renderer. */
enum class TransitionDirection {
    NONE,
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
}

/** Concrete transition selected for one prepared-slide handoff. */
data class ResolvedTransition(
    val effect: TransitionMode,
    val direction: TransitionDirection = TransitionDirection.NONE,
    /** Non-null only when the configured choice was replaced by a safe fallback. */
    val fallbackReason: String? = null,
) {
    val fallbackUsed: Boolean get() = fallbackReason != null
}

/**
 * Curated weighted selector for passive photo-frame playback.
 *
 * The selector owns only a tiny effect-name history; it never retains prepared slides.
 * Injecting [Random] makes every selection and Ken Burns direction deterministic in tests.
 */
class TransitionSelector(
    private val random: Random,
    private val historySize: Int = 3,
) {
    private val recent = ArrayDeque<TransitionMode>()

    fun select(
        mode: TransitionSelectionMode,
        configuredEffect: TransitionMode,
        supportedEffects: Set<TransitionMode>,
        reducedMotion: Boolean,
        forceCrossfade: Boolean,
    ): ResolvedTransition {
        val supported = supportedEffects.map { it }.toSet()
            .ifEmpty { setOf(TransitionMode.CROSSFADE) }

        val result = when {
            forceCrossfade -> resolveFixed(
                desired = TransitionMode.CROSSFADE,
                supported = supported,
                fallbackReason = "low_performance_mode",
            )

            mode == TransitionSelectionMode.FIXED -> {
                val configured = configuredEffect
                val desired = if (reducedMotion && !configured.isOpacityOnly) {
                    TransitionMode.CROSSFADE
                } else configured
                resolveFixed(
                    desired = desired,
                    supported = supported,
                    fallbackReason = if (desired != configured) "reduced_motion" else null,
                )
            }

            else -> selectAmbient(supported, reducedMotion)
        }

        remember(result.effect)
        return result.copy(
            direction = if (result.effect == TransitionMode.KEN_BURNS_HANDOFF) {
                when (random.nextInt(4)) {
                    0 -> TransitionDirection.LEFT_TO_RIGHT
                    1 -> TransitionDirection.RIGHT_TO_LEFT
                    2 -> TransitionDirection.TOP_TO_BOTTOM
                    else -> TransitionDirection.BOTTOM_TO_TOP
                }
            } else TransitionDirection.NONE,
        )
    }

    fun history(): List<TransitionMode> = recent.toList()

    private fun selectAmbient(
        supported: Set<TransitionMode>,
        reducedMotion: Boolean,
    ): ResolvedTransition {
        var eligible = TransitionMode.ambientRandomValues.filter { it in supported }
        if (reducedMotion) eligible = eligible.filter { it.isOpacityOnly }
        if (eligible.isEmpty()) {
            return resolveFixed(
                desired = TransitionMode.CROSSFADE,
                supported = supported,
                fallbackReason = "ambient_pool_empty",
            )
        }

        val last = recent.lastOrNull()
        var preferred = eligible.filter { it !in recent }
        if (preferred.isEmpty()) preferred = eligible.filter { it != last }
        if (preferred.isEmpty()) preferred = eligible

        val lastTwo = recent.toList().takeLast(2)
        val lastTwoHeavy = lastTwo.size == 2 && lastTwo.all { it.isMotionHeavy }
        if (lastTwoHeavy) {
            preferred.filterNot { it.isMotionHeavy }.takeIf { it.isNotEmpty() }?.let { preferred = it }
        }

        return ResolvedTransition(effect = weightedPick(preferred))
    }

    private fun resolveFixed(
        desired: TransitionMode,
        supported: Set<TransitionMode>,
        fallbackReason: String?,
    ): ResolvedTransition {
        val chain = fallbackChain(desired)
        val resolved = chain.firstOrNull { it in supported }
            ?: TransitionMode.CROSSFADE
        val reason = when {
            fallbackReason != null -> fallbackReason
            resolved != desired -> "unsupported_${desired.storageValue}"
            else -> null
        }
        return ResolvedTransition(resolved, fallbackReason = reason)
    }

    private fun fallbackChain(effect: TransitionMode): List<TransitionMode> = when (effect) {
        TransitionMode.SOFT_REVEAL -> listOf(
            TransitionMode.SOFT_REVEAL,
            TransitionMode.HORIZONTAL_GLIDE,
            TransitionMode.CROSSFADE,
        )
        TransitionMode.SOFT_FOCUS_FADE -> listOf(
            TransitionMode.SOFT_FOCUS_FADE,
            TransitionMode.SOFT_DISSOLVE,
            TransitionMode.CROSSFADE,
        )
        TransitionMode.KEN_BURNS_HANDOFF -> listOf(
            TransitionMode.KEN_BURNS_HANDOFF,
            TransitionMode.GENTLE_ZOOM_IN,
            TransitionMode.CROSSFADE,
        )
        else -> listOf(effect, TransitionMode.CROSSFADE)
    }

    private fun weightedPick(values: List<TransitionMode>): TransitionMode {
        val total = values.sumOf { weightOf(it) }
        if (total <= 0) return values.first()
        var ticket = random.nextInt(total)
        for (value in values) {
            ticket -= weightOf(value)
            if (ticket < 0) return value
        }
        return values.last()
    }

    private fun weightOf(effect: TransitionMode): Int = when (effect) {
        TransitionMode.CROSSFADE -> 22
        TransitionMode.SOFT_DISSOLVE -> 18
        TransitionMode.GENTLE_ZOOM_IN -> 12
        TransitionMode.GENTLE_ZOOM_OUT -> 12
        TransitionMode.HORIZONTAL_GLIDE -> 10
        TransitionMode.VERTICAL_GLIDE -> 8
        TransitionMode.DEPTH_FADE -> 10
        TransitionMode.KEN_BURNS_HANDOFF -> 8
        else -> 0
    }

    private fun remember(effect: TransitionMode) {
        recent.addLast(effect)
        while (recent.size > historySize.coerceAtLeast(1)) recent.removeFirst()
    }
}
