package com.example.familyphotoframe.ui.slideshow

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CappedInputStreamTest {
    @Test fun neverReadsPastConfiguredLimit() {
        val source = ByteArray(32) { it.toByte() }
        val capped = CappedInputStream(ByteArrayInputStream(source), 7)
        val result = ByteArray(20)
        val first = capped.read(result, 0, result.size)
        val second = capped.read(result, first, result.size - first)

        assertEquals(7, first)
        assertEquals(-1, second)
        assertArrayEquals(source.copyOfRange(0, 7), result.copyOfRange(0, 7))
    }

    @Test fun skipAlsoConsumesBudget() {
        val capped = CappedInputStream(ByteArrayInputStream(ByteArray(20) { it.toByte() }), 5)
        assertEquals(3L, capped.skip(3L))
        assertEquals(3, capped.read())
        assertEquals(4, capped.read())
        assertEquals(-1, capped.read())
    }
}
