package com.example.familyphotoframe.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the explicit v1→v2 migration (spec §6.3 / §27.5): a Phase 0 database is
 * upgraded without losing rows, the new tables/columns exist, and Room's own
 * post-migration schema validation passes (it runs when the Room DB is opened).
 *
 * A v1 database is created by hand with the exact Phase 0 `photos` schema, seeded,
 * then opened through Room with [MIGRATION_1_2] applied.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-1-to-2-test.db"

    @Before fun setUp() { ctx.deleteDatabase(dbName) }
    @After fun tearDown() { ctx.deleteDatabase(dbName) }

    @Test
    fun migrate1ToLatest_preservesRows_andCreatesNewSchema() {
        // ---- Build a v1 database exactly as Phase 0 (Room v1) would have. ----
        val path = ctx.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        val v1 = SQLiteDatabase.openOrCreateDatabase(path, null)
        v1.execSQL(
            """
            CREATE TABLE `photos` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `stableId` TEXT NOT NULL,
              `sourceId` TEXT NOT NULL,
              `normalizedPath` TEXT NOT NULL,
              `folderName` TEXT NOT NULL,
              `fileName` TEXT NOT NULL,
              `mimeType` TEXT,
              `sizeBytes` INTEGER NOT NULL,
              `fileModifiedEpochMs` INTEGER NOT NULL,
              `openToken` TEXT NOT NULL,
              `indexedAtEpochMs` INTEGER NOT NULL,
              `isHidden` INTEGER NOT NULL,
              `lastShownAtEpochMs` INTEGER,
              `decodeFailureCount` INTEGER NOT NULL,
              `lastDecodeFailureAtEpochMs` INTEGER
            )
            """.trimIndent()
        )
        v1.execSQL("CREATE UNIQUE INDEX `index_photos_sourceId_normalizedPath` ON `photos` (`sourceId`, `normalizedPath`)")
        v1.execSQL("CREATE INDEX `index_photos_sourceId_isHidden_lastShownAtEpochMs` ON `photos` (`sourceId`, `isHidden`, `lastShownAtEpochMs`)")
        v1.execSQL("CREATE INDEX `index_photos_stableId` ON `photos` (`stableId`)")
        v1.execSQL(
            "INSERT INTO photos (stableId, sourceId, normalizedPath, folderName, fileName, mimeType, " +
                "sizeBytes, fileModifiedEpochMs, openToken, indexedAtEpochMs, isHidden, lastShownAtEpochMs, " +
                "decodeFailureCount, lastDecodeFailureAtEpochMs) VALUES " +
                "('s1','local_saf','a/b.jpg','a','b.jpg','image/jpeg',100,0,'content://x',5,0,NULL,0,NULL)"
        )
        v1.version = 1
        v1.close()

        // ---- Open through Room with the migration; this also validates the schema. ----
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            )
            .build()

        runBlocking {
            // Pre-existing row survived.
            assertEquals(1, db.photoDao().countForSource("local_saf"))
            val row = db.photoDao().byId(1)
            assertNotNull(row)
            assertEquals("s1", row!!.stableId)
            // New columns are present with their defaults/nulls.
            assertEquals(0, row.exifOrientation)
            assertEquals(false, row.isFavorite)
            assertEquals(null, row.cacheKey)
            // v3->v4 (Phase 2 increment 5): caption/GPS columns exist and default to null.
            assertEquals(null, row.caption)
            assertEquals(null, row.gpsLat)
            assertEquals(null, row.gpsLon)
            // v4->v5 (increment 8): null means "EXIF never attempted", so pre-existing
            // rows are correctly queued for the display-time backfiller.
            assertEquals(null, row.exifScannedAtEpochMs)
            // v7->v8: direct-directory identity is backfilled without reading bytes.
            assertEquals("a", row.canonicalDirectory)
            assertEquals(null, row.contentSha256)
            assertEquals(null, row.contentHashScannedAtEpochMs)

            // New tables exist and are queryable.
            assertEquals(0L, db.cacheIndexDao().totalSizeBytes())
            assertEquals(0, db.sourceConfigDao().enabledSources().size)
            assertEquals(0, db.shuffleDao().scopesByRecentUse().size)
            assertEquals(0, db.shuffleDao().allReservations().size)
        }
        db.close()
    }
}
