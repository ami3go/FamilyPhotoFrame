package com.example.familyphotoframe.slideshow.shuffle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.db.PhotoItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShuffleEligibilityProviderTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun exactContentDuplicatesCollapseOnlyInsideSameDirectFolder() = runBlocking {
        val hash = "ab".repeat(32)
        db.photoDao().insertBatch(
            listOf(
                row(1, "local", "Trip/a.jpg", "Trip", hash),
                row(2, "local", "Trip/copy.jpg", "Trip", hash),
                row(3, "local", "Other/a.jpg", "Other", hash),
                row(4, "nas", "Trip/a.jpg", "Trip", hash),
            ),
        )

        val snapshot = ShuffleEligibilityProvider(db.photoDao()).snapshot(
            revision = 1,
            sourceIds = listOf("local", "nas"),
            maxFailures = 3,
            favoritesOnly = false,
            cachedOnly = false,
            selectedFolders = emptyList(),
        )

        assertEquals(3, snapshot.folders.size)
        val localTrip = snapshot.folders.single {
            it.folderKey == FolderKey("local", "Trip")
        }
        assertEquals(1, localTrip.members.size)
        assertEquals(setOf(1L, 2L), localTrip.members.single().equivalentPhotoIds)
        assertTrue(snapshot.folders.single { it.folderKey == FolderKey("local", "Other") }.members.isNotEmpty())
        assertTrue(snapshot.folders.single { it.folderKey == FolderKey("nas", "Trip") }.members.isNotEmpty())
    }

    @Test fun fallbackIdentityKeepsDifferentFilesSeparateUntilHashExists() = runBlocking {
        db.photoDao().insertBatch(
            listOf(
                row(10, "local", "Trip/a.jpg", "Trip", null),
                row(11, "local", "Trip/b.jpg", "Trip", null),
            ),
        )

        val folder = ShuffleEligibilityProvider(db.photoDao()).snapshot(
            revision = 1,
            sourceIds = listOf("local"),
            maxFailures = 3,
            favoritesOnly = false,
            cachedOnly = false,
            selectedFolders = emptyList(),
        ).folders.single()

        assertEquals(2, folder.members.size)
    }

    @Test fun playbackSnapshotLoadsFolderMetadataThenOnlyCurrentFolderMembers() = runBlocking {
        db.photoDao().insertBatch(
            listOf(
                row(20, "local", "A/a.jpg", "A", null),
                row(21, "local", "A/b.jpg", "A", null),
                row(22, "local", "B/c.jpg", "B", null),
            ),
        )
        val provider = ShuffleEligibilityProvider(db.photoDao())
        val query = ShuffleEligibilityProvider.Query(
            revision = 1,
            sourceIds = listOf("local"),
            maxFailures = 3,
            favoritesOnly = false,
            cachedOnly = false,
            selectedFolders = emptyList(),
        )

        val folders = provider.folderSnapshot(query)
        assertEquals(2, folders.folders.size)
        assertTrue(folders.folders.all { it.members.isEmpty() })
        assertEquals(setOf(1, 2), folders.folders.map { it.memberCount }.toSet())

        val folderA = provider.folderMembers(query, FolderKey("local", "A"))
        assertEquals(2, folderA?.members?.size)
        assertTrue(folderA!!.members.all { it.folderKey == FolderKey("local", "A") })
    }

    @Test fun legacyDeviceExcludesHeifBeforeFolderOrPhotoReservation() = runBlocking {
        db.photoDao().insertBatch(
            listOf(
                row(30, "local", "Mixed/a.jpg", "Mixed", null),
                row(31, "local", "Mixed/b.HEIC", "Mixed", null, "image/heic"),
                row(32, "local", "HeifOnly/c.heif", "HeifOnly", null, "image/heif"),
            ),
        )
        val provider = ShuffleEligibilityProvider(db.photoDao(), allowHeif = false)
        val query = ShuffleEligibilityProvider.Query(
            revision = 2,
            sourceIds = listOf("local"),
            maxFailures = 3,
            favoritesOnly = false,
            cachedOnly = false,
            selectedFolders = emptyList(),
        )

        val folders = provider.folderSnapshot(query)
        assertEquals(listOf(FolderKey("local", "Mixed")), folders.folders.map { it.key })
        assertEquals(1, folders.folders.single().memberCount)
        assertEquals(listOf(30L), provider.folderMembers(query, folders.folders.single().key)!!.members.map { it.photoId })
    }

    @Test fun folderSnapshotCacheRefreshesAfterExplicitIndexInvalidation() = runBlocking {
        db.photoDao().insertBatch(listOf(row(40, "local", "A/a.jpg", "A", null)))
        val provider = ShuffleEligibilityProvider(db.photoDao())
        val query = ShuffleEligibilityProvider.Query(
            revision = 3,
            sourceIds = listOf("local"),
            maxFailures = 3,
            favoritesOnly = false,
            cachedOnly = false,
            selectedFolders = emptyList(),
        )
        assertEquals(1, provider.folderSnapshot(query).folders.size)

        db.photoDao().insertBatch(listOf(row(41, "local", "B/b.jpg", "B", null)))
        assertEquals(1, provider.folderSnapshot(query).folders.size)
        provider.invalidate()
        assertEquals(2, provider.folderSnapshot(query).folders.size)
    }

    private fun row(
        id: Long,
        sourceId: String,
        path: String,
        directory: String,
        hash: String?,
        mimeType: String = "image/jpeg",
    ) = PhotoItemEntity(
        id = id,
        stableId = "$sourceId:$path",
        sourceId = sourceId,
        normalizedPath = path,
        folderName = directory.substringAfterLast('/'),
        fileName = path.substringAfterLast('/'),
        mimeType = mimeType,
        sizeBytes = 4,
        fileModifiedEpochMs = 1,
        openToken = path,
        indexedAtEpochMs = 1,
        canonicalDirectory = directory,
        contentSha256 = hash,
        contentHashScannedAtEpochMs = hash?.let { 1 },
    )
}
