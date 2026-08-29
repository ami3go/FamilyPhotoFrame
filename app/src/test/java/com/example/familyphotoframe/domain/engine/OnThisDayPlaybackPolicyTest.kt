package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnThisDayPlaybackPolicyTest {

    @Test fun onThisDayDoesNotPreloadAndConsumeItsOnlyPhotoTwice() {
        assertFalse(OnThisDayPlaybackPolicy.shouldPreloadNext(isOnThisDay = true))
        assertTrue(OnThisDayPlaybackPolicy.shouldPreloadNext(isOnThisDay = false))
    }

    @Test fun onlyTheLastUniquePhotoCompletesTheInterludePool() {
        assertFalse(OnThisDayPlaybackPolicy.isTerminalPick(isOnThisDay = true, remainingAfterPick = 2))
        assertTrue(OnThisDayPlaybackPolicy.isTerminalPick(isOnThisDay = true, remainingAfterPick = 0))
        assertFalse(OnThisDayPlaybackPolicy.isTerminalPick(isOnThisDay = false, remainingAfterPick = 0))
    }

    @Test fun finalPhotoIsHeldOnlyAfterTheUiAcknowledgesIt() {
        assertFalse(
            OnThisDayPlaybackPolicy.shouldHoldVisibleFrame(
                isOnThisDay = true,
                terminalPhotoId = 7L,
                renderedPhotoId = null,
            )
        )
        assertTrue(
            OnThisDayPlaybackPolicy.shouldHoldVisibleFrame(
                isOnThisDay = true,
                terminalPhotoId = 7L,
                renderedPhotoId = 7L,
            )
        )
    }
}
