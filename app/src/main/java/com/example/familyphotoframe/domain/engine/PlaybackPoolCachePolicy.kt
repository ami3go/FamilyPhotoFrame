package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.SelectionMode

/**
 * Rules for reusing the playback id pool between slides.
 *
 * Without reuse, every advance re-runs an unbounded `displayableIds` query and boxes the
 * whole result — megabytes of garbage per slide on a large library, which is exactly what
 * a 2 GB tablet cannot spare. The risk of reuse is the opposite one: playing a pool that
 * no longer matches the user's filters. So the key below must mention every input the
 * query reads, and a missing one is a bug rather than an inefficiency.
 *
 * Pure and total, so the completeness of that key is unit-testable.
 */
object PlaybackPoolCachePolicy {

    private const val UNIT_SEPARATOR = '\u001f'

    /** Every value `SlideshowEngine.queryPool` passes to the DAO, in a stable order. */
    fun key(
        selectionMode: SelectionMode,
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Boolean,
        cachedOnly: Boolean,
        allowHeif: Boolean,
        folders: List<String>,
    ): String = buildString {
        append(selectionMode.name).append(UNIT_SEPARATOR)
        append(sourceIds.joinToString(",")).append(UNIT_SEPARATOR)
        append(maxFailures).append(UNIT_SEPARATOR)
        append(if (favoritesOnly) 1 else 0)
        append(if (cachedOnly) 1 else 0)
        append(if (allowHeif) 1 else 0).append(UNIT_SEPARATOR)
        append(folders.joinToString(","))
    }

    /**
     * @param ageMs how long ago the cached pool was loaded. The age check is a backstop
     *   for a change nothing told the engine about; explicit invalidation covers the rest,
     *   and a row that became undisplayable is skipped by the pick-time recheck anyway.
     */
    fun canReuse(
        enabled: Boolean,
        cachedKey: String?,
        currentKey: String,
        ageMs: Long,
        maxAgeMs: Long,
    ): Boolean = enabled && cachedKey == currentKey && ageMs in 0 until maxAgeMs
}
