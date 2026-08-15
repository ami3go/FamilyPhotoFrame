package com.example.familyphotoframe.data.diagnostics

import kotlin.math.abs

enum class ProcessStartKind {
    FIRST_OBSERVATION,
    PROCESS_RESTART_SAME_BOOT,
    DEVICE_REBOOT,
}

data class ProcessStartClassification(
    val kind: ProcessStartKind,
    val estimatedBootEpochMs: Long,
    val bootEpochDeltaMs: Long?,
)

/** Pure API-21-compatible reboot/process-restart classifier. */
object ProcessStartClassifier {
    const val DEFAULT_BOOT_EPOCH_TOLERANCE_MS = 5L * 60_000L
    const val ELAPSED_RESET_TOLERANCE_MS = 5_000L

    fun classify(
        previousElapsedRealtimeMs: Long?,
        previousEstimatedBootEpochMs: Long?,
        currentWallClockMs: Long,
        currentElapsedRealtimeMs: Long,
        bootEpochToleranceMs: Long = DEFAULT_BOOT_EPOCH_TOLERANCE_MS,
    ): ProcessStartClassification {
        val estimatedBoot = currentWallClockMs - currentElapsedRealtimeMs.coerceAtLeast(0L)
        if (previousElapsedRealtimeMs == null || previousEstimatedBootEpochMs == null) {
            return ProcessStartClassification(ProcessStartKind.FIRST_OBSERVATION, estimatedBoot, null)
        }
        val bootDelta = abs(estimatedBoot - previousEstimatedBootEpochMs)
        val elapsedReset = currentElapsedRealtimeMs + ELAPSED_RESET_TOLERANCE_MS < previousElapsedRealtimeMs
        val reboot = elapsedReset || bootDelta > bootEpochToleranceMs.coerceAtLeast(0L)
        return ProcessStartClassification(
            kind = if (reboot) ProcessStartKind.DEVICE_REBOOT else ProcessStartKind.PROCESS_RESTART_SAME_BOOT,
            estimatedBootEpochMs = estimatedBoot,
            bootEpochDeltaMs = bootDelta,
        )
    }
}
