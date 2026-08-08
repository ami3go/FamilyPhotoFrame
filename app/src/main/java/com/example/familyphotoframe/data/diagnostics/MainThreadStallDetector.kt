package com.example.familyphotoframe.data.diagnostics

/** Pure state machine behind the Android main-thread watchdog. */
class MainThreadStallDetector(
    private val warningThresholdMs: Long = WARNING_THRESHOLD_MS,
    private val escalationThresholdMs: Long = ESCALATION_THRESHOLD_MS,
    private val captureCooldownMs: Long = CAPTURE_COOLDOWN_MS,
) {
    enum class Kind { STARTED, ESCALATED, RECOVERED, SUPPRESSED_BY_DEBUGGER }

    data class Signal(
        val kind: Kind,
        val durationMs: Long,
        val captureEnvelope: Boolean,
    )

    private var stallStartedAtMs: Long? = null
    private var escalated = false
    private var lastCaptureAtMs: Long? = null

    fun observe(nowMs: Long, lastMainAckMs: Long, debuggerAttached: Boolean): List<Signal> {
        if (debuggerAttached) {
            val wasStalled = stallStartedAtMs != null
            stallStartedAtMs = null
            escalated = false
            return if (wasStalled) {
                listOf(Signal(Kind.SUPPRESSED_BY_DEBUGGER, 0L, false))
            } else emptyList()
        }

        val lag = (nowMs - lastMainAckMs).coerceAtLeast(0L)
        if (lag < warningThresholdMs) {
            val started = stallStartedAtMs ?: return emptyList()
            stallStartedAtMs = null
            escalated = false
            return listOf(Signal(Kind.RECOVERED, (nowMs - started).coerceAtLeast(0L), false))
        }

        val signals = arrayListOf<Signal>()
        if (stallStartedAtMs == null) {
            stallStartedAtMs = lastMainAckMs
            signals += Signal(Kind.STARTED, lag, reserveCapture(nowMs))
        }
        if (lag >= escalationThresholdMs && !escalated) {
            escalated = true
            signals += Signal(Kind.ESCALATED, lag, reserveCapture(nowMs))
        }
        return signals
    }

    private fun reserveCapture(nowMs: Long): Boolean {
        val previous = lastCaptureAtMs
        if (previous != null && nowMs - previous < captureCooldownMs) return false
        lastCaptureAtMs = nowMs
        return true
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 2_000L
        const val WARNING_THRESHOLD_MS = 5_000L
        const val ESCALATION_THRESHOLD_MS = 10_000L
        const val CAPTURE_COOLDOWN_MS = 60_000L
    }
}
