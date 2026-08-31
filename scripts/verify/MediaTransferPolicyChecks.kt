fun runMediaTransferPolicyChecks() {
    println("-- media transfer priority --")
    check(
        "remote copies use a bounded SMB-sized buffer",
        64 * 1024,
        MediaTransferPolicy.REMOTE_COPY_BUFFER_BYTES,
    )
    check(
        "selected transfer ends before preparation watchdog",
        58_000L,
        MediaTransferPolicy.deadlineMs(MediaTransferPriority.SELECTED_PRESENTATION),
    )
    check(
        "background preload retains long transfer budget",
        120_000L,
        MediaTransferPolicy.deadlineMs(MediaTransferPriority.BACKGROUND_PRELOAD),
    )
}
