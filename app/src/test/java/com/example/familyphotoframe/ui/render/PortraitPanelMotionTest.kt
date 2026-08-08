package com.example.familyphotoframe.ui.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Task §17 unit tests for three-portrait-panel motion.
 *
 * The crop-safety cases are the important ones: an exposed panel edge is the most visible
 * possible failure of this feature, and it depends on an invariant that holds across the
 * whole path rather than at any single sampled point.
 */
class PortraitPanelMotionTest {

    private val duration = 15_000

    // ---- crop safety (task §6) ----

    /**
     * The core guarantee: a centre-cropped image covers its panel exactly at scale 1, so
     * scale `s` yields `(s - 1) / 2` of overscan per side. Sampling densely across every
     * preset proves no point on any path can expose an edge.
     */
    @Test fun noPresetEverExposesAPanelEdge() {
        for (preset in PanelMotionPreset.entries) {
            for (panelIndex in 0..2) {
                val path = PortraitPanelMotion.pathFor(
                    preset, panelIndex, duration, PanelMotionProfile.SUBTLE, seed = 7L,
                )
                for (step in 0..200) {
                    val frame = PortraitPanelMotion.frameAt(path, step / 200f)
                    val allowed = (frame.scale - 1f) / 2f + 1e-6f
                    assertTrue(
                        "$preset panel $panelIndex exposed an edge on X at step $step",
                        abs(frame.translateXFraction) <= allowed,
                    )
                    assertTrue(
                        "$preset panel $panelIndex exposed an edge on Y at step $step",
                        abs(frame.translateYFraction) <= allowed,
                    )
                }
            }
        }
    }

    /** Zoom-out paths are tightest at the end, so they are the case most likely to slip. */
    @Test fun zoomOutStaysSafeAtItsSmallestScale() {
        val path = PortraitPanelMotion.pathFor(
            PanelMotionPreset.ZOOM_OUT, 0, duration, PanelMotionProfile.SUBTLE, seed = 3L,
        )
        assertTrue("zoom out should end smaller than it starts", path.endScale < path.startScale)
        val end = PortraitPanelMotion.frameAt(path, 1f)
        assertTrue(abs(end.translateXFraction) <= (end.scale - 1f) / 2f + 1e-6f)
        assertTrue(abs(end.translateYFraction) <= (end.scale - 1f) / 2f + 1e-6f)
    }

    /** Task §5: scale must stay inside the declared 1.01x-1.05x band. */
    @Test fun scaleStaysWithinTheDeclaredBand() {
        for (preset in PanelMotionPreset.entries) {
            val path = PortraitPanelMotion.pathFor(
                preset, 0, duration, PanelMotionProfile.SUBTLE, seed = 11L,
            )
            for (scale in listOf(path.startScale, path.endScale)) {
                assertTrue("$preset scale $scale below band", scale >= PortraitPanelMotion.BASE_MIN_SCALE - 1e-6f)
                assertTrue("$preset scale $scale above band", scale <= PortraitPanelMotion.BASE_MAX_SCALE + 1e-6f)
            }
        }
    }

    /** Task §5: translation never exceeds 2% of the panel on either axis. */
    @Test fun translationNeverExceedsTheTwoPercentCap() {
        for (preset in PanelMotionPreset.entries) {
            val path = PortraitPanelMotion.pathFor(
                preset, 0, duration, PanelMotionProfile.SUBTLE, seed = 5L,
            )
            val values = listOf(
                path.startTranslateXFraction, path.endTranslateXFraction,
                path.startTranslateYFraction, path.endTranslateYFraction,
            )
            values.forEach {
                assertTrue("$preset translation $it exceeds cap", abs(it) <= PortraitPanelMotion.MAX_TRANSLATION_FRACTION + 1e-6f)
            }
        }
    }

