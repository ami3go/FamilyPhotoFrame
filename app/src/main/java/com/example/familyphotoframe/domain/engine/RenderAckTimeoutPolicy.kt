package com.example.familyphotoframe.domain.engine

/**
 * Maps the furthest renderer hand-off stage reached by a selected slide to a bounded,
 * actionable timeout reason. The engine keeps playing after a lost acknowledgement, but
 * the reason lets HIL diagnostics distinguish a missing Compose launch from a stalled
 * preparation or transition.
 */
internal object RenderAckTimeoutPolicy {
    const val SELECTED = "SELECTED"

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
