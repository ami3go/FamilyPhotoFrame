package com.example.familyphotoframe.data.index

import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

/**
 * EXIF metadata pulled from a single photo (Phase 2 increment 5, spec §11). All
 * fields are best-effort and nullable/default — a photo with no EXIF block, or one
 * that fails to parse, simply yields an all-default [ExifMetadata]; callers never
 * treat a missing value as an error (spec §8 "failures never touch the slideshow"
 * posture, same as the weather overlay).
 */
data class ExifMetadata(
    val width: Int? = null,
    val height: Int? = null,
    /**
     * EXIF orientation 1–8, or [ExifInterface.ORIENTATION_UNDEFINED] (0) when the tag is
     * absent. 0 means "unknown", NOT "normal" — a photo with no orientation tag must not
     * be recorded as if it had explicitly declared itself upright, since `photos
     * .exifOrientation` also stores 0 for never-read rows. Keeping both paths on 0 means
     * whoever wires up rotation later sees one unambiguous "unknown" value.
     */
    val orientation: Int = ExifInterface.ORIENTATION_UNDEFINED,
    val dateTakenEpochMs: Long? = null,
    val caption: String? = null,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
)

/**
 * Pure parsing/formatting helpers with no Android framework dependency, so they are
 * unit-testable on the plain JVM (same split as [com.example.familyphotoframe.ui.render.ImageBlur]
 * / `KenBurns`: Android glue stays thin and untested at unit level; the logic that can
 * be pure, is).
 */
object ExifParsing {

    /** The standard EXIF `DateTimeOriginal`/`DateTime` layout: `yyyy:MM:dd HH:mm:ss`. */
    private val EXIF_DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * Parses an EXIF date/time tag into epoch millis. [offset] is the optional
     * `OffsetTimeOriginal`/`OffsetTime` companion tag (e.g. `"+02:00"`, API-level
     * independent because it is read as a plain string tag). When no offset is
     * present the value is interpreted in the device's current local time zone —
     * EXIF has no reliable zone of its own, so this is a documented best-effort
     * choice, not a claim of precision.
     *
     * Returns null for blank/malformed input rather than throwing, so a single bad
     * tag never fails indexing of the file.
     */
    fun parseExifDateTime(raw: String?, offset: String? = null, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = raw?.trim()
        // "0000:00:00 ..." is the common all-zero placeholder; anything else malformed is
        // caught by the parse below rather than by guessing at more sentinel strings.
        if (text.isNullOrEmpty() || text.startsWith("0000")) return null
        return try {
            val local = LocalDateTime.parse(text, EXIF_DATE_FORMAT)
            val offsetText = offset?.trim()
            if (!offsetText.isNullOrEmpty()) {
                val parsedOffset = try {
                    ZoneOffset.of(offsetText)
                } catch (e: DateTimeException) {
                    null
                }
                if (parsedOffset != null) {
                    return OffsetDateTime.of(local, parsedOffset).toInstant().toEpochMilli()
                }
            }
            local.atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Filters out captions that are blank or obviously placeholder junk some cameras
     * write by default into `ImageDescription` (e.g. an unedited default string).
     */
    fun cleanCaption(raw: String?): String? {
        val text = raw?.trim() ?: return null
        if (text.isEmpty()) return null
        if (text.all { it == '-' || it == '_' || it.isWhitespace() }) return null
        return text.take(280)
    }

    /**
     * Formats decimal-degree GPS coordinates for on-screen display, e.g.
     * `"40.7128°N, 74.0060°W"`. No reverse geocoding / network lookup is performed —
     * the frame is local-first and this never requests a location permission or makes
     * a network call (spec §11).
     */
    fun formatGpsCoordinate(lat: Double, lon: Double): String {
        val latHemi = if (lat >= 0) "N" else "S"
        val lonHemi = if (lon >= 0) "E" else "W"
        // Locale.ROOT, not the default locale: coordinates are a technical notation and
        // must always use a dot decimal separator. Kotlin's String.format() would
        // otherwise render "40,7128" on e.g. a German or French device.
        return String.format(Locale.ROOT, "%.4f°%s, %.4f°%s", abs(lat), latHemi, abs(lon), lonHemi)
    }
}

/** Thin androidx.exifinterface wrapper — the only Android-framework-dependent part. */
object ExifExtractor {

    fun extract(stream: InputStream): ExifMetadata {
        val exif = ExifInterface(stream)

        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

        val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val offsetStr = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
        val dateTakenEpochMs = ExifParsing.parseExifDateTime(dateStr, offsetStr)

        val caption = ExifParsing.cleanCaption(exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))

        val latLong = exif.latLong // DoubleArray? [lat, lon], null if absent/invalid
        val gpsLat = latLong?.getOrNull(0)
        val gpsLon = latLong?.getOrNull(1)

        return ExifMetadata(
            width = width,
            height = height,
            orientation = orientation,
            dateTakenEpochMs = dateTakenEpochMs,
            caption = caption,
            gpsLat = gpsLat,
            gpsLon = gpsLon,
        )
    }
}
