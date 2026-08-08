package com.example.familyphotoframe.data.cache

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.ScanCursor
import com.example.familyphotoframe.data.source.ScanEvent
import com.example.familyphotoframe.data.source.ScanOptions
import com.example.familyphotoframe.data.source.SourceHealth
import com.example.familyphotoframe.data.source.SourceId
import com.example.familyphotoframe.data.source.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class MediaCacheTest {

    private lateinit var db: AppDatabase
    private lateinit var pngBytes: ByteArray

    /** A source that serves the same valid PNG for any item. */
    private inner class FakeSource : PhotoSource {
        override val id = SourceId("fake")
        override val type = SourceType.SMB_SOURCE
        override suspend fun healthCheck(timeoutMs: Long): SourceHealth = SourceHealth.Ok
        override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> =
            flowOf(ScanEvent.Finished(ScanCursor("x")))
        override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream =
            ByteArrayInputStream(pngBytes)
    }

    private fun item(key: String) = PhotoItem(
        stableId = key, sourceId = SourceId("fake"), normalizedPath = "$key.png",
        folderName = "f", fileName = "$key.png", mimeType = null,
        sizeBytes = pngBytes.size.toLong(), fileModifiedEpochMs = 1, openToken = "tok/$key",
    )

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        pngBytes = ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
    }

    @After fun tearDown() = db.close()

    private fun cache(maxBytes: Long) = MediaCache(
        ApplicationProvider.getApplicationContext(), db.cacheIndexDao(), Dispatchers.IO,
    ) { maxBytes }

    @Test fun downloadsThenServesFromCache() = runBlocking {
        val cache = cache(10L * 1024 * 1024)
        val f1 = cache.get(item("k1"), FakeSource(), emptySet())
        assertNotNull(f1); assertTrue(f1!!.exists())
        // Second call is a cache hit: same file, and the index has exactly one entry.
        val f2 = cache.get(item("k1"), FakeSource(), emptySet())
        assertEquals(f1, f2)
        assertNotNull(db.cacheIndexDao().get("k1"))
    }

    @Test fun evictionSparesProtectedKey() = runBlocking {
        val cache = cache(1L) // tiny budget forces eviction on every write
        cache.get(item("keep"), FakeSource(), setOf("keep"))
        cache.get(item("drop"), FakeSource(), setOf("keep"))
        // The protected key is never evicted.
        assertNotNull(db.cacheIndexDao().get("keep"))
    }

    @Test fun clearEmptiesCache() = runBlocking {
        val cache = cache(10L * 1024 * 1024)
        cache.get(item("k1"), FakeSource(), emptySet())
        cache.clear()
        assertNull(db.cacheIndexDao().get("k1"))
        assertEquals(0L, db.cacheIndexDao().totalSizeBytes())
    }
}
