package com.example.familyphotoframe.slideshow.shuffle

import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.db.FolderSelectionSql
import com.example.familyphotoframe.data.db.ShufflePhotoRow
import java.util.concurrent.atomic.AtomicLong

/** One canonical eligibility provider shared by cycle creation and reconciliation. */
class ShuffleEligibilityProvider(
    private val photoDao: PhotoDao,
    private val allowHeif: Boolean = true,
) {
    private data class CachedFolderSnapshot(
        val generation: Long,
        val query: Query,
        val snapshot: ShuffleEligibilitySnapshot,
    )

    private val cacheGeneration = AtomicLong(0L)
    @Volatile private var cachedFolderSnapshot: CachedFolderSnapshot? = null

    data class Query(
        val revision: Long,
        val sourceIds: List<String>,
        val maxFailures: Int,
        val favoritesOnly: Boolean,
        val cachedOnly: Boolean,
        val selectedFolders: List<String>,
        val temporarilyUnavailableSourceIds: Set<String> = emptySet(),
        val exhaustedUnavailableSourceIds: Set<String> = emptySet(),
    )

    /**
     * Load folder metadata only. This keeps selection memory proportional to the folder
     * count instead of the total photo count, as required by FPF-FEAT-SHUFFLE-002 §32.
     */
    suspend fun folderSnapshot(query: Query): ShuffleEligibilitySnapshot {
        val generation = cacheGeneration.get()
        cachedFolderSnapshot?.takeIf { it.generation == generation && it.query == query }
            ?.let { return it.snapshot }
        if (query.sourceIds.isEmpty()) {
            return ShuffleEligibilitySnapshot(
                query.revision,
                emptyList(),
                query.temporarilyUnavailableSourceIds,
                query.exhaustedUnavailableSourceIds,
            ).also { snapshot -> cacheIfCurrent(generation, query, snapshot) }
        }
        val allFolders = if (query.selectedFolders.isEmpty()) 1 else 0
        val folderSelection = FolderSelectionSql.encode(query.selectedFolders)
        val folders = photoDao.shuffleEligibleFolders(
            sourceIds = query.sourceIds,
            maxFailures = query.maxFailures,
            favoritesOnly = if (query.favoritesOnly) 1 else 0,
            cachedOnly = if (query.cachedOnly) 1 else 0,
            allowHeif = if (allowHeif) 1 else 0,
            allFolders = allFolders,
            folderSelection = folderSelection,
        ).map { row ->
            EligibleFolder(
                key = FolderKey.fromIndexedDirectory(row.sourceId, row.canonicalDirectory),
                members = emptyList(),
                memberCount = row.memberCount,
            )
        }
        return ShuffleEligibilitySnapshot(
            revision = query.revision,
            folders = folders,
            temporarilyUnavailableSourceIds = query.temporarilyUnavailableSourceIds,
            exhaustedUnavailableSourceIds = query.exhaustedUnavailableSourceIds,
        ).also { snapshot -> cacheIfCurrent(generation, query, snapshot) }
    }

    /** Index/configuration changes invalidate the expensive all-folder GROUP BY query. */
    fun invalidate() {
        cacheGeneration.incrementAndGet()
        cachedFolderSnapshot = null
    }

    private fun cacheIfCurrent(
        generation: Long,
        query: Query,
        snapshot: ShuffleEligibilitySnapshot,
    ) {
        if (cacheGeneration.get() == generation) {
            cachedFolderSnapshot = CachedFolderSnapshot(generation, query, snapshot)
        }
    }

    /** Load and canonicalize members for the one folder currently being resolved. */
    suspend fun folderMembers(query: Query, folderKey: FolderKey): EligibleFolder? {
        if (folderKey.sourceId !in query.sourceIds) return null
        val rows = photoDao.shuffleEligibilityRowsForFolder(
            sourceId = folderKey.sourceId,
            canonicalDirectory = folderKey.canonicalRelativeDirectory,
            maxFailures = query.maxFailures,
            favoritesOnly = if (query.favoritesOnly) 1 else 0,
            cachedOnly = if (query.cachedOnly) 1 else 0,
            allowHeif = if (allowHeif) 1 else 0,
        )
        if (rows.isEmpty()) return null
        return EligibleFolder(folderKey, collapseRows(rows), rows.size)
    }

    /**
     * Full snapshot retained for focused tests and administrative diagnostics only.
     * Playback uses [folderSnapshot] + [folderMembers] and never calls this method.
     */
    suspend fun snapshot(
        revision: Long,
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Boolean,
        cachedOnly: Boolean,
        selectedFolders: List<String>,
        temporarilyUnavailableSourceIds: Set<String> = emptySet(),
        exhaustedUnavailableSourceIds: Set<String> = emptySet(),
    ): ShuffleEligibilitySnapshot {
        val query = Query(
            revision, sourceIds, maxFailures, favoritesOnly, cachedOnly, selectedFolders,
            temporarilyUnavailableSourceIds, exhaustedUnavailableSourceIds,
        )
        val folders = folderSnapshot(query).folders.mapNotNull { folderMembers(query, it.key) }
        return ShuffleEligibilitySnapshot(
            revision,
            folders,
            temporarilyUnavailableSourceIds,
            exhaustedUnavailableSourceIds,
        )
    }

    private fun collapseRows(rows: List<ShufflePhotoRow>): List<EligiblePhotoMember> {
        val grouped = linkedMapOf<String, EligiblePhotoMember>()
        rows.forEach { row ->
            val folder = FolderKey.fromIndexedDirectory(row.sourceId, row.canonicalDirectory)
            // Content SHA-256 provides exact in-folder duplicate suppression once the
            // asynchronous hash index has completed. Stable identity remains the task's
            // permitted fallback and never blocks slideshow rendering.
            val identity = row.contentSha256?.takeIf { it.length == 64 } ?: row.stableId.ifBlank {
                "${row.sourceId}|${row.normalizedPath}|${row.sizeBytes}|${row.fileModifiedEpochMs}"
            }
            val memberKey = FolderPhotoKey(folder, identity).storageKey()
            val candidate = EligiblePhotoMember(
                photoId = row.id,
                folderKey = folder,
                folderPhotoKey = memberKey,
                sourceId = row.sourceId,
                canonicalRelativePath = row.normalizedPath,
                contentIdentity = identity,
                width = row.width,
                height = row.height,
                exifOrientation = row.exifOrientation,
                consecutiveFailureCount = row.decodeFailureCount,
                equivalentPhotoIds = setOf(row.id),
            )
            val existing = grouped[memberKey]
            grouped[memberKey] = when {
                existing == null -> candidate
                row.decodeFailureCount < existing.consecutiveFailureCount -> candidate.copy(
                    equivalentPhotoIds = existing.equivalentPhotoIds + row.id,
                )
                else -> existing.copy(
                    equivalentPhotoIds = existing.equivalentPhotoIds + row.id,
                )
            }
        }
        return grouped.values.toList()
    }
}
