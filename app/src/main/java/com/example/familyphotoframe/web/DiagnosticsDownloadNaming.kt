package com.example.familyphotoframe.web

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** UTC support-bundle filename used by HTTP headers and browser fallbacks. */
object DiagnosticsDownloadNaming {
    fun fileName(epochMs: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd'T'HHmmssSSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return "FamilyPhotoFrame-diagnostics-${formatter.format(Date(epochMs))}.jsonl"
    }
}
