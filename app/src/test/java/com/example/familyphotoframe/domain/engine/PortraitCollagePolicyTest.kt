package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.PortraitCollageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitCollagePolicyTest {

    @Test fun portraitLandscapeAndSquareClassificationUsesDecodedGeometry() {
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(3000, 4000))
        assertEquals(PhotoOrientation.LANDSCAPE, PortraitCollagePolicy.classify(4000, 3000))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(1000, 1000))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(null, 4000))
    }

    @Test fun exifRotationSwapsDimensions() {
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(4000, 3000, 6))
        assertEquals(PhotoOrientation.LANDSCAPE, PortraitCollagePolicy.classify(4000, 3000, 1))
    }

    @Test fun tileFitLossIsZeroForMatchingRatiosAndSymmetricForScaleMismatch() {
        assertEquals(0f, PortraitCollagePolicy.tileFitLoss(0.75f, 0.75f), 0.0001f)
        assertEquals(0.5f, PortraitCollagePolicy.tileFitLoss(0.75f, 1.5f), 0.0001f)
        assertEquals(0.5f, PortraitCollagePolicy.tileFitLoss(1.5f, 0.75f), 0.0001f)
    }

    @Test fun sameFolderMixedBeatsCrossFolderPortraitCombination() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event-a", 0)
        val samePortrait = photo(2, 0.74f, PhotoOrientation.PORTRAIT, "event-a", 1)
        val sameLandscape = photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event-a", 2)
        val otherPortrait = photo(4, 0.75f, PhotoOrientation.PORTRAIT, "event-b", 3)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(samePortrait, sameLandscape, otherPortrait),
        )

        assertEquals(0, result.folderTier)
        assertEquals(setOf(1L, 2L, 3L), result.photoIds.toSet())
        assertEquals("PREFER_THREE", result.decisionReason)
    }

    @Test fun enoughSameFolderPortraitsBeatLandscapeCandidate() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val p1 = photo(2, 0.73f, PhotoOrientation.PORTRAIT, "event", 1)
        val p2 = photo(3, 0.77f, PhotoOrientation.PORTRAIT, "event", 2)
        val landscape = photo(4, 1.78f, PhotoOrientation.LANDSCAPE, "event", 3)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(landscape, p1, p2),
        )

        assertEquals(0, result.orientationTier)
        assertEquals(setOf(1L, 2L, 3L), result.photoIds.toSet())
    }

    @Test fun bestCombinationSearchCanSkipPoorEarlyCandidates() {
        val anchor = photo(1, 0.66f, PhotoOrientation.PORTRAIT, "event", 0)
        val poorA = photo(2, 0.42f, PhotoOrientation.PORTRAIT, "event", 1)
        val poorB = photo(3, 0.48f, PhotoOrientation.PORTRAIT, "event", 2)
        val goodC = photo(4, 0.68f, PhotoOrientation.PORTRAIT, "event", 3)
        val goodD = photo(5, 0.70f, PhotoOrientation.PORTRAIT, "event", 4)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(poorA, poorB, goodC, goodD),
        )

        assertEquals(setOf(1L, 4L, 5L), result.photoIds.toSet())
        assertEquals(6, result.evaluatedThreePhotoCombinations)
    }

    @Test fun alwaysTwoSearchesCompletePoolAndChoosesBestCompanionAndLayout() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val poor = photo(2, 0.35f, PhotoOrientation.PORTRAIT, "event", 1)
        val good = photo(3, 0.82f, PhotoOrientation.PORTRAIT, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.ALWAYS_TWO,
            anchor = anchor,
            candidates = listOf(poor, good),
        )

        assertEquals(2, result.photoIds.size)
        assertEquals(setOf(1L, 3L), result.photoIds.toSet())
        assertEquals(CollageLayout.TWO_COLUMNS, result.layout)
    }

    @Test fun pplCanUseAdaptiveLayout() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val portrait = photo(2, 0.75f, PhotoOrientation.PORTRAIT, "event", 1)
        val landscape = photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(portrait, landscape),
        )

        assertEquals(3, result.photoIds.size)
        assertTrue(
            result.layout == CollageLayout.FULL_LEFT_STACK_RIGHT ||
                result.layout == CollageLayout.STACK_LEFT_FULL_RIGHT,
        )
    }

    @Test fun pllUsesPortraitBesideStackedLandscapes() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val l1 = photo(2, 1.78f, PhotoOrientation.LANDSCAPE, "event", 1)
        val l2 = photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(l1, l2),
        )

        assertTrue(
            result.layout == CollageLayout.FULL_LEFT_STACK_RIGHT ||
                result.layout == CollageLayout.STACK_LEFT_FULL_RIGHT,
        )
        assertTrue(result.averageCropLoss < 0.10f)
    }

    @Test fun lllLandscapeFallbackIsAvailable() {
        val anchor = photo(1, 1.78f, PhotoOrientation.LANDSCAPE, "event", 0)
        val l1 = photo(2, 1.78f, PhotoOrientation.LANDSCAPE, "event", 1)
        val l2 = photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(l1, l2),
        )

        assertEquals(3, result.orientationTier)
        assertEquals(3, result.photoIds.size)
        assertEquals(setOf(1L, 2L, 3L), result.photoIds.toSet())
        assertTrue(result.averageCropLoss < 0.25f)
    }

    @Test fun squareCandidateIsPreferredToLandscapeWhenPortraitsAreInsufficient() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val square = photo(2, 1.0f, PhotoOrientation.SQUARE_OR_UNKNOWN, "event", 1)
        val landscape = photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.ALWAYS_TWO,
            anchor = anchor,
            candidates = listOf(landscape, square),
        )

        assertEquals(setOf(1L, 2L), result.photoIds.toSet())
        assertEquals(1, result.orientationTier)
    }

    @Test fun automaticChoosesTwoWhenThirdPortraitWouldBeDestructivelyCropped() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val companion = photo(2, 0.76f, PhotoOrientation.PORTRAIT, "event", 1)
        val veryNarrow = photo(3, 0.20f, PhotoOrientation.PORTRAIT, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.AUTOMATIC,
            anchor = anchor,
            candidates = listOf(companion, veryNarrow),
        )

        assertEquals(2, result.photoIds.size)
        assertEquals(setOf(1L, 2L), result.photoIds.toSet())
    }

    @Test fun automaticChoosesThreeWhenThreeNarrowPortraitsFitSubstantiallyBetter() {
        val anchor = photo(1, 0.50f, PhotoOrientation.PORTRAIT, "event", 0)
        val p1 = photo(2, 0.52f, PhotoOrientation.PORTRAIT, "event", 1)
        val p2 = photo(3, 0.48f, PhotoOrientation.PORTRAIT, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.AUTOMATIC,
            anchor = anchor,
            candidates = listOf(p1, p2),
        )

        assertEquals(3, result.photoIds.size)
        assertEquals(CollageLayout.THREE_COLUMNS, result.layout)
    }

    @Test fun preferThreeSelectsSameFolderMixedThreeWhenPortraitsAreInsufficient() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val portrait = photo(2, 0.76f, PhotoOrientation.PORTRAIT, "event", 1)
        val landscape = photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event", 2)

        val result = choose(
            mode = PortraitCollageMode.PREFER_THREE,
            anchor = anchor,
            candidates = listOf(portrait, landscape),
        )

        assertEquals(3, result.photoIds.size)
        assertEquals(0, result.folderTier)
        assertEquals(2, result.orientationTier)
    }

    @Test fun alwaysTwoReturnsExactlyTwoPhotosWhenCompanionExists() {
        val result = choose(
            mode = PortraitCollageMode.ALWAYS_TWO,
            anchor = photo(1, 0.7f, PhotoOrientation.PORTRAIT, "event", 0),
            candidates = listOf(
                photo(2, 0.7f, PhotoOrientation.PORTRAIT, "event", 1),
                photo(3, 0.7f, PhotoOrientation.PORTRAIT, "event", 2),
            ),
        )
        assertEquals(2, result.photoIds.size)
    }

    @Test fun unseenNearEquivalentCandidateBeatsRecentlyShownCandidate() {
        val now = 10L * 24L * 3_600_000L
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0, time = now)
        val recent = photo(
            2, 0.75f, PhotoOrientation.PORTRAIT, "event", 1,
            time = now, lastShown = now - 5L * 60_000L,
        )
        val unseen = photo(3, 0.75f, PhotoOrientation.PORTRAIT, "event", 2, time = now)

        val result = choose(
            mode = PortraitCollageMode.ALWAYS_TWO,
            anchor = anchor,
            candidates = listOf(recent, unseen),
            now = now,
        )

        assertEquals(setOf(1L, 3L), result.photoIds.toSet())
    }

    @Test fun sameInputAlwaysProducesSameSelection() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val candidates = listOf(
            photo(2, 0.74f, PhotoOrientation.PORTRAIT, "event", 1),
            photo(3, 1.78f, PhotoOrientation.LANDSCAPE, "event", 2),
            photo(4, 1.0f, PhotoOrientation.SQUARE_OR_UNKNOWN, "event", 3),
            photo(5, 0.76f, PhotoOrientation.PORTRAIT, "other", 4),
        )
        val first = choose(PortraitCollageMode.AUTOMATIC, anchor, candidates)
        repeat(20) {
            assertEquals(first, choose(PortraitCollageMode.AUTOMATIC, anchor, candidates))
        }
    }

    @Test fun candidatePoolIsBoundedToTwelve() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        val candidates = (2L..20L).mapIndexed { index, id ->
            photo(id, 0.75f, PhotoOrientation.PORTRAIT, "event", index + 1)
        }
        val result = choose(PortraitCollageMode.ALWAYS_TWO, anchor, candidates)
        assertEquals(12, result.evaluatedTwoPhotoCombinations)
    }

    @Test fun disabledOrMissingCompanionReturnsNoCollage() {
        val anchor = photo(1, 0.75f, PhotoOrientation.PORTRAIT, "event", 0)
        assertNull(
            PortraitCollagePolicy.chooseBestPresentation(
                PortraitCollageMode.OFF, 1920, 1080, anchor,
                listOf(photo(2, 0.75f, PhotoOrientation.PORTRAIT, "event", 1)), 3, 0,
            ),
        )
        assertNull(
            PortraitCollagePolicy.chooseBestPresentation(
                PortraitCollageMode.AUTOMATIC, 1920, 1080, anchor, emptyList(), 3, 0,
            ),
        )
    }

    @Test fun compatibilityChooseLayoutStillSupportsExistingCallers() {
        assertEquals(
            CollageLayout.TWO_COLUMNS,
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.ALWAYS_TWO, 1920, 1080, listOf(0.75f, 0.75f), 3,
            ),
        )
        assertNotNull(
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.AUTOMATIC, 1920, 1080, listOf(0.5f, 0.52f, 0.48f), 3,
            ),
        )
    }

    private fun choose(
        mode: PortraitCollageMode,
        anchor: CollagePhotoCandidate,
        candidates: List<CollagePhotoCandidate>,
        now: Long = 100L * 24L * 3_600_000L,
    ): CollageSelection = requireNotNull(
        PortraitCollagePolicy.chooseBestPresentation(
            mode = mode,
            screenWidth = 1920,
            screenHeight = 1080,
            anchor = anchor,
            candidates = candidates,
            maxPhotos = 3,
            nowEpochMs = now,
        ),
    )

    private fun photo(
        id: Long,
        ratio: Float,
        orientation: PhotoOrientation,
        folder: String,
        order: Int,
        time: Long = 0L,
        lastShown: Long? = null,
    ) = CollagePhotoCandidate(
        id = id,
        aspectRatio = ratio,
        orientation = orientation,
        folderKey = folder,
        captureTimeEpochMs = time,
        lastShownAtEpochMs = lastShown,
        order = order,
    )
}
