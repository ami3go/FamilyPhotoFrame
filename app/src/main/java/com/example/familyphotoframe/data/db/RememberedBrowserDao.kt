package com.example.familyphotoframe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RememberedBrowserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(record: RememberedBrowserEntity)

    @Query("SELECT * FROM remembered_browsers WHERE id = :id")
    suspend fun get(id: String): RememberedBrowserEntity?

    @Query("SELECT * FROM remembered_browsers ORDER BY createdAtEpochMs DESC")
    suspend fun all(): List<RememberedBrowserEntity>

    @Query(
        "SELECT COUNT(*) FROM remembered_browsers " +
            "WHERE revokedAtEpochMs IS NULL AND (expiresAtEpochMs IS NULL OR expiresAtEpochMs > :now)"
    )
    suspend fun activeCount(now: Long): Int

    @Query("UPDATE remembered_browsers SET revokedAtEpochMs = :now WHERE id = :id AND revokedAtEpochMs IS NULL")
    suspend fun revoke(id: String, now: Long): Int

    @Query("UPDATE remembered_browsers SET revokedAtEpochMs = :now WHERE id != :keepId AND revokedAtEpochMs IS NULL")
    suspend fun revokeAllExcept(keepId: String, now: Long): Int

    @Query("UPDATE remembered_browsers SET revokedAtEpochMs = :now WHERE revokedAtEpochMs IS NULL")
    suspend fun revokeAll(now: Long): Int

    @Query(
        "DELETE FROM remembered_browsers WHERE " +
            "(expiresAtEpochMs IS NOT NULL AND expiresAtEpochMs < :expiredBefore) OR " +
            "(revokedAtEpochMs IS NOT NULL AND revokedAtEpochMs < :expiredBefore)"
    )
    suspend fun purgeOld(expiredBefore: Long): Int

    @Query("DELETE FROM remembered_browsers")
    suspend fun deleteAll()
}
