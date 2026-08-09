package com.example.familyphotoframe.data.cache

import android.content.Context
import android.graphics.Bitmap
import com.example.familyphotoframe.data.db.LocalThumbnailCacheDao
import com.example.familyphotoframe.data.db.LocalThumbnailCacheEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Disk cache of decoded-and-downscaled JPEG thumbnails for LOCAL (SAF/fallback) photos.
 *
 * This is a distinct concern from [MediaCache]: that cache avoids re-fetching original
 * bytes over a network for remote sources. Local photos are already on local storage,
 * so re-fetching is never the cost — the repeated cost is CPU decode/downscale time,
 * paid in full on every display with no reuse. This cache persists the already-decoded
 * thumbnail instead, keyed by the photo's stableId plus the exact target decode
 * dimensions (the same photo legitimately decodes at different sizes: a full-frame
 * slide vs. a portrait-collage tile vs. a memory-pressure-scaled decode).
 *
 * [get] never decodes or populates — it is a pure cache lookup. [put] is meant to be
 * called synchronously (awaited) by the caller immediately after a real decode, while
 * the caller still exclusively owns the just-decoded [Bitmap] and before it is handed
 * to the display/reclaim pipeline — deliberately NOT fire-and-forget on a background
 * scope. This app's `LegacyBitmapReclaimer` uses a delayed-recycle strategy on API
 * 21–25 specifically so a still-drawing display list is never handed a recycled
 * bitmap; it has no way to know about a second, independent background reader, so a
 * detached "populate later" job could race a recycle and crash. Paying the JPEG-encode
 * cost synchronously, once per (photo, size) ever, is the safe trade.
 */
class LocalThumbnailCache(
    context: Context,
    private val dao: LocalThumbnailCacheDao,
    private val io: CoroutineDispatcher,
    /** Suspend, not a synchronous property: settings are read via `settings.settings.first()`
     * throughout this codebase (DataStore-backed, no synchronously-readable snapshot). */
    private val enabledProvider: suspend () -> Boolean,
    /** Max cache size in bytes; callers should pass an already-[clampMaxBytes]ed value. */
    private val maxBytesProvider: suspend () -> Long,
) {
    private val dir: File = File(context.filesDir, "localthumbcache").apply { mkdirs() }
    private val tmpSuffixCounter = java.util.concurrent.atomic.AtomicLong(0)

    /** Mirrors the ViewModel's rebuild-job progress so the web status endpoint can poll it
     * without a direct reference to the ViewModel. Written by the rebuild job only. */
    @Volatile var rebuildInProgress: Boolean = false
    @Volatile var rebuildCount: Int = 0

    private fun keyFor(stableId: String, width: Int, height: Int): String =
        "${stableId}_${width}x${height}"

    /** Returns a cached thumbnail file, or null on a miss/disabled/stale entry. */
    suspend fun get(stableId: String, width: Int, height: Int): File? = withContext(io) {
        if (!enabledProvider()) return@withContext null
        val key = keyFor(stableId, width, height)
        val entry = try {
            dao.get(key)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            return@withContext null
        } ?: return@withContext null
        val f = File(entry.localFilePathPrivate)
        if (!f.exists()) {
            dao.delete(key)
            return@withContext null
        }
        dao.touch(key, System.currentTimeMillis())
        f
    }

    /**
     * Persist [bitmap] (already decoded at [width]x[height]) as a JPEG thumbnail.
     * A no-op when disabled or when [bitmap] is already recycled. [protectedStableIds]
     * (the currently-displayed and next-preloaded photos) are never evicted to make
     * room for this write.
     */
    suspend fun put(
        stableId: String,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        protectedStableIds: Set<String>,
    ) {
        if (!enabledProvider() || bitmap.isRecycled) return
        withContext(io) {
            val key = keyFor(stableId, width, height)
            val target = File(dir, key)
            val tmp = File(dir, "$key.${tmpSuffixCounter.incrementAndGet()}.part")
            try {
                FileOutputStream(tmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                val now = System.currentTimeMillis()
                dao.put(
                    LocalThumbnailCacheEntity(
                        cacheKey = key,
                        photoStableId = stableId,
                        sizeBucket = "${width}x${height}",
                        localFilePathPrivate = target.path,
                        sizeBytes = target.length(),
                        createdAtEpochMs = now,
                        lastAccessedAtEpochMs = now,
                    )
                )
                evictIfNeeded(protectedStableIds)
            } catch (c: CancellationException) {
                tmp.delete()
                throw c
            } catch (_: Exception) {
                tmp.delete()
            }
        }
    }

    private suspend fun evictIfNeeded(protectedStableIds: Set<String>) {
        var total = dao.totalSizeBytes()
        val max = maxBytesProvider()
        if (total <= max) return
        val candidates = dao.evictionCandidates(limit = 256)
        for (c in candidates) {
            if (total <= max) break
            if (c.photoStableId in protectedStableIds) continue
            File(c.localFilePathPrivate).delete()
            dao.delete(c.cacheKey)
            total -= c.sizeBytes
        }
    }

    /** Deletes every cached thumbnail. The cache repopulates lazily as photos are shown again. */
    suspend fun clear() = withContext(io) {
        dao.clear()
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    suspend fun currentSizeBytes(): Long = withContext(io) { dao.totalSizeBytes() }

    /** The configured max, clamped against currently-available space right now. */
    suspend fun effectiveMaxBytes(): Long = maxBytesProvider()

    companion object {
        private const val JPEG_QUALITY = 85
        private const val GB = 1024L * 1024L * 1024L

        const val MIN_MAX_BYTES = 1L * GB
        const val RESERVED_FREE_BYTES = 2L * GB

        /**
         * Clamp a user-requested max size to `[1 GiB, currently-available space - 2 GiB]`.
         * The ceiling is recomputed against live free space every time — it is not a
         * one-time setting, since available space genuinely changes as the device is used.
         */
        fun clampMaxBytes(requestedBytes: Long, context: Context): Long {
            val free = context.filesDir.usableSpace
            val ceiling = max(MIN_MAX_BYTES, free - RESERVED_FREE_BYTES)
            return requestedBytes.coerceIn(MIN_MAX_BYTES, ceiling)
        }
    }
}
