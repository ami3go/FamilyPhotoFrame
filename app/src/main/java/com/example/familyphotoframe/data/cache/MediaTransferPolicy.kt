package com.example.familyphotoframe.data.cache

/**
 * Cache-transfer budgets by playback priority.
 *
 * A selected presentation is already covered by the UI's 20-second preparation
 * watchdog.  Its cache transfer therefore ends two seconds earlier, leaving time for
 * cancellation, stream close, and the engine to select a replacement.  Preloading is
 * speculative work and retains the established two-minute ceiling so a slow NAS does
 * not turn an otherwise ready current frame into a failure.
 */
enum class MediaTransferPriority {
    SELECTED_PRESENTATION,
    BACKGROUND_PRELOAD,
}

internal object MediaTransferPolicy {
    const val SELECTED_PRESENTATION_DEADLINE_MS = 58_000L
    const val BACKGROUND_PRELOAD_DEADLINE_MS = 2L * 60L * 1_000L

    /**
     * jCIFS maps each caller read to a bounded SMB read request. Kotlin's generic
     * 8 KiB stream-copy buffer therefore turns one photo into hundreds of network
     * round trips on some Android 6 clients. 64 KiB remains a small fixed allocation
     * while matching the practical SMB2 read size and preserving prompt cancellation.
     */
    const val REMOTE_COPY_BUFFER_BYTES = 64 * 1024

    fun deadlineMs(priority: MediaTransferPriority): Long = when (priority) {
        MediaTransferPriority.SELECTED_PRESENTATION -> SELECTED_PRESENTATION_DEADLINE_MS
        MediaTransferPriority.BACKGROUND_PRELOAD -> BACKGROUND_PRELOAD_DEADLINE_MS
    }
}
