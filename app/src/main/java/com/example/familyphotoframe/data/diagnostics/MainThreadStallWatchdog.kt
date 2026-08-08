package com.example.familyphotoframe.data.diagnostics

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Application-lifetime, API-21-compatible main-thread heartbeat watchdog. */
class MainThreadStallWatchdog(
    private val diagnostics: DiagnosticsLog,
    private val captureEnvelope: (stallDurationMs: Long, mainThread: Thread) -> Unit,
    private val clearRecoveredEnvelope: () -> Unit,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val debuggerAttached: () -> Boolean = Debug::isDebuggerConnected,
    private val detector: MainThreadStallDetector = MainThreadStallDetector(),
) {
    private val running = AtomicBoolean(false)
    private val lastMainAckMs = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainThread: Thread get() = Looper.getMainLooper().thread

    private val heartbeat = object : Runnable {
        override fun run() {
            if (!running.get()) return
            lastMainAckMs.set(elapsedRealtimeMs())
            mainHandler.postDelayed(this, MainThreadStallDetector.HEARTBEAT_INTERVAL_MS)
        }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        lastMainAckMs.set(elapsedRealtimeMs())
        mainHandler.post(heartbeat)
        Thread(::watch, "fpf-main-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        mainHandler.removeCallbacks(heartbeat)
    }

    private fun watch() {
        while (running.get()) {
            try {
                Thread.sleep(MainThreadStallDetector.HEARTBEAT_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            val now = elapsedRealtimeMs()
            val attached = runCatching(debuggerAttached).getOrDefault(false)
            // This thread's job is to detect app unresponsiveness; it must never itself
            // become a crash source. An exception here is swallowed rather than left to
            // reach the default uncaught-exception handler, which would kill the process.
            runCatching { processSignals(now, attached) }
        }
    }

    private fun processSignals(now: Long, attached: Boolean) {
        detector.observe(now, lastMainAckMs.get(), attached).forEach { signal ->
            when (signal.kind) {
                MainThreadStallDetector.Kind.STARTED -> {
                    diagnostics.logEvent(
                        "MAIN_THREAD_STALL_STARTED",
                        mapOf(
                            "stallDurationMs" to signal.durationMs.toString(),
                            "debuggerAttached" to "false",
                            "frameCount" to mainThread.stackTrace.take(MAX_FRAMES).size.toString(),
                        ),
                        DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                    )
                    if (signal.captureEnvelope) captureEnvelope(signal.durationMs, mainThread)
                }
                MainThreadStallDetector.Kind.ESCALATED -> {
                    diagnostics.logEvent(
                        "MAIN_THREAD_STALL_ESCALATED",
                        mapOf(
                            "stallDurationMs" to signal.durationMs.toString(),
                            "debuggerAttached" to "false",
                            "frameCount" to mainThread.stackTrace.take(MAX_FRAMES).size.toString(),
                        ),
                        DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                    )
                    if (signal.captureEnvelope) captureEnvelope(signal.durationMs, mainThread)
                }
                MainThreadStallDetector.Kind.RECOVERED -> {
                    diagnostics.logEvent(
                        "MAIN_THREAD_STALL_RECOVERED",
                        mapOf(
                            "stallDurationMs" to signal.durationMs.toString(),
                            "debuggerAttached" to "false",
                        ),
                        DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                    )
                    clearRecoveredEnvelope()
                }
                MainThreadStallDetector.Kind.SUPPRESSED_BY_DEBUGGER -> {
                    // Debugger pauses are not ANRs and must not leave restart evidence.
                    clearRecoveredEnvelope()
                }
            }
        }
    }

    private companion object { const val MAX_FRAMES = 8 }
}
