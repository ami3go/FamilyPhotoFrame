package com.example.familyphotoframe.domain.engine

/** Playback degradation levels used to keep low-memory frames alive. */
enum class PlaybackMemoryLevel {
    NORMAL,
    GUARDED,
    CRITICAL,
    CIRCUIT_OPEN,
    RECOVERY,
}

/** The strongest signal currently responsible for the playback decision. */
enum class PlaybackMemoryPressureSource {
    NONE,
    JAVA_HEAP,
    PROCESS_PSS,
    SYSTEM_HEADROOM,
    SYSTEM_LOW_MEMORY,
    PLATFORM_CALLBACK,
    DECODE_OOM,
    RECOVERY,
}

/**
 * Immutable decision state for slideshow memory protection.
 *
 * The state deliberately contains only primitives and enums. It is safe to expose through a
 * StateFlow without retaining a decoded image or Android object.
 */
data class PlaybackMemoryState(
    val level: PlaybackMemoryLevel = PlaybackMemoryLevel.NORMAL,
    val heapUsedBytes: Long = 0L,
    val heapMaxBytes: Long = 0L,
    /** Java-heap pressure, retained under the original field name for schema compatibility. */
    val pressurePercent: Int = 0,
    val processPssBytes: Long = 0L,
    /** Base `ActivityManager.memoryClass`, not the optional large-heap growth limit. */
    val processMemoryBudgetBytes: Long = 0L,
    val processPressurePercent: Int = 0,
    val systemAvailBytes: Long = 0L,
    val systemThresholdBytes: Long = 0L,
    /** Available system memory as a percentage of Android's low-memory threshold. */
    val systemHeadroomPercent: Int = 0,
    val systemLowMemory: Boolean = false,
    /** Static startup profile. LOW devices remain economical even while [level] is NORMAL. */
    val lowMemoryTier: Boolean = false,
    val pressureSource: PlaybackMemoryPressureSource = PlaybackMemoryPressureSource.NONE,
    val externalPressureSource: PlaybackMemoryPressureSource = PlaybackMemoryPressureSource.NONE,
    val externalCriticalUntilElapsedMs: Long = 0L,
    val externalGuardedUntilElapsedMs: Long = 0L,
    val circuitOpenUntilElapsedMs: Long = 0L,
    val recoveryUntilElapsedMs: Long = 0L,
    val lastOomElapsedMs: Long = 0L,
    val oomStreak: Int = 0,
    val totalOomCount: Long = 0L,
    /** First sample continuously below the Java-heap exit threshold; zero otherwise. */
    val lowPressureSinceElapsedMs: Long = 0L,
    /** Changes only when a preparation decision changes, not on every memory sample. */
    val decisionVersion: Long = 0L,
) {
    /** Only a real decode OOM opens the decode circuit. */
    val allowSelectedDecode: Boolean get() = level != PlaybackMemoryLevel.CIRCUIT_OPEN
    val allowNextPreload: Boolean
        get() = level == PlaybackMemoryLevel.NORMAL && !lowMemoryTier
    val allowSoftFocus: Boolean
        get() = level == PlaybackMemoryLevel.NORMAL && !lowMemoryTier
    val allowBlurredBackdrop: Boolean
        get() = level == PlaybackMemoryLevel.NORMAL && !lowMemoryTier
    val allowWebPreview: Boolean
        get() = level == PlaybackMemoryLevel.NORMAL && !lowMemoryTier
    val forceSimpleTransition: Boolean
        get() = level != PlaybackMemoryLevel.NORMAL || lowMemoryTier

    /** Low-memory devices never prepare a three-photo frame, even before pressure appears. */
    val maxCollagePhotos: Int get() = when (level) {
        PlaybackMemoryLevel.NORMAL -> if (lowMemoryTier) 2 else 3
        PlaybackMemoryLevel.GUARDED -> 2
        PlaybackMemoryLevel.CRITICAL,
        PlaybackMemoryLevel.CIRCUIT_OPEN,
        PlaybackMemoryLevel.RECOVERY -> 1
    }

    /** Decode below physical display size while guarded; the GPU scales the result. */
    val decodeScale: Float get() = when (level) {
        PlaybackMemoryLevel.NORMAL -> if (lowMemoryTier) 0.82f else 1.0f
        PlaybackMemoryLevel.GUARDED -> 0.80f
        PlaybackMemoryLevel.CRITICAL -> 0.68f
        PlaybackMemoryLevel.CIRCUIT_OPEN -> 0.60f
        PlaybackMemoryLevel.RECOVERY -> 0.70f
    }

    fun circuitRemainingMs(nowElapsedMs: Long): Long =
        (circuitOpenUntilElapsedMs - nowElapsedMs).coerceAtLeast(0L)

    fun externalCriticalRemainingMs(nowElapsedMs: Long): Long =
        (externalCriticalUntilElapsedMs - nowElapsedMs).coerceAtLeast(0L)

    fun externalGuardedRemainingMs(nowElapsedMs: Long): Long =
        (externalGuardedUntilElapsedMs - nowElapsedMs).coerceAtLeast(0L)
}

