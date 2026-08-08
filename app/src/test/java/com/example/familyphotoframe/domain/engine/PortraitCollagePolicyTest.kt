package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.PortraitCollageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortraitCollagePolicyTest {

    // ---- orientation eligibility (task §3, §17) ----

    /** Task §3: eligible only when decoded width is less than height. */
    @Test fun portraitPhotosAreEligible() {
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(3000, 4000))
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(1080, 1920))
    }

    /** Task §2: landscape photos must never be forced into the three-panel frame. */
    @Test fun landscapePhotosAreRejected() {
        assertEquals(PhotoOrientation.LANDSCAPE, PortraitCollagePolicy.classify(4000, 3000))
        assertEquals(PhotoOrientation.LANDSCAPE, PortraitCollagePolicy.classify(1920, 1080))
    }

    /**
     * Near-square images sit in neither band. They are excluded deliberately: cropping one
     * into a tall narrow panel would discard most of the frame, which task §3 forbids.
     */
    @Test fun nearSquarePhotosAreNotTreatedAsPortrait() {
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(1000, 1000))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(1000, 1050))
    }

    @Test fun missingOrInvalidDimensionsAreNotEligible() {
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(null, 4000))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(0, 4000))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(3000, -1))
    }

    /** Task §3: fewer than three eligible portraits must not yield a three-panel frame. */
    @Test fun fewerThanThreePortraitsNeverYieldsThreeColumns() {
        val twoPortraits = listOf(0.75f, 0.75f)
        assertEquals(
            CollageLayout.TWO_COLUMNS,
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.PREFER_THREE, 1920, 1080, twoPortraits, maxPhotos = 3,
            ),
        )
        assertNull(
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.PREFER_THREE, 1920, 1080, listOf(0.75f), maxPhotos = 3,
            ),
        )
    }

    @Test fun exifRotationSwapsDimensions() {
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(4000, 3000, 6))
        assertEquals(PhotoOrientation.LANDSCAPE, PortraitCollagePolicy.classify(4000, 3000, 1))
    }

    @Test fun automaticUsesTwoForTypicalThreeByFourPortraits() {
        assertEquals(
            CollageLayout.TWO_COLUMNS,
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.AUTOMATIC,
                1920, 1080,
                listOf(0.75f, 0.75f, 0.75f),
                3,
            ),
        )
    }

    @Test fun automaticUsesThreeForVeryNarrowPortraits() {
        assertEquals(
            CollageLayout.THREE_COLUMNS,
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.AUTOMATIC,
                1920, 1080,
                listOf(0.50f, 0.52f, 0.48f),
                3,
            ),
        )
    }

    @Test fun disabledOrSingleCandidateReturnsNoCollage() {
        assertNull(PortraitCollagePolicy.chooseLayout(PortraitCollageMode.OFF, 1920, 1080, listOf(.7f, .7f), 3))
        assertNull(PortraitCollagePolicy.chooseLayout(PortraitCollageMode.AUTOMATIC, 1920, 1080, listOf(.7f), 3))
    }
}
