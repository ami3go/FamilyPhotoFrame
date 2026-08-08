import java.io.ByteArrayInputStream

/** Executable dependency-free contracts for the v52.8 stability fixes. */
fun runStabilityHardeningChecks() {
    println("-- v52.8 image memory budget --")
    check(
        "100 MiB heap gets 10 MiB image cache",
        10 * 1024 * 1024,
        ImageMemoryBudget.bytesForHeap(100L * 1024L * 1024L),
    )
    check(
        "small heap respects 8 MiB floor",
        8 * 1024 * 1024,
        ImageMemoryBudget.bytesForHeap(32L * 1024L * 1024L),
    )
    check(
        "large heap respects 16 MiB ceiling",
        16 * 1024 * 1024,
        ImageMemoryBudget.bytesForHeap(512L * 1024L * 1024L),
    )

    println("-- v52.8 bounded import reader --")
    check(
        "reads exactly at limit",
        "a".repeat(1_024),
        BoundedTextInput.readUtf8(ByteArrayInputStream(ByteArray(1_024) { 'a'.code.toByte() }), 1_024),
    )
    val oversizedRejected = try {
        BoundedTextInput.readUtf8(ByteArrayInputStream(ByteArray(1_025)), 1_024)
        false
    } catch (_: ImportTooLargeException) {
        true
    }
    check("rejects one byte over limit", true, oversizedRejected)

    println("-- v52.8 virtualized folder selection policy --")
    val folders = (0 until 10_000).map { index ->
        FolderSummary("smb", "album/$index", "album-$index", 1)
    }
    val oneDeselected = FolderSelectionPolicy.toggle(
        folders,
        emptySet(),
        folders.first().selectionKey,
        selected = false,
    )
    check("one toggle creates 9,999 explicit keys", 9_999, oneDeselected.size)
    check("first folder is deselected", false, FolderSelectionPolicy.isSelected(folders.first(), oneDeselected))
    check("selection count remains exact", 9_999, FolderSelectionPolicy.selectedCount(folders, oneDeselected))
    check(
        "selecting every folder normalizes to all",
        emptySet<String>(),
        FolderSelectionPolicy.toggle(folders, oneDeselected, folders.first().selectionKey, selected = true),
    )

    println("-- v52.8 bounded shuffle diagnostic tracking --")
    val tracker = BoundedScopeLogTracker(128)
    repeat(1_000) { tracker.mark("scope-$it") }
    check("scope tracker is bounded", 128, tracker.size)
    tracker.forget("scope-999")
    check("deleted scope can be logged again", true, tracker.mark("scope-999"))
}
