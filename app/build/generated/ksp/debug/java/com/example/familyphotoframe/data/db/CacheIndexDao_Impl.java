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
import java.lang.Exception;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CacheIndexDao_Impl implements CacheIndexDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CacheIndexEntity> __insertionAdapterOfCacheIndexEntity;

  private final SharedSQLiteStatement __preparedStmtOfTouch;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  public CacheIndexDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCacheIndexEntity = new EntityInsertionAdapter<CacheIndexEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cache_index` (`cacheKey`,`photoStableId`,`localFilePathPrivate`,`sizeBytes`,`createdAtEpochMs`,`lastAccessedAtEpochMs`,`verifiedDecodeOk`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CacheIndexEntity entity) {
        statement.bindString(1, entity.getCacheKey());
        statement.bindString(2, entity.getPhotoStableId());
        statement.bindString(3, entity.getLocalFilePathPrivate());
        statement.bindLong(4, entity.getSizeBytes());
        statement.bindLong(5, entity.getCreatedAtEpochMs());
        statement.bindLong(6, entity.getLastAccessedAtEpochMs());
        final int _tmp = entity.getVerifiedDecodeOk() ? 1 : 0;
        statement.bindLong(7, _tmp);
      }
    };
    this.__preparedStmtOfTouch = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE cache_index SET lastAccessedAtEpochMs = ? WHERE cacheKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cache_index WHERE cacheKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cache_index";
        return _query;
      }
    };
  }

  @Override
  public Object put(final CacheIndexEntity entry, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCacheIndexEntity.insert(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object touch(final String key, final long ts,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfTouch.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, ts);
        _argIndex = 2;
        _stmt.bindString(_argIndex, key);
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
          __preparedStmtOfTouch.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String key, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, key);
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
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clear(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClear.acquire();
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
          __preparedStmtOfClear.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String key, final Continuation<? super CacheIndexEntity> $completion) {
    final String _sql = "SELECT * FROM cache_index WHERE cacheKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CacheIndexEntity>() {
      @Override
      @Nullable
      public CacheIndexEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfPhotoStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "photoStableId");
          final int _cursorIndexOfLocalFilePathPrivate = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePathPrivate");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfLastAccessedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastAccessedAtEpochMs");
          final int _cursorIndexOfVerifiedDecodeOk = CursorUtil.getColumnIndexOrThrow(_cursor, "verifiedDecodeOk");
          final CacheIndexEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpCacheKey;
            _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            final String _tmpPhotoStableId;
            _tmpPhotoStableId = _cursor.getString(_cursorIndexOfPhotoStableId);
            final String _tmpLocalFilePathPrivate;
            _tmpLocalFilePathPrivate = _cursor.getString(_cursorIndexOfLocalFilePathPrivate);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            final long _tmpLastAccessedAtEpochMs;
            _tmpLastAccessedAtEpochMs = _cursor.getLong(_cursorIndexOfLastAccessedAtEpochMs);
            final boolean _tmpVerifiedDecodeOk;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfVerifiedDecodeOk);
            _tmpVerifiedDecodeOk = _tmp != 0;
            _result = new CacheIndexEntity(_tmpCacheKey,_tmpPhotoStableId,_tmpLocalFilePathPrivate,_tmpSizeBytes,_tmpCreatedAtEpochMs,_tmpLastAccessedAtEpochMs,_tmpVerifiedDecodeOk);
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
  public Object totalSizeBytes(final Continuation<? super Long> $completion) {
    final String _sql = "SELECT COALESCE(SUM(sizeBytes), 0) FROM cache_index";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp;
            _tmp = _cursor.getLong(0);
            _result = _tmp;
          } else {
            _result = 0L;
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
  public Object evictionCandidates(final List<String> protectedKeys, final int limit,
      final Continuation<? super List<CacheIndexEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM cache_index WHERE cacheKey NOT IN (");
    final int _inputSize = protectedKeys.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(") ORDER BY lastAccessedAtEpochMs ASC LIMIT ");
    _stringBuilder.append("?");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 1 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : protectedKeys) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    _argIndex = 1 + _inputSize;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CacheIndexEntity>>() {
      @Override
      @NonNull
      public List<CacheIndexEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCacheKey = CursorUtil.getColumnIndexOrThrow(_cursor, "cacheKey");
          final int _cursorIndexOfPhotoStableId = CursorUtil.getColumnIndexOrThrow(_cursor, "photoStableId");
          final int _cursorIndexOfLocalFilePathPrivate = CursorUtil.getColumnIndexOrThrow(_cursor, "localFilePathPrivate");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfLastAccessedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastAccessedAtEpochMs");
          final int _cursorIndexOfVerifiedDecodeOk = CursorUtil.getColumnIndexOrThrow(_cursor, "verifiedDecodeOk");
          final List<CacheIndexEntity> _result = new ArrayList<CacheIndexEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CacheIndexEntity _item_1;
            final String _tmpCacheKey;
            _tmpCacheKey = _cursor.getString(_cursorIndexOfCacheKey);
            final String _tmpPhotoStableId;
            _tmpPhotoStableId = _cursor.getString(_cursorIndexOfPhotoStableId);
            final String _tmpLocalFilePathPrivate;
            _tmpLocalFilePathPrivate = _cursor.getString(_cursorIndexOfLocalFilePathPrivate);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            final long _tmpLastAccessedAtEpochMs;
            _tmpLastAccessedAtEpochMs = _cursor.getLong(_cursorIndexOfLastAccessedAtEpochMs);
            final boolean _tmpVerifiedDecodeOk;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfVerifiedDecodeOk);
            _tmpVerifiedDecodeOk = _tmp != 0;
            _item_1 = new CacheIndexEntity(_tmpCacheKey,_tmpPhotoStableId,_tmpLocalFilePathPrivate,_tmpSizeBytes,_tmpCreatedAtEpochMs,_tmpLastAccessedAtEpochMs,_tmpVerifiedDecodeOk);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
