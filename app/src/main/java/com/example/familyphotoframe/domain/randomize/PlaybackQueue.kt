package com.example.familyphotoframe.domain.randomize

import kotlin.random.Random

/**
 * Ordered playback over an explicit pool of photo ids (spec §9.6 `shuffle_no_repeat`,
 * plus the date-ordered modes).
 *
 * This queue consumes the whole displayable id list rather than sampling a partial
 * window, which makes the no-repeat guarantee explicit and testable.
 *
 * Everything is held in primitive arrays. The obvious implementation — `List<Long>`,
 * `LinkedHashSet<Long>`, `ArrayDeque<Long>` — allocates a distinct boxed object per id
 * and roughly forty bytes per set entry, so a 50,000-photo library costs several
 * megabytes per queue and the engine keeps two. Ids live in a [LongArray], "already
 * shown" is a bitset, and the outstanding cycle is an [IntArray] of pool positions with a
 * read cursor, which brings the same library under half a megabyte.
 *
 * Pure and deterministic given [Random] — no Room, no Android, no clock — so the
 * guarantee is unit-testable rather than merely asserted.
 *
 * Ownership: the engine holds one instance and calls [sync] whenever the pool may have
 * changed (rescan, source swap, curation edit) and [next] to advance. Both are cheap;
 * [sync] is a no-op when nothing relevant changed.
 */
class PlaybackQueue {

    /** Caller-supplied order. Never mutated here, and never copied — see [sync]. */
    private var pool: LongArray = EMPTY_LONGS
    /** [pool] sorted, with [sortedToPool] mapping each entry back to its pool position. */
    private var sortedIds: LongArray = EMPTY_LONGS
    private var sortedToPool: IntArray = EMPTY_INTS

    /** One bit per pool position: has this id been handed out in the current cycle. */
    private var consumedBits: LongArray = EMPTY_LONGS
    private var consumedCount: Int = 0

    private var shuffle: Boolean = true

    /** Pool positions still owed in this cycle, issued from [remainingHead] upwards. */
    private var remaining: IntArray = EMPTY_INTS
    private var remainingHead: Int = 0
    private var remainingEnd: Int = 0

    /** Last id returned by [next]; used only to avoid a repeat across a cycle seam. */
    private var lastReturned: Long? = null

    /** Completed passes over the pool — surfaced for diagnostics, not behaviour. */
    var cyclesCompleted: Int = 0
        private set

    val poolSize: Int get() = pool.size
    val remainingInCycle: Int get() = remainingEnd - remainingHead
    fun isEmpty(): Boolean = pool.isEmpty()

    /**
     * Point the queue at [newPool], which must already be in the caller's desired play
     * order for sequential modes (the DAO does the `ORDER BY`); for [shuffleMode] the
     * incoming order is irrelevant because it is shuffled here.
     *
     * The array is retained rather than copied, so the caller must not mutate it
     * afterwards. In exchange, an engine that hands back its cached pool unchanged is
     * recognised by identity and this returns without touching anything.
     *
     * Progress within the current cycle survives a pool change: ids already shown stay
     * shown, ids that disappeared are forgotten, and ids that appeared are added to the
     * current cycle rather than having to wait for the next one. That matters on a frame
     * whose NAS index is rescanned while it plays — a rescan must not silently restart
     * the cycle and reshow the same photos.
     */
    fun sync(newPool: LongArray, shuffleMode: Boolean, random: Random = Random.Default) {
        val poolChanged = !(newPool === pool || newPool.contentEquals(pool))
        val modeChanged = shuffleMode != shuffle
        if (!poolChanged && !modeChanged) return

        val previouslyConsumed = consumedIds()
        pool = newPool
        shuffle = shuffleMode
        rebuildIdIndex()

        consumedBits = LongArray((pool.size + 63) ushr 6)
        consumedCount = 0
        // Forget consumption of ids that are no longer in the pool at all.
        for (id in previouslyConsumed) {
            val position = positionOf(id)
            if (position >= 0) markConsumed(position)
        }
        rebuildRemaining(random)
    }

    /** Convenience for callers holding a boxed list, such as tests and legacy paths. */
    fun sync(newPool: List<Long>, shuffleMode: Boolean, random: Random = Random.Default) =
        sync(newPool.toLongArray(), shuffleMode, random)

