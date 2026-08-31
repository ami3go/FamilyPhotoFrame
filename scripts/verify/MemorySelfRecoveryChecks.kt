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
    check(
        "pressure recovery cancels the watchdog but preserves the GC rate limit",
        MemorySelfRecoveryState(lastGcRequestedAtElapsedMs = gc.state.lastGcRequestedAtElapsedMs),
        recovered.state,
    )
    check("pressure recovery never restarts", MemorySelfRecoveryAction.NONE, recovered.action)

    val staleOom = MemorySelfRecoveryPolicy.evaluate(
        MemorySelfRecoveryState(), critical, 3L, oomAt, 99,
        oomAt + MemorySelfRecoveryPolicy.OOM_ELIGIBILITY_WINDOW_MS + 1L,
    )
    check("stale historical OOM cannot trigger restart", MemorySelfRecoveryAction.NONE, staleOom.action)

    val secondOomAt = gc.state.lastGcRequestedAtElapsedMs + 30_000L
    val secondStarted = MemorySelfRecoveryPolicy.evaluate(
        recovered.state, critical, 2L, secondOomAt, 99, secondOomAt,
    )
    val suppressedGc = MemorySelfRecoveryPolicy.evaluate(
        secondStarted.state, critical, 2L, secondOomAt, 99,
        secondOomAt + MemorySelfRecoveryPolicy.SUSTAINED_PRESSURE_BEFORE_GC_MS,
    )
    check("a second OOM cannot request GC inside the global interval", MemorySelfRecoveryAction.NONE, suppressedGc.action)
    val allowedGc = MemorySelfRecoveryPolicy.evaluate(
        suppressedGc.state, critical, 2L, secondOomAt, 99,
        gc.state.lastGcRequestedAtElapsedMs + MemorySelfRecoveryPolicy.GC_MIN_INTERVAL_MS,
    )
    check("GC becomes eligible after the global interval", MemorySelfRecoveryAction.REQUEST_GC, allowedGc.action)
}
