fun runMediaTransferPolicyChecks() {
    println("-- media transfer priority --")
    check(
        "selected transfer ends before preparation watchdog",
        18_000L,
        MediaTransferPolicy.deadlineMs(MediaTransferPriority.SELECTED_PRESENTATION),
    )
    check(
        "background preload retains long transfer budget",
        120_000L,
        MediaTransferPolicy.deadlineMs(MediaTransferPriority.BACKGROUND_PRELOAD),
    )
}
