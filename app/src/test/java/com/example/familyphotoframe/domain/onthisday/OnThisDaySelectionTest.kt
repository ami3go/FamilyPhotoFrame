package com.example.familyphotoframe.domain.onthisday

import com.example.familyphotoframe.data.db.PhotoDao.OnThisDayCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Year-grouping/selection logic for the "on this day" memory feature
 * (docs/FPF-FEAT-ON-THIS-DAY-001.md §4.1). All fixtures use UTC so the epoch-millis ->
 * year conversion is deterministic regardless of the machine running the test.
 */
class OnThisDaySelectionTest {

    private val zone = ZoneOffset.UTC

    private fun photoInYear(
        year: Int,
        id: Long = year.toLong(),
        isFavorite: Boolean = false,
    ) = OnThisDayCandidate(
        id = id,
        stableId = "stable-$id",
        folderName = "Folder $year",
        dateTakenEpochMs = java.time.ZonedDateTime.of(year, 6, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli(),
        isFavorite = isFavorite,
        openToken = "token-$id",
    )

    @Test fun emptyInputYieldsEmptyOutput() {
        assertEquals(emptyList<OnThisDayCandidate>(), OnThisDaySelection.select(emptyList(), 2026, 1, 6, zone))
    }

    @Test fun zeroMaxYearsYieldsEmptyOutput() {
        val candidates = listOf(photoInYear(2020))
        assertEquals(emptyList<OnThisDayCandidate>(), OnThisDaySelection.select(candidates, 2026, 1, 0, zone))
    }

    @Test fun onePerDistinctYearOrderedOldestFirst() {
        val candidates = listOf(photoInYear(2022), photoInYear(2019), photoInYear(2024))
        val result = OnThisDaySelection.select(candidates, currentYear = 2026, minYearsAgo = 1, maxYears = 6, zone)
        assertEquals(listOf(2019, 2022, 2024), result.map { it.dateTakenEpochMs.toYear() })
    }

    @Test fun favoritePreferredWithinSameYear() {
        val candidates = listOf(
            photoInYear(2020, id = 1, isFavorite = false),
            photoInYear(2020, id = 2, isFavorite = true),
        )
        val result = OnThisDaySelection.select(candidates, 2026, 1, 6, zone)
        assertEquals(1, result.size)
        assertEquals(2L, result.single().id)
    }

    @Test fun lowestIdBreaksTieWhenFavoriteStatusMatches() {
        val candidates = listOf(
            photoInYear(2020, id = 5, isFavorite = false),
            photoInYear(2020, id = 3, isFavorite = false),
        )
        val result = OnThisDaySelection.select(candidates, 2026, 1, 6, zone)
        assertEquals(3L, result.single().id)
    }

    @Test fun minYearsAgoExcludesRecentYears() {
        val candidates = listOf(photoInYear(2026), photoInYear(2025), photoInYear(2020))
        val result = OnThisDaySelection.select(candidates, currentYear = 2026, minYearsAgo = 1, maxYears = 6, zone)
        // 2026 (0 years ago) excluded; 2025 (1 year ago) and 2020 kept.
        assertEquals(listOf(2020, 2025), result.map { it.dateTakenEpochMs.toYear() })
    }

    @Test fun minYearsAgoZeroIncludesThisYear() {
        val candidates = listOf(photoInYear(2026))
        val result = OnThisDaySelection.select(candidates, currentYear = 2026, minYearsAgo = 0, maxYears = 6, zone)
        assertEquals(listOf(2026), result.map { it.dateTakenEpochMs.toYear() })
    }

    @Test fun capKeepsMostRecentYearsButDisplaysOldestFirst() {
        val candidates = listOf(2015, 2018, 2020, 2022, 2024).map { photoInYear(it) }
        val result = OnThisDaySelection.select(candidates, currentYear = 2026, minYearsAgo = 1, maxYears = 3, zone)
        // Most recent 3 years kept (2018, 2020, 2022, 2024 -> top 3 recent = 2024,2022,2020),
        // but returned oldest-first for left-to-right collage rendering.
        assertEquals(listOf(2020, 2022, 2024), result.map { it.dateTakenEpochMs.toYear() })
    }

    @Test fun resultNeverExceedsMaxYears() {
        val candidates = (2010..2025).map { photoInYear(it) }
        val result = OnThisDaySelection.select(candidates, currentYear = 2026, minYearsAgo = 1, maxYears = 6, zone)
        assertTrue(result.size <= 6)
        assertEquals(6, result.size)
    }

    private fun Long.toYear(): Int =
        java.time.Instant.ofEpochMilli(this).atZone(zone).year
}
