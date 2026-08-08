package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.db.PhotoItemEntity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.SourceRefreshTrigger
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.ScanEvent
import com.example.familyphotoframe.data.source.ScanOptions
import com.example.familyphotoframe.data.source.SourceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether [Indexer] reads EXIF during a scan (Phase 2 increment 8).
 *
 * Reading EXIF costs one `openStream` per photo. For local sources that is a cheap
 * local read; for SMB it is a full network round-trip against an uncached
 * `SmbFile(...).inputStream`, so a 10k-photo NAS library would pay ~10k extra opens
 * per scan. [LOCAL_ONLY] is therefore the default: remote photos are left with null
 * EXIF columns at scan time and filled in later by `ExifBackfiller` when they are
 * actually displayed, which spreads the cost across photos the user really sees.
 */
enum class ExifScanPolicy { LOCAL_ONLY, ALL_SOURCES, NEVER }

sealed interface IndexProgress {
    data class Scanning(val found: Int) : IndexProgress
    data class Completed(
        val total: Int,
        val errors: Int,
        val found: Int,
        val exifMisses: Int,
        val reconciled: Boolean,
        val completionState: String,
    ) : IndexProgress
}

/** Correlation metadata supplied by the source-refresh owner. */
data class ScanDiagnosticContext(
    val sourceKind: String,
    val trigger: SourceRefreshTrigger,
    val configRevision: String,
    val context: DiagnosticContext,
) {
    companion object {
        fun untracked(source: PhotoSource): ScanDiagnosticContext = ScanDiagnosticContext(
            sourceKind = source.type.name,
            trigger = SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED,
            configRevision = "config_untracked",
            context = DiagnosticContext(origin = DiagnosticOrigin.INTERNAL),
        )
    }
}

/**
 * Drives a [PhotoSource] scan into Room (spec §5.2): streams [ScanEvent]s, inserts
 * in batches (never building an unbounded list — Contract Rule 3), and reconciles
 * by deleting rows not refreshed by this scan. The source's own flow runs off the
 * main thread; Room suspend calls run on Room's executor.
 *
 * Subject to [exifPolicy], the scan also opens each file once to read date-taken,
 * caption, and GPS (Phase 2 increment 5, spec §11 overlays). This is strictly
 * best-effort: a read that fails or times out just leaves those columns null and does
 * NOT count as a scan error (spec §8 posture — missing overlay metadata is never a
 * reason to treat a photo as undisplayable). It is bounded per-file by [exifTimeoutMs]
 * so one slow file cannot stall the whole scan. Under the default
 * [ExifScanPolicy.LOCAL_ONLY], remote sources skip this entirely and are filled in by
 * [ExifBackfiller] at display time instead.
 */
