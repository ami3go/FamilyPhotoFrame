package com.example.familyphotoframe.data.cache

import android.content.Context
import android.graphics.Bitmap
import com.example.familyphotoframe.data.db.LocalThumbnailCacheDao
import com.example.familyphotoframe.data.db.LocalThumbnailCacheEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
    private class KeyLock {
        val mutex = Mutex()
        var users = 0
    }

    private val keyLocksGuard = Any()
    private val keyLocks = HashMap<String, KeyLock>()
    private val reconcileLock = Mutex()
    private val evictionLock = Mutex()
    private val maintenanceLock = Mutex()
    private val cacheGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile private var reconciled = false

    /** Mirrors the ViewModel's rebuild-job progress so the web status endpoint can poll it
     * without a direct reference to the ViewModel. Written by the rebuild job only. */
    @Volatile var rebuildInProgress: Boolean = false
    @Volatile var rebuildCount: Int = 0

    private fun keyFor(stableId: String, width: Int, height: Int): String =
        "${stableId}_${width}x${height}"

    /** Returns a cached thumbnail file, or null on a miss/disabled/stale entry. */
    suspend fun get(stableId: String, width: Int, height: Int): File? = withContext(io) {
        if (!enabledProvider()) return@withContext null
        ensureReconciled()
        val key = keyFor(stableId, width, height)
        withKeyLock(key) {
            maintenanceLock.withLock {
                val entry = try {
                    dao.get(key)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    return@withLock null
                } ?: return@withLock null
                val f = File(entry.localFilePathPrivate)
                if (!f.exists()) {
                    dao.delete(key)
                    return@withLock null
                }
                dao.touch(key, System.currentTimeMillis())
                f
            }
        }
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
            ensureReconciled()
            val key = keyFor(stableId, width, height)
            withKeyLock(key) {
                val generation = cacheGeneration.get()
                val target = File(dir, key)
                val tmp = File(dir, "$key.${tmpSuffixCounter.incrementAndGet()}.part")
                var targetCommitted = false
                var indexCommitted = false
                try {
                    if (dir.usableSpace <= RESERVED_FREE_BYTES) return@withKeyLock
                    FileOutputStream(tmp).use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                            "thumbnail_encode_failed"
                        }
                        out.fd.sync()
                    }
                    maintenanceLock.withLock {
                        if (cacheGeneration.get() != generation) return@withLock
                        if (target.exists() && !target.delete()) return@withLock
                        if (!tmp.renameTo(target)) return@withLock
                        targetCommitted = true
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
                        indexCommitted = true
                    }
                    if (!indexCommitted) return@withKeyLock
                    evictIfNeeded(protectedStableIds + stableId)
                } catch (c: CancellationException) {
                    if (targetCommitted && !indexCommitted) target.delete()
                    throw c
                } catch (_: Exception) {
                    if (targetCommitted && !indexCommitted) target.delete()
                } finally {
                    tmp.delete()
                }
            }
        }
    }

    private suspend fun <T> withKeyLock(key: String, block: suspend () -> T): T {
        val holder = synchronized(keyLocksGuard) {
            keyLocks.getOrPut(key, ::KeyLock).also { it.users++ }
        }
        var locked = false
        return try {
            holder.mutex.lock()
            locked = true
            block()
        } finally {
            if (locked) holder.mutex.unlock()
            synchronized(keyLocksGuard) {
                holder.users--
                if (holder.users == 0 && keyLocks[key] === holder) keyLocks.remove(key)
            }
        }
    }

    private suspend fun ensureReconciled() {
        if (reconciled) return
        reconcileLock.withLock {
            if (reconciled) return
            maintenanceLock.withLock {
                var afterKey = ""
                while (true) {
                    val page = dao.reconciliationPage(afterKey, RECONCILIATION_BATCH_SIZE)
                    if (page.isEmpty()) break
                    for (entry in page) {
                        val file = File(entry.localFilePathPrivate)
                        val owned = file.absoluteFile.parentFile == dir.absoluteFile
                        if (!owned || !file.isFile) {
                            dao.delete(entry.cacheKey)
                            if (owned) file.delete()
                        }
                    }
                    afterKey = page.last().cacheKey
                    if (page.size < RECONCILIATION_BATCH_SIZE) break
                }
                dir.listFiles()?.forEach { file ->
                    val indexed = if (file.name.endsWith(".part")) null else dao.get(file.name)
                    val ownsThisFile = indexed != null &&
                        File(indexed.localFilePathPrivate).absoluteFile == file.absoluteFile
                    if (!ownsThisFile) file.delete()
                }
                reconciled = true
            }
        }
    }

    private suspend fun evictIfNeeded(protectedStableIds: Set<String>) = evictionLock.withLock {
        maintenanceLock.withLock {
            val max = maxBytesProvider().coerceAtLeast(0L)
            var total = dao.totalSizeBytes()
            val protected = (protectedStableIds + EMPTY_PROTECTED_SENTINEL).toList()
            while (total > max) {
                val candidates = dao.evictionCandidates(protected, limit = EVICTION_BATCH_SIZE)
                if (candidates.isEmpty()) break
                var removedAny = false
                for (candidate in candidates) {
                    if (total <= max) break
                    val file = File(candidate.localFilePathPrivate)
                    if (file.exists() && !file.delete()) continue
                    dao.delete(candidate.cacheKey)
                    total = subtractSize(total, candidate.sizeBytes)
                    removedAny = true
                }
                if (!removedAny) break
                total = dao.totalSizeBytes()
            }
        }
    }

    /** Deletes every cached thumbnail. The cache repopulates lazily as photos are shown again. */
    suspend fun clear() = withContext(io) {
        maintenanceLock.withLock {
            cacheGeneration.incrementAndGet()
            dao.clear()
            dir.listFiles()?.forEach { it.delete() }
            reconciled = true
        }
        Unit
    }

    suspend fun currentSizeBytes(): Long = withContext(io) { dao.totalSizeBytes() }

    /** The configured max, clamped against currently-available space right now. */
    suspend fun effectiveMaxBytes(): Long = maxBytesProvider()

    companion object {
        private const val JPEG_QUALITY = 85
        private const val GB = 1024L * 1024L * 1024L
        private const val EVICTION_BATCH_SIZE = 256
        private const val RECONCILIATION_BATCH_SIZE = 256
        private const val EMPTY_PROTECTED_SENTINEL = "__never_a_photo_id__"

        const val RESERVED_FREE_BYTES = 2L * GB

        private fun subtractSize(total: Long, removed: Long): Long {
            val safeRemoved = removed.coerceAtLeast(0L)
            return if (safeRemoved >= total) 0L else total - safeRemoved
        }

        /**
         * Clamp the configured maximum while preserving 2 GiB of live free space. On a
         * nearly-full filesystem zero disables new cache growth; forcing a 1 GiB minimum
         * there would violate the reserve this function promises to keep.
         */
        fun clampMaxBytes(
            requestedBytes: Long,
            context: Context,
            currentCacheBytes: Long = 0L,
        ): Long {
            val free = context.filesDir.usableSpace.coerceAtLeast(0L)
            val current = currentCacheBytes.coerceAtLeast(0L)
            val reclaimable = if (current > Long.MAX_VALUE - free) Long.MAX_VALUE else free + current
            val ceiling = (reclaimable - RESERVED_FREE_BYTES).coerceAtLeast(0L)
            return requestedBytes.coerceAtLeast(0L).coerceAtMost(ceiling)
        }
    }
}
