package com.example.familyphotoframe.data.index

import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExifParsingTest {

    @Test fun parsesStandardExifDateTimeWithExplicitOffset() {
        val ms = ExifParsing.parseExifDateTime("2024:07:04 08:30:00", "+02:00")
        val expected = java.time.OffsetDateTime.of(2024, 7, 4, 8, 30, 0, 0, ZoneOffset.ofHours(2))
            .toInstant().toEpochMilli()
        assertEquals(expected, ms)
    }

    @Test fun parsesStandardExifDateTimeWithoutOffset_usingGivenZone() {
        val zone = ZoneOffset.UTC
        val ms = ExifParsing.parseExifDateTime("2024:07:04 08:30:00", offset = null, zone = zone)
        val expected = java.time.LocalDateTime.of(2024, 7, 4, 8, 30, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ms)
    }

    @Test fun blankOrNullDateReturnsNull() {
        assertNull(ExifParsing.parseExifDateTime(null))
        assertNull(ExifParsing.parseExifDateTime(""))
        assertNull(ExifParsing.parseExifDateTime("   "))
    }

    @Test fun zeroedPlaceholderDateReturnsNull() {
        assertNull(ExifParsing.parseExifDateTime("0000:00:00 00:00:00"))
    }

    @Test fun malformedDateReturnsNullInsteadOfThrowing() {
        assertNull(ExifParsing.parseExifDateTime("not a date"))
        assertNull(ExifParsing.parseExifDateTime("2024-07-04 08:30:00")) // wrong separator
    }

    @Test fun garbageOffsetFallsBackToZoneInterpretation() {
        val zone = ZoneOffset.UTC
        val ms = ExifParsing.parseExifDateTime("2024:07:04 08:30:00", offset = "nonsense", zone = zone)
        val expected = java.time.LocalDateTime.of(2024, 7, 4, 8, 30, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ms)
    }

    @Test fun cleanCaptionTrimsAndKeepsRealText() {
        assertEquals("Family reunion", ExifParsing.cleanCaption("  Family reunion  "))
    }

    @Test fun cleanCaptionRejectsBlankAndPlaceholderJunk() {
        assertNull(ExifParsing.cleanCaption(null))
        assertNull(ExifParsing.cleanCaption(""))
        assertNull(ExifParsing.cleanCaption("   "))
        assertNull(ExifParsing.cleanCaption("----"))
        assertNull(ExifParsing.cleanCaption("___"))
    }

    @Test fun cleanCaptionTruncatesVeryLongText() {
        val long = "x".repeat(500)
        assertEquals(280, ExifParsing.cleanCaption(long)!!.length)
    }

    @Test fun formatsGpsCoordinateForAllQuadrants() {
        assertEquals("40.7128°N, 74.0060°W", ExifParsing.formatGpsCoordinate(40.7128, -74.0060))
        assertEquals("33.8688°S, 151.2093°E", ExifParsing.formatGpsCoordinate(-33.8688, 151.2093))
        assertEquals("0.0000°N, 0.0000°E", ExifParsing.formatGpsCoordinate(0.0, 0.0))
    }

    /**
     * Regression guard: coordinates must use a dot decimal separator regardless of the
     * JVM default locale. A plain `"%.4f".format(...)` picks up `Locale.getDefault()`
     * and renders "40,7128" under e.g. German, which would both look wrong on-device and
     * make the assertions above fail on a European-locale CI machine.
     */
    @Test fun gpsCoordinateFormattingIsLocaleIndependent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("40.7128°N, 74.0060°W", ExifParsing.formatGpsCoordinate(40.7128, -74.0060))
        } finally {
            Locale.setDefault(original)
        }
    }
}
