package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.BrightnessPeriod
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Compatibility adapter for pre-unified-brightness web clients. */
internal object LegacyBrightnessWebPatch {
    private val keys = setOf(
        "sleepEnabled", "sleepStart", "sleepEnd", "brightnessDay", "brightnessNight",
    )

    fun validationError(patch: JsonObject): String? {
        string(patch, "sleepStart")?.let {
            if (SleepSchedule.parseMinutes(it) == null) return "sleepStart must be HH:mm"
        }
        string(patch, "sleepEnd")?.let {
            if (SleepSchedule.parseMinutes(it) == null) return "sleepEnd must be HH:mm"
        }
        float(patch, "brightnessDay")?.let {
            if (it !in 0.05f..1f) return "brightnessDay must be 0.05-1.0"
        }
        float(patch, "brightnessNight")?.let {
            if (it !in 0.05f..1f) return "brightnessNight must be 0.05-1.0"
        }
        return null
    }

    fun apply(current: AppSettings, patch: JsonObject): AppSettings {
        if (keys.none(patch::containsKey)) return current

        val base = current.brightnessAutomation
        val periods = base.periods.toMutableList()
        fun replacePeriod(period: BrightnessPeriod) {
            val index = periods.indexOfFirst { it.id == period.id }
            if (index >= 0) periods[index] = period else periods += period
        }

        val day = periods.firstOrNull { it.id == "day" } ?: BrightnessPeriod(
            id = "day",
            startTime = "07:00",
            brightness = base.manualBrightness,
            action = NightAction.DIM_ONLY,
        )
        val night = periods.firstOrNull { it.id == "night" } ?: BrightnessPeriod(
            id = "night",
            startTime = "23:00",
            brightness = 0.30f,
            action = NightAction.PAUSE_SLIDESHOW,
        )
        replacePeriod(day.copy(
            startTime = string(patch, "sleepEnd")?.trim() ?: day.startTime,
            brightness = float(patch, "brightnessDay") ?: day.brightness,
        ))
        replacePeriod(night.copy(
            startTime = string(patch, "sleepStart")?.trim() ?: night.startTime,
            brightness = float(patch, "brightnessNight") ?: night.brightness,
            action = if (bool(patch, "sleepEnabled") == true) {
                NightAction.PAUSE_SLIDESHOW
            } else {
                night.action
            },
        ))
        val mode = when (bool(patch, "sleepEnabled")) {
            true -> BrightnessMode.SCHEDULED
            false -> BrightnessMode.MANUAL
            null -> base.mode
        }
        return current.copy(
            brightnessAutomation = base.copy(
                mode = mode,
                manualBrightness = float(patch, "brightnessDay") ?: base.manualBrightness,
                periods = periods,
            ).normalized(),
        )
    }

    private fun string(patch: JsonObject, key: String): String? =
        patch[key]?.jsonPrimitive?.let { if (it.isString) it.content else null }

    private fun float(patch: JsonObject, key: String): Float? =
        patch[key]?.jsonPrimitive?.doubleOrNull?.toFloat()

    private fun bool(patch: JsonObject, key: String): Boolean? =
        patch[key]?.jsonPrimitive?.booleanOrNull
}
