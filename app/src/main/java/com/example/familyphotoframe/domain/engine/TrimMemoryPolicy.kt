package com.example.familyphotoframe.domain.engine

/** What an `onTrimMemory` level means for playback. */
enum class TrimMemoryResponse {
    /** The frame's own heap is nearly exhausted: enter emergency protection. */
    SEVERE_PRESSURE,

    /** The frame's own heap is tight: enter constrained protection. */
    RUNNING_PRESSURE,

    /**
     * The process is in the background LRU list. Caches are worth dropping, but playback
     * must not degrade: nothing is on screen, and the heap is whole again on return.
     */
    BACKGROUND_ONLY,

    /** Nothing to do. */
    NONE,
}

/**
 * Interprets `ComponentCallbacks2` trim levels.
 *
 * The levels form two unrelated series that share one callback. `RUNNING_*` describes the
 * frame's own heap while it is in the foreground and is what the playback guard exists to
 * react to. `UI_HIDDEN`/`BACKGROUND`/`MODERATE`/`COMPLETE` describe the *process's position
 * in the background LRU list* — how soon it would be killed to make room for something
 * else. They carry no information about this frame's heap, and their numeric values are
 * higher only because the constants were laid out that way.
 *
 * Reading the background series as heap pressure is what the field logs showed: every one
 * of six launches on the target frame received `COMPLETE` (80) within a second of startup
 * at 7–8% heap use, which opened the circuit breaker and left collages disabled for the
 * ten-minute recovery window while the heap sat near-empty the whole time.
 *
 * Pure and total, like [PlaybackMemoryPolicy] whose decisions it feeds.
 */
object TrimMemoryPolicy {

    // From android.content.ComponentCallbacks2. Duplicated so this stays a pure JVM
    // object testable without a device; the values are frozen platform API.
    const val TRIM_MEMORY_RUNNING_MODERATE = 5
    const val TRIM_MEMORY_RUNNING_LOW = 10
    const val TRIM_MEMORY_RUNNING_CRITICAL = 15
    const val TRIM_MEMORY_UI_HIDDEN = 20
    const val TRIM_MEMORY_BACKGROUND = 40
    const val TRIM_MEMORY_MODERATE = 60
    const val TRIM_MEMORY_COMPLETE = 80

    fun classify(level: Int): TrimMemoryResponse = when (level) {
        TRIM_MEMORY_RUNNING_CRITICAL -> TrimMemoryResponse.SEVERE_PRESSURE
        TRIM_MEMORY_RUNNING_LOW -> TrimMemoryResponse.RUNNING_PRESSURE
        TRIM_MEMORY_RUNNING_MODERATE -> TrimMemoryResponse.NONE
        // The background series, and any future level the platform adds above it.
        in TRIM_MEMORY_UI_HIDDEN..Int.MAX_VALUE -> TrimMemoryResponse.BACKGROUND_ONLY
        else -> TrimMemoryResponse.NONE
    }

    /**
     * Dropping the image cache is free whenever the platform asks: it is rebuilt on
     * demand, and under [TrimMemoryResponse.BACKGROUND_ONLY] nothing is on screen to
     * re-decode. Only the playback guard needs to distinguish the two series.
     */
    fun shouldClearImageCache(level: Int): Boolean =
        classify(level) != TrimMemoryResponse.NONE
}
