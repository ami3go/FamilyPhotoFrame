package com.example.familyphotoframe.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Disk cache index for decoded-and-downscaled thumbnails of LOCAL (SAF/fallback) photos.
 *
 * This is a distinct concern from [CacheIndexEntity]/`MediaCache`: that cache exists to
 * avoid re-fetching original bytes over a network for remote sources. Local photos are
 * already on local storage, so re-fetching is never the cost — the repeated cost is CPU
 * decode/downscale time. [cacheKey] is `photoStableId + sizeBucket` (not just
 * `photoStableId`) because the same photo is legitimately decoded at different target
 * dimensions (a full-frame slide vs. a portrait-collage tile vs. a memory-pressure
 * decode-scaled size), and each needs its own cached thumbnail.
 */
@Entity(
    tableName = "local_thumbnail_cache",
    indices = [Index(value = ["photoStableId"])],
)
data class LocalThumbnailCacheEntity(
    @PrimaryKey val cacheKey: String,
    val photoStableId: String,
    val sizeBucket: String,
    val localFilePathPrivate: String,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
    val lastAccessedAtEpochMs: Long,
)
