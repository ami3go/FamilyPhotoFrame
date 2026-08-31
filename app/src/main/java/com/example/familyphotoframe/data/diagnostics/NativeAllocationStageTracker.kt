package com.example.familyphotoframe.data.diagnostics

import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Aggregate attribution for native-heap changes across the slideshow's expensive stages.
 *
 * Android 5.x does not provide allocation stacks for ordinary support bundles. This tracker
 * therefore records bounded operation counts, lifetimes and process-native-heap deltas at real
 * stage boundaries. It never retains a Bitmap, request model, path or photo identifier.
 *
 * A delta is observational rather than exclusive: another thread may allocate while an operation
 * is active. The Phase 2B HIL modes deliberately remove most overlap, making the aggregate deltas
 * useful without adding per-photo logging or a profiler to production playback.
 */
class NativeAllocationStageTracker(
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nativeHeapBytes: () -> Long = { 0L },
) {
    enum class Stage {
        PHOTO_DECODE,
        BOUNDS_PROBE,
        CACHE_VERIFY,
        GENERATED_BITMAP,
        TRANSITION,
    }

    enum class Outcome { COMPLETED, FAILED, CANCELLED, TIMED_OUT }

    data class StageSnapshot(
        val started: Long = 0L,
        val completed: Long = 0L,
        val failed: Long = 0L,
        val cancelled: Long = 0L,
        val timedOut: Long = 0L,
        val active: Int = 0,
        val peakActive: Int = 0,
        val oldestActiveAgeMs: Long = 0L,
        val cumulativeDurationMs: Long = 0L,
        val maximumDurationMs: Long = 0L,
        val cumulativeNativeDeltaBytes: Long = 0L,
        val positiveNativeDeltaBytes: Long = 0L,
        val negativeNativeDeltaBytes: Long = 0L,
        val lastNativeDeltaBytes: Long = 0L,
        val trackingSaturated: Boolean = false,
    )

    data class Snapshot(
        val photoDecode: StageSnapshot = StageSnapshot(),
        val boundsProbe: StageSnapshot = StageSnapshot(),
        val cacheVerify: StageSnapshot = StageSnapshot(),
        val generatedBitmap: StageSnapshot = StageSnapshot(),
        val transition: StageSnapshot = StageSnapshot(),
    ) {
        operator fun get(stage: Stage): StageSnapshot = when (stage) {
            Stage.PHOTO_DECODE -> photoDecode
            Stage.BOUNDS_PROBE -> boundsProbe
            Stage.CACHE_VERIFY -> cacheVerify
            Stage.GENERATED_BITMAP -> generatedBitmap
            Stage.TRANSITION -> transition
        }
    }

    class Operation internal constructor(
        private val tracker: NativeAllocationStageTracker,
        private val stage: Stage,
        private val id: Long,
        private val startedElapsedMs: Long,
        private val startedNativeHeapBytes: Long,
    ) {
        private val finished = AtomicBoolean(false)

        fun finish(outcome: Outcome = Outcome.COMPLETED) {
            if (!finished.compareAndSet(false, true)) return
            tracker.finish(
                stage = stage,
                id = id,
                startedElapsedMs = startedElapsedMs,
                startedNativeHeapBytes = startedNativeHeapBytes,
                outcome = outcome,
            )
        }
    }

    private data class Bucket(
        val activeStartedAt: LinkedHashMap<Long, Long> = LinkedHashMap(),
        var started: Long = 0L,
        var completed: Long = 0L,
        var failed: Long = 0L,
        var cancelled: Long = 0L,
        var timedOut: Long = 0L,
        var activeCount: Int = 0,
        var peakActive: Int = 0,
        var cumulativeDurationMs: Long = 0L,
        var maximumDurationMs: Long = 0L,
        var cumulativeNativeDeltaBytes: Long = 0L,
        var positiveNativeDeltaBytes: Long = 0L,
        var negativeNativeDeltaBytes: Long = 0L,
        var lastNativeDeltaBytes: Long = 0L,
        var trackingSaturated: Boolean = false,
    )

    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val buckets = EnumMap<Stage, Bucket>(Stage::class.java).apply {
        Stage.entries.forEach { put(it, Bucket()) }
    }

    fun start(stage: Stage): Operation {
        val id = nextId.incrementAndGet()
        val startedAt = elapsedRealtimeMs().coerceAtLeast(0L)
        val nativeAtStart = runCatching(nativeHeapBytes).getOrDefault(0L).coerceAtLeast(0L)
        synchronized(lock) {
            val bucket = buckets.getValue(stage)
            bucket.started = saturatingIncrement(bucket.started)
            bucket.activeCount = saturatingIncrement(bucket.activeCount)
            bucket.peakActive = maxOf(bucket.peakActive, bucket.activeCount)
            if (bucket.activeStartedAt.size < MAX_TRACKED_ACTIVE_OPERATIONS) {
                bucket.activeStartedAt[id] = startedAt
            } else {
                bucket.trackingSaturated = true
            }
        }
        return Operation(this, stage, id, startedAt, nativeAtStart)
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        val now = elapsedRealtimeMs().coerceAtLeast(0L)
        Snapshot(
            photoDecode = snapshot(Stage.PHOTO_DECODE, now),
            boundsProbe = snapshot(Stage.BOUNDS_PROBE, now),
            cacheVerify = snapshot(Stage.CACHE_VERIFY, now),
            generatedBitmap = snapshot(Stage.GENERATED_BITMAP, now),
            transition = snapshot(Stage.TRANSITION, now),
        )
    }

    private fun finish(
        stage: Stage,
        id: Long,
        startedElapsedMs: Long,
        startedNativeHeapBytes: Long,
        outcome: Outcome,
    ) {
        val finishedAt = elapsedRealtimeMs().coerceAtLeast(0L)
        val nativeAtFinish = runCatching(nativeHeapBytes).getOrDefault(0L).coerceAtLeast(0L)
        val duration = (finishedAt - startedElapsedMs).coerceAtLeast(0L)
        val nativeDelta = signedDifference(nativeAtFinish, startedNativeHeapBytes)
        synchronized(lock) {
            val bucket = buckets.getValue(stage)
            bucket.activeStartedAt.remove(id)
            bucket.activeCount = (bucket.activeCount - 1).coerceAtLeast(0)
            when (outcome) {
                Outcome.COMPLETED -> bucket.completed = saturatingIncrement(bucket.completed)
                Outcome.FAILED -> bucket.failed = saturatingIncrement(bucket.failed)
                Outcome.CANCELLED -> bucket.cancelled = saturatingIncrement(bucket.cancelled)
                Outcome.TIMED_OUT -> bucket.timedOut = saturatingIncrement(bucket.timedOut)
            }
            bucket.cumulativeDurationMs = saturatingAddPositive(
                bucket.cumulativeDurationMs,
                duration,
            )
            bucket.maximumDurationMs = maxOf(bucket.maximumDurationMs, duration)
            bucket.cumulativeNativeDeltaBytes = saturatingAddSigned(
                bucket.cumulativeNativeDeltaBytes,
                nativeDelta,
            )
            if (nativeDelta >= 0L) {
                bucket.positiveNativeDeltaBytes = saturatingAddPositive(
                    bucket.positiveNativeDeltaBytes,
                    nativeDelta,
                )
            } else {
                bucket.negativeNativeDeltaBytes = saturatingAddPositive(
                    bucket.negativeNativeDeltaBytes,
                    if (nativeDelta == Long.MIN_VALUE) Long.MAX_VALUE else -nativeDelta,
                )
            }
            bucket.lastNativeDeltaBytes = nativeDelta
        }
    }

    private fun snapshot(stage: Stage, now: Long): StageSnapshot {
        val bucket = buckets.getValue(stage)
        val oldest = bucket.activeStartedAt.values.minOrNull()?.let {
            (now - it).coerceAtLeast(0L)
        } ?: 0L
        return StageSnapshot(
            started = bucket.started,
            completed = bucket.completed,
            failed = bucket.failed,
            cancelled = bucket.cancelled,
            timedOut = bucket.timedOut,
            active = bucket.activeCount,
            peakActive = bucket.peakActive,
            oldestActiveAgeMs = oldest,
            cumulativeDurationMs = bucket.cumulativeDurationMs,
            maximumDurationMs = bucket.maximumDurationMs,
            cumulativeNativeDeltaBytes = bucket.cumulativeNativeDeltaBytes,
            positiveNativeDeltaBytes = bucket.positiveNativeDeltaBytes,
            negativeNativeDeltaBytes = bucket.negativeNativeDeltaBytes,
            lastNativeDeltaBytes = bucket.lastNativeDeltaBytes,
            trackingSaturated = bucket.trackingSaturated,
        )
    }

    private fun signedDifference(end: Long, start: Long): Long = when {
        end >= start -> (end - start).coerceAtMost(Long.MAX_VALUE)
        else -> -((start - end).coerceAtMost(Long.MAX_VALUE))
    }

    private fun saturatingIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) value else value + 1L

    private fun saturatingIncrement(value: Int): Int =
        if (value == Int.MAX_VALUE) value else value + 1

    private fun saturatingAddPositive(first: Long, second: Long): Long {
        val safe = second.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - first < safe) Long.MAX_VALUE else first + safe
    }

    private fun saturatingAddSigned(first: Long, second: Long): Long = when {
        second > 0L && first > Long.MAX_VALUE - second -> Long.MAX_VALUE
        second < 0L && first < Long.MIN_VALUE - second -> Long.MIN_VALUE
        else -> first + second
    }

    private companion object {
        const val MAX_TRACKED_ACTIVE_OPERATIONS = 512
    }
}
