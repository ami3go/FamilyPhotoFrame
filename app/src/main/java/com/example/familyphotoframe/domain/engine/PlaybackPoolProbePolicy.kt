package com.example.familyphotoframe.domain.engine

/**
 * Decides whether selection may enter the primary playback pool.
 *
 * An unavailable source is retained separately for folder-progress reconciliation, but it
 * is not an active primary. Probing an empty primary pool lets folder-balanced selection
 * restore stale history before falling through to samples; because the fallback has its
 * own scope, repeated probes then alternate that stale photo with a bundled image.
 *
 * Stale-cache playback remains a primary pool: the ViewModel configures the remote source
 * ID explicitly when cached rows are available, so it passes this gate.
 */
internal object PlaybackPoolProbePolicy {
    fun shouldProbePrimary(configuredPrimarySourceCount: Int): Boolean =
        configuredPrimarySourceCount > 0
}
