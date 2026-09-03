package com.example.familyphotoframe.data.cache

import kotlin.math.ceil

/** Pure decision used at the 58-second selected-transfer soft boundary. */
internal object SelectedTransferDeadlinePolicy {
    const val MIN_COMPLETION_FRACTION = 0.80
    const val MAX_PROGRESS_AGE_MS = 2_000L

    fun extensionMs(
        copiedBytes: Long,
        expectedBytes: Long,
        lastProgressAgeMs: Long,
        recentBytesPerSecond: Long,
    ): Long {
        if (expectedBytes <= 0L || copiedBytes <= 0L || copiedBytes >= expectedBytes) return 0L
        if (copiedBytes.toDouble() / expectedBytes < MIN_COMPLETION_FRACTION) return 0L
        if (lastProgressAgeMs !in 0..MAX_PROGRESS_AGE_MS || recentBytesPerSecond <= 0L) return 0L
        val remaining = expectedBytes - copiedBytes
        val predictedMs = ceil(remaining * 1_000.0 / recentBytesPerSecond).toLong()
        return predictedMs.takeIf { it in 1..MediaTransferPolicy.SELECTED_PRESENTATION_MAX_EXTENSION_MS }
            ?: 0L
    }
}
