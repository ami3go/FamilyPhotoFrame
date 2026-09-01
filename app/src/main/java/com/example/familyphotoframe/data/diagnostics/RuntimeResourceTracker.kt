package com.example.familyphotoframe.data.diagnostics

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide ownership counters for resources whose native/provider allocations are
 * otherwise invisible to the Java heap sampler.
 *
 * A lease is idempotent and must follow the lifetime of the owned resource. The tracker
 * retains only one timestamp per currently active resource; completed operations leave
 * counters, not historical objects, behind.
 */
class RuntimeResourceTracker(
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    enum class Kind { SMB_CONTEXT, SMB_STREAM, MEDIA_TRANSFER }

    /** Privacy-safe reason for an SMB stream; never contains a path or photo identity. */
    enum class SmbStreamPurpose {
        DISPLAY_CACHE,
        COLLAGE_BOUNDS,
        EXIF_METADATA,
        CONTENT_HASH,
        INDEX_METADATA,
        OTHER,
    }

    data class Snapshot(
        val activeSmbContexts: Int = 0,
        val peakSmbContexts: Int = 0,
        val smbContextsCreated: Long = 0L,
        val smbContextsClosed: Long = 0L,
        val oldestSmbContextAgeMs: Long = 0L,
        val smbContextTrackingSaturated: Boolean = false,
        val activeSmbStreams: Int = 0,
        val peakSmbStreams: Int = 0,
        val smbStreamsOpened: Long = 0L,
        val smbStreamsClosed: Long = 0L,
        val oldestSmbStreamAgeMs: Long = 0L,
        val oldestSmbStreamPurpose: SmbStreamPurpose = SmbStreamPurpose.OTHER,
        val oldestSmbStreamDeadlineMs: Long = 0L,
        val overdueSmbStreams: Int = 0,
        val smbStreamDeadlineExpirations: Long = 0L,
        val smbTrackingSaturated: Boolean = false,
        val activeMediaTransfers: Int = 0,
        val peakMediaTransfers: Int = 0,
        val mediaTransfersStarted: Long = 0L,
        val mediaTransfersFinished: Long = 0L,
        val oldestMediaTransferAgeMs: Long = 0L,
        val mediaTrackingSaturated: Boolean = false,
        val contentHashYieldsToMediaTransfers: Long = 0L,
    )

    class Lease internal constructor(
        private val release: () -> Unit,
        private val expire: () -> Unit = {},
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        private val expired = AtomicBoolean(false)

        /** Records the deadline exactly once; closing remains the stream owner's job. */
        fun markDeadlineExpired() {
            if (expired.compareAndSet(false, true)) expire()
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) release()
        }
    }

    private data class ActiveResource(
        val startedAtElapsedMs: Long,
        val purpose: SmbStreamPurpose = SmbStreamPurpose.OTHER,
        val deadlineMs: Long = 0L,
    )

    private data class Bucket(
        val active: LinkedHashMap<Long, ActiveResource> = LinkedHashMap(),
        var activeCount: Int = 0,
        var peak: Int = 0,
        var started: Long = 0L,
        var finished: Long = 0L,
        var deadlineExpirations: Long = 0L,
        var trackingSaturated: Boolean = false,
    )

    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val smbContexts = Bucket()
    private val smbStreams = Bucket()
    private val mediaTransfers = Bucket()
    private var contentHashYieldsToMediaTransfers = 0L

    fun openSmbContext(): Lease = acquire(Kind.SMB_CONTEXT)

    fun openSmbStream(
        purpose: SmbStreamPurpose = SmbStreamPurpose.OTHER,
        deadlineMs: Long = 0L,
    ): Lease = acquire(Kind.SMB_STREAM, purpose, deadlineMs)

    fun startMediaTransfer(): Lease = acquire(Kind.MEDIA_TRANSFER)

    /** Fast, privacy-safe priority signal used by low-priority content hashing. */
    fun hasActiveMediaTransfer(): Boolean = synchronized(lock) {
        mediaTransfers.activeCount > 0
    }

    /** Counts hash reads preempted so selected media can use the SMB transport first. */
    fun recordContentHashYieldToMediaTransfer() = synchronized(lock) {
        contentHashYieldsToMediaTransfers = saturatingIncrement(contentHashYieldsToMediaTransfers)
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        val now = elapsedRealtimeMs()
        Snapshot(
            activeSmbContexts = smbContexts.activeCount,
            peakSmbContexts = smbContexts.peak,
            smbContextsCreated = smbContexts.started,
            smbContextsClosed = smbContexts.finished,
            oldestSmbContextAgeMs = oldestAge(smbContexts, now),
            smbContextTrackingSaturated = smbContexts.trackingSaturated,
            activeSmbStreams = smbStreams.activeCount,
            peakSmbStreams = smbStreams.peak,
            smbStreamsOpened = smbStreams.started,
            smbStreamsClosed = smbStreams.finished,
            oldestSmbStreamAgeMs = oldestAge(smbStreams, now),
            oldestSmbStreamPurpose = oldest(smbStreams)?.purpose ?: SmbStreamPurpose.OTHER,
            oldestSmbStreamDeadlineMs = oldest(smbStreams)?.deadlineMs ?: 0L,
            overdueSmbStreams = overdueCount(smbStreams, now),
            smbStreamDeadlineExpirations = smbStreams.deadlineExpirations,
            smbTrackingSaturated = smbStreams.trackingSaturated,
            activeMediaTransfers = mediaTransfers.activeCount,
            peakMediaTransfers = mediaTransfers.peak,
            mediaTransfersStarted = mediaTransfers.started,
            mediaTransfersFinished = mediaTransfers.finished,
            oldestMediaTransferAgeMs = oldestAge(mediaTransfers, now),
            mediaTrackingSaturated = mediaTransfers.trackingSaturated,
            contentHashYieldsToMediaTransfers = contentHashYieldsToMediaTransfers,
        )
    }

    private fun acquire(
        kind: Kind,
        purpose: SmbStreamPurpose = SmbStreamPurpose.OTHER,
        deadlineMs: Long = 0L,
    ): Lease {
        val id = nextId.incrementAndGet()
        synchronized(lock) {
            val bucket = bucket(kind)
            bucket.activeCount = if (bucket.activeCount == Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                bucket.activeCount + 1
            }
            if (bucket.active.size < MAX_TRACKED_ACTIVE_RESOURCES) {
                bucket.active[id] = ActiveResource(
                    startedAtElapsedMs = elapsedRealtimeMs(),
                    purpose = if (kind == Kind.SMB_STREAM) purpose else SmbStreamPurpose.OTHER,
                    deadlineMs = if (kind == Kind.SMB_STREAM) deadlineMs.coerceAtLeast(0L) else 0L,
                )
            } else {
                bucket.trackingSaturated = true
            }
            bucket.started = saturatingIncrement(bucket.started)
            bucket.peak = maxOf(bucket.peak, bucket.activeCount)
        }
        return Lease(
            release = { release(kind, id) },
            expire = { markDeadlineExpired(kind) },
        )
    }

    private fun release(kind: Kind, id: Long) = synchronized(lock) {
        val bucket = bucket(kind)
        bucket.active.remove(id)
        bucket.activeCount = (bucket.activeCount - 1).coerceAtLeast(0)
        bucket.finished = saturatingIncrement(bucket.finished)
    }

    private fun bucket(kind: Kind): Bucket = when (kind) {
        Kind.SMB_CONTEXT -> smbContexts
        Kind.SMB_STREAM -> smbStreams
        Kind.MEDIA_TRANSFER -> mediaTransfers
    }

    private fun oldest(bucket: Bucket): ActiveResource? =
        bucket.active.values.minByOrNull { it.startedAtElapsedMs }

    private fun oldestAge(bucket: Bucket, now: Long): Long =
        oldest(bucket)?.let { (now - it.startedAtElapsedMs).coerceAtLeast(0L) } ?: 0L

    private fun overdueCount(bucket: Bucket, now: Long): Int = bucket.active.values.count { active ->
        active.deadlineMs > 0L && now - active.startedAtElapsedMs >= active.deadlineMs
    }

    private fun markDeadlineExpired(kind: Kind) = synchronized(lock) {
        val bucket = bucket(kind)
        bucket.deadlineExpirations = saturatingIncrement(bucket.deadlineExpirations)
    }

    private fun saturatingIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L

    private companion object {
        const val MAX_TRACKED_ACTIVE_RESOURCES = 1_024
    }
}
