package com.example.familyphotoframe.data.cache

import android.content.Context
import android.graphics.BitmapFactory
import com.example.familyphotoframe.data.db.CacheIndexDao
import com.example.familyphotoframe.data.db.CacheIndexEntity
import com.example.familyphotoframe.data.source.PhotoItem
import com.example.familyphotoframe.data.source.PhotoSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
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
    /** Max cache size in bytes; defaults to spec §16.1 formula. */
    private val maxBytesProvider: () -> Long = { defaultMaxBytes(context) },
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
        val existing = try {
            dao.get(key)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return@withContext ResolveResult.Failed(FailureStage.CACHE_LOOKUP, e.javaClass.simpleName)
        }
        if (existing != null) {
            val f = File(existing.localFilePathPrivate)
            if (f.exists() && existing.verifiedDecodeOk) {
                dao.touch(key, System.currentTimeMillis())
                return@withContext ResolveResult.Ready(f, cacheHit = true)
            }
            // Stale/missing entry — drop it and re-download.
            dao.delete(key)
            f.delete()
            photoIndex?.clearCacheKey(key)
        }
        when (val downloaded = download(item, source, key)) {
            is ResolveResult.Ready -> {
                photoIndex?.setCacheKey(item.stableId, key)
                evictIfNeeded(protectedKeys)
                downloaded
            }
            is ResolveResult.Failed -> downloaded
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
        val existing = try {
            dao.get(key)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            return@withContext ResolveResult.Failed(FailureStage.CACHE_LOOKUP, e.javaClass.simpleName)
        } ?: return@withContext ResolveResult.Failed(FailureStage.CACHE_LOOKUP)
        val f = File(existing.localFilePathPrivate)
        if (f.exists() && existing.verifiedDecodeOk) {
            dao.touch(key, System.currentTimeMillis())
            return@withContext ResolveResult.Ready(f, cacheHit = true)
        }
        dao.delete(key)
        f.delete()
        photoIndex?.clearCacheKey(key)
        ResolveResult.Failed(FailureStage.CACHE_LOOKUP)
    }

    suspend fun getIfCached(item: PhotoItem): File? =
        (resolveIfCached(item) as? ResolveResult.Ready)?.file

    private suspend fun download(item: PhotoItem, source: PhotoSource, key: String): ResolveResult {
        val target = File(dir, key)
        val tmp = File(dir, "$key.part")
        var stage = FailureStage.SOURCE_READ
        return try {
            source.openStream(item).use { input ->
                stage = FailureStage.SOURCE_READ
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            stage = FailureStage.VERIFY_DECODE
            if (!decodes(tmp)) {
                tmp.delete()
                return ResolveResult.Failed(FailureStage.VERIFY_DECODE)
            }
            stage = FailureStage.CACHE_COMMIT
            if (!tmp.renameTo(target)) {          // rename can fail across some FS states
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
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
            ResolveResult.Ready(target, cacheHit = false)
        } catch (c: CancellationException) {
            tmp.delete()
            throw c
        } catch (e: Exception) {
            tmp.delete()
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
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }

    private suspend fun evictIfNeeded(protectedKeys: Set<String>) {
        var total = dao.totalSizeBytes()
        val max = maxBytesProvider()
        if (total <= max) return
        val candidates = dao.evictionCandidates(protectedKeys.toList(), limit = 64)
        for (c in candidates) {
            if (total <= max) break
            File(c.localFilePathPrivate).delete()
            dao.delete(c.cacheKey)
            photoIndex?.clearCacheKey(c.cacheKey)
            total -= c.sizeBytes
        }
    }

    suspend fun clear() = withContext(io) {
        dao.clear()
        dir.listFiles()?.forEach { it.delete() }
        photoIndex?.clearAllCacheKeys()
        Unit
    }

    companion object {
        private const val MB = 1024L * 1024L

        /** Default max: min(1024 MB, 10% of free space), lower bound 64 MB (spec §16.1). */
        fun defaultMaxBytes(context: Context): Long {
            val free = context.filesDir.usableSpace
            val tenPercent = (free * 0.10).toLong()
            return max(64L * MB, min(1024L * MB, tenPercent))
        }
    }
}
