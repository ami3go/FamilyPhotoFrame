package com.example.familyphotoframe.domain.schedule

import com.example.familyphotoframe.data.settings.BrightnessAutomationSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.BrightnessPeriod
import com.example.familyphotoframe.data.settings.NightAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrightnessPolicyTest {
    private val scheduled = BrightnessAutomationSettings(
        mode = BrightnessMode.SCHEDULED,
        manualBrightness = 0.65f,
        periods = listOf(
            BrightnessPeriod("day", "07:00", 1.0f, NightAction.DIM_ONLY),
            BrightnessPeriod("night", "23:00", 0.08f, NightAction.BLACK_SCREEN),
        ),
    )

    @Test fun scheduleChangesExactlyAtPeriodBoundary() {
        val before = BrightnessPolicy.decide(scheduled, nowEpochMs = 1L, nowMinutes = 22 * 60 + 59)
        val night = BrightnessPolicy.decide(scheduled, nowEpochMs = 1L, nowMinutes = 23 * 60)
        val afterMidnight = BrightnessPolicy.decide(scheduled, nowEpochMs = 1L, nowMinutes = 60)

        assertEquals("day", before.periodId)
        assertEquals(1.0f, before.brightness, 0.0001f)
        assertEquals("night", night.periodId)
        assertEquals(0.08f, night.brightness, 0.0001f)
        assertEquals(NightAction.BLACK_SCREEN, night.action)
        assertEquals("night", afterMidnight.periodId)
    }

    @Test fun allInvalidTimesFallBackWithoutCrashingWatcher() {
        val invalid = scheduled.copy(
            periods = listOf(
                BrightnessPeriod("broken-a", "bad", 0.2f, NightAction.BLACK_SCREEN),
                BrightnessPeriod("broken-b", "25:99", 0.1f, NightAction.PAUSE_SLIDESHOW),
            ),
        )

        val decision = BrightnessPolicy.decide(invalid, nowEpochMs = 1L, nowMinutes = 12 * 60)

        assertEquals(0.65f, decision.brightness, 0.0001f)
        assertEquals(NightAction.DIM_ONLY, decision.action)
        assertNull(decision.periodId)
    }

    @Test fun malformedPeriodDoesNotHideValidPeriod() {
        val mixed = scheduled.copy(
            periods = listOf(
                BrightnessPeriod("broken", "xx:yy", 0.1f),
                BrightnessPeriod("day", "07:00", 0.75f),
            ),
        )

        val decision = BrightnessPolicy.decide(mixed, nowEpochMs = 1L, nowMinutes = 12 * 60)
        assertEquals("day", decision.periodId)
        assertEquals(0.75f, decision.brightness, 0.0001f)
    }
}
