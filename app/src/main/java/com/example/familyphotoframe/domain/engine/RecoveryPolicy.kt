package com.example.familyphotoframe.domain.engine

/**
 * Backoff and state-transition policy for the remote-source recovery loop (spec §9.5).
 *
 * Extracted from `SlideshowViewModel.startSourceRecovery` so the decisions can be
 * exercised without Android or a real NAS. The policy and its integration boundary
 * through [SourceRecoveryCoordinator] both have executable regression tests.
 *
 * The loop keeps ownership of I/O and coroutines; this owns only arithmetic.
 */
object RecoveryPolicy {

    /** FPF-FEAT-SHUFFLE-002 v1.1 source-level waits: 30s, 2m, 5m, 15m maximum. */
    val BACKOFF_SECONDS: LongArray = longArrayOf(30, 120, 300, 900)

    /** Steady poll once healthy — frequent enough to notice a drop, cheap enough to ignore. */
    const val HEALTHY_POLL_MS: Long = 10L * 60_000L

    /** Upper bound on the random jitter added to every wait. */
    const val MAX_JITTER_MS: Long = 2_000

    /** What the loop should do after one health check. */
    enum class Action {
        /** Source came back: un-suppress, reindex, promote to primary. */
        PROMOTE,
        /** Source dropped: demote and play the configured unreachable content. */
        DEMOTE,
        /** No change in status; keep waiting. */
        NONE,
    }

    /** Immutable loop state, advanced by [next]. */
    data class State(val primaryActive: Boolean, val attempt: Int = 0)

    data class Step(val action: Action, val state: State, val waitMs: Long)

    /**
     * Decide the next action and wait from the current [state] and a health result.
     *
     * [jitterMs] is supplied by the caller rather than drawn here so the whole decision
     * stays deterministic and testable; the loop passes a random value. Jitter matters
     * because several frames on one LAN that all rebooted together would otherwise
     * retry a downed NAS in lockstep.
     */
    fun next(state: State, healthy: Boolean, jitterMs: Long = 0): Step {
        val action = when {
            healthy && !state.primaryActive -> Action.PROMOTE
            !healthy && state.primaryActive -> Action.DEMOTE
            else -> Action.NONE
        }
        // A status *change* resets the backoff: a source that just dropped should be
        // retried promptly, not at whatever long delay the previous outage had reached.
        val attempt = when (action) {
            Action.PROMOTE, Action.DEMOTE -> 0
            Action.NONE -> if (healthy) 0 else state.attempt + 1
        }
        val nextState = State(primaryActive = healthy, attempt = attempt)
        return Step(action, nextState, waitMs(healthy, attempt) + jitterMs.coerceIn(0, MAX_JITTER_MS))
    }

    /** Wait before the next health check, excluding jitter. */
    fun waitMs(healthy: Boolean, attempt: Int): Long =
        if (healthy) HEALTHY_POLL_MS
        else BACKOFF_SECONDS[attempt.coerceIn(0, BACKOFF_SECONDS.size - 1)] * 1000L
}
