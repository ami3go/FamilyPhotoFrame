package com.example.familyphotoframe.domain.randomize

import com.example.familyphotoframe.data.db.PhotoItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LeastRecentRandomTest {

    private fun entity(id: Long) = PhotoItemEntity(
        id = id,
        stableId = "s$id",
        sourceId = "src",
        normalizedPath = "p$id",
        folderName = "f",
        fileName = "f$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1,
        fileModifiedEpochMs = 0,
        openToken = "/tmp/$id",
        indexedAtEpochMs = 0,
    )

    @Test
    fun emptyWindow_returnsNull() {
        assertNull(LeastRecentRandom.pick(emptyList(), currentId = null, random = Random(1)))
    }

    @Test
    fun singleItem_returnsIt_evenIfCurrent() {
        val only = entity(7)
        assertEquals(only, LeastRecentRandom.pick(listOf(only), currentId = 7, random = Random(1)))
    }

    @Test
    fun avoidsBackToBackRepeat_whenAlternativesExist() {
        val window = (1L..5L).map { entity(it) }
        repeat(50) { seed ->
            val picked = LeastRecentRandom.pick(window, currentId = 3, random = Random(seed.toLong()))
            assertNotNull(picked)
            assertTrue("should not repeat current when others exist", picked!!.id != 3L)
        }
    }

    @Test
    fun picksWithinWindow() {
        val window = (1L..4L).map { entity(it) }
        val ids = window.map { it.id }.toSet()
        repeat(50) { seed ->
            val picked = LeastRecentRandom.pick(window, currentId = null, random = Random(seed.toLong()))
            assertTrue(picked!!.id in ids)
        }
    }
}
