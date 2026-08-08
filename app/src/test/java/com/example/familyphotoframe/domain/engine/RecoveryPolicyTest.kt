package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Closes the "recovery loop has no automated test" gap for the decision logic. */
class RecoveryPolicyTest {

    @Test fun backoffEscalatesWhileDownAndCaps() {
        var state = RecoveryPolicy.State(primaryActive = true)
        val waits = mutableListOf<Long>()
        repeat(8) {
            val step = RecoveryPolicy.next(state, healthy = false)
            waits += step.waitMs
            state = step.state
        }
        assertEquals(listOf(30_000L, 120_000L, 300_000L, 900_000L, 900_000L, 900_000L), waits.take(6))
        assertEquals("caps at the longest wait", 900_000L, waits.last())
    }

    @Test fun promotesOnRecoveryAndResetsBackoff() {
        val down = RecoveryPolicy.State(primaryActive = false, attempt = 5)
        val step = RecoveryPolicy.next(down, healthy = true)
        assertEquals(RecoveryPolicy.Action.PROMOTE, step.action)
        assertEquals(0, step.state.attempt)
        assertEquals(10L * 60_000L, step.waitMs)
    }

    /** A fresh outage must retry promptly, not inherit the previous outage's long wait. */
    @Test fun secondOutageRestartsTheBackoff() {
        val healthy = RecoveryPolicy.State(primaryActive = true, attempt = 0)
        val step = RecoveryPolicy.next(healthy, healthy = false)
        assertEquals(RecoveryPolicy.Action.DEMOTE, step.action)
        assertEquals(30_000L, step.waitMs)
    }

    @Test fun jitterIsClampedAndNeverNegative() {
        val s = RecoveryPolicy.State(primaryActive = true)
        assertEquals(30_000L + RecoveryPolicy.MAX_JITTER_MS,
            RecoveryPolicy.next(s, healthy = false, jitterMs = 99_999).waitMs)
        assertEquals(30_000L, RecoveryPolicy.next(s, healthy = false, jitterMs = -10).waitMs)
    }
}
