package com.example.familyphotoframe.ui.slideshow.transition

/** What a cancelled animation may do with the target it was rendering. */
internal enum class TransitionCancellationAction {
    COMMIT_TARGET,
    KEEP_OUTGOING,
}

internal data class TransitionCancellationDecision(
    val action: TransitionCancellationAction,
    val reason: String?,
)

/** Pure policy so a superseded transition can never commit an obsolete presentation. */
internal object TransitionCancellationPolicy {
    fun decide(
        completed: Boolean,
        targetStillDesired: Boolean,
        paused: Boolean,
    ): TransitionCancellationDecision = when {
        completed -> TransitionCancellationDecision(
            TransitionCancellationAction.COMMIT_TARGET,
            null,
        )
        !targetStillDesired -> TransitionCancellationDecision(
            TransitionCancellationAction.KEEP_OUTGOING,
            "superseded",
        )
        paused -> TransitionCancellationDecision(
            TransitionCancellationAction.COMMIT_TARGET,
            "paused",
        )
        else -> TransitionCancellationDecision(
            TransitionCancellationAction.COMMIT_TARGET,
            "reconfigured",
        )
    }
}
