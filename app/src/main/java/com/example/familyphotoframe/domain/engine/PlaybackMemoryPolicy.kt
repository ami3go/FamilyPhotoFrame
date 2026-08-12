package com.example.familyphotoframe.domain.engine

/** Playback degradation levels used to keep low-memory frames alive. */
enum class PlaybackMemoryLevel {
    NORMAL,
    GUARDED,
    CRITICAL,
    CIRCUIT_OPEN,
    RECOVERY,
}

/**
 * Immutable decision state for slideshow memory protection.
 *
 * The state deliberately contains only primitives. It is safe to expose through a
 * StateFlow without retaining a decoded image or Android object.
 */
data class PlaybackMemoryState(
    val level: PlaybackMemoryLevel = PlaybackMemoryLevel.NORMAL,
    val heapUsedBytes: Long = 0L,
    val heapMaxBytes: Long = 0L,
    val pressurePercent: Int = 0,
    val circuitOpenUntilElapsedMs: Long = 0L,
    val recoveryUntilElapsedMs: Long = 0L,
    val lastOomElapsedMs: Long = 0L,
    val oomStreak: Int = 0,
    val totalOomCount: Long = 0L,
    /** First sample continuously below the exit threshold; zero while not recovering. */
    val lowPressureSinceElapsedMs: Long = 0L,
    /** Changes only when a preparation decision changes, not on every heap sample. */
    val decisionVersion: Long = 0L,
) {
    val allowSelectedDecode: Boolean get() = level != PlaybackMemoryLevel.CIRCUIT_OPEN
    val allowNextPreload: Boolean get() = level == PlaybackMemoryLevel.NORMAL
    val allowSoftFocus: Boolean get() = level == PlaybackMemoryLevel.NORMAL
    val allowBlurredBackdrop: Boolean get() = level == PlaybackMemoryLevel.NORMAL
    val allowWebPreview: Boolean get() = level == PlaybackMemoryLevel.NORMAL
    val forceSimpleTransition: Boolean get() = level != PlaybackMemoryLevel.NORMAL

    /** Three-photo portrait frames are disabled under pressure; critical/recovery uses singles. */
    val maxCollagePhotos: Int get() = when (level) {
        PlaybackMemoryLevel.NORMAL -> 3
        PlaybackMemoryLevel.GUARDED -> 2
        PlaybackMemoryLevel.CRITICAL,
        PlaybackMemoryLevel.CIRCUIT_OPEN,
        PlaybackMemoryLevel.RECOVERY -> 1
    }

    /** Decode below physical display size while guarded; the GPU scales the result. */
    val decodeScale: Float get() = when (level) {
        PlaybackMemoryLevel.NORMAL -> 1.0f
        PlaybackMemoryLevel.GUARDED -> 0.80f
        PlaybackMemoryLevel.CRITICAL -> 0.68f
        PlaybackMemoryLevel.CIRCUIT_OPEN -> 0.60f
        PlaybackMemoryLevel.RECOVERY -> 0.70f
    }

    fun circuitRemainingMs(nowElapsedMs: Long): Long =
        (circuitOpenUntilElapsedMs - nowElapsedMs).coerceAtLeast(0L)
}

/** Pure state machine behind [PlaybackMemoryGuard]. */
object PlaybackMemoryPolicy {
    const val GUARDED_ENTER_PERCENT = 85
    const val CRITICAL_ENTER_PERCENT = 92
    const val GUARDED_EXIT_PERCENT = 70
    const val CRITICAL_EXIT_PERCENT = 80

    /**
     * API-21-era devices commonly expose only a 96–128 MiB Java heap. The field soak
     * showed a 100 MiB heap reaching 98–99% before the old thresholds reacted strongly
     * enough. Keep more absolute headroom on those heaps while leaving modern devices
     * on the established thresholds above.
     */
    const val LOW_HEAP_MAX_BYTES = 128L * 1024L * 1024L
    const val LOW_HEAP_GUARDED_ENTER_PERCENT = 75
    const val LOW_HEAP_CRITICAL_ENTER_PERCENT = 85
    const val LOW_HEAP_GUARDED_EXIT_PERCENT = 62
    const val LOW_HEAP_CRITICAL_EXIT_PERCENT = 72