class Indexer(
    private val dao: PhotoDao,
    private val diagnostics: DiagnosticsLog,
    private val batchSize: Int = 200,
    private val exifPolicy: ExifScanPolicy = ExifScanPolicy.LOCAL_ONLY,
    private val exifTimeoutMs: Long = 4_000,
) {

    /** Remote sources are skipped under [ExifScanPolicy.LOCAL_ONLY]; see the enum docs. */
    private fun readsExifDuringScan(source: PhotoSource): Boolean =
        ExifScanDecision.shouldRead(exifPolicy, source.type)

    fun index(
        source: PhotoSource,
        options: ScanOptions,
        diagnosticContext: ScanDiagnosticContext = ScanDiagnosticContext.untracked(source),
    ): Flow<IndexProgress> = flow {
        val scanStarted = System.currentTimeMillis()
        val startedElapsedMs = System.nanoTime() / 1_000_000L
        val buffer = ArrayList<PhotoItemEntity>(batchSize)
        val extractExif = readsExifDuringScan(source)
        var found = 0
        var errors = 0
        var exifFailures = 0
        var receivedFinished = false
        var terminalCode: String? = null
        var terminalFields: Map<String, String> = emptyMap()
        var lastProgressElapsedMs = startedElapsedMs
        var lastProgressFound = 0

        fun elapsedMs(): Long = (System.nanoTime() / 1_000_000L - startedElapsedMs).coerceAtLeast(0L)

        fun baseFields(): Map<String, String> = mapOf(
            "sourceKind" to diagnosticContext.sourceKind,
            "trigger" to diagnosticContext.trigger.name,
            "configRevision" to diagnosticContext.configRevision,
        )

        fun prepareTerminal(code: String, fields: Map<String, String>) {
            terminalCode = code
            terminalFields = fields
        }

        fun logProgressIfDue() {
            val now = System.nanoTime() / 1_000_000L
            if (now - lastProgressElapsedMs < PROGRESS_INTERVAL_MS ||
                found - lastProgressFound < PROGRESS_FILE_INTERVAL
            ) return
            lastProgressElapsedMs = now
            lastProgressFound = found
            diagnostics.logEvent(
                "SCAN_PROGRESS",
                baseFields() + mapOf(
                    "found" to found.toString(),
                    "errors" to errors.toString(),
                    "exifMisses" to exifFailures.toString(),
                    "durationMs" to elapsedMs().toString(),
                    "progressBucket" to (found / PROGRESS_FILE_INTERVAL).toString(),
                ),
                diagnosticContext.context,
            )
        }

        suspend fun flush() {
            if (buffer.isEmpty()) return
            val fresh = buffer.toList()
            val previousByPath = dao.rowsForScanMerge(
                sourceId = source.id.value,
                normalizedPaths = fresh.map { it.normalizedPath },
            ).associateBy { it.normalizedPath }
            dao.insertBatch(
                fresh.map { row -> ScanMergePolicy.merge(row, previousByPath[row.normalizedPath]) }
            )
            buffer.clear()
        }

        diagnostics.logEvent(
            "SCAN_STARTED",
            baseFields() + mapOf(
                "stage" to "ENUMERATING",
                "includeSubfolders" to options.includeSubfolders.toString(),
            ),
            diagnosticContext.context,
        )

        try {
            source.scan(previousCursor = null, options = options).collect { event ->
                when (event) {
                    is ScanEvent.FileFound -> {
                        val exif = if (extractExif) readExifSafely(source, event.item) else null
                        if (extractExif && exif == null) exifFailures++
                        buffer.add(event.item.toEntity(scanStarted, exif, attemptedExif = extractExif))
                        found++
                        if (buffer.size >= batchSize) {
                            flush()
                            emit(IndexProgress.Scanning(found))
                            logProgressIfDue()
                        }
                    }
                    is ScanEvent.DirectoryProgress -> {
                        emit(IndexProgress.Scanning(found))
                        logProgressIfDue()
                    }
                    is ScanEvent.DeletedOrMissing -> Unit
                    is ScanEvent.Error -> {
                        errors++
                        diagnostics.logEvent(
                            "SCAN_ERROR",
                            baseFields() + mapOf(
                                "errorCode" to event.error.name,
                                "errors" to errors.toString(),
                                "found" to found.toString(),
                            ),
                            diagnosticContext.context,
                        )
                    }
                    is ScanEvent.Finished -> {
                        receivedFinished = true
                        flush()
                        val reconciled = ScanCompletionPolicy.shouldReconcile(receivedFinished, errors)
                        if (reconciled) dao.deleteStaleForSource(source.id.value, scanStarted)
                        diagnostics.logEvent(
                            if (reconciled) "SCAN_RECONCILIATION_COMPLETED"
                            else "SCAN_RECONCILIATION_SKIPPED",
                            baseFields() + mapOf(
                                "found" to found.toString(),
                                "errors" to errors.toString(),
                                "reconciled" to reconciled.toString(),
                                "completionState" to if (reconciled) "COMPLETE" else "PARTIAL",
                            ),
                            diagnosticContext.context,
                        )
                        val total = dao.countForSource(source.id.value)
                        prepareTerminal(
                            "SCAN_COMPLETED",
                            mapOf(
                                "durationMs" to elapsedMs().toString(),
                                "found" to found.toString(),
                                "total" to total.toString(),
                                "errors" to errors.toString(),
                                "exifMisses" to exifFailures.toString(),
                                "reconciled" to reconciled.toString(),
                                "completionState" to if (errors == 0) "COMPLETE" else "PARTIAL",
                            ),
                        )
                        emit(
                            IndexProgress.Completed(
                                total = total,
                                errors = errors,
                                found = found,
                                exifMisses = exifFailures,
                                reconciled = reconciled,
                                completionState = if (errors == 0) "COMPLETE" else "PARTIAL",
                            )
                        )
                    }
                }
            }

            // Authentication loss and permission loss deliberately stop without Finished.
            if (!receivedFinished) {
                flush()
                errors = ScanCompletionPolicy.finalErrorCount(receivedFinished, errors)
                val total = dao.countForSource(source.id.value)
                diagnostics.logEvent(
                    "SCAN_RECONCILIATION_SKIPPED",
                    baseFields() + mapOf(
                        "found" to found.toString(),
                        "errors" to errors.toString(),
                        "reconciled" to "false",
                        "completionState" to "MISSING_FINISHED",
                        "reason" to "SOURCE_FLOW_ENDED_EARLY",
                    ),
                    diagnosticContext.context,
                )
                prepareTerminal(
                    "SCAN_ABORTED",
                    mapOf(
                        "durationMs" to elapsedMs().toString(),
                        "found" to found.toString(),
                        "total" to total.toString(),
                        "errors" to errors.toString(),
                        "exifMisses" to exifFailures.toString(),
                        "reconciled" to "false",
                        "completionState" to "MISSING_FINISHED",
                        "cancellationReason" to "SOURCE_FLOW_ENDED_EARLY",
                    ),
                )
                emit(
                    IndexProgress.Completed(
                        total = total,
                        errors = errors,
                        found = found,
                        exifMisses = exifFailures,
                        reconciled = false,
                        completionState = "MISSING_FINISHED",
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            prepareTerminal(
                "SCAN_ABORTED",
                mapOf(
                    "durationMs" to elapsedMs().toString(),
                    "found" to found.toString(),
                    "errors" to errors.toString(),
                    "exifMisses" to exifFailures.toString(),
                    "reconciled" to "false",
                    "completionState" to "CANCELLED",
                    "cancellationReason" to if (cancelled.message == "SOURCE_CONFIGURATION_CHANGED") {
                        "SOURCE_CONFIGURATION_CHANGED"
                    } else {
                        "COROUTINE_CANCELLED"
                    },
                ),
            )
            throw cancelled
        } catch (error: Exception) {
            prepareTerminal(
                "SCAN_ABORTED",
                mapOf(
                    "durationMs" to elapsedMs().toString(),
                    "found" to found.toString(),
                    "errors" to (errors + 1).toString(),
                    "exifMisses" to exifFailures.toString(),
                    "reconciled" to "false",
                    "completionState" to "FAILED",
                    "errorClass" to error.javaClass.simpleName,
                    "errorCode" to "SCAN_EXCEPTION",
                    "cancellationReason" to "NONE",
                ),
            )
            throw error
        } finally {
            val code = terminalCode ?: "SCAN_ABORTED"
            val fields = if (terminalCode != null) terminalFields else mapOf(
                "durationMs" to elapsedMs().toString(),
                "found" to found.toString(),
                "errors" to errors.toString(),
                "exifMisses" to exifFailures.toString(),
                "reconciled" to "false",
                "completionState" to "TERMINATED",
                "cancellationReason" to "UNKNOWN_TERMINATION",
            )
            diagnostics.logEvent(code, baseFields() + fields, diagnosticContext.context)
        }
    }

    /**
     * Best-effort EXIF read for one file. Never throws, never delays the scan beyond
     * [exifTimeoutMs], and never logs photo content — only an aggregate miss count is
     * logged in the terminal `SCAN_COMPLETED`/`SCAN_ABORTED` fields (spec §17.2:
     * no GPS/paths in diagnostics).
     */
    private suspend fun readExifSafely(source: PhotoSource, item: PhotoItem): ExifMetadata? =
        try {
            withTimeoutOrNull(exifTimeoutMs) {
                source.openStream(item, OpenOptions(timeoutMs = exifTimeoutMs)).use { input ->
                    ExifExtractor.extract(input)
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            null
        }

    private fun PhotoItem.toEntity(indexedAt: Long, exif: ExifMetadata?, attemptedExif: Boolean) = PhotoItemEntity(
        stableId = stableId,
        sourceId = sourceId.value,
        normalizedPath = normalizedPath,
        folderName = folderName,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        fileModifiedEpochMs = fileModifiedEpochMs,
        openToken = openToken,
        indexedAtEpochMs = indexedAt,
        width = exif?.width,
        height = exif?.height,
        exifOrientation = exif?.orientation ?: 0,
        dateTakenEpochMs = exif?.dateTakenEpochMs,
        caption = exif?.caption,
        gpsLat = exif?.gpsLat,
        gpsLon = exif?.gpsLon,
        // Only stamped when EXIF was actually read; left null when the scan skipped it,
        // which is the signal ExifBackfiller uses to fill this row in on first display.
        exifScannedAtEpochMs = if (attemptedExif) indexedAt else null,
        canonicalDirectory = CanonicalPhotoPath.directDirectory(normalizedPath),
    )

    private companion object {
        const val PROGRESS_INTERVAL_MS = 5_000L
        const val PROGRESS_FILE_INTERVAL = 1_000
    }
}

/** Pure policy seam so every source type is covered by a regression test. */
internal object ExifScanDecision {
    fun shouldRead(policy: ExifScanPolicy, sourceType: SourceType): Boolean = when (policy) {
        ExifScanPolicy.NEVER -> false
        ExifScanPolicy.ALL_SOURCES -> true
        ExifScanPolicy.LOCAL_ONLY -> sourceType.isLocal
    }
}
