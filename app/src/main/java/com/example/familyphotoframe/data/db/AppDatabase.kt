package com.example.familyphotoframe.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * App database. Schema v9 adds a persistent thumbnail cache for local-source photos on
 * top of canonical folder identity, asynchronous content hashes, durable folder-balanced
 * shuffle queues, reservations, and bounded presentation history. Every migration remains
 * explicit and additive; destructive fallback is intentionally forbidden.
 *
 * [exportSchema] stays enabled so release builds can export the authoritative
 * Room schema for migration review and device compatibility testing.
 */
@Database(
    entities = [
        PhotoItemEntity::class,
        SourceConfigEntity::class,
        SmbSourceConfigEntity::class,
        LocalSafSourceConfigEntity::class,
        SecretEntity::class,
        CacheIndexEntity::class,
        RememberedBrowserEntity::class,
        ShuffleScopeEntity::class,
        FolderShuffleEntryEntity::class,
        FolderPhotoCycleEntity::class,
        PhotoShuffleEntryEntity::class,
        ShuffleReservationEntity::class,
        PresentationHistoryEntity::class,
        LocalThumbnailCacheEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun sourceConfigDao(): SourceConfigDao
    abstract fun secretDao(): SecretDao
    abstract fun cacheIndexDao(): CacheIndexDao
    abstract fun rememberedBrowserDao(): RememberedBrowserDao
    abstract fun shuffleDao(): ShuffleDao
    abstract fun localThumbnailCacheDao(): LocalThumbnailCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "frame.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9,
                    )
                    // No fallbackToDestructiveMigration(): destructive loss is forbidden.
                    .build()
                    .also { instance = it }
            }
    }
}
