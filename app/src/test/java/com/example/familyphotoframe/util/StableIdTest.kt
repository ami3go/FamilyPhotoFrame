package com.example.familyphotoframe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdTest {

    @Test
    fun deterministic_forSameInputs() {
        val a = StableId.of("local_saf", "a/b/c.jpg", 1234, 999)
        val b = StableId.of("local_saf", "a/b/c.jpg", 1234, 999)
        assertEquals(a, b)
    }

    @Test
    fun changes_whenAnyComponentChanges() {
        val base = StableId.of("local_saf", "a/b/c.jpg", 1234, 999)
        assertNotEquals(base, StableId.of("smb", "a/b/c.jpg", 1234, 999))
        assertNotEquals(base, StableId.of("local_saf", "a/b/d.jpg", 1234, 999))
        assertNotEquals(base, StableId.of("local_saf", "a/b/c.jpg", 1235, 999))
        assertNotEquals(base, StableId.of("local_saf", "a/b/c.jpg", 1234, 1000))
    }

    @Test
    fun is32HexChars() {
        val id = StableId.of("s", "p", 1, 2)
        assertEquals(32, id.length)
        assertEquals(true, id.all { it in "0123456789abcdef" })
    }

    @Test
    fun supportedFormats_acceptByExtensionOrMime() {
        assertEquals(true, SupportedFormats.isSupported("photo.JPG", null))
        assertEquals(true, SupportedFormats.isSupported("IMG_1234.HEIC", null))
        assertEquals(true, SupportedFormats.isSupported("IMG_1234.HEIF", null))
        assertEquals(true, SupportedFormats.isSupported("noext", "image/heic"))
        assertEquals(true, SupportedFormats.isSupported("noext", "image/heif-sequence"))
        assertEquals(true, SupportedFormats.isSupported("noext", "image/png"))
        assertEquals(false, SupportedFormats.isSupported("notes.txt", "text/plain"))
        assertEquals(false, SupportedFormats.isSupported("trailingdot.", null))
    }
}
