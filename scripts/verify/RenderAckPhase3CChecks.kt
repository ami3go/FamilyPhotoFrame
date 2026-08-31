fun runRenderAckPhase3CChecks() {
    println("-- render acknowledgement phase 3C --")
    check(
        "preparation watchdog leaves room for final render recovery",
        true,
        RenderAckTimeoutPolicy.PREPARATION_WATCHDOG_TIMEOUT_MS <
            RenderAckTimeoutPolicy.FINAL_RENDER_ACK_TIMEOUT_MS,
    )
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
    check(
        "preparation watchdog only recovers pre-render stages",
        true,
        RenderAckTimeoutPolicy.shouldRecoverPreparation("PREPARE_STARTED"),
    )
    check(
        "preparation watchdog leaves transition recovery to the render guard",
        false,
        RenderAckTimeoutPolicy.shouldRecoverPreparation("PREPARED"),
    )
    check(
        "preparation watchdog does not race an already-ready slide",
        false,
        RenderAckTimeoutPolicy.shouldRecoverPreparation("PREPARE_STARTED", "PREPARATION_READY"),
    )
}
