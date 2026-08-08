package com.example.familyphotoframe.slideshow.shuffle

import com.example.familyphotoframe.data.index.CanonicalPhotoPath

/** Stable key for the indexed directory that directly contains a photo. */
data class FolderKey(
    val sourceId: String,
    val canonicalRelativeDirectory: String,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(canonicalRelativeDirectory.isNotBlank()) { "directory must not be blank" }
    }

    fun storageKey(): String = "$sourceId$SEPARATOR$canonicalRelativeDirectory"

    companion object {
        const val ROOT_DIRECTORY = CanonicalPhotoPath.ROOT_DIRECTORY
        private const val SEPARATOR = '\u001f'

        fun fromIndexedPath(sourceId: String, normalizedPath: String): FolderKey =
            fromIndexedDirectory(sourceId, CanonicalPhotoPath.directDirectory(normalizedPath))

        fun fromIndexedDirectory(sourceId: String, canonicalDirectory: String): FolderKey =
            FolderKey(sourceId.trim(), canonicalDirectory.ifBlank { ROOT_DIRECTORY })


        fun parse(storageKey: String): FolderKey {
            val split = storageKey.indexOf(SEPARATOR)
            require(split > 0 && split < storageKey.lastIndex) { "Invalid folder key" }
            return FolderKey(storageKey.substring(0, split), storageKey.substring(split + 1))
        }

        /**
         * Source id of a persisted folder key, or null when the row cannot be parsed.
         *
         * Reconciliation and selection must survive a malformed or hand-edited row
         * rather than aborting the whole scope, so an unparseable key is treated as
         * "belongs to no known source" instead of throwing.
         */
        fun sourceIdOrNull(storageKey: String): String? =
            runCatching { parse(storageKey).sourceId }.getOrNull()
    }
}

/** Identity of one photo member inside one specific folder. */
data class FolderPhotoKey(
    val folderKey: FolderKey,
    val contentIdentity: String,
) {
    init { require(contentIdentity.isNotBlank()) }
    fun storageKey(): String = "${folderKey.storageKey()}\u001e$contentIdentity"
}

enum class FolderEntryState { PENDING, RESERVED, PRESENTED, SKIPPED, REMOVED }
enum class PhotoEntryState { PENDING, RESERVED, CONSUMED, REMOVED, QUARANTINED }

data class EligiblePhotoMember(
    val photoId: Long,
    val folderKey: FolderKey,
    val folderPhotoKey: String,
    val sourceId: String,
    val canonicalRelativePath: String,
    val contentIdentity: String,
    val width: Int? = null,
    val height: Int? = null,
    val exifOrientation: Int = 0,
    val consecutiveFailureCount: Int = 0,
    /** All database rows collapsed into this same-folder content member. */
    val equivalentPhotoIds: Set<Long> = setOf(photoId),
)

data class EligibleFolder(
    val key: FolderKey,
    val members: List<EligiblePhotoMember>,
    /** Canonical eligible count from the lightweight folder query. */
    val memberCount: Int = members.size,
)

data class ShuffleEligibilitySnapshot(
    val revision: Long,
    val folders: List<EligibleFolder>,
    /** Existing queue entries from these sources are deferred, not removed. */
    val temporarilyUnavailableSourceIds: Set<String> = emptySet(),
    /** Sources whose bounded source-level retry policy is exhausted for this cycle. */
    val exhaustedUnavailableSourceIds: Set<String> = emptySet(),
) {
    val folderKeys: Set<String> get() = folders.mapTo(linkedSetOf()) { it.key.storageKey() }
    fun folder(storageKey: String): EligibleFolder? = folders.firstOrNull { it.key.storageKey() == storageKey }
}

data class ShuffleScopeDescriptor(
    val scopeKey: String,
    val playlistId: String,
    val eligibilityRevision: Long,
    val poolRole: String,
)

data class ReservedPresentation(
    val reservationId: String,
    val scopeKey: String,
    val folderKey: String,
    val anchorPhotoId: Long,
    /** Anchor first, followed by bounded same-folder lookahead entries. */
    val candidatePhotoIds: List<Long>,
    val createdAtEpochMs: Long,
)

data class HistoryPresentation(
    val presentationId: String,
    val scopeKey: String,
    val sequence: Long,
    val folderKey: String,
    val presentationType: String,
    val photoIds: List<Long>,
    val committedAtEpochMs: Long,
)

data class ShuffleProgress(
    val scopeKey: String = "",
    val folderCycle: Long = 0L,
    val folderResolved: Int = 0,
    val folderTotal: Int = 0,
    val eligibleFolderCount: Int = 0,
    val foldersPresented: Int = 0,
    val foldersPending: Int = 0,
    val foldersSkipped: Int = 0,
    val foldersRemoved: Int = 0,
    val currentFolderKey: String? = null,
    val photoCycle: Long = 0L,
    val photoResolved: Int = 0,
    val photoTotal: Int = 0,
    val pendingPhotos: Int = 0,
    val quarantinedPhotos: Int = 0,
    val unavailableSourceCount: Int = 0,
    val activeReservationAgeMs: Long? = null,
    val lastCommitEpochMs: Long? = null,
    val lastReconciliationEpochMs: Long? = null,
    val lastRecoveryEpochMs: Long? = null,
)

sealed interface ShuffleAdvanceResult {
    data class Reserved(val presentation: ReservedPresentation) : ShuffleAdvanceResult
    data object Empty : ShuffleAdvanceResult
}
