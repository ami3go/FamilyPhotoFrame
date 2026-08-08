package com.example.familyphotoframe.data.source

/**
 * Pure request-building, response-parsing and error-mapping for the Synology
 * **File Station** API (ROADMAP.md → "Network photo-app sources", step 1).
 *
 * Deliberately split from the HTTP transport, exactly as [OpenMeteoParser] is split
 * from `OpenMeteoProvider`: the roadmap calls for "a thin, mockable HTTP layer so the
 * *mapping* logic can be tested without a live NAS", and everything here is
 * dependency-free Kotlin that runs under `scripts/verify-pure-logic.sh`.
 *
 * File Station is the official, documented API and is the deliberate first step; the
 * unofficial `SYNO.Foto.*` album API is a follow-on (see ROADMAP.md for why).
 *
 * Contract note: a session id (`sid`) is as sensitive as a password. [redactSid] exists
 * so URLs can be logged or surfaced in errors without leaking one (Contract Rule 5).
 */
object SynologyApi {

    const val PATH_ENTRY = "/webapi/entry.cgi"
    const val PATH_AUTH = "/webapi/auth.cgi"

    /** Page size for directory listings; File Station caps very large pages. */
    const val LIST_PAGE_SIZE = 100

    // ---- URL building -----------------------------------------------------------

