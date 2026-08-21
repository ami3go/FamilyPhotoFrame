package com.example.familyphotoframe.domain.engine

/**
 * Failure count marking a photo this device cannot decode at all, as opposed to one that
 * merely failed a few times.
 *
 * Deliberately far above any `temporarilySuppressAfterDecodeFailures` threshold so the two
 * cases stay distinguishable in the same column: transient suppression is expired after a
 * while, permanent suppression never is. Shared rather than engine-private because the
 * expiry path has to recognise the sentinel to leave it alone.
 */
const val PERMANENT_DECODE_FAILURE_COUNT = 1_000_000

/** Stage at which a selected photo failed to become visible. */
enum class DecodeFailureStage {
    CAPABILITY,
    CACHE_LOOKUP,
    SOURCE_READ,
    VERIFY_DECODE,
    CACHE_COMMIT,
    IMAGE_LOADER,
    UNKNOWN,
}

/**
 * Privacy-safe decode failure description. File names and paths are deliberately not
 * logged; [fileExtension] is sufficient to identify format-specific failures.
 */
data class DecodeFailure(
    val photoId: Long,
    val sourceId: String,
    val fileExtension: String,
    val mimeType: String? = null,
    val stage: DecodeFailureStage,
    val exceptionClass: String? = null,
    val permanent: Boolean = false,
    val reason: String? = null,
    /** Connection/auth/session failure affecting the source rather than one folder/photo. */
    val sourceLevelFailure: Boolean = false,
)
