package com.example.familyphotoframe.data.weather

import kotlin.math.roundToInt

enum class TemperatureUnits { CELSIUS, FAHRENHEIT }

/** A single observation. [observedAtEpochMs] is when the frame fetched it, not the model run time. */
data class WeatherSnapshot(
    val temperatureC: Double,
    val weatherCode: Int,
    val observedAtEpochMs: Long,
)

/** What the overlay should draw right now. */
sealed interface WeatherDisplay {
    data object Hidden : WeatherDisplay
    data class Visible(val text: String, val stale: Boolean) : WeatherDisplay
}

/**
 * Presentation rules for the weather overlay (spec §11).
 *
 * The governing constraint is that weather is decoration: a provider outage must never
 * blank the slideshow or block a slide change. So this is a pure function of the last
 * snapshot — if there is none, or it is older than the caller's staleness budget, the
 * overlay simply hides rather than showing an error.
 */
object WeatherPresentation {

    fun resolve(
        snapshot: WeatherSnapshot?,
        nowEpochMs: Long,
        maxStaleMs: Long,
        units: TemperatureUnits,
        staleAfterMs: Long,
    ): WeatherDisplay {
        if (snapshot == null) return WeatherDisplay.Hidden
        val age = nowEpochMs - snapshot.observedAtEpochMs
        if (age > maxStaleMs) return WeatherDisplay.Hidden      // too old to be meaningful
        return WeatherDisplay.Visible(
            text = format(snapshot, units),
            stale = age > staleAfterMs,
        )
    }

    fun format(snapshot: WeatherSnapshot, units: TemperatureUnits): String {
        val value = when (units) {
            TemperatureUnits.CELSIUS -> snapshot.temperatureC
            TemperatureUnits.FAHRENHEIT -> snapshot.temperatureC * 9.0 / 5.0 + 32.0
        }
        val suffix = if (units == TemperatureUnits.CELSIUS) "\u00B0C" else "\u00B0F"
        val label = WeatherCodes.label(snapshot.weatherCode)
        return if (label.isEmpty()) "${value.roundToInt()}$suffix" else "${value.roundToInt()}$suffix  $label"
    }
}

/**
 * WMO weather interpretation codes, grouped into the handful of words a photo frame
 * should show. Deliberately coarse: this is glanceable decoration, not a forecast app.
 */
object WeatherCodes {
    fun label(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> ""
    }
}
