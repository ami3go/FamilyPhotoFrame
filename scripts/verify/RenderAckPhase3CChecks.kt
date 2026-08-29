fun runRenderAckPhase3CChecks() {
    println("-- render acknowledgement phase 3C --")
    check(
        "selection without preparation is diagnosed",
        "PREPARATION_NOT_STARTED",
        RenderAckTimeoutPolicy.reasonFor(null),
    )
    check(
        "prepared target without transition is diagnosed",
        "TRANSITION_NOT_STARTED",
        RenderAckTimeoutPolicy.reasonFor("PREPARED"),
    )
    check(
        "started transition without callback is diagnosed",
        "TRANSITION_STALLED",
        RenderAckTimeoutPolicy.reasonFor("TRANSITION_STARTED"),
    )
    check(
        "completed transition without engine callback is diagnosed",
        "RENDER_CALLBACK_NOT_DELIVERED",
        RenderAckTimeoutPolicy.reasonFor("TRANSITION_COMPLETED"),
    )
}
