package com.example.familyphotoframe.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPreviewStoreTest {
    private fun frame(revision: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) = WebPreviewFrame(
        revision = revision,
        jpeg = bytes,
        presentationId = 1,
        type = "single",
        photoIds = listOf(1),
        fileName = "one.jpg",
        sourceId = "test",
        folder = "folder",
        transition = "crossfade",
        committedAtEpochMs = 1,
        width = 320,
        height = 180,
    )

    @Test fun storeRetainsOnlyOneImmutableByContractByteArray() {
        val store = WebPreviewStore()
        val bytes = byteArrayOf(4, 5, 6)
        assertTrue(store.update(frame("a", bytes)))
        assertSame(bytes, store.snapshot()!!.jpeg)

        val replacement = byteArrayOf(7, 8)
        assertTrue(store.update(frame("b", replacement)))
        assertSame(replacement, store.snapshot()!!.jpeg)
    }

    @Test fun duplicateRevisionIsNotRepublished() {
        val store = WebPreviewStore()
        assertTrue(store.update(frame("same")))
        assertFalse(store.update(frame("same", byteArrayOf(9))))
    }
}
