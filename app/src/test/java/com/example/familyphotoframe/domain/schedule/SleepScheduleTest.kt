package com.example.familyphotoframe.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quiet-hours logic (spec §20, §22.4 "sleep schedule works and recovers correctly").
 * The awkward cases are the midnight wrap, the boundary minutes, and a zero-length
 * window — all of which strand a wall-mounted frame if they are wrong.
 */
class SleepScheduleTest {

    private fun t(hhmm: String) = SleepSchedule.parseMinutes(hhmm)!!

    // ---- parsing ----

    @Test fun parsesValidTimes() {
        assertEquals(0, t("00:00"))
        assertEquals(23 * 60 + 59, t("23:59"))
        assertEquals(7 * 60, t("07:00"))
        assertEquals(7 * 60, SleepSchedule.parseMinutes(" 7:00 "))
    }

    @Test fun rejectsMalformedTimes() {
        listOf("", "7", "24:00", "23:60", "-1:00", "aa:bb", "12:34:56", "12-34").forEach {
            assertNull("should reject '$it'", SleepSchedule.parseMinutes(it))
        }
    }

    @Test fun formatRoundTrips() {
        listOf("00:00", "07:05", "23:59").forEach {
            assertEquals(it, SleepSchedule.formatMinutes(t(it)))
        }
    }

    // ---- same-day window ----

    @Test fun sameDayWindow() {
        val s = t("01:00"); val e = t("06:00")
        assertFalse(SleepSchedule.isAsleep(t("00:59"), s, e))
        assertTrue(SleepSchedule.isAsleep(t("01:00"), s, e))   // start is inclusive
        assertTrue(SleepSchedule.isAsleep(t("03:00"), s, e))
        assertFalse(SleepSchedule.isAsleep(t("06:00"), s, e))  // end is exclusive
        assertFalse(SleepSchedule.isAsleep(t("12:00"), s, e))
    }

    // ---- window that wraps midnight (the default) ----

    @Test fun wrappingWindowCoversBothSidesOfMidnight() {
        val s = t("23:00"); val e = t("07:00")
        assertFalse(SleepSchedule.isAsleep(t("22:59"), s, e))
        assertTrue(SleepSchedule.isAsleep(t("23:00"), s, e))
        assertTrue(SleepSchedule.isAsleep(t("23:59"), s, e))
        assertTrue(SleepSchedule.isAsleep(t("00:00"), s, e))
        assertTrue(SleepSchedule.isAsleep(t("06:59"), s, e))
        assertFalse(SleepSchedule.isAsleep(t("07:00"), s, e))
        assertFalse(SleepSchedule.isAsleep(t("15:00"), s, e))
    }

    // ---- degenerate window ----

    @Test fun zeroLengthWindowNeverSleeps() {
        // Treating start == end as "always asleep" would leave a frame dark with no
        // obvious way back, so it must mean "never".
        val s = t("12:00")
        listOf("00:00", "11:59", "12:00", "12:01", "23:59").forEach {
            assertFalse(SleepSchedule.isAsleep(t(it), s, s))
        }
        assertEquals(SleepSchedule.MINUTES_PER_DAY, SleepSchedule.minutesUntilChange(t("12:00"), s, s))
    }

    // ---- next transition ----

    @Test fun minutesUntilChangeWhenAwake() {
        val s = t("23:00"); val e = t("07:00")
        assertEquals(60, SleepSchedule.minutesUntilChange(t("22:00"), s, e))
        assertEquals(8 * 60, SleepSchedule.minutesUntilChange(t("15:00"), s, e))
    }

    @Test fun minutesUntilChangeWhenAsleep() {
        val s = t("23:00"); val e = t("07:00")
        assertEquals(8 * 60, SleepSchedule.minutesUntilChange(t("23:00"), s, e))
        assertEquals(7 * 60, SleepSchedule.minutesUntilChange(t("00:00"), s, e))
        assertEquals(1, SleepSchedule.minutesUntilChange(t("06:59"), s, e))
    }

    @Test fun minutesUntilChangeIsAlwaysPositive() {
        val s = t("23:00"); val e = t("07:00")
        for (m in 0 until SleepSchedule.MINUTES_PER_DAY) {
            assertTrue(SleepSchedule.minutesUntilChange(m, s, e) >= 1)
        }
    }

    /** Walking a whole day must produce exactly the configured number of asleep minutes. */
    @Test fun windowLengthMatchesConfiguredHours() {
        val s = t("23:00"); val e = t("07:00")
        val asleepMinutes = (0 until SleepSchedule.MINUTES_PER_DAY).count {
            SleepSchedule.isAsleep(it, s, e)
        }
        assertEquals(8 * 60, asleepMinutes)
    }

    /** State is derived from the clock, so any starting minute gives the same answer. */
    @Test fun evaluationIsStatelessAcrossRestart() {
        val s = t("23:00"); val e = t("07:00")
        val first = (0 until SleepSchedule.MINUTES_PER_DAY).map { SleepSchedule.isAsleep(it, s, e) }
        val second = (0 until SleepSchedule.MINUTES_PER_DAY).map { SleepSchedule.isAsleep(it, s, e) }
        assertEquals(first, second)
    }

    @Test fun outOfRangeMinutesWrapSafely() {
        val s = t("23:00"); val e = t("07:00")
        assertEquals(
            SleepSchedule.isAsleep(t("01:00"), s, e),
            SleepSchedule.isAsleep(t("01:00") + SleepSchedule.MINUTES_PER_DAY, s, e),
        )
        assertEquals(
            SleepSchedule.isAsleep(t("01:00"), s, e),
            SleepSchedule.isAsleep(t("01:00") - SleepSchedule.MINUTES_PER_DAY, s, e),
        )
    }

    // ---- brightness ----

    @Test fun brightnessFollowsState() {
        assertEquals(1.0f, SleepSchedule.brightnessFor(false, 1.0f, 0.3f), 0.0001f)
        assertEquals(0.3f, SleepSchedule.brightnessFor(true, 1.0f, 0.3f), 0.0001f)
    }

    @Test fun brightnessNeverFullyBlack() {
        // A frame at 0 looks broken and gives no hint that it is only asleep.
        assertEquals(SleepSchedule.MIN_BRIGHTNESS, SleepSchedule.brightnessFor(true, 1f, 0f), 0.0001f)
        assertTrue(SleepSchedule.brightnessFor(true, 1f, -5f) > 0f)
        assertEquals(1f, SleepSchedule.brightnessFor(false, 9f, 0.3f), 0.0001f)
    }
}
