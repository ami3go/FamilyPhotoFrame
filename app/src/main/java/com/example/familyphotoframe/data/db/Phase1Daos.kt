package com.example.familyphotoframe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: SourceConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSmb(config: SmbSourceConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSaf(config: LocalSafSourceConfigEntity)

    @Query("SELECT * FROM source_config WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun enabledSources(): List<SourceConfigEntity>

    @Query("SELECT * FROM source_config")
    fun allSourcesFlow(): Flow<List<SourceConfigEntity>>

    @Query("SELECT * FROM source_config WHERE id = :id")
    suspend fun sourceById(id: String): SourceConfigEntity?

    @Query("SELECT * FROM smb_source_config WHERE sourceId = :sourceId")
    suspend fun smbConfig(sourceId: String): SmbSourceConfigEntity?

    @Query("SELECT * FROM local_saf_source_config WHERE sourceId = :sourceId")
    suspend fun safConfig(sourceId: String): LocalSafSourceConfigEntity?

    @Query("UPDATE local_saf_source_config SET permissionState = :state WHERE sourceId = :sourceId")
    suspend fun updateSafPermissionState(sourceId: String, state: String)

    @Query("DELETE FROM source_config WHERE id = :id")
    suspend fun deleteSource(id: String)

    @Query("DELETE FROM smb_source_config WHERE sourceId = :id")
    suspend fun deleteSmb(id: String)

    @Query("DELETE FROM local_saf_source_config WHERE sourceId = :id")
    suspend fun deleteSaf(id: String)
}

@Dao
interface SecretDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(secret: SecretEntity)

    @Query("SELECT * FROM secrets WHERE credentialRef = :ref")
    suspend fun get(ref: String): SecretEntity?

    @Query("DELETE FROM secrets WHERE credentialRef = :ref")
    suspend fun delete(ref: String)
}

@Dao
interface CacheIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheIndexEntity)

    @Query("SELECT * FROM cache_index WHERE cacheKey = :key")
    suspend fun get(key: String): CacheIndexEntity?

    @Query("UPDATE cache_index SET lastAccessedAtEpochMs = :ts WHERE cacheKey = :key")
    suspend fun touch(key: String, ts: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cache_index")
    suspend fun totalSizeBytes(): Long

    /** Eviction candidates: least-recently-accessed first, excluding the protected keys. */
    @Query(
        "SELECT * FROM cache_index WHERE cacheKey NOT IN (:protectedKeys) " +
            "ORDER BY lastAccessedAtEpochMs ASC LIMIT :limit"
    )
    suspend fun evictionCandidates(protectedKeys: List<String>, limit: Int): List<CacheIndexEntity>

    @Query("DELETE FROM cache_index WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cache_index")
    suspend fun clear()
}
