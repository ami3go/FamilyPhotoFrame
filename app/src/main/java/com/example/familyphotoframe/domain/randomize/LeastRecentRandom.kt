package com.example.familyphotoframe.domain.randomize

import com.example.familyphotoframe.data.db.PhotoItemEntity
import kotlin.random.Random

/**
 * `least_recent_random` (spec §9.6): bias selection away from recently shown photos.
 *
 * The DAO supplies a window of the least-recently-shown displayable rows (NULL
 * lastShownAt first). This picks uniformly within that window — so unseen and
 * long-unseen photos dominate — while avoiding an immediate back-to-back repeat
 * when more than one candidate exists. Pure and deterministic given [random];
 * unit-tested without Android.
 *
 * This is the MVP-safe mode. True no-repeat `shuffle_no_repeat` requires a backing
 * ShuffleSession/ShuffleQueue (spec §9.6) and is deferred to Phase 1+.
 */
object LeastRecentRandom {

    fun pick(
        window: List<PhotoItemEntity>,
        currentId: Long?,
        random: Random = Random.Default,
    ): PhotoItemEntity? {
        if (window.isEmpty()) return null
        if (window.size == 1) return window.first()
        val pool = if (currentId != null) window.filter { it.id != currentId } else window
        val effective = pool.ifEmpty { window }
        return effective[random.nextInt(effective.size)]
    }
}
