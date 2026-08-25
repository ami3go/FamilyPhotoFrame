package com.example.familyphotoframe.domain.engine

/** One-shot actions for legacy-device recovery after a real decode OOM. */
enum class MemorySelfRecoveryAction { NONE, REQUEST_GC, RESTART_PROCESS }

/**
 * Tiny immutable watchdog state.  It deliberately contains no Android object so the
 * recovery timing can be executed in the offline JVM gate.
 */
data class MemorySelfRecoveryState(
    val highPressureSinceElapsedMs: Long = -1L,
    val gcRequestedAtElapsedMs: Long = -1L,
    /** Survives completion/reset of one recovery attempt so GC cannot loop across OOMs. */
    val lastGcRequestedAtElapsedMs: Long = -1L,
    val restartIssued: Boolean = false,
)

data class MemorySelfRecoveryDecision(
    val state: MemorySelfRecoveryState,
    val action: MemorySelfRecoveryAction,
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

    fun evaluate(
        previous: MemorySelfRecoveryState,
        memoryLevel: PlaybackMemoryLevel,
        oomCount: Long,
        lastOomElapsedMs: Long,
        pressurePercent: Int,
        nowElapsedMs: Long,
    ): MemorySelfRecoveryDecision {
        val now = nowElapsedMs.coerceAtLeast(0L)
        val oomAgeMs = if (lastOomElapsedMs > 0L) now - lastOomElapsedMs else Long.MAX_VALUE
        val eligible = memoryLevel == PlaybackMemoryLevel.CIRCUIT_OPEN &&
            oomCount > 0L && oomAgeMs in 0L..OOM_ELIGIBILITY_WINDOW_MS &&
            pressurePercent >= RESTART_PRESSURE_PERCENT
        if (!eligible) {
            return MemorySelfRecoveryDecision(
                MemorySelfRecoveryState(
                    lastGcRequestedAtElapsedMs = previous.lastGcRequestedAtElapsedMs,
                ),
                MemorySelfRecoveryAction.NONE,
            )
        }

        if (previous.restartIssued) {
            return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE)
        }
        if (previous.highPressureSinceElapsedMs < 0L || now < previous.highPressureSinceElapsedMs) {
            return MemorySelfRecoveryDecision(
                MemorySelfRecoveryState(
                    highPressureSinceElapsedMs = now,
                    lastGcRequestedAtElapsedMs = previous.lastGcRequestedAtElapsedMs,
                ),
                MemorySelfRecoveryAction.NONE,
            )
        }
        if (previous.gcRequestedAtElapsedMs < 0L) {
            if (now - previous.highPressureSinceElapsedMs < SUSTAINED_PRESSURE_BEFORE_GC_MS) {
                return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE)
            }
            val lastGc = previous.lastGcRequestedAtElapsedMs
            val gcRateLimited = lastGc >= 0L &&
                (now < lastGc || now - lastGc < GC_MIN_INTERVAL_MS)
            if (gcRateLimited) {
                return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE)
            }
            return MemorySelfRecoveryDecision(
                previous.copy(
                    gcRequestedAtElapsedMs = now,
                    lastGcRequestedAtElapsedMs = now,
                ),
                MemorySelfRecoveryAction.REQUEST_GC,
            )
        }
        if (now < previous.gcRequestedAtElapsedMs ||
            now - previous.gcRequestedAtElapsedMs < POST_GC_VERIFY_MS
        ) {
            return MemorySelfRecoveryDecision(previous, MemorySelfRecoveryAction.NONE)
        }
        return MemorySelfRecoveryDecision(
            previous.copy(restartIssued = true),
            MemorySelfRecoveryAction.RESTART_PROCESS,
        )
    }
}
