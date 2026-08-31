package com.example.familyphotoframe.data.diagnostics

import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * Bounded long-horizon native-PSS trend used for soak/HIL attribution.
 *
 * This is intentionally separate from [com.example.familyphotoframe.domain.engine.PlaybackMemoryPolicy]:
 * the policy keeps its short windows for fast protection, while this tracker answers the different
 * Phase 4 question: is native memory retaining over hours after warm-up?
 *
 * Samples contain only process-level counters and timestamps. No Bitmap, path, request, or photo
 * identity is retained. Linear regression is used instead of operation-local before/after deltas,
 * which are easily contaminated by concurrent allocator activity and delayed native frees.
 */
class NativeMemoryTrendTracker(
    private val minObservationMs: Long = DEFAULT_MIN_OBSERVATION_MS,
    private val maxObservationMs: Long = DEFAULT_MAX_OBSERVATION_MS,
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES,
) {
    init {
        require(minObservationMs > 0L)
        require(maxObservationMs >= minObservationMs)
        require(maxSamples >= 2)
    }

    data class Trend(
        val ready: Boolean = false,
        val sampleCount: Int = 0,
        val observationMs: Long = 0L,
        val baselinePssKb: Long = 0L,
        val currentPssKb: Long = 0L,
        val growthKb: Long = 0L,
        val rateKbPerMin: Int = 0,
    )

    private data class Sample(
        val elapsedMs: Long,
        val nativePssKb: Long,
    )

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun record(elapsedMs: Long, nativePssKb: Long): Trend {
        val now = elapsedMs.coerceAtLeast(0L)
        val pss = nativePssKb.coerceAtLeast(0L)
        if (pss == 0L) return currentTrend()

        val previous = samples.peekLast()
        if (previous != null && now < previous.elapsedMs) {
            // elapsedRealtime should not go backwards inside one process, but injected/test clocks
            // and wall-clock based callers can. Never calculate a slope across a clock reset.
            samples.clear()
        }

        samples.addLast(Sample(now, pss))
        prune(now)
        return currentTrend()
    }

    @Synchronized
    fun currentTrend(): Trend {
        val first = samples.peekFirst() ?: return Trend()
        val last = samples.peekLast() ?: return Trend()
        val observationMs = (last.elapsedMs - first.elapsedMs).coerceAtLeast(0L)
        val growthKb = signedDifference(last.nativePssKb, first.nativePssKb)
        val ready = samples.size >= 2 && observationMs >= minObservationMs
        return Trend(
            ready = ready,
            sampleCount = samples.size,
            observationMs = observationMs,
            baselinePssKb = first.nativePssKb,
            currentPssKb = last.nativePssKb,
            growthKb = growthKb,
            rateKbPerMin = if (ready) regressionRateKbPerMin(first.elapsedMs) else 0,
        )
    }

    @Synchronized
    fun reset() {
        samples.clear()
    }

    private fun prune(now: Long) {
        val cutoff = (now - maxObservationMs).coerceAtLeast(0L)
        while (samples.size > 2 && samples.peekFirst().elapsedMs < cutoff) {
            samples.removeFirst()
        }
        while (samples.size > maxSamples) {
            samples.removeFirst()
        }
    }

    /** Least-squares slope in KiB/minute, bounded for diagnostics serialization. */
    private fun regressionRateKbPerMin(originMs: Long): Int {
        if (samples.size < 2) return 0
        var count = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        for (sample in samples) {
            val xMinutes = (sample.elapsedMs - originMs).toDouble() / 60_000.0
            val yKb = sample.nativePssKb.toDouble()
            count += 1.0
            sumX += xMinutes
            sumY += yKb
            sumXX += xMinutes * xMinutes
            sumXY += xMinutes * yKb
        }
        val denominator = count * sumXX - sumX * sumX
        if (denominator <= 0.0) return 0
        val slope = (count * sumXY - sumX * sumY) / denominator
        return slope.roundToInt().coerceIn(-MAX_REPORTED_RATE_KB_PER_MIN, MAX_REPORTED_RATE_KB_PER_MIN)
    }

    private fun signedDifference(end: Long, start: Long): Long = when {
        end >= start -> (end - start).coerceAtMost(Long.MAX_VALUE)
        start == Long.MAX_VALUE && end == 0L -> Long.MIN_VALUE + 1L
        else -> -((start - end).coerceAtMost(Long.MAX_VALUE))
    }

    companion object {
        /** One hour smooths allocator/GC noise while still producing useful same-day HIL evidence. */
        const val DEFAULT_MIN_OBSERVATION_MS = 60L * 60_000L

        /** Match the existing six-hour soak gate and keep memory use strictly bounded. */
        const val DEFAULT_MAX_OBSERVATION_MS = 6L * 60L * 60_000L
        const val DEFAULT_MAX_SAMPLES = 512
        private const val MAX_REPORTED_RATE_KB_PER_MIN = 1_000_000
    }
}
