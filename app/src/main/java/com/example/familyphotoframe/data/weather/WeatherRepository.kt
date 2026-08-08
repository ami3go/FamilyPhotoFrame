package com.example.familyphotoframe.data.weather

import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.settings.WeatherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the weather refresh loop (spec §11).
 *
 * Design rules, all driven by "weather must never affect the slideshow":
 *  - runs in its own coroutine; nothing in the display path ever awaits it;
 *  - a failed refresh keeps the previous snapshot rather than clearing it, so a brief
 *    outage shows slightly stale data instead of flickering the overlay away;
 *  - the snapshot is dropped only once it exceeds the caller's staleness budget;
 *  - failures are logged as codes only and never retried aggressively.
 */
class WeatherRepository(
    private val diagnostics: DiagnosticsLog,
    private val providerFactory: (WeatherSettings, String) -> WeatherProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    private var job: Job? = null

    /** Restart the loop for [settings]; stops and clears when weather is disabled. */
    /** [apiKey] is resolved from the Keystore by the caller; it is never stored in settings. */
    fun restart(scope: CoroutineScope, settings: WeatherSettings, apiKey: String) {
        job?.cancel()
        if (!settings.enabled || !settings.hasValidCoordinates) {
            _snapshot.value = null
            return
        }
        val provider = providerFactory(settings, apiKey)
        job = scope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                val result = provider.fetch(settings.latitude, settings.longitude, clock())
                val failure = result as? WeatherFetchResult.Failure
                when (result) {
                    is WeatherFetchResult.Success -> {
                        consecutiveFailures = 0
                        _snapshot.value = result.snapshot
                        diagnostics.log(DiagnosticsLog.Category.APP, "WEATHER_OK")
                    }
                    is WeatherFetchResult.Failure -> {
                        consecutiveFailures++
                        // Keep the previous snapshot; presentation decides when it is too old.
                    }
                }
                // Back off on repeated failure so a dead endpoint is not hammered.
                val minutes = if (consecutiveFailures == 0) {
                    settings.refreshMinutesClamped
                } else {
                    (settings.refreshMinutesClamped * (1 shl consecutiveFailures.coerceAtMost(3)))
                        .coerceAtMost(MAX_BACKOFF_MINUTES)
                }
                if (failure != null) {
                    diagnostics.log(
                        DiagnosticsLog.Category.APP, "WEATHER_FETCH_FAILED",
                        "attempt" to consecutiveFailures.toString(),
                        "stage" to failure.stage,
                        "exception" to failure.exceptionClass.orEmpty(),
                        "httpStatus" to failure.httpStatus?.toString().orEmpty(),
                        "retryMinutes" to minutes.toString(),
                    )
                }
                delay(minutes.toLong() * 60_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val MAX_BACKOFF_MINUTES = 120
    }
}
