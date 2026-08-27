package com.example.familyphotoframe.data.cache

import android.content.Context
import android.graphics.BitmapFactory
import com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker
import com.example.familyphotoframe.data.diagnostics.RuntimeResourceTracker
import com.example.familyphotoframe.data.db.CacheIndexDao
import com.example.familyphotoframe.data.db.CacheIndexEntity
import com.example.familyphotoframe.data.source.OpenOptions
import com.example.familyphotoframe.data.source.OpenPurpose
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.max
import kotlin.math.min

/**
 * App-owned disk LRU cache for remote image bytes (SMB and Synology) (spec §16, Contract Rule 19).
 *
 * This is the single owner of full remote bytes — Coil's disk cache is NOT used for
 * remote sources; the slideshow loads the returned cached [File]. Writes are atomic
 * (temp `.part` → verified decode → rename), so a cancelled or corrupt download never
 * becomes a valid cache entry (spec §16.1, §8.3). Eviction is least-recently-accessed
 * and never removes the currently displayed or next-preloaded item (spec §16.1).
 *
 * The cache key equals the photo's stableId (both are hash(sourceId+path+size+mtime),
 * spec §16.2), so a changed remote file naturally gets a new key and old bytes evict.
 */
class MediaCache(
    context: Context,
    private val dao: CacheIndexDao,
    private val io: CoroutineDispatcher,
    /**
     * Mirrors cache membership onto the photo index (`photos.cacheKey`) so playback can
     * ask "which photos of this source can I show right now, offline?" with an indexed
     * query. Optional so existing callers and tests need no cache/index wiring; when it
     * is absent the cache behaves exactly as before.
     *
     * Declared before [maxBytesProvider] deliberately: callers pass the size provider as
     * a trailing lambda, and a trailing lambda binds to the *last* parameter.
     */
    private val photoIndex: PhotoCacheIndexWriter? = null,
    /** Shared process counters used by the one-minute runtime evidence sampler. */
    private val resourceTracker: RuntimeResourceTracker = RuntimeResourceTracker(),
    /** Aggregate native-heap attribution for the bounds-only cache verification decode. */
    private val nativeStageTracker: NativeAllocationStageTracker = NativeAllocationStageTracker(),
    /** Max cache size in bytes; defaults to spec §16.1 formula. */
    private val maxBytesProvider: (suspend () -> Long)? = null,
) {
    /** The slice of [com.example.familyphotoframe.data.db.PhotoDao] this cache may write. */
    interface PhotoCacheIndexWriter {
        suspend fun setCacheKey(stableId: String, cacheKey: String?)
        suspend fun clearCacheKey(cacheKey: String)
        suspend fun clearAllCacheKeys()
    }

    enum class FailureStage {
        CACHE_LOOKUP,
        SOURCE_READ,
        VERIFY_DECODE,
        CACHE_COMMIT,
        UNKNOWN,
    }

    sealed interface ResolveResult {
        data class Ready(val file: File, val cacheHit: Boolean) : ResolveResult
        data class Failed(
            val stage: FailureStage,
            val exceptionClass: String? = null,
            /** True only for connection/auth/session failures affecting the whole source. */
            val sourceLevelFailure: Boolean = false,
        ) : ResolveResult
    }

    private val dir: File = File(context.filesDir, "mediacache").apply { mkdirs() }
    private val tmpSuffixCounter = java.util.concurrent.atomic.AtomicLong(0)
    private class KeyLock {
        val mutex = Mutex()
        var users = 0
    }

    private val keyLocksGuard = Any()
    private val keyLocks = HashMap<String, KeyLock>()
    private val transferSlots = Semaphore(MAX_CONCURRENT_TRANSFERS)
    private val reconcileLock = Mutex()
    private val evictionLock = Mutex()
    private val maintenanceLock = Mutex()
    private val cacheGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile private var reconciled = false

    fun keyFor(item: PhotoItem): String = item.stableId

    /**
     * Resolve [item] to verified local bytes, preserving the failure stage for diagnostics.
     * [protectedKeys] are never evicted (current/next).
     */
    suspend fun resolve(
        item: PhotoItem,
        source: PhotoSource,
        protectedKeys: Set<String>,
    ): ResolveResult = withContext(io) {
        val key = keyFor(item)
        try {
            ensureReconciled()
            withKeyLock(key) {
                val cached = maintenanceLock.withLock {
                    val existing = dao.get(key)
                    if (existing != null) {
                        val f = File(existing.localFilePathPrivate)
                        if (f.exists() && existing.verifiedDecodeOk) {
                            dao.touch(key, System.currentTimeMillis())
                            mirrorCacheKey(item.stableId, key)
                            return@withLock ResolveResult.Ready(f, cacheHit = true)
                        }
                        // Stale/missing entry — drop it and re-download.
                        dao.delete(key)
                        f.delete()
                        photoIndex?.clearCacheKey(key)
                    }
                    null
                }
                if (cached != null) return@withKeyLock cached
                val generation = cacheGeneration.get()
                when (val downloaded = transferSlots.withPermit {
                    resourceTracker.startMediaTransfer().use {
                        download(item, source, key, generation)
                    }
                }) {
                    is ResolveResult.Ready -> {
                        evictIfNeeded(protectedKeys + key)
                        downloaded
                    }
                    is ResolveResult.Failed -> downloaded
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            ResolveResult.Failed(FailureStage.CACHE_LOOKUP, e.javaClass.simpleName)
        }
    }

    /** Compatibility wrapper for callers that only need the file/null contract. */
    suspend fun get(item: PhotoItem, source: PhotoSource, protectedKeys: Set<String>): File? =
        (resolve(item, source, protectedKeys) as? ResolveResult.Ready)?.file

    /**
     * Return already-cached bytes for [item], or null — never downloads.
     *
     * This is the read path for stale-cache playback (spec §9.3 `on_unreachable`): with
     * the NAS unreachable there is no source to fetch from, so an attempt would only
     * stall the slideshow for a timeout per photo. A missing or unreadable entry is
     * cleaned up here so the index cannot keep advertising a file that is gone.
     */
    suspend fun resolveIfCached(item: PhotoItem): ResolveResult = withContext(io) {
        val key = keyFor(item)
        try {
            ensureReconciled()
            withKeyLock(key) {
                maintenanceLock.withLock {
                    val existing = dao.get(key)
                        ?: return@withLock ResolveResult.Failed(FailureStage.CACHE_LOOKUP)
                    val f = File(existing.localFilePathPrivate)
                    if (f.exists() && existing.verifiedDecodeOk) {
                        dao.touch(key, System.currentTimeMillis())
                        return@withLock ResolveResult.Ready(f, cacheHit = true)
                    }
                    dao.delete(key)
                    f.delete()
                    photoIndex?.clearCacheKey(key)
                    ResolveResult.Failed(FailureStage.CACHE_LOOKUP)
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            ResolveResult.Failed(FailureStage.CACHE_LOOKUP, e.javaClass.simpleName)
        }
    }

    suspend fun getIfCached(item: PhotoItem): File? =
        (resolveIfCached(item) as? ResolveResult.Ready)?.file

    /** One producer per stable cache key; waiters re-check the committed entry. */
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

    private suspend fun mirrorCacheKey(stableId: String, key: String) {
        try {
            photoIndex?.setCacheKey(stableId, key)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            // The cache entry remains usable; a later hit retries this optional mirror.
        }
    }

    private suspend fun download(
        item: PhotoItem,
        source: PhotoSource,
        key: String,
        generation: Long,
    ): ResolveResult {
        val target = File(dir, key)
        // Unique per call so concurrent downloads of the same key (e.g. current photo also
        // happens to be the preloaded next photo) never write/delete/rename the same temp file.
        val tmp = File(dir, "$key.${tmpSuffixCounter.incrementAndGet()}.part")
        var stage = FailureStage.SOURCE_READ
        var targetCommitted = false
        var indexCommitted = false
        return try {
            if (dir.usableSpace <= RESERVED_FREE_BYTES) {
                throw CacheStorageReserveException(RESERVED_FREE_BYTES)
            }
            withTimeout(TOTAL_TRANSFER_TIMEOUT_MS) {
                source.openStream(
                    item,
                    OpenOptions(
                        timeoutMs = TOTAL_TRANSFER_TIMEOUT_MS,
                        purpose = OpenPurpose.DISPLAY_CACHE,
                    ),
                ).use { input ->
                    stage = FailureStage.SOURCE_READ
                    FileOutputStream(tmp).use { out ->
                        input.copyToCancellable(
                            output = out,
                            maxBytes = MAX_ENTRY_BYTES,
                            minimumUsableBytes = RESERVED_FREE_BYTES,
                            usableBytes = { dir.usableSpace },
                        )
                        out.fd.sync()
                    }
                }
            }
            stage = FailureStage.VERIFY_DECODE
            if (!decodes(tmp)) {
                tmp.delete()
                return ResolveResult.Failed(FailureStage.VERIFY_DECODE)
            }
            stage = FailureStage.CACHE_COMMIT
            maintenanceLock.withLock {
                if (cacheGeneration.get() != generation) {
                    throw java.io.IOException("cache_cleared_during_transfer")
                }
                if (target.exists() && !target.delete()) {
                    throw java.io.IOException("cache_target_replace_failed")
                }
                if (!tmp.renameTo(target)) throw java.io.IOException("cache_atomic_rename_failed")
                targetCommitted = true
                val now = System.currentTimeMillis()
                dao.put(
                    CacheIndexEntity(
                        cacheKey = key,
                        photoStableId = item.stableId,
                        localFilePathPrivate = target.path,
                        sizeBytes = target.length(),
                        createdAtEpochMs = now,
                        lastAccessedAtEpochMs = now,
                        verifiedDecodeOk = true,
                    )
                )
                indexCommitted = true
                mirrorCacheKey(item.stableId, key)
            }
            ResolveResult.Ready(target, cacheHit = false)
        } catch (c: CancellationException) {
            tmp.delete()
            if (targetCommitted && !indexCommitted) target.delete()
            throw c
        } catch (e: Exception) {
            tmp.delete()
            if (targetCommitted && !indexCommitted) target.delete()
            ResolveResult.Failed(
                stage,
                e.javaClass.simpleName,
                sourceLevelFailure = stage == FailureStage.SOURCE_READ && isSourceLevelFailure(e),
            )
        }
    }

    private fun isSourceLevelFailure(error: Throwable): Boolean {
        if (error is UnknownHostException || error is ConnectException ||
            error is NoRouteToHostException || error is SocketTimeoutException ||
            error is InterruptedIOException
        ) return true
        val type = error.javaClass.simpleName.lowercase()
        if (type.contains("transport") || type.contains("connection") ||
            type.contains("smbapiexception") || type.contains("authentication")
        ) return true
        val message = error.message.orEmpty().uppercase()
        return message.contains("HOST_UNREACHABLE") ||
            message.contains("QUICK_CONNECT_UNAVAILABLE") ||
            message.contains("QUICKCONNECT_UNAVAILABLE") ||
            message.contains("SESSION_EXPIRED") ||
            message.contains("AUTH_FAILED") ||
            message.contains("TIMED OUT") ||
            message.contains("TIMEOUT")
    }

    /** Bounds-only decode: distinguishes "file exists" from "decodes" (spec §16.1). */
    private fun decodes(f: File): Boolean {
        val operation = nativeStageTracker.start(NativeAllocationStageTracker.Stage.CACHE_VERIFY)
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, opts)
            val valid = opts.outWidth > 0 && opts.outHeight > 0
            operation.finish(
                if (valid) NativeAllocationStageTracker.Outcome.COMPLETED
                else NativeAllocationStageTracker.Outcome.FAILED,
            )
            valid
        } catch (error: Throwable) {
            operation.finish(NativeAllocationStageTracker.Outcome.FAILED)
            throw error
        }
    }

    /** Removes crash leftovers and stale DB rows before the first cache operation. */
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
                        if (!owned || !file.isFile || !entry.verifiedDecodeOk) {
                            dao.delete(entry.cacheKey)
                            photoIndex?.clearCacheKey(entry.cacheKey)
                            if (owned) file.delete()
                        }
                    }
                    afterKey = page.last().cacheKey
                    if (page.size < RECONCILIATION_BATCH_SIZE) break
                }
                dir.listFiles()?.forEach { file ->
                    val indexed = if (file.name.endsWith(".part")) null else dao.get(file.name)
                    val ownsThisFile = indexed != null && indexed.verifiedDecodeOk &&
                        File(indexed.localFilePathPrivate).absoluteFile == file.absoluteFile
                    if (!ownsThisFile) file.delete()
                }
                reconciled = true
            }
        }
    }

    private suspend fun evictIfNeeded(protectedKeys: Set<String>) = evictionLock.withLock {
        maintenanceLock.withLock {
            var total = dao.totalSizeBytes()
            val max = (maxBytesProvider?.invoke() ?: defaultMaxBytes(dir, total)).coerceAtLeast(0L)
            val protected = (protectedKeys + EMPTY_PROTECTED_SENTINEL).toList()
            while (total > max) {
                val candidates = dao.evictionCandidates(protected, limit = EVICTION_BATCH_SIZE)
                if (candidates.isEmpty()) break
                var removedAny = false
                for (candidate in candidates) {
                    if (total <= max) break
                    val file = File(candidate.localFilePathPrivate)
                    if (file.exists() && !file.delete()) continue
                    dao.delete(candidate.cacheKey)
                    photoIndex?.clearCacheKey(candidate.cacheKey)
                    total = subtractSize(total, candidate.sizeBytes)
                    removedAny = true
                }
                if (!removedAny) break
                total = dao.totalSizeBytes()
            }
        }
    }

    suspend fun clear() = withContext(io) {
        maintenanceLock.withLock {
            cacheGeneration.incrementAndGet()
            dao.clear()
            dir.listFiles()?.forEach { it.delete() }
            photoIndex?.clearAllCacheKeys()
            reconciled = true
        }
        Unit
    }

    companion object {
        private const val MB = 1024L * 1024L
        private const val MAX_CONCURRENT_TRANSFERS = 2
        private const val EVICTION_BATCH_SIZE = 64
        private const val RECONCILIATION_BATCH_SIZE = 256
        private const val EMPTY_PROTECTED_SENTINEL = "__never_a_cache_key__"
        private const val MAX_ENTRY_BYTES = 256L * MB
        private const val RESERVED_FREE_BYTES = 512L * MB
        private const val TOTAL_TRANSFER_TIMEOUT_MS = 2L * 60L * 1000L

        private fun subtractSize(total: Long, removed: Long): Long {
            val safeRemoved = removed.coerceAtLeast(0L)
            return if (safeRemoved >= total) 0L else total - safeRemoved
        }

        /** Default max while always preserving a 512 MiB filesystem safety reserve. */
        fun defaultMaxBytes(context: Context, currentCacheBytes: Long = 0L): Long =
            defaultMaxBytes(context.filesDir, currentCacheBytes)

        private fun defaultMaxBytes(storage: File, currentCacheBytes: Long): Long {
            val free = storage.usableSpace.coerceAtLeast(0L)
            val current = currentCacheBytes.coerceAtLeast(0L)
            // Cache bytes are reclaimable. Including them prevents a live max-size
            // calculation from shrinking as the cache grows, then evicting everything
            // when free space approaches the reserve.
            val reclaimable = if (current > Long.MAX_VALUE - free) Long.MAX_VALUE else free + current
            val ceiling = (reclaimable - RESERVED_FREE_BYTES).coerceAtLeast(0L)
            if (ceiling == 0L) return 0L
            val tenPercent = (reclaimable * 0.10).toLong()
            return min(ceiling, max(64L * MB, min(1024L * MB, tenPercent)))
        }
    }
}
