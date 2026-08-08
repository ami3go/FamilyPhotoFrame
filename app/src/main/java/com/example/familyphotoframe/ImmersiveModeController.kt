package com.example.familyphotoframe

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog

/**
 * Owns the Android 5+ immersive-mode compatibility path and its state-change diagnostics.
 * Visibility callbacks can fire repeatedly for one gesture, so loss and retry events are
 * guarded by explicit state rather than emitted per callback.
 */
internal class ImmersiveModeController(
    private val activity: Activity,
    private val diagnostics: DiagnosticsLog,
    private val activityToken: String,
    private val activityState: () -> String,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var installed = false
    private var immersiveApplied = false
    private var barsLostAtMs: Long? = null
    private var retryScheduled = false
    private var retryCount = 0
    private var applyScheduled = false
    private var pendingReason = "LIFECYCLE"

    private val applyNow = Runnable {
        applyScheduled = false
        apply(pendingReason)
    }
    private val settle = Runnable { apply("WINDOW_SETTLE") }
    private val retry = Runnable {
        retryScheduled = false
        apply("SYSTEM_BARS_RETRY")
    }

    @Suppress("DEPRECATION")
    fun install() {
        if (installed) return
        installed = true
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            val statusVisible = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
            val navigationVisible = visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
            if (statusVisible || navigationVisible) {
                if (activity.hasWindowFocus() && immersiveApplied && barsLostAtMs == null) {
                    barsLostAtMs = elapsedRealtimeMs()
                    immersiveApplied = false
                    log(
                        "IMMERSIVE_LOST",
                        reason = "SYSTEM_BARS_VISIBLE",
                        visibility = visibility,
                    )
                    log(
                        "IMMERSIVE_EXITED_BY_USER",
                        reason = "TRANSIENT_BARS_REVEALED",
                        visibility = visibility,
                    )
                    scheduleRetry(visibility)
                }
            }
        }
    }

    /** Re-assert fullscreen after lifecycle, picker, dialog, or settings transitions. */
    fun recover(reason: String = "LIFECYCLE") {
        pendingReason = safeReason(reason)
        if ((!immersiveApplied || barsLostAtMs != null) && !applyScheduled) {
            log("IMMERSIVE_REQUESTED", reason = pendingReason)
        }
        if (!applyScheduled) {
            applyScheduled = true
            handler.post(applyNow)
        }
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, WINDOW_SETTLE_DELAY_MS)
    }

    /** Focus returning from a system surface can reveal bars without a visibility callback. */
    @Suppress("DEPRECATION")
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) return
        val visibility = activity.window.decorView.systemUiVisibility
        val statusVisible = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
        val navigationVisible = visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
        if ((statusVisible || navigationVisible) && immersiveApplied && barsLostAtMs == null) {
            barsLostAtMs = elapsedRealtimeMs()
            immersiveApplied = false
            log("IMMERSIVE_LOST", reason = "WINDOW_FOCUS_RETURN", visibility = visibility)
            scheduleRetry(visibility)
        }
        recover("WINDOW_FOCUS_RETURN")
    }

    @Suppress("DEPRECATION")
    private fun apply(reason: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val lostAt = barsLostAtMs

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.decorView.systemUiVisibility = REQUIRED_FLAGS

        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
        }

        val firstApply = !immersiveApplied && lostAt == null
        immersiveApplied = true
        if (lostAt != null) {
            barsLostAtMs = null
            handler.removeCallbacks(retry)
            retryScheduled = false
            log(
                "IMMERSIVE_RESTORED",
                reason = reason,
                durationMs = (elapsedRealtimeMs() - lostAt).coerceAtLeast(0L),
            )
            retryCount = 0
        } else if (firstApply) {
            log("IMMERSIVE_APPLIED", reason = reason)
        }
    }

    @Suppress("DEPRECATION")
    private fun scheduleRetry(visibility: Int) {
        if (retryScheduled) return
        retryScheduled = true
        retryCount = (retryCount + 1).coerceAtMost(MAX_RETRY_COUNT)
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, TRANSIENT_BAR_DELAY_MS)
        log(
            "IMMERSIVE_RETRY_SCHEDULED",
            reason = "SYSTEM_BARS_VISIBLE",
            visibility = visibility,
            durationMs = TRANSIENT_BAR_DELAY_MS,
        )
    }

    @Suppress("DEPRECATION")
    private fun log(
        code: String,
        reason: String,
        visibility: Int = activity.window.decorView.systemUiVisibility,
        durationMs: Long = 0L,
    ) {
        diagnostics.logEvent(
            code,
            mapOf(
                "activityToken" to activityToken,
                "activityState" to safeReason(activityState()),
                "reason" to safeReason(reason),
                "systemUiFlags" to visibility.toString(),
                "retryCount" to retryCount.toString(),
                "durationMs" to durationMs.coerceAtLeast(0L).toString(),
                "legacyPath" to (Build.VERSION.SDK_INT < 30).toString(),
            ),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
    }

    @Suppress("DEPRECATION")
    fun release() {
        handler.removeCallbacksAndMessages(null)
        activity.window.decorView.setOnSystemUiVisibilityChangeListener(null)
        installed = false
        retryScheduled = false
        applyScheduled = false
    }

    private fun safeReason(value: String): String = value.uppercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .take(48)
        .ifEmpty { "UNKNOWN" }

    private companion object {
        const val WINDOW_SETTLE_DELAY_MS = 250L
        const val TRANSIENT_BAR_DELAY_MS = 1_200L
        const val MAX_RETRY_COUNT = 8
        const val REQUIRED_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}
