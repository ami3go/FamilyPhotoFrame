package com.example.familyphotoframe.ui.slideshow

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun probeWrapperClosesWithoutDrainingRemainingBudget() {
        val source = TrackingInputStream(REMOTE_COLLAGE_PROBE_BYTE_LIMIT.toInt() * 2)

        val decoded = useRemoteProbeStream(source, REMOTE_COLLAGE_PROBE_BYTE_LIMIT) { stream ->
            assertEquals(0, stream.read())
            "dimensions-read"
        }

        assertEquals("dimensions-read", decoded)
        assertTrue(source.closed)
        assertTrue(source.bytesRead >= 1L && source.bytesRead < REMOTE_COLLAGE_PROBE_BYTE_LIMIT)
    }

    private class TrackingInputStream(private val size: Int) : InputStream() {
        var bytesRead = 0L
        var closed = false

        override fun read(): Int {
            if (bytesRead >= size) return -1
            return (bytesRead++ and 0xffL).toInt()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= size) return -1
            val count = minOf(length.toLong(), size - bytesRead).toInt()
            repeat(count) { index -> buffer[offset + index] = ((bytesRead + index) and 0xffL).toByte() }
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