    /** Normalizes a user-entered base URL: adds a scheme, strips any trailing slash. */
    fun normalizeBaseUrl(raw: String, useHttps: Boolean = true): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else (if (useHttps) "https://" else "http://") + trimmed
    }

    /**
     * Login. [otpCode] is the optional 2FA one-time code. `format=sid` returns the
     * session id in the JSON body rather than as a cookie, which keeps it out of any
     * shared cookie jar.
     */
    fun buildAuthUrl(baseUrl: String, user: String, password: String, otpCode: String? = null): String {
        val sb = StringBuilder(normalizeBaseUrl(baseUrl))
            .append(PATH_AUTH)
            .append("?api=SYNO.API.Auth&version=6&method=login")
            .append("&account=").append(encode(user))
            .append("&passwd=").append(encode(password))
            .append("&session=FileStation&format=sid")
        if (!otpCode.isNullOrBlank()) sb.append("&otp_code=").append(encode(otpCode.trim()))
        return sb.toString()
    }

    fun buildLogoutUrl(baseUrl: String, sid: String): String =
        normalizeBaseUrl(baseUrl) + PATH_AUTH +
            "?api=SYNO.API.Auth&version=1&method=logout&session=FileStation&_sid=" + encode(sid)

    /** One page of a folder listing. Only the fields the indexer needs are requested. */
    fun buildListUrl(baseUrl: String, sid: String, folderPath: String, offset: Int, limit: Int = LIST_PAGE_SIZE): String =
        normalizeBaseUrl(baseUrl) + PATH_ENTRY +
            "?api=SYNO.FileStation.List&version=2&method=list" +
            "&folder_path=" + encode(folderPath) +
            "&offset=" + offset + "&limit=" + limit +
            "&additional=" + encode("[\"size\",\"time\"]") +
            "&_sid=" + encode(sid)

    /** Full-resolution download of one file. */
    fun buildDownloadUrl(baseUrl: String, sid: String, path: String): String =
        normalizeBaseUrl(baseUrl) + PATH_ENTRY +
            "?api=SYNO.FileStation.Download&version=2&method=download&mode=open" +
            "&path=" + encode(path) +
            "&_sid=" + encode(sid)

    /**
     * Server-side thumbnail. This is the main reason to prefer File Station over raw
     * SMB: the NAS returns an already-generated, right-sized JPEG, so the frame does not
     * pull full-res originals (or HEIC/RAW the device may not decode at all).
     *
     * [size] is File Station's size code — `small`, `medium`, `large`, `original`.
     */
    fun buildThumbnailUrl(baseUrl: String, sid: String, path: String, size: String = "large"): String =
        normalizeBaseUrl(baseUrl) + PATH_ENTRY +
            "?api=SYNO.FileStation.Thumb&version=2&method=get" +
            "&path=" + encode(path) +
            "&size=" + encode(size) +
            "&_sid=" + encode(sid)

    /** Replaces the `_sid` value so a URL can be logged or shown in an error safely. */
    fun redactSid(url: String): String =
        Regex("(_sid=)[^&]*").replace(url) { "${it.groupValues[1]}REDACTED" }

    // ---- Response parsing -------------------------------------------------------

    /**
     * Minimal JSON reading. The API returns small, flat, predictable payloads, and the
     * project's rule is "no new third-party dependency" for this track, so rather than
     * pull in a parser these read the handful of fields actually needed. Anything
     * unexpected yields null / empty and is treated as a protocol error upstream.
     */
    internal fun readBool(json: String, key: String): Boolean? =
        Regex("\"$key\"\\s*:\\s*(true|false)").find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()

    internal fun readInt(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    internal fun readString(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\/", "/")?.replace("\\\"", "\"")

    /** True when the API reported `"success": true`. */
    fun isSuccess(json: String): Boolean = readBool(json, "success") == true

    /** The `error.code` value, or null when the response did not report one. */
    fun errorCode(json: String): Int? {
        val errObj = Regex("\"error\"\\s*:\\s*\\{[^}]*}").find(json)?.value ?: return null
        return readInt(errObj, "code")
    }

    /** Extracts the session id from a successful login response. */
    fun parseSid(json: String): String? = if (isSuccess(json)) readString(json, "sid") else null

    /** One file entry from a listing. Directories are reported so the scan can recurse. */
    data class Entry(
        val path: String,
        val name: String,
        val isDir: Boolean,
        val sizeBytes: Long,
        val modifiedEpochMs: Long,
    )

    data class ListPage(val entries: List<Entry>, val total: Int)

    /**
     * Parses a `SYNO.FileStation.List` page. File Station reports `time.mtime` in
     * **seconds**; the rest of the app works in epoch millis, so it is converted here
     * rather than at each call site.
     */
    fun parseListPage(json: String): ListPage? {
        if (!isSuccess(json)) return null
        val total = readInt(json, "total") ?: 0
        val entries = splitObjects(json, "files").mapNotNull { obj ->
            val path = readString(obj, "path") ?: return@mapNotNull null
            val name = readString(obj, "name") ?: path.substringAfterLast('/')
            Entry(
                path = path,
                name = name,
                isDir = readBool(obj, "isdir") ?: false,
                sizeBytes = Regex("\"size\"\\s*:\\s*(\\d+)").find(obj)?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                modifiedEpochMs = (Regex("\"mtime\"\\s*:\\s*(\\d+)").find(obj)
                    ?.groupValues?.get(1)?.toLongOrNull() ?: 0L) * 1000L,
            )
        }
        return ListPage(entries, total)
    }

    /** Splits the objects inside the `"<key>": [ ... ]` array, respecting nesting. */
    internal fun splitObjects(json: String, key: String): List<String> {
        val start = json.indexOf("\"$key\"").takeIf { it >= 0 } ?: return emptyList()
        val arrayStart = json.indexOf('[', start).takeIf { it >= 0 } ?: return emptyList()
        val out = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        var inString = false
        var escaped = false
        for (i in arrayStart until json.length) {
            val c = json[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> {}
                c == '{' -> { if (depth == 0) objStart = i; depth++ }
                c == '}' -> { depth--; if (depth == 0 && objStart >= 0) { out.add(json.substring(objStart, i + 1)); objStart = -1 } }
                c == ']' && depth == 0 -> return out
            }
        }
        return out
    }

    // ---- Error mapping ----------------------------------------------------------

    /**
     * Maps a Synology API error code onto the app's typed taxonomy (spec §8.2).
     * Codes are from the official File Station and Auth API documentation; anything
     * unrecognised becomes [SourceError.ProtocolError] rather than being guessed at.
     */
    fun mapError(code: Int?): SourceError = when (code) {
        null -> SourceError.ProtocolError
        // --- common API codes ---
        105 -> SourceError.PermissionDenied             // insufficient user privilege
        106, 107 -> SourceError.SessionExpired          // timeout / duplicate-login interrupt
        119 -> SourceError.SessionExpired               // sid not found or invalid
        // --- SYNO.API.Auth codes ---
        400, 401 -> SourceError.AuthFailed              // no such account / bad password
        402 -> SourceError.PermissionDenied             // account disabled
        403, 404, 406 -> SourceError.TwoFactorRequired  // 2FA required / wrong code / not enrolled
        407 -> SourceError.PermissionDenied             // IP blocked by auto-block
        408, 409, 410 -> SourceError.AuthFailed         // password expired / must be changed
        // --- SYNO.FileStation.* codes ---
        414, 416 -> SourceError.FileGone                // path no longer exists
        1002, 1003 -> SourceError.PermissionDenied      // file operation not permitted
        else -> SourceError.ProtocolError
    }

    /** Percent-encoding for query values, without depending on `java.net.URLEncoder`. */
    internal fun encode(value: String): String {
        val safe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
        val sb = StringBuilder(value.length)
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt().toChar()
            if (safe.indexOf(c) >= 0) sb.append(c)
            else sb.append('%').append("0123456789ABCDEF"[(b.toInt() shr 4) and 0xF])
                .append("0123456789ABCDEF"[b.toInt() and 0xF])
        }
        return sb.toString()
    }
}
