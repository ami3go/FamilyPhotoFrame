package com.example.familyphotoframe.domain.schedule

/**
 * Quiet-hours logic for the sleep schedule (spec §20 `schedule`, §9.1 `Sleeping`).
 *
 * Pure arithmetic on "minutes since midnight" so every awkward case — a window that
 * wraps past midnight, the exact boundary minutes, a malformed time string, a window
 * of zero length — is unit-testable without a device or a clock.
 *
 * The schedule is evaluated from the wall clock rather than tracked with a timer, so
 * it is inherently correct after a reboot, a process death, or a device that slept
 * through the transition (spec §22.4 "sleep schedule works and recovers correctly").
 */
object SleepSchedule {

    const val MINUTES_PER_DAY = 24 * 60

    /** Parse `"HH:mm"` into minutes since midnight, or null if malformed. */
    fun parseMinutes(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Render minutes since midnight back to `"HH:mm"`. */
    fun formatMinutes(minutes: Int): String {
        val n = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(n / 60, n % 60)
    }

    /**
     * True when [nowMinutes] falls inside the quiet window.
     *
     * The window is half-open: it includes [startMinutes] and excludes [endMinutes],
     * so a schedule can't be both asleep and awake on a boundary minute. A window
     * where start == end has zero length and never sleeps — treating it as "always
     * asleep" would strand the frame dark with no obvious way back.
     */
    fun isAsleep(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val now = wrap(nowMinutes)
        val start = wrap(startMinutes)
        val end = wrap(endMinutes)
        if (start == end) return false
        return if (start < end) {
            now >= start && now < end          // same-day window, e.g. 01:00-06:00
        } else {
            now >= start || now < end          // wraps midnight, e.g. 23:00-07:00
        }
    }

    /**
     * Minutes until the schedule next changes state, so the caller can sleep exactly
     * that long instead of polling. Always >= 1 so a caller cannot spin.
     */
    fun minutesUntilChange(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Int {
        val now = wrap(nowMinutes)
        val start = wrap(startMinutes)
        val end = wrap(endMinutes)
        if (start == end) return MINUTES_PER_DAY      // never changes
        val target = if (isAsleep(now, start, end)) end else start
        val delta = wrap(target - now)
        return if (delta == 0) MINUTES_PER_DAY else delta
    }

    /** Brightness to apply right now: night value inside the window, day value outside. */
    fun brightnessFor(
        asleep: Boolean,
        brightnessDay: Float,
        brightnessNight: Float,
    ): Float = (if (asleep) brightnessNight else brightnessDay).coerceIn(MIN_BRIGHTNESS, 1f)

    /**
     * Never fully black: a frame that goes to 0 looks broken and gives no feedback that
     * it is merely asleep, and some panels treat 0 as "use system brightness".
     */
    const val MIN_BRIGHTNESS = 0.01f

    private fun wrap(minutes: Int) = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
}
