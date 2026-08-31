fun runRenderAckRecoveryChecks() {
    println("-- render acknowledgement recovery --")
    check(
        "stale same-photo preparation callbacks are rejected by generation",
        false,
        RenderAckRecoveryPolicy.isCurrentAttempt(12L, 13L),
    )
    check(
        "current preparation callbacks keep flowing",
        true,
        RenderAckRecoveryPolicy.isCurrentAttempt(13L, 13L),
    )
    check(
        "selected transfer deadline releases the slow anchor",
        true,
        RenderAckRecoveryPolicy.shouldReleaseCancelledAnchor("SELECTED_DEADLINE"),
    )
    check(
        "ordinary preparation cancellation preserves anchor eligibility",
        false,
        RenderAckRecoveryPolicy.shouldReleaseCancelledAnchor("TRANSFER_SLOT_RELEASED"),
    )
    check(
        "visible same-photo re-selection bypasses an unobservable acknowledgement",
        true,
        RenderAckRecoveryPolicy.reusesVisiblePresentation(41L, 41L, 41L),
    )
    check(
        "different selected photo waits for the UI",
        false,
        RenderAckRecoveryPolicy.reusesVisiblePresentation(41L, 41L, 42L),
    )
    check(
        "unacknowledged selection is never assumed visible",
        false,
        RenderAckRecoveryPolicy.reusesVisiblePresentation(41L, 40L, 41L),
    )
}
