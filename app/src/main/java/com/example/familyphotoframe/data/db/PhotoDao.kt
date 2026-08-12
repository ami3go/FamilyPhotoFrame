package com.example.familyphotoframe.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Photo index queries.
 *
 * Every query in this interface is prepared against a real SQLite engine by
 * `scripts/verify-sql.py`, since Room's own annotation processor cannot run in the
 * offline build environment.
 *
 * Filter flags ([favoritesOnly], [cachedOnly], [allFolders]) are ints rather than
 * booleans so the `:flag = 0 OR ...` short-circuit reads unambiguously in SQL.
 */
@Dao
interface PhotoDao {

    /**
     * Batch insert (spec §5.2 "insert in batches"). REPLACE resolves on the unique
     * (sourceId, normalizedPath) index so a re-seen file updates in place. Runs in a
     * Room-managed transaction off the main thread.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(items: List<PhotoItemEntity>)

    /**
     * Existing rows for one scan batch. The indexer merges these into fresh metadata
     * before REPLACE so rescans preserve curation, playback history, verified cache
     * membership, decode suppression, and display-time EXIF backfill.
     */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId = :sourceId AND normalizedPath IN (:normalizedPaths)
        """
    )
    suspend fun rowsForScanMerge(
        sourceId: String,
        normalizedPaths: List<String>,
    ): List<PhotoItemEntity>

    /** Lightweight projection for [localThumbnailRebuildCandidates]. */
    data class LocalThumbnailRebuildCandidate(val stableId: String, val openToken: String)

    /**
     * Local-source (SAF/fallback/local-upload) photos eligible for the on-disk thumbnail
     * cache, paged so a rebuild over a large library never loads the whole index into
     * memory at once. `sourceId NOT IN (...)` mirrors
     * [com.example.familyphotoframe.data.source.BuiltInSourceIds.requiresMediaCache]'s
     * remote-source list — those are out of scope for this cache (see
     * docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md §3).
     */
    @Query(
        """
        SELECT stableId, openToken FROM photos
        WHERE sourceId NOT IN ('smb', 'synology', 'webdav') AND isHidden = 0
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun localThumbnailRebuildCandidates(limit: Int, offset: Int): List<LocalThumbnailRebuildCandidate>

    /** Lightweight projection for [onThisDayCandidates]. */
    data class OnThisDayCandidate(
        val id: Long,
        val stableId: String,
        val folderName: String,
        val dateTakenEpochMs: Long,
        val isFavorite: Boolean,
        val openToken: String,
    )

