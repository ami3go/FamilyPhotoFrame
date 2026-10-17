package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.CredentialPolicy
import com.example.familyphotoframe.data.weather.TemperatureUnits
import com.example.familyphotoframe.data.weather.WeatherDisplay
import com.example.familyphotoframe.data.weather.WeatherPresentation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Weather overlay settings and text (spec §11). The overlay only ever reads the last
 * snapshot, so weather can never delay a slide.
 */
internal class WeatherActions(
    host: SlideshowFeatureHost,
    private val currentSettings: () -> AppSettings?,
) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics
    private val lastSettings: AppSettings? get() = currentSettings()

    fun setWeatherEnabled(value: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(weather = it.weather.copy(enabled = value)) }
        }
    }

    /** Coordinates are typed by the user, so the app never needs a location permission. */
    fun setWeatherLocation(latitudeText: String, longitudeText: String) {
        val lat = latitudeText.trim().toDoubleOrNull()
        val lon = longitudeText.trim().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_weather_bad_location)) }
            return
        }
        viewModelScope.launch {
            services.settings.update {
                it.copy(weather = it.weather.copy(latitude = lat, longitude = lon))
            }
        }
    }

    fun setWeatherUnits(units: TemperatureUnits) {
        viewModelScope.launch {
            services.settings.update { it.copy(weather = it.weather.copy(units = units)) }
        }
    }

    fun setWeatherEndpoint(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) return
        viewModelScope.launch {
            services.settings.update { it.copy(weather = it.weather.copy(endpointBaseUrl = trimmed)) }
        }
    }

    /** The key is a secret: stored in the Keystore, never in the settings file. */
    fun setWeatherApiKey(key: String) {
        viewModelScope.launch {
            val ref = if (key.isBlank()) "" else WEATHER_KEY_REF
            if (key.isNotBlank()) services.secretStore.store(WEATHER_KEY_REF, "weather_api_key", key)
            else services.secretStore.forget(WEATHER_KEY_REF)
            services.settings.update { it.copy(weather = it.weather.copy(apiKeyRef = ref)) }
        }
    }

    fun restartWeather(settings: com.example.familyphotoframe.data.settings.WeatherSettings) {
        viewModelScope.launch {
            val key = if (settings.apiKeyRef.isBlank()) "" else services.secretStore.reveal(settings.apiKeyRef).orEmpty()
            services.weather.restart(viewModelScope, settings, key)
            refreshWeatherText()
        }
    }

    /**
     * Recompute the overlay string. Weather is decoration, so this only ever reads the
     * last snapshot — it never waits on the network and cannot delay a slide.
     */
    fun refreshWeatherText() {
        val settings = lastSettings?.weather ?: return
        val display = WeatherPresentation.resolve(
            snapshot = services.weather.snapshot.value,
            nowEpochMs = System.currentTimeMillis(),
            maxStaleMs = settings.maxStaleMs,
            units = settings.units,
            staleAfterMs = settings.staleAfterMs,
        )
        val text = (display as? WeatherDisplay.Visible)?.text
        _state.update { it.copy(weatherText = if (settings.enabled) text else null) }
    }

    private companion object {
        const val WEATHER_KEY_REF = CredentialPolicy.WEATHER_API_KEY_REF
    }
}
