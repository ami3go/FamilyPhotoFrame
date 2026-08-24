package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.data.source.PhotoSource

/**
 * Owns remote playback and metadata-backfill sources as one atomic registry.
 *
 * Source construction can suspend on settings and credential reads. These methods make
 * the subsequent winner selection indivisible, close every losing source, and perform
 * potentially blocking transport shutdown outside the registry monitor.
 */
internal class RemoteSourceRegistry(
    private val closeSource: (PhotoSource) -> Unit = { source -> source.close() },
) {
    private val lock = Any()
    private val active = mutableMapOf<String, PhotoSource>()
    private val backfill = mutableMapOf<String, PhotoSource>()
    private var replacementGeneration = 0L
    private var acceptingBackfills = true

    /**
     * Blocks newly built backfill sources while a source set is being replaced. The
     * returned generation prevents an older cancelled apply from reopening the gate
     * after a newer replacement has begun.
     */
    fun beginReplacement(): Long = synchronized(lock) {
        replacementGeneration++
        acceptingBackfills = false
        replacementGeneration
    }

    fun finishReplacement(generation: Long) {
        synchronized(lock) {
            if (replacementGeneration == generation) acceptingBackfills = true
        }
    }

    fun active(sourceId: String): PhotoSource? = synchronized(lock) { active[sourceId] }

    fun containsActive(sourceId: String): Boolean = synchronized(lock) { sourceId in active }

    fun resolved(sourceId: String): PhotoSource? = synchronized(lock) {
        active[sourceId] ?: backfill[sourceId]
    }

    /**
     * Retains [built] only when no active or backfill source won while it was being
     * constructed. A losing instance is closed immediately and the winner is returned.
     */
    fun retainBackfill(sourceId: String, built: PhotoSource): PhotoSource? {
        val winner = synchronized(lock) {
            active[sourceId]
                ?: backfill[sourceId]
                ?: if (acceptingBackfills) built.also { backfill[sourceId] = it } else null
        }
        if (winner !== built) closeSafely(built)
        return winner
    }

    /** Publishes [source] and retires both an old active source and any backfill loser. */
    fun promote(sourceId: String, source: PhotoSource) {
        val displaced = synchronized(lock) {
            val candidates = listOfNotNull(active.put(sourceId, source), backfill.remove(sourceId))
            candidates.filter { candidate -> candidate !== source }
        }
        closeDistinct(displaced)
    }

    /** Atomically empties the registry, then releases every source it owned. */
    fun releaseAll() {
        val released = synchronized(lock) {
            (active.values + backfill.values).also {
                active.clear()
                backfill.clear()
            }
        }
        closeDistinct(released)
    }

    private fun closeDistinct(sources: List<PhotoSource>) {
        val closed = mutableListOf<PhotoSource>()
        sources.forEach { source ->
            if (closed.none { it === source }) {
                closed += source
                closeSafely(source)
            }
        }
    }

    private fun closeSafely(source: PhotoSource) {
        runCatching { closeSource(source) }
    }
}
