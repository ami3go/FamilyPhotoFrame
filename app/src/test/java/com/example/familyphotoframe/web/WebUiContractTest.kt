package com.example.familyphotoframe.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiContractTest {
    @Test fun shellContainsAllTopLevelTabsAndExternalAssets() {
        val html = SetupPage.HTML
        listOf("overview", "photos", "playback", "display", "schedule", "device", "diagnostics", "backup", "about")
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
        assertTrue(js.contains("URL.createObjectURL(blob)"))
        assertTrue(js.contains("document.hidden"))
        assertTrue(js.contains("/api/v1/presentation/current"))
        assertTrue(js.contains("/api/v1/diagnostics/events"))
        assertTrue(js.contains("FOLDER_BALANCED_SHUFFLE"))
        assertTrue(js.contains("reset_shuffle_active"))
        assertTrue(js.contains("shuffleFolderResolved"))
        assertFalse(js.contains("credentialRef"))
        assertFalse(js.contains("smbPassword"))
    }
}
