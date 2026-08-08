package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRecoveryCoordinatorTest {

    @Test fun playbackFailureIsPromotedExactlyOnceByNextSuccessfulCheck() {
        val coordinator = SourceRecoveryCoordinator(initiallyPrimary = true)

        coordinator.markPlaybackUnavailable()
        val recovery = coordinator.beginCheck(actualPrimaryActive = false)
        val promoted = coordinator.decide(recovery, healthy = true)

        assertEquals(RecoveryPolicy.Action.PROMOTE, promoted.step.action)
        assertFalse(promoted.superseded)
        assertTrue(coordinator.promotionStillValid(recovery))

        val steady = coordinator.beginCheck(actualPrimaryActive = true)
        assertEquals(
            RecoveryPolicy.Action.NONE,
            coordinator.decide(steady, healthy = true).step.action,
        )
    }

    @Test fun newerPlaybackFailureInvalidatesInFlightHealthResult() {
        val coordinator = SourceRecoveryCoordinator(initiallyPrimary = true)
        val check = coordinator.beginCheck(actualPrimaryActive = true)

        coordinator.markPlaybackUnavailable()
        val decision = coordinator.decide(check, healthy = true)

        assertTrue(decision.superseded)
        assertEquals(RecoveryPolicy.Action.NONE, decision.step.action)
        assertFalse(decision.step.state.primaryActive)
        assertFalse(coordinator.promotionStillValid(check))
        assertEquals(30_000L, decision.step.waitMs)
    }

    @Test fun failedPromotionCanBeRolledBackAndRetried() {
        val coordinator = SourceRecoveryCoordinator(initiallyPrimary = false)
        val first = coordinator.beginCheck(actualPrimaryActive = false)
        assertEquals(RecoveryPolicy.Action.PROMOTE, coordinator.decide(first, healthy = true).step.action)

        // Models recovery indexing failing after a healthy probe but before pool commit.
        coordinator.markPlaybackUnavailable()
        val retry = coordinator.beginCheck(actualPrimaryActive = false)

        assertEquals(RecoveryPolicy.Action.PROMOTE, coordinator.decide(retry, healthy = true).step.action)
    }
}
