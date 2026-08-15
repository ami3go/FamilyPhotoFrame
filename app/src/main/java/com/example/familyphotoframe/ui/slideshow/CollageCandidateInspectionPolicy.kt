package com.example.familyphotoframe.ui.slideshow

internal enum class CollageCandidateInspectionAction {
    USE_METADATA,
    PROBE_LOCAL,
    QUEUE_REMOTE_BOUNDED_PROBE,
    SKIP_LOCAL_PROBE_BUDGET,
}

/** Pure bounded-I/O policy for optimizer candidate inspection. */
internal object CollageCandidateInspectionPolicy {
    fun decide(
        hasMetadata: Boolean,
        needsRemoteCache: Boolean,
        localProbesUsed: Int,
        maxLocalProbes: Int,
    ): CollageCandidateInspectionAction = when {
        hasMetadata -> CollageCandidateInspectionAction.USE_METADATA
        needsRemoteCache -> CollageCandidateInspectionAction.QUEUE_REMOTE_BOUNDED_PROBE
        localProbesUsed >= maxLocalProbes -> CollageCandidateInspectionAction.SKIP_LOCAL_PROBE_BUDGET
        else -> CollageCandidateInspectionAction.PROBE_LOCAL
    }
}
