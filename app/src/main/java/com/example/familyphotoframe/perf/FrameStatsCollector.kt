package com.example.familyphotoframe.perf

import android.view.Choreographer

/**
 * Records frame timestamps from [Choreographer] so the §22.4 performance budget can
 * actually be measured on the reference device instead of remaining an open checklist
 * item. All arithmetic lives in [FrameStats]; this is the thin, untested-by-unit-test
 * Android half, matching the split used elsewhere (`ExifParsing`/`ExifExtractor`,
 * `SynologyApi`/`SynologyFileStationSource`).
 *
 * Cost when stopped is zero — no callback is posted at all — so leaving the feature in a
 * release build does not tax the frame it is meant to be measuring. While running it
 * appends one long per frame into a fixed-capacity ring, so a long session cannot grow
 * the heap (which would itself corrupt the "heap does not grow across a long run" part
 * of the budget it is measuring).
 */
class FrameStatsCollector(
    /** ~60 s at 60 Hz; enough for a full slideshow interval plus several transitions. */
    private val capacity: Int = 3_600,
) {
    private val timestamps = LongArray(capacity)
    private var count = 0
    private var writeIndex = 0

    @Volatile var isRunning: Boolean = false
        private set

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            record(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun record(ts: Long) {
        timestamps[writeIndex] = ts
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) count++
    }

    /** Clears any previous run and begins sampling. Must be called on the main thread. */
    fun start() {
        if (isRunning) return
        count = 0
        writeIndex = 0
        isRunning = true
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /**
     * Stops sampling. The collected samples remain available via [summary].
     *
     * The queued callback is removed rather than merely flagged off. The chain does end on
     * its own — the next `doFrame` sees [isRunning] false and stops re-posting — but that
     * leaves roughly one frame (~16 ms) during which a restart would post a second callback
     * while the first is still queued. Both chains would then re-post indefinitely, doubling
     * the per-frame work for the life of the process. Must be called on the main thread.
     */
    fun stop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    /**
     * Samples in chronological order. Once the ring has wrapped, this is the most recent
     * [capacity] frames — so a long run reports its recent behaviour rather than a
     * snapshot of start-up, which is usually the least representative part.
     */
    fun samples(): LongArray {
        if (count < capacity) return timestamps.copyOfRange(0, count)
        val out = LongArray(capacity)
        val tail = capacity - writeIndex
        System.arraycopy(timestamps, writeIndex, out, 0, tail)
        System.arraycopy(timestamps, 0, out, tail, writeIndex)
        return out
    }

    fun summary(): FrameStats.Summary = FrameStats.summarize(samples())

    /** One-line form for the on-screen overlay. */
    fun formatted(): String = FrameStats.format(summary())
}
