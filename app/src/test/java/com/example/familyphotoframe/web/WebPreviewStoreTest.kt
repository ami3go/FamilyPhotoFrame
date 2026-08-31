package com.example.familyphotoframe.web

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun onePendingCaptureRejectsDuplicateWorkAndPublishesExactlyOnce() {
        runBlocking {
            val store = WebPreviewStore(captureTimeoutMs = 1_000)
            val result = async(start = CoroutineStart.UNDISPATCHED) { store.requestCapture() }
            val request = store.captureRequest.value!!

            assertSame(WebPreviewCaptureResult.Busy, store.requestCapture())
            val captured = frame("requested", byteArrayOf(8, 7, 6))
            assertTrue(store.completeCapture(request.id, captured))
            assertFalse(store.completeCapture(request.id, frame("late")))

            val ready = result.await() as WebPreviewCaptureResult.Ready
            assertTrue(ready.publishedNewRevision)
            assertSame(captured, ready.frame)
            assertSame(captured, store.snapshot())
            assertNull(store.captureRequest.value)
        }
    }

    @Test fun failedCaptureKeepsPreviousPictureAndRejectsLateRenderer() {
        runBlocking {
            val store = WebPreviewStore(captureTimeoutMs = 1_000)
            val previous = frame("previous")
            store.update(previous)
            val result = async(start = CoroutineStart.UNDISPATCHED) { store.requestCapture() }
            val requestId = store.captureRequest.value!!.id

            assertTrue(store.failCapture(requestId, "MEMORY_PROTECTED"))
            assertEquals(
                WebPreviewCaptureResult.Failed("MEMORY_PROTECTED"),
                result.await(),
            )
            assertSame(previous, store.snapshot())
            assertFalse(store.completeCapture(requestId, frame("stale")))
        }
    }

    @Test fun captureTimesOutAndClearsDemand() {
        runBlocking {
            val store = WebPreviewStore(captureTimeoutMs = 20)
            assertSame(WebPreviewCaptureResult.TimedOut, store.requestCapture())
            assertNull(store.captureRequest.value)
        }
    }

    @Test fun clearReleasesPendingRequestAndDropsRetainedPicture() {
        runBlocking {
            val store = WebPreviewStore(captureTimeoutMs = 1_000)
            store.update(frame("previous"))
            val result = async(start = CoroutineStart.UNDISPATCHED) { store.requestCapture() }

            store.clear()

            assertEquals(WebPreviewCaptureResult.Failed("PREVIEW_CLEARED"), result.await())
            assertNull(store.captureRequest.value)
            assertNull(store.snapshot())
        }
    }
}