/** Pure state machine behind [PlaybackMemoryGuard]. */
object PlaybackMemoryPolicy {
    const val GUARDED_ENTER_PERCENT = 85
    const val CRITICAL_ENTER_PERCENT = 92
    const val GUARDED_EXIT_PERCENT = 70
    const val CRITICAL_EXIT_PERCENT = 80

    /**
     * API-21-era devices commonly expose only a 96–128 MiB Java heap. Keep more absolute
     * headroom on those heaps while leaving modern devices on the established thresholds.
     */
    const val LOW_HEAP_MAX_BYTES = 128L * 1024L * 1024L
    const val LOW_HEAP_GUARDED_ENTER_PERCENT = 75
    const val LOW_HEAP_CRITICAL_ENTER_PERCENT = 85
    const val LOW_HEAP_GUARDED_EXIT_PERCENT = 62
    const val LOW_HEAP_CRITICAL_EXIT_PERCENT = 72

    /** PSS is compared with the ordinary memory class so `largeHeap` cannot hide process cost. */
    const val PROCESS_GUARDED_ENTER_PERCENT = 100
    const val PROCESS_CRITICAL_ENTER_PERCENT = 125

    /** System memory at/below the LMK threshold is critical; twice that threshold is guarded. */
    const val SYSTEM_CRITICAL_HEADROOM_PERCENT = 100
    const val SYSTEM_GUARDED_HEADROOM_PERCENT = 200

    const val GUARDED_RECOVERY_HOLD_MS = 3L * 60_000L
    const val CRITICAL_RECOVERY_HOLD_MS = 5L * 60_000L
    const val EXTERNAL_CRITICAL_HOLD_MS = 10L * 60_000L
    const val EXTERNAL_GUARDED_HOLD_MS = 15L * 60_000L
    const val FIRST_OOM_COOLDOWN_MS = 60_000L
    const val MAX_OOM_COOLDOWN_MS = 5L * 60_000L
    const val OOM_STREAK_WINDOW_MS = 10L * 60_000L
    const val RECOVERY_WINDOW_MS = 10L * 60_000L

    private data class Thresholds(
        val guardedEnter: Int,
        val criticalEnter: Int,
        val guardedExit: Int,
        val criticalExit: Int,
    )

    private enum class ExternalLevel { NONE, GUARDED, CRITICAL }

