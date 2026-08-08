package com.example.familyphotoframe.ui.slideshow.transition

import com.example.familyphotoframe.data.settings.TransitionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionModelTest {
    @Test
    fun durationUsesEffectMultiplierAndBounds() {
        assertEquals(900, TransitionTiming.resolvedDurationMs(900, TransitionMode.CROSSFADE))
        assertEquals(1215, TransitionTiming.resolvedDurationMs(900, TransitionMode.SOFT_DISSOLVE))
        assertEquals(300, TransitionTiming.resolvedDurationMs(1, TransitionMode.CROSSFADE))
        assertEquals(2500, TransitionTiming.resolvedDurationMs(2000, TransitionMode.SOFT_DISSOLVE))
    }

    @Test
    fun everyImplementedEffectFinishesNeutralAndFullyVisible() {
        TransitionMode.selectableValues.forEach { effect ->
            val frame = transitionFrame(effect, 1f)
            assertEquals("$effect alpha", 1f, frame.incoming.alpha, 0.0001f)
            assertEquals("$effect scale", 1f, frame.incoming.scale, 0.0001f)
            assertEquals("$effect tx", 0f, frame.incoming.translationXFraction, 0.0001f)
            assertEquals("$effect ty", 0f, frame.incoming.translationYFraction, 0.0001f)
        }
    }

    @Test
    fun opacityNeverExposesTheApplicationBackground() {
        TransitionMode.selectableValues.forEach { effect ->
            for (step in 0..100) {
                val frame = transitionFrame(effect, step / 100f)
                assertTrue(
                    "$effect opacity sum at $step",
                    frame.outgoing.alpha + frame.incoming.alpha >= 0.999f,
                )
            }
        }
    }

    @Test
    fun transitionProgressProducesValidAlphaRange() {
        TransitionMode.selectableValues.forEach { effect ->
            for (step in 0..20) {
                val frame = transitionFrame(effect, step / 20f)
                assertTrue("$effect outgoing alpha", frame.outgoing.alpha in 0f..1f)
                assertTrue("$effect incoming alpha", frame.incoming.alpha in 0f..1f)
            }
        }
    }
    @Test
    fun softFocusLayersNeverExposeTheApplicationBackground() {
        for (step in 0..100) {
            val frame = softFocusFrame(step / 100f)
            assertTrue("soft focus alpha sum at $step", frame.combinedAlpha >= 0.999f)
        }
    }

}
