import com.example.familyphotoframe.web.WebPreviewCaptureResult
import com.example.familyphotoframe.web.WebPreviewFrame
import com.example.familyphotoframe.web.WebPreviewStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

private fun previewFrame(revision: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) =
    WebPreviewFrame(
        revision = revision,
        jpeg = bytes,
        presentationId = 1L,
        type = "single",
        photoIds = listOf(1L),
        fileName = "one.jpg",
        sourceId = "test",
        folder = "folder",
        transition = "crossfade",
        committedAtEpochMs = 1L,
        width = 320,
        height = 180,
    )

fun runWebPreviewChecks() = runBlocking {
    println("-- on-demand web preview --")
    val store = WebPreviewStore(captureTimeoutMs = 1_000L)
    val first = async(start = CoroutineStart.UNDISPATCHED) { store.requestCapture() }
    val request = store.captureRequest.value!!
    check("one capture request becomes pending", request, store.captureRequest.value)
    check("duplicate capture creates no work", WebPreviewCaptureResult.Busy, store.requestCapture())

    val captured = previewFrame("requested", byteArrayOf(8, 7, 6))
    check("matching capture publishes", true, store.completeCapture(request.id, captured))
    val ready = first.await() as WebPreviewCaptureResult.Ready
    check("request receives captured frame", captured, ready.frame)
    check("capture demand clears", null, store.captureRequest.value)
    check("late completion is rejected", false, store.completeCapture(request.id, previewFrame("late")))

    val failed = async(start = CoroutineStart.UNDISPATCHED) { store.requestCapture() }
    val failedId = store.captureRequest.value!!.id
    check("memory-protected request ends", true, store.failCapture(failedId, "MEMORY_PROTECTED"))
    check(
        "failed capture reports reason",
        WebPreviewCaptureResult.Failed("MEMORY_PROTECTED"),
        failed.await(),
    )
    check("failed capture keeps previous JPEG", captured, store.snapshot())

    val cleared = async(start = CoroutineStart.UNDISPATCHED) { store.requestCapture() }
    store.clear()
    check(
        "server stop releases pending capture",
        WebPreviewCaptureResult.Failed("PREVIEW_CLEARED"),
        cleared.await(),
    )
    check("server stop drops retained JPEG", null, store.snapshot())
}
