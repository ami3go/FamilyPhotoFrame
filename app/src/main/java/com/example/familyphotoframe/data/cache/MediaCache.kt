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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
    private val transferCoordinator: RemoteTransferCoordinator = RemoteTransferCoordinator(),
    /** Max cache size in bytes; defaults to spec §16.1 formula. */
    private val maxBytesProvider: (suspend () -> Long)? = null,
) {
    /** The slice of [com.example.familyphotoframe.data.db.PhotoDao] this cache may write. */
    interface PhotoCacheIndexWriter {
        suspend fun setCacheKey(stableId: String, cacheKey: String?)
        suspend fun clearCacheKey(cacheKey: String)
        suspend fun clearAllCacheKeys()
        suspend fun needsContentHash(stableId: String): Boolean = false
        suspend fun setContentHash(stableId: String, sha256: String, scannedAtEpochMs: Long) {}
    }

    enum class FailureStage {
        CACHE_LOOKUP,
        SOURCE_READ,
        VERIFY_DECODE,
        CACHE_COMMIT,
        UNKNOWN,
    }

    /**
     * Fine-grained progress markers for a single cache resolution.
     *
     * The slideshow keeps these only in its bounded per-presentation trace.  They are
     * deliberately callbacks instead of durable events: ordinary cache traffic must not
     * rotate a diagnostic bundle, while a watchdog timeout still needs to identify the
     * blocking boundary that was reached last.
     */
    enum class ResolveStage {
        RECONCILIATION_LOCK_WAIT,
        RECONCILIATION,
        KEY_LOCK_WAIT,
        CACHE_LOOKUP,
        TRANSFER_SLOT_WAIT,
        STREAM_OPEN,
        TRANSFER_COPY,
        VERIFY_DECODE,
        CACHE_COMMIT,
        EVICTION,
    }

    /**
     * Bounded, privacy-safe transfer evidence retained by the selected presentation
     * trace.  It is intentionally not a durable event per read: the timeout/cancellation
     * event carries the final snapshot without consuming the diagnostic bulk budget.
     */
    enum class TransferTelemetryState {
        STARTED,
        PROGRESS,
        SELECTED_DEADLINE,
        STREAM_CLOSE_REQUESTED,
        TRANSFER_SLOT_RELEASED,
    }

    data class TransferTelemetry(
        val state: TransferTelemetryState,
        val copiedBytes: Long,
        val expectedBytes: Long,
        val deadlineMs: Long,
        val streamCloseSucceeded: Boolean? = null,
    )

    sealed interface ResolveResult {
        data class Ready(val file: File, val cacheHit: Boolean) : ResolveResult
        /** Transfer made bounded progress and can safely continue from [copiedBytes]. */
        data class Deferred(
            val copiedBytes: Long,
            val expectedBytes: Long,
            val reason: String,
            /** True only when this attempt appended at least one new byte. */
            val progressed: Boolean,
        ) : ResolveResult
        data class Failed(
            val stage: FailureStage,
            val exceptionClass: String? = null,
            /** True only for connection/auth/session failures affecting the whole source. */
            val sourceLevelFailure: Boolean = false,
        ) : ResolveResult
    }

    private val dir: File = File(context.filesDir, "mediacache").apply { mkdirs() }
    private val maintenanceScope = CoroutineScope(SupervisorJob() + io)
    private val activePartialNames = ConcurrentHashMap.newKeySet<String>()
    private val contentHashInFlight = ConcurrentHashMap.newKeySet<String>()
    private val mutableContentHashUpdates = MutableSharedFlow<String>(
        extraBufferCapacity = CONTENT_HASH_UPDATE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Stable ids whose missing hash was persisted from already-verified local bytes. */
    val contentHashUpdates: SharedFlow<String> = mutableContentHashUpdates.asSharedFlow()
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
        priority: MediaTransferPriority = MediaTransferPriority.BACKGROUND_PRELOAD,
        /** Absolute monotonic selected-presentation deadline, or the priority default. */
        transferDeadlineMonotonicMs: Long? = null,
        onStage: (ResolveStage) -> Unit = {},
        onTransferTelemetry: (TransferTelemetry) -> Unit = {},
    ): ResolveResult = withContext(io) {
        val key = keyFor(item)
        try {
            ensureReconciled(onStage)
            withKeyLock(key, onWait = { onStage(ResolveStage.KEY_LOCK_WAIT) }) {
                onStage(ResolveStage.CACHE_LOOKUP)
                val cached = maintenanceLock.withLock {
                    val existing = dao.get(key)
                    if (existing != null) {
                        val f = File(existing.localFilePathPrivate)
                        if (f.exists() && existing.verifiedDecodeOk) {
                            dao.touch(key, System.currentTimeMillis())
                            mirrorCacheKey(item.stableId, key)
                            scheduleLocalContentHash(item, f)
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
                onStage(ResolveStage.TRANSFER_SLOT_WAIT)
                when (val downloaded = withTransferPermit(
                    item = item,
                    priority = priority,
                    transferDeadlineMonotonicMs = transferDeadlineMonotonicMs,
                    onTransferTelemetry = onTransferTelemetry,
                ) { effectiveTransferDeadlineMs ->
                    transferCoordinator.withPermit(
                        sourceId = source.id.value,
                        priority = MediaTransferPolicy.remotePriority(priority),
                    ) { permit ->
                        resourceTracker.startMediaTransfer().use {
                            download(
                                item = item,
                                source = source,
                                key = key,
                                generation = generation,
                                priority = priority,
                                transferDeadlineMs = effectiveTransferDeadlineMs,
                                shouldYield = permit::shouldYield,
                                onStage = onStage,
                                onTransferTelemetry = onTransferTelemetry,
                            )
                        }
                    }
                }) {
                    is ResolveResult.Ready -> {
                        onStage(ResolveStage.EVICTION)
                        evictIfNeeded(protectedKeys + key)
                        downloaded
                    }
                    is ResolveResult.Deferred -> downloaded
                    is ResolveResult.Failed -> downloaded
                }
            }
        } catch (deadline: SelectedTransferPermitDeadlineException) {
            ResolveResult.Deferred(
                copiedBytes = 0L,
                expectedBytes = item.sizeBytes.coerceAtLeast(0L),
                reason = "transfer_slot_deadline",
                progressed = false,
            )
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
    suspend fun resolveIfCached(
        item: PhotoItem,
        onStage: (ResolveStage) -> Unit = {},
    ): ResolveResult = withContext(io) {
        val key = keyFor(item)
        try {
            ensureReconciled(onStage)
            withKeyLock(key, onWait = { onStage(ResolveStage.KEY_LOCK_WAIT) }) {
                onStage(ResolveStage.CACHE_LOOKUP)
                maintenanceLock.withLock {
                    val existing = dao.get(key)
                        ?: return@withLock ResolveResult.Failed(FailureStage.CACHE_LOOKUP)
                    val f = File(existing.localFilePathPrivate)
                    if (f.exists() && existing.verifiedDecodeOk) {
                        dao.touch(key, System.currentTimeMillis())
                        mirrorCacheKey(item.stableId, key)
                        scheduleLocalContentHash(item, f)
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
    private suspend fun <T> withKeyLock(
        key: String,
        onWait: () -> Unit = {},
        block: suspend () -> T,
    ): T {
        val holder = synchronized(keyLocksGuard) {
            keyLocks.getOrPut(key, ::KeyLock).also { it.users++ }
        }
        var locked = false
        return try {
            onWait()
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
        priority: MediaTransferPriority,
        transferDeadlineMs: Long,
        shouldYield: () -> Boolean,
        onStage: (ResolveStage) -> Unit,
        onTransferTelemetry: (TransferTelemetry) -> Unit,
    ): ResolveResult {
        val target = File(dir, key)
        // The per-key mutex makes one deterministic partial safe. Its key already binds
        // source, path, size and mtime, so changed remote content cannot reuse old bytes.
        val tmp = File(dir, "$key.part")
        var stage = FailureStage.SOURCE_READ
        var targetCommitted = false
        var indexCommitted = false
        var copiedBytes = 0L
        var initialCopiedBytes = 0L
        val expectedBytes = item.sizeBytes.coerceAtLeast(0L)
        lateinit var progress: TransferProgress
        activePartialNames += tmp.name
        return try {
            prunePartials(tmp.name)
            copiedBytes = validatedPartialLength(tmp, item.sizeBytes)
            initialCopiedBytes = copiedBytes
            progress = TransferProgress(copiedBytes)
            if (dir.usableSpace <= RESERVED_FREE_BYTES) {
                throw CacheStorageReserveException(RESERVED_FREE_BYTES)
            }
            if (expectedBytes <= 0L && copiedBytes > 0L) {
                tmp.delete()
                copiedBytes = 0L
            }
            if (expectedBytes == 0L || copiedBytes < expectedBytes) runWithTransferBudget(
                priority = priority,
                softDeadlineMs = transferDeadlineMs,
                progress = progress,
                expectedBytes = expectedBytes,
            ) {
                onStage(ResolveStage.STREAM_OPEN)
                val options = OpenOptions(
                    // The source-level deadline stays at the established limit. A selected
                    // presentation uses the earlier cache budget without demoting the source.
                    timeoutMs = MediaTransferPolicy.BACKGROUND_PRELOAD_DEADLINE_MS,
                    preferOriginal = true,
                    purpose = OpenPurpose.DISPLAY_CACHE,
                )
                var input = source.openStreamFrom(item, copiedBytes, options)
                if (input == null) {
                    tmp.delete()
                    copiedBytes = 0L
                    input = source.openStream(item, options)
                }
                input.use {
                    stage = FailureStage.SOURCE_READ
                    onStage(ResolveStage.TRANSFER_COPY)
                    onTransferTelemetry(
                        TransferTelemetry(
                            state = TransferTelemetryState.STARTED,
                            copiedBytes = copiedBytes,
                            expectedBytes = expectedBytes,
                            deadlineMs = transferDeadlineMs,
                        )
                    )
                    FileOutputStream(tmp, copiedBytes > 0L).use { out ->
                        it.copyToCancellable(
                            output = out,
                            maxBytes = MAX_ENTRY_BYTES,
                            minimumUsableBytes = RESERVED_FREE_BYTES,
                            usableBytes = { dir.usableSpace },
                            bufferSize = MediaTransferPolicy.REMOTE_COPY_BUFFER_BYTES,
                            initialBytes = copiedBytes,
                            shouldYield = shouldYield,
                            onProgress = { copied ->
                                copiedBytes = copied
                                progress.record(copied)
                                onTransferTelemetry(
                                    TransferTelemetry(
                                        state = TransferTelemetryState.PROGRESS,
                                        copiedBytes = copied,
                                        expectedBytes = expectedBytes,
                                        deadlineMs = transferDeadlineMs,
                                    )
                                )
                            },
                            onCancellationClose = { copied, closeSucceeded ->
                                copiedBytes = copied
                                onTransferTelemetry(
                                    TransferTelemetry(
                                        state = TransferTelemetryState.STREAM_CLOSE_REQUESTED,
                                        copiedBytes = copied,
                                        expectedBytes = expectedBytes,
                                        deadlineMs = transferDeadlineMs,
                                        streamCloseSucceeded = closeSucceeded,
                                    )
                                )
                            },
                        )
                        out.fd.sync()
                    }
                }
            }
            if (expectedBytes > 0L && copiedBytes != expectedBytes) {
                throw java.io.EOFException("remote item length did not match indexed size")
            }
            stage = FailureStage.VERIFY_DECODE
            onStage(ResolveStage.VERIFY_DECODE)
            if (!decodes(tmp)) {
                tmp.delete()
                return ResolveResult.Failed(FailureStage.VERIFY_DECODE)
            }
            stage = FailureStage.CACHE_COMMIT
            onStage(ResolveStage.CACHE_COMMIT)
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
            scheduleLocalContentHash(item, target)
            ResolveResult.Ready(target, cacheHit = false)
        } catch (deadline: SelectedTransferDeadlineException) {
            onTransferTelemetry(
                TransferTelemetry(
                    state = TransferTelemetryState.SELECTED_DEADLINE,
                    copiedBytes = copiedBytes,
                    expectedBytes = expectedBytes,
                    deadlineMs = transferDeadlineMs,
                )
            )
            val retained = retainPartial(tmp, copiedBytes, expectedBytes)
            if (targetCommitted && !indexCommitted) target.delete()
            if (retained) {
                ResolveResult.Deferred(
                    copiedBytes,
                    expectedBytes,
                    "selected_deadline",
                    progressed = copiedBytes > initialCopiedBytes,
                )
            } else {
                ResolveResult.Failed(FailureStage.SOURCE_READ, "SelectedTransferDeadline")
            }
        } catch (c: CancellationException) {
            if (c is TimeoutCancellationException &&
                priority == MediaTransferPriority.SELECTED_PRESENTATION
            ) {
                onTransferTelemetry(
                    TransferTelemetry(
                        state = TransferTelemetryState.SELECTED_DEADLINE,
                        copiedBytes = copiedBytes,
                        expectedBytes = expectedBytes,
                        deadlineMs = transferDeadlineMs,
                    )
                )
            }
            val retained = retainPartial(tmp, copiedBytes, expectedBytes)
            if (targetCommitted && !indexCommitted) target.delete()
            if (c is TimeoutCancellationException &&
                priority == MediaTransferPriority.SELECTED_PRESENTATION && retained
            ) {
                return ResolveResult.Deferred(
                    copiedBytes,
                    expectedBytes,
                    "selected_deadline",
                    progressed = copiedBytes > initialCopiedBytes,
                )
            }
            if (c is TimeoutCancellationException &&
                priority == MediaTransferPriority.PARTIAL_RESUME && retained
            ) {
                return ResolveResult.Deferred(
                    copiedBytes,
                    expectedBytes,
                    "resume_slice_deadline",
                    progressed = copiedBytes > initialCopiedBytes,
                )
            }
            if (c is RemoteTransferCoordinator.YieldException && retained) {
                return ResolveResult.Deferred(
                    copiedBytes,
                    expectedBytes,
                    "higher_priority_waiting",
                    progressed = copiedBytes > initialCopiedBytes,
                )
            }
            throw c
        } catch (e: Exception) {
            val retained = stage == FailureStage.SOURCE_READ &&
                retainPartial(tmp, copiedBytes, expectedBytes)
            if (!retained) tmp.delete()
            if (targetCommitted && !indexCommitted) target.delete()
            ResolveResult.Failed(
                stage,
                e.javaClass.simpleName,
                sourceLevelFailure = stage == FailureStage.SOURCE_READ && isSourceLevelFailure(e),
            )
        } finally {
            activePartialNames -= tmp.name
        }
    }

    private class SelectedTransferDeadlineException : java.io.IOException("selected_transfer_deadline")
    private class SelectedTransferPermitDeadlineException :
        java.io.IOException("selected_transfer_slot_deadline")

    private class TransferProgress(initialBytes: Long) {
        private var previousBytes = initialBytes
        private var previousAtMs = System.nanoTime() / 1_000_000L
        private var latestBytes = initialBytes
        private var latestAtMs = previousAtMs

        @Synchronized fun record(bytes: Long) {
            val now = System.nanoTime() / 1_000_000L
            if (bytes > latestBytes) {
                previousBytes = latestBytes
                previousAtMs = latestAtMs
                latestBytes = bytes
                latestAtMs = now
            }
        }

        @Synchronized fun extensionMs(expectedBytes: Long): Long {
            val elapsed = (latestAtMs - previousAtMs).coerceAtLeast(1L)
            val bytesPerSecond = ((latestBytes - previousBytes).coerceAtLeast(0L) * 1_000L) / elapsed
            return SelectedTransferDeadlinePolicy.extensionMs(
                copiedBytes = latestBytes,
                expectedBytes = expectedBytes,
                lastProgressAgeMs = System.nanoTime() / 1_000_000L - latestAtMs,
                recentBytesPerSecond = bytesPerSecond,
            )
        }
    }

    private suspend fun runWithTransferBudget(
        priority: MediaTransferPriority,
        softDeadlineMs: Long,
        progress: TransferProgress,
        expectedBytes: Long,
        block: suspend () -> Unit,
    ) {
        if (priority != MediaTransferPriority.SELECTED_PRESENTATION) {
            withTimeout(softDeadlineMs) { block() }
            return
        }
        coroutineScope {
            val operation = async { block() }
            try {
                val completedAtSoftBoundary = withTimeoutOrNull(softDeadlineMs) {
                    operation.await()
                    true
                } == true
                if (completedAtSoftBoundary) return@coroutineScope
                val extensionMs = progress.extensionMs(expectedBytes)
                val completedDuringExtension = extensionMs > 0L &&
                    withTimeoutOrNull(extensionMs) {
                        operation.await()
                        true
                    } == true
                if (!completedDuringExtension) {
                    operation.cancelAndJoin()
                    throw SelectedTransferDeadlineException()
                }
            } finally {
                if (!operation.isCompleted) operation.cancelAndJoin()
            }
        }
    }

    private fun validatedPartialLength(file: File, expectedBytes: Long): Long {
        if (!file.isFile) return 0L
        val length = file.length()
        val valid = expectedBytes > 0L && length in 1L..expectedBytes &&
            length <= MAX_ENTRY_BYTES &&
            System.currentTimeMillis() - file.lastModified() <= PARTIAL_TTL_MS
        if (!valid) {
            file.delete()
            return 0L
        }
        return length
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(MediaTransferPolicy.REMOTE_COPY_BUFFER_BYTES).use { input ->
            val buffer = ByteArray(MediaTransferPolicy.REMOTE_COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    /** Hash verified app-owned cache bytes without opening the remote source again. */
    private fun scheduleLocalContentHash(
        item: PhotoItem,
        file: File,
    ) {
        val index = photoIndex ?: return
        if (!file.isFile || !contentHashInFlight.add(item.stableId)) return
        maintenanceScope.launch {
            try {
                if (!index.needsContentHash(item.stableId)) return@launch
                index.setContentHash(
                    item.stableId,
                    sha256(file),
                    System.currentTimeMillis(),
                )
                mutableContentHashUpdates.tryEmit(item.stableId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Playback does not depend on the optional hash; a later cache hit retries.
            } finally {
                contentHashInFlight.remove(item.stableId)
            }
        }
    }

    private fun retainPartial(file: File, copiedBytes: Long, expectedBytes: Long): Boolean {
        val valid = expectedBytes > 0L && copiedBytes in 1 until expectedBytes &&
            file.isFile && file.length() == copiedBytes && copiedBytes <= MAX_ENTRY_BYTES
        if (valid) file.setLastModified(System.currentTimeMillis()) else file.delete()
        return valid
    }

    private suspend fun prunePartials(currentName: String) = maintenanceLock.withLock {
        val now = System.currentTimeMillis()
        val candidates = dir.listFiles().orEmpty()
            .filter { file ->
                file.name.endsWith(PartialCachePolicy.FILE_SUFFIX) &&
                    (file.name == currentName || file.name !in activePartialNames)
            }
        candidates.filter { file ->
            !PartialCachePolicy.ownsFileName(file.name) || file.length() !in 1L..MAX_ENTRY_BYTES ||
                now - file.lastModified() > PARTIAL_TTL_MS
        }.forEach(File::delete)

        val committedBytes = dao.totalSizeBytes()
        val cacheMax = (maxBytesProvider?.invoke() ?: defaultMaxBytes(dir, committedBytes))
            .coerceAtLeast(0L)
        val partialMax = min(MAX_PARTIAL_BYTES, cacheMax / 4L)
        val retained = candidates.filter(File::isFile).sortedByDescending(File::lastModified)
        var kept = 0L
        retained.forEach { file ->
            val size = file.length().coerceAtLeast(0L)
            if (kept > partialMax - size) file.delete() else kept += size
        }
    }

    /**
     * Emits slot-release evidence only after the semaphore has actually been returned.
     * A waiting selected presentation remains identified by [ResolveStage.TRANSFER_SLOT_WAIT]
     * rather than producing a misleading release record before it acquired a slot.
     */
    private suspend fun <T> withTransferPermit(
        item: PhotoItem,
        priority: MediaTransferPriority,
        transferDeadlineMonotonicMs: Long?,
        onTransferTelemetry: (TransferTelemetry) -> Unit,
        block: suspend (transferDeadlineMs: Long) -> T,
    ): T {
        val selectedDeadlineMs = transferDeadlineMonotonicMs?.let { deadline ->
            (deadline - monotonicNowMs()).coerceAtLeast(1L)
        }
        var permitAcquired = false
        if (priority == MediaTransferPriority.SELECTED_PRESENTATION && selectedDeadlineMs != null) {
            try {
                withTimeout(selectedDeadlineMs) {
                    transferSlots.acquire()
                    permitAcquired = true
                }
            } catch (timeout: TimeoutCancellationException) {
                if (permitAcquired) {
                    transferSlots.release()
                    permitAcquired = false
                }
                onTransferTelemetry(
                    TransferTelemetry(
                        state = TransferTelemetryState.SELECTED_DEADLINE,
                        copiedBytes = 0L,
                        expectedBytes = item.sizeBytes.coerceAtLeast(0L),
                        deadlineMs = selectedDeadlineMs,
                    )
                )
                throw SelectedTransferPermitDeadlineException()
            }
        } else {
            transferSlots.acquire()
            permitAcquired = true
        }
        val transferDeadlineMs = transferDeadlineMonotonicMs?.let { deadline ->
            (deadline - monotonicNowMs()).coerceAtLeast(1L)
        } ?: MediaTransferPolicy.deadlineMs(priority)
        return try {
            block(transferDeadlineMs)
        } finally {
            if (permitAcquired) {
                transferSlots.release()
                onTransferTelemetry(
                    TransferTelemetry(
                        state = TransferTelemetryState.TRANSFER_SLOT_RELEASED,
                        copiedBytes = 0L,
                        expectedBytes = item.sizeBytes.coerceAtLeast(0L),
                        deadlineMs = transferDeadlineMs,
                    )
                )
            }
        }
    }

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

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
    private suspend fun ensureReconciled(onStage: (ResolveStage) -> Unit = {}) {
        if (reconciled) return
        onStage(ResolveStage.RECONCILIATION_LOCK_WAIT)
        reconcileLock.withLock {
            if (reconciled) return
            onStage(ResolveStage.RECONCILIATION)
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
                    if (file.name.endsWith(PartialCachePolicy.FILE_SUFFIX)) {
                        val validName = PartialCachePolicy.ownsFileName(file.name)
                        val validAge = System.currentTimeMillis() - file.lastModified() <= PARTIAL_TTL_MS
                        if (!validName || !validAge || file.length() !in 1L..MAX_ENTRY_BYTES) file.delete()
                        return@forEach
                    }
                    val indexed = dao.get(file.name)
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
        private const val CONTENT_HASH_UPDATE_BUFFER = 64
        private const val EMPTY_PROTECTED_SENTINEL = "__never_a_cache_key__"
        private const val MAX_ENTRY_BYTES = 256L * MB
        private const val RESERVED_FREE_BYTES = 512L * MB
        private const val PARTIAL_TTL_MS = 7L * 24L * 60L * 60L * 1_000L
        private const val MAX_PARTIAL_BYTES = 256L * MB
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
