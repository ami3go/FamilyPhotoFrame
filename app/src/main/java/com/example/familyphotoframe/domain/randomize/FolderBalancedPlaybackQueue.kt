package com.example.familyphotoframe.domain.randomize

import kotlin.random.Random

/**
 * Two-level no-repeat shuffle.
 *
 * The outer cycle visits every folder once before any folder repeats. Each folder owns
 * an independent [PlaybackQueue], so photos within that folder are also exhausted before
 * a photo repeats. This prevents large folders from monopolising playback while retaining
 * the photo-level no-repeat guarantee inside every folder.
 */
class FolderBalancedPlaybackQueue {

    data class Entry(val id: Long, val folder: String)

    private var entries: List<Entry> = emptyList()
    private var folderById: Map<Long, String> = emptyMap()
    private val folderCycle = FolderCycleQueue()
    private val photoQueues = LinkedHashMap<String, PlaybackQueue>()

    val folderCount: Int get() = folderCycle.poolSize
    val remainingFoldersInCycle: Int get() = folderCycle.remainingInCycle
    val cyclesCompleted: Int get() = folderCycle.cyclesCompleted
    fun isEmpty(): Boolean = entries.isEmpty()

    fun sync(newEntries: List<Entry>, random: Random = Random.Default) {
        val normalized = newEntries.distinctBy { it.id }
        if (normalized == entries) return

        entries = normalized
        folderById = normalized.associate { it.id to it.folder }
        val grouped = normalized.groupBy({ it.folder }, { it.id })

        photoQueues.keys.retainAll(grouped.keys)
        grouped.forEach { (folder, ids) ->
            photoQueues.getOrPut(folder) { PlaybackQueue() }
                .sync(ids, shuffleMode = true, random = random)
        }
        folderCycle.sync(grouped.keys.toList(), random)
    }

    fun next(random: Random = Random.Default): Long? {
        if (entries.isEmpty()) return null
        repeat(folderCycle.poolSize.coerceAtLeast(1)) {
            val folder = folderCycle.next(random) ?: return null
            val id = photoQueues[folder]?.next(random)
            if (id != null) return id
        }
        return null
    }

    /** Mark collage companions as shown without consuming extra folder turns. */
    fun consume(ids: Collection<Long>) {
        ids.groupBy { folderById[it] }.forEach { (folder, members) ->
            if (folder != null) photoQueues[folder]?.consume(members)
        }
    }

    fun reset() {
        entries = emptyList()
        folderById = emptyMap()
        folderCycle.reset()
        photoQueues.values.forEach(PlaybackQueue::reset)
        photoQueues.clear()
    }
}
