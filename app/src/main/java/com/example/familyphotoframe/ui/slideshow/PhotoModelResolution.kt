package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.domain.engine.DecodeFailure

/** Result of resolving an indexed photo into a model Coil can decode. */
sealed interface PhotoModelResolution {
    /**
     * @param localThumbnailCacheEligible True only for local (SAF/fallback/local-upload)
     * items — never for remote items already served from `MediaCache`, which are out of
     * scope for the local thumbnail cache (docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md §3).
     */
    data class Ready(val model: Any, val localThumbnailCacheEligible: Boolean = false) : PhotoModelResolution
    data class Failed(val failure: DecodeFailure) : PhotoModelResolution
}
