package com.example.familyphotoframe.util

import java.security.MessageDigest
import java.util.Locale

/**
 * Stable identity for an indexed photo (spec §6.1):
 *   stableId = hash(sourceId + normalizedPath + sizeBytes + lastModifiedAt)
 *
 * Used as the cache key seed and to detect "same logical file" across rescans so
 * a rescan updates rather than duplicates rows.
 */
object StableId {
    fun of(sourceId: String, normalizedPath: String, sizeBytes: Long, lastModifiedEpochMs: Long): String {
        val seed = "$sourceId|$normalizedPath|$sizeBytes|$lastModifiedEpochMs"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.toHexString().substring(0, 32)
    }
}

/**
 * Photo formats accepted by every index source.
 *
 * HEIC/HEIF are included because they are the default high-efficiency still-image
 * formats produced by modern iPhones. Actual decoding remains dependent on the
 * Android device's image decoder; unsupported devices fail safely at render time.
 */
object SupportedFormats {
    val defaultIncludeGlobs: List<String> =
        listOf("*.jpg", "*.jpeg", "*.png", "*.webp", "*.heic", "*.heif")

    private val extensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
    private val mimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
        "image/heic-sequence",
        "image/heif-sequence",
    )

    fun isSupportedExtension(fileName: String): Boolean {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return false
        return fileName.substring(dot + 1).lowercase(Locale.ROOT) in extensions
    }

    fun isSupportedMime(mime: String?): Boolean {
        if (mime == null) return false
        return mime.lowercase(Locale.ROOT) in mimeTypes
    }

    /**
     * A provider sometimes returns a null/garbage MIME (spec §7.11). Treat the item
     * as supported if EITHER the MIME or the extension says so.
     */
    fun isSupported(fileName: String, mime: String?): Boolean =
        isSupportedMime(mime) || isSupportedExtension(fileName)
}
