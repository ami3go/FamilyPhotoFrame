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
    val normal = PlaybackMemoryPolicy.sample(recovering, heap / 2L, heap, 930_001L)
    check("stable low heap and expired external hold restore normal mode", PlaybackMemoryLevel.NORMAL, normal.level)
    check("recovery resets OOM streak", 0, normal.oomStreak)

    val baseline = PlaybackMemoryPolicy.sample(
        PlaybackMemoryState(), heap / 2L, heap, 1_000_000L,
    )
    val platformCritical = PlaybackMemoryPolicy.systemPressure(
        baseline, 1_001_000L, severe = true,
    )
    check(
        "system pressure degrades without opening the decode circuit",
        PlaybackMemoryLevel.CRITICAL,
        platformCritical.level,
    )
    check("system pressure keeps selected decode enabled", true, platformCritical.allowSelectedDecode)
    val repeatedPlatformCritical = PlaybackMemoryPolicy.systemPressure(
        platformCritical, 1_002_000L, severe = true,
    )
    check(
        "repeated callback does not change preparation decision",
        platformCritical.decisionVersion,
        repeatedPlatformCritical.decisionVersion,
    )
    var callbackStorm = baseline
    var callbackAtMs = 1_000_000L
    repeat(45) {
        callbackAtMs += 30_000L
        callbackStorm = PlaybackMemoryPolicy.systemPressure(
            callbackStorm, callbackAtMs, severe = true,
        )
        callbackStorm = PlaybackMemoryPolicy.sample(
            callbackStorm, heap / 5L, heap, callbackAtMs + 10_000L,
        )
    }
    check(
        "45-callback storm changes preparation only once",
        baseline.decisionVersion + 1L,
        callbackStorm.decisionVersion,
    )

    val budget = 100L * 1024L * 1024L
    val pssCritical = PlaybackMemoryPolicy.sample(
        previous = PlaybackMemoryState(processMemoryBudgetBytes = budget),
        heapUsedBytes = heap / 2L,
        heapMaxBytes = heap,
        nowElapsedMs = 2_000_000L,
        processPssBytes = budget * 125L / 100L,
        systemAvailBytes = 500L * 1024L * 1024L,
        systemThresholdBytes = 100L * 1024L * 1024L,
        systemLowMemory = false,
    )
    check("PSS can drive critical policy with low Java heap", PlaybackMemoryLevel.CRITICAL, pssCritical.level)
    check("PSS source is explicit", PlaybackMemoryPressureSource.PROCESS_PSS, pssCritical.pressureSource)

    var renderStalls = baseline
    repeat(PlaybackMemoryPolicy.RENDER_TIMEOUT_GUARDED_COUNT) { index ->
        renderStalls = PlaybackMemoryPolicy.renderAckTimeout(
            renderStalls,
            2_100_000L + index.toLong() * 10_000L,
        )
    }
    renderStalls = PlaybackMemoryPolicy.sample(renderStalls, heap / 2L, heap, 2_140_000L)
    check("repeated render stalls enter guarded mode", PlaybackMemoryLevel.GUARDED, renderStalls.level)
    check(
        "render stall source is explicit",
        PlaybackMemoryPressureSource.RENDER_ACK_TIMEOUTS,
        renderStalls.pressureSource,
    )
    check("render stalls never block selected decode", true, renderStalls.allowSelectedDecode)
    val renderHold = renderStalls.externalGuardedUntilElapsedMs
    renderStalls = PlaybackMemoryPolicy.sample(renderStalls, heap / 2L, heap, 2_150_000L)
    check("stale render window cannot refresh its hold", renderHold, renderStalls.externalGuardedUntilElapsedMs)

    var endurance = PlaybackMemoryPolicy.sample(
        PlaybackMemoryState(), heap / 2L, heap, 4_000_000L,
    )
    var enduranceAt = 4_000_000L
    var enduranceTransitions = 0
    var enduranceCircuitObserved = false
    repeat(1_000) { index ->
        enduranceAt += 10_000L
        if (index < 600 && index % 5 == 0) {
            endurance = PlaybackMemoryPolicy.renderAckTimeout(endurance, enduranceAt)
        }
        val beforeLevel = endurance.level
        endurance = PlaybackMemoryPolicy.sample(endurance, heap / 2L, heap, enduranceAt)
        if (endurance.level != beforeLevel) enduranceTransitions++
        enduranceCircuitObserved = enduranceCircuitObserved ||
            endurance.level == PlaybackMemoryLevel.CIRCUIT_OPEN
    }
    check("render endurance never opens decode circuit", false, enduranceCircuitObserved)
    check("1,000-sample pressure run settles back to normal", PlaybackMemoryLevel.NORMAL, endurance.level)
    check("1,000-sample pressure run has bounded level changes", true, enduranceTransitions <= 4)
    check("render endurance counter remains exact", 120L, endurance.totalRenderTimeoutCount)

    val overdueTransfer = PlaybackMemoryPolicy.sample(
        previous = PlaybackMemoryState(),
        heapUsedBytes = heap / 2L,
        heapMaxBytes = heap,
        nowElapsedMs = 2_200_000L,
        activeMediaTransfers = 1,
        oldestMediaTransferAgeMs = PlaybackMemoryPolicy.TRANSFER_GUARDED_AGE_MS,
    )
    check("overdue transfer enters guarded mode", PlaybackMemoryLevel.GUARDED, overdueTransfer.level)
    check(
        "overdue transfer source is explicit",
        PlaybackMemoryPressureSource.OVERDUE_TRANSFER,
        overdueTransfer.pressureSource,
    )

    val baseNative = 40L * 1024L * 1024L
    var nativeAt = 3_000_000L
    var nativeGrowth = PlaybackMemoryPolicy.sample(
        PlaybackMemoryState(), heap / 2L, heap, nativeAt, nativePssBytes = baseNative,
    )
    repeat(PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK) { index ->
        nativeAt += PlaybackMemoryPolicy.NATIVE_GROWTH_MIN_WINDOW_MS
        nativeGrowth = PlaybackMemoryPolicy.sample(
            previous = nativeGrowth,
            heapUsedBytes = heap / 2L,
            heapMaxBytes = heap,
            nowElapsedMs = nativeAt,
            nativePssBytes = baseNative + (index.toLong() + 1L) * 1024L * 1024L,
        )
    }
    check("repeated native growth escalates to critical", PlaybackMemoryLevel.CRITICAL, nativeGrowth.level)
    check(
        "native growth source is explicit",
        PlaybackMemoryPressureSource.NATIVE_PSS_GROWTH,
        nativeGrowth.pressureSource,
    )
    check(
        "native growth evidence is bounded",
        PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK,
        nativeGrowth.nativeGrowthStreak,
    )

    val economy = PlaybackMemoryState(lowMemoryTier = true)
    check("low tier starts without speculative preload", false, economy.allowNextPreload)
    check("low tier starts with two-photo maximum", 2, economy.maxCollagePhotos)
    check("low tier forces bounded transition", true, economy.forceSimpleTransition)

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