    private data class ExternalSignal(
        val level: ExternalLevel,
        val source: PlaybackMemoryPressureSource,
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

    /**
     * Samples Java heap and, when supplied, fresh process/system memory evidence.
     *
     * Nullable external arguments are intentional: ten-second heap checks must retain the latest
     * readings without treating a one-minute-old PSS value as a fresh signal and extending its
     * hold forever. [PlaybackMemoryGuard.recordMemory] supplies all four values only after a fresh
     * process sample.
     */
    fun sample(
        previous: PlaybackMemoryState,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        nowElapsedMs: Long,
        processPssBytes: Long? = null,
        systemAvailBytes: Long? = null,
        systemThresholdBytes: Long? = null,
        systemLowMemory: Boolean? = null,
    ): PlaybackMemoryState {
        val used = heapUsedBytes.coerceAtLeast(0L)
        val max = heapMaxBytes.coerceAtLeast(0L)
        val limits = thresholds(max)
        val percent = boundedPercent(used.coerceAtMost(max), max)
        val pss = processPssBytes?.coerceAtLeast(0L) ?: previous.processPssBytes
        val systemAvail = systemAvailBytes?.coerceAtLeast(0L) ?: previous.systemAvailBytes
        val systemThreshold =
            systemThresholdBytes?.coerceAtLeast(0L) ?: previous.systemThresholdBytes
        val lowMemory = systemLowMemory ?: previous.systemLowMemory
        val processPercent = boundedPercent(pss, previous.processMemoryBudgetBytes)
        val systemPercent = boundedPercent(systemAvail, systemThreshold)

        val externalRefreshed = processPssBytes != null || systemAvailBytes != null ||
            systemThresholdBytes != null || systemLowMemory != null
        val signal = if (externalRefreshed) {
            externalSignal(
                processPressurePercent = processPercent,
                processBudgetKnown = previous.processMemoryBudgetBytes > 0L,
                systemHeadroomPercent = systemPercent,
                systemThresholdKnown = systemThreshold > 0L,
                systemLowMemory = lowMemory,
            )
        } else {
            ExternalSignal(ExternalLevel.NONE, PlaybackMemoryPressureSource.NONE)
        }

        var externalCriticalUntil = previous.externalCriticalUntilElapsedMs
        var externalGuardedUntil = previous.externalGuardedUntilElapsedMs
        var externalSource = previous.externalPressureSource
        when (signal.level) {
            ExternalLevel.CRITICAL -> {
                externalCriticalUntil = maxOf(
                    externalCriticalUntil,
                    nowElapsedMs + EXTERNAL_CRITICAL_HOLD_MS,
                )
                externalGuardedUntil = maxOf(
                    externalGuardedUntil,
                    nowElapsedMs + EXTERNAL_GUARDED_HOLD_MS,
                )
                externalSource = signal.source
            }
            ExternalLevel.GUARDED -> {
                externalGuardedUntil = maxOf(
                    externalGuardedUntil,
                    nowElapsedMs + EXTERNAL_GUARDED_HOLD_MS,
                )
                if (nowElapsedMs >= externalCriticalUntil) externalSource = signal.source
            }
            ExternalLevel.NONE -> {
                if (nowElapsedMs >= externalCriticalUntil &&
                    nowElapsedMs >= externalGuardedUntil
                ) {
                    externalSource = PlaybackMemoryPressureSource.NONE
                }
            }
        }

        val externalCritical = nowElapsedMs < externalCriticalUntil
        val externalGuarded = nowElapsedMs < externalGuardedUntil
        val recoveringFromCritical = previous.level == PlaybackMemoryLevel.CRITICAL
        val exitThreshold = if (recoveringFromCritical) limits.criticalExit else limits.guardedExit
        val belowExit = percent < exitThreshold
        val lowSince = when {
            previous.level !in setOf(PlaybackMemoryLevel.GUARDED, PlaybackMemoryLevel.CRITICAL) -> 0L
            !belowExit -> 0L
            previous.lowPressureSinceElapsedMs > 0L -> previous.lowPressureSinceElapsedMs
            else -> nowElapsedMs
        }
        val requiredHold =
            if (recoveringFromCritical) CRITICAL_RECOVERY_HOLD_MS else GUARDED_RECOVERY_HOLD_MS
        val recoveryHeld = lowSince > 0L && nowElapsedMs - lowSince >= requiredHold

        val level = when {
            nowElapsedMs < previous.circuitOpenUntilElapsedMs -> PlaybackMemoryLevel.CIRCUIT_OPEN
            // Once a real OOM opens the circuit, do not retry while Java heap is still at its
            // critical boundary. External pressure alone never keeps selected decoding blocked.
            previous.level == PlaybackMemoryLevel.CIRCUIT_OPEN &&
                percent >= limits.criticalEnter -> PlaybackMemoryLevel.CIRCUIT_OPEN
            externalCritical -> PlaybackMemoryLevel.CRITICAL
            percent >= limits.criticalEnter -> PlaybackMemoryLevel.CRITICAL
            nowElapsedMs < previous.recoveryUntilElapsedMs -> PlaybackMemoryLevel.RECOVERY
            previous.level == PlaybackMemoryLevel.CRITICAL && !recoveryHeld ->
                PlaybackMemoryLevel.CRITICAL
            previous.level == PlaybackMemoryLevel.CRITICAL -> PlaybackMemoryLevel.GUARDED
            externalGuarded -> PlaybackMemoryLevel.GUARDED
            percent >= limits.guardedEnter -> PlaybackMemoryLevel.GUARDED
            previous.level == PlaybackMemoryLevel.GUARDED && !recoveryHeld ->
                PlaybackMemoryLevel.GUARDED
            else -> PlaybackMemoryLevel.NORMAL
        }
        val source = when {
            level == PlaybackMemoryLevel.CIRCUIT_OPEN -> PlaybackMemoryPressureSource.DECODE_OOM
            percent >= limits.criticalEnter -> PlaybackMemoryPressureSource.JAVA_HEAP
            externalCritical -> externalSource
            level == PlaybackMemoryLevel.RECOVERY -> PlaybackMemoryPressureSource.RECOVERY
            percent >= limits.guardedEnter -> PlaybackMemoryPressureSource.JAVA_HEAP
            externalGuarded -> externalSource
            level == PlaybackMemoryLevel.GUARDED || level == PlaybackMemoryLevel.CRITICAL ->
                PlaybackMemoryPressureSource.JAVA_HEAP
            else -> PlaybackMemoryPressureSource.NONE
        }
        val recovered = level == PlaybackMemoryLevel.NORMAL &&
            nowElapsedMs >= previous.recoveryUntilElapsedMs &&
            percent < limits.guardedExit
        return previous.copy(
            level = level,
            heapUsedBytes = used,
            heapMaxBytes = max,
            pressurePercent = percent,
            processPssBytes = pss,
            processPressurePercent = processPercent,
            systemAvailBytes = systemAvail,
            systemThresholdBytes = systemThreshold,
            systemHeadroomPercent = systemPercent,
            systemLowMemory = lowMemory,
            pressureSource = source,
            externalPressureSource = externalSource,
            externalCriticalUntilElapsedMs = externalCriticalUntil,
            externalGuardedUntilElapsedMs = externalGuardedUntil,
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
            pressureSource = PlaybackMemoryPressureSource.DECODE_OOM,
            circuitOpenUntilElapsedMs = nowElapsedMs + cooldown,
            recoveryUntilElapsedMs = maxOf(
                previous.recoveryUntilElapsedMs,
                nowElapsedMs + RECOVERY_WINDOW_MS,
            ),
            lastOomElapsedMs = nowElapsedMs,
            oomStreak = streak,
            totalOomCount = previous.totalOomCount + 1L,
            lowPressureSinceElapsedMs = 0L,
            decisionVersion = previous.decisionVersion +
                if (previous.level != PlaybackMemoryLevel.CIRCUIT_OPEN) 1L else 0L,
        )
    }

    fun systemPressure(
        previous: PlaybackMemoryState,
        nowElapsedMs: Long,
        severe: Boolean,
    ): PlaybackMemoryState {
        // Platform callbacks describe whole-device pressure, not proof that one selected decode
        // failed. Keep the current frame alive and reserve CIRCUIT_OPEN for a caught decode OOM.
        val signalled = previous.copy(
            externalCriticalUntilElapsedMs = if (severe) {
                maxOf(
                    previous.externalCriticalUntilElapsedMs,
                    nowElapsedMs + EXTERNAL_CRITICAL_HOLD_MS,
                )
            } else {
                previous.externalCriticalUntilElapsedMs
            },
            externalGuardedUntilElapsedMs = maxOf(
                previous.externalGuardedUntilElapsedMs,
                nowElapsedMs + EXTERNAL_GUARDED_HOLD_MS,
            ),
            externalPressureSource = PlaybackMemoryPressureSource.PLATFORM_CALLBACK,
        )
        return sample(
            previous = signalled,
            heapUsedBytes = previous.heapUsedBytes,
            heapMaxBytes = previous.heapMaxBytes,
            nowElapsedMs = nowElapsedMs,
        )
    }

    private fun externalSignal(
        processPressurePercent: Int,
        processBudgetKnown: Boolean,
        systemHeadroomPercent: Int,
        systemThresholdKnown: Boolean,
        systemLowMemory: Boolean,
    ): ExternalSignal = when {
        systemLowMemory -> ExternalSignal(
            ExternalLevel.CRITICAL,
            PlaybackMemoryPressureSource.SYSTEM_LOW_MEMORY,
        )
        systemThresholdKnown && systemHeadroomPercent <= SYSTEM_CRITICAL_HEADROOM_PERCENT ->
            ExternalSignal(
                ExternalLevel.CRITICAL,
                PlaybackMemoryPressureSource.SYSTEM_HEADROOM,
            )
        processBudgetKnown && processPressurePercent >= PROCESS_CRITICAL_ENTER_PERCENT ->
            ExternalSignal(
                ExternalLevel.CRITICAL,
                PlaybackMemoryPressureSource.PROCESS_PSS,
            )
        systemThresholdKnown && systemHeadroomPercent <= SYSTEM_GUARDED_HEADROOM_PERCENT ->
            ExternalSignal(
                ExternalLevel.GUARDED,
                PlaybackMemoryPressureSource.SYSTEM_HEADROOM,
            )
        processBudgetKnown && processPressurePercent >= PROCESS_GUARDED_ENTER_PERCENT ->
            ExternalSignal(
                ExternalLevel.GUARDED,
                PlaybackMemoryPressureSource.PROCESS_PSS,
            )
        else -> ExternalSignal(ExternalLevel.NONE, PlaybackMemoryPressureSource.NONE)
    }

    private fun boundedPercent(numerator: Long, denominator: Long): Int {
        if (denominator <= 0L) return 0
        return ((numerator.coerceAtLeast(0L).toDouble() / denominator.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, MAX_REPORTED_PERCENT)
    }

    private const val MAX_REPORTED_PERCENT = 999
}
