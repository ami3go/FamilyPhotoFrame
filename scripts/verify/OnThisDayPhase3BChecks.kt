fun runOnThisDayPhase3BChecks() {
    println("-- on this day phase 3B --")
    check(
        "on this day avoids speculative next-photo consumption",
        false,
        OnThisDayPlaybackPolicy.shouldPreloadNext(isOnThisDay = true),
    )
    check(
        "only the final unique memory completes the interlude pool",
        true,
        OnThisDayPlaybackPolicy.isTerminalPick(isOnThisDay = true, remainingAfterPick = 0),
    )
    check(
        "terminal memory waits for a real visible acknowledgement",
        false,
        OnThisDayPlaybackPolicy.shouldHoldVisibleFrame(true, terminalPhotoId = 9L, renderedPhotoId = null),
    )
    check(
        "terminal memory holds after it is visibly rendered",
        true,
        OnThisDayPlaybackPolicy.shouldHoldVisibleFrame(true, terminalPhotoId = 9L, renderedPhotoId = 9L),
    )
    check(
        "timed override wakes at its own expiry",
        300_000L,
        PlaylistOverrideWakePolicy.nextWakeDelayMs(
            nowEpochMs = 1_000L,
            scheduleDelayMs = 900_000L,
            overrideUntilEpochMs = 301_000L,
        ),
    )
}
