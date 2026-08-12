package com.example.familyphotoframe.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile PhotoDao _photoDao;

  private volatile SourceConfigDao _sourceConfigDao;

  private volatile SecretDao _secretDao;

  private volatile CacheIndexDao _cacheIndexDao;

  private volatile RememberedBrowserDao _rememberedBrowserDao;

  private volatile ShuffleDao _shuffleDao;

  private volatile LocalThumbnailCacheDao _localThumbnailCacheDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(9) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stableId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `normalizedPath` TEXT NOT NULL, `folderName` TEXT NOT NULL, `fileName` TEXT NOT NULL, `mimeType` TEXT, `sizeBytes` INTEGER NOT NULL, `fileModifiedEpochMs` INTEGER NOT NULL, `openToken` TEXT NOT NULL, `indexedAtEpochMs` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, `lastShownAtEpochMs` INTEGER, `decodeFailureCount` INTEGER NOT NULL, `lastDecodeFailureAtEpochMs` INTEGER, `width` INTEGER, `height` INTEGER, `exifOrientation` INTEGER NOT NULL DEFAULT 0, `dateTakenEpochMs` INTEGER, `isFavorite` INTEGER NOT NULL DEFAULT 0, `missingSinceEpochMs` INTEGER, `cacheKey` TEXT, `caption` TEXT, `gpsLat` REAL, `gpsLon` REAL, `exifScannedAtEpochMs` INTEGER, `canonicalDirectory` TEXT NOT NULL DEFAULT '@root', `contentSha256` TEXT, `contentHashScannedAtEpochMs` INTEGER)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_photos_sourceId_normalizedPath` ON `photos` (`sourceId`, `normalizedPath`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_sourceId_isHidden_lastShownAtEpochMs` ON `photos` (`sourceId`, `isHidden`, `lastShownAtEpochMs`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_stableId` ON `photos` (`stableId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_sourceId_dateTakenEpochMs` ON `photos` (`sourceId`, `dateTakenEpochMs`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_cacheKey` ON `photos` (`cacheKey`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_missingSinceEpochMs` ON `photos` (`missingSinceEpochMs`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_isFavorite` ON `photos` (`isFavorite`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_sourceId_canonicalDirectory` ON `photos` (`sourceId`, `canonicalDirectory`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_contentSha256` ON `photos` (`contentSha256`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `source_config` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `displayName` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1, `role` TEXT NOT NULL, `credentialRef` TEXT, `includeSubfolders` INTEGER NOT NULL DEFAULT 1, `includeGlobsCsv` TEXT NOT NULL, `excludeGlobsCsv` TEXT NOT NULL, `priority` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `smb_source_config` (`sourceId` TEXT NOT NULL, `host` TEXT NOT NULL, `share` TEXT NOT NULL, `path` TEXT NOT NULL, `user` TEXT NOT NULL, `domain` TEXT NOT NULL, `connectionTimeoutMs` INTEGER NOT NULL DEFAULT 5000, `readTimeoutMs` INTEGER NOT NULL DEFAULT 15000, `listTimeoutMs` INTEGER NOT NULL DEFAULT 15000, PRIMARY KEY(`sourceId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `local_saf_source_config` (`sourceId` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `permissionState` TEXT NOT NULL, PRIMARY KEY(`sourceId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `secrets` (`credentialRef` TEXT NOT NULL, `type` TEXT NOT NULL, `encryptedSecretBlob` BLOB NOT NULL, `iv` BLOB NOT NULL, `wrappedKey` BLOB, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `securityLevel` TEXT NOT NULL, PRIMARY KEY(`credentialRef`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cache_index` (`cacheKey` TEXT NOT NULL, `photoStableId` TEXT NOT NULL, `localFilePathPrivate` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `lastAccessedAtEpochMs` INTEGER NOT NULL, `verifiedDecodeOk` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`cacheKey`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cache_index_photoStableId` ON `cache_index` (`photoStableId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `remembered_browsers` (`id` TEXT NOT NULL, `currentTokenHash` BLOB NOT NULL, `previousTokenHash` BLOB, `previousTokenValidUntilEpochMs` INTEGER, `retiredTokenHash` BLOB, `label` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `lastUsedAtEpochMs` INTEGER NOT NULL, `expiresAtEpochMs` INTEGER, `revokedAtEpochMs` INTEGER, `browserSummary` TEXT, `osSummary` TEXT, `lastTrustedWallClockEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `shuffle_scopes` (`scopeKey` TEXT NOT NULL, `playlistId` TEXT NOT NULL, `poolRole` TEXT NOT NULL, `activeFolderCycle` INTEGER NOT NULL, `lastPresentedFolderKey` TEXT, `lastUsedAtEpochMs` INTEGER NOT NULL, `eligibilityRevision` INTEGER NOT NULL, `reconciliationRevision` INTEGER NOT NULL, `historyCursorSequence` INTEGER NOT NULL, `latestHistorySequence` INTEGER NOT NULL, `lastCommitEpochMs` INTEGER, `lastReconciliationEpochMs` INTEGER, `lastRecoveryEpochMs` INTEGER, PRIMARY KEY(`scopeKey`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_scopes_lastUsedAtEpochMs` ON `shuffle_scopes` (`lastUsedAtEpochMs`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_scopes_playlistId_poolRole` ON `shuffle_scopes` (`playlistId`, `poolRole`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `folder_shuffle_entries` (`scopeKey` TEXT NOT NULL, `folderCycle` INTEGER NOT NULL, `position` INTEGER NOT NULL, `folderKey` TEXT NOT NULL, `state` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `skipReason` TEXT, PRIMARY KEY(`scopeKey`, `folderCycle`, `position`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folder_shuffle_entries_scopeKey_folderCycle_folderKey` ON `folder_shuffle_entries` (`scopeKey`, `folderCycle`, `folderKey`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_shuffle_entries_scopeKey_folderCycle_state_position` ON `folder_shuffle_entries` (`scopeKey`, `folderCycle`, `state`, `position`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `folder_photo_cycles` (`scopeKey` TEXT NOT NULL, `folderKey` TEXT NOT NULL, `activePhotoCycle` INTEGER NOT NULL, `lastConsumedPhotoKey` TEXT, `reconciliationRevision` INTEGER NOT NULL, `lastUsedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`scopeKey`, `folderKey`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folder_photo_cycles_scopeKey_lastUsedAtEpochMs` ON `folder_photo_cycles` (`scopeKey`, `lastUsedAtEpochMs`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `photo_shuffle_entries` (`scopeKey` TEXT NOT NULL, `folderKey` TEXT NOT NULL, `photoCycle` INTEGER NOT NULL, `position` INTEGER NOT NULL, `folderPhotoKey` TEXT NOT NULL, `photoId` INTEGER NOT NULL, `state` TEXT NOT NULL, `failureCount` INTEGER NOT NULL, PRIMARY KEY(`scopeKey`, `folderKey`, `photoCycle`, `position`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_photo_shuffle_entries_scopeKey_folderKey_photoCycle_folderPhotoKey` ON `photo_shuffle_entries` (`scopeKey`, `folderKey`, `photoCycle`, `folderPhotoKey`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_shuffle_entries_scopeKey_folderKey_photoCycle_state_position` ON `photo_shuffle_entries` (`scopeKey`, `folderKey`, `photoCycle`, `state`, `position`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_shuffle_entries_photoId` ON `photo_shuffle_entries` (`photoId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `shuffle_reservations` (`scopeKey` TEXT NOT NULL, `reservationId` TEXT NOT NULL, `folderCycle` INTEGER NOT NULL, `folderPosition` INTEGER NOT NULL, `folderKey` TEXT NOT NULL, `photoCycle` INTEGER NOT NULL, `photoPositionsJson` TEXT NOT NULL, `photoIdsJson` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`scopeKey`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_reservations_createdAtEpochMs` ON `shuffle_reservations` (`createdAtEpochMs`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shuffle_reservations_folderKey` ON `shuffle_reservations` (`folderKey`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `presentation_history` (`presentationId` TEXT NOT NULL, `scopeKey` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `folderKey` TEXT NOT NULL, `presentationType` TEXT NOT NULL, `photoIdsJson` TEXT NOT NULL, `committedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`presentationId`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_presentation_history_scopeKey_sequence` ON `presentation_history` (`scopeKey`, `sequence`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_presentation_history_scopeKey_committedAtEpochMs` ON `presentation_history` (`scopeKey`, `committedAtEpochMs`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `local_thumbnail_cache` (`cacheKey` TEXT NOT NULL, `photoStableId` TEXT NOT NULL, `sizeBucket` TEXT NOT NULL, `localFilePathPrivate` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `lastAccessedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_thumbnail_cache_photoStableId` ON `local_thumbnail_cache` (`photoStableId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '85486d915a0d0b70091573f8dc064298')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `photos`");
        db.execSQL("DROP TABLE IF EXISTS `source_config`");
        db.execSQL("DROP TABLE IF EXISTS `smb_source_config`");
        db.execSQL("DROP TABLE IF EXISTS `local_saf_source_config`");
        db.execSQL("DROP TABLE IF EXISTS `secrets`");
        db.execSQL("DROP TABLE IF EXISTS `cache_index`");
        db.execSQL("DROP TABLE IF EXISTS `remembered_browsers`");
        db.execSQL("DROP TABLE IF EXISTS `shuffle_scopes`");
        db.execSQL("DROP TABLE IF EXISTS `folder_shuffle_entries`");
        db.execSQL("DROP TABLE IF EXISTS `folder_photo_cycles`");
        db.execSQL("DROP TABLE IF EXISTS `photo_shuffle_entries`");
        db.execSQL("DROP TABLE IF EXISTS `shuffle_reservations`");
        db.execSQL("DROP TABLE IF EXISTS `presentation_history`");
        db.execSQL("DROP TABLE IF EXISTS `local_thumbnail_cache`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPhotos = new HashMap<String, TableInfo.Column>(29);
        _columnsPhotos.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("stableId", new TableInfo.Column("stableId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("sourceId", new TableInfo.Column("sourceId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("normalizedPath", new TableInfo.Column("normalizedPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("folderName", new TableInfo.Column("folderName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("fileName", new TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("mimeType", new TableInfo.Column("mimeType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("fileModifiedEpochMs", new TableInfo.Column("fileModifiedEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("openToken", new TableInfo.Column("openToken", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("indexedAtEpochMs", new TableInfo.Column("indexedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("isHidden", new TableInfo.Column("isHidden", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("lastShownAtEpochMs", new TableInfo.Column("lastShownAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("decodeFailureCount", new TableInfo.Column("decodeFailureCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("lastDecodeFailureAtEpochMs", new TableInfo.Column("lastDecodeFailureAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("width", new TableInfo.Column("width", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("height", new TableInfo.Column("height", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("exifOrientation", new TableInfo.Column("exifOrientation", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("dateTakenEpochMs", new TableInfo.Column("dateTakenEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("isFavorite", new TableInfo.Column("isFavorite", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("missingSinceEpochMs", new TableInfo.Column("missingSinceEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("cacheKey", new TableInfo.Column("cacheKey", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("caption", new TableInfo.Column("caption", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("gpsLat", new TableInfo.Column("gpsLat", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("gpsLon", new TableInfo.Column("gpsLon", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("exifScannedAtEpochMs", new TableInfo.Column("exifScannedAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("canonicalDirectory", new TableInfo.Column("canonicalDirectory", "TEXT", true, 0, "'@root'", TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("contentSha256", new TableInfo.Column("contentSha256", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotos.put("contentHashScannedAtEpochMs", new TableInfo.Column("contentHashScannedAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPhotos = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPhotos = new HashSet<TableInfo.Index>(9);
        _indicesPhotos.add(new TableInfo.Index("index_photos_sourceId_normalizedPath", true, Arrays.asList("sourceId", "normalizedPath"), Arrays.asList("ASC", "ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_sourceId_isHidden_lastShownAtEpochMs", false, Arrays.asList("sourceId", "isHidden", "lastShownAtEpochMs"), Arrays.asList("ASC", "ASC", "ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_stableId", false, Arrays.asList("stableId"), Arrays.asList("ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_sourceId_dateTakenEpochMs", false, Arrays.asList("sourceId", "dateTakenEpochMs"), Arrays.asList("ASC", "ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_cacheKey", false, Arrays.asList("cacheKey"), Arrays.asList("ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_missingSinceEpochMs", false, Arrays.asList("missingSinceEpochMs"), Arrays.asList("ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_isFavorite", false, Arrays.asList("isFavorite"), Arrays.asList("ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_sourceId_canonicalDirectory", false, Arrays.asList("sourceId", "canonicalDirectory"), Arrays.asList("ASC", "ASC")));
        _indicesPhotos.add(new TableInfo.Index("index_photos_contentSha256", false, Arrays.asList("contentSha256"), Arrays.asList("ASC")));
        final TableInfo _infoPhotos = new TableInfo("photos", _columnsPhotos, _foreignKeysPhotos, _indicesPhotos);
        final TableInfo _existingPhotos = TableInfo.read(db, "photos");
        if (!_infoPhotos.equals(_existingPhotos)) {
          return new RoomOpenHelper.ValidationResult(false, "photos(com.example.familyphotoframe.data.db.PhotoItemEntity).\n"
                  + " Expected:\n" + _infoPhotos + "\n"
                  + " Found:\n" + _existingPhotos);
        }
        final HashMap<String, TableInfo.Column> _columnsSourceConfig = new HashMap<String, TableInfo.Column>(10);
        _columnsSourceConfig.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, "1", TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("role", new TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("credentialRef", new TableInfo.Column("credentialRef", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("includeSubfolders", new TableInfo.Column("includeSubfolders", "INTEGER", true, 0, "1", TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("includeGlobsCsv", new TableInfo.Column("includeGlobsCsv", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("excludeGlobsCsv", new TableInfo.Column("excludeGlobsCsv", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSourceConfig.put("priority", new TableInfo.Column("priority", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSourceConfig = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSourceConfig = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSourceConfig = new TableInfo("source_config", _columnsSourceConfig, _foreignKeysSourceConfig, _indicesSourceConfig);
        final TableInfo _existingSourceConfig = TableInfo.read(db, "source_config");
        if (!_infoSourceConfig.equals(_existingSourceConfig)) {
          return new RoomOpenHelper.ValidationResult(false, "source_config(com.example.familyphotoframe.data.db.SourceConfigEntity).\n"
                  + " Expected:\n" + _infoSourceConfig + "\n"
                  + " Found:\n" + _existingSourceConfig);
        }
        final HashMap<String, TableInfo.Column> _columnsSmbSourceConfig = new HashMap<String, TableInfo.Column>(9);
        _columnsSmbSourceConfig.put("sourceId", new TableInfo.Column("sourceId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("host", new TableInfo.Column("host", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("share", new TableInfo.Column("share", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("path", new TableInfo.Column("path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("user", new TableInfo.Column("user", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("domain", new TableInfo.Column("domain", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("connectionTimeoutMs", new TableInfo.Column("connectionTimeoutMs", "INTEGER", true, 0, "5000", TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("readTimeoutMs", new TableInfo.Column("readTimeoutMs", "INTEGER", true, 0, "15000", TableInfo.CREATED_FROM_ENTITY));
        _columnsSmbSourceConfig.put("listTimeoutMs", new TableInfo.Column("listTimeoutMs", "INTEGER", true, 0, "15000", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSmbSourceConfig = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSmbSourceConfig = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSmbSourceConfig = new TableInfo("smb_source_config", _columnsSmbSourceConfig, _foreignKeysSmbSourceConfig, _indicesSmbSourceConfig);
        final TableInfo _existingSmbSourceConfig = TableInfo.read(db, "smb_source_config");
        if (!_infoSmbSourceConfig.equals(_existingSmbSourceConfig)) {
          return new RoomOpenHelper.ValidationResult(false, "smb_source_config(com.example.familyphotoframe.data.db.SmbSourceConfigEntity).\n"
                  + " Expected:\n" + _infoSmbSourceConfig + "\n"
                  + " Found:\n" + _existingSmbSourceConfig);
        }
        final HashMap<String, TableInfo.Column> _columnsLocalSafSourceConfig = new HashMap<String, TableInfo.Column>(3);
        _columnsLocalSafSourceConfig.put("sourceId", new TableInfo.Column("sourceId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalSafSourceConfig.put("treeUri", new TableInfo.Column("treeUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalSafSourceConfig.put("permissionState", new TableInfo.Column("permissionState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLocalSafSourceConfig = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLocalSafSourceConfig = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoLocalSafSourceConfig = new TableInfo("local_saf_source_config", _columnsLocalSafSourceConfig, _foreignKeysLocalSafSourceConfig, _indicesLocalSafSourceConfig);
        final TableInfo _existingLocalSafSourceConfig = TableInfo.read(db, "local_saf_source_config");
        if (!_infoLocalSafSourceConfig.equals(_existingLocalSafSourceConfig)) {
          return new RoomOpenHelper.ValidationResult(false, "local_saf_source_config(com.example.familyphotoframe.data.db.LocalSafSourceConfigEntity).\n"
                  + " Expected:\n" + _infoLocalSafSourceConfig + "\n"
                  + " Found:\n" + _existingLocalSafSourceConfig);
        }
        final HashMap<String, TableInfo.Column> _columnsSecrets = new HashMap<String, TableInfo.Column>(8);
        _columnsSecrets.put("credentialRef", new TableInfo.Column("credentialRef", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("encryptedSecretBlob", new TableInfo.Column("encryptedSecretBlob", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("iv", new TableInfo.Column("iv", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("wrappedKey", new TableInfo.Column("wrappedKey", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("createdAtEpochMs", new TableInfo.Column("createdAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("updatedAtEpochMs", new TableInfo.Column("updatedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSecrets.put("securityLevel", new TableInfo.Column("securityLevel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSecrets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSecrets = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSecrets = new TableInfo("secrets", _columnsSecrets, _foreignKeysSecrets, _indicesSecrets);
        final TableInfo _existingSecrets = TableInfo.read(db, "secrets");
        if (!_infoSecrets.equals(_existingSecrets)) {
          return new RoomOpenHelper.ValidationResult(false, "secrets(com.example.familyphotoframe.data.db.SecretEntity).\n"
                  + " Expected:\n" + _infoSecrets + "\n"
                  + " Found:\n" + _existingSecrets);
        }
        final HashMap<String, TableInfo.Column> _columnsCacheIndex = new HashMap<String, TableInfo.Column>(7);
        _columnsCacheIndex.put("cacheKey", new TableInfo.Column("cacheKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCacheIndex.put("photoStableId", new TableInfo.Column("photoStableId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCacheIndex.put("localFilePathPrivate", new TableInfo.Column("localFilePathPrivate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCacheIndex.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCacheIndex.put("createdAtEpochMs", new TableInfo.Column("createdAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCacheIndex.put("lastAccessedAtEpochMs", new TableInfo.Column("lastAccessedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCacheIndex.put("verifiedDecodeOk", new TableInfo.Column("verifiedDecodeOk", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCacheIndex = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCacheIndex = new HashSet<TableInfo.Index>(1);
        _indicesCacheIndex.add(new TableInfo.Index("index_cache_index_photoStableId", false, Arrays.asList("photoStableId"), Arrays.asList("ASC")));
        final TableInfo _infoCacheIndex = new TableInfo("cache_index", _columnsCacheIndex, _foreignKeysCacheIndex, _indicesCacheIndex);
        final TableInfo _existingCacheIndex = TableInfo.read(db, "cache_index");
        if (!_infoCacheIndex.equals(_existingCacheIndex)) {
          return new RoomOpenHelper.ValidationResult(false, "cache_index(com.example.familyphotoframe.data.db.CacheIndexEntity).\n"
                  + " Expected:\n" + _infoCacheIndex + "\n"
                  + " Found:\n" + _existingCacheIndex);
        }
        final HashMap<String, TableInfo.Column> _columnsRememberedBrowsers = new HashMap<String, TableInfo.Column>(13);
        _columnsRememberedBrowsers.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("currentTokenHash", new TableInfo.Column("currentTokenHash", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("previousTokenHash", new TableInfo.Column("previousTokenHash", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("previousTokenValidUntilEpochMs", new TableInfo.Column("previousTokenValidUntilEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("retiredTokenHash", new TableInfo.Column("retiredTokenHash", "BLOB", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("createdAtEpochMs", new TableInfo.Column("createdAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("lastUsedAtEpochMs", new TableInfo.Column("lastUsedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("expiresAtEpochMs", new TableInfo.Column("expiresAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("revokedAtEpochMs", new TableInfo.Column("revokedAtEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("browserSummary", new TableInfo.Column("browserSummary", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("osSummary", new TableInfo.Column("osSummary", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRememberedBrowsers.put("lastTrustedWallClockEpochMs", new TableInfo.Column("lastTrustedWallClockEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRememberedBrowsers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRememberedBrowsers = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRememberedBrowsers = new TableInfo("remembered_browsers", _columnsRememberedBrowsers, _foreignKeysRememberedBrowsers, _indicesRememberedBrowsers);
        final TableInfo _existingRememberedBrowsers = TableInfo.read(db, "remembered_browsers");
        if (!_infoRememberedBrowsers.equals(_existingRememberedBrowsers)) {
          return new RoomOpenHelper.ValidationResult(false, "remembered_browsers(com.example.familyphotoframe.data.db.RememberedBrowserEntity).\n"
                  + " Expected:\n" + _infoRememberedBrowsers + "\n"
                  + " Found:\n" + _existingRememberedBrowsers);
        }
        final HashMap<String, TableInfo.Column> _columnsShuffleScopes = new HashMap<String, TableInfo.Column>(13);
        _columnsShuffleScopes.put("scopeKey", new TableInfo.Column("scopeKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("playlistId", new TableInfo.Column("playlistId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("poolRole", new TableInfo.Column("poolRole", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("activeFolderCycle", new TableInfo.Column("activeFolderCycle", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("lastPresentedFolderKey", new TableInfo.Column("lastPresentedFolderKey", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("lastUsedAtEpochMs", new TableInfo.Column("lastUsedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("eligibilityRevision", new TableInfo.Column("eligibilityRevision", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("reconciliationRevision", new TableInfo.Column("reconciliationRevision", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("historyCursorSequence", new TableInfo.Column("historyCursorSequence", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("latestHistorySequence", new TableInfo.Column("latestHistorySequence", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("lastCommitEpochMs", new TableInfo.Column("lastCommitEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("lastReconciliationEpochMs", new TableInfo.Column("lastReconciliationEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleScopes.put("lastRecoveryEpochMs", new TableInfo.Column("lastRecoveryEpochMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysShuffleScopes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesShuffleScopes = new HashSet<TableInfo.Index>(2);
        _indicesShuffleScopes.add(new TableInfo.Index("index_shuffle_scopes_lastUsedAtEpochMs", false, Arrays.asList("lastUsedAtEpochMs"), Arrays.asList("ASC")));
        _indicesShuffleScopes.add(new TableInfo.Index("index_shuffle_scopes_playlistId_poolRole", false, Arrays.asList("playlistId", "poolRole"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoShuffleScopes = new TableInfo("shuffle_scopes", _columnsShuffleScopes, _foreignKeysShuffleScopes, _indicesShuffleScopes);
        final TableInfo _existingShuffleScopes = TableInfo.read(db, "shuffle_scopes");
        if (!_infoShuffleScopes.equals(_existingShuffleScopes)) {
          return new RoomOpenHelper.ValidationResult(false, "shuffle_scopes(com.example.familyphotoframe.data.db.ShuffleScopeEntity).\n"
                  + " Expected:\n" + _infoShuffleScopes + "\n"
                  + " Found:\n" + _existingShuffleScopes);
        }
        final HashMap<String, TableInfo.Column> _columnsFolderShuffleEntries = new HashMap<String, TableInfo.Column>(7);
        _columnsFolderShuffleEntries.put("scopeKey", new TableInfo.Column("scopeKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderShuffleEntries.put("folderCycle", new TableInfo.Column("folderCycle", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderShuffleEntries.put("position", new TableInfo.Column("position", "INTEGER", true, 3, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderShuffleEntries.put("folderKey", new TableInfo.Column("folderKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderShuffleEntries.put("state", new TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderShuffleEntries.put("retryCount", new TableInfo.Column("retryCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderShuffleEntries.put("skipReason", new TableInfo.Column("skipReason", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFolderShuffleEntries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFolderShuffleEntries = new HashSet<TableInfo.Index>(2);
        _indicesFolderShuffleEntries.add(new TableInfo.Index("index_folder_shuffle_entries_scopeKey_folderCycle_folderKey", true, Arrays.asList("scopeKey", "folderCycle", "folderKey"), Arrays.asList("ASC", "ASC", "ASC")));
        _indicesFolderShuffleEntries.add(new TableInfo.Index("index_folder_shuffle_entries_scopeKey_folderCycle_state_position", false, Arrays.asList("scopeKey", "folderCycle", "state", "position"), Arrays.asList("ASC", "ASC", "ASC", "ASC")));
        final TableInfo _infoFolderShuffleEntries = new TableInfo("folder_shuffle_entries", _columnsFolderShuffleEntries, _foreignKeysFolderShuffleEntries, _indicesFolderShuffleEntries);
        final TableInfo _existingFolderShuffleEntries = TableInfo.read(db, "folder_shuffle_entries");
        if (!_infoFolderShuffleEntries.equals(_existingFolderShuffleEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "folder_shuffle_entries(com.example.familyphotoframe.data.db.FolderShuffleEntryEntity).\n"
                  + " Expected:\n" + _infoFolderShuffleEntries + "\n"
                  + " Found:\n" + _existingFolderShuffleEntries);
        }
        final HashMap<String, TableInfo.Column> _columnsFolderPhotoCycles = new HashMap<String, TableInfo.Column>(6);
        _columnsFolderPhotoCycles.put("scopeKey", new TableInfo.Column("scopeKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderPhotoCycles.put("folderKey", new TableInfo.Column("folderKey", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderPhotoCycles.put("activePhotoCycle", new TableInfo.Column("activePhotoCycle", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderPhotoCycles.put("lastConsumedPhotoKey", new TableInfo.Column("lastConsumedPhotoKey", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderPhotoCycles.put("reconciliationRevision", new TableInfo.Column("reconciliationRevision", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFolderPhotoCycles.put("lastUsedAtEpochMs", new TableInfo.Column("lastUsedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFolderPhotoCycles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFolderPhotoCycles = new HashSet<TableInfo.Index>(1);
        _indicesFolderPhotoCycles.add(new TableInfo.Index("index_folder_photo_cycles_scopeKey_lastUsedAtEpochMs", false, Arrays.asList("scopeKey", "lastUsedAtEpochMs"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoFolderPhotoCycles = new TableInfo("folder_photo_cycles", _columnsFolderPhotoCycles, _foreignKeysFolderPhotoCycles, _indicesFolderPhotoCycles);
        final TableInfo _existingFolderPhotoCycles = TableInfo.read(db, "folder_photo_cycles");
        if (!_infoFolderPhotoCycles.equals(_existingFolderPhotoCycles)) {
          return new RoomOpenHelper.ValidationResult(false, "folder_photo_cycles(com.example.familyphotoframe.data.db.FolderPhotoCycleEntity).\n"
                  + " Expected:\n" + _infoFolderPhotoCycles + "\n"
                  + " Found:\n" + _existingFolderPhotoCycles);
        }
        final HashMap<String, TableInfo.Column> _columnsPhotoShuffleEntries = new HashMap<String, TableInfo.Column>(8);
        _columnsPhotoShuffleEntries.put("scopeKey", new TableInfo.Column("scopeKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("folderKey", new TableInfo.Column("folderKey", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("photoCycle", new TableInfo.Column("photoCycle", "INTEGER", true, 3, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("position", new TableInfo.Column("position", "INTEGER", true, 4, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("folderPhotoKey", new TableInfo.Column("folderPhotoKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("photoId", new TableInfo.Column("photoId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("state", new TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPhotoShuffleEntries.put("failureCount", new TableInfo.Column("failureCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPhotoShuffleEntries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPhotoShuffleEntries = new HashSet<TableInfo.Index>(3);
        _indicesPhotoShuffleEntries.add(new TableInfo.Index("index_photo_shuffle_entries_scopeKey_folderKey_photoCycle_folderPhotoKey", true, Arrays.asList("scopeKey", "folderKey", "photoCycle", "folderPhotoKey"), Arrays.asList("ASC", "ASC", "ASC", "ASC")));
        _indicesPhotoShuffleEntries.add(new TableInfo.Index("index_photo_shuffle_entries_scopeKey_folderKey_photoCycle_state_position", false, Arrays.asList("scopeKey", "folderKey", "photoCycle", "state", "position"), Arrays.asList("ASC", "ASC", "ASC", "ASC", "ASC")));
        _indicesPhotoShuffleEntries.add(new TableInfo.Index("index_photo_shuffle_entries_photoId", false, Arrays.asList("photoId"), Arrays.asList("ASC")));
        final TableInfo _infoPhotoShuffleEntries = new TableInfo("photo_shuffle_entries", _columnsPhotoShuffleEntries, _foreignKeysPhotoShuffleEntries, _indicesPhotoShuffleEntries);
        final TableInfo _existingPhotoShuffleEntries = TableInfo.read(db, "photo_shuffle_entries");
        if (!_infoPhotoShuffleEntries.equals(_existingPhotoShuffleEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "photo_shuffle_entries(com.example.familyphotoframe.data.db.PhotoShuffleEntryEntity).\n"
                  + " Expected:\n" + _infoPhotoShuffleEntries + "\n"
                  + " Found:\n" + _existingPhotoShuffleEntries);
        }
        final HashMap<String, TableInfo.Column> _columnsShuffleReservations = new HashMap<String, TableInfo.Column>(9);
        _columnsShuffleReservations.put("scopeKey", new TableInfo.Column("scopeKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("reservationId", new TableInfo.Column("reservationId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("folderCycle", new TableInfo.Column("folderCycle", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("folderPosition", new TableInfo.Column("folderPosition", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("folderKey", new TableInfo.Column("folderKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("photoCycle", new TableInfo.Column("photoCycle", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("photoPositionsJson", new TableInfo.Column("photoPositionsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("photoIdsJson", new TableInfo.Column("photoIdsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShuffleReservations.put("createdAtEpochMs", new TableInfo.Column("createdAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysShuffleReservations = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesShuffleReservations = new HashSet<TableInfo.Index>(2);
        _indicesShuffleReservations.add(new TableInfo.Index("index_shuffle_reservations_createdAtEpochMs", false, Arrays.asList("createdAtEpochMs"), Arrays.asList("ASC")));
        _indicesShuffleReservations.add(new TableInfo.Index("index_shuffle_reservations_folderKey", false, Arrays.asList("folderKey"), Arrays.asList("ASC")));
        final TableInfo _infoShuffleReservations = new TableInfo("shuffle_reservations", _columnsShuffleReservations, _foreignKeysShuffleReservations, _indicesShuffleReservations);
        final TableInfo _existingShuffleReservations = TableInfo.read(db, "shuffle_reservations");
        if (!_infoShuffleReservations.equals(_existingShuffleReservations)) {
          return new RoomOpenHelper.ValidationResult(false, "shuffle_reservations(com.example.familyphotoframe.data.db.ShuffleReservationEntity).\n"
                  + " Expected:\n" + _infoShuffleReservations + "\n"
                  + " Found:\n" + _existingShuffleReservations);
        }
        final HashMap<String, TableInfo.Column> _columnsPresentationHistory = new HashMap<String, TableInfo.Column>(7);
        _columnsPresentationHistory.put("presentationId", new TableInfo.Column("presentationId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPresentationHistory.put("scopeKey", new TableInfo.Column("scopeKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPresentationHistory.put("sequence", new TableInfo.Column("sequence", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPresentationHistory.put("folderKey", new TableInfo.Column("folderKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPresentationHistory.put("presentationType", new TableInfo.Column("presentationType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPresentationHistory.put("photoIdsJson", new TableInfo.Column("photoIdsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPresentationHistory.put("committedAtEpochMs", new TableInfo.Column("committedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPresentationHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPresentationHistory = new HashSet<TableInfo.Index>(2);
        _indicesPresentationHistory.add(new TableInfo.Index("index_presentation_history_scopeKey_sequence", true, Arrays.asList("scopeKey", "sequence"), Arrays.asList("ASC", "ASC")));
        _indicesPresentationHistory.add(new TableInfo.Index("index_presentation_history_scopeKey_committedAtEpochMs", false, Arrays.asList("scopeKey", "committedAtEpochMs"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoPresentationHistory = new TableInfo("presentation_history", _columnsPresentationHistory, _foreignKeysPresentationHistory, _indicesPresentationHistory);
        final TableInfo _existingPresentationHistory = TableInfo.read(db, "presentation_history");
        if (!_infoPresentationHistory.equals(_existingPresentationHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "presentation_history(com.example.familyphotoframe.data.db.PresentationHistoryEntity).\n"
                  + " Expected:\n" + _infoPresentationHistory + "\n"
                  + " Found:\n" + _existingPresentationHistory);
        }
        final HashMap<String, TableInfo.Column> _columnsLocalThumbnailCache = new HashMap<String, TableInfo.Column>(7);
        _columnsLocalThumbnailCache.put("cacheKey", new TableInfo.Column("cacheKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalThumbnailCache.put("photoStableId", new TableInfo.Column("photoStableId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalThumbnailCache.put("sizeBucket", new TableInfo.Column("sizeBucket", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalThumbnailCache.put("localFilePathPrivate", new TableInfo.Column("localFilePathPrivate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalThumbnailCache.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalThumbnailCache.put("createdAtEpochMs", new TableInfo.Column("createdAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLocalThumbnailCache.put("lastAccessedAtEpochMs", new TableInfo.Column("lastAccessedAtEpochMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLocalThumbnailCache = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLocalThumbnailCache = new HashSet<TableInfo.Index>(1);
        _indicesLocalThumbnailCache.add(new TableInfo.Index("index_local_thumbnail_cache_photoStableId", false, Arrays.asList("photoStableId"), Arrays.asList("ASC")));
        final TableInfo _infoLocalThumbnailCache = new TableInfo("local_thumbnail_cache", _columnsLocalThumbnailCache, _foreignKeysLocalThumbnailCache, _indicesLocalThumbnailCache);
        final TableInfo _existingLocalThumbnailCache = TableInfo.read(db, "local_thumbnail_cache");
        if (!_infoLocalThumbnailCache.equals(_existingLocalThumbnailCache)) {
          return new RoomOpenHelper.ValidationResult(false, "local_thumbnail_cache(com.example.familyphotoframe.data.db.LocalThumbnailCacheEntity).\n"
                  + " Expected:\n" + _infoLocalThumbnailCache + "\n"
                  + " Found:\n" + _existingLocalThumbnailCache);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "85486d915a0d0b70091573f8dc064298", "b14b3d9da06c36094645632caad620e4");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "photos","source_config","smb_source_config","local_saf_source_config","secrets","cache_index","remembered_browsers","shuffle_scopes","folder_shuffle_entries","folder_photo_cycles","photo_shuffle_entries","shuffle_reservations","presentation_history","local_thumbnail_cache");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `photos`");
      _db.execSQL("DELETE FROM `source_config`");
      _db.execSQL("DELETE FROM `smb_source_config`");
      _db.execSQL("DELETE FROM `local_saf_source_config`");
      _db.execSQL("DELETE FROM `secrets`");
      _db.execSQL("DELETE FROM `cache_index`");
      _db.execSQL("DELETE FROM `remembered_browsers`");
      _db.execSQL("DELETE FROM `shuffle_scopes`");
      _db.execSQL("DELETE FROM `folder_shuffle_entries`");
      _db.execSQL("DELETE FROM `folder_photo_cycles`");
      _db.execSQL("DELETE FROM `photo_shuffle_entries`");
      _db.execSQL("DELETE FROM `shuffle_reservations`");
      _db.execSQL("DELETE FROM `presentation_history`");
      _db.execSQL("DELETE FROM `local_thumbnail_cache`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PhotoDao.class, PhotoDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SourceConfigDao.class, SourceConfigDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SecretDao.class, SecretDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CacheIndexDao.class, CacheIndexDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RememberedBrowserDao.class, RememberedBrowserDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ShuffleDao.class, ShuffleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LocalThumbnailCacheDao.class, LocalThumbnailCacheDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public PhotoDao photoDao() {
    if (_photoDao != null) {
      return _photoDao;
    } else {
      synchronized(this) {
        if(_photoDao == null) {
          _photoDao = new PhotoDao_Impl(this);
        }
        return _photoDao;
      }
    }
  }

  @Override
  public SourceConfigDao sourceConfigDao() {
    if (_sourceConfigDao != null) {
      return _sourceConfigDao;
    } else {
      synchronized(this) {
        if(_sourceConfigDao == null) {
          _sourceConfigDao = new SourceConfigDao_Impl(this);
        }
        return _sourceConfigDao;
      }
    }
  }

  @Override
  public SecretDao secretDao() {
    if (_secretDao != null) {
      return _secretDao;
    } else {
      synchronized(this) {
        if(_secretDao == null) {
          _secretDao = new SecretDao_Impl(this);
        }
        return _secretDao;
      }
    }
  }

  @Override
  public CacheIndexDao cacheIndexDao() {
    if (_cacheIndexDao != null) {
      return _cacheIndexDao;
    } else {
      synchronized(this) {
        if(_cacheIndexDao == null) {
          _cacheIndexDao = new CacheIndexDao_Impl(this);
        }
        return _cacheIndexDao;
      }
    }
  }

  @Override
  public RememberedBrowserDao rememberedBrowserDao() {
    if (_rememberedBrowserDao != null) {
      return _rememberedBrowserDao;
    } else {
      synchronized(this) {
        if(_rememberedBrowserDao == null) {
          _rememberedBrowserDao = new RememberedBrowserDao_Impl(this);
        }
        return _rememberedBrowserDao;
      }
    }
  }

  @Override
  public ShuffleDao shuffleDao() {
    if (_shuffleDao != null) {
      return _shuffleDao;
    } else {
      synchronized(this) {
        if(_shuffleDao == null) {
          _shuffleDao = new ShuffleDao_Impl(this);
        }
        return _shuffleDao;
      }
    }
  }

  @Override
  public LocalThumbnailCacheDao localThumbnailCacheDao() {
    if (_localThumbnailCacheDao != null) {
      return _localThumbnailCacheDao;
    } else {
      synchronized(this) {
        if(_localThumbnailCacheDao == null) {
          _localThumbnailCacheDao = new LocalThumbnailCacheDao_Impl(this);
        }
        return _localThumbnailCacheDao;
      }
    }
  }
}
