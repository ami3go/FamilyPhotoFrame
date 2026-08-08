package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.db.PhotoItemEntity

/**
 * Preserves user/runtime state when a scan refreshes metadata for the same logical path.
 *
 * Room's REPLACE conflict strategy deletes and reinserts a row. Without this merge every
 * rescan resets favourites, hidden state, playback history, decode suppression, cache
 * membership, and display-time EXIF. Content-dependent state is retained only while the
 * stable id is unchanged; replacing bytes at the same path gets a fresh decode/cache/EXIF
 * state while path-level curation remains intact.
 */
object ScanMergePolicy {
    fun merge(fresh: PhotoItemEntity, previous: PhotoItemEntity?): PhotoItemEntity {
        previous ?: return fresh
        val sameContent = fresh.stableId == previous.stableId
        return fresh.copy(
            id = previous.id,
            isHidden = previous.isHidden,
            lastShownAtEpochMs = previous.lastShownAtEpochMs,
            decodeFailureCount = if (sameContent) previous.decodeFailureCount else 0,
            lastDecodeFailureAtEpochMs = if (sameContent) previous.lastDecodeFailureAtEpochMs else null,
            isFavorite = previous.isFavorite,
            cacheKey = if (sameContent) previous.cacheKey else null,
            width = fresh.width ?: previous.width.takeIf { sameContent },
            height = fresh.height ?: previous.height.takeIf { sameContent },
            exifOrientation = if (fresh.exifOrientation != 0) {
                fresh.exifOrientation
            } else {
                previous.exifOrientation.takeIf { sameContent } ?: 0
            },
            dateTakenEpochMs = fresh.dateTakenEpochMs ?: previous.dateTakenEpochMs.takeIf { sameContent },
            caption = fresh.caption ?: previous.caption.takeIf { sameContent },
            gpsLat = fresh.gpsLat ?: previous.gpsLat.takeIf { sameContent },
            gpsLon = fresh.gpsLon ?: previous.gpsLon.takeIf { sameContent },
            exifScannedAtEpochMs = fresh.exifScannedAtEpochMs
                ?: previous.exifScannedAtEpochMs.takeIf { sameContent },
            contentSha256 = previous.contentSha256.takeIf { sameContent },
            contentHashScannedAtEpochMs = previous.contentHashScannedAtEpochMs.takeIf { sameContent },
        )
    }
}
