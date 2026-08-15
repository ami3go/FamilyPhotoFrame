package com.example.familyphotoframe.domain.randomize

import kotlin.random.Random

/**
 * Ordered playback over an explicit pool of photo ids (spec §9.6 `shuffle_no_repeat`,
 * plus the date-ordered modes).
 *
 * This queue consumes the whole displayable id list rather than sampling a partial
 * window, which makes the no-repeat guarantee explicit and testable.
 *
 * Pure and deterministic given [Random] — no Room, no Android, no clock — so the
 * guarantee is unit-testable rather than merely asserted.
 *
 * Ownership: the engine holds one instance and calls [sync] whenever the pool may have
 * changed (rescan, source swap, curation edit) and [next] to advance. Both are cheap;
 * [sync] is a no-op when nothing relevant changed.
 */
class PlaybackQueue {

    private var pool: List<Long> = emptyList()
    private var shuffle: Boolean = true

    /** Ids already handed out in the current cycle, in issue order. */
    private val consumed = LinkedHashSet<Long>()

    /** Ids still owed in the current cycle, in the order they will be issued. */
    private val remaining = ArrayDeque<Long>()

    /** Last id returned by [next]; used only to avoid a repeat across a cycle seam. */
    private var lastReturned: Long? = null

    /** Completed passes over the pool — surfaced for diagnostics, not behaviour. */
    var cyclesCompleted: Int = 0
        private set

    val poolSize: Int get() = pool.size
    val remainingInCycle: Int get() = remaining.size
    fun isEmpty(): Boolean = pool.isEmpty()

    /**
     * Point the queue at [newPool], which must already be in the caller's desired play
     * order for sequential modes (the DAO does the `ORDER BY`); for [shuffleMode] the
     * incoming order is irrelevant because it is shuffled here.
     *
     * Progress within the current cycle survives a pool change: ids already shown stay
     * shown, ids that disappeared are forgotten, and ids that appeared are added to the
     * current cycle rather than having to wait for the next one. That matters on a frame
     * whose NAS index is rescanned while it plays — a rescan must not silently restart
     * the cycle and reshow the same photos.
     */
    fun sync(newPool: List<Long>, shuffleMode: Boolean, random: Random = Random.Default) {
        val poolChanged = newPool != pool
        val modeChanged = shuffleMode != shuffle
        if (!poolChanged && !modeChanged) return

        pool = newPool
        shuffle = shuffleMode

        val poolSet = newPool.toHashSet()
        // Forget consumption of ids that are no longer in the pool at all.
        consumed.retainAll(poolSet)
        rebuildRemaining(random)
    }

    /**
     * Next id to display, or null when the pool is empty.
     *
     * Never repeats an id until every other id in the pool has been shown. At the seam
     * between cycles the first pick of the new cycle is nudged away from the last pick
     * of the old one, so "no repeat" holds across the wrap as well as within it.
     */
    fun next(random: Random = Random.Default): Long? {
        if (pool.isEmpty()) return null
        if (remaining.isEmpty()) {
            cyclesCompleted++
            consumed.clear()
            rebuildRemaining(random)
            avoidSeamRepeat()
        }
        val id = remaining.removeFirst()
        consumed.add(id)
        lastReturned = id
        return id
    }

    /**
     * Forget all progress (source swap, or a mode change the caller wants to restart).
     *
     * Drops the remembered pool too, so the next [sync] genuinely rebuilds instead of
     * short-circuiting on an unchanged pool and leaving the queue empty — which would
     * make the following [next] look like the end of a cycle that never ran.
     */
    fun reset() {
        pool = emptyList()
        consumed.clear()
        remaining.clear()
        lastReturned = null
        cyclesCompleted = 0
    }

    /**
     * Mark photos shown as members of a collage as consumed in the current cycle.
     * They are removed from the remaining queue so date/shuffle modes do not replay
     * them immediately after they were already visible beside the anchor photo.
     */
    fun consume(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val valid = ids.filterTo(LinkedHashSet()) { it in pool }
        if (valid.isEmpty()) return
        consumed.addAll(valid)
        val kept = remaining.filterNot { it in valid }
        remaining.clear()
        remaining.addAll(kept)
        lastReturned = valid.lastOrNull() ?: lastReturned
    }

    private fun rebuildRemaining(random: Random) {
        val owed = pool.filter { it !in consumed }
        remaining.clear()
        remaining.addAll(if (shuffle) owed.shuffled(random) else owed)
    }

    /**
     * Swap the first two entries when a fresh cycle would otherwise replay the photo
     * still on screen. A swap (rather than a reshuffle) keeps the call deterministic and
     * cannot loop; with a pool of one there is nothing to avoid, so the repeat stands.
     */
    private fun avoidSeamRepeat() {
        if (remaining.size < 2) return
        if (remaining.first() != lastReturned) return
        val first = remaining.removeFirst()
        val second = remaining.removeFirst()
        remaining.addFirst(first)
        remaining.addFirst(second)
    }
}
