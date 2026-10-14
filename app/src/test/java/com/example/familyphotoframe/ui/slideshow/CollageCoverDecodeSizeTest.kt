package com.example.familyphotoframe.ui.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageCoverDecodeSizeTest {
    @Test
    fun matchingAspectDecodesAtTileSize() {
        assertEquals(800 to 1200, collageCoverDecodeSize(800, 1200, 800f / 1200f))
    }

    @Test
    fun landscapeInTallColumnCoversTheTileInsteadOfFittingInsideIt() {
        // A 4:3 landscape in a 635x794 column: fit would give 635x476 and a 1.67x stretch.
        val (w, h) = collageCoverDecodeSize(635, 794, 4f / 3f)
        assertTrue("height covers tile: $h", h >= 794)
        assertTrue("width covers tile: $w", w >= 635)
        assertEquals(4f / 3f, w.toFloat() / h, 0.01f)
    }

    @Test
    fun portraitInWideTileCoversTheTile() {
        // FULL_TOP_TWO_BOTTOM-style wide tile; covering needs 1.9x the tile area.
        val (w, h) = collageCoverDecodeSize(1000, 700, 3f / 4f)
        assertTrue(w >= 1000)
        assertTrue(h >= 700)
    }

    @Test
    fun extremePanoramaIsBoundedToTwiceTheTileArea() {
        val (w, h) = collageCoverDecodeSize(400, 1200, 4f)
        val ratio = (w.toLong() * h) / (400.0 * 1200.0)
        assertTrue("area ratio $ratio", ratio <= COLLAGE_MAX_COVER_AREA_RATIO + 0.01)
        assertEquals(4f, w.toFloat() / h, 0.02f)
    }

    @Test
    fun unknownAspectFallsBackToTileSize() {
        assertEquals(640 to 480, collageCoverDecodeSize(640, 480, Float.NaN))
        assertEquals(640 to 480, collageCoverDecodeSize(640, 480, 0f))
    }
}
