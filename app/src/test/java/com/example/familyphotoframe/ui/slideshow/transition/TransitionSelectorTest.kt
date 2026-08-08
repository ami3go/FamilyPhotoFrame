package com.example.familyphotoframe.ui.slideshow.transition

import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionSelectorTest {
    @Test
    fun ambientRandomDoesNotRepeatAndLimitsMotionHeavyRuns() {
        val selector = TransitionSelector(Random(1234))
        val effects = (1..1_000).map {
            selector.select(
                mode = TransitionSelectionMode.AMBIENT_RANDOM,
                configuredEffect = TransitionMode.CROSSFADE,
                supportedEffects = TransitionMode.selectableValues.toSet(),
                reducedMotion = false,
                forceCrossfade = false,
            ).effect
        }
        effects.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertFalse(effects.windowed(3).any { window -> window.all { it.isMotionHeavy } })
        assertFalse(TransitionMode.SOFT_REVEAL in effects)
        assertFalse(TransitionMode.SOFT_FOCUS_FADE in effects)
    }

    @Test
    fun seededSelectorsAreDeterministicIncludingDirection() {
        fun sequence() = TransitionSelector(Random(99)).let { selector ->
            (1..100).map {
                selector.select(
                    TransitionSelectionMode.AMBIENT_RANDOM,
                    TransitionMode.CROSSFADE,
                    TransitionMode.selectableValues.toSet(),
                    reducedMotion = false,
                    forceCrossfade = false,
                )
            }
        }
        assertEquals(sequence(), sequence())
    }

    @Test
    fun reducedMotionUsesOnlyOpacityEffects() {
        val selector = TransitionSelector(Random(1))
        repeat(100) {
            assertTrue(
                selector.select(
                    TransitionSelectionMode.AMBIENT_RANDOM,
                    TransitionMode.KEN_BURNS_HANDOFF,
                    TransitionMode.selectableValues.toSet(),
                    reducedMotion = true,
                    forceCrossfade = false,
                ).effect.isOpacityOnly,
            )
        }
        val fixed = selector.select(
            TransitionSelectionMode.FIXED,
            TransitionMode.HORIZONTAL_GLIDE,
            TransitionMode.selectableValues.toSet(),
            reducedMotion = true,
            forceCrossfade = false,
        )
        assertEquals(TransitionMode.CROSSFADE, fixed.effect)
        assertEquals("reduced_motion", fixed.fallbackReason)
    }

    @Test
    fun advancedEffectsUseSpecifiedFallbackChains() {
        val selector = TransitionSelector(Random(1))
        val noSoftFocus = TransitionMode.selectableValues.toSet() - TransitionMode.SOFT_FOCUS_FADE
        assertEquals(
            TransitionMode.SOFT_DISSOLVE,
            selector.select(
                TransitionSelectionMode.FIXED,
                TransitionMode.SOFT_FOCUS_FADE,
                noSoftFocus,
                reducedMotion = false,
                forceCrossfade = false,
            ).effect,
        )
        val noReveal = TransitionMode.selectableValues.toSet() - TransitionMode.SOFT_REVEAL
        assertEquals(
            TransitionMode.HORIZONTAL_GLIDE,
            selector.select(
                TransitionSelectionMode.FIXED,
                TransitionMode.SOFT_REVEAL,
                noReveal,
                reducedMotion = false,
                forceCrossfade = false,
            ).effect,
        )
    }

    @Test
    fun lowPerformanceForcesCrossfade() {
        val result = TransitionSelector(Random(1)).select(
            TransitionSelectionMode.AMBIENT_RANDOM,
            TransitionMode.SOFT_REVEAL,
            TransitionMode.selectableValues.toSet(),
            reducedMotion = false,
            forceCrossfade = true,
        )
        assertEquals(TransitionMode.CROSSFADE, result.effect)
        assertEquals("low_performance_mode", result.fallbackReason)
    }
}
