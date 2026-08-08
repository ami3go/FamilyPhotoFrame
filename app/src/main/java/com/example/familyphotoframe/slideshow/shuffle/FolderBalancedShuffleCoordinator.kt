package com.example.familyphotoframe.slideshow.shuffle

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only public ordering authority for folder-balanced playback.
 * Every touch/web/auto/history/reconciliation action is serialized by one mutex.
 */
class FolderBalancedShuffleCoordinator(
    private val repository: ShuffleRepository,
    private val eligibilityProvider: ShuffleEligibilityProvider,
) {
    data class PoolConfiguration(
        val descriptor: ShuffleScopeDescriptor,
        val sourceIds: List<String>,
        val maxFailures: Int,
        val favoritesOnly: Boolean,
        val cachedOnly: Boolean,
        val selectedFolders: List<String>,
        val unavailableSourceIds: Set<String> = emptySet(),
        val exhaustedUnavailableSourceIds: Set<String> = emptySet(),
    )

    private val mutex = Mutex()
    @Volatile private var activeScopeKey: String? = null

    /** Safe from any thread; the next reserve refreshes the global folder snapshot. */
    fun invalidateEligibility() = eligibilityProvider.invalidate()

    suspend fun startupRecovery(): Int = mutex.withLock { repository.recoverInterruptedReservations() }

    suspend fun reserveNext(
        configuration: PoolConfiguration,
        collageLookahead: Int,
    ): ShuffleAdvanceResult = mutex.withLock {
        activeScopeKey = configuration.descriptor.scopeKey
        val query = ShuffleEligibilityProvider.Query(
            revision = configuration.descriptor.eligibilityRevision,
            sourceIds = configuration.sourceIds,
            maxFailures = configuration.maxFailures,
            favoritesOnly = configuration.favoritesOnly,
            cachedOnly = configuration.cachedOnly,
            selectedFolders = configuration.selectedFolders,
            temporarilyUnavailableSourceIds = configuration.unavailableSourceIds,
            exhaustedUnavailableSourceIds = configuration.exhaustedUnavailableSourceIds,
        )
        val snapshot = eligibilityProvider.folderSnapshot(query)
        repository.reserve(configuration.descriptor, snapshot, collageLookahead) { storageKey ->
            val folderKey = runCatching { FolderKey.parse(storageKey) }.getOrNull()
                ?: return@reserve null
            eligibilityProvider.folderMembers(query, folderKey)
        }
    }

    suspend fun commitPrepared(
        scopeKey: String,
        reservationId: String,
        memberIds: List<Long>,
        presentationType: String,
    ): HistoryPresentation? = mutex.withLock {
        repository.commitPrepared(scopeKey, reservationId, memberIds, presentationType).also { committed ->
            if (committed != null) repository.cleanup(activeScopeKey)
        }
    }

    suspend fun releaseFolderFailure(
        scopeKey: String,
        reservationId: String,
        reason: String,
    ): Boolean = mutex.withLock {
        repository.releaseAfterFolderFailure(scopeKey, reservationId, reason)
    }

    suspend fun releaseAnchorFailure(
        scopeKey: String,
        reservationId: String,
        failedPhotoId: Long,
        reason: String,
    ): Boolean = mutex.withLock {
        repository.releaseAfterAnchorFailure(scopeKey, reservationId, failedPhotoId, reason)
    }

    suspend fun recordCandidateFailure(
        scopeKey: String,
        reservationId: String,
        failedPhotoId: Long,
    ): Boolean = mutex.withLock {
        repository.recordReservedCandidateFailure(scopeKey, reservationId, failedPhotoId)
    }

    suspend fun cancelReservation(scopeKey: String, reservationId: String, reason: String): Boolean =
        mutex.withLock { repository.release(scopeKey, reservationId, reason) }

    suspend fun previous(scopeKey: String): HistoryPresentation? =
        mutex.withLock { repository.previousHistory(scopeKey) }

    suspend fun nextFromHistory(scopeKey: String): HistoryPresentation? =
        mutex.withLock { repository.nextHistory(scopeKey) }

    suspend fun newestHistory(scopeKey: String): HistoryPresentation? =
        mutex.withLock { repository.newestHistory(scopeKey) }

    suspend fun resetActive(clearHistory: Boolean = false) = mutex.withLock {
        activeScopeKey?.let { repository.reset(it, clearHistory) }
    }

    suspend fun resetScope(scopeKey: String, clearHistory: Boolean = false) = mutex.withLock {
        repository.reset(scopeKey, clearHistory)
    }

    suspend fun resetPlaylist(playlistId: String, clearHistory: Boolean = false) = mutex.withLock {
        repository.resetPlaylist(playlistId, clearHistory)
    }

    suspend fun resetAll(clearHistory: Boolean = false) = mutex.withLock {
        repository.resetAll(clearHistory)
    }

    suspend fun deletePlaylist(playlistId: String) = mutex.withLock {
        repository.deletePlaylist(playlistId)
    }

    suspend fun progress(scopeKey: String): ShuffleProgress =
        mutex.withLock { repository.progress(scopeKey) }

    suspend fun cleanup() = mutex.withLock { repository.cleanup(activeScopeKey) }
}
