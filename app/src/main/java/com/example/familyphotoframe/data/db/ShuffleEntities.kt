package com.example.familyphotoframe.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "shuffle_scopes",
    indices = [
        Index(value = ["lastUsedAtEpochMs"]),
        Index(value = ["playlistId", "poolRole"]),
    ],
)
data class ShuffleScopeEntity(
    @androidx.room.PrimaryKey val scopeKey: String,
    val playlistId: String,
    val poolRole: String,
    val activeFolderCycle: Long,
    val lastPresentedFolderKey: String?,
    val lastUsedAtEpochMs: Long,
    val eligibilityRevision: Long,
    val reconciliationRevision: Long,
    val historyCursorSequence: Long,
    val latestHistorySequence: Long,
    val lastCommitEpochMs: Long?,
    val lastReconciliationEpochMs: Long?,
    val lastRecoveryEpochMs: Long?,
)

@Entity(
    tableName = "folder_shuffle_entries",
    primaryKeys = ["scopeKey", "folderCycle", "position"],
    indices = [
        Index(value = ["scopeKey", "folderCycle", "folderKey"], unique = true),
        Index(value = ["scopeKey", "folderCycle", "state", "position"]),
    ],
)
data class FolderShuffleEntryEntity(
    val scopeKey: String,
    val folderCycle: Long,
    val position: Int,
    val folderKey: String,
    val state: String,
    val retryCount: Int,
    val skipReason: String?,
)

@Entity(
    tableName = "folder_photo_cycles",
    primaryKeys = ["scopeKey", "folderKey"],
    indices = [Index(value = ["scopeKey", "lastUsedAtEpochMs"])],
)
data class FolderPhotoCycleEntity(
    val scopeKey: String,
    val folderKey: String,
    val activePhotoCycle: Long,
    val lastConsumedPhotoKey: String?,
    val reconciliationRevision: Long,
    val lastUsedAtEpochMs: Long,
)

@Entity(
    tableName = "photo_shuffle_entries",
    primaryKeys = ["scopeKey", "folderKey", "photoCycle", "position"],
    indices = [
        Index(value = ["scopeKey", "folderKey", "photoCycle", "folderPhotoKey"], unique = true),
        Index(value = ["scopeKey", "folderKey", "photoCycle", "state", "position"]),
        Index(value = ["photoId"]),
    ],
)
data class PhotoShuffleEntryEntity(
    val scopeKey: String,
    val folderKey: String,
    val photoCycle: Long,
    val position: Int,
    val folderPhotoKey: String,
    val photoId: Long,
    val state: String,
    val failureCount: Int,
)

@Entity(
    tableName = "shuffle_reservations",
    indices = [
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["folderKey"]),
    ],
)
data class ShuffleReservationEntity(
    @androidx.room.PrimaryKey val scopeKey: String,
    val reservationId: String,
    val folderCycle: Long,
    val folderPosition: Int,
    val folderKey: String,
    val photoCycle: Long,
    val photoPositionsJson: String,
    val photoIdsJson: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "presentation_history",
    indices = [
        Index(value = ["scopeKey", "sequence"], unique = true),
        Index(value = ["scopeKey", "committedAtEpochMs"]),
    ],
)
data class PresentationHistoryEntity(
    @androidx.room.PrimaryKey val presentationId: String,
    val scopeKey: String,
    val sequence: Long,
    val folderKey: String,
    val presentationType: String,
    val photoIdsJson: String,
    val committedAtEpochMs: Long,
)
