package com.example.familyphotoframe.ui.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageBlurTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(p: Int) = (p ushr 16) and 0xFF
    private fun alpha(p: Int) = (p ushr 24) and 0xFF

    @Test fun uniformImageIsUnchanged() {
        val pixels = IntArray(8 * 8) { argb(255, 100, 150, 200) }
        val out = ImageBlur.boxBlur(pixels, 8, 8, radius = 3)
        out.forEach { assertEquals(argb(255, 100, 150, 200), it) }
    }

    @Test fun zeroRadiusIsIdentity() {
        val pixels = IntArray(4 * 4) { it }
        assertTrue(ImageBlur.boxBlur(pixels, 4, 4, radius = 0).contentEquals(pixels))
    }

    @Test fun blurSpreadsASinglePeak() {
        // One bright pixel in the middle of a black field must bleed into neighbours.
        val w = 9; val h = 9
        val pixels = IntArray(w * h) { argb(255, 0, 0, 0) }
        pixels[4 * w + 4] = argb(255, 255, 0, 0)
        val out = ImageBlur.boxBlur(pixels, w, h, radius = 2)
        assertTrue("centre must dim", red(out[4 * w + 4]) < 255)
        assertTrue("neighbour must brighten", red(out[4 * w + 5]) > 0)
    }

    @Test fun alphaIsPreservedForOpaqueInput() {
        val pixels = IntArray(6 * 6) { argb(255, it * 3, 0, 0) }
        ImageBlur.boxBlur(pixels, 6, 6, radius = 2).forEach { assertEquals(255, alpha(it)) }
    }

    @Test fun outputStaysInByteRange() {
        val pixels = IntArray(16 * 16) { argb(255, (it * 7) % 256, (it * 13) % 256, (it * 29) % 256) }
        ImageBlur.boxBlur(pixels, 16, 16, radius = 5).forEach { p ->
            listOf((p ushr 24) and 0xFF, (p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF)
                .forEach { assertTrue(it in 0..255) }
        }
    }

    @Test fun radiusLargerThanImageDoesNotCrash() {
        val pixels = IntArray(3 * 3) { argb(255, 10, 20, 30) }
        val out = ImageBlur.boxBlur(pixels, 3, 3, radius = 50)
        assertEquals(9, out.size)
    }

    @Test fun nonSquareImageKeepsDimensions() {
        val out = ImageBlur.boxBlur(IntArray(12 * 5) { argb(255, 40, 40, 40) }, 12, 5, radius = 2)
        assertEquals(60, out.size)
    }

    @Test fun downsampleBudgetMatchesSpec() {
        // Spec §10.2: blur must come from a heavily downsampled bitmap (<= 256 px).
        assertTrue(ImageBlur.MAX_DIMENSION_PX <= 256)
    }
}

class KenBurnsTest {

    @Test fun startsUnscaledAndCentred() {
        val f = KenBurns.frameFor(seed = 1L, progress = 0f)
        assertEquals(KenBurns.DEFAULT_START_SCALE, f.scale, 0.0001f)
        assertEquals(0f, f.translateXFraction, 0.0001f)
        assertEquals(0f, f.translateYFraction, 0.0001f)
    }

    @Test fun endsAtTargetScale() {
        val f = KenBurns.frameFor(seed = 1L, progress = 1f)
        assertEquals(KenBurns.DEFAULT_END_SCALE, f.scale, 0.0001f)
    }

    @Test fun panNeverExposesAnEdge() {
        // The image must always cover the viewport: |translate| <= (scale - 1) / 2.
        for (seed in 0L..20L) {
            var p = 0f
            while (p <= 1f) {
                val f = KenBurns.frameFor(seed, p)
                val bound = (f.scale - 1f) / 2f + 1e-4f
                assertTrue("x out of bounds at p=$p", kotlin.math.abs(f.translateXFraction) <= bound)
                assertTrue("y out of bounds at p=$p", kotlin.math.abs(f.translateYFraction) <= bound)
                p += 0.05f
            }
        }
    }

    @Test fun progressIsClamped() {
        assertEquals(KenBurns.frameFor(3L, 0f), KenBurns.frameFor(3L, -5f))
        assertEquals(KenBurns.frameFor(3L, 1f), KenBurns.frameFor(3L, 9f))
    }

    @Test fun sameSeedIsDeterministic() {
        assertEquals(KenBurns.frameFor(42L, 0.5f), KenBurns.frameFor(42L, 0.5f))
    }

    @Test fun differentSeedsUseDifferentDirections() {
        val directions = (0L..50L).map { KenBurns.directionIndex(it) }.toSet()
        assertTrue("expected several distinct directions, got $directions", directions.size >= 3)
    }

    @Test fun scaleIsMonotonic() {
        var previous = 0f
        var p = 0f
        while (p <= 1f) {
            val s = KenBurns.frameFor(7L, p).scale
            assertTrue(s >= previous)
            previous = s
            p += 0.1f
        }
    }

    @Test fun zoomStaysSubtle() {
        // Big zooms cost fill rate on weak GPUs (spec §2.2) and look cheap.
        assertTrue(KenBurns.DEFAULT_END_SCALE <= 1.2f)
        assertNotEquals(KenBurns.DEFAULT_START_SCALE, KenBurns.DEFAULT_END_SCALE)
    }
}
