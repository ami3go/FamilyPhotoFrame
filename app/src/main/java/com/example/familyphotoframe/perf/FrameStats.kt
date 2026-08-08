package com.example.familyphotoframe.perf

/**
 * Pure frame-timing statistics for the §22.4 acceptance gate — *"fit-blur, Ken Burns,
 * and transitions pass the performance budget (≥ 30 fps crossfade on the reference
 * low-end device)"*, the last item on that checklist and the one that has stayed open
 * because it says "must still be measured".
 *
 * This is the arithmetic half, kept free of Android so it runs under
 * `scripts/verify-pure-logic.sh`; [FrameStatsCollector] is the thin Choreographer glue.
 *
 * **Why percentiles and not a mean.** A mean frame rate hides exactly the failure the
 * budget cares about: a transition that averages 45 fps while stalling for 200 ms
 * mid-crossfade reads as a pass but looks broken on a TV. The p95/p99 frame *interval*
 * and the jank count are the numbers that correspond to what someone actually sees.
 */
object FrameStats {

    /** 60 Hz frame budget in nanoseconds; the usual reference for "a dropped frame". */
    const val FRAME_BUDGET_60HZ_NS = 16_666_667L

    /** §10.3's floor: a crossfade must sustain at least this. */
    const val MIN_ACCEPTABLE_FPS = 30.0

    /**
     * Summary of one measured run. Intervals are nanoseconds **between** consecutive
     * frames, so N frames yield N-1 intervals.
     */
    data class Summary(
        val frameCount: Int,
        val meanFps: Double,
        val p50IntervalNs: Long,
        val p95IntervalNs: Long,
        val p99IntervalNs: Long,
        val worstIntervalNs: Long,
        /** Frames that took longer than one 60 Hz budget beyond the expected interval. */
        val jankFrames: Int,
        /** Frames that took longer than twice the budget — visibly stuttery. */
        val severeJankFrames: Int,
    ) {
        /**
         * The gate itself. Uses the p95 interval rather than the mean, so a run that
         * averages well but stalls repeatedly correctly fails.
         */
        val passesBudget: Boolean
            get() = frameCount >= MIN_SAMPLES && fpsFromIntervalNs(p95IntervalNs) >= MIN_ACCEPTABLE_FPS
    }

    /** Below this a run is too short to say anything; ~1.5 s at 60 Hz. */
    const val MIN_SAMPLES = 90

    fun fpsFromIntervalNs(intervalNs: Long): Double =
        if (intervalNs <= 0L) 0.0 else 1_000_000_000.0 / intervalNs

    /**
     * Nearest-rank percentile on a copy of [intervals]. Nearest-rank (rather than an
     * interpolating variant) is deliberate: every reported value is a frame interval
     * that genuinely occurred, which matters when the number is going into a
     * pass/fail gate someone has to defend.
     */
    fun percentileNs(intervals: LongArray, percentile: Double): Long {
        if (intervals.isEmpty()) return 0L
        val sorted = intervals.sortedArray()
        val p = percentile.coerceIn(0.0, 100.0)
        // Nearest-rank: ceil(p/100 * N), clamped into range.
        val rank = Math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /**
     * Summarises raw frame timestamps (nanoseconds, monotonically increasing — e.g.
     * Choreographer's frameTimeNanos).
     *
     * Non-monotonic or duplicate timestamps are dropped rather than producing negative
     * intervals: a clock that goes backwards should not silently manufacture an
     * impressive frame rate.
     */
    fun summarize(frameTimestampsNs: LongArray, expectedIntervalNs: Long = FRAME_BUDGET_60HZ_NS): Summary {
        val intervals = ArrayList<Long>(maxOf(0, frameTimestampsNs.size - 1))
        for (i in 1 until frameTimestampsNs.size) {
            val d = frameTimestampsNs[i] - frameTimestampsNs[i - 1]
            if (d > 0L) intervals.add(d)
        }
        if (intervals.isEmpty()) {
            return Summary(0, 0.0, 0, 0, 0, 0, 0, 0)
        }
        val arr = intervals.toLongArray()
        val total = arr.sum()
        val jankThreshold = expectedIntervalNs + FRAME_BUDGET_60HZ_NS
        val severeThreshold = expectedIntervalNs + (2 * FRAME_BUDGET_60HZ_NS)
        return Summary(
            frameCount = arr.size + 1,
            meanFps = if (total <= 0) 0.0 else arr.size * 1_000_000_000.0 / total,
            p50IntervalNs = percentileNs(arr, 50.0),
            p95IntervalNs = percentileNs(arr, 95.0),
            p99IntervalNs = percentileNs(arr, 99.0),
            worstIntervalNs = arr.max(),
            jankFrames = arr.count { it > jankThreshold },
            severeJankFrames = arr.count { it > severeThreshold },
        )
    }

    /** One-line human-readable form for the on-screen overlay and diagnostics. */
    fun format(s: Summary): String {
        if (s.frameCount == 0) return "no frames sampled"
        val verdict = when {
            s.frameCount < MIN_SAMPLES -> "SAMPLING"
            s.passesBudget -> "PASS"
            else -> "FAIL"
        }
        return buildString {
            append(verdict).append("  ")
            append("mean ").append(round1(s.meanFps)).append("fps  ")
            append("p95 ").append(round1(fpsFromIntervalNs(s.p95IntervalNs))).append("fps  ")
            append("worst ").append(round1(s.worstIntervalNs / 1_000_000.0)).append("ms  ")
            append("jank ").append(s.jankFrames).append("/").append(s.severeJankFrames)
            append("  n=").append(s.frameCount)
        }
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
