package com.example.familyphotoframe.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Weather overlay (spec §11). The behaviour that matters most is the failure path:
 * weather is decoration, so a bad payload or an old snapshot must degrade quietly and
 * never surface as an error or block the slideshow.
 */
class WeatherTest {

    private val now = 1_700_000_000_000L
    private fun snap(tempC: Double = 12.0, code: Int = 3, ageMs: Long = 0) =
        WeatherSnapshot(tempC, code, now - ageMs)

    // ---- parsing ----

    @Test fun parsesCurrentConditions() {
        val body = """{"current":{"temperature_2m":12.4,"weather_code":61}}"""
        val s = OpenMeteoParser.parse(body, now)!!
        assertEquals(12.4, s.temperatureC, 0.001)
        assertEquals(61, s.weatherCode)
        assertEquals(now, s.observedAtEpochMs)
    }

    @Test fun toleratesUnknownFields() {
        val body = """{"latitude":51.5,"generationtime_ms":0.2,"current":{"time":"x","temperature_2m":7.0,"weather_code":0,"extra":9}}"""
        assertEquals(7.0, OpenMeteoParser.parse(body, now)!!.temperatureC, 0.001)
    }

    @Test fun missingWeatherCodeStillParses() {
        val s = OpenMeteoParser.parse("""{"current":{"temperature_2m":5.0}}""", now)!!
        assertEquals(-1, s.weatherCode)
        assertEquals("", WeatherCodes.label(s.weatherCode))
    }

    @Test fun malformedPayloadsReturnNull() {
        listOf("", "not json", "{}", """{"current":{}}""", """{"current":{"weather_code":3}}""")
            .forEach { assertNull("should not parse: $it", OpenMeteoParser.parse(it, now)) }
    }

    // ---- URL building ----

    @Test fun urlCarriesCoordinatesAndFields() {
        val url = OpenMeteoParser.buildUrl("https://api.example.com/v1/forecast", 51.5074, -0.1278, "")
        assertTrue(url.contains("latitude=51.5074"))
        assertTrue(url.contains("longitude=-0.1278"))
        assertTrue(url.contains("temperature_2m"))
        assertTrue(url.contains("weather_code"))
        assertFalse("no key means no apikey parameter", url.contains("apikey"))
    }

    @Test fun apiKeyIsAppendedOnlyWhenPresent() {
        val url = OpenMeteoParser.buildUrl("https://customer-api.example.com/v1/forecast", 1.0, 2.0, "abc123")
        assertTrue(url.contains("apikey=abc123"))
    }

    @Test fun trailingSlashDoesNotDoubleUp() {
        val url = OpenMeteoParser.buildUrl("https://api.example.com/v1/forecast/", 1.0, 2.0, "")
        assertFalse(url.contains("forecast/?"))
    }

    // ---- presentation / failure semantics ----

    @Test fun hiddenWhenNoSnapshotYet() {
        assertEquals(
            WeatherDisplay.Hidden,
            WeatherPresentation.resolve(null, now, 6 * 3_600_000L, TemperatureUnits.CELSIUS, 90 * 60_000L),
        )
    }

    @Test fun visibleAndFreshForRecentSnapshot() {
        val d = WeatherPresentation.resolve(
            snap(ageMs = 60_000), now, 6 * 3_600_000L, TemperatureUnits.CELSIUS, 90 * 60_000L,
        ) as WeatherDisplay.Visible
        assertFalse(d.stale)
    }

    @Test fun markedStaleButStillShownAfterThreshold() {
        // A brief outage should show slightly old data, not blank the overlay.
        val d = WeatherPresentation.resolve(
            snap(ageMs = 2 * 3_600_000L), now, 6 * 3_600_000L, TemperatureUnits.CELSIUS, 90 * 60_000L,
        ) as WeatherDisplay.Visible
        assertTrue(d.stale)
    }

    @Test fun hiddenOnceBeyondMaxAge() {
        assertEquals(
            WeatherDisplay.Hidden,
            WeatherPresentation.resolve(
                snap(ageMs = 7 * 3_600_000L), now, 6 * 3_600_000L, TemperatureUnits.CELSIUS, 90 * 60_000L,
            ),
        )
    }

    // ---- formatting ----

    @Test fun formatsCelsius() {
        assertEquals("12\u00B0C  Overcast", WeatherPresentation.format(snap(12.4, 3), TemperatureUnits.CELSIUS))
    }

    @Test fun convertsToFahrenheit() {
        assertEquals("32\u00B0F  Clear", WeatherPresentation.format(snap(0.0, 0), TemperatureUnits.FAHRENHEIT))
        assertEquals("212\u00B0F  Clear", WeatherPresentation.format(snap(100.0, 0), TemperatureUnits.FAHRENHEIT))
    }

    @Test fun omitsLabelForUnknownCode() {
        assertEquals("5\u00B0C", WeatherPresentation.format(snap(5.0, 12345), TemperatureUnits.CELSIUS))
    }

    @Test fun roundsRatherThanTruncates() {
        assertEquals("13\u00B0C  Clear", WeatherPresentation.format(snap(12.6, 0), TemperatureUnits.CELSIUS))
        assertEquals("-3\u00B0C  Clear", WeatherPresentation.format(snap(-2.6, 0), TemperatureUnits.CELSIUS))
    }

    // ---- code labels ----

    @Test fun labelsCoverCommonConditions() {
        assertEquals("Clear", WeatherCodes.label(0))
        assertEquals("Fog", WeatherCodes.label(45))
        assertEquals("Rain", WeatherCodes.label(63))
        assertEquals("Snow", WeatherCodes.label(75))
        assertEquals("Thunderstorm", WeatherCodes.label(95))
        assertEquals("", WeatherCodes.label(-1))
    }
}
