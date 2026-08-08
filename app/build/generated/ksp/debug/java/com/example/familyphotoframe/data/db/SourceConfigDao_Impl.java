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
public final class SourceConfigDao_Impl implements SourceConfigDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SourceConfigEntity> __insertionAdapterOfSourceConfigEntity;

  private final EntityInsertionAdapter<SmbSourceConfigEntity> __insertionAdapterOfSmbSourceConfigEntity;

  private final EntityInsertionAdapter<LocalSafSourceConfigEntity> __insertionAdapterOfLocalSafSourceConfigEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateSafPermissionState;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSource;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSmb;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSaf;

  public SourceConfigDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSourceConfigEntity = new EntityInsertionAdapter<SourceConfigEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `source_config` (`id`,`type`,`displayName`,`enabled`,`role`,`credentialRef`,`includeSubfolders`,`includeGlobsCsv`,`excludeGlobsCsv`,`priority`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SourceConfigEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getType());
        statement.bindString(3, entity.getDisplayName());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindString(5, entity.getRole());
        if (entity.getCredentialRef() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getCredentialRef());
        }
        final int _tmp_1 = entity.getIncludeSubfolders() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindString(8, entity.getIncludeGlobsCsv());
        statement.bindString(9, entity.getExcludeGlobsCsv());
        statement.bindLong(10, entity.getPriority());
      }
    };
    this.__insertionAdapterOfSmbSourceConfigEntity = new EntityInsertionAdapter<SmbSourceConfigEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `smb_source_config` (`sourceId`,`host`,`share`,`path`,`user`,`domain`,`connectionTimeoutMs`,`readTimeoutMs`,`listTimeoutMs`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SmbSourceConfigEntity entity) {
        statement.bindString(1, entity.getSourceId());
        statement.bindString(2, entity.getHost());
        statement.bindString(3, entity.getShare());
        statement.bindString(4, entity.getPath());
        statement.bindString(5, entity.getUser());
        statement.bindString(6, entity.getDomain());
        statement.bindLong(7, entity.getConnectionTimeoutMs());
        statement.bindLong(8, entity.getReadTimeoutMs());
        statement.bindLong(9, entity.getListTimeoutMs());
      }
    };
    this.__insertionAdapterOfLocalSafSourceConfigEntity = new EntityInsertionAdapter<LocalSafSourceConfigEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `local_saf_source_config` (`sourceId`,`treeUri`,`permissionState`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalSafSourceConfigEntity entity) {
        statement.bindString(1, entity.getSourceId());
        statement.bindString(2, entity.getTreeUri());
        statement.bindString(3, entity.getPermissionState());
      }
    };
    this.__preparedStmtOfUpdateSafPermissionState = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE local_saf_source_config SET permissionState = ? WHERE sourceId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSource = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM source_config WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSmb = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM smb_source_config WHERE sourceId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSaf = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM local_saf_source_config WHERE sourceId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final SourceConfigEntity config,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSourceConfigEntity.insert(config);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertSmb(final SmbSourceConfigEntity config,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfSmbSourceConfigEntity.insert(config);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertSaf(final LocalSafSourceConfigEntity config,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalSafSourceConfigEntity.insert(config);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSafPermissionState(final String sourceId, final String state,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSafPermissionState.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, state);
        _argIndex = 2;
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
          __preparedStmtOfUpdateSafPermissionState.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSource(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSource.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteSource.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSmb(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSmb.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteSmb.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSaf(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSaf.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteSaf.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object enabledSources(final Continuation<? super List<SourceConfigEntity>> $completion) {
    final String _sql = "SELECT * FROM source_config WHERE enabled = 1 ORDER BY priority DESC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SourceConfigEntity>>() {
      @Override
      @NonNull
      public List<SourceConfigEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfCredentialRef = CursorUtil.getColumnIndexOrThrow(_cursor, "credentialRef");
          final int _cursorIndexOfIncludeSubfolders = CursorUtil.getColumnIndexOrThrow(_cursor, "includeSubfolders");
          final int _cursorIndexOfIncludeGlobsCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "includeGlobsCsv");
          final int _cursorIndexOfExcludeGlobsCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "excludeGlobsCsv");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final List<SourceConfigEntity> _result = new ArrayList<SourceConfigEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SourceConfigEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final String _tmpRole;
            _tmpRole = _cursor.getString(_cursorIndexOfRole);
            final String _tmpCredentialRef;
            if (_cursor.isNull(_cursorIndexOfCredentialRef)) {
              _tmpCredentialRef = null;
            } else {
              _tmpCredentialRef = _cursor.getString(_cursorIndexOfCredentialRef);
            }
            final boolean _tmpIncludeSubfolders;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIncludeSubfolders);
            _tmpIncludeSubfolders = _tmp_1 != 0;
            final String _tmpIncludeGlobsCsv;
            _tmpIncludeGlobsCsv = _cursor.getString(_cursorIndexOfIncludeGlobsCsv);
            final String _tmpExcludeGlobsCsv;
            _tmpExcludeGlobsCsv = _cursor.getString(_cursorIndexOfExcludeGlobsCsv);
            final int _tmpPriority;
            _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
            _item = new SourceConfigEntity(_tmpId,_tmpType,_tmpDisplayName,_tmpEnabled,_tmpRole,_tmpCredentialRef,_tmpIncludeSubfolders,_tmpIncludeGlobsCsv,_tmpExcludeGlobsCsv,_tmpPriority);
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
  public Flow<List<SourceConfigEntity>> allSourcesFlow() {
    final String _sql = "SELECT * FROM source_config";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"source_config"}, new Callable<List<SourceConfigEntity>>() {
      @Override
      @NonNull
      public List<SourceConfigEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfCredentialRef = CursorUtil.getColumnIndexOrThrow(_cursor, "credentialRef");
          final int _cursorIndexOfIncludeSubfolders = CursorUtil.getColumnIndexOrThrow(_cursor, "includeSubfolders");
          final int _cursorIndexOfIncludeGlobsCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "includeGlobsCsv");
          final int _cursorIndexOfExcludeGlobsCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "excludeGlobsCsv");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final List<SourceConfigEntity> _result = new ArrayList<SourceConfigEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SourceConfigEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final String _tmpRole;
            _tmpRole = _cursor.getString(_cursorIndexOfRole);
            final String _tmpCredentialRef;
            if (_cursor.isNull(_cursorIndexOfCredentialRef)) {
              _tmpCredentialRef = null;
            } else {
              _tmpCredentialRef = _cursor.getString(_cursorIndexOfCredentialRef);
            }
            final boolean _tmpIncludeSubfolders;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIncludeSubfolders);
            _tmpIncludeSubfolders = _tmp_1 != 0;
            final String _tmpIncludeGlobsCsv;
            _tmpIncludeGlobsCsv = _cursor.getString(_cursorIndexOfIncludeGlobsCsv);
            final String _tmpExcludeGlobsCsv;
            _tmpExcludeGlobsCsv = _cursor.getString(_cursorIndexOfExcludeGlobsCsv);
            final int _tmpPriority;
            _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
            _item = new SourceConfigEntity(_tmpId,_tmpType,_tmpDisplayName,_tmpEnabled,_tmpRole,_tmpCredentialRef,_tmpIncludeSubfolders,_tmpIncludeGlobsCsv,_tmpExcludeGlobsCsv,_tmpPriority);
            _result.add(_item);
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
  public Object sourceById(final String id,
      final Continuation<? super SourceConfigEntity> $completion) {
    final String _sql = "SELECT * FROM source_config WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SourceConfigEntity>() {
      @Override
      @Nullable
      public SourceConfigEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfCredentialRef = CursorUtil.getColumnIndexOrThrow(_cursor, "credentialRef");
          final int _cursorIndexOfIncludeSubfolders = CursorUtil.getColumnIndexOrThrow(_cursor, "includeSubfolders");
          final int _cursorIndexOfIncludeGlobsCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "includeGlobsCsv");
          final int _cursorIndexOfExcludeGlobsCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "excludeGlobsCsv");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final SourceConfigEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final String _tmpRole;
            _tmpRole = _cursor.getString(_cursorIndexOfRole);
            final String _tmpCredentialRef;
            if (_cursor.isNull(_cursorIndexOfCredentialRef)) {
              _tmpCredentialRef = null;
            } else {
              _tmpCredentialRef = _cursor.getString(_cursorIndexOfCredentialRef);
            }
            final boolean _tmpIncludeSubfolders;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIncludeSubfolders);
            _tmpIncludeSubfolders = _tmp_1 != 0;
            final String _tmpIncludeGlobsCsv;
            _tmpIncludeGlobsCsv = _cursor.getString(_cursorIndexOfIncludeGlobsCsv);
            final String _tmpExcludeGlobsCsv;
            _tmpExcludeGlobsCsv = _cursor.getString(_cursorIndexOfExcludeGlobsCsv);
            final int _tmpPriority;
            _tmpPriority = _cursor.getInt(_cursorIndexOfPriority);
            _result = new SourceConfigEntity(_tmpId,_tmpType,_tmpDisplayName,_tmpEnabled,_tmpRole,_tmpCredentialRef,_tmpIncludeSubfolders,_tmpIncludeGlobsCsv,_tmpExcludeGlobsCsv,_tmpPriority);
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
  public Object smbConfig(final String sourceId,
      final Continuation<? super SmbSourceConfigEntity> $completion) {
    final String _sql = "SELECT * FROM smb_source_config WHERE sourceId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SmbSourceConfigEntity>() {
      @Override
      @Nullable
      public SmbSourceConfigEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfShare = CursorUtil.getColumnIndexOrThrow(_cursor, "share");
          final int _cursorIndexOfPath = CursorUtil.getColumnIndexOrThrow(_cursor, "path");
          final int _cursorIndexOfUser = CursorUtil.getColumnIndexOrThrow(_cursor, "user");
          final int _cursorIndexOfDomain = CursorUtil.getColumnIndexOrThrow(_cursor, "domain");
          final int _cursorIndexOfConnectionTimeoutMs = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionTimeoutMs");
          final int _cursorIndexOfReadTimeoutMs = CursorUtil.getColumnIndexOrThrow(_cursor, "readTimeoutMs");
          final int _cursorIndexOfListTimeoutMs = CursorUtil.getColumnIndexOrThrow(_cursor, "listTimeoutMs");
          final SmbSourceConfigEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final String _tmpShare;
            _tmpShare = _cursor.getString(_cursorIndexOfShare);
            final String _tmpPath;
            _tmpPath = _cursor.getString(_cursorIndexOfPath);
            final String _tmpUser;
            _tmpUser = _cursor.getString(_cursorIndexOfUser);
            final String _tmpDomain;
            _tmpDomain = _cursor.getString(_cursorIndexOfDomain);
            final long _tmpConnectionTimeoutMs;
            _tmpConnectionTimeoutMs = _cursor.getLong(_cursorIndexOfConnectionTimeoutMs);
            final long _tmpReadTimeoutMs;
            _tmpReadTimeoutMs = _cursor.getLong(_cursorIndexOfReadTimeoutMs);
            final long _tmpListTimeoutMs;
            _tmpListTimeoutMs = _cursor.getLong(_cursorIndexOfListTimeoutMs);
            _result = new SmbSourceConfigEntity(_tmpSourceId,_tmpHost,_tmpShare,_tmpPath,_tmpUser,_tmpDomain,_tmpConnectionTimeoutMs,_tmpReadTimeoutMs,_tmpListTimeoutMs);
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
  public Object safConfig(final String sourceId,
      final Continuation<? super LocalSafSourceConfigEntity> $completion) {
    final String _sql = "SELECT * FROM local_saf_source_config WHERE sourceId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sourceId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalSafSourceConfigEntity>() {
      @Override
      @Nullable
      public LocalSafSourceConfigEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSourceId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceId");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfPermissionState = CursorUtil.getColumnIndexOrThrow(_cursor, "permissionState");
          final LocalSafSourceConfigEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSourceId;
            _tmpSourceId = _cursor.getString(_cursorIndexOfSourceId);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpPermissionState;
            _tmpPermissionState = _cursor.getString(_cursorIndexOfPermissionState);
            _result = new LocalSafSourceConfigEntity(_tmpSourceId,_tmpTreeUri,_tmpPermissionState);
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
