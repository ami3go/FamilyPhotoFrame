package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.NightAction
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyBrightnessWebPatchTest {
    @Test
    fun oldSleepAliasesTranslateIntoUnifiedBrightnessOnly() {
        val patch = buildJsonObject {
            put("sleepEnabled", true)
            put("sleepStart", "22:10")
            put("sleepEnd", "06:40")
            put("brightnessDay", 0.70)
            put("brightnessNight", 0.12)
        }
        assertNull(LegacyBrightnessWebPatch.validationError(patch))

        val updated = LegacyBrightnessWebPatch.apply(AppSettings(), patch)
        assertEquals(BrightnessMode.SCHEDULED, updated.brightnessAutomation.mode)
        assertEquals(0.70f, updated.brightnessAutomation.manualBrightness, 0.0001f)
        val day = updated.brightnessAutomation.periods.single { it.id == "day" }
        val night = updated.brightnessAutomation.periods.single { it.id == "night" }
        assertEquals("06:40", day.startTime)
        assertEquals("22:10", night.startTime)
        assertEquals(NightAction.PAUSE_SLIDESHOW, night.action)
        // The compatibility adapter never re-enables the retired Schedule runtime flag.
        assertEquals(false, updated.schedule.sleepEnabled)
    }

    @Test
    fun malformedLegacyClockIsRejectedBeforeWrite() {
        val patch = buildJsonObject { put("sleepStart", "25:61") }
        assertEquals("sleepStart must be HH:mm", LegacyBrightnessWebPatch.validationError(patch))
    }
}
