package com.example.familyphotoframe.domain.engine

/**
 * Coordinates one remote source's recovery policy with the *actual* playback pool.
 *
 * A playback read can fail while the periodic health loop is sleeping or awaiting I/O.
 * Keeping the loop's old `primaryActive` bit in a local variable lets that failure and
 * the real pool drift apart. This coordinator makes every check start from the pool's
 * current state and rejects a health result that was overtaken by a newer read failure.
 */
class SourceRecoveryCoordinator(initiallyPrimary: Boolean) {

    data class Check internal constructor(
        internal val failureGeneration: Long,
        internal val state: RecoveryPolicy.State,
    )

    data class Decision(
        val step: RecoveryPolicy.Step,
        /** True when a playback failure happened after this health check started. */
        val superseded: Boolean,
    )

    private var failureGeneration: Long = 0L
    private var state = RecoveryPolicy.State(primaryActive = initiallyPrimary)

    /** Begin a check from the source's authoritative membership in the playback pool. */
    @Synchronized
    fun beginCheck(actualPrimaryActive: Boolean): Check {
        state = state.copy(primaryActive = actualPrimaryActive)
        return Check(failureGeneration, state)
    }

    /**
     * Apply a health result only if no newer playback read failure superseded it.
     * A superseded result stays demoted and retries on the first recovery interval.
     */
    @Synchronized
    fun decide(check: Check, healthy: Boolean, jitterMs: Long = 0L): Decision {
        if (check.failureGeneration != failureGeneration) {
            state = RecoveryPolicy.State(primaryActive = false, attempt = 0)
            return Decision(
                step = RecoveryPolicy.Step(
                    action = RecoveryPolicy.Action.NONE,
                    state = state,
                    waitMs = RecoveryPolicy.waitMs(healthy = false, attempt = 0) +
                        jitterMs.coerceIn(0L, RecoveryPolicy.MAX_JITTER_MS),
                ),
                superseded = true,
            )
        }
        val step = RecoveryPolicy.next(check.state, healthy, jitterMs)
        state = step.state
        return Decision(step, superseded = false)
    }

    /** Demote immediately and invalidate any health/index result already in flight. */
    @Synchronized
    fun markPlaybackUnavailable(): Boolean {
        val changed = state.primaryActive
        failureGeneration += 1L
        state = RecoveryPolicy.State(primaryActive = false, attempt = 0)
        return changed
    }

    /** Re-check after recovery indexing, which is itself a suspending operation. */
    @Synchronized
    fun promotionStillValid(check: Check): Boolean =
        check.failureGeneration == failureGeneration && state.primaryActive

    @Synchronized
    fun snapshot(): RecoveryPolicy.State = state
}
