package com.example.familyphotoframe.ui.slideshow.transition

import java.util.ArrayDeque

/** Bounded per-transition frame sampler; one timestamp is recorded per animation frame. */
class TransitionFrameSampler(private val capacity: Int = 256) {
    private val timestamps = LongArray(capacity.coerceAtLeast(2))
    private var count = 0

    fun record(timestampNs: Long) {
        if (count < timestamps.size) timestamps[count++] = timestampNs
    }

    fun summary(): TransitionFrameMetrics {
        if (count < 2) return TransitionFrameMetrics.EMPTY
        var validIntervals = 0
        var slow = 0
        var maximum = 0L
        for (index in 1 until count) {
            val interval = timestamps[index] - timestamps[index - 1]
            if (interval <= 0L) continue
            validIntervals++
            if (interval > SLOW_FRAME_NS) slow++
            if (interval > maximum) maximum = interval
        }
        if (validIntervals == 0) return TransitionFrameMetrics.EMPTY
        return TransitionFrameMetrics(
            frameCount = validIntervals + 1,
            slowFrameCount = slow,
            maximumFrameNs = maximum,
        )
    }

    companion object {
        const val SLOW_FRAME_NS: Long = 33_333_333L
    }
}

data class TransitionFrameMetrics(
    val frameCount: Int,
    val slowFrameCount: Int,
    val maximumFrameNs: Long,
) {
    val intervalCount: Int get() = (frameCount - 1).coerceAtLeast(0)
    val slowFrameRatio: Double
        get() = if (intervalCount == 0) 0.0 else slowFrameCount.toDouble() / intervalCount
    val maximumFrameMs: Long get() = maximumFrameNs / 1_000_000L
    val isPerformanceWarning: Boolean
        get() = intervalCount >= 2 && (slowFrameRatio > 0.20 || maximumFrameNs > 250_000_000L)

    companion object {
        val EMPTY = TransitionFrameMetrics(0, 0, 0L)
    }
}

enum class TransitionPerformanceStateChange { NONE, ENTERED_LOW_PERFORMANCE, EXITED_LOW_PERFORMANCE }

data class TransitionPerformanceDecision(
    val warning: Boolean,
    val stateChange: TransitionPerformanceStateChange,
)

/**
 * Enters a bounded Crossfade-only cooldown when three of the last five transitions are
 * visibly slow. No prepared content is retained here; only five booleans and a counter.
 */
class TransitionPerformanceController(
    private val sampleWindow: Int = 5,
    private val warningThreshold: Int = 3,
    private val cooldownTransitions: Int = 20,
) {
    private val recentWarnings = ArrayDeque<Boolean>()
    private var remainingCooldown = 0

    val isLowPerformanceMode: Boolean get() = remainingCooldown > 0
    val remainingLowPerformanceTransitions: Int get() = remainingCooldown

    fun record(metrics: TransitionFrameMetrics): TransitionPerformanceDecision {
        val warning = metrics.isPerformanceWarning

        if (remainingCooldown > 0) {
            remainingCooldown--
            if (remainingCooldown == 0) {
                recentWarnings.clear()
                return TransitionPerformanceDecision(
                    warning = warning,
                    stateChange = TransitionPerformanceStateChange.EXITED_LOW_PERFORMANCE,
                )
            }
            return TransitionPerformanceDecision(warning, TransitionPerformanceStateChange.NONE)
        }

        recentWarnings.addLast(warning)
        while (recentWarnings.size > sampleWindow.coerceAtLeast(1)) recentWarnings.removeFirst()
        if (
            recentWarnings.size == sampleWindow &&
            recentWarnings.count { it } >= warningThreshold
        ) {
            remainingCooldown = cooldownTransitions.coerceAtLeast(1)
            recentWarnings.clear()
            return TransitionPerformanceDecision(
                warning = warning,
                stateChange = TransitionPerformanceStateChange.ENTERED_LOW_PERFORMANCE,
            )
        }
        return TransitionPerformanceDecision(warning, TransitionPerformanceStateChange.NONE)
    }
}
