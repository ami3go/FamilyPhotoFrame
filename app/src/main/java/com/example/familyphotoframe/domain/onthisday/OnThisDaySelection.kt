package com.example.familyphotoframe.domain.onthisday

import com.example.familyphotoframe.data.db.PhotoDao
import java.time.Instant
import java.time.ZoneId

/**
 * Pure grouping/selection logic for the "on this day" memory feature (see
 * docs/FPF-FEAT-ON-THIS-DAY-001.md §4.1). `PhotoDao.onThisDayCandidates` has already
 * narrowed rows to an exact today's-month-day match across any year (§0.1 — no
 * day-window tolerance); this groups them by calendar year and picks one representative
 * photo per year, which is what both the single-photo and collage playback paths need.
 */
object OnThisDaySelection {

    /**
     * One candidate per distinct year, favorites preferred over non-favorites within a
     * year (lowest id breaks remaining ties for determinism — there is no "closest
     * date" tie-break because every candidate already matches the exact same day).
     * Years less than [minYearsAgo] before [currentYear] are excluded (so `minYearsAgo
     * = 1` drops "this year" from counting as a memory). When more than [maxYears]
     * distinct years match, the most recent ones are kept — a recent memory is more
     * likely to be recognizable than one from a decade back — but the returned list is
     * still ordered oldest-first, matching how the collage renders left-to-right.
     */
    fun select(
        candidates: List<PhotoDao.OnThisDayCandidate>,
        currentYear: Int,
        minYearsAgo: Int,
        maxYears: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<PhotoDao.OnThisDayCandidate> {
        if (candidates.isEmpty() || maxYears <= 0) return emptyList()
        val bestByYear = LinkedHashMap<Int, PhotoDao.OnThisDayCandidate>()
        for (candidate in candidates) {
            val year = Instant.ofEpochMilli(candidate.dateTakenEpochMs).atZone(zoneId).year
            if (currentYear - year < minYearsAgo) continue
            val existing = bestByYear[year]
            if (existing == null || isBetter(candidate, existing)) {
                bestByYear[year] = candidate
            }
        }
        return bestByYear.entries
            .sortedByDescending { it.key }
            .take(maxYears)
            .sortedBy { it.key }
            .map { it.value }
    }

    private fun isBetter(candidate: PhotoDao.OnThisDayCandidate, current: PhotoDao.OnThisDayCandidate): Boolean {
        if (candidate.isFavorite != current.isFavorite) return candidate.isFavorite
        return candidate.id < current.id
    }
}
