package com.example.familyphotoframe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalThumbnailCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LocalThumbnailCacheEntity)

    @Query("SELECT * FROM local_thumbnail_cache WHERE cacheKey = :key")
    suspend fun get(key: String): LocalThumbnailCacheEntity?

    /** Keyset page used for bounded startup reconciliation with private files. */
    @Query(
        """
        SELECT * FROM local_thumbnail_cache
        WHERE cacheKey > :afterKey
        ORDER BY cacheKey ASC
        LIMIT :limit
        """
    )
    suspend fun reconciliationPage(afterKey: String, limit: Int): List<LocalThumbnailCacheEntity>

    @Query("UPDATE local_thumbnail_cache SET lastAccessedAtEpochMs = :ts WHERE cacheKey = :key")
    suspend fun touch(key: String, ts: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM local_thumbnail_cache")
    suspend fun totalSizeBytes(): Long

    /**
     * Eviction candidates, least-recently-accessed first. Unlike `CacheIndexDao`'s
     * version, protection is filtered by the caller (by `photoStableId`, since one photo
     * can have several cache entries — one per decode size bucket — and the caller only
     * knows which *photos*, not which exact size-bucketed entries, are protected).
     */
    @Query(
        "SELECT * FROM local_thumbnail_cache WHERE photoStableId NOT IN (:protectedStableIds) " +
            "ORDER BY lastAccessedAtEpochMs ASC LIMIT :limit"
    )
    suspend fun evictionCandidates(
        protectedStableIds: List<String>,
        limit: Int,
    ): List<LocalThumbnailCacheEntity>

    @Query("DELETE FROM local_thumbnail_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM local_thumbnail_cache")
    suspend fun clear()
}
