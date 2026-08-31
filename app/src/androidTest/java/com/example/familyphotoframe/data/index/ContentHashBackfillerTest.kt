package com.example.familyphotoframe.data.index

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.db.PhotoItemEntity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.ScanCursor
import com.example.familyphotoframe.data.source.ScanEvent
import com.example.familyphotoframe.data.source.ScanOptions
import com.example.familyphotoframe.data.source.SourceHealth
import com.example.familyphotoframe.data.source.SourceId
import com.example.familyphotoframe.data.source.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ContentHashBackfillerTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun hashesOriginalBytesAndPersistsLowercaseSha256() = runBlocking {
        val bytes = "family-photo".toByteArray()
        db.photoDao().insertBatch(listOf(row(1, bytes.size.toLong())))
        val source = FakeSource(bytes)
        val hash = ContentHashBackfiller(
            dao = db.photoDao(),
            diagnostics = DiagnosticsLog(),
            timeoutMs = 5_000,
            batchSize = 4,
        ).backfill(1) { source }

        assertEquals("8488abcb16aadf96523255102524b4d447e49c15b6d072a76dcf6ccc272ae2df", hash)
        assertEquals(hash, db.photoDao().byId(1)?.contentSha256)
        assertTrue(source.lastOptions?.preferOriginal == true)
    }

    @Test fun reusesOneDigestAcrossBackgroundBatch() = runBlocking {
        val bytes = "same-content".toByteArray()
        db.photoDao().insertBatch(listOf(row(1, bytes.size.toLong()), row(2, bytes.size.toLong())))
        var factoryCalls = 0
        val result = ContentHashBackfiller(
            dao = db.photoDao(),
            diagnostics = DiagnosticsLog(),
            timeoutMs = 5_000,
            batchSize = 4,
            messageDigestFactory = {
                factoryCalls++
                MessageDigest.getInstance("SHA-256")
            },
        ).backfillPending(FakeSource(bytes), maxBatches = 1)

        assertEquals(2, result.indexed)
        assertEquals(0, result.failed)
        assertEquals(1, factoryCalls)
        assertEquals(db.photoDao().byId(1)?.contentSha256, db.photoDao().byId(2)?.contentSha256)
    }

    private fun row(id: Long, size: Long) = PhotoItemEntity(
        id = id,
        stableId = "local:Trip/$id.jpg",
        sourceId = "local",
        normalizedPath = "Trip/$id.jpg",
        folderName = "Trip",
        fileName = "$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = size,
        fileModifiedEpochMs = 1,
        openToken = "Trip/$id.jpg",
        indexedAtEpochMs = 1,
        canonicalDirectory = "Trip",
    )

    private class FakeSource(private val bytes: ByteArray) : PhotoSource {
        override val id = SourceId("local")
        override val type = SourceType.APP_PRIVATE_BUILTIN
        var lastOptions: OpenOptions? = null

        override suspend fun healthCheck(timeoutMs: Long): SourceHealth = SourceHealth.Ok
        override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = emptyFlow()
        override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream {
            lastOptions = options
            return ByteArrayInputStream(bytes)
        }
    }
}
