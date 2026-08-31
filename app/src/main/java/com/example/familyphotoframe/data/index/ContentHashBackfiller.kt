package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.db.PhotoItemEntity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.SourceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.familyphotoframe.util.toHexString
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Incrementally computes exact photo-content SHA-256 values outside slideshow rendering.
 *
 * Hashing is intentionally asynchronous and bounded: one source batch runs at a time,
 * every open has a timeout, and no bitmap is decoded. Successful hashes allow the
 * folder-balanced eligibility provider to collapse byte-identical copies inside one
 * direct folder while leaving identical copies in different folders independent.
 */
class ContentHashBackfiller(
    private val dao: PhotoDao,
    private val diagnostics: DiagnosticsLog,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Long = 60_000L,
    private val batchSize: Int = 32,
    private val messageDigestFactory: () -> MessageDigest = { MessageDigest.getInstance("SHA-256") },
) {
    data class BatchResult(val indexed: Int, val failed: Int, val remainingMayExist: Boolean)

    private val gate = Mutex()

    /**
     * Android 5's SHA-256 provider owns a native digest context. Constructing one for
     * every remote photo makes a long SMB backfill look like a native-memory leak until
     * finalization catches up. The gate serializes every call, so one resettable digest
     * safely serves both foreground and background hashing without per-file native churn.
     */
    private val sha256Digest = ReusableMessageDigest(messageDigestFactory)

    suspend fun backfill(
        photoId: Long,
        resolveSource: suspend (SourceId) -> PhotoSource?,
    ): String? = gate.withLock {
        val row = dao.byId(photoId) ?: return@withLock null
        row.contentSha256?.let { return@withLock it }
        val source = resolveSource(SourceId(row.sourceId)) ?: return@withLock null
        hashAndPersist(row, source)
    }

    /** Process a bounded source batch. Call repeatedly from a cancellable background job. */
    suspend fun backfillPending(source: PhotoSource, maxBatches: Int = 64): BatchResult = gate.withLock {
        var indexed = 0
        var failed = 0
        var hadFullBatch = false
        repeat(maxBatches.coerceAtLeast(1)) {
            coroutineContext.ensureActive()
            val rows = dao.photosNeedingContentHash(source.id.value, batchSize)
            if (rows.isEmpty()) {
                diagnostics.log(
                    DiagnosticsLog.Category.SCAN, "CONTENT_HASH_INDEX_COMPLETE",
                    "sourceToken" to diagnosticToken(source.id.value, "source"),
                    "indexed" to indexed.toString(),
                    "failed" to failed.toString(),
                )
                return@withLock BatchResult(indexed, failed, false)
            }
            hadFullBatch = rows.size == batchSize
            var batchSucceeded = 0
            for (row in rows) {
                coroutineContext.ensureActive()
                if (hashAndPersist(row, source) != null) {
                    indexed++
                    batchSucceeded++
                } else {
                    failed++
                }
            }
            // Stop after a completely failed batch. A disconnected source is handled by
            // source-level recovery; continuing here would create a per-photo retry storm.
            if (batchSucceeded == 0) {
                diagnostics.log(
                    DiagnosticsLog.Category.SCAN, "CONTENT_HASH_BACKOFF",
                    "sourceToken" to diagnosticToken(source.id.value, "source"),
                    "failed" to failed.toString(),
                )
                return@withLock BatchResult(indexed, failed, true)
            }
            if (rows.size < batchSize) {
                return@withLock BatchResult(indexed, failed, false)
            }
        }
        BatchResult(indexed, failed, hadFullBatch)
    }

    private suspend fun hashAndPersist(row: PhotoItemEntity, source: PhotoSource): String? {
        val attemptedAt = System.currentTimeMillis()
        return try {
            val digest = withTimeoutOrNull(timeoutMs) {
                withContext(io) {
                    val md = sha256Digest.resetForNextHash()
                    val item = row.toPhotoItem()
                    var bytesRead = 0L
                    source.openStream(
                        item,
                        OpenOptions(
                            timeoutMs = timeoutMs,
                            preferOriginal = true,
                            purpose = com.example.familyphotoframe.data.source.OpenPurpose.CONTENT_HASH,
                        ),
                    ).use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            md.update(buffer, 0, read)
                            bytesRead += read
                        }
                    }
                    if (row.sizeBytes > 0L && bytesRead != row.sizeBytes) null
                    else md.digest().toHexString()
                }
            }
            if (digest == null) {
                dao.markContentHashAttempt(row.id, attemptedAt)
                null
            } else {
                dao.updateContentHash(row.id, digest, attemptedAt)
                digest
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            dao.markContentHashAttempt(row.id, attemptedAt)
            null
        }
    }

    private fun PhotoItemEntity.toPhotoItem() = PhotoItem(
        stableId = stableId,
        sourceId = SourceId(sourceId),
        normalizedPath = normalizedPath,
        folderName = folderName,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        fileModifiedEpochMs = fileModifiedEpochMs,
        openToken = openToken,
    )
}

/** Single-owner digest holder; [ContentHashBackfiller.gate] provides serialization. */
internal class ReusableMessageDigest(
    private val factory: () -> MessageDigest = { MessageDigest.getInstance("SHA-256") },
) {
    private val digest: MessageDigest by lazy(LazyThreadSafetyMode.NONE, factory)

    fun resetForNextHash(): MessageDigest = digest.apply { reset() }
}
