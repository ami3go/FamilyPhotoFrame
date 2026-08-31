package com.example.familyphotoframe.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiContractTest {
    @Test fun shellContainsAllTopLevelTabsAndExternalAssets() {
        val html = SetupPage.HTML
        listOf(
            "overview", "photos", "playback", "collage", "display", "schedule",
            "device", "web", "diagnostics", "backup", "about",
        ).forEach { assertTrue("missing $it tab", html.contains("id=\"tab-$it\"")) }
        // Assets are cache-busted, so the shell references the revisioned URLs.
        assertTrue(html.contains("/assets/app-${WebUiAssets.REVISION}.css"))
        assertTrue(html.contains("/assets/app-${WebUiAssets.REVISION}.js"))
        assertFalse(html.contains("<script>"))
    }

    /**
     * The asset URLs are served `immutable` for a year, so a revision that does not move
     * with the content pins every browser that already cached the bundle to the old one.
     * Deriving it from the bytes is what makes that impossible; pin the derivation so it
     * cannot quietly become a literal again.
     */
    @Test fun assetRevisionIsDerivedFromAssetContent() {
        val expected = "v" + Integer.toHexString(31 * WebUiCss.VALUE.hashCode() + WebUiScript.VALUE.hashCode())
        assertEquals(expected, WebUiAssets.REVISION)
        assertEquals(WebUiAssets.REVISION, WebUiAssets.REVISION)
        assertTrue(WebUiAssets.REVISION.startsWith("v"))
        assertTrue(WebUiAssets.REVISION.length > 1)
        assertTrue(WebUiAssets.REVISION.all { it == 'v' || it.isDigit() || it in 'a'..'f' })
    }

    /**
     * Every class the stylesheet targets must still be produced by the client or the shell.
     *
     * A rule whose selector no longer matches anything is invisible: it costs bytes, and it
     * reads to the next person as though the layout it describes is still in use. Two of the
     * defects in this file's history were exactly that kind of leftover — a render function
     * shadowed by a later reassignment, and column wrappers kept after the columns went — so
     * the orphans are worth failing on rather than leaving to be noticed.
     */
    @Test fun everyStyledClassIsStillRendered() {
        val css = WebUiAssets.CSS
        val markup = WebUiAssets.JS + SetupPage.HTML
        val orphans = Regex("\\.([a-zA-Z][\\w-]+)").findAll(css)
            .map { it.groupValues[1] }
            .distinct()
            .filterNot { markup.contains(it) }
            .toList()
        assertTrue("stylesheet targets classes nothing renders: $orphans", orphans.isEmpty())
    }

    @Test fun assetsContainResponsiveAccessibleAndLowOverheadContracts() {
        val css = WebUiAssets.CSS
        val js = WebUiAssets.JS
        assertTrue(css.contains("max-width:899px"))
        assertTrue(css.contains("max-width:599px"))
        assertTrue(css.contains(":focus-visible"))
        assertTrue(css.contains("prefers-reduced-motion"))
        assertTrue(css.contains(".settings-row>*,.setting-control>*{min-width:0;max-width:100%}"))
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
        assertTrue(js.contains("Get picture"))
        assertTrue(js.contains("rawFetch('/api/v1/preview',{method:'POST'})"))
        assertTrue(js.contains("previewLoading"))
        assertFalse(js.contains("previewTimer"))
        assertFalse(js.contains("setInterval(function(){if(!document.hidden)loadPresentation()"))
        assertFalse(js.contains("function applyPreview()"))
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
        assertTrue(js.contains("decodeResolution"))
        assertTrue(js.contains("device-memory-card"))
        // Device tab mirrors the frame's own Device page: Startup, Performance, Memory.
        // Web-server administration moved to its own tab so the device options are not
        // buried under a page titled "Web control".
        assertTrue(js.contains("function renderDevice()"))
        assertTrue(js.contains("function renderWebControl()"))
        assertTrue(js.contains("renderWebControl()"))
        assertTrue(js.contains("['device','Device'"))
        assertTrue(js.contains("['web','Web control'"))
        assertTrue(js.contains("pageTitle('Device'"))
        assertTrue(js.contains("pageTitle('Web control'"))
        assertTrue(js.contains("device-startup-card"))
        assertTrue(js.contains("device-performance-card"))
        // Settings read as one column of hairline-separated groups rather than boxed cards.
        // Side-by-side groups only worked because each sat on its own surface; with the
        // surface gone they read as a broken table, so every layout wrapper is a block.
        assertTrue(css.contains(".card{background:transparent;border:0"))
        assertTrue(css.contains("box-shadow:none"))
        assertTrue(css.contains(".card-grid{display:block}"))
        assertFalse(js.contains("card-column"))
        assertFalse(css.contains("card-column"))
        assertTrue(css.contains(".stack{display:block"))
        assertTrue(css.contains(".display-layout{display:block}"))
        assertTrue(css.contains(".photos-grid{display:block}"))
        // A capped measure keeps a label and its control readable as a pair.
        assertTrue(css.contains(".tab-page{max-width:940px"))
        // One rule closes every row. :first-of-type/:last-of-type silently miss whenever a
        // non-row element sits at either end of a group, which is how a button row once
        // left its group open, so neither may come back for the separators.
        assertTrue(css.contains(".settings-row{display:grid"))
        assertTrue(css.contains("border-top:0;border-bottom:1px solid var(--border)"))
        assertFalse(css.contains(".settings-row:first-of-type"))
        assertFalse(css.contains(".settings-row:last-of-type"))
        // Section rhythm comes from the section's own margin; a wrapper gap used to add to
        // it, so spacing depended on whether two groups happened to share a parent.
        assertTrue(css.contains(".card{background:transparent;border:0;border-radius:0;padding:0;box-shadow:none;min-width:0;overflow-wrap:anywhere;display:block;margin:0 0 34px}"))
        assertFalse(css.contains(".stack{display:grid;gap:16px"))
        // A real switch, styled from the checkbox so no markup depends on it.
        assertTrue(css.contains(".switch-line input{appearance:none"))
        assertTrue(css.contains(".switch-line input:checked{background:var(--accent)"))
        // The switch must not be accompanied by a rendered On/Off word. savePatch updates
        // state without re-rendering, so that text stayed at its initial value and read the
        // opposite of the switch on every tab status polling does not rebuild.
        assertFalse(js.contains("(value?'On':'Off')"))
        assertTrue(js.contains("function toggleControl(id,value,setting){return '<label class=\"switch-line\">"))
        assertTrue(js.contains("photos-local-cache-card"))
        assertTrue(js.contains("showPerformanceOverlay"))
        assertTrue(js.contains("capture_performance_sample"))
        assertTrue(js.contains("memoryTier"))
        // Every tab built from status must be re-rendered when status arrives. The web tab
        // was omitted, so Identity kept its pre-status render and showed a column of
        // dashes for the whole session.
        assertTrue(
            js.contains("renderOverview();renderDevice();renderWebControl();renderAbout();renderDiagnosticDeviceGrid()")
        )
        // Policy buttons wrap instead of overflowing the card they sit in.
        assertTrue(css.contains(".remember-policy-actions{flex-wrap:wrap}"))
        assertFalse(css.contains(".remember-policy-actions{flex-wrap:nowrap}"))
        // autoStartOnBoot has exactly one control, on Device, so the two pages cannot disagree.
        assertTrue(js.contains("toggleControl('autoStartOnBoot'"))
        assertEquals(1, Regex("toggleControl\\('autoStartOnBoot'").findAll(js).count())
        // Web-only cards must not remain on the Device tab.
        assertFalse(js.contains("el('tab-device').innerHTML=pageTitle('Web control'"))
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

    @Test fun folderBrowserUsesBoundedServerPagesWithoutLosingOffPageSelection() {
        val js = WebUiAssets.JS
        assertTrue(js.contains("/api/v1/folders?offset="))
        assertTrue(js.contains("&limit=100&q="))
        assertTrue(js.contains("set_selected_batch"))
        assertTrue(js.contains("Deselect page"))
        assertFalse(js.contains("document.querySelectorAll('.folder-row').forEach"))
    }
}
