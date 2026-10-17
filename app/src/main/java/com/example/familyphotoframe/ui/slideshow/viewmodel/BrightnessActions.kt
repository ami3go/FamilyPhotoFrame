package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.settings.BrightnessAutomationSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.domain.schedule.BrightnessPolicy
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Screen brightness and night mode: manual, scheduled and ambient-light brightness,
 * night actions that pause the slideshow or blank the screen, and temporary wake.
 */
internal class BrightnessActions(host: SlideshowFeatureHost) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics
    private val engine = services.engine
    var brightnessJob: Job? = null
    @Volatile private var ambientLux: Float? = null
    @Volatile private var ambientSensorAvailable: Boolean = false

    fun setNightBrightness(value: Float) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(schedule = it.schedule.copy(brightnessNight = value.coerceIn(0.01f, 1f)))
            }
        }
    }

    /**
     * Re-evaluate the quiet-hours window and drive the engine and screen brightness.
     *
     * The state is derived from the wall clock each time rather than tracked with a
     * running timer, so it is correct after a reboot, process death, or a device that
     * was suspended across the transition (spec §22.4). Waits are capped so a clock
     * change or timezone shift is picked up within the cap rather than at the next
     * scheduled boundary.
     */
    fun restartBrightnessWatcher(automation: BrightnessAutomationSettings) {
        brightnessJob?.cancel()
        brightnessJob = viewModelScope.launch {
            while (isActive) {
                val nowMs = System.currentTimeMillis()
                val now = nowMinutes()
                val decision = BrightnessPolicy.decide(automation, nowMs, now)
                val brightness = applyAmbientBrightness(decision.brightness, automation)
                val asleep = decision.action == NightAction.PAUSE_SLIDESHOW ||
                    decision.action == NightAction.BLACK_SCREEN
                engine.setAsleep(asleep)
                _state.update {
                    it.copy(
                        asleep = asleep,
                        screenBrightness = brightness,
                        activeBrightnessPeriodId = decision.periodId,
                        activeNightAction = decision.action,
                        temporaryWakeActive = decision.temporaryWake,
                        blackScreen = decision.action == NightAction.BLACK_SCREEN && !decision.temporaryWake,
                        ambientLux = ambientLux,
                        ambientSensorAvailable = ambientSensorAvailable,
                    )
                }
                diagnostics.log(
                    DiagnosticsLog.Category.ENGINE,
                    "BRIGHTNESS_LEVEL_APPLIED",
                    "level" to (brightness * 100f).toInt().coerceIn(1, 100).toString(),
                    "action" to decision.action.name,
                    "mode" to automation.mode.name,
                    "periodToken" to diagnosticToken(decision.periodId.orEmpty(), "period"),
                )
                delay(30_000L)
            }
        }
    }

    private fun applyAmbientBrightness(base: Float, settings: BrightnessAutomationSettings): Float {
        if (settings.mode != BrightnessMode.AMBIENT && settings.mode != BrightnessMode.SCHEDULED_AMBIENT) {
            return base.coerceIn(0.01f, 1f)
        }
        val lux = ambientLux ?: return base.coerceIn(settings.ambientMinimum, settings.ambientMaximum)
        // Logarithmic response: useful from a dark room through ordinary indoor light,
        // with a hard min/max so sensor noise cannot flash the wall frame.
        val normalized = (kotlin.math.ln(1f + lux.coerceAtLeast(0f)) / kotlin.math.ln(1001f))
            .coerceIn(0f, 1f)
        val ambient = settings.ambientMinimum +
            (settings.ambientMaximum - settings.ambientMinimum) * normalized
        return minOf(base, ambient).coerceIn(0.01f, 1f)
    }

    fun setBrightnessMode(mode: BrightnessMode) {
        viewModelScope.launch {
            services.settings.update { it.copy(brightnessAutomation = it.brightnessAutomation.copy(mode = mode)) }
        }
    }

    /**
     * Window-only brightness while a settings slider is dragged; nothing is persisted. The
     * matching setter clears it once the chosen value has been saved, so the window does not
     * flick back to the previous level in between.
     */
    fun previewBrightness(value: Float?) {
        _state.update { it.copy(brightnessPreview = value?.coerceIn(0.01f, 1f)) }
    }

    fun setManualBrightness(value: Float) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(brightnessAutomation = it.brightnessAutomation.copy(
                    manualBrightness = value.coerceIn(0.01f, 1f),
                ))
            }
            previewBrightness(null)
        }
    }

    fun setBrightnessPeriod(index: Int, startTime: String, brightness: Float, action: NightAction) {
        val startMinutes = SleepSchedule.parseMinutes(startTime) ?: run {
            previewBrightness(null)
            return
        }
        val normalizedStart = SleepSchedule.formatMinutes(startMinutes)
        viewModelScope.launch {
            services.settings.update { current ->
                val periods = current.brightnessAutomation.periods.toMutableList()
                if (index !in periods.indices) return@update current
                periods[index] = periods[index].copy(
                    startTime = normalizedStart,
                    brightness = brightness.coerceIn(0.01f, 1f),
                    action = action,
                )
                current.copy(brightnessAutomation = current.brightnessAutomation.copy(periods = periods))
            }
            previewBrightness(null)
        }
    }

    fun temporaryWake() {
        viewModelScope.launch {
            services.settings.update { current ->
                val minutes = current.brightnessAutomation.temporaryWakeMinutes.coerceIn(1, 240)
                current.copy(brightnessAutomation = current.brightnessAutomation.copy(
                    temporaryWakeUntilEpochMs = System.currentTimeMillis() + minutes * 60_000L,
                ))
            }
            diagnostics.log(DiagnosticsLog.Category.APP, "TEMPORARY_WAKE_STARTED")
        }
    }

    fun onAmbientLight(lux: Float?, available: Boolean) {
        ambientSensorAvailable = available
        if (lux != null && lux.isFinite()) {
            val previous = ambientLux
            ambientLux = if (previous == null) lux.coerceAtLeast(0f)
                else previous * 0.8f + lux.coerceAtLeast(0f) * 0.2f
        }
        _state.update { it.copy(ambientLux = ambientLux, ambientSensorAvailable = available) }
    }
}
