package com.example.familyphotoframe.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wall-clock due/grace timing for the periodic "on this day" interlude
 * (docs/FPF-FEAT-ON-THIS-DAY-001.md §0.2, §4.2). The awkward cases mirror
 * [RescanSchedule]'s: the sleeping-device catch-up, not firing twice for one window,
 * and — new here — multiple windows a day instead of one.
 */
class OnThisDayScheduleTest {

    // ---- windowStartsMinutes ----

    @Test fun disabledHasNoWindows() {
        assertEquals(emptyList<Int>(), OnThisDaySchedule.windowStartsMinutes(0))
        assertEquals(emptyList<Int>(), OnThisDaySchedule.windowStartsMinutes(-1))
    }

    @Test fun evenlySpacedWindows() {
        assertEquals(listOf(720), OnThisDaySchedule.windowStartsMinutes(1))
        assertEquals(listOf(240, 720, 1200), OnThisDaySchedule.windowStartsMinutes(3))
        assertEquals(listOf(180, 540, 900, 1260), OnThisDaySchedule.windowStartsMinutes(4))
    }

    // ---- isDue: disabled ----

    @Test fun disabledNeverDue() {
        assertFalse(OnThisDaySchedule.isDue(nowMinutes = 240, timesPerDay = 0, minutesSinceLastTrigger = null))
    }

    // ---- isDue: first-ever fire ----

    @Test fun firstFireInsideWindow() {
        // 3x/day -> windows at 240, 720, 1200. Never fired before.
        assertTrue(OnThisDaySchedule.isDue(nowMinutes = 240, timesPerDay = 3, minutesSinceLastTrigger = null))
        assertTrue(OnThisDaySchedule.isDue(nowMinutes = 255, timesPerDay = 3, minutesSinceLastTrigger = null)) // 15 min into grace
    }

    @Test fun notYetDueBeforeWindow() {
        assertFalse(OnThisDaySchedule.isDue(nowMinutes = 239, timesPerDay = 3, minutesSinceLastTrigger = null))
    }

    @Test fun missedPastGrace() {
        // Grace is 20 minutes by default; 21 minutes late is missed until the next window.
        assertFalse(OnThisDaySchedule.isDue(nowMinutes = 261, timesPerDay = 3, minutesSinceLastTrigger = null))
    }

    // ---- isDue: sleeping-device catch-up ----

    @Test fun catchesUpWithinGraceAfterSleep() {
        // Device wakes 10 minutes into the window's grace, never fired before.
        assertTrue(OnThisDaySchedule.isDue(nowMinutes = 250, timesPerDay = 3, minutesSinceLastTrigger = null))
    }

    // ---- isDue: already fired this window ----

    @Test fun doesNotFireTwiceInsideOneWindow() {
        // Fired 5 minutes ago; window started 10 minutes ago (elapsed = 10).
        assertFalse(
            OnThisDaySchedule.isDue(nowMinutes = 250, timesPerDay = 3, minutesSinceLastTrigger = 5L),
        )
    }

    @Test fun firesAgainOnceEnoughTimeHasPassedSinceLastFire() {
        // Last fire predates this window's start (elapsed = 10, last fire 400 min ago).
        assertTrue(
            OnThisDaySchedule.isDue(nowMinutes = 250, timesPerDay = 3, minutesSinceLastTrigger = 400L),
        )
    }

    // ---- isDue: multiple windows across a day ----

    @Test fun firesOnceForEachWindowAcrossTheDay() {
        val timesPerDay = 3 // windows: 240, 720, 1200
        val firedAtMinute = 245L // window 1: never fired before, fires at minute 245

        // Window 1 due on first check.
        assertTrue(OnThisDaySchedule.isDue(nowMinutes = 245, timesPerDay, minutesSinceLastTrigger = null))

        // Immediately after firing (5 minutes later), not due again inside the same window.
        assertFalse(
            OnThisDaySchedule.isDue(nowMinutes = 250, timesPerDay, minutesSinceLastTrigger = 250L - firedAtMinute),
        )

        // Window 2 due, long after window 1 fired.
        assertTrue(
            OnThisDaySchedule.isDue(nowMinutes = 725, timesPerDay, minutesSinceLastTrigger = 725L - firedAtMinute),
        )
    }

    @Test fun betweenWindowsIsNotDue() {
        // Halfway between window 1 (240) and window 2 (720), well past window 1's grace.
        assertFalse(OnThisDaySchedule.isDue(nowMinutes = 480, timesPerDay = 3, minutesSinceLastTrigger = null))
    }

    // ---- midnight wrap ----

    @Test fun wrapsNegativeAndOverflowMinutes() {
        // -5 minutes wraps to 1435 (23:55); should behave the same as 1435 directly.
        assertEquals(
            OnThisDaySchedule.isDue(nowMinutes = 1435, timesPerDay = 1, minutesSinceLastTrigger = null),
            OnThisDaySchedule.isDue(nowMinutes = -5, timesPerDay = 1, minutesSinceLastTrigger = null),
        )
    }
}