    /** Task §6: panels are tall and narrow, so horizontal pan is limited more aggressively. */
    @Test fun horizontalMovementIsLimitedMoreThanVertical() {
        val horizontal = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_LEFT_TO_RIGHT, 0, duration, PanelMotionProfile.SUBTLE, seed = 1L,
        )
        val vertical = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_DOWN, 0, duration, PanelMotionProfile.SUBTLE, seed = 1L,
        )
        val hSpan = abs(horizontal.endTranslateXFraction - horizontal.startTranslateXFraction)
        val vSpan = abs(vertical.endTranslateYFraction - vertical.startTranslateYFraction)
        assertTrue("horizontal span $hSpan should be under vertical span $vSpan", hSpan < vSpan)
    }

    @Test fun maxSafeTranslationIsNeverNegative() {
        assertEquals(0f, PortraitPanelMotion.maxSafeTranslation(1f), 1e-6f)
        assertEquals(0f, PortraitPanelMotion.maxSafeTranslation(0.5f), 1e-6f)
        assertEquals(0.025f, PortraitPanelMotion.maxSafeTranslation(1.05f), 1e-6f)
    }

    // ---- preset selection (task §8) ----

    @Test fun threePresetsAreAlwaysDistinct() {
        for (seed in 0L until 400L) {
            val presets = PortraitPanelMotion.selectPresets(seed)
            assertEquals(3, presets.size)
            assertEquals("seed $seed produced a duplicate preset", 3, presets.toSet().size)
        }
    }

    /** Task §8: the outer panels must not both drift the same way horizontally. */
    @Test fun outerPanelsNeverDriftTheSameDirection() {
        for (seed in 0L until 400L) {
            val (left, _, right) = PortraitPanelMotion.selectPresets(seed).let {
                Triple(it[0], it[1], it[2])
            }
            assertTrue(
                "seed $seed sent both outer panels the same way",
                !PortraitPanelMotion.sameHorizontalDirection(left, right),
            )
        }
    }

    /** Task §8: no two panels may look synchronised. */
    @Test fun noTwoPanelsUseNearlyIdenticalPaths() {
        for (seed in 0L until 200L) {
            val paths = PortraitPanelMotion.pathsForFrame(seed, duration, PanelMotionProfile.SUBTLE)
            for (i in paths.indices) {
                for (j in i + 1 until paths.size) {
                    assertTrue(
                        "seed $seed panels $i and $j are nearly identical",
                        !PortraitPanelMotion.nearlyIdentical(paths[i], paths[j]),
                    )
                }
            }
        }
    }

    /** Task §8: the centre panel stays calmer than the outer panels. */
    @Test fun centrePanelIsLessActiveThanOuterPanels() {
        val outer = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_DOWN, 0, duration, PanelMotionProfile.SUBTLE, seed = 2L,
        )
        val centre = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_DOWN, 1, duration, PanelMotionProfile.SUBTLE, seed = 2L,
        )
        val outerSpan = abs(outer.endTranslateYFraction - outer.startTranslateYFraction)
        val centreSpan = abs(centre.endTranslateYFraction - centre.startTranslateYFraction)
        assertTrue("centre span $centreSpan should be under outer span $outerSpan", centreSpan < outerSpan)
    }

    // ---- determinism (task §7) ----

    @Test fun sameSeedProducesIdenticalPaths() {
        val a = PortraitPanelMotion.pathsForFrame(4242L, duration, PanelMotionProfile.SUBTLE)
        val b = PortraitPanelMotion.pathsForFrame(4242L, duration, PanelMotionProfile.SUBTLE)
        assertEquals(a, b)
    }

    @Test fun differentSeedsEventuallyDiffer() {
        val distinct = (0L until 60L)
            .map { PortraitPanelMotion.selectPresets(it) }
            .toSet()
        assertTrue("seeded selection collapsed to one combination", distinct.size > 1)
    }

    // ---- profiles and accessibility (task §12, §13) ----

    @Test fun disabledSettingProducesNoMotion() {
        assertEquals(PanelMotionProfile.OFF, PortraitPanelMotion.profileFor(enabled = false, animationScale = 1f))
    }

    @Test fun systemAnimationsDisabledProducesNoMotion() {
        assertEquals(PanelMotionProfile.OFF, PortraitPanelMotion.profileFor(enabled = true, animationScale = 0f))
    }

    @Test fun reducedSystemAnimationsReduceAmplitude() {
        assertEquals(PanelMotionProfile.REDUCED, PortraitPanelMotion.profileFor(enabled = true, animationScale = 0.5f))
    }

    @Test fun normalSystemAnimationsUseSubtleProfile() {
        assertEquals(PanelMotionProfile.SUBTLE, PortraitPanelMotion.profileFor(enabled = true, animationScale = 1f))
    }

    /** An unreadable system setting must not silently disable an enabled feature. */
    @Test fun unknownAnimationScaleFallsBackToSubtle() {
        assertEquals(PanelMotionProfile.SUBTLE, PortraitPanelMotion.profileFor(enabled = true, animationScale = -1f))
    }

    @Test fun reducedProfileMovesLessThanSubtle() {
        val subtle = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_DOWN, 0, duration, PanelMotionProfile.SUBTLE, seed = 9L,
        )
        val reduced = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_DOWN, 0, duration, PanelMotionProfile.REDUCED, seed = 9L,
        )
        val subtleSpan = abs(subtle.endTranslateYFraction - subtle.startTranslateYFraction)
        val reducedSpan = abs(reduced.endTranslateYFraction - reduced.startTranslateYFraction)
        assertTrue("reduced span $reducedSpan should be under subtle span $subtleSpan", reducedSpan < subtleSpan)
    }

    @Test fun offProfileProducesAnIdentityTransform() {
        val path = PortraitPanelMotion.pathFor(
            PanelMotionPreset.PAN_DOWN, 0, duration, PanelMotionProfile.OFF, seed = 1L,
        )
        val start = PortraitPanelMotion.frameAt(path, 0f)
        val end = PortraitPanelMotion.frameAt(path, 1f)
        listOf(start, end).forEach {
            assertEquals(1f, it.scale, 1e-6f)
            assertEquals(0f, it.translateXFraction, 1e-6f)
            assertEquals(0f, it.translateYFraction, 1e-6f)
        }
    }

    // ---- easing and sampling (task §5) ----

    @Test fun easingIsClampedAndMonotonic() {
        assertEquals(0f, PortraitPanelMotion.easeInOut(-1f), 1e-6f)
        assertEquals(1f, PortraitPanelMotion.easeInOut(2f), 1e-6f)
        var previous = -1f
        for (step in 0..100) {
            val value = PortraitPanelMotion.easeInOut(step / 100f)
            assertTrue("easing went backwards at $step", value >= previous)
            previous = value
        }
    }

    /** Task §5: one continuous move, so the endpoints are the path endpoints exactly. */
    @Test fun pathRunsFromStartToEndAcrossTheSlide() {
        val path = PortraitPanelMotion.pathFor(
            PanelMotionPreset.ZOOM_IN, 0, duration, PanelMotionProfile.SUBTLE, seed = 6L,
        )
        assertEquals(path.startScale, PortraitPanelMotion.frameAt(path, 0f).scale, 1e-6f)
        assertEquals(path.endScale, PortraitPanelMotion.frameAt(path, 1f).scale, 1e-6f)
        assertNotEquals(path.startScale, path.endScale)
    }

    @Test fun progressOutsideZeroToOneIsClamped() {
        val path = PortraitPanelMotion.pathFor(
            PanelMotionPreset.ZOOM_IN, 0, duration, PanelMotionProfile.SUBTLE, seed = 6L,
        )
        assertEquals(path.startScale, PortraitPanelMotion.frameAt(path, -5f).scale, 1e-6f)
        assertEquals(path.endScale, PortraitPanelMotion.frameAt(path, 5f).scale, 1e-6f)
    }

    // ---- diagnostics (task §16) ----

    @Test fun describeFrameNamesEveryPanelAndPreset() {
        val paths = PortraitPanelMotion.pathsForFrame(21L, duration, PanelMotionProfile.SUBTLE)
        val text = PortraitPanelMotion.describeFrame(paths)
        assertTrue(text.contains("left="))
        assertTrue(text.contains("centre="))
        assertTrue(text.contains("right="))
        paths.forEach { assertTrue("missing ${it.preset}", text.contains(it.preset.name)) }
    }
}
