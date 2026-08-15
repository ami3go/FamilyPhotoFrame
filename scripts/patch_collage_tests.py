#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def rd(p): return (R/p).read_text(encoding='utf-8')
def wr(p,s):
    q=R/p; q.parent.mkdir(parents=True,exist_ok=True); q.write_text(s,encoding='utf-8')
def rep(s,a,b,label):
    if s.count(a)!=1: raise RuntimeError(f'{label}: {s.count(a)} matches')
    return s.replace(a,b,1)

wr('app/src/test/java/com/example/familyphotoframe/domain/engine/CollageCustomizationPolicyTest.kt','''package com.example.familyphotoframe.domain.engine

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
''')

p='app/src/test/java/com/example/familyphotoframe/web/WebSettingsJsonTest.kt'; s=rd(p)
s=rep(s,'import com.example.familyphotoframe.data.settings.NightAction\n','import com.example.familyphotoframe.data.settings.NightAction\nimport com.example.familyphotoframe.data.settings.CollageAlignment\nimport com.example.familyphotoframe.data.settings.CollageBackground\nimport com.example.familyphotoframe.data.settings.CollageGap\nimport com.example.familyphotoframe.data.settings.CollageLayoutPreference\nimport com.example.familyphotoframe.data.settings.CollageOrientationFilter\nimport com.example.familyphotoframe.data.settings.CollageScaleMode\nimport com.example.familyphotoframe.data.settings.PortraitCollageSettings\n','imports')
insert='''
    @Test
    fun collageCustomizationIsProjectedForWebParity() {
        val settings=AppSettings(portraitCollage=PortraitCollageSettings(
            gap=CollageGap.LARGE,
            orientationFilter=CollageOrientationFilter.LANDSCAPE_ONLY,
            layoutPreference=CollageLayoutPreference.ROWS,
            scaleMode=CollageScaleMode.FIT,
            alignment=CollageAlignment.TOP,
            background=CollageBackground.BLACK,
            cornerRadiusDp=24,
        ))
        val json=WebSettingsJson.redactedConfig(settings,nextScheduleAction="none")
        assertEquals("LANDSCAPE_ONLY",json.getValue("portraitCollageOrientationFilter").jsonPrimitive.content)
        assertEquals("ROWS",json.getValue("portraitCollageLayoutPreference").jsonPrimitive.content)
        assertEquals("FIT",json.getValue("portraitCollageScaleMode").jsonPrimitive.content)
        assertEquals("TOP",json.getValue("portraitCollageAlignment").jsonPrimitive.content)
        assertEquals("BLACK",json.getValue("portraitCollageBackground").jsonPrimitive.content)
        assertEquals("24",json.getValue("portraitCollageCornerRadiusDp").jsonPrimitive.content)
        assertEquals("LARGE",json.getValue("portraitCollageGap").jsonPrimitive.content)
    }
'''
# Insert before class-closing brace (last occurrence).
pos=s.rfind('\n}')
if pos<0: raise RuntimeError('class close not found')
s=s[:pos]+insert+s[pos:]; wr(p,s)

p='app/src/test/java/com/example/familyphotoframe/web/WebUiContractTest.kt'; s=rd(p)
a='        assertTrue(js.contains("FOLDER_BALANCED_SHUFFLE"))\n'
b='''        assertTrue(js.contains("portraitCollageOrientationFilter"))
        assertTrue(js.contains("portraitCollageLayoutPreference"))
        assertTrue(js.contains("portraitCollageScaleMode"))
        assertTrue(js.contains("portraitCollageAlignment"))
        assertTrue(js.contains("portraitCollageBackground"))
        assertTrue(js.contains("portraitCollageCornerRadiusDp"))
        assertTrue(js.contains("portraitCollageAnimateThree"))
        assertTrue(js.contains("Fit whole photo"))
'''+a
s=rep(s,a,b,'web contract'); wr(p,s)
print('collage tests patch applied')
