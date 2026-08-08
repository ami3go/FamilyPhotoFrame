package com.example.familyphotoframe.data.db

/** Lightweight projection used to build canonical folder/photo eligibility snapshots. */
data class ShufflePhotoRow(
    val id: Long,
    val stableId: String,
    val sourceId: String,
    val normalizedPath: String,
    val canonicalDirectory: String,
    val contentSha256: String?,
    val sizeBytes: Long,
    val fileModifiedEpochMs: Long,
    val width: Int?,
    val height: Int?,
    val exifOrientation: Int,
    val decodeFailureCount: Int,
)


/** Lightweight direct-folder projection; no photo rows are loaded for other folders. */
data class ShuffleFolderRow(
    val sourceId: String,
    val canonicalDirectory: String,
    val memberCount: Int,
)
