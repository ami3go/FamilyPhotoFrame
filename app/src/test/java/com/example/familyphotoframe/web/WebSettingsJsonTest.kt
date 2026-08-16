package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.BrightnessAutomationSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.BrightnessPeriod
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.DecodeColorDepth
import com.example.familyphotoframe.data.settings.DecodeResolution
import com.example.familyphotoframe.data.settings.PortraitCollageSettings
import com.example.familyphotoframe.data.settings.FilterSettings
import com.example.familyphotoframe.data.settings.PlaylistScheduleRule
import com.example.familyphotoframe.data.settings.PlaylistSettings
import com.example.familyphotoframe.data.settings.SlideshowPlaylist
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSettingsJsonTest {
    @Test
    fun primitiveCollectionsAreProjectedAsJsonElements() {
        val settings = AppSettings(
            selectedFolders = setOf("Trip B", "Trip A"),
            filters = FilterSettings(
                includeGlobs = listOf("*.jpg", "*.png"),
                excludeGlobs = listOf("Thumbs.db"),
                excludeFolders = listOf("@eaDir"),
            ),
            playlists = PlaylistSettings(
                playlists = listOf(
                    SlideshowPlaylist(id = "custom", name = "Custom"),
                ),
                activePlaylistId = "custom",
                defaultPlaylistId = "custom",
                scheduleRules = listOf(
                    PlaylistScheduleRule(
                        id = "weekday",
                        name = "Weekdays",
                        playlistId = "custom",
                        daysOfWeek = setOf(5, 1, 3),
                    ),
                ),
            ),
        )

        val json = WebSettingsJson.redactedConfig(settings, nextScheduleAction = "none")

        assertEquals(
            listOf("Trip A", "Trip B"),
            json.getValue("selectedFolders").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("*.jpg", "*.png"),
            json.getValue("includeGlobs").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("Thumbs.db"),
            json.getValue("excludeGlobs").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("@eaDir"),
            json.getValue("excludeFolders").jsonArray.map { it.jsonPrimitive.content },
        )
        val firstRule = json.getValue("playlistScheduleRules").jsonArray.first()
        assertEquals(
            listOf(1, 3, 5),
            firstRule.jsonObject.getValue("daysOfWeek").jsonArray.map { it.jsonPrimitive.content.toInt() },
        )
    }

    @Test
    fun legacyBrightnessAliasesReflectUnifiedAutomation() {
        val settings = AppSettings(
            brightnessAutomation = BrightnessAutomationSettings(
                mode = BrightnessMode.SCHEDULED,
                manualBrightness = 0.65f,
                periods = listOf(
                    BrightnessPeriod("day", "06:30", 0.65f, NightAction.DIM_ONLY),
                    BrightnessPeriod("night", "22:15", 0.10f, NightAction.PAUSE_SLIDESHOW),
                ),
            ),
        )

        val json = WebSettingsJson.redactedConfig(settings, nextScheduleAction = "none")

        assertEquals("true", json.getValue("sleepEnabled").jsonPrimitive.content)
        assertEquals("22:15", json.getValue("sleepStart").jsonPrimitive.content)
        assertEquals("06:30", json.getValue("sleepEnd").jsonPrimitive.content)
        assertEquals(0.65f, json.getValue("brightnessDay").jsonPrimitive.content.toFloat(), 0.0001f)
        assertEquals(0.10f, json.getValue("brightnessNight").jsonPrimitive.content.toFloat(), 0.0001f)
    }
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

    @Test
    fun lowMemoryOptionsAreProjectedForWebParity() {
        val defaults=WebSettingsJson.redactedConfig(AppSettings(),nextScheduleAction="none")
        assertEquals("AUTO",defaults.getValue("decodeColorDepth").jsonPrimitive.content)
        assertEquals("AUTO",defaults.getValue("decodeResolution").jsonPrimitive.content)
        assertEquals("true",defaults.getValue("cachePlaybackPool").jsonPrimitive.content)

        val tuned=WebSettingsJson.redactedConfig(
            AppSettings(
                decodeColorDepth=DecodeColorDepth.LOW_MEMORY,
                decodeResolution=DecodeResolution.REDUCED,
                cachePlaybackPool=false,
            ),
            nextScheduleAction="none",
        )
        assertEquals("LOW_MEMORY",tuned.getValue("decodeColorDepth").jsonPrimitive.content)
        assertEquals("REDUCED",tuned.getValue("decodeResolution").jsonPrimitive.content)
        assertEquals("false",tuned.getValue("cachePlaybackPool").jsonPrimitive.content)
    }

    /** The frame's Device page also carries the performance overlay; the web tab mirrors it. */
    @Test
    fun performanceOverlayIsProjectedForWebParity() {
        val defaults=WebSettingsJson.redactedConfig(AppSettings(),nextScheduleAction="none")
        assertEquals("false",defaults.getValue("showPerformanceOverlay").jsonPrimitive.content)

        val enabled=WebSettingsJson.redactedConfig(
            AppSettings(showPerformanceOverlay=true),
            nextScheduleAction="none",
        )
        assertEquals("true",enabled.getValue("showPerformanceOverlay").jsonPrimitive.content)
    }

}
