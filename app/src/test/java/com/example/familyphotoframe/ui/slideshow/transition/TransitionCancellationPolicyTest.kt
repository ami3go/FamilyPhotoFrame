package com.example.familyphotoframe.ui.slideshow.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransitionCancellationPolicyTest {
    @Test fun completedTransitionCommitsTarget() {
        val decision = TransitionCancellationPolicy.decide(true, false, false)
        assertEquals(TransitionCancellationAction.COMMIT_TARGET, decision.action)
        assertNull(decision.reason)
    }

    @Test fun supersededTransitionKeepsOutgoingFrame() {
        val decision = TransitionCancellationPolicy.decide(false, false, false)
        assertEquals(TransitionCancellationAction.KEEP_OUTGOING, decision.action)
        assertEquals("superseded", decision.reason)
    }

    @Test fun cancelledDesiredTargetMaySnapOnPauseOrReconfiguration() {
        val paused = TransitionCancellationPolicy.decide(false, true, true)
        assertEquals(TransitionCancellationAction.COMMIT_TARGET, paused.action)
        assertEquals("paused", paused.reason)

        val reconfigured = TransitionCancellationPolicy.decide(false, true, false)
        assertEquals(TransitionCancellationAction.COMMIT_TARGET, reconfigured.action)
        assertEquals("reconfigured", reconfigured.reason)
    }
}
