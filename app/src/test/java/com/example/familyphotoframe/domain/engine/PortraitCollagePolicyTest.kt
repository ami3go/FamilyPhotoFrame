package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitCollagePolicyTest {
    private val screenW = 1920
    private val screenH = 1080
    private val now = 2_000_000_000_000L

    private fun meta(
        id: Long,
        ratio: Float,
        orientation: PhotoOrientation,
        folder: String = "source\u001ffolder-a",
        order: Int = id.toInt(),
        time: Long = 1_700_000_000_000L + id * 60_000L,
        lastShown: Long? = null,
    ) = CollageCandidateMeta(
        id = id,
        aspectRatio = ratio,
        orientation = orientation,
        folderKey = folder,
        captureTimeEpochMs = time,
        lastShownAtEpochMs = lastShown,
        candidateOrder = order,
    )

    @Test fun orientationClassificationAndExifRotationRemainStable() {
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(3000, 4000))
        assertEquals(PhotoOrientation.LANDSCAPE, PortraitCollagePolicy.classify(4000, 3000))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(1000, 1000))
        assertEquals(PhotoOrientation.PORTRAIT, PortraitCollagePolicy.classify(4000, 3000, 6))
        assertEquals(PhotoOrientation.SQUARE_OR_UNKNOWN, PortraitCollagePolicy.classify(null, 4000))
    }

    @Test fun tileFitLossMatchesRetainedFractionDefinition() {
        assertEquals(0f, PortraitCollagePolicy.tileFitLoss(1f, 1f), 0.0001f)
        assertEquals(.5f, PortraitCollagePolicy.tileFitLoss(2f, 1f), 0.0001f)
        assertEquals(.5f, PortraitCollagePolicy.tileFitLoss(.5f, 1f), 0.0001f)
    }

    @Test fun sameFolderMixedBeatsCrossFolderPortraitOnly() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val samePortrait = meta(2, .62f, PhotoOrientation.PORTRAIT, order = 0)
        val sameLandscape = meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1)
        val otherPortrait = meta(4, .61f, PhotoOrientation.PORTRAIT, folder = "source\u001ffolder-b", order = 2)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor,
            listOf(samePortrait, sameLandscape, otherPortrait), 3, now,
        )!!
        assertEquals(0, decision.folderTier)
        assertTrue(3L in decision.photoIds)
        assertTrue(4L !in decision.photoIds)
    }

    @Test fun enoughSameFolderPortraitsBeatLandscapeCandidates() {
        val anchor = meta(1, .58f, PhotoOrientation.PORTRAIT, order = -1)
        val p1 = meta(2, .59f, PhotoOrientation.PORTRAIT, order = 0)
        val p2 = meta(3, .60f, PhotoOrientation.PORTRAIT, order = 1)
        val landscape = meta(4, 1.78f, PhotoOrientation.LANDSCAPE, order = 2)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor,
            listOf(landscape, p1, p2), 3, now,
        )!!
        assertEquals(0, decision.orientationTier)
        assertEquals(setOf(1L, 2L, 3L), decision.photoIds.toSet())
        assertEquals(CollageLayout.THREE_COLUMNS, decision.layout)
    }

    @Test fun laterBetterCandidatesBeatPoorEarlyCandidates() {
        val anchor = meta(1, .59f, PhotoOrientation.PORTRAIT, order = -1)
        val poorA = meta(2, .30f, PhotoOrientation.PORTRAIT, order = 0)
        val poorB = meta(3, .34f, PhotoOrientation.PORTRAIT, order = 1)
        val goodC = meta(4, .58f, PhotoOrientation.PORTRAIT, order = 2)
        val goodD = meta(5, .60f, PhotoOrientation.PORTRAIT, order = 3)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor,
            listOf(poorA, poorB, goodC, goodD), 3, now,
        )!!
        assertEquals(setOf(1L, 4L, 5L), decision.photoIds.toSet())
    }

    @Test fun bestTwoPhotoLayoutIsEvaluatedAcrossPool() {
        val anchor = meta(1, 1.80f, PhotoOrientation.LANDSCAPE, order = -1)
        val candidate = meta(2, 1.78f, PhotoOrientation.LANDSCAPE, order = 0)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.ALWAYS_TWO, screenW, screenH, anchor, listOf(candidate), 3, now,
        )!!
        assertEquals(2, decision.photoIds.size)
        assertEquals(CollageLayout.TWO_ROWS, decision.layout)
    }

    @Test fun pplKeepsBothPortraitsFullHeightInUnequalColumns() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val portrait = meta(2, .62f, PhotoOrientation.PORTRAIT, order = 0)
        val landscape = meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor,
            listOf(portrait, landscape), 3, now,
        )!!
        assertEquals(3, decision.photoIds.size)
        assertEquals(1L, decision.photoIds.first())
        // Half-height featured tiles wreck the second portrait; a wide column for the
        // landscape keeps every crop moderate without leaving screen area empty.
        assertTrue(
            decision.layout in setOf(
                CollageLayout.WIDE_LEFT_TWO_NARROW,
                CollageLayout.NARROW_WIDE_NARROW,
                CollageLayout.TWO_NARROW_WIDE_RIGHT,
            )
        )
        assertTrue(decision.maximumCropLoss < .60f)
    }

    @Test fun pllUsesStackedMixedLayout() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val landscapeA = meta(2, 1.78f, PhotoOrientation.LANDSCAPE, order = 0)
        val landscapeB = meta(3, 1.84f, PhotoOrientation.LANDSCAPE, order = 1)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor,
            listOf(landscapeA, landscapeB), 3, now,
        )!!
        assertEquals(3, decision.photoIds.size)
        assertEquals(1L, decision.photoIds.first())
        assertTrue(decision.layout in setOf(CollageLayout.FULL_LEFT_TWO_RIGHT, CollageLayout.TWO_LEFT_FULL_RIGHT))
    }

    @Test fun lllIsAvailableAsFallback() {
        val anchor = meta(1, 1.75f, PhotoOrientation.LANDSCAPE, order = -1)
        val a = meta(2, 1.80f, PhotoOrientation.LANDSCAPE, order = 0)
        val b = meta(3, 1.70f, PhotoOrientation.LANDSCAPE, order = 1)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, listOf(a, b), 3, now,
        )!!
        assertEquals(3, decision.orientationTier)
        assertTrue(decision.layout in setOf(CollageLayout.FULL_TOP_TWO_BOTTOM, CollageLayout.TWO_TOP_FULL_BOTTOM))
    }

    @Test fun squareCandidateCanParticipateWhenNeeded() {
        val anchor = meta(1, .65f, PhotoOrientation.PORTRAIT, order = -1)
        val square = meta(2, 1.02f, PhotoOrientation.SQUARE_OR_UNKNOWN, order = 0)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.ALWAYS_TWO, screenW, screenH, anchor, listOf(square), 2, now,
        )!!
        assertEquals(setOf(1L, 2L), decision.photoIds.toSet())
        assertEquals(1, decision.orientationTier)
    }

    @Test fun automaticUsesTwoWhenThreeHasWorseVisualLoss() {
        val anchor = meta(1, .75f, PhotoOrientation.PORTRAIT, order = -1)
        val a = meta(2, .75f, PhotoOrientation.PORTRAIT, order = 0)
        val b = meta(3, .75f, PhotoOrientation.PORTRAIT, order = 1)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, listOf(a, b), 3, now,
        )!!
        assertEquals(2, decision.photoIds.size)
        assertEquals(CollageLayout.TWO_COLUMNS, decision.layout)
    }

    @Test fun automaticUsesThreeWhenItFitsSubstantiallyBetter() {
        val anchor = meta(1, .50f, PhotoOrientation.PORTRAIT, order = -1)
        val a = meta(2, .51f, PhotoOrientation.PORTRAIT, order = 0)
        val b = meta(3, .49f, PhotoOrientation.PORTRAIT, order = 1)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, listOf(a, b), 3, now,
        )!!
        assertEquals(3, decision.photoIds.size)
        assertEquals(CollageLayout.THREE_COLUMNS, decision.layout)
    }

    @Test fun preferThreeUsesSameFolderMixedWhenPortraitsAreInsufficient() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val portrait = meta(2, .61f, PhotoOrientation.PORTRAIT, order = 0)
        val landscape = meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor,
            listOf(portrait, landscape), 3, now,
        )!!
        assertEquals(3, decision.photoIds.size)
        assertEquals(2, decision.orientationTier)
    }

    @Test fun verticalFilterFallsBackToOnePhotoWithoutFill() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val landscapes = listOf(
            meta(2, 1.78f, PhotoOrientation.LANDSCAPE, order = 0),
            meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1),
        )
        assertNull(
            PortraitCollagePolicy.chooseBestPresentation(
                PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, landscapes, 3, now,
                orientationFilter = CollageOrientationFilter.PORTRAIT_ONLY,
            )
        )
    }

    @Test fun verticalFilterFillsRemainingTilesWithLandscapeWhenAllowed() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val landscapes = listOf(
            meta(2, 1.78f, PhotoOrientation.LANDSCAPE, order = 0),
            meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1),
        )
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, landscapes, 3, now,
            orientationFilter = CollageOrientationFilter.PORTRAIT_ONLY,
            fillWithOtherOrientations = true,
        )!!
        assertEquals(3, decision.photoIds.size)
        assertEquals(1L, decision.photoIds.first())
        assertEquals("ORIENTATION_FILL_RELAXED", decision.decisionReason)
    }

    @Test fun fillAddsOneLandscapeOnlyWhenAThirdPortraitIsMissing() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = listOf(
            meta(2, .61f, PhotoOrientation.PORTRAIT, order = 0),
            meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1),
            meta(4, 1.78f, PhotoOrientation.LANDSCAPE, order = 2),
        )
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, candidates, 3, now,
            orientationFilter = CollageOrientationFilter.PORTRAIT_ONLY,
            fillWithOtherOrientations = true,
        )!!
        assertEquals(3, decision.photoIds.size)
        assertTrue(1L in decision.photoIds && 2L in decision.photoIds)
        assertEquals(1, decision.photoIds.count { it == 3L || it == 4L })
    }

    @Test fun fillKeepsThreeVerticalsWhenEnoughAreAvailable() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = listOf(
            meta(2, 1.80f, PhotoOrientation.LANDSCAPE, order = 0),
            meta(3, .61f, PhotoOrientation.PORTRAIT, order = 1),
            meta(4, .59f, PhotoOrientation.PORTRAIT, order = 2),
        )
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, candidates, 3, now,
            orientationFilter = CollageOrientationFilter.PORTRAIT_ONLY,
            fillWithOtherOrientations = true,
        )!!
        assertEquals(setOf(1L, 3L, 4L), decision.photoIds.toSet())
        assertEquals(CollageLayout.THREE_COLUMNS, decision.layout)
    }

    @Test fun fillNeverOverridesAnAnchorTheFilterExcludes() {
        val anchor = meta(1, 1.78f, PhotoOrientation.LANDSCAPE, order = -1)
        val candidates = listOf(meta(2, .60f, PhotoOrientation.PORTRAIT, order = 0))
        assertNull(
            PortraitCollagePolicy.chooseBestPresentation(
                PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, candidates, 3, now,
                orientationFilter = CollageOrientationFilter.PORTRAIT_ONLY,
                fillWithOtherOrientations = true,
            )
        )
    }

    @Test fun alwaysTwoSelectsExactlyTwoPhotos() {
        val anchor = meta(1, .55f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = listOf(
            meta(2, .56f, PhotoOrientation.PORTRAIT, order = 0),
            meta(3, .57f, PhotoOrientation.PORTRAIT, order = 1),
            meta(4, 1.8f, PhotoOrientation.LANDSCAPE, order = 2),
        )
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.ALWAYS_TWO, screenW, screenH, anchor, candidates, 3, now,
        )!!
        assertEquals(2, decision.photoIds.size)
    }

    @Test fun unseenNearEquivalentCandidateBeatsRecentlyShownCandidate() {
        val anchorTime = 1_700_000_000_000L
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1, time = anchorTime)
        val recent = meta(2, .60f, PhotoOrientation.PORTRAIT, order = 0, time = anchorTime, lastShown = now - 5 * 60_000L)
        val unseen = meta(3, .60f, PhotoOrientation.PORTRAIT, order = 1, time = anchorTime, lastShown = null)
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.ALWAYS_TWO, screenW, screenH, anchor, listOf(recent, unseen), 2, now,
        )!!
        assertTrue(3L in decision.photoIds)
        assertTrue(2L !in decision.photoIds)
    }

    @Test fun deterministicInputAlwaysProducesSameDecision() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = listOf(
            meta(2, .61f, PhotoOrientation.PORTRAIT, order = 0),
            meta(3, 1.80f, PhotoOrientation.LANDSCAPE, order = 1),
            meta(4, 1.00f, PhotoOrientation.SQUARE_OR_UNKNOWN, order = 2),
        )
        val first = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, candidates, 3, now,
        )
        repeat(20) {
            assertEquals(first, PortraitCollagePolicy.chooseBestPresentation(
                PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, candidates, 3, now,
            ))
        }
    }

    @Test fun twelveCandidatePoolEvaluatesAllSixtySixThreePhotoCombinations() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = (0 until 12).map { index ->
            meta(index.toLong() + 2L, .55f + index * .01f, PhotoOrientation.PORTRAIT, order = index)
        }
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, candidates, 3, now,
        )!!
        assertEquals(12, decision.stats.evaluatedTwoPhotoCombinations)
        assertEquals(66, decision.stats.evaluatedThreePhotoCombinations)
        assertTrue(decision.stats.evaluatedLayoutCount >= 78)
    }

    @Test fun offOrNoCompanionReturnsNull() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        assertNull(PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.OFF, screenW, screenH, anchor,
            listOf(meta(2, .6f, PhotoOrientation.PORTRAIT)), 3, now,
        ))
        assertNull(PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, emptyList(), 3, now,
        ))
    }

    @Test fun compatibilityChooseLayoutRetainsLegacyPortraitBehaviour() {
        assertEquals(
            CollageLayout.TWO_COLUMNS,
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.AUTOMATIC, screenW, screenH,
                listOf(.75f, .75f, .75f), 3,
            ),
        )
        assertEquals(
            CollageLayout.THREE_COLUMNS,
            PortraitCollagePolicy.chooseLayout(
                PortraitCollageMode.AUTOMATIC, screenW, screenH,
                listOf(.50f, .52f, .48f), 3,
            ),
        )
    }

    @Test fun allLayoutGeometryCoversTheScreenWithoutPolicyGaps() {
        CollageLayout.values().forEach { layout ->
            val tiles = CollageLayoutGeometry.tiles(layout)
            assertEquals(layout.tileCount, tiles.size)
            val area = tiles.sumOf { it.area.toDouble() }
            assertEquals(1.0, area, 0.0001)
            assertTrue(tiles.all { it.left >= 0f && it.top >= 0f && it.right <= 1f && it.bottom <= 1f })
        }
    }

    @Test fun automaticMaxTwoDoesNotClaimThreeWasCompared() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = listOf(
            meta(2, .61f, PhotoOrientation.PORTRAIT, order = 0),
            meta(3, .62f, PhotoOrientation.PORTRAIT, order = 1),
        )
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC, screenW, screenH, anchor, candidates, 2, now,
        )!!
        assertEquals(0, decision.stats.evaluatedThreePhotoCombinations)
        assertEquals("MAX_PHOTOS_LIMIT", decision.decisionReason)
    }

    @Test fun preferThreeMaxTwoReportsEffectiveLimit() {
        val anchor = meta(1, .60f, PhotoOrientation.PORTRAIT, order = -1)
        val candidates = listOf(
            meta(2, .61f, PhotoOrientation.PORTRAIT, order = 0),
            meta(3, .62f, PhotoOrientation.PORTRAIT, order = 1),
        )
        val decision = PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE, screenW, screenH, anchor, candidates, 2, now,
        )!!
        assertEquals(0, decision.stats.evaluatedThreePhotoCombinations)
        assertEquals("MAX_PHOTOS_LIMIT", decision.decisionReason)
    }
}
