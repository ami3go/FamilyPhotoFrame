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
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
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
public final class RememberedBrowserDao_Impl implements RememberedBrowserDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RememberedBrowserEntity> __insertionAdapterOfRememberedBrowserEntity;

  private final SharedSQLiteStatement __preparedStmtOfRevoke;

  private final SharedSQLiteStatement __preparedStmtOfRevokeAllExcept;

  private final SharedSQLiteStatement __preparedStmtOfRevokeAll;

  private final SharedSQLiteStatement __preparedStmtOfPurgeOld;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public RememberedBrowserDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRememberedBrowserEntity = new EntityInsertionAdapter<RememberedBrowserEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `remembered_browsers` (`id`,`currentTokenHash`,`previousTokenHash`,`previousTokenValidUntilEpochMs`,`retiredTokenHash`,`label`,`createdAtEpochMs`,`lastUsedAtEpochMs`,`expiresAtEpochMs`,`revokedAtEpochMs`,`browserSummary`,`osSummary`,`lastTrustedWallClockEpochMs`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RememberedBrowserEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindBlob(2, entity.getCurrentTokenHash());
        if (entity.getPreviousTokenHash() == null) {
          statement.bindNull(3);
        } else {
          statement.bindBlob(3, entity.getPreviousTokenHash());
        }
        if (entity.getPreviousTokenValidUntilEpochMs() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPreviousTokenValidUntilEpochMs());
        }
        if (entity.getRetiredTokenHash() == null) {
          statement.bindNull(5);
        } else {
          statement.bindBlob(5, entity.getRetiredTokenHash());
        }
        statement.bindString(6, entity.getLabel());
        statement.bindLong(7, entity.getCreatedAtEpochMs());
        statement.bindLong(8, entity.getLastUsedAtEpochMs());
        if (entity.getExpiresAtEpochMs() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getExpiresAtEpochMs());
        }
        if (entity.getRevokedAtEpochMs() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getRevokedAtEpochMs());
        }
        if (entity.getBrowserSummary() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getBrowserSummary());
        }
        if (entity.getOsSummary() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getOsSummary());
        }
        statement.bindLong(13, entity.getLastTrustedWallClockEpochMs());
      }
    };
    this.__preparedStmtOfRevoke = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE remembered_browsers SET revokedAtEpochMs = ? WHERE id = ? AND revokedAtEpochMs IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfRevokeAllExcept = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE remembered_browsers SET revokedAtEpochMs = ? WHERE id != ? AND revokedAtEpochMs IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfRevokeAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE remembered_browsers SET revokedAtEpochMs = ? WHERE revokedAtEpochMs IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfPurgeOld = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM remembered_browsers WHERE (expiresAtEpochMs IS NOT NULL AND expiresAtEpochMs < ?) OR (revokedAtEpochMs IS NOT NULL AND revokedAtEpochMs < ?)";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM remembered_browsers";
        return _query;
      }
    };
  }

  @Override
  public Object put(final RememberedBrowserEntity record,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfRememberedBrowserEntity.insert(record);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object revoke(final String id, final long now,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRevoke.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, now);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRevoke.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object revokeAllExcept(final String keepId, final long now,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRevokeAllExcept.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, now);
        _argIndex = 2;
        _stmt.bindString(_argIndex, keepId);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRevokeAllExcept.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object revokeAll(final long now, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRevokeAll.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, now);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRevokeAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object purgeOld(final long expiredBefore,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfPurgeOld.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, expiredBefore);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, expiredBefore);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfPurgeOld.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String id,
      final Continuation<? super RememberedBrowserEntity> $completion) {
    final String _sql = "SELECT * FROM remembered_browsers WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RememberedBrowserEntity>() {
      @Override
      @Nullable
      public RememberedBrowserEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCurrentTokenHash = CursorUtil.getColumnIndexOrThrow(_cursor, "currentTokenHash");
          final int _cursorIndexOfPreviousTokenHash = CursorUtil.getColumnIndexOrThrow(_cursor, "previousTokenHash");
          final int _cursorIndexOfPreviousTokenValidUntilEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "previousTokenValidUntilEpochMs");
          final int _cursorIndexOfRetiredTokenHash = CursorUtil.getColumnIndexOrThrow(_cursor, "retiredTokenHash");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfLastUsedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAtEpochMs");
          final int _cursorIndexOfExpiresAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "expiresAtEpochMs");
          final int _cursorIndexOfRevokedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "revokedAtEpochMs");
          final int _cursorIndexOfBrowserSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "browserSummary");
          final int _cursorIndexOfOsSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "osSummary");
          final int _cursorIndexOfLastTrustedWallClockEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastTrustedWallClockEpochMs");
          final RememberedBrowserEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final byte[] _tmpCurrentTokenHash;
            _tmpCurrentTokenHash = _cursor.getBlob(_cursorIndexOfCurrentTokenHash);
            final byte[] _tmpPreviousTokenHash;
            if (_cursor.isNull(_cursorIndexOfPreviousTokenHash)) {
              _tmpPreviousTokenHash = null;
            } else {
              _tmpPreviousTokenHash = _cursor.getBlob(_cursorIndexOfPreviousTokenHash);
            }
            final Long _tmpPreviousTokenValidUntilEpochMs;
            if (_cursor.isNull(_cursorIndexOfPreviousTokenValidUntilEpochMs)) {
              _tmpPreviousTokenValidUntilEpochMs = null;
            } else {
              _tmpPreviousTokenValidUntilEpochMs = _cursor.getLong(_cursorIndexOfPreviousTokenValidUntilEpochMs);
            }
            final byte[] _tmpRetiredTokenHash;
            if (_cursor.isNull(_cursorIndexOfRetiredTokenHash)) {
              _tmpRetiredTokenHash = null;
            } else {
              _tmpRetiredTokenHash = _cursor.getBlob(_cursorIndexOfRetiredTokenHash);
            }
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            final long _tmpLastUsedAtEpochMs;
            _tmpLastUsedAtEpochMs = _cursor.getLong(_cursorIndexOfLastUsedAtEpochMs);
            final Long _tmpExpiresAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExpiresAtEpochMs)) {
              _tmpExpiresAtEpochMs = null;
            } else {
              _tmpExpiresAtEpochMs = _cursor.getLong(_cursorIndexOfExpiresAtEpochMs);
            }
            final Long _tmpRevokedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfRevokedAtEpochMs)) {
              _tmpRevokedAtEpochMs = null;
            } else {
              _tmpRevokedAtEpochMs = _cursor.getLong(_cursorIndexOfRevokedAtEpochMs);
            }
            final String _tmpBrowserSummary;
            if (_cursor.isNull(_cursorIndexOfBrowserSummary)) {
              _tmpBrowserSummary = null;
            } else {
              _tmpBrowserSummary = _cursor.getString(_cursorIndexOfBrowserSummary);
            }
            final String _tmpOsSummary;
            if (_cursor.isNull(_cursorIndexOfOsSummary)) {
              _tmpOsSummary = null;
            } else {
              _tmpOsSummary = _cursor.getString(_cursorIndexOfOsSummary);
            }
            final long _tmpLastTrustedWallClockEpochMs;
            _tmpLastTrustedWallClockEpochMs = _cursor.getLong(_cursorIndexOfLastTrustedWallClockEpochMs);
            _result = new RememberedBrowserEntity(_tmpId,_tmpCurrentTokenHash,_tmpPreviousTokenHash,_tmpPreviousTokenValidUntilEpochMs,_tmpRetiredTokenHash,_tmpLabel,_tmpCreatedAtEpochMs,_tmpLastUsedAtEpochMs,_tmpExpiresAtEpochMs,_tmpRevokedAtEpochMs,_tmpBrowserSummary,_tmpOsSummary,_tmpLastTrustedWallClockEpochMs);
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
  public Object all(final Continuation<? super List<RememberedBrowserEntity>> $completion) {
    final String _sql = "SELECT * FROM remembered_browsers ORDER BY createdAtEpochMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RememberedBrowserEntity>>() {
      @Override
      @NonNull
      public List<RememberedBrowserEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfCurrentTokenHash = CursorUtil.getColumnIndexOrThrow(_cursor, "currentTokenHash");
          final int _cursorIndexOfPreviousTokenHash = CursorUtil.getColumnIndexOrThrow(_cursor, "previousTokenHash");
          final int _cursorIndexOfPreviousTokenValidUntilEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "previousTokenValidUntilEpochMs");
          final int _cursorIndexOfRetiredTokenHash = CursorUtil.getColumnIndexOrThrow(_cursor, "retiredTokenHash");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfLastUsedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAtEpochMs");
          final int _cursorIndexOfExpiresAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "expiresAtEpochMs");
          final int _cursorIndexOfRevokedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "revokedAtEpochMs");
          final int _cursorIndexOfBrowserSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "browserSummary");
          final int _cursorIndexOfOsSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "osSummary");
          final int _cursorIndexOfLastTrustedWallClockEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastTrustedWallClockEpochMs");
          final List<RememberedBrowserEntity> _result = new ArrayList<RememberedBrowserEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RememberedBrowserEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final byte[] _tmpCurrentTokenHash;
            _tmpCurrentTokenHash = _cursor.getBlob(_cursorIndexOfCurrentTokenHash);
            final byte[] _tmpPreviousTokenHash;
            if (_cursor.isNull(_cursorIndexOfPreviousTokenHash)) {
              _tmpPreviousTokenHash = null;
            } else {
              _tmpPreviousTokenHash = _cursor.getBlob(_cursorIndexOfPreviousTokenHash);
            }
            final Long _tmpPreviousTokenValidUntilEpochMs;
            if (_cursor.isNull(_cursorIndexOfPreviousTokenValidUntilEpochMs)) {
              _tmpPreviousTokenValidUntilEpochMs = null;
            } else {
              _tmpPreviousTokenValidUntilEpochMs = _cursor.getLong(_cursorIndexOfPreviousTokenValidUntilEpochMs);
            }
            final byte[] _tmpRetiredTokenHash;
            if (_cursor.isNull(_cursorIndexOfRetiredTokenHash)) {
              _tmpRetiredTokenHash = null;
            } else {
              _tmpRetiredTokenHash = _cursor.getBlob(_cursorIndexOfRetiredTokenHash);
            }
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            final long _tmpLastUsedAtEpochMs;
            _tmpLastUsedAtEpochMs = _cursor.getLong(_cursorIndexOfLastUsedAtEpochMs);
            final Long _tmpExpiresAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfExpiresAtEpochMs)) {
              _tmpExpiresAtEpochMs = null;
            } else {
              _tmpExpiresAtEpochMs = _cursor.getLong(_cursorIndexOfExpiresAtEpochMs);
            }
            final Long _tmpRevokedAtEpochMs;
            if (_cursor.isNull(_cursorIndexOfRevokedAtEpochMs)) {
              _tmpRevokedAtEpochMs = null;
            } else {
              _tmpRevokedAtEpochMs = _cursor.getLong(_cursorIndexOfRevokedAtEpochMs);
            }
            final String _tmpBrowserSummary;
            if (_cursor.isNull(_cursorIndexOfBrowserSummary)) {
              _tmpBrowserSummary = null;
            } else {
              _tmpBrowserSummary = _cursor.getString(_cursorIndexOfBrowserSummary);
            }
            final String _tmpOsSummary;
            if (_cursor.isNull(_cursorIndexOfOsSummary)) {
              _tmpOsSummary = null;
            } else {
              _tmpOsSummary = _cursor.getString(_cursorIndexOfOsSummary);
            }
            final long _tmpLastTrustedWallClockEpochMs;
            _tmpLastTrustedWallClockEpochMs = _cursor.getLong(_cursorIndexOfLastTrustedWallClockEpochMs);
            _item = new RememberedBrowserEntity(_tmpId,_tmpCurrentTokenHash,_tmpPreviousTokenHash,_tmpPreviousTokenValidUntilEpochMs,_tmpRetiredTokenHash,_tmpLabel,_tmpCreatedAtEpochMs,_tmpLastUsedAtEpochMs,_tmpExpiresAtEpochMs,_tmpRevokedAtEpochMs,_tmpBrowserSummary,_tmpOsSummary,_tmpLastTrustedWallClockEpochMs);
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
  public Object activeCount(final long now, final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM remembered_browsers WHERE revokedAtEpochMs IS NULL AND (expiresAtEpochMs IS NULL OR expiresAtEpochMs > ?)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, now);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
