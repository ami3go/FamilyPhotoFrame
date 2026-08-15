package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsCanonicalizerTest {
    @Test
    fun unsafePersistedBoundsAreCanonicalizedBeforeRuntimeUse() {
        val normalized = AppSettings(
            intervalSeconds = -100,
            transitionDurationMs = 99_999,
            temporarilySuppressAfterDecodeFailures = 0,
            portraitCollage = PortraitCollageSettings(maxPhotos = 99, cornerRadiusDp = -4),
        ).withCurrentDefaults()

        assertEquals(3, normalized.intervalSeconds)
        assertEquals(2_000, normalized.transitionDurationMs)
        assertEquals(1, normalized.temporarilySuppressAfterDecodeFailures)
        assertEquals(3, normalized.portraitCollage.maxPhotos)
        assertEquals(0, normalized.portraitCollage.cornerRadiusDp)
    }

    @Test
    fun malformedLegacySleepValuesFallBackToSafeClockAndBrightness() {
        val normalized = AppSettings(
            schedule = ScheduleSettings(
                sleepEnabled = true,
                sleepStart = "88:99",
                sleepEnd = "bad",
                brightnessDay = Float.NaN,
                brightnessNight = 8f,
            ),
        ).withCurrentDefaults()

        assertFalse(normalized.schedule.sleepEnabled)
        val day = normalized.brightnessAutomation.periods.single { it.id == "day" }
        val night = normalized.brightnessAutomation.periods.single { it.id == "night" }
        assertEquals("07:00", day.startTime)
        assertEquals(1f, day.brightness, 0.0001f)
        assertEquals("23:00", night.startTime)
        assertEquals(1f, night.brightness, 0.0001f)
    }
}
