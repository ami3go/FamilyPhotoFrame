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
        val smbTrackingSaturated: Boolean = false,
        val activeMediaTransfers: Int = 0,
        val peakMediaTransfers: Int = 0,
        val mediaTransfersStarted: Long = 0L,
        val mediaTransfersFinished: Long = 0L,
        val oldestMediaTransferAgeMs: Long = 0L,
        val mediaTrackingSaturated: Boolean = false,
    )

    class Lease internal constructor(
        private val release: () -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) release()
        }
    }

    private data class Bucket(
        val active: LinkedHashMap<Long, Long> = LinkedHashMap(),
        var activeCount: Int = 0,
        var peak: Int = 0,
        var started: Long = 0L,
        var finished: Long = 0L,
        var trackingSaturated: Boolean = false,
    )

    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val smbContexts = Bucket()
    private val smbStreams = Bucket()
    private val mediaTransfers = Bucket()

    fun openSmbContext(): Lease = acquire(Kind.SMB_CONTEXT)

    fun openSmbStream(): Lease = acquire(Kind.SMB_STREAM)

    fun startMediaTransfer(): Lease = acquire(Kind.MEDIA_TRANSFER)

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
            smbTrackingSaturated = smbStreams.trackingSaturated,
            activeMediaTransfers = mediaTransfers.activeCount,
            peakMediaTransfers = mediaTransfers.peak,
            mediaTransfersStarted = mediaTransfers.started,
            mediaTransfersFinished = mediaTransfers.finished,
            oldestMediaTransferAgeMs = oldestAge(mediaTransfers, now),
            mediaTrackingSaturated = mediaTransfers.trackingSaturated,
        )
    }

    private fun acquire(kind: Kind): Lease {
        val id = nextId.incrementAndGet()
        synchronized(lock) {
            val bucket = bucket(kind)
            bucket.activeCount = if (bucket.activeCount == Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                bucket.activeCount + 1
            }
            if (bucket.active.size < MAX_TRACKED_ACTIVE_RESOURCES) {
                bucket.active[id] = elapsedRealtimeMs()
            } else {
                bucket.trackingSaturated = true
            }
            bucket.started = saturatingIncrement(bucket.started)
            bucket.peak = maxOf(bucket.peak, bucket.activeCount)
        }
        return Lease { release(kind, id) }
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

    private fun oldestAge(bucket: Bucket, now: Long): Long =
        bucket.active.values.minOrNull()?.let { (now - it).coerceAtLeast(0L) } ?: 0L

    private fun saturatingIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L

    private companion object {
        const val MAX_TRACKED_ACTIVE_RESOURCES = 1_024
    }
}
