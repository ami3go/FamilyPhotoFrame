package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageCustomizationPolicyTest {
    private fun meta(id: Long, orientation: PhotoOrientation, order: Int = id.toInt()) = CollageCandidateMeta(
        id=id,
        aspectRatio=when(orientation){PhotoOrientation.PORTRAIT->0.6f;PhotoOrientation.LANDSCAPE->1.6f;PhotoOrientation.SQUARE_OR_UNKNOWN->1f},
        orientation=orientation, folderKey="same", captureTimeEpochMs=id*1_000L,
        lastShownAtEpochMs=null, candidateOrder=order,
    )

    @Test fun portraitOnlyRejectsLandscapeAnchor() {
        assertNull(PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.AUTOMATIC,1800,1000,meta(1,PhotoOrientation.LANDSCAPE),
            listOf(meta(2,PhotoOrientation.PORTRAIT)),3,0L,
            orientationFilter=CollageOrientationFilter.PORTRAIT_ONLY,
        ))
    }
    @Test fun portraitOnlyFiltersLandscapeCompanions() {
        val r=PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.ALWAYS_TWO,1800,1000,meta(1,PhotoOrientation.PORTRAIT),
            listOf(meta(2,PhotoOrientation.LANDSCAPE),meta(3,PhotoOrientation.PORTRAIT)),2,0L,
            orientationFilter=CollageOrientationFilter.PORTRAIT_ONLY,
        )!!
        assertEquals(listOf(1L,3L),r.photoIds)
    }
    @Test fun rowsPreferenceProducesEqualThreeRows() {
        val r=PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE,1000,1800,meta(1,PhotoOrientation.LANDSCAPE),
            listOf(meta(2,PhotoOrientation.LANDSCAPE),meta(3,PhotoOrientation.LANDSCAPE)),3,0L,
            orientationFilter=CollageOrientationFilter.LANDSCAPE_ONLY,
            layoutPreference=CollageLayoutPreference.ROWS,
        )!!
        assertEquals(CollageLayout.THREE_ROWS,r.layout); assertEquals(3,r.photoIds.size)
    }
    @Test fun columnsPreferenceProducesEqualColumns() {
        val r=PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE,1800,1000,meta(1,PhotoOrientation.PORTRAIT),
            listOf(meta(2,PhotoOrientation.PORTRAIT),meta(3,PhotoOrientation.PORTRAIT)),3,0L,
            layoutPreference=CollageLayoutPreference.COLUMNS,
        )!!
        assertEquals(CollageLayout.THREE_COLUMNS,r.layout)
    }
    @Test fun featuredPreferenceUsesAsymmetricLayout() {
        val r=PortraitCollagePolicy.chooseBestPresentation(
            PortraitCollageMode.PREFER_THREE,1800,1000,meta(1,PhotoOrientation.LANDSCAPE),
            listOf(meta(2,PhotoOrientation.LANDSCAPE),meta(3,PhotoOrientation.LANDSCAPE)),3,0L,
            layoutPreference=CollageLayoutPreference.FEATURED,
        )!!
        assertTrue(r.layout in setOf(CollageLayout.FULL_TOP_TWO_BOTTOM,CollageLayout.TWO_TOP_FULL_BOTTOM,CollageLayout.FULL_LEFT_TWO_RIGHT,CollageLayout.TWO_LEFT_FULL_RIGHT))
    }
}