    const val GUARDED_RECOVERY_HOLD_MS = 3L * 60_000L
    const val CRITICAL_RECOVERY_HOLD_MS = 5L * 60_000L
    const val FIRST_OOM_COOLDOWN_MS = 60_000L
    const val MAX_OOM_COOLDOWN_MS = 5L * 60_000L
    const val OOM_STREAK_WINDOW_MS = 10L * 60_000L
    const val RECOVERY_WINDOW_MS = 10L * 60_000L
    const val SYSTEM_PRESSURE_COOLDOWN_MS = 30_000L

    private data class Thresholds(
        val guardedEnter: Int,
        val criticalEnter: Int,
        val guardedExit: Int,
        val criticalExit: Int,
    )

    private fun thresholds(heapMaxBytes: Long): Thresholds =
        if (heapMaxBytes in 1L..LOW_HEAP_MAX_BYTES) {
            Thresholds(
                guardedEnter = LOW_HEAP_GUARDED_ENTER_PERCENT,
                criticalEnter = LOW_HEAP_CRITICAL_ENTER_PERCENT,
                guardedExit = LOW_HEAP_GUARDED_EXIT_PERCENT,
                criticalExit = LOW_HEAP_CRITICAL_EXIT_PERCENT,
            )
        } else {
            Thresholds(
                guardedEnter = GUARDED_ENTER_PERCENT,
                criticalEnter = CRITICAL_ENTER_PERCENT,
                guardedExit = GUARDED_EXIT_PERCENT,
                criticalExit = CRITICAL_EXIT_PERCENT,
            )
        }

    fun sample(
        previous: PlaybackMemoryState,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        nowElapsedMs: Long,
    ): PlaybackMemoryState {
        val used = heapUsedBytes.coerceAtLeast(0L)
        val max = heapMaxBytes.coerceAtLeast(0L)
        val limits = thresholds(max)
        val percent = if (max == 0L) 0 else {
            ((used.coerceAtMost(max).toDouble() / max.toDouble()) * 100.0).toInt()
        }
        val recoveringFromCritical = previous.level == PlaybackMemoryLevel.CRITICAL
        val exitThreshold = if (recoveringFromCritical) limits.criticalExit else limits.guardedExit
        val belowExit = percent < exitThreshold
        val lowSince = when {
            previous.level !in setOf(PlaybackMemoryLevel.GUARDED, PlaybackMemoryLevel.CRITICAL) -> 0L
            !belowExit -> 0L
            previous.lowPressureSinceElapsedMs > 0L -> previous.lowPressureSinceElapsedMs
            else -> nowElapsedMs
        }
        val requiredHold = if (recoveringFromCritical) CRITICAL_RECOVERY_HOLD_MS else GUARDED_RECOVERY_HOLD_MS
        val recoveryHeld = lowSince > 0L && nowElapsedMs - lowSince >= requiredHold
        val level = when {
            nowElapsedMs < previous.circuitOpenUntilElapsedMs -> PlaybackMemoryLevel.CIRCUIT_OPEN
            // After the OOM cooldown, a frame with enough headroom for the deliberately
            // small RECOVERY decode must not remain permanently circuit-open. At or above
            // the heap-specific critical boundary we keep the circuit closed instead.
            previous.level == PlaybackMemoryLevel.CIRCUIT_OPEN &&
                percent >= limits.criticalEnter -> PlaybackMemoryLevel.CIRCUIT_OPEN
            percent >= limits.criticalEnter -> PlaybackMemoryLevel.CRITICAL
            nowElapsedMs < previous.recoveryUntilElapsedMs -> PlaybackMemoryLevel.RECOVERY
            previous.level == PlaybackMemoryLevel.CRITICAL && !recoveryHeld -> PlaybackMemoryLevel.CRITICAL
            previous.level == PlaybackMemoryLevel.CRITICAL -> PlaybackMemoryLevel.GUARDED
            percent >= limits.guardedEnter -> PlaybackMemoryLevel.GUARDED
            previous.level == PlaybackMemoryLevel.GUARDED && !recoveryHeld -> PlaybackMemoryLevel.GUARDED
            else -> PlaybackMemoryLevel.NORMAL
        }
        val recovered = level == PlaybackMemoryLevel.NORMAL &&
            nowElapsedMs >= previous.recoveryUntilElapsedMs &&
            percent < limits.guardedExit
        return previous.copy(
            level = level,
            heapUsedBytes = used,
            heapMaxBytes = max,
            pressurePercent = percent,
            lowPressureSinceElapsedMs = if (level == PlaybackMemoryLevel.NORMAL) 0L else lowSince,
            oomStreak = if (recovered) 0 else previous.oomStreak,
            decisionVersion = previous.decisionVersion + if (level != previous.level) 1L else 0L,
        )
    }

