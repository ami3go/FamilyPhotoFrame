package com.example.familyphotoframe.data.weather

import com.example.familyphotoframe.data.source.DeadlineInputStream
import com.example.familyphotoframe.data.source.toSocketTimeoutMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private fun InputStream.readWeatherText(maxChars: Int): String = reader(Charsets.UTF_8).use { reader ->
    val result = StringBuilder(minOf(maxChars, 8 * 1024))
    val buffer = CharArray(4 * 1024)
    while (true) {
        val remaining = maxChars - result.length
        if (remaining == 0) {
            if (reader.read() != -1) throw IOException("weather_response_too_large")
            break
        }
        val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
        if (count <= 0) break
        result.append(buffer, 0, count)
    }
    result.toString()
}

/** A source of current conditions. Implementations must never throw into the caller. */
interface WeatherProvider {
    suspend fun fetch(latitude: Double, longitude: Double, nowEpochMs: Long): WeatherFetchResult
}

sealed interface WeatherFetchResult {
    data class Success(val snapshot: WeatherSnapshot) : WeatherFetchResult
    data class Failure(
        val stage: String,
        val exceptionClass: String? = null,
        val httpStatus: Int? = null,
    ) : WeatherFetchResult
}

/**
 * Parses the Open-Meteo `current` response. Split out from the network code so the
 * mapping — including malformed and partial payloads — is unit-testable without HTTP.
 */
object OpenMeteoParser {

    @Serializable
    private data class Response(val current: Current? = null)

    @Serializable
    private data class Current(
        val temperature_2m: Double? = null,
        val weather_code: Int? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, nowEpochMs: Long): WeatherSnapshot? {
        val response = runCatching { json.decodeFromString(Response.serializer(), body) }.getOrNull()
            ?: return null
        val temperature = response.current?.temperature_2m ?: return null
        return WeatherSnapshot(
            temperatureC = temperature,
            weatherCode = response.current.weather_code ?: -1,
            observedAtEpochMs = nowEpochMs,
        )
    }

    /**
     * Build the request URL. [baseUrl] is configurable because Open-Meteo's free
     * endpoint is licensed for non-commercial use only — a commercial deployment must
     * point at the customer endpoint and supply [apiKey]. See WEATHER_LICENSING.md.
     */
    fun buildUrl(baseUrl: String, latitude: Double, longitude: Double, apiKey: String): String {
        val base = baseUrl.trimEnd('/')
        val key = if (apiKey.isBlank()) "" else "&apikey=$apiKey"
        return "$base?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code&timezone=auto$key"
    }
}

/**
 * Open-Meteo-compatible HTTP provider (spec §11).
 *
 * Every call is time-bounded and every ordinary failure is returned as structured data: a
 * weather outage must not surface as an error state, retry storm, or slideshow stall.
 * No location permission is involved — coordinates are entered by the user (spec §11,
 * and it keeps the permission audit clean).
 */
class OpenMeteoProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val io: CoroutineDispatcher,
    private val timeoutMs: Long = 8_000,
) : WeatherProvider {

    override suspend fun fetch(
        latitude: Double,
        longitude: Double,
        nowEpochMs: Long,
    ): WeatherFetchResult = withContext(io) {
        withTimeoutOrNull(timeoutMs) {
            try {
                val url = URL(OpenMeteoParser.buildUrl(baseUrl, latitude, longitude, apiKey))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs.toSocketTimeoutMillis()
                    readTimeout = timeoutMs.toSocketTimeoutMillis()
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val status = conn.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        WeatherFetchResult.Failure(stage = "http", httpStatus = status)
                    } else {
                        val body = DeadlineInputStream(conn.inputStream, timeoutMs)
                            .readWeatherText(MAX_WEATHER_RESPONSE_CHARS)
                        val snapshot = OpenMeteoParser.parse(body, nowEpochMs)
                        if (snapshot == null) {
                            WeatherFetchResult.Failure(stage = "parse")
                        } else {
                            WeatherFetchResult.Success(snapshot)
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WeatherFetchResult.Failure(
                    stage = "network",
                    exceptionClass = e.javaClass.simpleName,
                )
            }
        } ?: WeatherFetchResult.Failure(stage = "timeout", exceptionClass = "Timeout")
    }

    private companion object {
        const val MAX_WEATHER_RESPONSE_CHARS = 128 * 1024
    }
}