    /**
     * Exact-day match (any year) for the "on this day" memory feature — see
     * docs/FPF-FEAT-ON-THIS-DAY-001.md §0.1 (no day-window tolerance by design).
     * [monthDay] is "MM-dd" in the device's local calendar; the caller computes it fresh
     * at query time so a device asleep across midnight is never served a stale day.
     * Grouping by year and picking one photo per year happens in
     * `domain.onthisday.OnThisDaySelection`, not here — SQLite has no per-group LIMIT,
     * so this only applies an overall safety cap via [limit].
     */
    @Query(
        """
        SELECT id, stableId, folderName, dateTakenEpochMs, isFavorite, openToken FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND dateTakenEpochMs IS NOT NULL
          AND strftime('%m-%d', dateTakenEpochMs / 1000, 'unixepoch', 'localtime') = :monthDay
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY isFavorite DESC, id ASC
        LIMIT :limit
        """
    )
    suspend fun onThisDayCandidates(
        sourceIds: List<String>,
        monthDay: String,
        maxFailures: Int,
        allowHeif: Int,
        limit: Int,
    ): List<OnThisDayCandidate>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM photos WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM photos
        WHERE sourceId = :sourceId AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        """
    )
    fun displayableCountFlow(sourceId: String, maxFailures: Int, allowHeif: Int): Flow<Int>

    @Query("DELETE FROM photos WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    /** Reconcile: drop rows for this source that were not refreshed by the latest scan. */
    @Query("DELETE FROM photos WHERE sourceId = :sourceId AND indexedAtEpochMs < :scanStartedEpochMs")
    suspend fun deleteStaleForSource(sourceId: String, scanStartedEpochMs: Long)

    /**
     * Least-recent-random candidate window (spec §9.6 `least_recent_random`):
     * fetch the N least-recently-shown displayable rows; the strategy then picks
     * randomly among them. NULL lastShownAt sorts first, so unseen photos lead.
     */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId = :sourceId AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY lastShownAtEpochMs ASC
        LIMIT :window
        """
    )
    suspend fun leastRecentWindow(
        sourceId: String,
        maxFailures: Int,
        window: Int,
        allowHeif: Int,
    ): List<PhotoItemEntity>

    /** Eligible folders for balanced random rotation, after all playback filters. */
    @Query(
        """
        SELECT DISTINCT folderName FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
          AND (:allFolders = 1 OR (sourceId || char(31) || canonicalDirectory) IN (:folders) OR folderName IN (:folders))
        ORDER BY folderName COLLATE NOCASE ASC
        """
    )
    suspend fun displayableFolderNames(
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        allFolders: Int,
        folders: List<String>,
    ): List<String>

    /** Least-recent photo window inside one folder selected by the outer folder cycle. */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId IN (:sourceIds) AND folderName = :folderName
          AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY lastShownAtEpochMs ASC, id ASC
        LIMIT :window
        """
    )
    suspend fun leastRecentWindowInFolder(
        sourceIds: List<String>,
        folderName: String,
        maxFailures: Int,
        window: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
    ): List<PhotoItemEntity>

    /**
     * Folder-only eligibility for the persistent coordinator. This is intentionally
     * O(number of folders): photo rows for non-current folders are never materialized.
     */
    @Query(
        """
        SELECT sourceId, canonicalDirectory, COUNT(*) AS memberCount
        FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
          AND (:allFolders = 1 OR (sourceId || char(31) || canonicalDirectory) IN (:folders) OR folderName IN (:folders))
        GROUP BY sourceId, canonicalDirectory
        ORDER BY sourceId ASC, canonicalDirectory COLLATE NOCASE ASC
        """
    )
    suspend fun shuffleEligibleFolders(
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        allFolders: Int,
        folders: List<String>,
    ): List<ShuffleFolderRow>

    /** Canonical member rows for one selected direct folder only. */
    @Query(
        """
        SELECT id, stableId, sourceId, normalizedPath, canonicalDirectory, contentSha256,
               sizeBytes, fileModifiedEpochMs, width, height, exifOrientation, decodeFailureCount
        FROM photos
        WHERE sourceId = :sourceId AND canonicalDirectory = :canonicalDirectory
          AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY normalizedPath ASC, id ASC
        """
    )
    suspend fun shuffleEligibilityRowsForFolder(
        sourceId: String,
        canonicalDirectory: String,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
    ): List<ShufflePhotoRow>

    /**
     * Whole displayable id pool for a set of sources, in stable insertion order.
     *
     * Ids only — a no-repeat cycle needs to know the *whole* pool, and materializing
     * every full row to do so would defeat the point of never enumerating storage
     * (spec §5.1). A Long per photo is affordable even for a large library; the row
     * itself is fetched one at a time by [byId] when the photo is actually shown.
     */
    @Query(
        """
        SELECT id FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
          AND (:allFolders = 1 OR (sourceId || char(31) || canonicalDirectory) IN (:folders) OR folderName IN (:folders))
        ORDER BY id ASC
        """
    )
    suspend fun displayableIds(
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        allFolders: Int,
        folders: List<String>,
    ): List<Long>

    /**
     * Displayable ids newest-first by EXIF date-taken. Photos with no date-taken sort
     * last (never interleaved into the dated run) and fall back to file-modified time
     * among themselves, so an undated photo is still shown in a predictable place
     * rather than jumping around between rescans.
     */
    @Query(
        """
        SELECT id FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
          AND (:allFolders = 1 OR (sourceId || char(31) || canonicalDirectory) IN (:folders) OR folderName IN (:folders))
        ORDER BY (dateTakenEpochMs IS NULL) ASC, dateTakenEpochMs DESC, fileModifiedEpochMs DESC, id ASC
        """
    )
    suspend fun displayableIdsByDateTakenDesc(
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        allFolders: Int,
        folders: List<String>,
    ): List<Long>

    /** Displayable ids oldest-first by EXIF date-taken; undated photos again sort last. */
    @Query(
        """
        SELECT id FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
          AND (:allFolders = 1 OR (sourceId || char(31) || canonicalDirectory) IN (:folders) OR folderName IN (:folders))
        ORDER BY (dateTakenEpochMs IS NULL) ASC, dateTakenEpochMs ASC, fileModifiedEpochMs ASC, id ASC
        """
    )
    suspend fun displayableIdsByDateTakenAsc(
        sourceIds: List<String>,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        allFolders: Int,
        folders: List<String>,
    ): List<Long>

    /** How many rows of these sources already have cached bytes (spec §9.3 gate). */
    @Query(
        """
        SELECT COUNT(*) FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND cacheKey IS NOT NULL
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        """
    )
    suspend fun cachedCount(sourceIds: List<String>, maxFailures: Int, allowHeif: Int): Int

    /**
     * Distinct folder names for a source, with a photo count each — the "albums" a user
     * picks from (spec §9.4). Folder names come from the index, so this costs one
     * grouped query rather than another walk of the NAS.
     */
    @Query(
        """
        SELECT sourceId, canonicalDirectory, MIN(folderName) AS name, COUNT(*) AS photoCount
        FROM photos
        WHERE sourceId IN (:sourceIds) AND isHidden = 0
        GROUP BY sourceId, canonicalDirectory
        ORDER BY sourceId COLLATE NOCASE ASC, canonicalDirectory COLLATE NOCASE ASC
        """
    )
    suspend fun folderSummaries(sourceIds: List<String>): List<FolderSummary>

    /**
     * Bounded same-folder pool for portrait-collage planning. Orientation is checked
     * after decode because remote rows may not have EXIF dimensions yet.
     */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId = :sourceId AND folderName = :folderName
          AND id != :anchorId AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY
          CASE WHEN :anchorTime > 0
            THEN ABS(COALESCE(dateTakenEpochMs, fileModifiedEpochMs) - :anchorTime)
            ELSE 0 END ASC,
          (lastShownAtEpochMs IS NOT NULL) ASC,
          lastShownAtEpochMs ASC,
          id ASC
        LIMIT :limit
        """
    )
    suspend fun collageCandidates(
        sourceId: String,
        folderName: String,
        anchorId: Long,
        anchorTime: Long,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        limit: Int,
    ): List<PhotoItemEntity>

    /**
     * Bounded source-wide pool used only after same-folder candidates have been loaded.
     * Folder locality is ranked in the pure optimizer; this query deliberately does not
     * reorder by orientation so deterministic reservation/query order remains available
     * as the final tie-breaker.
     */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId = :sourceId
          AND id != :anchorId AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY
          CASE WHEN :anchorTime > 0
            THEN ABS(COALESCE(dateTakenEpochMs, fileModifiedEpochMs) - :anchorTime)
            ELSE 0 END ASC,
          (lastShownAtEpochMs IS NOT NULL) ASC,
          lastShownAtEpochMs ASC,
          id ASC
        LIMIT :limit
        """
    )
    suspend fun collageCandidatesAcrossSource(
        sourceId: String,
        anchorId: Long,
        anchorTime: Long,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        limit: Int,
    ): List<PhotoItemEntity>

    /** Exact direct-folder preview candidates. This query never touches shuffle state. */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId = :sourceId AND canonicalDirectory = :canonicalDirectory
          AND isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:cachedOnly = 0 OR cacheKey IS NOT NULL)
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        ORDER BY lastShownAtEpochMs ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun previewFolderCandidates(
        sourceId: String,
        canonicalDirectory: String,
        maxFailures: Int,
        favoritesOnly: Int,
        cachedOnly: Int,
        allowHeif: Int,
        limit: Int,
    ): List<PhotoItemEntity>

    /** Small bounded batch for asynchronous content-hash indexing. */
    @Query(
        """
        SELECT * FROM photos
        WHERE sourceId = :sourceId AND contentSha256 IS NULL AND isHidden = 0
        ORDER BY contentHashScannedAtEpochMs ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun photosNeedingContentHash(sourceId: String, limit: Int): List<PhotoItemEntity>

    @Query(
        """
        UPDATE photos SET contentSha256 = :sha256,
            contentHashScannedAtEpochMs = :scannedAt
        WHERE id = :id
        """
    )
    suspend fun updateContentHash(id: Long, sha256: String, scannedAt: Long)

    @Query("UPDATE photos SET contentHashScannedAtEpochMs = :attemptedAt WHERE id = :id")
    suspend fun markContentHashAttempt(id: Long, attemptedAt: Long)

    // ---- curation (spec §9.4) --------------------------------------------------

    @Query("UPDATE photos SET isFavorite = :value WHERE id = :id")
    suspend fun setFavorite(id: Long, value: Boolean)

    @Query("UPDATE photos SET isFavorite = :value WHERE id IN (:ids)")
    suspend fun setFavorites(ids: List<Long>, value: Boolean)

    @Query("UPDATE photos SET isHidden = :value WHERE id = :id")
    suspend fun setHidden(id: Long, value: Boolean)

    @Query("UPDATE photos SET isHidden = :value WHERE id IN (:ids)")
    suspend fun setHiddenBatch(ids: List<Long>, value: Boolean)

    @Query("SELECT COUNT(*) FROM photos WHERE sourceId IN (:sourceIds) AND isFavorite = 1 AND isHidden = 0")
    suspend fun favoriteCount(sourceIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM photos WHERE isHidden = 1")
    suspend fun hiddenCount(): Int

    @Query("SELECT COUNT(*) FROM photos WHERE isFavorite = 1 AND isHidden = 0")
    suspend fun favoriteCountAll(): Int

    @Query(
        """
        SELECT COUNT(*) FROM photos
        WHERE isHidden = 0 AND decodeFailureCount < :maxFailures
          AND (:allowHeif = 1 OR (
            lower(fileName) NOT LIKE '%.heic' AND lower(fileName) NOT LIKE '%.heif'
            AND lower(COALESCE(mimeType, '')) NOT IN
              ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
          ))
        """
    )
    suspend fun eligibleCount(maxFailures: Int, allowHeif: Int): Int

    @Query(
        """
        SELECT COUNT(*) FROM photos
        WHERE decodeFailureCount >= :maxFailures OR (:allowHeif = 0 AND (
          lower(fileName) LIKE '%.heic' OR lower(fileName) LIKE '%.heif'
          OR lower(COALESCE(mimeType, '')) IN
            ('image/heic','image/heif','image/heic-sequence','image/heif-sequence')
        ))
        """
    )
    suspend fun failedOrUnsupportedCount(maxFailures: Int, allowHeif: Int): Int

    /** Un-hide everything, so a mis-tapped "hide" is always recoverable (spec §9.4). */
    @Query("UPDATE photos SET isHidden = 0")
    suspend fun unhideAll()

    // ---- cache bookkeeping -----------------------------------------------------

    /**
     * Record that this photo's bytes are (or are no longer) in `MediaCache`. Writing the
     * long-dormant `cacheKey` column here is what lets the stale-cache playback query
     * above use an index instead of joining `cache_index` with a thousand-element
     * `IN (...)` list.
     */
    @Query("UPDATE photos SET cacheKey = :cacheKey WHERE stableId = :stableId")
    suspend fun setCacheKey(stableId: String, cacheKey: String?)

    @Query("UPDATE photos SET cacheKey = NULL WHERE cacheKey = :cacheKey")
    suspend fun clearCacheKey(cacheKey: String)

    @Query("UPDATE photos SET cacheKey = NULL")
    suspend fun clearAllCacheKeys()

    /** Un-suppress a source's rows after it recovers (spec §9.5 recovery). */
    @Query("UPDATE photos SET decodeFailureCount = 0, lastDecodeFailureAtEpochMs = NULL WHERE sourceId = :sourceId")
    suspend fun clearSuppression(sourceId: String)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun byId(id: Long): PhotoItemEntity?

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<PhotoItemEntity>

    @Query("UPDATE photos SET lastShownAtEpochMs = :ts WHERE id = :id")
    suspend fun markShown(id: Long, ts: Long)

    /** A successful render clears transient failures accumulated by previous attempts. */
    @Query("UPDATE photos SET decodeFailureCount = 0, lastDecodeFailureAtEpochMs = NULL WHERE id = :id")
    suspend fun clearDecodeFailure(id: Long)

    /** Permanently suppress a format unsupported by this device without retrying it N times. */
    @Query(
        "UPDATE photos SET decodeFailureCount = :failureCount, " +
            "lastDecodeFailureAtEpochMs = :ts WHERE id = :id"
    )
    suspend fun suppressDecode(id: Long, ts: Long, failureCount: Int)

    /**
     * Persist EXIF read after indexing (Phase 2 increment 8 display-time backfill).
     * [scannedAt] is always written, even when every value is null, so a photo with no
     * EXIF block is not reopened on every showing.
     */
    @Query(
        """
        UPDATE photos SET
            width = :width, height = :height, exifOrientation = :orientation,
            dateTakenEpochMs = :dateTaken, caption = :caption,
            gpsLat = :gpsLat, gpsLon = :gpsLon, exifScannedAtEpochMs = :scannedAt
        WHERE id = :id
        """
    )
    suspend fun updateExif(
        id: Long,
        width: Int?,
        height: Int?,
        orientation: Int,
        dateTaken: Long?,
        caption: String?,
        gpsLat: Double?,
        gpsLon: Double?,
        scannedAt: Long,
    )

    @Query(
        """
        UPDATE photos
        SET decodeFailureCount = decodeFailureCount + 1, lastDecodeFailureAtEpochMs = :ts
        WHERE id = :id
        """
    )
    suspend fun recordDecodeFailure(id: Long, ts: Long)
}