    fun decodeOom(previous: PlaybackMemoryState, nowElapsedMs: Long): PlaybackMemoryState {
        val withinStreak = previous.lastOomElapsedMs > 0L &&
            nowElapsedMs - previous.lastOomElapsedMs <= OOM_STREAK_WINDOW_MS
        val streak = if (withinStreak) (previous.oomStreak + 1).coerceAtMost(8) else 1
        val multiplier = 1L shl (streak - 1).coerceAtMost(3)
        val cooldown = (FIRST_OOM_COOLDOWN_MS * multiplier).coerceAtMost(MAX_OOM_COOLDOWN_MS)
        return previous.copy(
            level = PlaybackMemoryLevel.CIRCUIT_OPEN,
            circuitOpenUntilElapsedMs = nowElapsedMs + cooldown,
            recoveryUntilElapsedMs = maxOf(
                previous.recoveryUntilElapsedMs,
                nowElapsedMs + RECOVERY_WINDOW_MS,
            ),
            lastOomElapsedMs = nowElapsedMs,
            oomStreak = streak,
            totalOomCount = previous.totalOomCount + 1L,
            lowPressureSinceElapsedMs = 0L,
            decisionVersion = previous.decisionVersion + 1L,
        )
    }

    fun systemPressure(
        previous: PlaybackMemoryState,
        nowElapsedMs: Long,
        severe: Boolean,
    ): PlaybackMemoryState {
        // A weaker platform callback must never undo a stronger protection decision.
        // In particular, RUNNING_LOW can arrive after a decode OOM on API 21–25; letting
        // it replace CIRCUIT_OPEN with GUARDED would immediately re-enable selected
        // decoding before the cooldown and heap recheck complete.
        val nextLevel = when {
            severe -> PlaybackMemoryLevel.CIRCUIT_OPEN
            nowElapsedMs < previous.circuitOpenUntilElapsedMs -> PlaybackMemoryLevel.CIRCUIT_OPEN
            previous.level == PlaybackMemoryLevel.CIRCUIT_OPEN -> PlaybackMemoryLevel.CIRCUIT_OPEN
            previous.level == PlaybackMemoryLevel.CRITICAL -> PlaybackMemoryLevel.CRITICAL
            previous.level == PlaybackMemoryLevel.RECOVERY -> PlaybackMemoryLevel.RECOVERY
            else -> PlaybackMemoryLevel.GUARDED
        }
        return previous.copy(
            level = nextLevel,
            circuitOpenUntilElapsedMs = if (severe) {
                maxOf(previous.circuitOpenUntilElapsedMs, nowElapsedMs + SYSTEM_PRESSURE_COOLDOWN_MS)
            } else previous.circuitOpenUntilElapsedMs,
            recoveryUntilElapsedMs = maxOf(
                previous.recoveryUntilElapsedMs,
                nowElapsedMs + RECOVERY_WINDOW_MS,
            ),
            lowPressureSinceElapsedMs = 0L,
            decisionVersion = previous.decisionVersion + if (nextLevel != previous.level) 1L else 0L,
        )
    }
}
