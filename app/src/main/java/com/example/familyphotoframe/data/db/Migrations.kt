package com.example.familyphotoframe.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.familyphotoframe.data.index.CanonicalPhotoPath

/**
 * Explicit, non-destructive v1→v2 migration (spec §6.3, Contract Rule 10).
 *
 * v1 (Phase 0) was local-only: a single `photos` table. v2 (Phase 1) adds:
 *  - decode/cache/curation columns on `photos`, plus the remaining §6.2 indexes;
 *  - `source_config`, `smb_source_config`, `local_saf_source_config` (multi-source);
 *  - `secrets` (Keystore-encrypted SMB credentials);
 *  - `cache_index` (app-owned LRU cache of remote bytes).
 *
 * Every statement is additive; no existing row or column is dropped or rewritten, so
 * a user's Phase 0 index and settings survive the upgrade. The exact column types,
 * NOT NULL flags, DEFAULTs and index names mirror what Room generates for the v2
 * entities so Room's post-migration schema validation passes.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ---- photos: additive columns (match PhotoItemEntity v2 exactly) ----
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `width` INTEGER")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `height` INTEGER")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `exifOrientation` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `dateTakenEpochMs` INTEGER")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `missingSinceEpochMs` INTEGER")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `cacheKey` TEXT")

        // ---- photos: remaining §6.2 indexes (Room's exact index names) ----
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_sourceId_dateTakenEpochMs` ON `photos` (`sourceId`, `dateTakenEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_cacheKey` ON `photos` (`cacheKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_missingSinceEpochMs` ON `photos` (`missingSinceEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_isFavorite` ON `photos` (`isFavorite`)")

        // ---- source_config ----
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `source_config` (
                `id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `role` TEXT NOT NULL,
                `credentialRef` TEXT,
                `includeSubfolders` INTEGER NOT NULL DEFAULT 1,
                `includeGlobsCsv` TEXT NOT NULL,
                `excludeGlobsCsv` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // ---- smb_source_config ----
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `smb_source_config` (
                `sourceId` TEXT NOT NULL,
                `host` TEXT NOT NULL,
                `share` TEXT NOT NULL,
                `path` TEXT NOT NULL,
                `user` TEXT NOT NULL,
                `domain` TEXT NOT NULL,
                `connectionTimeoutMs` INTEGER NOT NULL DEFAULT 5000,
                `readTimeoutMs` INTEGER NOT NULL DEFAULT 15000,
                `listTimeoutMs` INTEGER NOT NULL DEFAULT 15000,
                PRIMARY KEY(`sourceId`)
            )
            """.trimIndent()
        )

        // ---- local_saf_source_config ----
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_saf_source_config` (
                `sourceId` TEXT NOT NULL,
                `treeUri` TEXT NOT NULL,
                `permissionState` TEXT NOT NULL,
                PRIMARY KEY(`sourceId`)
            )
            """.trimIndent()
        )

        // ---- secrets ----
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `secrets` (
                `credentialRef` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `encryptedSecretBlob` BLOB NOT NULL,
                `iv` BLOB NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL,
                `securityLevel` TEXT NOT NULL,
                PRIMARY KEY(`credentialRef`)
            )
            """.trimIndent()
        )

        // ---- cache_index ----
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cache_index` (
                `cacheKey` TEXT NOT NULL,
                `photoStableId` TEXT NOT NULL,
                `localFilePathPrivate` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `lastAccessedAtEpochMs` INTEGER NOT NULL,
                `verifiedDecodeOk` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`cacheKey`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cache_index_photoStableId` ON `cache_index` (`photoStableId`)")
    }
}

/**
 * v2→v3: adds `secrets.wrappedKey` so API 21–22 devices can store an RSA-wrapped AES
 * key alongside the ciphertext (AndroidKeyStore has no symmetric keys before API 23).
 * Additive and nullable: existing API 23+ rows keep working unchanged.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `secrets` ADD COLUMN `wrappedKey` BLOB")
    }
}

/**
 * v3→v4: adds `photos.caption`, `photos.gpsLat`, `photos.gpsLon` (Phase 2 increment 5,
 * spec §11 date/caption/location overlays). Populated best-effort from EXIF during
 * indexing; existing rows simply get NULLs until their next rescan. Additive only —
 * no existing column is touched (Contract Rule 10). GPS is never written to the
 * diagnostics log or the redacted support-bundle export (spec §17.2).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `caption` TEXT")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `gpsLat` REAL")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `gpsLon` REAL")
    }
}

/**
 * v4→v5: adds `photos.exifScannedAtEpochMs` (Phase 2 increment 8). Additive only.
 * Existing rows get NULL, which correctly marks them as "EXIF never attempted" and
 * lets the display-time backfiller pick them up.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `exifScannedAtEpochMs` INTEGER")
    }
}


/** v5→v6: persistent remembered-browser authorization records. Token material is HMAC-only. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `remembered_browsers` (
                `id` TEXT NOT NULL,
                `currentTokenHash` BLOB NOT NULL,
                `previousTokenHash` BLOB,
                `previousTokenValidUntilEpochMs` INTEGER,
                `retiredTokenHash` BLOB,
                `label` TEXT NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `lastUsedAtEpochMs` INTEGER NOT NULL,
                `expiresAtEpochMs` INTEGER,
                `revokedAtEpochMs` INTEGER,
                `browserSummary` TEXT,
                `osSummary` TEXT,
                `lastTrustedWallClockEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v6→v7: durable folder-balanced shuffle state, reservations, and bounded history.
 * Every table is additive; the photo index and all user settings remain untouched.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shuffle_scopes` (
                `scopeKey` TEXT NOT NULL,
                `playlistId` TEXT NOT NULL,
                `poolRole` TEXT NOT NULL,
                `activeFolderCycle` INTEGER NOT NULL,
                `lastPresentedFolderKey` TEXT,
                `lastUsedAtEpochMs` INTEGER NOT NULL,
                `eligibilityRevision` INTEGER NOT NULL,
                `reconciliationRevision` INTEGER NOT NULL,
                `historyCursorSequence` INTEGER NOT NULL,
                `latestHistorySequence` INTEGER NOT NULL,
                `lastCommitEpochMs` INTEGER,
                `lastReconciliationEpochMs` INTEGER,
                `lastRecoveryEpochMs` INTEGER,
                PRIMARY KEY(`scopeKey`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_scopes_lastUsedAtEpochMs` ON `shuffle_scopes` (`lastUsedAtEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_scopes_playlistId_poolRole` ON `shuffle_scopes` (`playlistId`, `poolRole`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `folder_shuffle_entries` (
                `scopeKey` TEXT NOT NULL,
                `folderCycle` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `folderKey` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `retryCount` INTEGER NOT NULL,
                `skipReason` TEXT,
                PRIMARY KEY(`scopeKey`, `folderCycle`, `position`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folder_shuffle_entries_scopeKey_folderCycle_folderKey` ON `folder_shuffle_entries` (`scopeKey`, `folderCycle`, `folderKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_shuffle_entries_scopeKey_folderCycle_state_position` ON `folder_shuffle_entries` (`scopeKey`, `folderCycle`, `state`, `position`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `folder_photo_cycles` (
                `scopeKey` TEXT NOT NULL,
                `folderKey` TEXT NOT NULL,
                `activePhotoCycle` INTEGER NOT NULL,
                `lastConsumedPhotoKey` TEXT,
                `reconciliationRevision` INTEGER NOT NULL,
                `lastUsedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`scopeKey`, `folderKey`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_photo_cycles_scopeKey_lastUsedAtEpochMs` ON `folder_photo_cycles` (`scopeKey`, `lastUsedAtEpochMs`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `photo_shuffle_entries` (
                `scopeKey` TEXT NOT NULL,
                `folderKey` TEXT NOT NULL,
                `photoCycle` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `folderPhotoKey` TEXT NOT NULL,
                `photoId` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                `failureCount` INTEGER NOT NULL,
                PRIMARY KEY(`scopeKey`, `folderKey`, `photoCycle`, `position`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_photo_shuffle_entries_scopeKey_folderKey_photoCycle_folderPhotoKey` ON `photo_shuffle_entries` (`scopeKey`, `folderKey`, `photoCycle`, `folderPhotoKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_shuffle_entries_scopeKey_folderKey_photoCycle_state_position` ON `photo_shuffle_entries` (`scopeKey`, `folderKey`, `photoCycle`, `state`, `position`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_shuffle_entries_photoId` ON `photo_shuffle_entries` (`photoId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shuffle_reservations` (
                `scopeKey` TEXT NOT NULL,
                `reservationId` TEXT NOT NULL,
                `folderCycle` INTEGER NOT NULL,
                `folderPosition` INTEGER NOT NULL,
                `folderKey` TEXT NOT NULL,
                `photoCycle` INTEGER NOT NULL,
                `photoPositionsJson` TEXT NOT NULL,
                `photoIdsJson` TEXT NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`scopeKey`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_reservations_createdAtEpochMs` ON `shuffle_reservations` (`createdAtEpochMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_reservations_folderKey` ON `shuffle_reservations` (`folderKey`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `presentation_history` (
                `presentationId` TEXT NOT NULL,
                `scopeKey` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `folderKey` TEXT NOT NULL,
                `presentationType` TEXT NOT NULL,
                `photoIdsJson` TEXT NOT NULL,
                `committedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`presentationId`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_presentation_history_scopeKey_sequence` ON `presentation_history` (`scopeKey`, `sequence`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_presentation_history_scopeKey_committedAtEpochMs` ON `presentation_history` (`scopeKey`, `committedAtEpochMs`)")
    }
}

/**
 * v7→v8: exact canonical direct-directory identity and asynchronous SHA-256 indexing.
 * The migration is additive. Existing paths are backfilled in-place using the same
 * canonical helper used by the indexer; photo bytes are never read during migration.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `canonicalDirectory` TEXT NOT NULL DEFAULT '@root'")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `contentSha256` TEXT")
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `contentHashScannedAtEpochMs` INTEGER")

        val select = db.query("SELECT `id`, `normalizedPath` FROM `photos`")
        val update = db.compileStatement("UPDATE `photos` SET `canonicalDirectory` = ? WHERE `id` = ?")
        select.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val pathIndex = cursor.getColumnIndexOrThrow("normalizedPath")
            while (cursor.moveToNext()) {
                val directory = CanonicalPhotoPath.directDirectory(
                    cursor.getString(pathIndex).orEmpty(),
                )
                update.clearBindings()
                update.bindString(1, directory)
                update.bindLong(2, cursor.getLong(idIndex))
                update.executeUpdateDelete()
            }
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_sourceId_canonicalDirectory` ON `photos` (`sourceId`, `canonicalDirectory`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_contentSha256` ON `photos` (`contentSha256`)")
    }
}
