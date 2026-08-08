package com.example.familyphotoframe.domain.schedule

/**
 * Decides when the photo index should be refreshed automatically (spec §20 `schedule`).
 *
 * A frame is normally left alone for weeks; photos added to the NAS meanwhile only appear
 * after a rescan. Doing that on a fixed timer is the obvious approach and the wrong one
 * here: a rescan can hit a NAS hard, and the natural thing to ask for is "overnight, on
 * weekdays" rather than "every 37 hours".
 *
 * Like [SleepSchedule], this is evaluated from the wall clock and the last-run timestamp
 * rather than tracked by a running timer, so it is correct after a reboot, a process
 * death, or a device that was switched off through the scheduled moment — a
 * timer-based design would simply lose those.
 *
 * Pure arithmetic on minutes-since-midnight and ISO weekday numbers, so every awkward
 * case is unit-testable without a device or a clock.
 */
object RescanSchedule {

    const val MINUTES_PER_DAY = 24 * 60
    private const val MS_PER_MINUTE = 60_000L

    /** ISO-8601 weekday numbering, matching `java.time.DayOfWeek.getValue()`. */
    const val MONDAY = 1
    const val SUNDAY = 7

    /**
     * Is a scheduled rescan due now?
     *
     * @param nowMinutes minutes since local midnight
     * @param nowDayOfWeek ISO weekday, 1 = Monday
     * @param atMinutes the configured time of day
     * @param days ISO weekdays the rescan may run on; empty means never
     * @param minutesSinceLastRun null if it has never run
     * @param graceMinutes how late a missed run may still be picked up
     *
     * A grace window exists because the frame is not guaranteed to be awake, online, or
     * even powered at the exact minute. Without it, a device switched on an hour late
     * would skip the day entirely and the user would conclude the feature is broken.
     * The window is bounded rather than open-ended so that switching on days later does
     * not trigger a surprise rescan at an arbitrary moment.
     */
    fun isDue(
        nowMinutes: Int,
        nowDayOfWeek: Int,
        atMinutes: Int,
        days: Set<Int>,
        minutesSinceLastRun: Long?,
        graceMinutes: Int = DEFAULT_GRACE_MINUTES,
    ): Boolean {
        if (days.isEmpty()) return false
        if (nowDayOfWeek !in days) return false

        val now = wrap(nowMinutes)
        val at = wrap(atMinutes)
        if (now < at) return false
        if (now - at > graceMinutes) return false

        // Never run: the window is open, so take it.
        if (minutesSinceLastRun == null) return true

        // Already ran inside this window; do not run twice for one scheduled slot. The
        // comparison is against elapsed time rather than a stored calendar date so it
        // needs no timezone handling and cannot be confused by a clock adjustment.
        return minutesSinceLastRun > (now - at) + 1
    }

    /**
     * Minutes until the next scheduled run, for callers that want to sleep rather than
     * poll. Never returns 0, so a caller cannot spin; returns null when nothing is
     * scheduled.
     */
    fun minutesUntilNext(nowMinutes: Int, nowDayOfWeek: Int, atMinutes: Int, days: Set<Int>): Int? {
        if (days.isEmpty()) return null
        val now = wrap(nowMinutes)
        val at = wrap(atMinutes)
        for (offset in 0..7) {
            val day = ((nowDayOfWeek - 1 + offset) % 7) + 1
            if (day !in days) continue
            val minutes = offset * MINUTES_PER_DAY + at - now
            if (minutes > 0) return minutes
        }
        // Every candidate landed in the past, which can only happen when today is the
        // only scheduled day and its time has passed: the next one is a week out.
        return 7 * MINUTES_PER_DAY + at - now
    }

    /** Convenience for callers holding epoch milliseconds. */
    fun minutesSince(lastRunEpochMs: Long?, nowEpochMs: Long): Long? {
        if (lastRunEpochMs == null || lastRunEpochMs <= 0L) return null
        if (nowEpochMs < lastRunEpochMs) return null   // clock moved back; treat as unknown
        return (nowEpochMs - lastRunEpochMs) / MS_PER_MINUTE
    }

    /** Parse `"1,3,5"` into ISO weekday numbers, ignoring anything out of range. */
    fun parseDays(csv: String): Set<Int> =
        csv.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in MONDAY..SUNDAY }
            .toSet()

    fun formatDays(days: Set<Int>): String = days.sorted().joinToString(",")

    /** All seven days, the sensible default once the feature is switched on. */
    fun everyDay(): Set<Int> = (MONDAY..SUNDAY).toSet()

    fun weekdaysOnly(): Set<Int> = (MONDAY..5).toSet()

    private fun wrap(minutes: Int): Int =
        ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /**
     * One hour. Long enough to survive a late start or a sleeping device, short enough
     * that a rescan still happens near the time the user actually chose.
     */
    const val DEFAULT_GRACE_MINUTES = 60
}
