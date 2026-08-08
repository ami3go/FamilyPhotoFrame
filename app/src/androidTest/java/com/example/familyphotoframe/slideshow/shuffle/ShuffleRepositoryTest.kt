package com.example.familyphotoframe.slideshow.shuffle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShuffleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ShuffleRepository
    private var now = 1_000L

    private val descriptor = ShuffleScopeDescriptor("scope:test", "playlist", 1L, "primary")
    private val folderA = FolderKey("local", "A")
    private val folderB = FolderKey("local", "B")

    private fun member(id: Long, folder: FolderKey) = EligiblePhotoMember(
        photoId = id,
        folderKey = folder,
        folderPhotoKey = FolderPhotoKey(folder, "content-$id").storageKey(),
        sourceId = folder.sourceId,
        canonicalRelativePath = "${folder.canonicalRelativeDirectory}/$id.jpg",
        contentIdentity = "content-$id",
    )

    private val snapshot = ShuffleEligibilitySnapshot(
        revision = 1L,
        folders = listOf(
            EligibleFolder(folderA, listOf(member(1, folderA), member(2, folderA))),
            EligibleFolder(folderB, listOf(member(3, folderB), member(4, folderB))),
        ),
    )

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ShuffleRepository(db, DiagnosticsLog(), FixedSeedShuffleRandom(7)) { now++ }
    }

    @After fun close() = db.close()

    @Test fun reservationIsStable_andCommitAdvancesExactlyOnce() = runBlocking {
        val first = (repository.reserve(descriptor, snapshot, 1) as ShuffleAdvanceResult.Reserved).presentation
        val duplicate = (repository.reserve(descriptor, snapshot, 1) as ShuffleAdvanceResult.Reserved).presentation
        assertEquals(first.reservationId, duplicate.reservationId)
        assertEquals(first.folderKey, duplicate.folderKey)

        val before = repository.progress(descriptor.scopeKey)
        assertEquals(0, before.foldersPresented)
        assertNotNull(before.activeReservationAgeMs)

        val committed = repository.commitPrepared(
            descriptor.scopeKey,
            first.reservationId,
            listOf(first.anchorPhotoId),
            "SINGLE",
        )
        assertNotNull(committed)
        assertNull(db.shuffleDao().reservation(descriptor.scopeKey))
        assertEquals(1, repository.progress(descriptor.scopeKey).foldersPresented)
        assertNull(repository.commitPrepared(descriptor.scopeKey, first.reservationId, listOf(first.anchorPhotoId), "SINGLE"))

        val second = (repository.reserve(descriptor, snapshot, 1) as ShuffleAdvanceResult.Reserved).presentation
        assertNotEquals(first.folderKey, second.folderKey)
    }

    @Test fun anchorFailureKeepsFolderTurnPending_andRecoveryReleasesReservation() = runBlocking {
        val first = (repository.reserve(descriptor, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
        assertTrue(repository.releaseAfterAnchorFailure(
            descriptor.scopeKey, first.reservationId, first.anchorPhotoId, "decode",
        ))
        assertEquals(0, repository.progress(descriptor.scopeKey).foldersPresented)

        val retry = (repository.reserve(descriptor, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
        assertEquals(first.folderKey, retry.folderKey)
        assertNotEquals(first.anchorPhotoId, retry.anchorPhotoId)

        val recovered = ShuffleRepository(db, DiagnosticsLog(), FixedSeedShuffleRandom(9)) { now++ }
            .recoverInterruptedReservations()
        assertEquals(1, recovered)
        assertNull(db.shuffleDao().reservation(descriptor.scopeKey))
        assertEquals(0, repository.progress(descriptor.scopeKey).foldersPresented)
    }

    @Test fun exhaustedUnavailableSourceIsSkippedOnce_andHealthyFolderContinues() = runBlocking {
        val unavailableFolder = FolderKey("nas", "Offline")
        val healthyFolder = FolderKey("local", "Healthy")
        val outageSnapshot = ShuffleEligibilitySnapshot(
            revision = 2L,
            folders = listOf(
                EligibleFolder(unavailableFolder, listOf(member(10, unavailableFolder))),
                EligibleFolder(healthyFolder, listOf(member(11, healthyFolder))),
            ),
            temporarilyUnavailableSourceIds = setOf("nas"),
            exhaustedUnavailableSourceIds = setOf("nas"),
        )
        val outageDescriptor = descriptor.copy(scopeKey = "scope:outage", eligibilityRevision = 2L)
        val reservation = (repository.reserve(outageDescriptor, outageSnapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
        assertEquals(healthyFolder.storageKey(), reservation.folderKey)
        val progress = repository.progress(outageDescriptor.scopeKey)
        assertEquals(1, progress.foldersSkipped)
        assertEquals(0, progress.foldersPresented)
    }

    @Test fun resetPlaylistCoversAllPoolScopes_andDeleteRemovesOnlyThatPlaylist() = runBlocking {
        val fallback = descriptor.copy(scopeKey = "scope:fallback", poolRole = "fallback")
        val other = descriptor.copy(scopeKey = "scope:other", playlistId = "other-playlist")
        listOf(descriptor, fallback, other).forEach { d ->
            val reservation = (repository.reserve(d, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
            repository.commitPrepared(d.scopeKey, reservation.reservationId, listOf(reservation.anchorPhotoId), "SINGLE")
        }
        repository.resetPlaylist("playlist", clearHistory = false)
        assertEquals(0L, repository.progress(descriptor.scopeKey).folderCycle)
        assertEquals(0L, repository.progress(fallback.scopeKey).folderCycle)
        assertTrue(repository.progress(other.scopeKey).folderCycle > 0L)

        repository.deletePlaylist("playlist")
        assertEquals(0, db.shuffleDao().scopesForPlaylist("playlist").size)
        assertEquals(1, db.shuffleDao().scopesForPlaylist("other-playlist").size)
    }


    @Test fun commitRejectsUnreservedOrFailedCompanions_withoutAdvancing() = runBlocking {
        val reservation = (repository.reserve(descriptor, snapshot, 1) as ShuffleAdvanceResult.Reserved).presentation
        val secondary = reservation.candidatePhotoIds.drop(1).first()

        assertNull(repository.commitPrepared(
            descriptor.scopeKey,
            reservation.reservationId,
            listOf(reservation.anchorPhotoId, 999_999L),
            "COLLAGE",
        ))
        assertNotNull(db.shuffleDao().reservation(descriptor.scopeKey))
        assertEquals(0, repository.progress(descriptor.scopeKey).foldersPresented)

        assertTrue(repository.recordReservedCandidateFailure(
            descriptor.scopeKey,
            reservation.reservationId,
            secondary,
        ))
        assertNull(repository.commitPrepared(
            descriptor.scopeKey,
            reservation.reservationId,
            listOf(reservation.anchorPhotoId, secondary),
            "COLLAGE",
        ))
        assertNotNull(db.shuffleDao().reservation(descriptor.scopeKey))
        assertEquals(0, repository.progress(descriptor.scopeKey).foldersPresented)

        assertNotNull(repository.commitPrepared(
            descriptor.scopeKey,
            reservation.reservationId,
            listOf(reservation.anchorPhotoId),
            "SINGLE",
        ))
        assertEquals(1, repository.progress(descriptor.scopeKey).foldersPresented)
    }

    @Test fun candidateFailureApiCannotRetireReservationAnchor() = runBlocking {
        val reservation = (repository.reserve(descriptor, snapshot, 1) as ShuffleAdvanceResult.Reserved).presentation
        assertTrue(!repository.recordReservedCandidateFailure(
            descriptor.scopeKey,
            reservation.reservationId,
            reservation.anchorPhotoId,
        ))
        assertNotNull(db.shuffleDao().reservation(descriptor.scopeKey))
        assertEquals(0, repository.progress(descriptor.scopeKey).foldersPresented)
    }

    @Test fun historyCursorSurvivesRepositoryRecreation() = runBlocking {
        repeat(2) {
            val reservation = (repository.reserve(descriptor, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
            repository.commitPrepared(descriptor.scopeKey, reservation.reservationId, listOf(reservation.anchorPhotoId), "SINGLE")
        }
        val recreated = ShuffleRepository(db, DiagnosticsLog(), FixedSeedShuffleRandom(1)) { now++ }
        val previous = recreated.previousHistory(descriptor.scopeKey)
        assertNotNull(previous)
        val forward = recreated.nextHistory(descriptor.scopeKey)
        assertNotNull(forward)
        assertTrue(forward!!.sequence > previous!!.sequence)
    }
    @Test fun folderLevelFailureRetriesTwice_thenSkipsExactlyOnce() = runBlocking {
        val first = (repository.reserve(descriptor, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
        assertTrue(repository.releaseAfterFolderFailure(
            descriptor.scopeKey, first.reservationId, "permission_denied",
        ))
        assertEquals(0, repository.progress(descriptor.scopeKey).foldersSkipped)
        assertEquals(
            1,
            db.shuffleDao().folderEntries(descriptor.scopeKey, 1L)
                .first { it.folderKey == first.folderKey }.retryCount,
        )

        val second = (repository.reserve(descriptor, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
        assertEquals(first.folderKey, second.folderKey)
        assertEquals(first.anchorPhotoId, second.anchorPhotoId)
        assertTrue(repository.releaseAfterFolderFailure(
            descriptor.scopeKey, second.reservationId, "permission_denied",
        ))
        assertEquals(1, repository.progress(descriptor.scopeKey).foldersSkipped)
        assertNull(db.shuffleDao().reservation(descriptor.scopeKey))

        val healthy = (repository.reserve(descriptor, snapshot, 0) as ShuffleAdvanceResult.Reserved).presentation
        assertNotEquals(first.folderKey, healthy.folderKey)
    }

    @Test fun contentHashIdentityUpgradeDoesNotRepeatConsumedFallbackMember() = runBlocking {
        val identityDescriptor = descriptor.copy(scopeKey = "scope:identity")
        val initial = ShuffleEligibilitySnapshot(
            revision = 1L,
            folders = listOf(EligibleFolder(folderA, listOf(member(1, folderA), member(2, folderA)))),
        )
        val first = (repository.reserve(identityDescriptor, initial, 0) as ShuffleAdvanceResult.Reserved).presentation
        repository.commitPrepared(identityDescriptor.scopeKey, first.reservationId, listOf(first.anchorPhotoId), "SINGLE")

        val consumedId = first.anchorPhotoId
        val remainingId = if (consumedId == 1L) 2L else 1L
        val hashKey = FolderPhotoKey(folderA, "a".repeat(64)).storageKey()
        val upgradedMember = EligiblePhotoMember(
            photoId = consumedId,
            folderKey = folderA,
            folderPhotoKey = hashKey,
            sourceId = folderA.sourceId,
            canonicalRelativePath = "A/$consumedId.jpg",
            contentIdentity = "a".repeat(64),
            equivalentPhotoIds = setOf(consumedId, 99L),
        )
        val remaining = member(remainingId, folderA)
        val upgraded = ShuffleEligibilitySnapshot(
            revision = 2L,
            folders = listOf(EligibleFolder(folderA, listOf(upgradedMember, remaining))),
        )
        val next = (repository.reserve(
            identityDescriptor.copy(eligibilityRevision = 2L), upgraded, 0,
        ) as ShuffleAdvanceResult.Reserved).presentation
        assertEquals(remainingId, next.anchorPhotoId)
    }

}
