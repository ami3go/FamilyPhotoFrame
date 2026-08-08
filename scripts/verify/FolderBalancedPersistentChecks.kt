fun runFolderBalancedPersistentChecks() {
    println("-- persistent folder-balanced shuffle --")

    val folders = (1..106).map { "source\u001ffolder-$it" }
    val order = ShuffleCycleGenerator.generate(folders, null, FixedSeedShuffleRandom(52))
    check("persistent folder cycle includes all folders", folders.toSet(), order.toSet())
    check("persistent folder cycle has no duplicate", 106, order.distinct().size)

    val next = ShuffleCycleGenerator.generate(folders, order.last(), FixedSeedShuffleRandom(52))
    check("folder boundary repeat avoided", false, next.first() == order.last())

    var previousLast: String? = null
    var enduranceOk = true
    repeat(100) { cycle ->
        val cycleOrder = ShuffleCycleGenerator.generate(
            (1..50).map { "folder-$it" },
            previousLast,
            FixedSeedShuffleRandom(1_000L + cycle),
        )
        enduranceOk = enduranceOk && cycleOrder.size == 50 && cycleOrder.toSet().size == 50 &&
            (previousLast == null || cycleOrder.first() != previousLast)
        previousLast = cycleOrder.last()
    }
    check("100 complete folder cycles remain fair and unique", true, enduranceOk)

    var photoEnduranceOk = true
    (1..50).forEach { folderIndex ->
        val members = (1..(folderIndex % 17 + 2)).map { "f$folderIndex-photo-$it" }
        var last: String? = null
        repeat(10) { cycle ->
            val photoOrder = ShuffleCycleGenerator.generate(
                members, last, FixedSeedShuffleRandom(folderIndex * 100L + cycle),
            )
            photoEnduranceOk = photoEnduranceOk &&
                photoOrder.size == members.size && photoOrder.toSet() == members.toSet() &&
                (members.size == 1 || last == null || photoOrder.first() != last)
            last = photoOrder.lastOrNull()
        }
    }
    check("every folder completes 10 photo cycles without repeats", true, photoEnduranceOk)

    val largeStart = System.nanoTime()
    val largeOrder = ShuffleCycleGenerator.generate(
        (1..10_000).map { "photo-$it" }, null, FixedSeedShuffleRandom(99),
    )
    val largeElapsedMs = (System.nanoTime() - largeStart) / 1_000_000L
    check("10,000-member photo cycle remains complete", 10_000, largeOrder.toSet().size)
    check("10,000-member cycle generation remains bounded", true, largeElapsedMs < 2_000L)

    val one = ShuffleCycleGenerator.generate(listOf("only"), "only", FixedSeedShuffleRandom(1))
    check("single folder cycle remains predictable", listOf("only"), one)

    val merged = ShuffleCycleGenerator.mergeRemaining(
        existingRemaining = listOf("a", "b"),
        newlyEligible = listOf("c", "c", "d"),
        random = FixedSeedShuffleRandom(4),
    )
    check("reconciliation inserts each new folder once", setOf("a", "b", "c", "d"), merged.toSet())
    check("reconciliation result unique", 4, merged.distinct().size)

    val folder = FolderKey.fromIndexedPath("smb-main", "/Family/2025/Birthday/photo.jpg")
    check("folder identity uses direct parent", "Family/2025/Birthday", folder.canonicalRelativeDirectory)
    check("folder identity preserves source", "smb-main", FolderKey.parse(folder.storageKey()).sourceId)

    val tracker = SourceAvailabilityTracker()
    val first = tracker.failure("smb", "network", 1_000L)
    check("source first backoff 30 seconds", 31_000L, first.retryAtEpochMs)
    val second = tracker.failure("smb", "network", 31_000L)
    check("source second backoff 2 minutes", 151_000L, second.retryAtEpochMs)
    check("source retry waits for deadline", false, tracker.shouldRetry("smb", 150_999L))
    check("source retry opens at deadline", true, tracker.shouldRetry("smb", 151_000L))
    tracker.recovered("smb")
    check("source recovery clears outage", emptySet<String>(), tracker.unavailableSourceIds())

    val inputs = ShuffleScopeKeyFactory.EligibilityInputs(
        playlistId = "playlist-1",
        sourceIds = setOf("local", "smb"),
        selectedFolders = setOf("Family", "Travel"),
        favoritesOnly = false,
        cachedOnly = false,
        maxDecodeFailures = 3,
    )
    val revision = ShuffleScopeKeyFactory.eligibilityRevision(inputs)
    val sameDifferentOrder = ShuffleScopeKeyFactory.eligibilityRevision(
        inputs.copy(sourceIds = listOf("smb", "local"), selectedFolders = listOf("Travel", "Family"))
    )
    check("eligibility revision canonicalizes set ordering", revision, sameDifferentOrder)
    val cacheModeRevision = ShuffleScopeKeyFactory.eligibilityRevision(inputs.copy(cachedOnly = true))
    check("runtime cache mode does not reset shuffle scope", revision, cacheModeRevision)
    val changed = ShuffleScopeKeyFactory.eligibilityRevision(inputs.copy(favoritesOnly = true))
    check("eligibility change creates new revision", true, changed != revision)
    val scopeA = ShuffleScopeKeyFactory.scope("playlist-1", revision, SelectionMode.FOLDER_BALANCED_SHUFFLE, "primary")
    val scopeB = ShuffleScopeKeyFactory.scope("playlist-1", revision, SelectionMode.FOLDER_BALANCED_SHUFFLE, "primary")
    check("scope key is stable", scopeA.scopeKey, scopeB.scopeKey)
}
