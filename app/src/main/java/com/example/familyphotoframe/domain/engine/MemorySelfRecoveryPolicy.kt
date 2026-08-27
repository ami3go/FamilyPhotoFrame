package com.example.familyphotoframe.domain.engine

/** One-shot actions for legacy-device recovery after a real decode OOM. */
enum class MemorySelfRecoveryAction { NONE, REQUEST_GC, RESTART_PROCESS }

enum class MemorySelfRecoveryTrigger {
    NONE,
    DECODE_OOM_JAVA_HEAP,
    SUSTAINED_NATIVE_PROCESS_PRESSURE,
}

/**
 * Tiny immutable watchdog state.  It deliberately contains no Android object so the
 * recovery timing can be executed in the offline JVM gate.
 */
data class MemorySelfRecoveryState(
    val trigger: MemorySelfRecoveryTrigger = MemorySelfRecoveryTrigger.NONE,
    val highPressureSinceElapsedMs: Long = -1L,
    val gcRequestedAtElapsedMs: Long = -1L,
    /** Survives completion/reset of one recovery attempt so GC cannot loop across OOMs. */
    val lastGcRequestedAtElapsedMs: Long = -1L,
    val restartIssued: Boolean = false,
)

data class MemorySelfRecoveryDecision(
    val state: MemorySelfRecoveryState,
    val action: MemorySelfRecoveryAction,
    val trigger: MemorySelfRecoveryTrigger = MemorySelfRecoveryTrigger.NONE,
)

/**
 * Last-resort process recovery for the API-21..25 failure class seen in the field.
 *
 * A circuit-open state by itself is not enough: a normal cooldown must remain invisible.
 * Recovery is considered only after a *real decode OOM* and sustained >=92% Java-heap
 * pressure. The first eligible action requests one globally rate-limited GC; a restart is
 * allowed only if a later sample proves that the process is still critically full.
 */
object MemorySelfRecoveryPolicy {
    const val RESTART_PRESSURE_PERCENT = 92
    const val SUSTAINED_PRESSURE_BEFORE_GC_MS = 60_000L
    const val POST_GC_VERIFY_MS = 10_000L
    const val OOM_ELIGIBILITY_WINDOW_MS = 10L * 60_000L
    const val GC_MIN_INTERVAL_MS = 10L * 60_000L
    const val NATIVE_RESTART_PROCESS_PERCENT = 120
    const val NATIVE_SUSTAINED_PRESSURE_BEFORE_GC_MS = 30L * 60_000L
    const val NATIVE_POST_GC_VERIFY_MS = 2L * 60_000L

    fun evaluate(
        previous: MemorySelfRecoveryState,
        memoryLevel: PlaybackMemoryLevel,
        oomCount: Long,
        lastOomElapsedMs: Long,
        pressurePercent: Int,
        nowElapsedMs: Long,
        processPressurePercent: Int = 0,
        nativeGrowthStreak: Int = 0,
        nativeCriticalLatched: Boolean = false,
    ): MemorySelfRecoveryDecision {
        val now = nowElapsedMs.coerceAtLeast(0L)
        val oomAgeMs = if (lastOomElapsedMs > 0L) now - lastOomElapsedMs else Long.MAX_VALUE
        val decodeOomEligible = memoryLevel == PlaybackMemoryLevel.CIRCUIT_OPEN &&
            oomCount > 0L && oomAgeMs in 0L..OOM_ELIGIBILITY_WINDOW_MS &&
            pressurePercent >= RESTART_PRESSURE_PERCENT
        val nativePressureEligible = memoryLevel == PlaybackMemoryLevel.CRITICAL &&
            nativeCriticalLatched &&
            nativeGrowthStreak >= PlaybackMemoryPolicy.NATIVE_GROWTH_CRITICAL_STREAK &&
            processPressurePercent >= NATIVE_RESTART_PROCESS_PERCENT
        val trigger = when {
            decodeOomEligible -> MemorySelfRecoveryTrigger.DECODE_OOM_JAVA_HEAP
            nativePressureEligible -> MemorySelfRecoveryTrigger.SUSTAINED_NATIVE_PROCESS_PRESSURE
            else -> MemorySelfRecoveryTrigger.NONE
        }
        if (trigger == MemorySelfRecoveryTrigger.NONE) {
            return MemorySelfRecoveryDecision(
                MemorySelfRecoveryState(
                    lastGcRequestedAtElapsedMs = previous.lastGcRequestedAtElapsedMs,
                ),
                MemorySelfRecoveryAction.NONE,
                MemorySelfRecoveryTrigger.NONE,
            )
        }

        if (previous.trigger != trigger) {
            return MemorySelfRecoveryDecision(
                MemorySelfRecoveryState(
                    trigger = trigger,
                    highPressureSinceElapsedMs = now,
                    lastGcRequestedAtElapsedMs = previous.lastGcRequestedAtElapsedMs,
                ),
                MemorySelfRecoveryAction.NONE,
                trigger,
            )
        }

        if (previous.restartIssued) {
            return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE, trigger)
        }
        if (previous.highPressureSinceElapsedMs < 0L || now < previous.highPressureSinceElapsedMs) {
            return MemorySelfRecoveryDecision(
                MemorySelfRecoveryState(
                    trigger = trigger,
                    highPressureSinceElapsedMs = now,
                    lastGcRequestedAtElapsedMs = previous.lastGcRequestedAtElapsedMs,
                ),
                MemorySelfRecoveryAction.NONE,
                trigger,
            )
        }
        val sustainedBeforeGcMs = when (trigger) {
            MemorySelfRecoveryTrigger.DECODE_OOM_JAVA_HEAP -> SUSTAINED_PRESSURE_BEFORE_GC_MS
            MemorySelfRecoveryTrigger.SUSTAINED_NATIVE_PROCESS_PRESSURE ->
                NATIVE_SUSTAINED_PRESSURE_BEFORE_GC_MS
            MemorySelfRecoveryTrigger.NONE -> Long.MAX_VALUE
        }
        if (previous.gcRequestedAtElapsedMs < 0L) {
            if (now - previous.highPressureSinceElapsedMs < sustainedBeforeGcMs) {
                return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE, trigger)
            }
            val lastGc = previous.lastGcRequestedAtElapsedMs
            val gcRateLimited = lastGc >= 0L &&
                (now < lastGc || now - lastGc < GC_MIN_INTERVAL_MS)
            if (gcRateLimited) {
                return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE, trigger)
            }
            return MemorySelfRecoveryDecision(
                previous.copy(
                    gcRequestedAtElapsedMs = now,
                    lastGcRequestedAtElapsedMs = now,
                ),
                MemorySelfRecoveryAction.REQUEST_GC,
                trigger,
            )
        }
        val postGcVerifyMs = when (trigger) {
            MemorySelfRecoveryTrigger.DECODE_OOM_JAVA_HEAP -> POST_GC_VERIFY_MS
            MemorySelfRecoveryTrigger.SUSTAINED_NATIVE_PROCESS_PRESSURE -> NATIVE_POST_GC_VERIFY_MS
            MemorySelfRecoveryTrigger.NONE -> Long.MAX_VALUE
        }
        if (now < previous.gcRequestedAtElapsedMs ||
            now - previous.gcRequestedAtElapsedMs < postGcVerifyMs
        ) {
            return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE, trigger)
        }
        return MemorySelfRecoveryDecision(
            previous.copy(restartIssued = true),
            MemorySelfRecoveryAction.RESTART_PROCESS,
            trigger,
        )
    }
}
