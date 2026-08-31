package com.example.familyphotoframe.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Thread-safe process-wide owner of [PlaybackMemoryPolicy] state. */
class PlaybackMemoryGuard(
    lowMemoryTier: Boolean = false,
    processMemoryBudgetBytes: Long = 0L,
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(
        PlaybackMemoryState(
            lowMemoryTier = lowMemoryTier,
            processMemoryBudgetBytes = processMemoryBudgetBytes.coerceAtLeast(0L),
        )
    )

    val state: StateFlow<PlaybackMemoryState> = mutableState.asStateFlow()

    fun snapshot(): PlaybackMemoryState = mutableState.value

    /** Frequent Java-heap check; does not refresh the slower process/system evidence. */
    fun recordHeap(
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        nowElapsedMs: Long,
    ): PlaybackMemoryState = synchronized(lock) {
        PlaybackMemoryPolicy.sample(
            mutableState.value,
            heapUsedBytes,
            heapMaxBytes,
            nowElapsedMs,
        ).also { mutableState.value = it }
    }

    /** One fresh full-process sample, normally recorded once per minute. */
    fun recordMemory(
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        processPssBytes: Long,
        systemAvailBytes: Long,
        systemThresholdBytes: Long,
        systemLowMemory: Boolean,
        nowElapsedMs: Long,
        nativePssBytes: Long? = null,
        activeMediaTransfers: Int? = null,
        oldestMediaTransferAgeMs: Long? = null,
    ): PlaybackMemoryState = synchronized(lock) {
        PlaybackMemoryPolicy.sample(
            previous = mutableState.value,
            heapUsedBytes = heapUsedBytes,
            heapMaxBytes = heapMaxBytes,
            nowElapsedMs = nowElapsedMs,
            processPssBytes = processPssBytes,
            systemAvailBytes = systemAvailBytes,
            systemThresholdBytes = systemThresholdBytes,
            systemLowMemory = systemLowMemory,
            nativePssBytes = nativePssBytes,
            activeMediaTransfers = activeMediaTransfers,
            oldestMediaTransferAgeMs = oldestMediaTransferAgeMs,
        ).also { mutableState.value = it }
    }

    /** Event-only update; the Application-owned sample loop evaluates the bounded window. */
    fun recordRenderAckTimeout(nowElapsedMs: Long): PlaybackMemoryState = synchronized(lock) {
        PlaybackMemoryPolicy.renderAckTimeout(mutableState.value, nowElapsedMs)
            .also { mutableState.value = it }
    }

    fun recordDecodeOom(nowElapsedMs: Long): PlaybackMemoryState = synchronized(lock) {
        PlaybackMemoryPolicy.decodeOom(mutableState.value, nowElapsedMs)
            .also { mutableState.value = it }
    }

    fun recordSystemPressure(
        nowElapsedMs: Long,
        severe: Boolean,
    ): PlaybackMemoryState = synchronized(lock) {
        PlaybackMemoryPolicy.systemPressure(mutableState.value, nowElapsedMs, severe)
            .also { mutableState.value = it }
    }
}
