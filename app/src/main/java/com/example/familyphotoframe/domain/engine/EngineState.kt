package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.db.PhotoItemEntity
import com.example.familyphotoframe.data.source.BuiltInSourceIds

/** Slideshow engine states (spec §9.1). */
enum class EngineState {
    STARTING,
    INDEX_EMPTY,
    PLAYING_PRIMARY,    // PlayingPrimaryLive/Cached — a primary source (SAF or SMB)
    PLAYING_FALLBACK,   // PlayingFallback — fallback pool (bundled samples)
    PAUSED,
    SLEEPING,           // inside the configured quiet hours (spec §9.1, §20)
    DEGRADED_NO_CONTENT,
}

/**
 * A photo ready to display. [openToken] is a content URI string, an absolute file
 * path, an `smb://` URL, or a NAS-relative Synology token. When [needsCache] is true, the UI must resolve the
 * bytes through the MediaCache to a local file before decoding; the extra fields let
 * the cache rebuild the source [PhotoItem] without a DB round-trip.
 */
data class DisplayPhoto(
    val id: Long,
    val openToken: String,
    val isContentUri: Boolean,
    val fileName: String,
    val mimeType: String?,
    val folderName: String,
    val needsCache: Boolean = false,
    val stableId: String = "",
    val sourceId: String = "",
    val normalizedPath: String = "",
    val sizeBytes: Long = 0,
    val fileModifiedEpochMs: Long = 0,
    val width: Int? = null,
    val height: Int? = null,
    val exifOrientation: Int = 0,
    val lastShownAtEpochMs: Long? = null,
    /** EXIF date-taken, caption, and GPS (Phase 2 increment 5, spec §11 overlays). */
    val dateTakenEpochMs: Long? = null,
    val caption: String? = null,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    /** Curation state (spec §9.4); drives the on-screen star and favourites-only playback. */
    val isFavorite: Boolean = false,
)

/** Immutable snapshot the UI renders (unidirectional state — spec §3 architecture). */
data class EngineUiModel(
    val state: EngineState = EngineState.STARTING,
    val current: DisplayPhoto? = null,
    /** Changes for every selection attempt, even when the same photo is selected again. */
    val selectionGeneration: Long = 0L,
    val next: DisplayPhoto? = null,   // exposed so the UI can preload exactly one ahead (§10.2)
    val paused: Boolean = false,
    /** Progress through the current explicit-id playback cycle. */
    val cycleShown: Int = 0,
    val cycleTotal: Int = 0,
    /** Persisted folder-balanced progress and health counters. */
    val shuffleProgress: com.example.familyphotoframe.slideshow.shuffle.ShuffleProgress =
        com.example.familyphotoframe.slideshow.shuffle.ShuffleProgress(),
    /** Bounded same-folder candidates reserved with the current anchor. */
    val reservedCandidateIds: List<Long> = emptyList(),
    /** Exact committed member order when navigating persistent history. */
    val historyPhotoIds: List<Long> = emptyList(),
)


/** Shared row-to-domain mapping used by the engine and collage candidate resolver. */
fun PhotoItemEntity.toDisplayPhoto() = DisplayPhoto(
    id = id,
    openToken = openToken,
    isContentUri = openToken.startsWith("content://"),
    fileName = fileName,
    mimeType = mimeType,
    folderName = folderName,
    needsCache = BuiltInSourceIds.requiresMediaCache(sourceId),
    stableId = stableId,
    sourceId = sourceId,
    normalizedPath = normalizedPath,
    sizeBytes = sizeBytes,
    fileModifiedEpochMs = fileModifiedEpochMs,
    width = width,
    height = height,
    exifOrientation = exifOrientation,
    lastShownAtEpochMs = lastShownAtEpochMs,
    dateTakenEpochMs = dateTakenEpochMs,
    caption = caption,
    gpsLat = gpsLat,
    gpsLon = gpsLon,
    isFavorite = isFavorite,
)
