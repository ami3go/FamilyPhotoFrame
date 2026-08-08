package com.example.familyphotoframe

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.Surface
import java.util.UUID

/**
 * Owns Activity/runtime diagnostics state so lifecycle instrumentation does not turn
 * [MainActivity] into a second logging implementation.
 */
internal class ActivityDiagnosticsController(
    private val activity: Activity,
    private val services: ServiceLocator,
) {
    val activityToken: String = diagnosticToken(UUID.randomUUID().toString(), "activity")
    var activityStateCode: String = "INITIALIZING"
        private set

    private var lastActivityState: String? = null
    private var lastSurface: String? = null
    private var lastEngineState: String? = null
    private var lastInteractive: Boolean? = null
    private var lastDisplaySignature: String? = null
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> recordScreenInteractive(true, "SCREEN_ON")
                Intent.ACTION_SCREEN_OFF -> recordScreenInteractive(false, "SCREEN_OFF")
            }
        }
    }

    fun onCreated(configuration: Configuration) {
        recordActivityState("CREATED")
        recordDisplayConfiguration(configuration, "ACTIVITY_CREATED")
    }

    fun onStarted() {
        recordActivityState("STARTED")
        registerScreenReceiver()
        val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        recordScreenInteractive(power.isInteractive, "ACTIVITY_STARTED")
    }

    fun onResumed() = recordActivityState("RESUMED")

    fun onPaused() = recordActivityState("PAUSED")

    fun onStopped() {
        unregisterScreenReceiver()
        recordActivityState("STOPPED")
    }

    fun onDestroyed() {
        unregisterScreenReceiver()
        recordActivityState("DESTROYED")
    }

    fun onConfigurationChanged(configuration: Configuration) {
        recordDisplayConfiguration(configuration, "CONFIGURATION_CHANGED")
    }

    fun recordUiState(ui: SlideshowUiState, settingsVisible: Boolean, reason: String) {
        recordSurface(ui, settingsVisible, reason)
        recordEngineState(ui.engine.state.name, reason)
    }

    private fun recordActivityState(state: String) {
        if (lastActivityState == state) return
        lastActivityState = state
        activityStateCode = state
        val code = when (state) {
            "CREATED" -> "ACTIVITY_CREATED"
            "STARTED" -> "ACTIVITY_STARTED"
            "RESUMED" -> "ACTIVITY_RESUMED"
            "PAUSED" -> "ACTIVITY_PAUSED"
            "STOPPED" -> "ACTIVITY_STOPPED"
            "DESTROYED" -> "ACTIVITY_DESTROYED"
            else -> return
        }
        services.diagnostics.logEvent(
            code,
            mapOf("activityToken" to activityToken, "activityState" to state),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
    }

    private fun recordSurface(ui: SlideshowUiState, settingsVisible: Boolean, reason: String) {
        val current = if (settingsVisible) {
            "SETTINGS"
        } else if (ui.blackScreen) {
            "BLACK_SCREEN"
        } else {
            when (ui.surface) {
                Surface.Loading -> "LOADING"
                Surface.FirstRun -> "FIRST_RUN"
                is Surface.Recovery -> "RECOVERY"
                Surface.EmptyIndex -> "EMPTY_INDEX"
                Surface.Playing -> "PLAYING"
            }
        }
        val previous = lastSurface
        services.diagnosticRuntimeState.updatePlayback { it.copy(surface = current) }
        if (previous == current) return
        lastSurface = current
        services.diagnostics.logEvent(
            "SLIDESHOW_SURFACE_CHANGED",
            mapOf(
                "surface" to current,
                "previousSurface" to (previous ?: "NONE"),
                "reason" to safeReason(reason),
                "activityToken" to activityToken,
            ),
            DiagnosticContext(origin = DiagnosticOrigin.APP),
        )
    }

    private fun recordEngineState(state: String, reason: String) {
        val previous = lastEngineState
        if (previous == state) return
        lastEngineState = state
        services.diagnostics.logEvent(
            "ENGINE_STATE_CHANGED",
            mapOf(
                "engineState" to state,
                "previousEngineState" to (previous ?: "NONE"),
                "reason" to safeReason(reason),
                "activityToken" to activityToken,
            ),
            DiagnosticContext(origin = DiagnosticOrigin.APP),
        )
    }

    private fun recordScreenInteractive(interactive: Boolean, reason: String) {
        if (lastInteractive == interactive) return
        lastInteractive = interactive
        services.diagnostics.logEvent(
            "SCREEN_INTERACTIVE_CHANGED",
            mapOf(
                "interactive" to interactive.toString(),
                "reason" to safeReason(reason),
                "activityToken" to activityToken,
            ),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
    }

    private fun recordDisplayConfiguration(config: Configuration, reason: String) {
        val orientation = when (config.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
            Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
            else -> "UNDEFINED"
        }
        val sizeClass = when {
            config.screenWidthDp < 600 -> "COMPACT"
            config.screenWidthDp < 840 -> "MEDIUM"
            else -> "EXPANDED"
        }
        val density = activity.resources.displayMetrics.densityDpi
        val densityBucket = when {
            density <= 140 -> "LDPI"
            density <= 200 -> "MDPI"
            density <= 280 -> "HDPI"
            density <= 400 -> "XHDPI"
            density <= 560 -> "XXHDPI"
            else -> "XXXHDPI"
        }
        val signature = "$orientation|$sizeClass|$densityBucket"
        if (lastDisplaySignature == signature) return
        lastDisplaySignature = signature
        services.diagnostics.logEvent(
            "DISPLAY_CONFIGURATION_CHANGED",
            mapOf(
                "orientation" to orientation,
                "sizeClass" to sizeClass,
                "densityBucket" to densityBucket,
                "reason" to safeReason(reason),
                "activityToken" to activityToken,
            ),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        ContextCompat.registerReceiver(
            activity,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { activity.unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun safeReason(value: String): String = value.uppercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .take(48)
        .ifEmpty { "UNKNOWN" }
}
