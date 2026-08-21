package com.example.familyphotoframe.domain.engine

/** What a failed presentation should persist against the photo it was showing. */
enum class DecodeSuppressionOutcome {
    /** Nothing is written: the failure was not this photo's fault. */
    NONE,

    /** One more strike towards `temporarilySuppressAfterDecodeFailures`. */
    COUNT,

    /** This device cannot decode the format at all; suppress without retrying N times. */
    PERMANENT,
}

/**
 * Decides whether a decode failure counts against the photo.
 *
 * Pure so the rule is testable and stated once — the engine performs only the resulting
 * write. The distinction that matters is whether the failure describes *the photo* or
 * *its surroundings*: a corrupt file is evidence about the file, whereas an unplugged NAS,
 * an expired session or an exhausted heap are evidence about the moment.
 *
 * Getting this wrong is not cosmetic. Every playback query filters on
 * `decodeFailureCount < maxFailures` (default three), a rescan preserves the count for
 * unchanged files, and the only automatic reset is a recovery promotion that happens
 * in-process. So charging photos for a source outage could suppress an entire library
 * within minutes and leave the frame showing nothing long after the share came back — with
 * a restart in between, permanently.
 */
object DecodeSuppressionPolicy {

    fun outcomeFor(failure: DecodeFailure): DecodeSuppressionOutcome = when {
        // Connection, auth or session failure: says nothing about this photo.
        failure.sourceLevelFailure -> DecodeSuppressionOutcome.NONE
        // Heap exhaustion is a process condition. The caller normally intercepts this
        // earlier and retries the same photo; the rule is repeated here so a path that
        // does not intercept still cannot poison the library.
        failure.exceptionClass == OUT_OF_MEMORY -> DecodeSuppressionOutcome.NONE
        failure.permanent -> DecodeSuppressionOutcome.PERMANENT
        else -> DecodeSuppressionOutcome.COUNT
    }

    private const val OUT_OF_MEMORY = "OutOfMemoryError"
}
