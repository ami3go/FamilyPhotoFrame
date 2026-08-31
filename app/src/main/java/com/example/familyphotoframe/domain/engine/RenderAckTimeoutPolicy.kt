package com.example.familyphotoframe.domain.engine

/**
 * Maps the furthest renderer hand-off stage reached by a selected slide to a bounded,
 * actionable timeout reason. The engine keeps playing after a lost acknowledgement, but
 * the reason lets HIL diagnostics distinguish a missing Compose launch from a stalled
 * preparation or transition.
 */
internal object RenderAckTimeoutPolicy {
    const val SELECTED = "SELECTED"

    /**
     * Keep this above the selected cache-transfer budget and below the engine's final
     * render-ack recovery. Slow but advancing SMB2 reads get a usable bounded window,
     * while a wedged preparation is still abandoned after one minute.
     */
    const val PREPARATION_WATCHDOG_TIMEOUT_MS = 60_000L

    /** Last-resort guard for the entire UI hand-off, including transition commit. */
    const val FINAL_RENDER_ACK_TIMEOUT_MS = 70_000L

    fun shouldRecoverPreparation(lastStage: String?, preparationSubstage: String? = null): Boolean = when (lastStage) {
        null, SELECTED -> true
        "PREPARE_STARTED" -> preparationSubstage != "PREPARATION_READY"
        else -> false
    }

    /**
     * A Compose preparation can be cancelled by a display-configuration recreation while
     * its selected photo is still current.  It is neither a decode error nor a terminal
     * render state, so the engine should advance immediately rather than wait for the
     * broader render-ack timeout.
     */
    fun shouldRecoverCancelledPreparation(lastStage: String?): Boolean = when (lastStage) {
        "RENDERED", "PREPARE_FAILED", "NATIVE_HIL_INSTANT_COMMIT" -> false
        else -> true
    }

    fun reasonFor(lastStage: String?): String = when (lastStage) {
        null, SELECTED -> "PREPARATION_NOT_STARTED"
        "PREPARE_STARTED" -> "PREPARATION_STALLED"
        "ENGINE_COMMITTED", "PREPARED", "TRANSITION_SELECTED" -> "TRANSITION_NOT_STARTED"
        "TRANSITION_STARTED" -> "TRANSITION_STALLED"
        "TRANSITION_COMPLETED", "RENDERED", "NATIVE_HIL_INSTANT_COMMIT" -> "RENDER_CALLBACK_NOT_DELIVERED"
        "PREPARE_FAILED" -> "FAILURE_NOT_DELIVERED"
        "PREPARE_CANCELLED", "PREPARE_STALE", "ENGINE_COMMIT_REJECTED", "PREPARED_COMMIT_REJECTED",
        "PREPARED_SLIDE_MISSING", "TRANSITION_CANCELLED" -> "PRESENTATION_ABORTED"
        else -> "UNKNOWN_PRESENTATION_STAGE"
    }
}
