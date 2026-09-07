fun runSmbStabilityPolicyChecks() {
    println("-- SMB stability policies --")
    check(
        "resumable partial accepts the stable-id key shape",
        true,
        PartialCachePolicy.ownsFileName("0123456789abcdef0123456789abcdef.part"),
    )
    check(
        "partial continuation has a bounded slice",
        30_000L,
        MediaTransferPolicy.deadlineMs(MediaTransferPriority.PARTIAL_RESUME),
    )
    check(
        "selected transfer receives bounded near-complete grace",
        4_000L,
        SelectedTransferDeadlinePolicy.extensionMs(
            copiedBytes = 9_600L,
            expectedBytes = 10_000L,
            lastProgressAgeMs = 100L,
            recentBytesPerSecond = 100L,
        ),
    )
    check(
        "stalled selected transfer receives no grace",
        0L,
        SelectedTransferDeadlinePolicy.extensionMs(
            copiedBytes = 9_600L,
            expectedBytes = 10_000L,
            lastProgressAgeMs = 2_001L,
            recentBytesPerSecond = 100L,
        ),
    )

    var slow = SlowLinkPlaybackPolicy.State()
    repeat(SlowLinkPlaybackPolicy.DEADLINES_TO_ENTER) { index ->
        slow = SlowLinkPlaybackPolicy.onProgressDeadline(
            state = slow,
            nowMs = index * 1_000L,
            cachedPhotos = SlowLinkPlaybackPolicy.MIN_CACHED_PHOTOS,
        )
    }
    check("repeated progressing deadlines enter cached playback", true, slow.active)
    repeat(SlowLinkPlaybackPolicy.HEALTHY_COMPLETIONS_TO_EXIT) {
        slow = SlowLinkPlaybackPolicy.onRemoteCompletion(
            slow,
            slow.lastDeadlineMs + SlowLinkPlaybackPolicy.DEADLINE_FREE_EXIT_MS,
        )
    }
    check("healthy completions leave cached playback", false, slow.active)
}
