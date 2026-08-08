package com.example.familyphotoframe.domain.randomize

import kotlin.random.Random

/**
 * Random folder rotation with a strict no-repeat cycle.
 *
 * Every eligible folder is returned exactly once before any folder is returned again.
 * A pool update preserves progress: removed folders disappear, newly discovered folders
 * join the current cycle, and folders already issued remain consumed until the cycle ends.
 */
class FolderCycleQueue {

    private var pool: List<String> = emptyList()
    private val consumed = LinkedHashSet<String>()
    private val remaining = ArrayDeque<String>()
    private var lastReturned: String? = null

    var cyclesCompleted: Int = 0
        private set

    val poolSize: Int get() = pool.size
    val remainingInCycle: Int get() = remaining.size
    fun isEmpty(): Boolean = pool.isEmpty()

    fun sync(newPool: List<String>, random: Random = Random.Default) {
        val normalized = newPool.distinct()
        if (normalized == pool) return

        pool = normalized
        val poolSet = normalized.toHashSet()
        consumed.retainAll(poolSet)
        rebuildRemaining(random)
    }

    fun next(random: Random = Random.Default): String? {
        if (pool.isEmpty()) return null
        if (remaining.isEmpty()) {
            cyclesCompleted++
            consumed.clear()
            rebuildRemaining(random)
            avoidSeamRepeat()
        }
        val folder = remaining.removeFirst()
        consumed.add(folder)
        lastReturned = folder
        return folder
    }

    fun reset() {
        pool = emptyList()
        consumed.clear()
        remaining.clear()
        lastReturned = null
        cyclesCompleted = 0
    }

    private fun rebuildRemaining(random: Random) {
        val owed = pool.filter { it !in consumed }.shuffled(random)
        remaining.clear()
        remaining.addAll(owed)
    }

    private fun avoidSeamRepeat() {
        if (remaining.size < 2 || remaining.first() != lastReturned) return
        val first = remaining.removeFirst()
        val second = remaining.removeFirst()
        remaining.addFirst(first)
        remaining.addFirst(second)
    }
}
