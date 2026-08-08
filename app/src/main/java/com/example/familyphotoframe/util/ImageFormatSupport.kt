package com.example.familyphotoframe.util

import java.util.Locale

/**
 * Device-side decode capability policy.
 *
 * HEIC/HEIF remains indexable on every device so a library does not change merely because
 * the frame hardware changes. Playback is stricter: Android's platform HEIF decoder is not
 * available before Android 8.0 (API 26). On API 21-25 those entries are therefore
 * classified as unsupported before remote bytes are downloaded repeatedly.
 */
object ImageFormatSupport {
    const val PLATFORM_HEIF_MIN_SDK = 26

    private val heifExtensions = setOf("heic", "heif")
    private val heifMimeTypes = setOf(
        "image/heic",
        "image/heif",
        "image/heic-sequence",
        "image/heif-sequence",
    )

    fun extension(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun isHeif(fileName: String, mimeType: String?): Boolean =
        extension(fileName) in heifExtensions ||
            mimeType?.lowercase(Locale.ROOT) in heifMimeTypes

    /**
     * Conservative platform policy. A bundled decoder can replace this decision later;
     * until then, returning false is preferable to downloading and retrying a format that
     * is guaranteed not to decode on the reference Android 6 frame.
     */
    fun isPlatformDecodable(fileName: String, mimeType: String?, sdkInt: Int): Boolean =
        !isHeif(fileName, mimeType) || sdkInt >= PLATFORM_HEIF_MIN_SDK

    /** True when the platform image stack can decode HEIC/HEIF still images. */
    fun supportsPlatformHeif(sdkInt: Int): Boolean = sdkInt >= PLATFORM_HEIF_MIN_SDK

    /**
     * Stable eligibility input. A capability change must create a new shuffle scope so
     * reservations generated on a HEIF-capable device cannot be restored on a legacy one.
     */
    fun playbackCapabilitySignature(sdkInt: Int): String =
        if (supportsPlatformHeif(sdkInt)) "heif-platform" else "heif-excluded"
}
