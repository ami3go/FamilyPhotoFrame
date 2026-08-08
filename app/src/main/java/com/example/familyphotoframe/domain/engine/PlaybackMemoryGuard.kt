package com.example.familyphotoframe.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Thread-safe process-wide owner of [PlaybackMemoryPolicy] state. */
class PlaybackMemoryGuard {
    private val lock = Any()
    private val mutableState = MutableStateFlow(PlaybackMemoryState())

    val state: StateFlow<PlaybackMemoryState> = mutableState.asStateFlow()

    fun snapshot(): PlaybackMemoryState = mutableState.value

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
