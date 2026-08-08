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
public final class ShuffleDao_Impl implements ShuffleDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ShuffleScopeEntity> __insertionAdapterOfShuffleScopeEntity;

  private final EntityInsertionAdapter<FolderShuffleEntryEntity> __insertionAdapterOfFolderShuffleEntryEntity;

  private final EntityInsertionAdapter<FolderPhotoCycleEntity> __insertionAdapterOfFolderPhotoCycleEntity;

  private final EntityInsertionAdapter<PhotoShuffleEntryEntity> __insertionAdapterOfPhotoShuffleEntryEntity;

  private final EntityInsertionAdapter<ShuffleReservationEntity> __insertionAdapterOfShuffleReservationEntity;

  private final EntityInsertionAdapter<PresentationHistoryEntity> __insertionAdapterOfPresentationHistoryEntity;

  private final SharedSQLiteStatement __preparedStmtOfTouchScope;

  private final SharedSQLiteStatement __preparedStmtOfDeleteScope;

  private final SharedSQLiteStatement __preparedStmtOfDeleteFolderCycle;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllFolderEntries;

  private final SharedSQLiteStatement __preparedStmtOfUpdateFolderEntry;

  private final SharedSQLiteStatement __preparedStmtOfDeletePhotoCycles;

  private final SharedSQLiteStatement __preparedStmtOfDeletePhotoCycleEntries;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllPhotoEntries;

  private final SharedSQLiteStatement __preparedStmtOfUpdatePhotoEntry;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReservation;

  private final SharedSQLiteStatement __preparedStmtOfTrimHistory;

  private final SharedSQLiteStatement __preparedStmtOfClearHistory;

  public ShuffleDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfShuffleScopeEntity = new EntityInsertionAdapter<ShuffleScopeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `shuffle_scopes` (`scopeKey`,`playlistId`,`poolRole`,`activeFolderCycle`,`lastPresentedFolderKey`,`lastUsedAtEpochMs`,`eligibilityRevision`,`reconciliationRevision`,`historyCursorSequence`,`latestHistorySequence`,`lastCommitEpochMs`,`lastReconciliationEpochMs`,`lastRecoveryEpochMs`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ShuffleScopeEntity entity) {
        statement.bindString(1, entity.getScopeKey());
        statement.bindString(2, entity.getPlaylistId());
        statement.bindString(3, entity.getPoolRole());
        statement.bindLong(4, entity.getActiveFolderCycle());
        if (entity.getLastPresentedFolderKey() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLastPresentedFolderKey());
        }
        statement.bindLong(6, entity.getLastUsedAtEpochMs());
        statement.bindLong(7, entity.getEligibilityRevision());
        statement.bindLong(8, entity.getReconciliationRevision());
        statement.bindLong(9, entity.getHistoryCursorSequence());
        statement.bindLong(10, entity.getLatestHistorySequence());
        if (entity.getLastCommitEpochMs() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getLastCommitEpochMs());
        }
        if (entity.getLastReconciliationEpochMs() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getLastReconciliationEpochMs());
        }
        if (entity.getLastRecoveryEpochMs() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getLastRecoveryEpochMs());
        }
      }
    };
    this.__insertionAdapterOfFolderShuffleEntryEntity = new EntityInsertionAdapter<FolderShuffleEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `folder_shuffle_entries` (`scopeKey`,`folderCycle`,`position`,`folderKey`,`state`,`retryCount`,`skipReason`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FolderShuffleEntryEntity entity) {
        statement.bindString(1, entity.getScopeKey());
        statement.bindLong(2, entity.getFolderCycle());
        statement.bindLong(3, entity.getPosition());
        statement.bindString(4, entity.getFolderKey());
        statement.bindString(5, entity.getState());
        statement.bindLong(6, entity.getRetryCount());
        if (entity.getSkipReason() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSkipReason());
        }
      }
    };
    this.__insertionAdapterOfFolderPhotoCycleEntity = new EntityInsertionAdapter<FolderPhotoCycleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `folder_photo_cycles` (`scopeKey`,`folderKey`,`activePhotoCycle`,`lastConsumedPhotoKey`,`reconciliationRevision`,`lastUsedAtEpochMs`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FolderPhotoCycleEntity entity) {
        statement.bindString(1, entity.getScopeKey());
        statement.bindString(2, entity.getFolderKey());
        statement.bindLong(3, entity.getActivePhotoCycle());
        if (entity.getLastConsumedPhotoKey() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getLastConsumedPhotoKey());
        }
        statement.bindLong(5, entity.getReconciliationRevision());
        statement.bindLong(6, entity.getLastUsedAtEpochMs());
      }
    };
    this.__insertionAdapterOfPhotoShuffleEntryEntity = new EntityInsertionAdapter<PhotoShuffleEntryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `photo_shuffle_entries` (`scopeKey`,`folderKey`,`photoCycle`,`position`,`folderPhotoKey`,`photoId`,`state`,`failureCount`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoShuffleEntryEntity entity) {
        statement.bindString(1, entity.getScopeKey());
        statement.bindString(2, entity.getFolderKey());
        statement.bindLong(3, entity.getPhotoCycle());
        statement.bindLong(4, entity.getPosition());
        statement.bindString(5, entity.getFolderPhotoKey());
        statement.bindLong(6, entity.getPhotoId());
        statement.bindString(7, entity.getState());
        statement.bindLong(8, entity.getFailureCount());
      }
    };
    this.__insertionAdapterOfShuffleReservationEntity = new EntityInsertionAdapter<ShuffleReservationEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `shuffle_reservations` (`scopeKey`,`reservationId`,`folderCycle`,`folderPosition`,`folderKey`,`photoCycle`,`photoPositionsJson`,`photoIdsJson`,`createdAtEpochMs`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ShuffleReservationEntity entity) {
        statement.bindString(1, entity.getScopeKey());
        statement.bindString(2, entity.getReservationId());
        statement.bindLong(3, entity.getFolderCycle());
        statement.bindLong(4, entity.getFolderPosition());
        statement.bindString(5, entity.getFolderKey());
        statement.bindLong(6, entity.getPhotoCycle());
        statement.bindString(7, entity.getPhotoPositionsJson());
        statement.bindString(8, entity.getPhotoIdsJson());
        statement.bindLong(9, entity.getCreatedAtEpochMs());
      }
    };
    this.__insertionAdapterOfPresentationHistoryEntity = new EntityInsertionAdapter<PresentationHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `presentation_history` (`presentationId`,`scopeKey`,`sequence`,`folderKey`,`presentationType`,`photoIdsJson`,`committedAtEpochMs`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PresentationHistoryEntity entity) {
        statement.bindString(1, entity.getPresentationId());
        statement.bindString(2, entity.getScopeKey());
        statement.bindLong(3, entity.getSequence());
        statement.bindString(4, entity.getFolderKey());
        statement.bindString(5, entity.getPresentationType());
        statement.bindString(6, entity.getPhotoIdsJson());
        statement.bindLong(7, entity.getCommittedAtEpochMs());
      }
    };
    this.__preparedStmtOfTouchScope = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE shuffle_scopes SET lastUsedAtEpochMs = ? WHERE scopeKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteScope = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shuffle_scopes WHERE scopeKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteFolderCycle = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM folder_shuffle_entries WHERE scopeKey = ? AND folderCycle = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllFolderEntries = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM folder_shuffle_entries WHERE scopeKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateFolderEntry = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE folder_shuffle_entries SET state = ?, retryCount = ?, skipReason = ? WHERE scopeKey = ? AND folderCycle = ? AND position = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeletePhotoCycles = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM folder_photo_cycles WHERE scopeKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeletePhotoCycleEntries = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photo_shuffle_entries WHERE scopeKey = ? AND folderKey = ? AND photoCycle = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllPhotoEntries = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photo_shuffle_entries WHERE scopeKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdatePhotoEntry = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photo_shuffle_entries SET state = ?, failureCount = ? WHERE scopeKey = ? AND folderKey = ? AND photoCycle = ? AND position = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReservation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shuffle_reservations WHERE scopeKey = ?";
        return _query;
      }
    };
    this.__preparedStmtOfTrimHistory = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM presentation_history WHERE scopeKey = ? AND sequence NOT IN (SELECT sequence FROM presentation_history WHERE scopeKey = ? ORDER BY sequence DESC LIMIT ?)";
        return _query;
      }
    };
    this.__preparedStmtOfClearHistory = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM presentation_history WHERE scopeKey = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsertScope(final ShuffleScopeEntity scope,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfShuffleScopeEntity.insert(scope);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertFolderEntries(final List<FolderShuffleEntryEntity> entries,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFolderShuffleEntryEntity.insert(entries);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertPhotoCycle(final FolderPhotoCycleEntity cycle,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFolderPhotoCycleEntity.insert(cycle);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertPhotoEntries(final List<PhotoShuffleEntryEntity> entries,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPhotoShuffleEntryEntity.insert(entries);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertReservation(final ShuffleReservationEntity reservation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfShuffleReservationEntity.insert(reservation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertHistory(final PresentationHistoryEntity history,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPresentationHistoryEntity.insert(history);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object touchScope(final String scopeKey, final long now,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfTouchScope.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, now);
        _argIndex = 2;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfTouchScope.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteScope(final String scopeKey, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteScope.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteScope.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteFolderCycle(final String scopeKey, final long cycle,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteFolderCycle.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, cycle);
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
          __preparedStmtOfDeleteFolderCycle.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllFolderEntries(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllFolderEntries.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteAllFolderEntries.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateFolderEntry(final String scopeKey, final long cycle, final int position,
      final String state, final int retryCount, final String skipReason,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateFolderEntry.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, state);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, retryCount);
        _argIndex = 3;
        if (skipReason == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, skipReason);
        }
        _argIndex = 4;
        _stmt.bindString(_argIndex, scopeKey);
        _argIndex = 5;
        _stmt.bindLong(_argIndex, cycle);
        _argIndex = 6;
        _stmt.bindLong(_argIndex, position);
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
          __preparedStmtOfUpdateFolderEntry.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePhotoCycles(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePhotoCycles.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeletePhotoCycles.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePhotoCycleEntries(final String scopeKey, final String folderKey,
      final long cycle, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePhotoCycleEntries.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
        _argIndex = 2;
        _stmt.bindString(_argIndex, folderKey);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, cycle);
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
          __preparedStmtOfDeletePhotoCycleEntries.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllPhotoEntries(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllPhotoEntries.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteAllPhotoEntries.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updatePhotoEntry(final String scopeKey, final String folderKey, final long cycle,
      final int position, final String state, final int failureCount,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdatePhotoEntry.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, state);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, failureCount);
        _argIndex = 3;
        _stmt.bindString(_argIndex, scopeKey);
        _argIndex = 4;
        _stmt.bindString(_argIndex, folderKey);
        _argIndex = 5;
        _stmt.bindLong(_argIndex, cycle);
        _argIndex = 6;
        _stmt.bindLong(_argIndex, position);
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
          __preparedStmtOfUpdatePhotoEntry.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteReservation(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteReservation.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteReservation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object trimHistory(final String scopeKey, final int keep,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfTrimHistory.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
        _argIndex = 2;
        _stmt.bindString(_argIndex, scopeKey);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, keep);
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
          __preparedStmtOfTrimHistory.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearHistory(final String scopeKey, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearHistory.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfClearHistory.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object cleanupFolderEntries(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllFolderEntries.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteAllFolderEntries.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object cleanupPhotoEntries(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllPhotoEntries.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteAllPhotoEntries.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object cleanupPhotoCycles(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePhotoCycles.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeletePhotoCycles.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object cleanupReservations(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteReservation.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfDeleteReservation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object cleanupHistory(final String scopeKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearHistory.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scopeKey);
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
          __preparedStmtOfClearHistory.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object scope(final String scopeKey,
      final Continuation<? super ShuffleScopeEntity> $completion) {
    final String _sql = "SELECT * FROM shuffle_scopes WHERE scopeKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ShuffleScopeEntity>() {
      @Override
      @Nullable
      public ShuffleScopeEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
          final int _cursorIndexOfPoolRole = CursorUtil.getColumnIndexOrThrow(_cursor, "poolRole");
          final int _cursorIndexOfActiveFolderCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "activeFolderCycle");
          final int _cursorIndexOfLastPresentedFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPresentedFolderKey");
          final int _cursorIndexOfLastUsedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAtEpochMs");
          final int _cursorIndexOfEligibilityRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "eligibilityRevision");
          final int _cursorIndexOfReconciliationRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "reconciliationRevision");
          final int _cursorIndexOfHistoryCursorSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "historyCursorSequence");
          final int _cursorIndexOfLatestHistorySequence = CursorUtil.getColumnIndexOrThrow(_cursor, "latestHistorySequence");
          final int _cursorIndexOfLastCommitEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastCommitEpochMs");
          final int _cursorIndexOfLastReconciliationEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReconciliationEpochMs");
          final int _cursorIndexOfLastRecoveryEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRecoveryEpochMs");
          final ShuffleScopeEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpPlaylistId;
            _tmpPlaylistId = _cursor.getString(_cursorIndexOfPlaylistId);
            final String _tmpPoolRole;
            _tmpPoolRole = _cursor.getString(_cursorIndexOfPoolRole);
            final long _tmpActiveFolderCycle;
            _tmpActiveFolderCycle = _cursor.getLong(_cursorIndexOfActiveFolderCycle);
            final String _tmpLastPresentedFolderKey;
            if (_cursor.isNull(_cursorIndexOfLastPresentedFolderKey)) {
              _tmpLastPresentedFolderKey = null;
            } else {
              _tmpLastPresentedFolderKey = _cursor.getString(_cursorIndexOfLastPresentedFolderKey);
            }
            final long _tmpLastUsedAtEpochMs;
            _tmpLastUsedAtEpochMs = _cursor.getLong(_cursorIndexOfLastUsedAtEpochMs);
            final long _tmpEligibilityRevision;
            _tmpEligibilityRevision = _cursor.getLong(_cursorIndexOfEligibilityRevision);
            final long _tmpReconciliationRevision;
            _tmpReconciliationRevision = _cursor.getLong(_cursorIndexOfReconciliationRevision);
            final long _tmpHistoryCursorSequence;
            _tmpHistoryCursorSequence = _cursor.getLong(_cursorIndexOfHistoryCursorSequence);
            final long _tmpLatestHistorySequence;
            _tmpLatestHistorySequence = _cursor.getLong(_cursorIndexOfLatestHistorySequence);
            final Long _tmpLastCommitEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastCommitEpochMs)) {
              _tmpLastCommitEpochMs = null;
            } else {
              _tmpLastCommitEpochMs = _cursor.getLong(_cursorIndexOfLastCommitEpochMs);
            }
            final Long _tmpLastReconciliationEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastReconciliationEpochMs)) {
              _tmpLastReconciliationEpochMs = null;
            } else {
              _tmpLastReconciliationEpochMs = _cursor.getLong(_cursorIndexOfLastReconciliationEpochMs);
            }
            final Long _tmpLastRecoveryEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastRecoveryEpochMs)) {
              _tmpLastRecoveryEpochMs = null;
            } else {
              _tmpLastRecoveryEpochMs = _cursor.getLong(_cursorIndexOfLastRecoveryEpochMs);
            }
            _result = new ShuffleScopeEntity(_tmpScopeKey,_tmpPlaylistId,_tmpPoolRole,_tmpActiveFolderCycle,_tmpLastPresentedFolderKey,_tmpLastUsedAtEpochMs,_tmpEligibilityRevision,_tmpReconciliationRevision,_tmpHistoryCursorSequence,_tmpLatestHistorySequence,_tmpLastCommitEpochMs,_tmpLastReconciliationEpochMs,_tmpLastRecoveryEpochMs);
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
  public Object scopesByRecentUse(
      final Continuation<? super List<ShuffleScopeEntity>> $completion) {
    final String _sql = "SELECT * FROM shuffle_scopes ORDER BY lastUsedAtEpochMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ShuffleScopeEntity>>() {
      @Override
      @NonNull
      public List<ShuffleScopeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
          final int _cursorIndexOfPoolRole = CursorUtil.getColumnIndexOrThrow(_cursor, "poolRole");
          final int _cursorIndexOfActiveFolderCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "activeFolderCycle");
          final int _cursorIndexOfLastPresentedFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPresentedFolderKey");
          final int _cursorIndexOfLastUsedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAtEpochMs");
          final int _cursorIndexOfEligibilityRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "eligibilityRevision");
          final int _cursorIndexOfReconciliationRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "reconciliationRevision");
          final int _cursorIndexOfHistoryCursorSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "historyCursorSequence");
          final int _cursorIndexOfLatestHistorySequence = CursorUtil.getColumnIndexOrThrow(_cursor, "latestHistorySequence");
          final int _cursorIndexOfLastCommitEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastCommitEpochMs");
          final int _cursorIndexOfLastReconciliationEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReconciliationEpochMs");
          final int _cursorIndexOfLastRecoveryEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRecoveryEpochMs");
          final List<ShuffleScopeEntity> _result = new ArrayList<ShuffleScopeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShuffleScopeEntity _item;
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpPlaylistId;
            _tmpPlaylistId = _cursor.getString(_cursorIndexOfPlaylistId);
            final String _tmpPoolRole;
            _tmpPoolRole = _cursor.getString(_cursorIndexOfPoolRole);
            final long _tmpActiveFolderCycle;
            _tmpActiveFolderCycle = _cursor.getLong(_cursorIndexOfActiveFolderCycle);
            final String _tmpLastPresentedFolderKey;
            if (_cursor.isNull(_cursorIndexOfLastPresentedFolderKey)) {
              _tmpLastPresentedFolderKey = null;
            } else {
              _tmpLastPresentedFolderKey = _cursor.getString(_cursorIndexOfLastPresentedFolderKey);
            }
            final long _tmpLastUsedAtEpochMs;
            _tmpLastUsedAtEpochMs = _cursor.getLong(_cursorIndexOfLastUsedAtEpochMs);
            final long _tmpEligibilityRevision;
            _tmpEligibilityRevision = _cursor.getLong(_cursorIndexOfEligibilityRevision);
            final long _tmpReconciliationRevision;
            _tmpReconciliationRevision = _cursor.getLong(_cursorIndexOfReconciliationRevision);
            final long _tmpHistoryCursorSequence;
            _tmpHistoryCursorSequence = _cursor.getLong(_cursorIndexOfHistoryCursorSequence);
            final long _tmpLatestHistorySequence;
            _tmpLatestHistorySequence = _cursor.getLong(_cursorIndexOfLatestHistorySequence);
            final Long _tmpLastCommitEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastCommitEpochMs)) {
              _tmpLastCommitEpochMs = null;
            } else {
              _tmpLastCommitEpochMs = _cursor.getLong(_cursorIndexOfLastCommitEpochMs);
            }
            final Long _tmpLastReconciliationEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastReconciliationEpochMs)) {
              _tmpLastReconciliationEpochMs = null;
            } else {
              _tmpLastReconciliationEpochMs = _cursor.getLong(_cursorIndexOfLastReconciliationEpochMs);
            }
            final Long _tmpLastRecoveryEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastRecoveryEpochMs)) {
              _tmpLastRecoveryEpochMs = null;
            } else {
              _tmpLastRecoveryEpochMs = _cursor.getLong(_cursorIndexOfLastRecoveryEpochMs);
            }
            _item = new ShuffleScopeEntity(_tmpScopeKey,_tmpPlaylistId,_tmpPoolRole,_tmpActiveFolderCycle,_tmpLastPresentedFolderKey,_tmpLastUsedAtEpochMs,_tmpEligibilityRevision,_tmpReconciliationRevision,_tmpHistoryCursorSequence,_tmpLatestHistorySequence,_tmpLastCommitEpochMs,_tmpLastReconciliationEpochMs,_tmpLastRecoveryEpochMs);
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
  public Object scopesForPlaylist(final String playlistId,
      final Continuation<? super List<ShuffleScopeEntity>> $completion) {
    final String _sql = "SELECT * FROM shuffle_scopes WHERE playlistId = ? ORDER BY lastUsedAtEpochMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, playlistId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ShuffleScopeEntity>>() {
      @Override
      @NonNull
      public List<ShuffleScopeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfPlaylistId = CursorUtil.getColumnIndexOrThrow(_cursor, "playlistId");
          final int _cursorIndexOfPoolRole = CursorUtil.getColumnIndexOrThrow(_cursor, "poolRole");
          final int _cursorIndexOfActiveFolderCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "activeFolderCycle");
          final int _cursorIndexOfLastPresentedFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "lastPresentedFolderKey");
          final int _cursorIndexOfLastUsedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAtEpochMs");
          final int _cursorIndexOfEligibilityRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "eligibilityRevision");
          final int _cursorIndexOfReconciliationRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "reconciliationRevision");
          final int _cursorIndexOfHistoryCursorSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "historyCursorSequence");
          final int _cursorIndexOfLatestHistorySequence = CursorUtil.getColumnIndexOrThrow(_cursor, "latestHistorySequence");
          final int _cursorIndexOfLastCommitEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastCommitEpochMs");
          final int _cursorIndexOfLastReconciliationEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReconciliationEpochMs");
          final int _cursorIndexOfLastRecoveryEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRecoveryEpochMs");
          final List<ShuffleScopeEntity> _result = new ArrayList<ShuffleScopeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShuffleScopeEntity _item;
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpPlaylistId;
            _tmpPlaylistId = _cursor.getString(_cursorIndexOfPlaylistId);
            final String _tmpPoolRole;
            _tmpPoolRole = _cursor.getString(_cursorIndexOfPoolRole);
            final long _tmpActiveFolderCycle;
            _tmpActiveFolderCycle = _cursor.getLong(_cursorIndexOfActiveFolderCycle);
            final String _tmpLastPresentedFolderKey;
            if (_cursor.isNull(_cursorIndexOfLastPresentedFolderKey)) {
              _tmpLastPresentedFolderKey = null;
            } else {
              _tmpLastPresentedFolderKey = _cursor.getString(_cursorIndexOfLastPresentedFolderKey);
            }
            final long _tmpLastUsedAtEpochMs;
            _tmpLastUsedAtEpochMs = _cursor.getLong(_cursorIndexOfLastUsedAtEpochMs);
            final long _tmpEligibilityRevision;
            _tmpEligibilityRevision = _cursor.getLong(_cursorIndexOfEligibilityRevision);
            final long _tmpReconciliationRevision;
            _tmpReconciliationRevision = _cursor.getLong(_cursorIndexOfReconciliationRevision);
            final long _tmpHistoryCursorSequence;
            _tmpHistoryCursorSequence = _cursor.getLong(_cursorIndexOfHistoryCursorSequence);
            final long _tmpLatestHistorySequence;
            _tmpLatestHistorySequence = _cursor.getLong(_cursorIndexOfLatestHistorySequence);
            final Long _tmpLastCommitEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastCommitEpochMs)) {
              _tmpLastCommitEpochMs = null;
            } else {
              _tmpLastCommitEpochMs = _cursor.getLong(_cursorIndexOfLastCommitEpochMs);
            }
            final Long _tmpLastReconciliationEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastReconciliationEpochMs)) {
              _tmpLastReconciliationEpochMs = null;
            } else {
              _tmpLastReconciliationEpochMs = _cursor.getLong(_cursorIndexOfLastReconciliationEpochMs);
            }
            final Long _tmpLastRecoveryEpochMs;
            if (_cursor.isNull(_cursorIndexOfLastRecoveryEpochMs)) {
              _tmpLastRecoveryEpochMs = null;
            } else {
              _tmpLastRecoveryEpochMs = _cursor.getLong(_cursorIndexOfLastRecoveryEpochMs);
            }
            _item = new ShuffleScopeEntity(_tmpScopeKey,_tmpPlaylistId,_tmpPoolRole,_tmpActiveFolderCycle,_tmpLastPresentedFolderKey,_tmpLastUsedAtEpochMs,_tmpEligibilityRevision,_tmpReconciliationRevision,_tmpHistoryCursorSequence,_tmpLatestHistorySequence,_tmpLastCommitEpochMs,_tmpLastReconciliationEpochMs,_tmpLastRecoveryEpochMs);
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
  public Object folderEntries(final String scopeKey, final long cycle,
      final Continuation<? super List<FolderShuffleEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM folder_shuffle_entries WHERE scopeKey = ? AND folderCycle = ? ORDER BY position ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindLong(_argIndex, cycle);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FolderShuffleEntryEntity>>() {
      @Override
      @NonNull
      public List<FolderShuffleEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfFolderCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "folderCycle");
          final int _cursorIndexOfPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "position");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfRetryCount = CursorUtil.getColumnIndexOrThrow(_cursor, "retryCount");
          final int _cursorIndexOfSkipReason = CursorUtil.getColumnIndexOrThrow(_cursor, "skipReason");
          final List<FolderShuffleEntryEntity> _result = new ArrayList<FolderShuffleEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FolderShuffleEntryEntity _item;
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final long _tmpFolderCycle;
            _tmpFolderCycle = _cursor.getLong(_cursorIndexOfFolderCycle);
            final int _tmpPosition;
            _tmpPosition = _cursor.getInt(_cursorIndexOfPosition);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            final int _tmpRetryCount;
            _tmpRetryCount = _cursor.getInt(_cursorIndexOfRetryCount);
            final String _tmpSkipReason;
            if (_cursor.isNull(_cursorIndexOfSkipReason)) {
              _tmpSkipReason = null;
            } else {
              _tmpSkipReason = _cursor.getString(_cursorIndexOfSkipReason);
            }
            _item = new FolderShuffleEntryEntity(_tmpScopeKey,_tmpFolderCycle,_tmpPosition,_tmpFolderKey,_tmpState,_tmpRetryCount,_tmpSkipReason);
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
  public Object unresolvedFolderCount(final String scopeKey, final long cycle,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM folder_shuffle_entries WHERE scopeKey = ? AND folderCycle = ? AND state IN ('PENDING','RESERVED')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindLong(_argIndex, cycle);
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
  public Object photoCycle(final String scopeKey, final String folderKey,
      final Continuation<? super FolderPhotoCycleEntity> $completion) {
    final String _sql = "SELECT * FROM folder_photo_cycles WHERE scopeKey = ? AND folderKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindString(_argIndex, folderKey);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FolderPhotoCycleEntity>() {
      @Override
      @Nullable
      public FolderPhotoCycleEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfActivePhotoCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "activePhotoCycle");
          final int _cursorIndexOfLastConsumedPhotoKey = CursorUtil.getColumnIndexOrThrow(_cursor, "lastConsumedPhotoKey");
          final int _cursorIndexOfReconciliationRevision = CursorUtil.getColumnIndexOrThrow(_cursor, "reconciliationRevision");
          final int _cursorIndexOfLastUsedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedAtEpochMs");
          final FolderPhotoCycleEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final long _tmpActivePhotoCycle;
            _tmpActivePhotoCycle = _cursor.getLong(_cursorIndexOfActivePhotoCycle);
            final String _tmpLastConsumedPhotoKey;
            if (_cursor.isNull(_cursorIndexOfLastConsumedPhotoKey)) {
              _tmpLastConsumedPhotoKey = null;
            } else {
              _tmpLastConsumedPhotoKey = _cursor.getString(_cursorIndexOfLastConsumedPhotoKey);
            }
            final long _tmpReconciliationRevision;
            _tmpReconciliationRevision = _cursor.getLong(_cursorIndexOfReconciliationRevision);
            final long _tmpLastUsedAtEpochMs;
            _tmpLastUsedAtEpochMs = _cursor.getLong(_cursorIndexOfLastUsedAtEpochMs);
            _result = new FolderPhotoCycleEntity(_tmpScopeKey,_tmpFolderKey,_tmpActivePhotoCycle,_tmpLastConsumedPhotoKey,_tmpReconciliationRevision,_tmpLastUsedAtEpochMs);
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
  public Object photoEntries(final String scopeKey, final String folderKey, final long cycle,
      final Continuation<? super List<PhotoShuffleEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM photo_shuffle_entries WHERE scopeKey = ? AND folderKey = ? AND photoCycle = ? ORDER BY position ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindString(_argIndex, folderKey);
    _argIndex = 3;
    _statement.bindLong(_argIndex, cycle);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoShuffleEntryEntity>>() {
      @Override
      @NonNull
      public List<PhotoShuffleEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPhotoCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "photoCycle");
          final int _cursorIndexOfPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "position");
          final int _cursorIndexOfFolderPhotoKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderPhotoKey");
          final int _cursorIndexOfPhotoId = CursorUtil.getColumnIndexOrThrow(_cursor, "photoId");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "failureCount");
          final List<PhotoShuffleEntryEntity> _result = new ArrayList<PhotoShuffleEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoShuffleEntryEntity _item;
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final long _tmpPhotoCycle;
            _tmpPhotoCycle = _cursor.getLong(_cursorIndexOfPhotoCycle);
            final int _tmpPosition;
            _tmpPosition = _cursor.getInt(_cursorIndexOfPosition);
            final String _tmpFolderPhotoKey;
            _tmpFolderPhotoKey = _cursor.getString(_cursorIndexOfFolderPhotoKey);
            final long _tmpPhotoId;
            _tmpPhotoId = _cursor.getLong(_cursorIndexOfPhotoId);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            final int _tmpFailureCount;
            _tmpFailureCount = _cursor.getInt(_cursorIndexOfFailureCount);
            _item = new PhotoShuffleEntryEntity(_tmpScopeKey,_tmpFolderKey,_tmpPhotoCycle,_tmpPosition,_tmpFolderPhotoKey,_tmpPhotoId,_tmpState,_tmpFailureCount);
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
  public Object pendingPhotos(final String scopeKey, final String folderKey, final long cycle,
      final int limit, final Continuation<? super List<PhotoShuffleEntryEntity>> $completion) {
    final String _sql = "SELECT * FROM photo_shuffle_entries WHERE scopeKey = ? AND folderKey = ? AND photoCycle = ? AND state = 'PENDING' ORDER BY position ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindString(_argIndex, folderKey);
    _argIndex = 3;
    _statement.bindLong(_argIndex, cycle);
    _argIndex = 4;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoShuffleEntryEntity>>() {
      @Override
      @NonNull
      public List<PhotoShuffleEntryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPhotoCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "photoCycle");
          final int _cursorIndexOfPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "position");
          final int _cursorIndexOfFolderPhotoKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderPhotoKey");
          final int _cursorIndexOfPhotoId = CursorUtil.getColumnIndexOrThrow(_cursor, "photoId");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfFailureCount = CursorUtil.getColumnIndexOrThrow(_cursor, "failureCount");
          final List<PhotoShuffleEntryEntity> _result = new ArrayList<PhotoShuffleEntryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoShuffleEntryEntity _item;
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final long _tmpPhotoCycle;
            _tmpPhotoCycle = _cursor.getLong(_cursorIndexOfPhotoCycle);
            final int _tmpPosition;
            _tmpPosition = _cursor.getInt(_cursorIndexOfPosition);
            final String _tmpFolderPhotoKey;
            _tmpFolderPhotoKey = _cursor.getString(_cursorIndexOfFolderPhotoKey);
            final long _tmpPhotoId;
            _tmpPhotoId = _cursor.getLong(_cursorIndexOfPhotoId);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            final int _tmpFailureCount;
            _tmpFailureCount = _cursor.getInt(_cursorIndexOfFailureCount);
            _item = new PhotoShuffleEntryEntity(_tmpScopeKey,_tmpFolderKey,_tmpPhotoCycle,_tmpPosition,_tmpFolderPhotoKey,_tmpPhotoId,_tmpState,_tmpFailureCount);
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
  public Object unresolvedPhotoCount(final String scopeKey, final String folderKey,
      final long cycle, final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photo_shuffle_entries WHERE scopeKey = ? AND folderKey = ? AND photoCycle = ? AND state IN ('PENDING','RESERVED')";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindString(_argIndex, folderKey);
    _argIndex = 3;
    _statement.bindLong(_argIndex, cycle);
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
  public Object quarantinedPhotoCount(final String scopeKey,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photo_shuffle_entries WHERE scopeKey = ? AND state = 'QUARANTINED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
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
  public Object reservation(final String scopeKey,
      final Continuation<? super ShuffleReservationEntity> $completion) {
    final String _sql = "SELECT * FROM shuffle_reservations WHERE scopeKey = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ShuffleReservationEntity>() {
      @Override
      @Nullable
      public ShuffleReservationEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfReservationId = CursorUtil.getColumnIndexOrThrow(_cursor, "reservationId");
          final int _cursorIndexOfFolderCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "folderCycle");
          final int _cursorIndexOfFolderPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "folderPosition");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPhotoCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "photoCycle");
          final int _cursorIndexOfPhotoPositionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoPositionsJson");
          final int _cursorIndexOfPhotoIdsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoIdsJson");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final ShuffleReservationEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpReservationId;
            _tmpReservationId = _cursor.getString(_cursorIndexOfReservationId);
            final long _tmpFolderCycle;
            _tmpFolderCycle = _cursor.getLong(_cursorIndexOfFolderCycle);
            final int _tmpFolderPosition;
            _tmpFolderPosition = _cursor.getInt(_cursorIndexOfFolderPosition);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final long _tmpPhotoCycle;
            _tmpPhotoCycle = _cursor.getLong(_cursorIndexOfPhotoCycle);
            final String _tmpPhotoPositionsJson;
            _tmpPhotoPositionsJson = _cursor.getString(_cursorIndexOfPhotoPositionsJson);
            final String _tmpPhotoIdsJson;
            _tmpPhotoIdsJson = _cursor.getString(_cursorIndexOfPhotoIdsJson);
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            _result = new ShuffleReservationEntity(_tmpScopeKey,_tmpReservationId,_tmpFolderCycle,_tmpFolderPosition,_tmpFolderKey,_tmpPhotoCycle,_tmpPhotoPositionsJson,_tmpPhotoIdsJson,_tmpCreatedAtEpochMs);
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
  public Object allReservations(
      final Continuation<? super List<ShuffleReservationEntity>> $completion) {
    final String _sql = "SELECT * FROM shuffle_reservations";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ShuffleReservationEntity>>() {
      @Override
      @NonNull
      public List<ShuffleReservationEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfReservationId = CursorUtil.getColumnIndexOrThrow(_cursor, "reservationId");
          final int _cursorIndexOfFolderCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "folderCycle");
          final int _cursorIndexOfFolderPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "folderPosition");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPhotoCycle = CursorUtil.getColumnIndexOrThrow(_cursor, "photoCycle");
          final int _cursorIndexOfPhotoPositionsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoPositionsJson");
          final int _cursorIndexOfPhotoIdsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoIdsJson");
          final int _cursorIndexOfCreatedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtEpochMs");
          final List<ShuffleReservationEntity> _result = new ArrayList<ShuffleReservationEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShuffleReservationEntity _item;
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final String _tmpReservationId;
            _tmpReservationId = _cursor.getString(_cursorIndexOfReservationId);
            final long _tmpFolderCycle;
            _tmpFolderCycle = _cursor.getLong(_cursorIndexOfFolderCycle);
            final int _tmpFolderPosition;
            _tmpFolderPosition = _cursor.getInt(_cursorIndexOfFolderPosition);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final long _tmpPhotoCycle;
            _tmpPhotoCycle = _cursor.getLong(_cursorIndexOfPhotoCycle);
            final String _tmpPhotoPositionsJson;
            _tmpPhotoPositionsJson = _cursor.getString(_cursorIndexOfPhotoPositionsJson);
            final String _tmpPhotoIdsJson;
            _tmpPhotoIdsJson = _cursor.getString(_cursorIndexOfPhotoIdsJson);
            final long _tmpCreatedAtEpochMs;
            _tmpCreatedAtEpochMs = _cursor.getLong(_cursorIndexOfCreatedAtEpochMs);
            _item = new ShuffleReservationEntity(_tmpScopeKey,_tmpReservationId,_tmpFolderCycle,_tmpFolderPosition,_tmpFolderKey,_tmpPhotoCycle,_tmpPhotoPositionsJson,_tmpPhotoIdsJson,_tmpCreatedAtEpochMs);
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
  public Object newestHistory(final String scopeKey,
      final Continuation<? super PresentationHistoryEntity> $completion) {
    final String _sql = "SELECT * FROM presentation_history WHERE scopeKey = ? ORDER BY sequence DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PresentationHistoryEntity>() {
      @Override
      @Nullable
      public PresentationHistoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPresentationId = CursorUtil.getColumnIndexOrThrow(_cursor, "presentationId");
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "sequence");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPresentationType = CursorUtil.getColumnIndexOrThrow(_cursor, "presentationType");
          final int _cursorIndexOfPhotoIdsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoIdsJson");
          final int _cursorIndexOfCommittedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "committedAtEpochMs");
          final PresentationHistoryEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPresentationId;
            _tmpPresentationId = _cursor.getString(_cursorIndexOfPresentationId);
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final long _tmpSequence;
            _tmpSequence = _cursor.getLong(_cursorIndexOfSequence);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final String _tmpPresentationType;
            _tmpPresentationType = _cursor.getString(_cursorIndexOfPresentationType);
            final String _tmpPhotoIdsJson;
            _tmpPhotoIdsJson = _cursor.getString(_cursorIndexOfPhotoIdsJson);
            final long _tmpCommittedAtEpochMs;
            _tmpCommittedAtEpochMs = _cursor.getLong(_cursorIndexOfCommittedAtEpochMs);
            _result = new PresentationHistoryEntity(_tmpPresentationId,_tmpScopeKey,_tmpSequence,_tmpFolderKey,_tmpPresentationType,_tmpPhotoIdsJson,_tmpCommittedAtEpochMs);
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
  public Object previousHistory(final String scopeKey, final long sequence,
      final Continuation<? super PresentationHistoryEntity> $completion) {
    final String _sql = "SELECT * FROM presentation_history WHERE scopeKey = ? AND sequence < ? ORDER BY sequence DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindLong(_argIndex, sequence);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PresentationHistoryEntity>() {
      @Override
      @Nullable
      public PresentationHistoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPresentationId = CursorUtil.getColumnIndexOrThrow(_cursor, "presentationId");
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "sequence");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPresentationType = CursorUtil.getColumnIndexOrThrow(_cursor, "presentationType");
          final int _cursorIndexOfPhotoIdsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoIdsJson");
          final int _cursorIndexOfCommittedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "committedAtEpochMs");
          final PresentationHistoryEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPresentationId;
            _tmpPresentationId = _cursor.getString(_cursorIndexOfPresentationId);
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final long _tmpSequence;
            _tmpSequence = _cursor.getLong(_cursorIndexOfSequence);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final String _tmpPresentationType;
            _tmpPresentationType = _cursor.getString(_cursorIndexOfPresentationType);
            final String _tmpPhotoIdsJson;
            _tmpPhotoIdsJson = _cursor.getString(_cursorIndexOfPhotoIdsJson);
            final long _tmpCommittedAtEpochMs;
            _tmpCommittedAtEpochMs = _cursor.getLong(_cursorIndexOfCommittedAtEpochMs);
            _result = new PresentationHistoryEntity(_tmpPresentationId,_tmpScopeKey,_tmpSequence,_tmpFolderKey,_tmpPresentationType,_tmpPhotoIdsJson,_tmpCommittedAtEpochMs);
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
  public Object nextHistory(final String scopeKey, final long sequence,
      final Continuation<? super PresentationHistoryEntity> $completion) {
    final String _sql = "SELECT * FROM presentation_history WHERE scopeKey = ? AND sequence > ? ORDER BY sequence ASC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, scopeKey);
    _argIndex = 2;
    _statement.bindLong(_argIndex, sequence);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PresentationHistoryEntity>() {
      @Override
      @Nullable
      public PresentationHistoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPresentationId = CursorUtil.getColumnIndexOrThrow(_cursor, "presentationId");
          final int _cursorIndexOfScopeKey = CursorUtil.getColumnIndexOrThrow(_cursor, "scopeKey");
          final int _cursorIndexOfSequence = CursorUtil.getColumnIndexOrThrow(_cursor, "sequence");
          final int _cursorIndexOfFolderKey = CursorUtil.getColumnIndexOrThrow(_cursor, "folderKey");
          final int _cursorIndexOfPresentationType = CursorUtil.getColumnIndexOrThrow(_cursor, "presentationType");
          final int _cursorIndexOfPhotoIdsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "photoIdsJson");
          final int _cursorIndexOfCommittedAtEpochMs = CursorUtil.getColumnIndexOrThrow(_cursor, "committedAtEpochMs");
          final PresentationHistoryEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPresentationId;
            _tmpPresentationId = _cursor.getString(_cursorIndexOfPresentationId);
            final String _tmpScopeKey;
            _tmpScopeKey = _cursor.getString(_cursorIndexOfScopeKey);
            final long _tmpSequence;
            _tmpSequence = _cursor.getLong(_cursorIndexOfSequence);
            final String _tmpFolderKey;
            _tmpFolderKey = _cursor.getString(_cursorIndexOfFolderKey);
            final String _tmpPresentationType;
            _tmpPresentationType = _cursor.getString(_cursorIndexOfPresentationType);
            final String _tmpPhotoIdsJson;
            _tmpPhotoIdsJson = _cursor.getString(_cursorIndexOfPhotoIdsJson);
            final long _tmpCommittedAtEpochMs;
            _tmpCommittedAtEpochMs = _cursor.getLong(_cursorIndexOfCommittedAtEpochMs);
            _result = new PresentationHistoryEntity(_tmpPresentationId,_tmpScopeKey,_tmpSequence,_tmpFolderKey,_tmpPresentationType,_tmpPhotoIdsJson,_tmpCommittedAtEpochMs);
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
