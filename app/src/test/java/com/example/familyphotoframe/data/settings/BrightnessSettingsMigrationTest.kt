package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrightnessSettingsMigrationTest {

    @Test
    fun legacySleepScheduleMigratesIntoBrightnessAutomation() {
        val migrated = AppSettings(
            schedule = ScheduleSettings(
                sleepEnabled = true,
                sleepStart = "22:30",
                sleepEnd = "06:45",
                brightnessDay = 0.72f,
                brightnessNight = 0.12f,
            ),
        ).withCurrentDefaults()

        assertEquals(1, migrated.brightnessAutomationMigrationVersion)
        assertFalse(migrated.schedule.sleepEnabled)
        assertEquals(BrightnessMode.SCHEDULED, migrated.brightnessAutomation.mode)
        assertEquals(0.72f, migrated.brightnessAutomation.manualBrightness, 0.0001f)

        val day = migrated.brightnessAutomation.periods.single { it.id == "day" }
        assertEquals("06:45", day.startTime)
        assertEquals(0.72f, day.brightness, 0.0001f)
        assertEquals(NightAction.DIM_ONLY, day.action)

        val night = migrated.brightnessAutomation.periods.single { it.id == "night" }
        assertEquals("22:30", night.startTime)
        assertEquals(0.12f, night.brightness, 0.0001f)
        assertEquals(NightAction.PAUSE_SLIDESHOW, night.action)
    }

    @Test
    fun configuredBrightnessAutomationWinsOverLegacySleep() {
        val configured = BrightnessAutomationSettings(
            mode = BrightnessMode.SCHEDULED,
            manualBrightness = 0.55f,
            periods = listOf(
                BrightnessPeriod("day", "08:15", 0.55f, NightAction.DIM_ONLY),
                BrightnessPeriod("night", "21:45", 0.05f, NightAction.BLACK_SCREEN),
            ),
        )

        val migrated = AppSettings(
            schedule = ScheduleSettings(
                sleepEnabled = true,
                sleepStart = "23:00",
                sleepEnd = "07:00",
                brightnessNight = 0.30f,
            ),
            brightnessAutomation = configured,
        ).withCurrentDefaults()

        assertFalse(migrated.schedule.sleepEnabled)
        assertEquals(configured.normalized(), migrated.brightnessAutomation)
    }

    @Test
    fun migrationIsIdempotent() {
        val once = AppSettings(
            schedule = ScheduleSettings(sleepEnabled = true),
        ).withCurrentDefaults()

        assertEquals(once, once.withCurrentDefaults())
    }
}
