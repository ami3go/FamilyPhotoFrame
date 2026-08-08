package com.example.familyphotoframe.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PhotoDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.photoDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        path: String,
        shownAt: Long?,
        indexedAt: Long = 100,
        fails: Int = 0,
        mimeType: String = "image/jpeg",
    ) =
        PhotoItemEntity(
            stableId = "stable-$path",
            sourceId = "local_saf",
            normalizedPath = path,
            folderName = "Family",
            fileName = path,
            mimeType = mimeType,
            sizeBytes = 10,
            fileModifiedEpochMs = 0,
            openToken = "content://tree/$path",
            indexedAtEpochMs = indexedAt,
            lastShownAtEpochMs = shownAt,
            decodeFailureCount = fails,
        )

    @Test
    fun leastRecentWindow_ordersNullsThenOldest() = runBlocking {
        dao.insertBatch(
            listOf(
                item("a.jpg", shownAt = 5000),
                item("b.jpg", shownAt = null),
                item("c.jpg", shownAt = 1000),
            )
        )
        val window = dao.leastRecentWindow("local_saf", maxFailures = 3, window = 10, allowHeif = 1)
        assertEquals(listOf("b.jpg", "c.jpg", "a.jpg"), window.map { it.normalizedPath })
    }

    @Test
    fun leastRecentWindow_excludesSuppressedAndHidden() = runBlocking {
        dao.insertBatch(
            listOf(
                item("ok.jpg", shownAt = null),
                item("bad.jpg", shownAt = null, fails = 3),
            )
        )
        val window = dao.leastRecentWindow("local_saf", maxFailures = 3, window = 10, allowHeif = 1)
        assertEquals(listOf("ok.jpg"), window.map { it.normalizedPath })
    }

    @Test
    fun legacyPlaybackFlag_excludesHeifWithoutChangingIndex() = runBlocking {
        dao.insertBatch(
            listOf(
                item("ok.jpg", shownAt = null),
                item("iphone.HEIC", shownAt = null, mimeType = "image/heic"),
                item("mime-only", shownAt = null, mimeType = "image/heif"),
            )
        )

        val legacy = dao.leastRecentWindow("local_saf", 3, 10, allowHeif = 0)
        assertEquals(listOf("ok.jpg"), legacy.map { it.normalizedPath })
        assertEquals(3, dao.countForSource("local_saf"))
        assertEquals(1, dao.eligibleCount(3, allowHeif = 0))
        assertEquals(2, dao.failedOrUnsupportedCount(3, allowHeif = 0))
        assertEquals(3, dao.eligibleCount(3, allowHeif = 1))
    }

    @Test
    fun uniqueConflict_replacesSameLogicalRow() = runBlocking {
        dao.insertBatch(listOf(item("dup.jpg", shownAt = null, indexedAt = 1)))
        dao.insertBatch(listOf(item("dup.jpg", shownAt = null, indexedAt = 2)))
        assertEquals(1, dao.countForSource("local_saf"))
    }

    @Test
    fun reconcile_deletesStaleRows() = runBlocking {
        dao.insertBatch(
            listOf(
                item("kept.jpg", shownAt = null, indexedAt = 200),
                item("stale.jpg", shownAt = null, indexedAt = 50),
            )
        )
        dao.deleteStaleForSource("local_saf", scanStartedEpochMs = 100)
        val window = dao.leastRecentWindow("local_saf", maxFailures = 3, window = 10, allowHeif = 1)
        assertEquals(listOf("kept.jpg"), window.map { it.normalizedPath })
    }

    @Test
    fun markShown_updatesTimestamp_movingItToBackOfQueue() = runBlocking {
        dao.insertBatch(listOf(item("x.jpg", shownAt = null), item("y.jpg", shownAt = null)))
        val first = dao.leastRecentWindow("local_saf", 3, 10, 1).first()
        dao.markShown(first.id, ts = 9999)
        val refreshed = dao.byId(first.id)
        assertEquals(9999L, refreshed?.lastShownAtEpochMs)
        // After being shown, it should no longer be at the very front when another null exists.
        val front = dao.leastRecentWindow("local_saf", 3, 10, 1).first()
        assertTrue(front.id != first.id)
    }

    @Test
    fun recordDecodeFailure_incrementsCount() = runBlocking {
        dao.insertBatch(listOf(item("c.jpg", shownAt = null)))
        val row = dao.leastRecentWindow("local_saf", 3, 10, 1).first()
        dao.recordDecodeFailure(row.id, ts = 123)
        assertEquals(1, dao.byId(row.id)?.decodeFailureCount)
    }
}
