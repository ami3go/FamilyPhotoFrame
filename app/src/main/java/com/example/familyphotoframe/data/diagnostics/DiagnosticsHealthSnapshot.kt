package com.example.familyphotoframe.data.diagnostics

/** Immutable diagnostics health exposed to crash capture, exports, and support UIs. */
data class DiagnosticsHealthSnapshot(
    val queueCapacity: Int,
    val queueDepth: Int,
    val droppedTotal: Long,
    val droppedSinceLastReport: Long,
    val fieldsDropped: Long,
    val standard: Stream,
    val bulk: Stream,
    val lastSuccessfulWriteEpochMs: Long,
    val lastSuccessfulFlushEpochMs: Long,
    val lastFlushTimeoutMs: Long,
    val crashEnvelopePresent: Boolean,
    val crashEnvelopeBytes: Int,
    val rateStateEntries: Int,
) {
    data class Stream(
        val retainedBytes: Long = 0L,
        val retainedGenerations: Int = 0,
        val rotations: Long = 0L,
        val oldestKnownSessionId: String = "",
        val oldestKnownSequence: Long = 0L,
        val newestKnownSessionId: String = "",
        val newestKnownSequence: Long = 0L,
        val lastSuccessfulWriteEpochMs: Long = 0L,
        val lastAppendErrorClass: String = "",
    )
}
