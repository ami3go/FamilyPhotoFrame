package com.example.familyphotoframe.domain.power

/**
 * Bounded retry cadence for an unavailable root charge-control bridge.
 *
 * A failed read cannot safely prove whether charging is enabled. Retrying every five
 * minutes was both ineffective and expensive on Android 5.1, where each timed-out root
 * process can retain a native waiter. Back off quickly while retaining a faster recovery
 * cadence when the app may still own a disabled charging state.
 */
internal object BatteryProtectionRetryPolicy {
    const val FIRST_FAILURE_DELAY_MS = 15L * 60_000L
    const val SECOND_FAILURE_DELAY_MS = 60L * 60_000L
    const val OPEN_CIRCUIT_DELAY_MS = 6L * 60L * 60_000L
    const val OWNED_STATE_MAX_DELAY_MS = 60L * 60_000L
    const val DUPLICATE_EVALUATION_WINDOW_MS = 60_000L

    fun delayMs(consecutiveFailures: Int, appOwnsDisabledState: Boolean): Long {
        val base = when (consecutiveFailures.coerceAtLeast(1)) {
            1 -> FIRST_FAILURE_DELAY_MS
            2 -> SECOND_FAILURE_DELAY_MS
            else -> OPEN_CIRCUIT_DELAY_MS
        }
        return if (appOwnsDisabledState) base.coerceAtMost(OWNED_STATE_MAX_DELAY_MS) else base
    }
}
