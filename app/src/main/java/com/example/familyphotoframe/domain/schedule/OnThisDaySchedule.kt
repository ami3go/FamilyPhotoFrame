package com.example.familyphotoframe.domain.schedule

/**
 * Decides when a periodic "on this day" memory interlude is due (see
 * docs/FPF-FEAT-ON-THIS-DAY-001.md §0.2, §4.2).
 *
 * Splits the day into [timesPerDay] evenly spaced windows and applies the same
 * wall-clock due/grace arithmetic [RescanSchedule] uses for its single daily slot: a
 * device asleep at the exact trigger minute still catches up once instead of silently
 * skipping the window, and — symmetrically — a window that already fired does not fire
 * again before the next one comes due. Unlike [RescanSchedule], there is no day-of-week
 * filter; a memory interlude is meant to be a daily rhythm, not a scheduled chore.
 */
object OnThisDaySchedule {

    private const val MINUTES_PER_DAY = RescanSchedule.MINUTES_PER_DAY

    /**
     * The start minute (since local midnight) of each of [timesPerDay] evenly spaced
     * windows, offset by half a step so the first window isn't pinned to midnight.
     * E.g. `timesPerDay = 3` -> `[04:00, 12:00, 20:00]` (240, 720, 1200).
     */
    fun windowStartsMinutes(timesPerDay: Int): List<Int> {
        if (timesPerDay <= 0) return emptyList()
        val step = MINUTES_PER_DAY / timesPerDay
        return (0 until timesPerDay).map { it * step + step / 2 }
    }

    /**
     * Is an "on this day" interlude due now?
     *
     * @param nowMinutes minutes since local midnight
     * @param timesPerDay how many evenly spaced windows per day; `<= 0` means disabled
     * @param minutesSinceLastTrigger null if it has never fired
     * @param graceMinutes how late a missed window may still be picked up
     */
    fun isDue(
        nowMinutes: Int,
        timesPerDay: Int,
        minutesSinceLastTrigger: Long?,
        graceMinutes: Int = DEFAULT_GRACE_MINUTES,
    ): Boolean {
        if (timesPerDay <= 0) return false
        val now = wrap(nowMinutes)
        for (start in windowStartsMinutes(timesPerDay)) {
            val elapsed = now - start
            if (elapsed < 0 || elapsed > graceMinutes) continue
            if (minutesSinceLastTrigger == null) return true
            // Already fired inside this window; do not fire twice for one slot. Mirrors
            // RescanSchedule.isDue's elapsed-time comparison — no calendar-date state,
            // so a clock adjustment cannot desynchronize it.
            if (minutesSinceLastTrigger > elapsed + 1) return true
        }
        return false
    }

    private fun wrap(minutes: Int): Int =
        ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /**
     * Short grace: windows are frequent and brief (unlike the once-a-day rescan slot),
     * so a long grace risks bleeding into the next window when [timesPerDay] is large.
     */
    const val DEFAULT_GRACE_MINUTES = 20
}
