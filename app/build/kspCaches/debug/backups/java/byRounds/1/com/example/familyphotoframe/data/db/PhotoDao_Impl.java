package com.example.familyphotoframe.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PhotoDao_Impl implements PhotoDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PhotoItemEntity> __insertionAdapterOfPhotoItemEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteBySource;

  private final SharedSQLiteStatement __preparedStmtOfDeleteStaleForSource;

  private final SharedSQLiteStatement __preparedStmtOfUpdateContentHash;

  private final SharedSQLiteStatement __preparedStmtOfMarkContentHashAttempt;

  private final SharedSQLiteStatement __preparedStmtOfSetFavorite;

  private final SharedSQLiteStatement __preparedStmtOfSetHidden;

  private final SharedSQLiteStatement __preparedStmtOfUnhideAll;

  private final SharedSQLiteStatement __preparedStmtOfSetCacheKey;

  private final SharedSQLiteStatement __preparedStmtOfClearCacheKey;

  private final SharedSQLiteStatement __preparedStmtOfClearAllCacheKeys;

  private final SharedSQLiteStatement __preparedStmtOfClearSuppression;

  private final SharedSQLiteStatement __preparedStmtOfMarkShown;

  private final SharedSQLiteStatement __preparedStmtOfClearDecodeFailure;

  private final SharedSQLiteStatement __preparedStmtOfSuppressDecode;

  private final SharedSQLiteStatement __preparedStmtOfUpdateExif;

  private final SharedSQLiteStatement __preparedStmtOfRecordDecodeFailure;

  public PhotoDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPhotoItemEntity = new EntityInsertionAdapter<PhotoItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `photos` (`id`,`stableId`,`sourceId`,`normalizedPath`,`folderName`,`fileName`,`mimeType`,`sizeBytes`,`fileModifiedEpochMs`,`openToken`,`indexedAtEpochMs`,`isHidden`,`lastShownAtEpochMs`,`decodeFailureCount`,`lastDecodeFailureAtEpochMs`,`width`,`height`,`exifOrientation`,`dateTakenEpochMs`,`isFavorite`,`missingSinceEpochMs`,`cacheKey`,`caption`,`gpsLat`,`gpsLon`,`exifScannedAtEpochMs`,`canonicalDirectory`,`contentSha256`,`contentHashScannedAtEpochMs`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoItemEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getStableId());
        statement.bindString(3, entity.getSourceId());
        statement.bindString(4, entity.getNormalizedPath());
        statement.bindString(5, entity.getFolderName());
        statement.bindString(6, entity.getFileName());
        if (entity.getMimeType() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getMimeType());
        }
        statement.bindLong(8, entity.getSizeBytes());
        statement.bindLong(9, entity.getFileModifiedEpochMs());
        statement.bindString(10, entity.getOpenToken());
        statement.bindLong(11, entity.getIndexedAtEpochMs());
        final int _tmp = entity.isHidden() ? 1 : 0;
        statement.bindLong(12, _tmp);
        if (entity.getLastShownAtEpochMs() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getLastShownAtEpochMs());
        }
        statement.bindLong(14, entity.getDecodeFailureCount());
        if (entity.getLastDecodeFailureAtEpochMs() == null) {
          statement.bindNull(15);
        } else {
          statement.bindLong(15, entity.getLastDecodeFailureAtEpochMs());
        }
        if (entity.getWidth() == null) {
          statement.bindNull(16);
        } else {
          statement.bindLong(16, entity.getWidth());
        }
        if (entity.getHeight() == null) {
          statement.bindNull(17);
        } else {
          statement.bindLong(17, entity.getHeight());
        }
        statement.bindLong(18, entity.getExifOrientation());
        if (entity.getDateTakenEpochMs() == null) {
          statement.bindNull(19);
        } else {
          statement.bindLong(19, entity.getDateTakenEpochMs());
        }
        final int _tmp_1 = entity.isFavorite() ? 1 : 0;
        statement.bindLong(20, _tmp_1);
        if (entity.getMissingSinceEpochMs() == null) {
          statement.bindNull(21);
        } else {
          statement.bindLong(21, entity.getMissingSinceEpochMs());
        }
        if (entity.getCacheKey() == null) {
          statement.bindNull(22);
        } else {
          statement.bindString(22, entity.getCacheKey());
        }
        if (entity.getCaption() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getCaption());
        }
        if (entity.getGpsLat() == null) {
          statement.bindNull(24);
        } else {
          statement.bindDouble(24, entity.getGpsLat());
        }
        if (entity.getGpsLon() == null) {
          statement.bindNull(25);
        } else {
          statement.bindDouble(25, entity.getGpsLon());
        }
        if (entity.getExifScannedAtEpochMs() == null) {
          statement.bindNull(26);
        } else {
          statement.bindLong(26, entity.getExifScannedAtEpochMs());
        }
        statement.bindString(27, entity.getCanonicalDirectory());
        if (entity.getContentSha256() == null) {
          statement.bindNull(28);
        } else {
          statement.bindString(28, entity.getContentSha256());
        }
        if (entity.getContentHashScannedAtEpochMs() == null) {
          statement.bindNull(29);
        } else {
          statement.bindLong(29, entity.getContentHashScannedAtEpochMs());
        }
      }
    };
    this.__preparedStmtOfDeleteBySource = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photos WHERE sourceId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteStaleForSource = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photos WHERE sourceId = ? AND indexedAtEpochMs < ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateContentHash = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE photos SET contentSha256 = ?,\n"
                + "            contentHashScannedAtEpochMs = ?\n"
                + "        WHERE id = ?\n"
                + "        ";
        return _query;
      }
    };
    this.__preparedStmtOfMarkContentHashAttempt = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET contentHashScannedAtEpochMs = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetFavorite = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isFavorite = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetHidden = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isHidden = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUnhideAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isHidden = 0";
        return _query;
      }
    };
    this.__preparedStmtOfSetCacheKey = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET cacheKey = ? WHERE stableId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearCacheKey = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET cacheKey = NULL WHERE cacheKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAllCacheKeys = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET cacheKey = NULL";
        return _query;
      }
    };
    this.__preparedStmtOfClearSuppression = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET decodeFailureCount = 0, lastDecodeFailureAtEpochMs = NULL WHERE sourceId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkShown = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET lastShownAtEpochMs = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearDecodeFailure = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET decodeFailureCount = 0, lastDecodeFailureAtEpochMs = NULL WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSuppressDecode = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET decodeFailureCount = ?, lastDecodeFailureAtEpochMs = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateExif = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE photos SET\n"
                + "            width = ?, height = ?, exifOrientation = ?,\n"
                + "            dateTakenEpochMs = ?, caption = ?,\n"
                + "            gpsLat = ?, gpsLon = ?, exifScannedAtEpochMs = ?\n"
                + "        WHERE id = ?\n"
                + "        ";
        return _query;
      }
    };
    this.__preparedStmtOfRecordDecodeFailure = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE photos\n"
                + "        SET decodeFailureCount = decodeFailureCount + 1, lastDecodeFailureAtEpochMs = ?\n"
                + "        WHERE id = ?\n"
                + "        ";
        return _query;
      }
    };
  }

  @Override
  public Object insertBatch(final List<PhotoItemEntity> items,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPhotoItemEntity.insert(items);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBySource(final String sourceId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteBySource.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sourceId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteBySource.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteStaleForSource(final String sourceId, final long scanStartedEpochMs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteStaleForSource.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sourceId);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, scanStartedEpochMs);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteStaleForSource.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateContentHash(final long id, final String sha256, final long scannedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateContentHash.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sha256);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, scannedAt);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateContentHash.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markContentHashAttempt(final long id, final long attemptedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkContentHashAttempt.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, attemptedAt);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkContentHashAttempt.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setFavorite(final long id, final boolean value,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetFavorite.acquire();
        int _argIndex = 1;
        final int _tmp = value ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetFavorite.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setHidden(final long id, final boolean value,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetHidden.acquire();
        int _argIndex = 1;
        final int _tmp = value ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetHidden.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object unhideAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUnhideAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUnhideAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setCacheKey(final String stableId, final String cacheKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetCacheKey.acquire();
        int _argIndex = 1;
        if (cacheKey == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, cacheKey);
        }
        _argIndex = 2;
        _stmt.bindString(_argIndex, stableId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetCacheKey.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearCacheKey(final String cacheKey, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearCacheKey.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, cacheKey);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearCacheKey.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAllCacheKeys(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAllCacheKeys.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAllCacheKeys.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearSuppression(final String sourceId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearSuppression.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, sourceId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearSuppression.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markShown(final long id, final long ts,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkShown.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, ts);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkShown.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearDecodeFailure(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearDecodeFailure.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearDecodeFailure.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object suppressDecode(final long id, final long ts, final int failureCount,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSuppressDecode.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, failureCount);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, ts);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSuppressDecode.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateExif(final long id, final Integer width, final Integer height,
      final int orientation, final Long dateTaken, final String caption, final Double gpsLat,
      final Double gpsLon, final long scannedAt, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateExif.acquire();
        int _argIndex = 1;
        if (width == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, width);
        }
        _argIndex = 2;
        if (height == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, height);
        }
        _argIndex = 3;
        _stmt.bindLong(_argIndex, orientation);
        _argIndex = 4;
        if (dateTaken == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, dateTaken);
        }
        _argIndex = 5;
        if (caption == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, caption);
        }
        _argIndex = 6;
        if (gpsLat == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindDouble(_argIndex, gpsLat);
        }
        _argIndex = 7;
        if (gpsLon == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindDouble(_argIndex, gpsLon);
        }
        _argIndex = 8;
        _stmt.bindLong(_argIndex, scannedAt);
        _argIndex = 9;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateExif.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object recordDecodeFailure(final long id, final long ts,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRecordDecodeFailure.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, ts);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRecordDecodeFailure.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object rowsForScanMerge(final String sourceId, final List<String> normalizedPaths,
      final Continuation<? super List<PhotoItemEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT * FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId = ");
    _stringBuilder.append("?");
    _stringBuilder.append(" AND normalizedPath IN (");
    final int _inputSize = normalizedPaths.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 1 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    for (String _item : normalizedPaths) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item_1;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item_1 = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item_1);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photos";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object countForSource(final String sourceId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photos WHERE sourceId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> displayableCountFlow(final String sourceId, final int maxFailures,
      final int allowHeif) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM photos\n"
            + "        WHERE sourceId = ? AND isHidden = 0 AND decodeFailureCount < ?\n"
            + "          AND (? = 1 OR (\n"
            + "            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'\n"
            + "            AND lower(COALESCE(mimeType, '')) NOT IN\n"
            + "              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "          ))\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 3;
    _statement.bindLong(_argIndex, allowHeif);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"photos"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object leastRecentWindow(final String sourceId, final int maxFailures, final int window,
      final int allowHeif, final Continuation<? super List<PhotoItemEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM photos\n"
            + "        WHERE sourceId = ? AND isHidden = 0 AND decodeFailureCount < ?\n"
            + "          AND (? = 1 OR (\n"
            + "            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'\n"
            + "            AND lower(COALESCE(mimeType, '')) NOT IN\n"
            + "              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "          ))\n"
            + "        ORDER BY lastShownAtEpochMs ASC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 3;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 4;
    _statement.bindLong(_argIndex, window);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object displayableFolderNames(final List<String> sourceIds, final int maxFailures,
      final int favoritesOnly, final int cachedOnly, final int allowHeif, final int allFolders,
      final List<String> folders, final Continuation<? super List<String>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT DISTINCT folderName FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR isFavorite = 1)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR cacheKey IS NOT NULL)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (sourceId || char(31) || canonicalDirectory) IN (");
    final int _inputSize_1 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_1);
    _stringBuilder.append(") OR folderName IN (");
    final int _inputSize_2 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_2);
    _stringBuilder.append("))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY folderName COLLATE NOCASE ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 5 + _inputSize + _inputSize_1 + _inputSize_2;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 3 + _inputSize;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 4 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 5 + _inputSize;
    _statement.bindLong(_argIndex, allFolders);
    _argIndex = 6 + _inputSize;
    for (String _item_1 : folders) {
      _statement.bindString(_argIndex, _item_1);
      _argIndex++;
    }
    _argIndex = 6 + _inputSize + _inputSize_1;
    for (String _item_2 : folders) {
      _statement.bindString(_argIndex, _item_2);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item_3;
            _item_3 = _cursor.getString(0);
            _result.add(_item_3);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object leastRecentWindowInFolder(final List<String> sourceIds, final String folderName,
      final int maxFailures, final int window, final int favoritesOnly, final int cachedOnly,
      final int allowHeif, final Continuation<? super List<PhotoItemEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT * FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND folderName = ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR isFavorite = 1)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR cacheKey IS NOT NULL)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY lastShownAtEpochMs ASC, id ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        LIMIT ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 6 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindString(_argIndex, folderName);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 3 + _inputSize;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 4 + _inputSize;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 5 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 6 + _inputSize;
    _statement.bindLong(_argIndex, window);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item_1;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item_1 = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item_1);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object shuffleEligibleFolders(final List<String> sourceIds, final int maxFailures,
      final int favoritesOnly, final int cachedOnly, final int allowHeif, final int allFolders,
      final List<String> folders, final Continuation<? super List<ShuffleFolderRow>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT sourceId, canonicalDirectory, COUNT(*) AS memberCount");
    _stringBuilder.append("\n");
    _stringBuilder.append("        FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR isFavorite = 1)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR cacheKey IS NOT NULL)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (sourceId || char(31) || canonicalDirectory) IN (");
    final int _inputSize_1 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_1);
    _stringBuilder.append(") OR folderName IN (");
    final int _inputSize_2 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_2);
    _stringBuilder.append("))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        GROUP BY sourceId, canonicalDirectory");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY sourceId ASC, canonicalDirectory COLLATE NOCASE ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 5 + _inputSize + _inputSize_1 + _inputSize_2;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 3 + _inputSize;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 4 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 5 + _inputSize;
    _statement.bindLong(_argIndex, allFolders);
    _argIndex = 6 + _inputSize;
    for (String _item_1 : folders) {
      _statement.bindString(_argIndex, _item_1);
      _argIndex++;
    }
    _argIndex = 6 + _inputSize + _inputSize_1;
    for (String _item_2 : folders) {
      _statement.bindString(_argIndex, _item_2);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ShuffleFolderRow>>() {
      @Override
      @NonNull
      public List<ShuffleFolderRow> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSourceId = 0;
          final int _cursorIndexOfCanonicalDirectory = 1;
          final int _cursorIndexOfMemberCount = 2;
          final List<ShuffleFolderRow> _result = new ArrayList<ShuffleFolderRow>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShuffleFolderRow _item_3;
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final int _tmpMemberCount;
            _tmpMemberCount = _cursor.getInt(_cursorIndexOfMemberCount);
            _item_3 = new ShuffleFolderRow(_tmpSourceId,_tmpCanonicalDirectory,_tmpMemberCount);
            _result.add(_item_3);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object shuffleEligibilityRowsForFolder(final String sourceId,
      final String canonicalDirectory, final int maxFailures, final int favoritesOnly,
      final int cachedOnly, final int allowHeif,
      final Continuation<? super List<ShufflePhotoRow>> $completion) {
    final String _sql = "\n"
            + "        SELECT id, stableId, sourceId, normalizedPath, canonicalDirectory, contentSha256,\n"
            + "               sizeBytes, fileModifiedEpochMs, width, height, exifOrientation, decodeFailureCount\n"
            + "        FROM photos\n"
            + "        WHERE sourceId = ? AND canonicalDirectory = ?\n"
            + "          AND isHidden = 0 AND decodeFailureCount < ?\n"
            + "          AND (? = 0 OR isFavorite = 1)\n"
            + "          AND (? = 0 OR cacheKey IS NOT NULL)\n"
            + "          AND (? = 1 OR (\n"
            + "            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'\n"
            + "            AND lower(COALESCE(mimeType, '')) NOT IN\n"
            + "              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "          ))\n"
            + "        ORDER BY normalizedPath ASC, id ASC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 6);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    _statement.bindString(_argIndex, canonicalDirectory);
    _argIndex = 3;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 4;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 5;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 6;
    _statement.bindLong(_argIndex, allowHeif);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ShufflePhotoRow>>() {
      @Override
      @NonNull
      public List<ShufflePhotoRow> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = 0;
          final int _cursorIndexOfStableId = 1;
          final int _cursorIndexOfSourceId = 2;
          final int _cursorIndexOfNormalizedPath = 3;
          final int _cursorIndexOfCanonicalDirectory = 4;
          final int _cursorIndexOfContentSha256 = 5;
          final int _cursorIndexOfSizeBytes = 6;
          final int _cursorIndexOfFileModifiedEpochMs = 7;
          final int _cursorIndexOfWidth = 8;
          final int _cursorIndexOfHeight = 9;
          final int _cursorIndexOfExifOrientation = 10;
          final int _cursorIndexOfDecodeFailureCount = 11;
          final List<ShufflePhotoRow> _result = new ArrayList<ShufflePhotoRow>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShufflePhotoRow _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            _item = new ShufflePhotoRow(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpCanonicalDirectory,_tmpContentSha256,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDecodeFailureCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object displayableIds(final List<String> sourceIds, final int maxFailures,
      final int favoritesOnly, final int cachedOnly, final int allowHeif, final int allFolders,
      final List<String> folders, final Continuation<? super List<Long>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT id FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR isFavorite = 1)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR cacheKey IS NOT NULL)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (sourceId || char(31) || canonicalDirectory) IN (");
    final int _inputSize_1 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_1);
    _stringBuilder.append(") OR folderName IN (");
    final int _inputSize_2 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_2);
    _stringBuilder.append("))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY id ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 5 + _inputSize + _inputSize_1 + _inputSize_2;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 3 + _inputSize;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 4 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 5 + _inputSize;
    _statement.bindLong(_argIndex, allFolders);
    _argIndex = 6 + _inputSize;
    for (String _item_1 : folders) {
      _statement.bindString(_argIndex, _item_1);
      _argIndex++;
    }
    _argIndex = 6 + _inputSize + _inputSize_1;
    for (String _item_2 : folders) {
      _statement.bindString(_argIndex, _item_2);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item_3;
            _item_3 = _cursor.getLong(0);
            _result.add(_item_3);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object displayableIdsByDateTakenDesc(final List<String> sourceIds, final int maxFailures,
      final int favoritesOnly, final int cachedOnly, final int allowHeif, final int allFolders,
      final List<String> folders, final Continuation<? super List<Long>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT id FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR isFavorite = 1)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR cacheKey IS NOT NULL)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (sourceId || char(31) || canonicalDirectory) IN (");
    final int _inputSize_1 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_1);
    _stringBuilder.append(") OR folderName IN (");
    final int _inputSize_2 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_2);
    _stringBuilder.append("))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY (dateTakenEpochMs IS NULL) ASC, dateTakenEpochMs DESC, fileModifiedEpochMs DESC, id ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 5 + _inputSize + _inputSize_1 + _inputSize_2;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 3 + _inputSize;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 4 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 5 + _inputSize;
    _statement.bindLong(_argIndex, allFolders);
    _argIndex = 6 + _inputSize;
    for (String _item_1 : folders) {
      _statement.bindString(_argIndex, _item_1);
      _argIndex++;
    }
    _argIndex = 6 + _inputSize + _inputSize_1;
    for (String _item_2 : folders) {
      _statement.bindString(_argIndex, _item_2);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item_3;
            _item_3 = _cursor.getLong(0);
            _result.add(_item_3);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object displayableIdsByDateTakenAsc(final List<String> sourceIds, final int maxFailures,
      final int favoritesOnly, final int cachedOnly, final int allowHeif, final int allFolders,
      final List<String> folders, final Continuation<? super List<Long>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT id FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR isFavorite = 1)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 0 OR cacheKey IS NOT NULL)");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (sourceId || char(31) || canonicalDirectory) IN (");
    final int _inputSize_1 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_1);
    _stringBuilder.append(") OR folderName IN (");
    final int _inputSize_2 = folders.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize_2);
    _stringBuilder.append("))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY (dateTakenEpochMs IS NULL) ASC, dateTakenEpochMs ASC, fileModifiedEpochMs ASC, id ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 5 + _inputSize + _inputSize_1 + _inputSize_2;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 3 + _inputSize;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 4 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 5 + _inputSize;
    _statement.bindLong(_argIndex, allFolders);
    _argIndex = 6 + _inputSize;
    for (String _item_1 : folders) {
      _statement.bindString(_argIndex, _item_1);
      _argIndex++;
    }
    _argIndex = 6 + _inputSize + _inputSize_1;
    for (String _item_2 : folders) {
      _statement.bindString(_argIndex, _item_2);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item_3;
            _item_3 = _cursor.getLong(0);
            _result.add(_item_3);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object cachedCount(final List<String> sourceIds, final int maxFailures,
      final int allowHeif, final Continuation<? super Integer> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT COUNT(*) FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0 AND decodeFailureCount < ");
    _stringBuilder.append("?");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND cacheKey IS NOT NULL");
    _stringBuilder.append("\n");
    _stringBuilder.append("          AND (");
    _stringBuilder.append("?");
    _stringBuilder.append(" = 1 OR (");
    _stringBuilder.append("\n");
    _stringBuilder.append("            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'");
    _stringBuilder.append("\n");
    _stringBuilder.append("            AND lower(COALESCE(mimeType, '')) NOT IN");
    _stringBuilder.append("\n");
    _stringBuilder.append("              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')");
    _stringBuilder.append("\n");
    _stringBuilder.append("          ))");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 2 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2 + _inputSize;
    _statement.bindLong(_argIndex, allowHeif);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object folderSummaries(final List<String> sourceIds,
      final Continuation<? super List<FolderSummary>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("\n");
    _stringBuilder.append("        SELECT sourceId, canonicalDirectory, MIN(folderName) AS name, COUNT(*) AS photoCount");
    _stringBuilder.append("\n");
    _stringBuilder.append("        FROM photos");
    _stringBuilder.append("\n");
    _stringBuilder.append("        WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isHidden = 0");
    _stringBuilder.append("\n");
    _stringBuilder.append("        GROUP BY sourceId, canonicalDirectory");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ORDER BY sourceId COLLATE NOCASE ASC, canonicalDirectory COLLATE NOCASE ASC");
    _stringBuilder.append("\n");
    _stringBuilder.append("        ");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FolderSummary>>() {
      @Override
      @NonNull
      public List<FolderSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSourceId = 0;
          final int _cursorIndexOfCanonicalDirectory = 1;
          final int _cursorIndexOfName = 2;
          final int _cursorIndexOfPhotoCount = 3;
          final List<FolderSummary> _result = new ArrayList<FolderSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FolderSummary _item_1;
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpPhotoCount;
            _tmpPhotoCount = _cursor.getInt(_cursorIndexOfPhotoCount);
            _item_1 = new FolderSummary(_tmpSourceId,_tmpCanonicalDirectory,_tmpName,_tmpPhotoCount);
            _result.add(_item_1);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object collageCandidates(final String sourceId, final String folderName,
      final long anchorId, final long anchorTime, final int maxFailures, final int favoritesOnly,
      final int cachedOnly, final int allowHeif, final int limit,
      final Continuation<? super List<PhotoItemEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM photos\n"
            + "        WHERE sourceId = ? AND folderName = ?\n"
            + "          AND id != ? AND isHidden = 0 AND decodeFailureCount < ?\n"
            + "          AND (? = 0 OR isFavorite = 1)\n"
            + "          AND (? = 0 OR cacheKey IS NOT NULL)\n"
            + "          AND (? = 1 OR (\n"
            + "            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'\n"
            + "            AND lower(COALESCE(mimeType, '')) NOT IN\n"
            + "              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "          ))\n"
            + "        ORDER BY\n"
            + "          CASE WHEN ? > 0\n"
            + "            THEN ABS(COALESCE(dateTakenEpochMs, fileModifiedEpochMs) - ?)\n"
            + "            ELSE 0 END ASC,\n"
            + "          (lastShownAtEpochMs IS NOT NULL) ASC,\n"
            + "          lastShownAtEpochMs ASC,\n"
            + "          id ASC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 10);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    _statement.bindString(_argIndex, folderName);
    _argIndex = 3;
    _statement.bindLong(_argIndex, anchorId);
    _argIndex = 4;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 5;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 6;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 7;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 8;
    _statement.bindLong(_argIndex, anchorTime);
    _argIndex = 9;
    _statement.bindLong(_argIndex, anchorTime);
    _argIndex = 10;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object previewFolderCandidates(final String sourceId, final String canonicalDirectory,
      final int maxFailures, final int favoritesOnly, final int cachedOnly, final int allowHeif,
      final int limit, final Continuation<? super List<PhotoItemEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM photos\n"
            + "        WHERE sourceId = ? AND canonicalDirectory = ?\n"
            + "          AND isHidden = 0 AND decodeFailureCount < ?\n"
            + "          AND (? = 0 OR isFavorite = 1)\n"
            + "          AND (? = 0 OR cacheKey IS NOT NULL)\n"
            + "          AND (? = 1 OR (\n"
            + "            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'\n"
            + "            AND lower(COALESCE(mimeType, '')) NOT IN\n"
            + "              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "          ))\n"
            + "        ORDER BY lastShownAtEpochMs ASC, id ASC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 7);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    _statement.bindString(_argIndex, canonicalDirectory);
    _argIndex = 3;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 4;
    _statement.bindLong(_argIndex, favoritesOnly);
    _argIndex = 5;
    _statement.bindLong(_argIndex, cachedOnly);
    _argIndex = 6;
    _statement.bindLong(_argIndex, allowHeif);
    _argIndex = 7;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object photosNeedingContentHash(final String sourceId, final int limit,
      final Continuation<? super List<PhotoItemEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM photos\n"
            + "        WHERE sourceId = ? AND contentSha256 IS NULL AND isHidden = 0\n"
            + "        ORDER BY contentHashScannedAtEpochMs ASC, id ASC\n"
            + "        LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object favoriteCount(final List<String> sourceIds,
      final Continuation<? super Integer> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT COUNT(*) FROM photos WHERE sourceId IN (");
    final int _inputSize = sourceIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") AND isFavorite = 1 AND isHidden = 0");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : sourceIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object hiddenCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photos WHERE isHidden = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object favoriteCountAll(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photos WHERE isFavorite = 1 AND isHidden = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object eligibleCount(final int maxFailures, final int allowHeif,
      final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM photos\n"
            + "        WHERE isHidden = 0 AND decodeFailureCount < ?\n"
            + "          AND (? = 1 OR (\n"
            + "            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'\n"
            + "            AND lower(COALESCE(mimeType, '')) NOT IN\n"
            + "              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "          ))\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2;
    _statement.bindLong(_argIndex, allowHeif);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object failedOrUnsupportedCount(final int maxFailures, final int allowHeif,
      final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM photos\n"
            + "        WHERE decodeFailureCount >= ? OR (? = 0 AND (\n"
            + "          lower(fileName) LIKE '%.heic' OR lower(fileName) LIKE '%.heif'\n"
            + "          OR lower(COALESCE(mimeType, '')) IN\n"
            + "            ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')\n"
            + "        ))\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, maxFailures);
    _argIndex = 2;
    _statement.bindLong(_argIndex, allowHeif);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object byId(final long id, final Continuation<? super PhotoItemEntity> $completion) {
    final String _sql = "SELECT * FROM photos WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PhotoItemEntity>() {
      @Override
      @Nullable
      public PhotoItemEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final PhotoItemEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _result = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object byIds(final List<Long> ids,
      final Continuation<? super List<PhotoItemEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM photos WHERE id IN (");
    final int _inputSize = ids.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (long _item : ids) {
      _statement.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoItemEntity>>() {
      @Override
      @NonNull
      public List<PhotoItemEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "stableId");
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfNormalizedPath = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedPath");
          final int _cursorIndexOfFolderName = CursorUtil.getColumnIndexOrThrow(_cursor, "folderName");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfFileModifiedEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "fileModifiedEpochMs");
          final int _cursorIndexOfOpenToken = CursorUtil.getColumnIndexOrThrow(_cursor, "openToken");
          final int _cursorIndexOfIndexedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "indexedAtEpochMs");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLastShownAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastShownAtEpochMs");
          final int _cursorIndexOfDecodeFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "decodeFailureCount");
          final int _cursorIndexOfLastDecodeFailureAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastDecodeFailureAtEpochMs");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfExifOrientation = CursorUtil.getColumnIndexOrThrow(_cursor, "exifOrientation");
          final int _cursorIndexOfDateTakenEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTakenEpochMs");
          final int _cursorIndexOfIsFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "isFavorite");
          final int _cursorIndexOfMissingSinceEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "missingSinceEpochMs");
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "caption");
          final int _cursorIndexOfGpsLat = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLat");
          final int _cursorIndexOfGpsLon = CursorUtil.getColumnIndexOrThrow(_cursor, "gpsLon");
          final int _cursorIndexOfExifScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "exifScannedAtEpochMs");
          final int _cursorIndexOfCanonicalDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "canonicalDirectory");
          final int _cursorIndexOfContentSha256 = CursorUtil.getColumnIndexOrThrow(_cursor, "contentSha256");
          final int _cursorIndexOfContentHashScannedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "contentHashScannedAtEpochMs");
          final List<PhotoItemEntity> _result = new ArrayList<PhotoItemEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoItemEntity _item_1;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpStableId;
            _tmpStableId = _cursor.getString(_cursorIndexOfStableId);
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpNormalizedPath;
            _tmpNormalizedPath = _cursor.getString(_cursorIndexOfNormalizedPath);
            final String _tmpFolderName;
            _tmpFolderName = _cursor.getString(_cursorIndexOfFolderName);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpMimeType;
            if (_cursor.isNull(_cursorIndexOfMimeType)) {
              _tmpMimeType = null;
            } else {
              _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            }
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpFileModifiedEpochMs;
            _tmpFileModifiedEpochMs = _cursor.getLong(_cursorIndexOfFileModifiedEpochMs);
            final String _tmpOpenToken;
            _tmpOpenToken = _cursor.getString(_cursorIndexOfOpenToken);
            final long _tmpIndexedAtEpochMs;
            _tmpIndexedAtEpochMs = _cursor.getLong(_cursorIndexOfIndexedAtEpochMs);
            final boolean _tmpIsHidden;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp != 0;
            final Long _tmpLastShownAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastShownAtEpochMs)) {
              _tmpLastShownAtEpochMs = null;
            } else {
              _tmpLastShownAtEpochMs = _cursor.getLong(_cursorIndexOfLastShownAtEpochMs);
            }
            final int _tmpDecodeFailureCount;
            _tmpDecodeFailureCount = _cursor.getInt(_cursorIndexOfDecodeFailureCount);
            final Long _tmpLastDecodeFailureAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastDecodeFailureAtEpochMs)) {
              _tmpLastDecodeFailureAtEpochMs = null;
            } else {
              _tmpLastDecodeFailureAtEpochMs = _cursor.getLong(_cursorIndexOfLastDecodeFailureAtEpochMs);
            }
            final Integer _tmpWidth;
            if (_cursor.isNull(_cursorIndexOfWidth)) {
              _tmpWidth = null;
            } else {
              _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            }
            final Integer _tmpHeight;
            if (_cursor.isNull(_cursorIndexOfHeight)) {
              _tmpHeight = null;
            } else {
              _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            }
            final int _tmpExifOrientation;
            _tmpExifOrientation = _cursor.getInt(_cursorIndexOfExifOrientation);
            final Long _tmpDateTakenEpochMs;
            if (_cursor.isNull(_cursorIndexOfDateTakenEpochMs)) {
              _tmpDateTakenEpochMs = null;
            } else {
              _tmpDateTakenEpochMs = _cursor.getLong(_cursorIndexOfDateTakenEpochMs);
            }
            final boolean _tmpIsFavorite;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsFavorite);
            _tmpIsFavorite = _tmp_1 != 0;
            final Long _tmpMissingSinceEpochMs;
            if (_cursor.isNull(_cursorIndexOfMissingSinceEpochMs)) {
              _tmpMissingSinceEpochMs = null;
            } else {
              _tmpMissingSinceEpochMs = _cursor.getLong(_cursorIndexOfMissingSinceEpochMs);
            }
            final String _tmpCacheKey;
            if (_cursor.isNull(_cursorIndexOfCacheKey)) {
              _tmpCacheKey = null;
            } else {
              _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            }
            final String _tmpCaption;
            if (_cursor.isNull(_cursorIndexOfCaption)) {
              _tmpCaption = null;
            } else {
              _tmpCaption = _cursor.getString(_cursorIndexOfCaption);
            }
            final Double _tmpGpsLat;
            if (_cursor.isNull(_cursorIndexOfGpsLat)) {
              _tmpGpsLat = null;
            } else {
              _tmpGpsLat = _cursor.getDouble(_cursorIndexOfGpsLat);
            }
            final Double _tmpGpsLon;
            if (_cursor.isNull(_cursorIndexOfGpsLon)) {
              _tmpGpsLon = null;
            } else {
              _tmpGpsLon = _cursor.getDouble(_cursorIndexOfGpsLon);
            }
            final Long _tmpExifScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExifScannedAtEpochMs)) {
              _tmpExifScannedAtEpochMs = null;
            } else {
              _tmpExifScannedAtEpochMs = _cursor.getLong(_cursorIndexOfExifScannedAtEpochMs);
            }
            final String _tmpCanonicalDirectory;
            _tmpCanonicalDirectory = _cursor.getString(_cursorIndexOfCanonicalDirectory);
            final String _tmpContentSha256;
            if (_cursor.isNull(_cursorIndexOfContentSha256)) {
              _tmpContentSha256 = null;
            } else {
              _tmpContentSha256 = _cursor.getString(_cursorIndexOfContentSha256);
            }
            final Long _tmpContentHashScannedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfContentHashScannedAtEpochMs)) {
              _tmpContentHashScannedAtEpochMs = null;
            } else {
              _tmpContentHashScannedAtEpochMs = _cursor.getLong(_cursorIndexOfContentHashScannedAtEpochMs);
            }
            _item_1 = new PhotoItemEntity(_tmpId,_tmpStableId,_tmpSourceId,_tmpNormalizedPath,_tmpFolderName,_tmpFileName,_tmpMimeType,_tmpSizeBytes,_tmpFileModifiedEpochMs,_tmpOpenToken,_tmpIndexedAtEpochMs,_tmpIsHidden,_tmpLastShownAtEpochMs,_tmpDecodeFailureCount,_tmpLastDecodeFailureAtEpochMs,_tmpWidth,_tmpHeight,_tmpExifOrientation,_tmpDateTakenEpochMs,_tmpIsFavorite,_tmpMissingSinceEpochMs,_tmpCacheKey,_tmpCaption,_tmpGpsLat,_tmpGpsLon,_tmpExifScannedAtEpochMs,_tmpCanonicalDirectory,_tmpContentSha256,_tmpContentHashScannedAtEpochMs);
            _result.add(_item_1);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object setFavorites(final List<Long> ids, final boolean value,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE photos SET isFavorite = ");
        _stringBuilder.append("?");
        _stringBuilder.append(" WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        final int _tmp = value ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        for (long _item : ids) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setHiddenBatch(final List<Long> ids, final boolean value,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE photos SET isHidden = ");
        _stringBuilder.append("?");
        _stringBuilder.append(" WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        final int _tmp = value ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        for (long _item : ids) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
