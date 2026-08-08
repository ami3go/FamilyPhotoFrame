fun runMemorySelfRecoveryChecks() {
    println("-- legacy OOM process self-recovery --")
    val critical = PlaybackMemoryLevel.CIRCUIT_OPEN
    val oomAt = 10_000L

    val started = MemorySelfRecoveryPolicy.evaluate(
        MemorySelfRecoveryState(), critical, 1L, oomAt, 99, oomAt,
    )
    check("pinned heap watchdog starts without immediate action", MemorySelfRecoveryAction.NONE, started.action)

    val beforeMinute = MemorySelfRecoveryPolicy.evaluate(
        started.state, critical, 1L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS - 1L,
    )
    check("watchdog waits a full minute before GC", MemorySelfRecoveryAction.NONE, beforeMinute.action)

    val gc = MemorySelfRecoveryPolicy.evaluate(
        beforeMinute.state, critical, 1L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS,
    )
    check("sustained critical heap requests one GC", MemorySelfRecoveryAction.REQUEST_GC, gc.action)

    val verify = MemorySelfRecoveryPolicy.evaluate(
        gc.state, critical, 1L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS +
            MemorySelfRecoveryPolicy.POST_GC_VERIFY_MS - 1L,
    )
    check("restart waits for a post-GC sample", MemorySelfRecoveryAction.NONE, verify.action)

    val restart = MemorySelfRecoveryPolicy.evaluate(
        verify.state, critical, 1L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS +
            MemorySelfRecoveryPolicy.POST_GC_VERIFY_MS,
    )
    check("pinned post-GC heap requests process restart", MemorySelfRecoveryAction.RESTART_PROCESS, restart.action)
    val oneShot = MemorySelfRecoveryPolicy.evaluate(
        restart.state, critical, 1L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS +
            MemorySelfRecoveryPolicy.POST_GC_VERIFY_MS + 10_000L,
    )
    check("restart request is one-shot", MemorySelfRecoveryAction.NONE, oneShot.action)

    val recovered = MemorySelfRecoveryPolicy.evaluate(
        gc.state, PlaybackMemoryLevel.RECOVERY, 1L, oomAt, 90,
        oomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS + 20_000L,
    )
    check("pressure recovery cancels the watchdog", MemorySelfRecoveryState(), recovered.state)
    check("pressure recovery never restarts", MemorySelfRecoveryAction.NONE, recovered.action)

    val staleOom = MemorySelfRecoveryPolicy.evaluate(
        MemorySelfRecoveryState(), critical, 3L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.OOM_ELIGIBILITY_WINDOW_MS + 1L,
    )
    check("stale historical OOM cannot trigger restart", MemorySelfRecoveryAction.NONE, staleOom.action)
}
