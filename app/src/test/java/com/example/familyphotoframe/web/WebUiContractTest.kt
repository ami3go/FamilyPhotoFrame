package com.example.familyphotoframe.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiContractTest {
    @Test fun shellContainsAllTopLevelTabsAndExternalAssets() {
        val html = SetupPage.HTML
        listOf("overview", "photos", "playback", "collage", "display", "schedule", "device", "diagnostics", "backup", "about")
            .forEach { assertTrue("missing $it tab", html.contains("id=\"tab-$it\"")) }
        // Assets are cache-busted, so the shell references the revisioned URLs.
        assertTrue(html.contains("/assets/app-${WebUiAssets.REVISION}.css"))
        assertTrue(html.contains("/assets/app-${WebUiAssets.REVISION}.js"))
        assertFalse(html.contains("<script>"))
    }

    @Test fun assetsContainResponsiveAccessibleAndLowOverheadContracts() {
        val css = WebUiAssets.CSS
        val js = WebUiAssets.JS
        assertTrue(css.contains("max-width:899px"))
        assertTrue(css.contains("max-width:599px"))
        assertTrue(css.contains(":focus-visible"))
        assertTrue(css.contains("prefers-reduced-motion"))
        assertTrue(css.contains(".settings-row>*,.setting-control>*{min-width:0;max-width:100%}"))
        assertTrue(css.contains(".display-layout{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))"))
        assertTrue(css.contains(".display-layout{grid-template-columns:1fr}"))
        assertTrue(css.contains(".display-layout .brightness-period-row{grid-template-columns:1fr"))
        assertTrue(css.contains(".brightness-period-control{display:grid;grid-template-columns:"))
        assertTrue(js.contains("<div class=\"display-layout\"><div class=\"stack\">"))
        assertTrue(js.contains("brightness-period-control"))
        assertFalse(js.contains("card('Quiet hours'"))
        assertFalse(js.contains("row('Day brightness'"))
        assertFalse(js.contains("row('Night brightness'"))
        assertTrue(js.contains("URL.createObjectURL(blob)"))
        assertTrue(js.contains("document.hidden"))
        assertTrue(js.contains("/api/v1/presentation/current"))
        assertTrue(js.contains("/api/v1/diagnostics/events"))
        assertTrue(js.contains("portraitCollageOrientationFilter"))
        assertTrue(js.contains("portraitCollageLayoutPreference"))
        assertTrue(js.contains("portraitCollageScaleMode"))
        assertTrue(js.contains("portraitCollageAlignment"))
        assertTrue(js.contains("portraitCollageBackground"))
        assertTrue(js.contains("portraitCollageCornerRadiusDp"))
        assertTrue(js.contains("portraitCollageAnimateThree"))
        assertTrue(js.contains("Fit whole photo"))
        assertTrue(js.contains("function renderCollage()"))
        assertTrue(js.contains("data-tab-jump=\"collage\""))
        assertTrue(js.contains("reset-collage"))
        assertTrue(js.contains("Collage-specific controls have their own page"))
        // Low-memory options, reachable from a browser as well as the frame.
        assertTrue(js.contains("decodeColorDepth"))
        assertTrue(js.contains("cachePlaybackPool"))
        assertTrue(js.contains("device-memory-card"))
        // Source indicator: which source is set up, which one is playing, and the
        // switches that promote or merge one without retyping a connection.
        assertTrue(js.contains("function sourceStatusCard()"))
        assertTrue(js.contains("state.status.sources"))
        assertTrue(js.contains("data-source-primary"))
        assertTrue(js.contains("data-source-also"))
        assertTrue(js.contains("sourceStatusHost"))
        assertTrue(js.contains("FOLDER_BALANCED_SHUFFLE"))
        assertTrue(js.contains("reset_shuffle_active"))
        assertTrue(js.contains("shuffleFolderResolved"))
        assertFalse(js.contains("credentialRef"))
        assertFalse(js.contains("smbPassword"))
    }
}
