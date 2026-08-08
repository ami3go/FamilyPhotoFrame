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
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SecretDao_Impl implements SecretDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SecretEntity> __insertionAdapterOfSecretEntity;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public SecretDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSecretEntity = new EntityInsertionAdapter<SecretEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `secrets` (`credentialRef`,`type`,`encryptedSecretBlob`,`iv`,`wrappedKey`,`createdAtEpochMs`,`updatedAtEpochMs`,`securityLevel`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SecretEntity entity) {
        statement.bindString(1, entity.getCredentialRef());
        statement.bindString(2, entity.getType());
        statement.bindBlob(3, entity.getEncryptedSecretBlob());
        statement.bindBlob(4, entity.getIv());
        if (entity.getWrappedKey() == null) {
          statement.bindNull(5);
        } else {
          statement.bindBlob(5, entity.getWrappedKey());
        }
        statement.bindLong(6, entity.getCreatedAtEpochMs());
        statement.bindLong(7, entity.getUpdatedAtEpochMs());
        statement.bindString(8, entity.getSecurityLevel());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM secrets WHERE credentialRef = ?";
        return _query;
      }
    };
  }

  @Override
  public Object put(final SecretEntity secret, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSecretEntity.insert(secret);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String ref, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, ref);
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
  public Object get(final String ref, final Continuation<? super SecretEntity> $completion) {
    final String _sql = "SELECT * FROM secrets WHERE credentialRef = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, ref);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SecretEntity>() {
      @Override
      @Nullable
      public SecretEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCredentialRef = CursorUtil.getColumnIndexOrThrow(_cursor, "credentialRef");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfEncryptedSecretBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "encryptedSecretBlob");
          final int _cursorIndexOfIv = CursorUtil.getColumnIndexOrThrow(_cursor, "iv");
          final int _cursorIndexOfWrappedKey = CursorUtil.getColumnIndexOrThrow(_cursor, "wrappedKey");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final int _cursorIndexOfUpdatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtEpochMs");
          final int _cursorIndexOfSecurityLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "securityLevel");
          final SecretEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpCredentialRef;
            _tmpCredentialRef = _cursor.getString(_cursorIndexOfCredentialRef);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final byte[] _tmpEncryptedSecretBlob;
            _tmpEncryptedSecretBlob = _cursor.getBlob(_cursorIndexOfEncryptedSecretBlob);
            final byte[] _tmpIv;
            _tmpIv = _cursor.getBlob(_cursorIndexOfIv);
            final byte[] _tmpWrappedKey;
            if (_cursor.isNull(_cursorIndexOfWrappedKey)) {
              _tmpWrappedKey = null;
            } else {
              _tmpWrappedKey = _cursor.getBlob(_cursorIndexOfWrappedKey);
            }
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            final long _tmpUpdatedAtEpochMs;
            _tmpUpdatedAtEpochMs = _cursor.getLong(_cursorIndexOfUpdatedAtEpochMs);
            final String _tmpSecurityLevel;
            _tmpSecurityLevel = _cursor.getString(_cursorIndexOfSecurityLevel);
            _result = new SecretEntity(_tmpCredentialRef,_tmpType,_tmpEncryptedSecretBlob,_tmpIv,_tmpWrappedKey,_tmpCreatedAtEpochMs,_tmpUpdatedAtEpochMs,_tmpSecurityLevel);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
