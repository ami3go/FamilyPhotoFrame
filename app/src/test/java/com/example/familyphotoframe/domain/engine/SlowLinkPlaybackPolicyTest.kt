package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowLinkPlaybackPolicyTest {
    @Test fun entersAfterThreeRecentProgressDeadlinesWithACachedPool() {
        var state = SlowLinkPlaybackPolicy.State()
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 0, 2)
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 60_000, 2)
        assertFalse(state.active)
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 120_000, 2)
        assertTrue(state.active)
    }

    @Test fun doesNotEnterWithoutTwoCachedPhotosOrWithExpiredDeadlines() {
        var state = SlowLinkPlaybackPolicy.State()
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 0, 1)
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 60_000, 1)
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 120_000, 1)
        assertFalse(state.active)
        state = SlowLinkPlaybackPolicy.onProgressDeadline(state, 600_001, 10)
        assertFalse(state.active)
    }

    @Test fun exitsOnlyAfterThreeCompletionsAndTenDeadlineFreeMinutes() {
        var state = SlowLinkPlaybackPolicy.State(active = true, lastDeadlineMs = 1_000)
        state = SlowLinkPlaybackPolicy.onRemoteCompletion(state, 500_000)
        state = SlowLinkPlaybackPolicy.onRemoteCompletion(state, 600_999)
        assertTrue(state.active)
        state = SlowLinkPlaybackPolicy.onRemoteCompletion(state, 601_000)
        assertFalse(state.active)
    }

    @Test fun idleTimerExitsWhenCompletionsFinishedBeforeTheQuietWindow() {
        val state = SlowLinkPlaybackPolicy.State(
            active = true,
            lastDeadlineMs = 1_000,
            completionsSinceDeadline = 3,
        )
        assertTrue(SlowLinkPlaybackPolicy.onIdleTimer(state, 600_999).active)
        assertFalse(SlowLinkPlaybackPolicy.onIdleTimer(state, 601_000).active)
    }
}
