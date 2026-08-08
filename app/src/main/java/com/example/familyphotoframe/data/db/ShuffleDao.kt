package com.example.familyphotoframe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShuffleDao {
    @Query("SELECT * FROM shuffle_scopes WHERE scopeKey = :scopeKey")
    suspend fun scope(scopeKey: String): ShuffleScopeEntity?

    @Query("SELECT * FROM shuffle_scopes ORDER BY lastUsedAtEpochMs DESC")
    suspend fun scopesByRecentUse(): List<ShuffleScopeEntity>

    @Query("SELECT * FROM shuffle_scopes WHERE playlistId = :playlistId ORDER BY lastUsedAtEpochMs DESC")
    suspend fun scopesForPlaylist(playlistId: String): List<ShuffleScopeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScope(scope: ShuffleScopeEntity)

    @Query("UPDATE shuffle_scopes SET lastUsedAtEpochMs = :now WHERE scopeKey = :scopeKey")
    suspend fun touchScope(scopeKey: String, now: Long)

    @Query("DELETE FROM shuffle_scopes WHERE scopeKey = :scopeKey")
    suspend fun deleteScope(scopeKey: String)

    @Query("SELECT * FROM folder_shuffle_entries WHERE scopeKey = :scopeKey AND folderCycle = :cycle ORDER BY position ASC")
    suspend fun folderEntries(scopeKey: String, cycle: Long): List<FolderShuffleEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolderEntries(entries: List<FolderShuffleEntryEntity>)

    @Query("DELETE FROM folder_shuffle_entries WHERE scopeKey = :scopeKey AND folderCycle = :cycle")
    suspend fun deleteFolderCycle(scopeKey: String, cycle: Long)

    @Query("DELETE FROM folder_shuffle_entries WHERE scopeKey = :scopeKey")
    suspend fun deleteAllFolderEntries(scopeKey: String)

    @Query("UPDATE folder_shuffle_entries SET state = :state, retryCount = :retryCount, skipReason = :skipReason WHERE scopeKey = :scopeKey AND folderCycle = :cycle AND position = :position")
    suspend fun updateFolderEntry(
        scopeKey: String,
        cycle: Long,
        position: Int,
        state: String,
        retryCount: Int,
        skipReason: String?,
    )

    @Query("SELECT COUNT(*) FROM folder_shuffle_entries WHERE scopeKey = :scopeKey AND folderCycle = :cycle AND state IN ('PENDING','RESERVED')")
    suspend fun unresolvedFolderCount(scopeKey: String, cycle: Long): Int

    @Query("SELECT * FROM folder_photo_cycles WHERE scopeKey = :scopeKey AND folderKey = :folderKey")
    suspend fun photoCycle(scopeKey: String, folderKey: String): FolderPhotoCycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotoCycle(cycle: FolderPhotoCycleEntity)

    @Query("DELETE FROM folder_photo_cycles WHERE scopeKey = :scopeKey")
    suspend fun deletePhotoCycles(scopeKey: String)

    @Query("SELECT * FROM photo_shuffle_entries WHERE scopeKey = :scopeKey AND folderKey = :folderKey AND photoCycle = :cycle ORDER BY position ASC")
    suspend fun photoEntries(scopeKey: String, folderKey: String, cycle: Long): List<PhotoShuffleEntryEntity>

    @Query("SELECT * FROM photo_shuffle_entries WHERE scopeKey = :scopeKey AND folderKey = :folderKey AND photoCycle = :cycle AND state = 'PENDING' ORDER BY position ASC LIMIT :limit")
    suspend fun pendingPhotos(scopeKey: String, folderKey: String, cycle: Long, limit: Int): List<PhotoShuffleEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhotoEntries(entries: List<PhotoShuffleEntryEntity>)

    @Query("DELETE FROM photo_shuffle_entries WHERE scopeKey = :scopeKey AND folderKey = :folderKey AND photoCycle = :cycle")
    suspend fun deletePhotoCycleEntries(scopeKey: String, folderKey: String, cycle: Long)

    @Query("DELETE FROM photo_shuffle_entries WHERE scopeKey = :scopeKey")
    suspend fun deleteAllPhotoEntries(scopeKey: String)

    @Query("UPDATE photo_shuffle_entries SET state = :state, failureCount = :failureCount WHERE scopeKey = :scopeKey AND folderKey = :folderKey AND photoCycle = :cycle AND position = :position")
    suspend fun updatePhotoEntry(
        scopeKey: String,
        folderKey: String,
        cycle: Long,
        position: Int,
        state: String,
        failureCount: Int,
    )

    @Query("SELECT COUNT(*) FROM photo_shuffle_entries WHERE scopeKey = :scopeKey AND folderKey = :folderKey AND photoCycle = :cycle AND state IN ('PENDING','RESERVED')")
    suspend fun unresolvedPhotoCount(scopeKey: String, folderKey: String, cycle: Long): Int

    @Query("SELECT COUNT(*) FROM photo_shuffle_entries WHERE scopeKey = :scopeKey AND state = 'QUARANTINED'")
    suspend fun quarantinedPhotoCount(scopeKey: String): Int

    @Query("SELECT * FROM shuffle_reservations WHERE scopeKey = :scopeKey")
    suspend fun reservation(scopeKey: String): ShuffleReservationEntity?

    @Query("SELECT * FROM shuffle_reservations")
    suspend fun allReservations(): List<ShuffleReservationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReservation(reservation: ShuffleReservationEntity)

    @Query("DELETE FROM shuffle_reservations WHERE scopeKey = :scopeKey")
    suspend fun deleteReservation(scopeKey: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(history: PresentationHistoryEntity)

    @Query("SELECT * FROM presentation_history WHERE scopeKey = :scopeKey ORDER BY sequence DESC LIMIT 1")
    suspend fun newestHistory(scopeKey: String): PresentationHistoryEntity?

    @Query("SELECT * FROM presentation_history WHERE scopeKey = :scopeKey AND sequence < :sequence ORDER BY sequence DESC LIMIT 1")
    suspend fun previousHistory(scopeKey: String, sequence: Long): PresentationHistoryEntity?

    @Query("SELECT * FROM presentation_history WHERE scopeKey = :scopeKey AND sequence > :sequence ORDER BY sequence ASC LIMIT 1")
    suspend fun nextHistory(scopeKey: String, sequence: Long): PresentationHistoryEntity?

    @Query("DELETE FROM presentation_history WHERE scopeKey = :scopeKey AND sequence NOT IN (SELECT sequence FROM presentation_history WHERE scopeKey = :scopeKey ORDER BY sequence DESC LIMIT :keep)")
    suspend fun trimHistory(scopeKey: String, keep: Int)

    @Query("DELETE FROM presentation_history WHERE scopeKey = :scopeKey")
    suspend fun clearHistory(scopeKey: String)

    @Query("DELETE FROM folder_shuffle_entries WHERE scopeKey = :scopeKey")
    suspend fun cleanupFolderEntries(scopeKey: String)

    @Query("DELETE FROM photo_shuffle_entries WHERE scopeKey = :scopeKey")
    suspend fun cleanupPhotoEntries(scopeKey: String)

    @Query("DELETE FROM folder_photo_cycles WHERE scopeKey = :scopeKey")
    suspend fun cleanupPhotoCycles(scopeKey: String)

    @Query("DELETE FROM shuffle_reservations WHERE scopeKey = :scopeKey")
    suspend fun cleanupReservations(scopeKey: String)

    @Query("DELETE FROM presentation_history WHERE scopeKey = :scopeKey")
    suspend fun cleanupHistory(scopeKey: String)
}
