package com.example.familyphotoframe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobTest {

    @Test
    fun star_matchesWithinSegment() {
        assertTrue(Glob.matches("*.jpg", "photo.jpg"))
        assertTrue(Glob.matches("*.JPG", "photo.jpg")) // case-insensitive
        assertFalse(Glob.matches("*.jpg", "photo.png"))
    }

    @Test
    fun question_matchesSingleChar() {
        assertTrue(Glob.matches("IMG_?.jpg", "IMG_1.jpg"))
        assertFalse(Glob.matches("IMG_?.jpg", "IMG_12.jpg"))
    }

    @Test
    fun doubleStar_crossesSegments() {
        assertTrue(Glob.matches("@eaDir/**", "@eaDir/sub/thumb.jpg"))
        assertFalse(Glob.matches("*/x", "a/b/x"))
    }

    @Test
    fun dotFiles_excludedByDotStar() {
        assertTrue(Glob.matches(".*", ".hidden"))
        assertFalse(Glob.matches(".*", "visible.jpg"))
    }

    @Test
    fun isAllowed_appliesIncludeThenExclude() {
        val include = listOf("*.jpg", "*.png")
        val exclude = listOf(".*", "Thumbs.db")
        assertTrue(Glob.isAllowed("vacation.jpg", include, exclude))
        assertFalse(Glob.isAllowed("Thumbs.db", include, exclude))
        assertFalse(Glob.isAllowed("notes.txt", include, exclude))
        assertEquals(false, Glob.isAllowed(".secret.jpg", include, exclude))
    }
}
