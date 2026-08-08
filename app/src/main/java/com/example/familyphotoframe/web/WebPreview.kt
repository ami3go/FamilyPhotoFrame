package com.example.familyphotoframe.web

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

class WebPreviewStore {
    private val lock = Any()
    private var current: WebPreviewFrame? = null

    /** Replace the single retained frame. Returns false for a duplicate revision. */
    fun update(frame: WebPreviewFrame): Boolean = synchronized(lock) {
        if (current?.revision == frame.revision) return false
        current = frame
        true
    }

    /** Immutable-by-contract snapshot; no JPEG copy is made. */
    fun snapshot(): WebPreviewFrame? = synchronized(lock) { current }

    fun revision(): String? = synchronized(lock) { current?.revision }

    fun clear() = synchronized(lock) { current = null }
}
