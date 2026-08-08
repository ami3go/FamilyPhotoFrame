package com.example.familyphotoframe.slideshow.shuffle

import androidx.room.withTransaction
import com.example.familyphotoframe.data.db.AppDatabase
import com.example.familyphotoframe.data.db.FolderPhotoCycleEntity
import com.example.familyphotoframe.data.db.FolderShuffleEntryEntity
import com.example.familyphotoframe.data.db.PhotoShuffleEntryEntity
import com.example.familyphotoframe.data.db.PresentationHistoryEntity
import com.example.familyphotoframe.data.db.ShuffleReservationEntity
import com.example.familyphotoframe.data.db.ShuffleScopeEntity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import java.util.UUID

/**
 * Room-backed authority for generated queue order and entry state.
 * All multi-row state transitions are atomic and generated order is authoritative.
 */
class ShuffleRepository(
    private val database: AppDatabase,
    private val diagnostics: DiagnosticsLog,
    private val random: ShuffleRandom = KotlinShuffleRandom(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.shuffleDao()
    private val restoredScopesLogged = BoundedScopeLogTracker(MAX_RESTORED_SCOPE_LOG_KEYS)

    suspend fun reserve(
        descriptor: ShuffleScopeDescriptor,
        snapshot: ShuffleEligibilitySnapshot,
        collageLookahead: Int,
        folderLoader: suspend (String) -> EligibleFolder? = { snapshot.folder(it) },
    ): ShuffleAdvanceResult = database.withTransaction {
        dao.reservation(descriptor.scopeKey)?.let { existing ->
            return@withTransaction ShuffleAdvanceResult.Reserved(existing.toDomain())
        }
        var scope = ensureScope(descriptor)
        scope = reconcileFolderCycle(scope, snapshot)
        if (snapshot.folders.isEmpty()) return@withTransaction ShuffleAdvanceResult.Empty

        val attemptLimit = snapshot.folders.size.coerceAtLeast(1) + 1
        repeat(attemptLimit) {
            val folderEntries = dao.folderEntries(scope.scopeKey, scope.activeFolderCycle)
            val exhausted = folderEntries.firstOrNull { entry ->
                entry.state == FolderEntryState.PENDING.name &&
                    FolderKey.sourceIdOrNull(entry.folderKey) in
                    snapshot.exhaustedUnavailableSourceIds
            }
            if (exhausted != null) {
                dao.updateFolderEntry(
                    scope.scopeKey, scope.activeFolderCycle, exhausted.position,
                    FolderEntryState.SKIPPED.name, exhausted.retryCount, "source_retry_exhausted",
                )
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE, "FOLDER_SKIPPED",
                    "scope" to scope.scopeKey,
                    "reason" to "source_retry_exhausted",
                )
                return@repeat
            }
            val folder = folderEntries.firstOrNull { entry ->
                entry.state == FolderEntryState.PENDING.name &&
                    FolderKey.sourceIdOrNull(entry.folderKey) !in
                    snapshot.temporarilyUnavailableSourceIds
            }
            if (folder == null) {
                if (folderEntries.none { it.state == FolderEntryState.PENDING.name }) {
                    scope = startNextFolderCycle(scope, snapshot)
                    return@repeat
                }
                // Every unresolved folder belongs to a deferred source. Healthy pools may
                // continue elsewhere; this scope waits without consuming or retrying them.
                return@withTransaction ShuffleAdvanceResult.Empty
            }

            // Only the currently selected folder's photo rows are materialized. The
            // coordinator mutex prevents another ordering action between folder choice
            // and this transactional member query.
            val eligibleFolder = folderLoader(folder.folderKey)
            if (eligibleFolder == null || eligibleFolder.members.isEmpty()) {
                dao.updateFolderEntry(
                    scope.scopeKey, scope.activeFolderCycle, folder.position,
                    FolderEntryState.REMOVED.name, folder.retryCount, "no_longer_eligible",
                )
                return@repeat
            }
            val photoCycle = reconcilePhotoCycle(scope, eligibleFolder, snapshot.revision)
            val candidates = dao.pendingPhotos(
                scope.scopeKey,
                folder.folderKey,
                photoCycle.activePhotoCycle,
                (collageLookahead.coerceIn(0, MAX_COLLAGE_LOOKAHEAD) + 1),
            )
            if (candidates.isEmpty()) {
                dao.updateFolderEntry(
                    scope.scopeKey, scope.activeFolderCycle, folder.position,
                    FolderEntryState.SKIPPED.name, folder.retryCount, "no_usable_photo",
                )
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE, "FOLDER_SKIPPED",
                    "scope" to scope.scopeKey,
                    "reason" to "no_usable_photo",
                )
                return@repeat
            }

            dao.updateFolderEntry(
                scope.scopeKey, scope.activeFolderCycle, folder.position,
                FolderEntryState.RESERVED.name, folder.retryCount, null,
            )
            candidates.forEach { entry ->
                dao.updatePhotoEntry(
                    scope.scopeKey, folder.folderKey, photoCycle.activePhotoCycle,
                    entry.position, PhotoEntryState.RESERVED.name, entry.failureCount,
                )
            }
            val now = nowMs()
            val reservation = ShuffleReservationEntity(
                scopeKey = scope.scopeKey,
                reservationId = UUID.randomUUID().toString(),
                folderCycle = scope.activeFolderCycle,
                folderPosition = folder.position,
                folderKey = folder.folderKey,
                photoCycle = photoCycle.activePhotoCycle,
                photoPositionsJson = encodeInts(candidates.map { it.position }),
                photoIdsJson = encodeLongs(candidates.map { it.photoId }),
                createdAtEpochMs = now,
            )
            dao.insertReservation(reservation)
            dao.touchScope(scope.scopeKey, now)
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "FOLDER_RESERVED",
                "scope" to scope.scopeKey,
                "folderToken" to diagnosticToken(folder.folderKey, "folder"),
                "cycle" to scope.activeFolderCycle.toString(),
            )
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "PHOTO_RESERVED",
                "scope" to scope.scopeKey,
                "photoCycle" to photoCycle.activePhotoCycle.toString(),
                "candidateCount" to candidates.size.toString(),
            )
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "PRESENTATION_RESERVED",
                "scope" to scope.scopeKey,
                "reservation" to reservation.reservationId.take(12),
                "candidateCount" to candidates.size.toString(),
            )
            return@withTransaction ShuffleAdvanceResult.Reserved(reservation.toDomain())
        }
        ShuffleAdvanceResult.Empty
    }

    /** Commit ordering/history before the prepared presentation is allowed to become visible. */
    suspend fun commitPrepared(
        scopeKey: String,
        reservationId: String,
        presentedPhotoIds: List<Long>,
        presentationType: String,
    ): HistoryPresentation? = database.withTransaction {
        val reservation = dao.reservation(scopeKey)
            ?.takeIf { it.reservationId == reservationId }
            ?: return@withTransaction null
        val scope = dao.scope(scopeKey) ?: return@withTransaction null
        val selectedIds = presentedPhotoIds.distinct()
        val reservedPositions = decodeInts(reservation.photoPositionsJson)
        val reservedIds = decodeLongs(reservation.photoIdsJson)
        val entries = dao.photoEntries(scopeKey, reservation.folderKey, reservation.photoCycle)
            .filter { it.position in reservedPositions }
            .sortedBy { it.position }
        val entriesByPosition = entries.associateBy { it.position }
        val reservationRowsMatch = reservedPositions.size == reservedIds.size &&
            reservedPositions.zip(reservedIds).all { (position, photoId) ->
                entriesByPosition[position]?.photoId == photoId
            }
        val rejectionReason = when {
            reservedIds.isEmpty() || entries.isEmpty() -> "empty_reservation"
            !reservationRowsMatch -> "reservation_rows_changed"
            selectedIds.isEmpty() -> "empty_presentation"
            selectedIds.first() != reservedIds.first() -> "anchor_not_first"
            selectedIds.any { it !in reservedIds } -> "unreserved_photo"
            selectedIds.any { id -> entries.none { it.photoId == id && it.state == PhotoEntryState.RESERVED.name } } ->
                "photo_not_reserved"
            else -> null
        }
        if (rejectionReason != null) {
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "PRESENTATION_COMMIT_REJECTED",
                "scope" to scopeKey,
                "reservation" to reservationId.take(12),
                "reason" to rejectionReason,
                "presentedCount" to selectedIds.size.toString(),
                "reservedCount" to reservedIds.size.toString(),
            )
            return@withTransaction null
        }
        val selected = selectedIds.toSet()

        var lastConsumedKey: String? = null
        entries.forEach { entry ->
            if (entry.photoId in selected) {
                dao.updatePhotoEntry(
                    scopeKey, reservation.folderKey, reservation.photoCycle, entry.position,
                    PhotoEntryState.CONSUMED.name, entry.failureCount,
                )
                lastConsumedKey = entry.folderPhotoKey
            } else if (entry.state == PhotoEntryState.RESERVED.name) {
                dao.updatePhotoEntry(
                    scopeKey, reservation.folderKey, reservation.photoCycle, entry.position,
                    PhotoEntryState.PENDING.name, entry.failureCount,
                )
            }
        }
        dao.updateFolderEntry(
            scopeKey, reservation.folderCycle, reservation.folderPosition,
            FolderEntryState.PRESENTED.name, 0, null,
        )
        dao.photoCycle(scopeKey, reservation.folderKey)?.let { cycle ->
            dao.upsertPhotoCycle(
                cycle.copy(
                    lastConsumedPhotoKey = lastConsumedKey ?: cycle.lastConsumedPhotoKey,
                    lastUsedAtEpochMs = nowMs(),
                )
            )
        }

        val now = nowMs()
        val sequence = scope.latestHistorySequence + 1L
        val history = PresentationHistoryEntity(
            presentationId = UUID.randomUUID().toString(),
            scopeKey = scopeKey,
            sequence = sequence,
            folderKey = reservation.folderKey,
            presentationType = presentationType,
            photoIdsJson = encodeLongs(selectedIds),
            committedAtEpochMs = now,
        )
        dao.insertHistory(history)
        dao.trimHistory(scopeKey, HISTORY_LIMIT)
        dao.deleteReservation(scopeKey)
        dao.upsertScope(
            scope.copy(
                lastPresentedFolderKey = reservation.folderKey,
                lastUsedAtEpochMs = now,
                latestHistorySequence = sequence,
                historyCursorSequence = sequence,
                lastCommitEpochMs = now,
            )
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PHOTO_CONSUMED",
            "scope" to scopeKey,
            "photoCount" to selected.size.toString(),
            "photoCycle" to reservation.photoCycle.toString(),
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_PRESENTED",
            "scope" to scopeKey,
            "folderToken" to diagnosticToken(reservation.folderKey, "folder"),
            "folderCycle" to reservation.folderCycle.toString(),
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PRESENTATION_COMMITTED",
            "scope" to scopeKey,
            "sequence" to sequence.toString(),
            "photoCount" to selected.size.toString(),
        )
        history.toDomain()
    }

    /**
     * Release a reservation after a folder/path-level failure while its source is healthy.
     * The folder turn is retried at most twice in this cycle, then explicitly skipped.
     */
    suspend fun releaseAfterFolderFailure(
        scopeKey: String,
        reservationId: String,
        reason: String,
    ): Boolean = database.withTransaction {
        val reservation = dao.reservation(scopeKey)
            ?.takeIf { it.reservationId == reservationId }
            ?: return@withTransaction false
        val positions = decodeInts(reservation.photoPositionsJson)
        dao.photoEntries(scopeKey, reservation.folderKey, reservation.photoCycle)
            .filter { it.position in positions }
            .forEach { entry ->
                if (entry.state == PhotoEntryState.RESERVED.name) {
                    dao.updatePhotoEntry(
                        scopeKey, reservation.folderKey, reservation.photoCycle, entry.position,
                        PhotoEntryState.PENDING.name, entry.failureCount,
                    )
                }
            }
        val folder = dao.folderEntries(scopeKey, reservation.folderCycle)
            .firstOrNull { it.position == reservation.folderPosition }
        if (folder != null && folder.state == FolderEntryState.RESERVED.name) {
            val retryCount = folder.retryCount + 1
            val exhausted = retryCount >= MAX_FOLDER_RETRIES
            dao.updateFolderEntry(
                scopeKey, reservation.folderCycle, reservation.folderPosition,
                if (exhausted) FolderEntryState.SKIPPED.name else FolderEntryState.PENDING.name,
                retryCount,
                if (exhausted) reason.take(80) else "folder_retry_pending",
            )
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                if (exhausted) "FOLDER_SKIPPED" else "FOLDER_RETRY",
                "scope" to scopeKey,
                "folderToken" to diagnosticToken(reservation.folderKey, "folder"),
                "retryCount" to retryCount.toString(),
                "reason" to reason.take(80),
            )
        }
        dao.deleteReservation(scopeKey)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PRESENTATION_RELEASED",
            "scope" to scopeKey,
            "reason" to "folder_failure:${reason.take(64)}",
        )
        true
    }

    suspend fun releaseAfterAnchorFailure(
        scopeKey: String,
        reservationId: String,
        failedPhotoId: Long,
        reason: String,
    ): Boolean = database.withTransaction {
        val reservation = dao.reservation(scopeKey)
            ?.takeIf { it.reservationId == reservationId }
            ?: return@withTransaction false
        releaseReservationRows(reservation, failedPhotoId, reason)
        true
    }

    /** A failed secondary tile is removed from this cycle; the valid anchor may still commit. */
    suspend fun recordReservedCandidateFailure(
        scopeKey: String,
        reservationId: String,
        failedPhotoId: Long,
    ): Boolean = database.withTransaction {
        val reservation = dao.reservation(scopeKey)
            ?.takeIf { it.reservationId == reservationId }
            ?: return@withTransaction false
        if (decodeLongs(reservation.photoIdsJson).firstOrNull() == failedPhotoId) {
            // The anchor owns the folder turn. It must go through releaseAfterAnchorFailure
            // so the folder entry returns to PENDING atomically with the photo failure.
            return@withTransaction false
        }
        val entry = dao.photoEntries(scopeKey, reservation.folderKey, reservation.photoCycle)
            .firstOrNull { it.photoId == failedPhotoId && it.state == PhotoEntryState.RESERVED.name }
            ?: return@withTransaction false
        val failures = entry.failureCount + 1
        dao.updatePhotoEntry(
            scopeKey, reservation.folderKey, reservation.photoCycle, entry.position,
            if (failures >= QUARANTINE_AFTER_FAILURES) PhotoEntryState.QUARANTINED.name else PhotoEntryState.REMOVED.name,
            failures,
        )
        diagnostics.log(
            DiagnosticsLog.Category.DECODE,
            if (failures >= QUARANTINE_AFTER_FAILURES) "PHOTO_QUARANTINED" else "PHOTO_REMOVED",
            "scope" to scopeKey,
            "failureCount" to failures.toString(),
        )
        true
    }

    suspend fun release(scopeKey: String, reservationId: String, reason: String): Boolean =
        database.withTransaction {
            val reservation = dao.reservation(scopeKey)
                ?.takeIf { it.reservationId == reservationId }
                ?: return@withTransaction false
            releaseReservationRows(reservation, failedPhotoId = null, reason = reason)
            true
        }

    suspend fun recoverInterruptedReservations(): Int = database.withTransaction {
        val reservations = dao.allReservations()
        val now = nowMs()
        reservations.forEach { reservation ->
            releaseReservationRows(reservation, failedPhotoId = null, reason = "startup_recovery")
            dao.scope(reservation.scopeKey)?.let { scope ->
                dao.upsertScope(scope.copy(lastRecoveryEpochMs = now, lastUsedAtEpochMs = now))
            }
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "RESERVATION_RECOVERED",
                "scope" to reservation.scopeKey,
                "ageMs" to (now - reservation.createdAtEpochMs).coerceAtLeast(0L).toString(),
            )
        }
        reservations.size
    }

    suspend fun previousHistory(scopeKey: String): HistoryPresentation? = database.withTransaction {
        val scope = dao.scope(scopeKey) ?: return@withTransaction null
        val cursor = scope.historyCursorSequence.takeIf { it > 0 } ?: scope.latestHistorySequence
        val previous = dao.previousHistory(scopeKey, cursor) ?: return@withTransaction null
        dao.upsertScope(scope.copy(historyCursorSequence = previous.sequence, lastUsedAtEpochMs = nowMs()))
        previous.toDomain()
    }

    suspend fun nextHistory(scopeKey: String): HistoryPresentation? = database.withTransaction {
        val scope = dao.scope(scopeKey) ?: return@withTransaction null
        if (scope.historyCursorSequence >= scope.latestHistorySequence) return@withTransaction null
        val next = dao.nextHistory(scopeKey, scope.historyCursorSequence) ?: return@withTransaction null
        dao.upsertScope(scope.copy(historyCursorSequence = next.sequence, lastUsedAtEpochMs = nowMs()))
        next.toDomain()
    }

    suspend fun newestHistory(scopeKey: String): HistoryPresentation? = database.withTransaction {
        val scope = dao.scope(scopeKey) ?: return@withTransaction null
        val newest = dao.newestHistory(scopeKey) ?: return@withTransaction null
        dao.upsertScope(scope.copy(historyCursorSequence = newest.sequence, lastUsedAtEpochMs = nowMs()))
        newest.toDomain()
    }

    suspend fun reset(scopeKey: String, clearHistory: Boolean) = database.withTransaction {
        resetScopeRows(scopeKey, clearHistory)
    }

    suspend fun resetPlaylist(playlistId: String, clearHistory: Boolean) = database.withTransaction {
        dao.scopesForPlaylist(playlistId).forEach { resetScopeRows(it.scopeKey, clearHistory) }
    }

    suspend fun resetAll(clearHistory: Boolean) = database.withTransaction {
        dao.scopesByRecentUse().forEach { resetScopeRows(it.scopeKey, clearHistory) }
    }

    suspend fun deletePlaylist(playlistId: String) = database.withTransaction {
        dao.scopesForPlaylist(playlistId).forEach { deleteScopeRows(it.scopeKey) }
    }

    suspend fun progress(scopeKey: String): ShuffleProgress = database.withTransaction {
        val scope = dao.scope(scopeKey) ?: return@withTransaction ShuffleProgress(scopeKey = scopeKey)
        val folders = dao.folderEntries(scopeKey, scope.activeFolderCycle)
        val reservation = dao.reservation(scopeKey)
        val currentFolder = reservation?.folderKey ?: folders.firstOrNull { it.state == FolderEntryState.PENDING.name }?.folderKey
        val photoCycle = currentFolder?.let { dao.photoCycle(scopeKey, it) }
        val photos = photoCycle?.let { dao.photoEntries(scopeKey, it.folderKey, it.activePhotoCycle) }.orEmpty()
        ShuffleProgress(
            scopeKey = scopeKey,
            folderCycle = scope.activeFolderCycle,
            folderResolved = folders.count { it.state in TERMINAL_FOLDER_STATES },
            folderTotal = folders.size,
            eligibleFolderCount = folders.count { it.state != FolderEntryState.REMOVED.name },
            foldersPresented = folders.count { it.state == FolderEntryState.PRESENTED.name },
            foldersPending = folders.count {
                it.state == FolderEntryState.PENDING.name || it.state == FolderEntryState.RESERVED.name
            },
            foldersSkipped = folders.count { it.state == FolderEntryState.SKIPPED.name },
            foldersRemoved = folders.count { it.state == FolderEntryState.REMOVED.name },
            currentFolderKey = currentFolder,
            photoCycle = photoCycle?.activePhotoCycle ?: 0L,
            photoResolved = photos.count { it.state in TERMINAL_PHOTO_STATES },
            photoTotal = photos.size,
            pendingPhotos = photos.count {
                it.state == PhotoEntryState.PENDING.name || it.state == PhotoEntryState.RESERVED.name
            },
            quarantinedPhotos = dao.quarantinedPhotoCount(scopeKey),
            activeReservationAgeMs = reservation?.let { (nowMs() - it.createdAtEpochMs).coerceAtLeast(0L) },
            lastCommitEpochMs = scope.lastCommitEpochMs,
            lastReconciliationEpochMs = scope.lastReconciliationEpochMs,
            lastRecoveryEpochMs = scope.lastRecoveryEpochMs,
        )
    }

    suspend fun cleanup(activeScopeKey: String?) = database.withTransaction {
        val now = nowMs()
        val staleBefore = now - INACTIVE_SCOPE_TTL_MS
        val scopes = dao.scopesByRecentUse()
        val keep = scopes.filter { it.scopeKey == activeScopeKey }.mapTo(mutableSetOf()) { it.scopeKey }
        scopes.filter { it.scopeKey != activeScopeKey && it.lastUsedAtEpochMs >= staleBefore }
            .take(MAX_INACTIVE_SCOPES)
            .forEach { keep += it.scopeKey }
        scopes.filterNot { it.scopeKey in keep }.forEach { deleteScopeRows(it.scopeKey) }
    }

    private suspend fun ensureScope(descriptor: ShuffleScopeDescriptor): ShuffleScopeEntity {
        val existing = dao.scope(descriptor.scopeKey)
        if (existing != null) {
            val touched = existing.copy(lastUsedAtEpochMs = nowMs())
            dao.upsertScope(touched)
            if (restoredScopesLogged.mark(existing.scopeKey)) {
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE, "SHUFFLE_SCOPE_RESTORED",
                    "scope" to existing.scopeKey,
                    "playlistToken" to diagnosticToken(existing.playlistId, "playlist"),
                    "folderCycle" to existing.activeFolderCycle.toString(),
                )
            }
            return touched
        }
        val created = ShuffleScopeEntity(
            scopeKey = descriptor.scopeKey,
            playlistId = descriptor.playlistId,
            poolRole = descriptor.poolRole,
            activeFolderCycle = 0L,
            lastPresentedFolderKey = null,
            lastUsedAtEpochMs = nowMs(),
            eligibilityRevision = descriptor.eligibilityRevision,
            reconciliationRevision = 0L,
            historyCursorSequence = 0L,
            latestHistorySequence = 0L,
            lastCommitEpochMs = null,
            lastReconciliationEpochMs = null,
            lastRecoveryEpochMs = null,
        )
        dao.upsertScope(created)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "SHUFFLE_SCOPE_CREATED",
            "scope" to created.scopeKey,
            "playlistToken" to diagnosticToken(descriptor.playlistId, "playlist"),
            "pool" to descriptor.poolRole,
        )
        return created
    }

    private suspend fun reconcileFolderCycle(
        scope: ShuffleScopeEntity,
        snapshot: ShuffleEligibilitySnapshot,
    ): ShuffleScopeEntity {
        if (scope.activeFolderCycle <= 0L || dao.folderEntries(scope.scopeKey, scope.activeFolderCycle).isEmpty()) {
            return startNextFolderCycle(scope, snapshot)
        }
        if (dao.unresolvedFolderCount(scope.scopeKey, scope.activeFolderCycle) == 0) {
            return startNextFolderCycle(scope, snapshot)
        }
        val existing = dao.folderEntries(scope.scopeKey, scope.activeFolderCycle)
        val eligible = snapshot.folderKeys
        val changed = existing.any { entry ->
            val sourceUnavailable = FolderKey.sourceIdOrNull(entry.folderKey) in snapshot.temporarilyUnavailableSourceIds
            (entry.folderKey !in eligible && !sourceUnavailable && entry.state != FolderEntryState.REMOVED.name) ||
                (entry.folderKey in eligible && entry.state == FolderEntryState.REMOVED.name) ||
                (sourceUnavailable && entry.state == FolderEntryState.PENDING.name && entry.skipReason != "source_deferred") ||
                (!sourceUnavailable && entry.skipReason == "source_deferred")
        } || eligible.any { key -> existing.none { it.folderKey == key } }
        if (!changed && scope.reconciliationRevision == snapshot.revision) return scope

        val terminal = mutableListOf<FolderShuffleEntryEntity>()
        val remaining = mutableListOf<FolderShuffleEntryEntity>()
        existing.forEach { entry ->
            val sourceUnavailable = FolderKey.sourceIdOrNull(entry.folderKey) in snapshot.temporarilyUnavailableSourceIds
            when {
                entry.state == FolderEntryState.PRESENTED.name || entry.state == FolderEntryState.SKIPPED.name ->
                    terminal += entry
                entry.folderKey in eligible || sourceUnavailable ->
                    remaining += entry.copy(
                        state = FolderEntryState.PENDING.name,
                        skipReason = if (sourceUnavailable) "source_deferred" else null,
                    )
                else -> terminal += entry.copy(
                    state = FolderEntryState.REMOVED.name,
                    skipReason = "no_longer_eligible",
                )
            }
        }
        val existingKeys = (terminal + remaining).mapTo(hashSetOf()) { it.folderKey }
        val newlyEligible = eligible.filterNot { it in existingKeys }
        val removedCount = terminal.count { it.state == FolderEntryState.REMOVED.name }
        val mergedKeys = ShuffleCycleGenerator.mergeRemaining(
            remaining.map { it.folderKey },
            newlyEligible,
            random,
        )
        val byKey = remaining.associateBy { it.folderKey }
        val rebuilt = buildList {
            terminal.forEach { add(it) }
            mergedKeys.forEach { key ->
                add(byKey[key] ?: FolderShuffleEntryEntity(
                    scope.scopeKey, scope.activeFolderCycle, 0, key,
                    FolderEntryState.PENDING.name, 0, null,
                ))
            }
        }.mapIndexed { position, entry -> entry.copy(position = position) }
        val deferredCount = rebuilt.count { entry ->
            entry.skipReason == "source_deferred" && existing.none {
                it.folderKey == entry.folderKey && it.skipReason == "source_deferred"
            }
        }
        val recoveredCount = existing.count { old ->
            old.skipReason == "source_deferred" && rebuilt.any {
                it.folderKey == old.folderKey && it.skipReason == null
            }
        }
        dao.deleteFolderCycle(scope.scopeKey, scope.activeFolderCycle)
        if (rebuilt.isNotEmpty()) dao.insertFolderEntries(rebuilt)
        val now = nowMs()
        val updated = scope.copy(
            eligibilityRevision = snapshot.revision,
            reconciliationRevision = snapshot.revision,
            lastReconciliationEpochMs = now,
            lastUsedAtEpochMs = now,
        )
        dao.upsertScope(updated)
        if (newlyEligible.isNotEmpty()) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_INSERTED",
            "scope" to scope.scopeKey, "count" to newlyEligible.size.toString(),
        )
        if (removedCount > 0) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_REMOVED",
            "scope" to scope.scopeKey, "count" to removedCount.toString(),
        )
        if (deferredCount > 0) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_DEFERRED",
            "scope" to scope.scopeKey, "count" to deferredCount.toString(),
        )
        if (recoveredCount > 0) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_DEFERRED_RELEASED",
            "scope" to scope.scopeKey, "count" to recoveredCount.toString(),
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "SHUFFLE_RECONCILED",
            "scope" to scope.scopeKey,
            "folders" to eligible.size.toString(),
        )
        return updated
    }

    private suspend fun startNextFolderCycle(
        scope: ShuffleScopeEntity,
        snapshot: ShuffleEligibilitySnapshot,
    ): ShuffleScopeEntity {
        val nextCycle = scope.activeFolderCycle + 1L
        val order = ShuffleCycleGenerator.generate(snapshot.folderKeys, scope.lastPresentedFolderKey, random)
        if (order.isEmpty()) {
            if (scope.activeFolderCycle > 0) dao.deleteFolderCycle(scope.scopeKey, scope.activeFolderCycle)
            val updated = scope.copy(
                activeFolderCycle = nextCycle,
                eligibilityRevision = snapshot.revision,
                reconciliationRevision = snapshot.revision,
                lastReconciliationEpochMs = nowMs(),
            )
            dao.upsertScope(updated)
            return updated
        }
        val rows = order.mapIndexed { index, key ->
            val sourceId = FolderKey.sourceIdOrNull(key)
            FolderShuffleEntryEntity(
                scopeKey = scope.scopeKey,
                folderCycle = nextCycle,
                position = index,
                folderKey = key,
                state = FolderEntryState.PENDING.name,
                retryCount = 0,
                skipReason = if (sourceId in snapshot.temporarilyUnavailableSourceIds) {
                    "source_deferred"
                } else null,
            )
        }
        dao.insertFolderEntries(rows)
        if (scope.activeFolderCycle > 0) dao.deleteFolderCycle(scope.scopeKey, scope.activeFolderCycle)
        val now = nowMs()
        val updated = scope.copy(
            activeFolderCycle = nextCycle,
            eligibilityRevision = snapshot.revision,
            reconciliationRevision = snapshot.revision,
            lastReconciliationEpochMs = now,
            lastUsedAtEpochMs = now,
        )
        dao.upsertScope(updated)
        val deferredAtStart = rows.count { it.skipReason == "source_deferred" }
        if (deferredAtStart > 0) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_DEFERRED",
            "scope" to scope.scopeKey,
            "count" to deferredAtStart.toString(),
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_CYCLE_STARTED",
            "scope" to scope.scopeKey,
            "cycle" to nextCycle.toString(),
            "folders" to rows.size.toString(),
        )
        return updated
    }

    private suspend fun reconcilePhotoCycle(
        scope: ShuffleScopeEntity,
        folder: EligibleFolder,
        revision: Long,
    ): FolderPhotoCycleEntity {
        val folderKey = folder.key.storageKey()
        val current = dao.photoCycle(scope.scopeKey, folderKey)
        if (current == null || dao.photoEntries(scope.scopeKey, folderKey, current.activePhotoCycle).isEmpty() ||
            dao.unresolvedPhotoCount(scope.scopeKey, folderKey, current.activePhotoCycle) == 0
        ) {
            return startNextPhotoCycle(scope, folder, current, revision)
        }
        val existing = dao.photoEntries(scope.scopeKey, folderKey, current.activePhotoCycle)
        val eligibleByKey = folder.members.associateBy { it.folderPhotoKey }
        val aliasToCanonical = buildMap<Long, String> {
            folder.members.forEach { member ->
                member.equivalentPhotoIds.forEach { photoId -> put(photoId, member.folderPhotoKey) }
            }
        }
        fun canonicalKey(entry: PhotoShuffleEntryEntity): String? = when {
            entry.folderPhotoKey in eligibleByKey -> entry.folderPhotoKey
            else -> aliasToCanonical[entry.photoId]
        }

        // A background SHA-256 result can replace a fallback stable-ID key. Treat all
        // equivalent database rows as aliases so a consumed fallback entry stays consumed
        // and cannot be inserted again under its new content-hash identity.
        val representedCanonicalKeys = existing.mapNotNullTo(linkedSetOf()) { canonicalKey(it) }
        val changed = existing.any { entry ->
            val canonical = canonicalKey(entry)
            canonical == null && entry.state != PhotoEntryState.REMOVED.name ||
                canonical != null && canonical != entry.folderPhotoKey ||
                canonical != null && entry.state == PhotoEntryState.REMOVED.name && entry.failureCount == 0
        } || eligibleByKey.keys.any { it !in representedCanonicalKeys }
        if (!changed && current.reconciliationRevision == revision) {
            dao.upsertPhotoCycle(current.copy(lastUsedAtEpochMs = nowMs()))
            return current
        }

        val canonicalGroups = linkedMapOf<String, MutableList<PhotoShuffleEntryEntity>>()
        val removed = mutableListOf<PhotoShuffleEntryEntity>()
        existing.forEach { entry ->
            val canonical = canonicalKey(entry)
            if (canonical == null) removed += entry.copy(state = PhotoEntryState.REMOVED.name)
            else canonicalGroups.getOrPut(canonical) { mutableListOf() } += entry
        }

        val terminal = mutableListOf<PhotoShuffleEntryEntity>()
        val remaining = mutableListOf<PhotoShuffleEntryEntity>()
        canonicalGroups.forEach { (key, aliases) ->
            val member = eligibleByKey.getValue(key)
            val consumed = aliases.any { it.state == PhotoEntryState.CONSUMED.name }
            val allQuarantined = aliases.all {
                it.state == PhotoEntryState.QUARANTINED.name || it.failureCount >= QUARANTINE_AFTER_FAILURES
            }
            val allFailedRemoved = aliases.all {
                it.state == PhotoEntryState.REMOVED.name && it.failureCount > 0
            }
            val failureCount = minOf(
                member.consecutiveFailureCount,
                aliases.minOfOrNull { it.failureCount } ?: member.consecutiveFailureCount,
            )
            val base = aliases.first().copy(
                folderPhotoKey = key,
                photoId = member.photoId,
                failureCount = failureCount,
            )
            when {
                consumed -> terminal += base.copy(state = PhotoEntryState.CONSUMED.name)
                allQuarantined -> terminal += base.copy(
                    state = PhotoEntryState.QUARANTINED.name,
                    failureCount = maxOf(failureCount, QUARANTINE_AFTER_FAILURES),
                )
                allFailedRemoved -> terminal += base.copy(state = PhotoEntryState.REMOVED.name)
                else -> remaining += base.copy(state = PhotoEntryState.PENDING.name)
            }
        }
        removed.forEach { terminal += it }

        val existingKeys = canonicalGroups.keys
        val newlyEligible = eligibleByKey.keys.filterNot { it in existingKeys }
        val mergedKeys = ShuffleCycleGenerator.mergeRemaining(
            remaining.map { it.folderPhotoKey },
            newlyEligible,
            random,
        )
        val byKey = remaining.associateBy { it.folderPhotoKey }
        val rebuilt = buildList {
            terminal.forEach { add(it) }
            mergedKeys.forEach { key ->
                val member = eligibleByKey.getValue(key)
                add(byKey[key] ?: PhotoShuffleEntryEntity(
                    scope.scopeKey, folderKey, current.activePhotoCycle, 0,
                    key, member.photoId, PhotoEntryState.PENDING.name,
                    member.consecutiveFailureCount,
                ))
            }
        }.distinctBy { entry ->
            // Removed rows without an eligible canonical key keep their historical key;
            // active canonical members remain unique by the v1.1 database invariant.
            entry.folderPhotoKey
        }.mapIndexed { position, entry -> entry.copy(position = position) }

        dao.deletePhotoCycleEntries(scope.scopeKey, folderKey, current.activePhotoCycle)
        if (rebuilt.isNotEmpty()) dao.insertPhotoEntries(rebuilt)
        val canonicalLastConsumed = existing.firstOrNull {
            it.folderPhotoKey == current.lastConsumedPhotoKey
        }?.let(::canonicalKey) ?: current.lastConsumedPhotoKey?.takeIf { it in eligibleByKey }
        val updated = current.copy(
            lastConsumedPhotoKey = canonicalLastConsumed,
            reconciliationRevision = revision,
            lastUsedAtEpochMs = nowMs(),
        ).also { dao.upsertPhotoCycle(it) }
        if (newlyEligible.isNotEmpty()) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PHOTO_INSERTED",
            "scope" to scope.scopeKey, "count" to newlyEligible.size.toString(),
        )
        if (removed.isNotEmpty()) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PHOTO_REMOVED",
            "scope" to scope.scopeKey, "count" to removed.size.toString(),
        )
        if (canonicalGroups.any { (key, rows) -> rows.size > 1 || rows.any { it.folderPhotoKey != key } }) {
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE, "PHOTO_IDENTITY_RECONCILED",
                "scope" to scope.scopeKey,
                "folderToken" to diagnosticToken(folderKey, "folder"),
            )
        }
        return updated
    }

    private suspend fun startNextPhotoCycle(
        scope: ShuffleScopeEntity,
        folder: EligibleFolder,
        previous: FolderPhotoCycleEntity?,
        revision: Long,
    ): FolderPhotoCycleEntity {
        val folderKey = folder.key.storageKey()
        val nextCycle = (previous?.activePhotoCycle ?: 0L) + 1L
        val byKey = folder.members.associateBy { it.folderPhotoKey }
        val order = ShuffleCycleGenerator.generate(byKey.keys, previous?.lastConsumedPhotoKey, random)
        val rows = order.mapIndexed { index, key ->
            PhotoShuffleEntryEntity(
                scopeKey = scope.scopeKey,
                folderKey = folderKey,
                photoCycle = nextCycle,
                position = index,
                folderPhotoKey = key,
                photoId = byKey.getValue(key).photoId,
                state = PhotoEntryState.PENDING.name,
                failureCount = byKey.getValue(key).consecutiveFailureCount,
            )
        }
        if (rows.isNotEmpty()) dao.insertPhotoEntries(rows)
        previous?.let { dao.deletePhotoCycleEntries(scope.scopeKey, folderKey, it.activePhotoCycle) }
        val cycle = FolderPhotoCycleEntity(
            scopeKey = scope.scopeKey,
            folderKey = folderKey,
            activePhotoCycle = nextCycle,
            lastConsumedPhotoKey = previous?.lastConsumedPhotoKey,
            reconciliationRevision = revision,
            lastUsedAtEpochMs = nowMs(),
        )
        dao.upsertPhotoCycle(cycle)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PHOTO_CYCLE_STARTED",
            "scope" to scope.scopeKey,
            "cycle" to nextCycle.toString(),
            "members" to rows.size.toString(),
        )
        return cycle
    }

    private suspend fun releaseReservationRows(
        reservation: ShuffleReservationEntity,
        failedPhotoId: Long?,
        reason: String,
    ) {
        val positions = decodeInts(reservation.photoPositionsJson)
        val entries = dao.photoEntries(reservation.scopeKey, reservation.folderKey, reservation.photoCycle)
            .filter { it.position in positions }
        entries.forEach { entry ->
            val failed = failedPhotoId == entry.photoId
            val failures = entry.failureCount + if (failed) 1 else 0
            val state = when {
                failed && failures >= QUARANTINE_AFTER_FAILURES -> PhotoEntryState.QUARANTINED.name
                failed -> PhotoEntryState.REMOVED.name
                entry.state == PhotoEntryState.RESERVED.name -> PhotoEntryState.PENDING.name
                else -> entry.state
            }
            dao.updatePhotoEntry(
                reservation.scopeKey, reservation.folderKey, reservation.photoCycle,
                entry.position, state, failures,
            )
        }
        val folder = dao.folderEntries(reservation.scopeKey, reservation.folderCycle)
            .firstOrNull { it.position == reservation.folderPosition }
        if (folder != null && folder.state == FolderEntryState.RESERVED.name) {
            dao.updateFolderEntry(
                reservation.scopeKey, reservation.folderCycle, reservation.folderPosition,
                FolderEntryState.PENDING.name, folder.retryCount, null,
            )
        }
        dao.deleteReservation(reservation.scopeKey)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PRESENTATION_RELEASED",
            "scope" to reservation.scopeKey,
            "reason" to reason.take(80),
        )
    }


    private suspend fun resetScopeRows(scopeKey: String, clearHistory: Boolean) {
        dao.reservation(scopeKey)?.let { releaseReservationRows(it, null, "reset") }
        dao.deleteAllFolderEntries(scopeKey)
        dao.deleteAllPhotoEntries(scopeKey)
        dao.deletePhotoCycles(scopeKey)
        if (clearHistory) dao.clearHistory(scopeKey)
        dao.scope(scopeKey)?.let { scope ->
            val newest = if (clearHistory) 0L else (dao.newestHistory(scopeKey)?.sequence ?: 0L)
            dao.upsertScope(
                scope.copy(
                    activeFolderCycle = 0L,
                    lastPresentedFolderKey = null,
                    reconciliationRevision = 0L,
                    historyCursorSequence = newest,
                    latestHistorySequence = newest,
                    lastUsedAtEpochMs = nowMs(),
                )
            )
        }
        if (clearHistory) diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "SHUFFLE_HISTORY_CLEARED",
            "scope" to scopeKey,
        )
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "SHUFFLE_RESET",
            "scope" to scopeKey,
            "historyCleared" to clearHistory.toString(),
        )
    }

    private suspend fun deleteScopeRows(scopeKey: String) {
        dao.cleanupReservations(scopeKey)
        dao.cleanupHistory(scopeKey)
        dao.cleanupPhotoEntries(scopeKey)
        dao.cleanupPhotoCycles(scopeKey)
        dao.cleanupFolderEntries(scopeKey)
        dao.deleteScope(scopeKey)
        restoredScopesLogged.forget(scopeKey)
    }

    private fun ShuffleReservationEntity.toDomain(): ReservedPresentation {
        val ids = decodeLongs(photoIdsJson)
        return ReservedPresentation(
            reservationId = reservationId,
            scopeKey = scopeKey,
            folderKey = folderKey,
            anchorPhotoId = ids.firstOrNull() ?: -1L,
            candidatePhotoIds = ids,
            createdAtEpochMs = createdAtEpochMs,
        )
    }

    private fun PresentationHistoryEntity.toDomain() = HistoryPresentation(
        presentationId = presentationId,
        scopeKey = scopeKey,
        sequence = sequence,
        folderKey = folderKey,
        presentationType = presentationType,
        photoIds = decodeLongs(photoIdsJson),
        committedAtEpochMs = committedAtEpochMs,
    )

    private fun encodeLongs(values: List<Long>): String = values.joinToString(prefix = "[", postfix = "]")
    private fun encodeInts(values: List<Int>): String = values.joinToString(prefix = "[", postfix = "]")
    private fun decodeLongs(value: String): List<Long> = decodeNumbers(value).mapNotNull(String::toLongOrNull)
    private fun decodeInts(value: String): List<Int> = decodeNumbers(value).mapNotNull(String::toIntOrNull)
    private fun decodeNumbers(value: String): List<String> = value.trim().removePrefix("[").removeSuffix("]")
        .split(',').map(String::trim).filter(String::isNotEmpty)

    companion object {
        const val HISTORY_LIMIT = 100
        const val MAX_INACTIVE_SCOPES = 32
        const val MAX_RESTORED_SCOPE_LOG_KEYS = 128
        const val MAX_COLLAGE_LOOKAHEAD = 12
        const val QUARANTINE_AFTER_FAILURES = 3
        const val MAX_FOLDER_RETRIES = 2
        const val INACTIVE_SCOPE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L

        private val TERMINAL_FOLDER_STATES = setOf(
            FolderEntryState.PRESENTED.name,
            FolderEntryState.SKIPPED.name,
            FolderEntryState.REMOVED.name,
        )
        private val TERMINAL_PHOTO_STATES = setOf(
            PhotoEntryState.CONSUMED.name,
            PhotoEntryState.REMOVED.name,
            PhotoEntryState.QUARANTINED.name,
        )
    }
}
