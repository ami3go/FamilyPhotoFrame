package com.example.familyphotoframe.web

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared low-resolution representation of the committed presentation.
 *
 * [jpeg] is treated as immutable after construction. The store keeps exactly one frame
 * and returns the same byte array to avoid allocating a second full JPEG on every status,
 * presentation, or preview request.
 */
data class WebPreviewFrame(
    val revision: String,
    val jpeg: ByteArray,
    val presentationId: Long,
    val type: String,
    val photoIds: List<Long>,
    val fileName: String,
    val sourceId: String,
    val folder: String,
    val transition: String,
    val committedAtEpochMs: Long,
    val width: Int,
    val height: Int,
)

/** One capture demand delivered to the active slideshow surface. */
data class WebPreviewCaptureRequest(val id: Long)

/** Bounded result of an authenticated on-demand capture request. */
sealed interface WebPreviewCaptureResult {
    data class Ready(val frame: WebPreviewFrame, val publishedNewRevision: Boolean) :
        WebPreviewCaptureResult

    data object Busy : WebPreviewCaptureResult
    data object TimedOut : WebPreviewCaptureResult
    data class Failed(val reason: String) : WebPreviewCaptureResult
}

/**
 * Owns the single retained JPEG and at most one in-flight capture request.
 *
 * A request never starts a second pipeline: concurrent presses receive [WebPreviewCaptureResult.Busy].
 * Completion is matched by request id so a late renderer cannot publish after timeout, cancellation,
 * server shutdown, or a newer request.
 */
class WebPreviewStore(
    private val captureTimeoutMs: Long = DEFAULT_CAPTURE_TIMEOUT_MS,
) {
    private data class PendingCapture(
        val request: WebPreviewCaptureRequest,
        val completion: CompletableDeferred<WebPreviewCaptureResult>,
    )

    private val lock = Any()
    private var current: WebPreviewFrame? = null
    private var nextRequestId = 0L
    private var pending: PendingCapture? = null
    private val _captureRequest = MutableStateFlow<WebPreviewCaptureRequest?>(null)

    init {
        require(captureTimeoutMs > 0L) { "captureTimeoutMs must be positive" }
    }

    val captureRequest: StateFlow<WebPreviewCaptureRequest?> = _captureRequest.asStateFlow()

    /** Replace the single retained frame. Returns false for a duplicate revision. */
    fun update(frame: WebPreviewFrame): Boolean = synchronized(lock) {
        if (current?.revision == frame.revision) return false
        current = frame
        true
    }

    /** Immutable-by-contract snapshot; no JPEG copy is made. */
    fun snapshot(): WebPreviewFrame? = synchronized(lock) { current }

    fun revision(): String? = synchronized(lock) { current?.revision }

    /**
     * Request exactly one render of the currently committed slideshow presentation.
     * The HTTP caller waits for this bounded result; duplicate requests do no extra work.
     */
    suspend fun requestCapture(): WebPreviewCaptureResult {
        val capture = synchronized(lock) {
            if (pending != null) return WebPreviewCaptureResult.Busy
            val request = WebPreviewCaptureRequest(++nextRequestId)
            PendingCapture(request, CompletableDeferred()).also {
                pending = it
                _captureRequest.value = request
            }
        }
        return try {
            withTimeoutOrNull(captureTimeoutMs) { capture.completion.await() }
                ?: timeoutCapture(capture.request.id)
        } catch (cancelled: CancellationException) {
            failCapture(capture.request.id, "REQUEST_CANCELLED")
            throw cancelled
        }
    }

    /** Atomically publish only if [requestId] still owns the pending capture. */
    fun completeCapture(requestId: Long, frame: WebPreviewFrame): Boolean {
        val completion = synchronized(lock) {
            val capture = pending?.takeIf { it.request.id == requestId } ?: return false
            val changed = current?.revision != frame.revision
            val retained = if (changed || current == null) frame.also { current = it } else current!!
            pending = null
            _captureRequest.value = null
            capture.completion to WebPreviewCaptureResult.Ready(retained, changed)
        }
        completion.first.complete(completion.second)
        return true
    }

    /** End a matching request without replacing the last successful JPEG. */
    fun failCapture(requestId: Long, reason: String): Boolean {
        val completion = synchronized(lock) {
            val capture = pending?.takeIf { it.request.id == requestId } ?: return false
            pending = null
            _captureRequest.value = null
            capture.completion
        }
        completion.complete(WebPreviewCaptureResult.Failed(reason))
        return true
    }

    fun clear() {
        val completion = synchronized(lock) {
            current = null
            val capture = pending
            pending = null
            _captureRequest.value = null
            capture?.completion
        }
        completion?.complete(WebPreviewCaptureResult.Failed("PREVIEW_CLEARED"))
    }

    private fun timeoutCapture(requestId: Long): WebPreviewCaptureResult {
        synchronized(lock) {
            if (pending?.request?.id == requestId) {
                pending = null
                _captureRequest.value = null
            }
        }
        return WebPreviewCaptureResult.TimedOut
    }

    private companion object {
        const val DEFAULT_CAPTURE_TIMEOUT_MS = 10_000L
    }
}
