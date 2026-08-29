package com.example.familyphotoframe.domain.schedule

/**
 * Calculates when the playlist watcher must wake next.
 *
 * A timed override is a hard boundary just like a schedule rule. Waiting only for the
 * next ordinary rule boundary leaves an expired On This Day playlist active for up to
 * the watcher cadence. The one-second floor avoids a busy loop when the wall clock is
 * exactly at, or has just crossed, the expiry instant.
 */
object PlaylistOverrideWakePolicy {
    private const val MIN_DELAY_MS = 1_000L

    fun nextWakeDelayMs(
        nowEpochMs: Long,
        scheduleDelayMs: Long,
        overrideUntilEpochMs: Long,
    ): Long {
        val ordinaryDelay = scheduleDelayMs.coerceAtLeast(MIN_DELAY_MS)
        if (overrideUntilEpochMs == Long.MAX_VALUE || overrideUntilEpochMs <= nowEpochMs) {
            return ordinaryDelay
        }
        val overrideDelay = (overrideUntilEpochMs - nowEpochMs).coerceAtLeast(MIN_DELAY_MS)
        return minOf(ordinaryDelay, overrideDelay)
    }
}
