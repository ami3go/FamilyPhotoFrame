package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fills in EXIF for photos that were indexed without it (Phase 2 increment 8).
 *
 * Remote scans skip EXIF because each read is a network round-trip
 * (see [ExifScanPolicy]); this reads it instead at the moment a photo is displayed,
 * so the cost is paid once per photo the user actually sees rather than once per photo
 * in the library, and is spread across the slideshow interval instead of landing in one
 * blocking scan.
 *
 * Everything here is best-effort and off the slideshow's critical path:
 *  - it never throws to the caller;
 *  - each read is bounded by [timeoutMs];
 *  - a row is stamped as attempted even when it has no EXIF at all, so photos without
 *    an EXIF block are not reopened on every showing;
 *  - one read runs at a time ([gate]), so a burst of D-pad "next" presses cannot pile up
 *    concurrent network opens.
 *
 * It deliberately does NOT live in `SlideshowEngine`, which documents that it performs
 * no disk or network I/O.
 */
class ExifBackfiller(
    private val dao: PhotoDao,
    private val diagnostics: DiagnosticsLog,
    private val timeoutMs: Long = 4_000,
) {
    private val gate = Mutex()

    /**
     * Reads and persists EXIF for [photoId] if it has never been attempted.
     *
     * @param resolveSource supplies the source that owns this photo, built on demand by
     *   the caller (sources are constructed from settings, not held in a registry).
     * @return the metadata just read, or null if it was already attempted, could not be
     *   read, or the source was unavailable. A null return is normal, not an error.
     */
    suspend fun backfill(photoId: Long, resolveSource: suspend (SourceId) -> PhotoSource?): ExifMetadata? {
        return try {
            gate.withLock {
                val row = dao.byId(photoId) ?: return null
                if (row.exifScannedAtEpochMs != null) return null   // already attempted

                val source = resolveSource(SourceId(row.sourceId)) ?: return null
                val item = PhotoItem(
                    stableId = row.stableId,
                    sourceId = SourceId(row.sourceId),
                    normalizedPath = row.normalizedPath,
                    folderName = row.folderName,
                    fileName = row.fileName,
                    mimeType = row.mimeType,
                    sizeBytes = row.sizeBytes,
                    fileModifiedEpochMs = row.fileModifiedEpochMs,
                    openToken = row.openToken,
                )

                val exif = withTimeoutOrNull(timeoutMs) {
                    source.openStream(item, OpenOptions(timeoutMs = timeoutMs)).use { ExifExtractor.extract(it) }
                }

                val now = System.currentTimeMillis()
                if (exif == null) {
                    // Read failed or timed out. Deliberately NOT stamped: unlike "this photo
                    // has no EXIF", a failure may be transient (NAS asleep, Wi-Fi drop), so
                    // the row stays queued for a later attempt.
                    diagnostics.log(
                        DiagnosticsLog.Category.SCAN,
                        "EXIF_BACKFILL_MISS",
                        "sourceToken" to com.example.familyphotoframe.data.diagnostics.diagnosticToken(
                            row.sourceId,
                            "source",
                        ),
                    )
                    return null
                }

                dao.updateExif(
                    id = photoId,
                    width = exif.width,
                    height = exif.height,
                    orientation = exif.orientation,
                    dateTaken = exif.dateTakenEpochMs,
                    caption = exif.caption,
                    gpsLat = exif.gpsLat,
                    gpsLon = exif.gpsLon,
                    scannedAt = now,
                )
                exif
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            null
        }
    }
}
