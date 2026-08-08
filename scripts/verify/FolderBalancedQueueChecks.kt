import kotlin.random.Random

fun runFolderBalancedQueueChecks() {
    println("-- folder-balanced shuffle --")
    val folders = (1..106).map { "folder-$it" }
    run {
        val q = FolderCycleQueue()
        q.sync(folders, Random(52))
        val cycle = (1..106).mapNotNull { q.next(Random(it)) }
        check("106-folder cycle complete", folders.toSet(), cycle.toSet())
        check("106-folder cycle unique", 106, cycle.distinct().size)
    }

    run {
        val entries = (1..4).flatMap { folder ->
            (1..3).map { photo ->
                FolderBalancedPlaybackQueue.Entry(folder * 100L + photo, "folder-$folder")
            }
        }
        val folderById = entries.associate { it.id to it.folder }
        val q = FolderBalancedPlaybackQueue()
        q.sync(entries, Random(9))
        val firstTwelve = (1..12).mapNotNull { q.next(Random(it + 30)) }
        check(
            "each outer cycle visits every folder",
            true,
            firstTwelve.chunked(4).all { chunk -> chunk.map(folderById::getValue).toSet().size == 4 },
        )
        check(
            "per-folder photo cycles do not repeat",
            true,
            folders.take(4).all { folder ->
                firstTwelve.filter { folderById.getValue(it) == folder }.distinct().size == 3
            },
        )
    }
}
