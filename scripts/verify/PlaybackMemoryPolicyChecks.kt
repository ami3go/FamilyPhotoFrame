fun runPlaybackMemoryPolicyChecks() {
    println("-- low-memory slideshow circuit breaker --")
    // Standard-threshold checks use a modern-sized heap. API-21-era small heaps have
    // deliberately earlier thresholds and are asserted separately below.
    val heap = 256L * 1024L * 1024L

    val guarded = PlaybackMemoryPolicy.sample(
        PlaybackMemoryState(), (heap * 85L + 99L) / 100L, heap, 1_000L,
    )
    check("85 percent enters guarded mode", PlaybackMemoryLevel.GUARDED, guarded.level)
    check("guarded mode disables preload", false, guarded.allowNextPreload)
    check("guarded mode limits collage to two", 2, guarded.maxCollagePhotos)

    val lowHeap = 100L * 1024L * 1024L
    val lowHeapGuarded = PlaybackMemoryPolicy.sample(
        PlaybackMemoryState(), lowHeap * 76L / 100L, lowHeap, 1_000L,
    )
    val lowHeapCritical = PlaybackMemoryPolicy.sample(
        lowHeapGuarded, lowHeap * 86L / 100L, lowHeap, 2_000L,
    )
    check("small heaps guard at 75 percent", PlaybackMemoryLevel.GUARDED, lowHeapGuarded.level)
    check("small heaps become critical at 85 percent", PlaybackMemoryLevel.CRITICAL, lowHeapCritical.level)

    val critical = PlaybackMemoryPolicy.sample(
        guarded, heap * 93L / 100L, heap, 2_000L,
    )
    check("92 percent enters critical mode", PlaybackMemoryLevel.CRITICAL, critical.level)
    check("critical mode uses one photo", 1, critical.maxCollagePhotos)
    check("critical mode disables blur", false, critical.allowBlurredBackdrop)

    val criticalLow = PlaybackMemoryPolicy.sample(critical, heap * 75L / 100L, heap, 3_000L)
    val criticalHeld = PlaybackMemoryPolicy.sample(
        criticalLow,
        heap * 75L / 100L,
        heap,
        3_000L + PlaybackMemoryPolicy.CRITICAL_RECOVERY_HOLD_MS - 1L,
    )
    val guardedAfterHold = PlaybackMemoryPolicy.sample(
        criticalHeld,
        heap * 75L / 100L,
        heap,
        3_000L + PlaybackMemoryPolicy.CRITICAL_RECOVERY_HOLD_MS,
    )
    check("critical holds for five stable minutes", PlaybackMemoryLevel.CRITICAL, criticalHeld.level)
    check("critical relaxes only to guarded", PlaybackMemoryLevel.GUARDED, guardedAfterHold.level)
    check("preload stays disabled after critical", false, guardedAfterHold.allowNextPreload)

    val guardedLow = PlaybackMemoryPolicy.sample(guarded, heap * 69L / 100L, heap, 4_000L)
    val guardedHeld = PlaybackMemoryPolicy.sample(
        guardedLow,
        heap * 69L / 100L,
        heap,
        4_000L + PlaybackMemoryPolicy.GUARDED_RECOVERY_HOLD_MS - 1L,
    )
    val normalAfterHold = PlaybackMemoryPolicy.sample(
        guardedHeld,
        heap * 69L / 100L,
        heap,
        4_000L + PlaybackMemoryPolicy.GUARDED_RECOVERY_HOLD_MS,
    )
    check("guarded holds for three stable minutes", PlaybackMemoryLevel.GUARDED, guardedHeld.level)
    check("guarded returns to normal after hold", PlaybackMemoryLevel.NORMAL, normalAfterHold.level)

    val firstOom = PlaybackMemoryPolicy.decodeOom(critical, 10_000L)
    check("first OOM opens circuit", PlaybackMemoryLevel.CIRCUIT_OPEN, firstOom.level)
    check("first OOM blocks selected decode", false, firstOom.allowSelectedDecode)
    check("first OOM cooldown is one minute", 60_000L, firstOom.circuitRemainingMs(10_000L))

    val secondOom = PlaybackMemoryPolicy.decodeOom(firstOom, 20_000L)
    check("repeated OOM doubles cooldown", 120_000L, secondOom.circuitRemainingMs(20_000L))
    check("OOM count is exact", 2L, secondOom.totalOomCount)

    val runningLow = PlaybackMemoryPolicy.systemPressure(secondOom, 30_000L, severe = false)
    check(
        "running-low signal cannot downgrade OOM circuit",
        PlaybackMemoryLevel.CIRCUIT_OPEN,
        runningLow.level,
    )
    check("running-low signal keeps selected decode blocked", false, runningLow.allowSelectedDecode)

    val heldOpen = PlaybackMemoryPolicy.sample(runningLow, heap * 93L / 100L, heap, 150_001L)
    check("critical heap keeps expired circuit open", PlaybackMemoryLevel.CIRCUIT_OPEN, heldOpen.level)
    val recovering = PlaybackMemoryPolicy.sample(runningLow, heap * 90L / 100L, heap, 150_001L)
    check("85-91 percent heap leaves expired circuit in recovery", PlaybackMemoryLevel.RECOVERY, recovering.level)
    check("recovery re-enables one selected decode", true, recovering.allowSelectedDecode)
    val reopened = PlaybackMemoryPolicy.decodeOom(recovering, 160_001L)
    check("OOM during recovery immediately re-opens circuit", PlaybackMemoryLevel.CIRCUIT_OPEN, reopened.level)
    val normal = PlaybackMemoryPolicy.sample(recovering, heap / 2L, heap, 700_001L)
    check("stable low heap restores normal mode", PlaybackMemoryLevel.NORMAL, normal.level)
    check("recovery resets OOM streak", 0, normal.oomStreak)

    val guard = PlaybackMemoryGuard()
    val workers = (0 until 8).map { worker ->
        Thread {
            guard.recordHeap(heap / 2L, heap, worker.toLong())
            guard.recordDecodeOom(1_000L + worker)
        }.apply { start() }
    }
    workers.forEach { it.join() }
    check("concurrent OOM updates are not lost", 8L, guard.snapshot().totalOomCount)
    check("concurrent guard remains circuit-open", PlaybackMemoryLevel.CIRCUIT_OPEN, guard.snapshot().level)
}
