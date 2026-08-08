package com.example.familyphotoframe.domain.schedule

import com.example.familyphotoframe.data.settings.BrightnessAutomationSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.NightAction

/** Permission-free, window-brightness policy suitable for Android 5. */
object BrightnessPolicy {
    data class Decision(
        val brightness: Float,
        val action: NightAction,
        val periodId: String?,
        val temporaryWake: Boolean,
    )

    fun decide(settings: BrightnessAutomationSettings, nowEpochMs: Long, nowMinutes: Int): Decision {
        val normalized = settings.normalized()
        if (normalized.temporaryWakeUntilEpochMs > nowEpochMs) {
            return Decision(normalized.manualBrightness, NightAction.DIM_ONLY, null, true)
        }
        if (normalized.mode == BrightnessMode.MANUAL || normalized.periods.isEmpty()) {
            return Decision(normalized.manualBrightness, NightAction.DIM_ONLY, null, false)
        }
        val selectedIndex = BrightnessTimeline.activeIndex(
            normalized.periods.map { it.startTime },
            nowMinutes,
        ) ?: return Decision(normalized.manualBrightness, NightAction.DIM_ONLY, null, false)
        val selected = normalized.periods[selectedIndex]
        return Decision(selected.brightness, selected.action, selected.id, false)
    }
}
