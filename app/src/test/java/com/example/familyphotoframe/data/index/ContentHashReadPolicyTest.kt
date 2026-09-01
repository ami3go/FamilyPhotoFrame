package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.cache.MediaTransferPolicy
import com.example.familyphotoframe.util.toHexString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

class ContentHashReadPolicyTest {
    @Test
    fun hashesAllBytesWithSmbSizedReads() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val input = RecordingInputStream(bytes)

        val result = digestContentStream(input, MessageDigest.getInstance("SHA-256"))

        assertEquals(bytes.size.toLong(), result.bytesRead)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(),
            result.sha256,
        )
        assertEquals(MediaTransferPolicy.REMOTE_COPY_BUFFER_BYTES, input.maxRequestedBytes)
    }

    @Test
    fun yieldsBeforeAnotherReadWhenSelectedMediaBecomesActive() = runBlocking {
        val input = RecordingInputStream(ByteArray(200_000) { 1 })
        var checks = 0
        var yielded = false

        try {
            digestContentStream(
                input,
                MessageDigest.getInstance("SHA-256"),
                shouldYield = { ++checks > 1 },
            )
        } catch (_: ContentHashYieldException) {
            yielded = true
        }

        assertTrue(yielded)
        assertEquals(1, input.readCalls)
    }

    private class RecordingInputStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var maxRequestedBytes = 0
            private set
        var readCalls = 0
            private set

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCalls++
            maxRequestedBytes = maxOf(maxRequestedBytes, length)
            return delegate.read(buffer, offset, length)
        }
    }
}