    /**
     * Next id to display, or null when the pool is empty.
     *
     * Never repeats an id until every other id in the pool has been shown. At the seam
     * between cycles the first pick of the new cycle is nudged away from the last pick
     * of the old one, so "no repeat" holds across the wrap as well as within it.
     */
    fun next(random: Random = Random.Default): Long? {
        if (pool.isEmpty()) return null
        if (remainingInCycle == 0) {
            cyclesCompleted++
            consumedBits.fill(0L)
            consumedCount = 0
            rebuildRemaining(random)
            avoidSeamRepeat()
        }
        if (remainingInCycle == 0) return null
        val position = remaining[remainingHead++]
        markConsumed(position)
        val id = pool[position]
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
        pool = EMPTY_LONGS
        sortedIds = EMPTY_LONGS
        sortedToPool = EMPTY_INTS
        consumedBits = EMPTY_LONGS
        consumedCount = 0
        remaining = EMPTY_INTS
        remainingHead = 0
        remainingEnd = 0
        lastReturned = null
        cyclesCompleted = 0
    }

    /**
     * Mark photos shown as members of a collage as consumed in the current cycle.
     * They are removed from the remaining queue so date/shuffle modes do not replay
     * them immediately after they were already visible beside the anchor photo.
     */
    fun consume(ids: Collection<Long>) {
        if (ids.isEmpty() || pool.isEmpty()) return
        var lastValid: Long? = null
        var matched = false
        for (id in ids) {
            val position = positionOf(id)
            if (position < 0) continue
            markConsumed(position)
            matched = true
            lastValid = id
        }
        if (!matched) return

        var write = remainingHead
        for (read in remainingHead until remainingEnd) {
            val position = remaining[read]
            if (!isConsumed(position)) remaining[write++] = position
        }
        remainingEnd = write
        lastReturned = lastValid ?: lastReturned
    }

    // ---- internals -------------------------------------------------------------

    private fun isConsumed(position: Int): Boolean =
        consumedBits[position ushr 6] and (1L shl (position and 63)) != 0L

    private fun markConsumed(position: Int) {
        if (isConsumed(position)) return
        consumedBits[position ushr 6] = consumedBits[position ushr 6] or (1L shl (position and 63))
        consumedCount++
    }

    private fun consumedIds(): LongArray {
        if (consumedCount == 0) return EMPTY_LONGS
        val ids = LongArray(consumedCount)
        var n = 0
        for (position in pool.indices) {
            if (isConsumed(position)) ids[n++] = pool[position]
        }
        return ids
    }

    /**
     * Sorted view of the pool for id lookup. Built once per pool change rather than kept
     * as a hash map, which would reintroduce a boxed key per photo.
     */
    private fun rebuildIdIndex() {
        val size = pool.size
        if (size == 0) {
            sortedIds = EMPTY_LONGS
            sortedToPool = EMPTY_INTS
            return
        }
        // Boxed only here, on a genuine pool change, never on the per-slide path.
        val order = (0 until size).sortedBy { pool[it] }
        sortedIds = LongArray(size)
        sortedToPool = IntArray(size)
        order.forEachIndexed { sortedPosition, poolPosition ->
            sortedIds[sortedPosition] = pool[poolPosition]
            sortedToPool[sortedPosition] = poolPosition
        }
    }

    /** Pool position of [id], or -1. Duplicate ids resolve to whichever sorts first. */
    private fun positionOf(id: Long): Int {
        val found = sortedIds.binarySearch(id)
        return if (found >= 0) sortedToPool[found] else -1
    }

    private fun rebuildRemaining(random: Random) {
        val owed = IntArray(pool.size - consumedCount)
        var n = 0
        for (position in pool.indices) {
            if (!isConsumed(position)) owed[n++] = position
        }
        if (shuffle) shuffleInPlace(owed, random)
        remaining = owed
        remainingHead = 0
        remainingEnd = n
    }

    /**
     * Fisher–Yates in the same order `List.shuffled(random)` draws, so a given seed keeps
     * producing the sequence the determinism tests pin down.
     */
    private fun shuffleInPlace(values: IntArray, random: Random) {
        for (i in values.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = values[i]
            values[i] = values[j]
            values[j] = tmp
        }
    }

    /**
     * Swap the first two entries when a fresh cycle would otherwise replay the photo
     * still on screen. A swap (rather than a reshuffle) keeps the call deterministic and
     * cannot loop; with a pool of one there is nothing to avoid, so the repeat stands.
     */
    private fun avoidSeamRepeat() {
        if (remainingInCycle < 2) return
        if (pool[remaining[remainingHead]] != lastReturned) return
        val first = remaining[remainingHead]
        remaining[remainingHead] = remaining[remainingHead + 1]
        remaining[remainingHead + 1] = first
    }

    private companion object {
        val EMPTY_LONGS = LongArray(0)
        val EMPTY_INTS = IntArray(0)
    }
}
