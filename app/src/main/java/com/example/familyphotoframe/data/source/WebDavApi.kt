package com.example.familyphotoframe.data.source

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Protocol-shaped half of the WebDAV / Nextcloud source (ROADMAP.md "other candidate
 * sources"). Everything here is pure: URL construction, PROPFIND request/response
 * handling, and error mapping. Network-shaped work lives in [WebDavPhotoSource].
 *
 * The split is the same one the Synology source uses, and for the same reason the
 * roadmap gives: the protocol cannot be meaningfully exercised without a server, but the
 * *mapping* can be tested exhaustively without one, so the mapping is kept where tests
 * can reach it.
 *
 * **Why a hand-written XML reader.** WebDAV replies are namespaced, and real servers
 * disagree about the prefix: Nextcloud emits `d:`, Apache `mod_dav` emits `D:` and
 * `lp1:`, others emit none at all. A strict parser bound to one prefix breaks on the
 * next server; a namespace-aware DOM parse costs an Android dependency and still has to
 * be told which namespace to trust. Matching on local names, prefix-insensitively, is
 * both smaller and more tolerant of what servers actually send.
 */
object WebDavApi {

    const val MAX_PROPFIND_BYTES: Long = 32L * 1024L * 1024L
    const val MAX_RESPONSE_BYTES: Int = 256 * 1024
    private const val READ_BUFFER_CHARS = 8 * 1024
    private const val TAG_BOUNDARY_CHARS = 8 * 1024

    private val multistatusOpen = Regex(
        """<(?:[A-Za-z0-9_.-]+:)?multistatus\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val multistatusClose = Regex(
        """</(?:[A-Za-z0-9_.-]+:)?multistatus\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val responseOpen = Regex(
        """<(?:[A-Za-z0-9_.-]+:)?response\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val responseClose = Regex(
        """</(?:[A-Za-z0-9_.-]+:)?response\s*>""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Minimal PROPFIND body. Only the four properties the indexer needs are requested —
     * asking for `allprop` makes servers return large per-file property sets (ACLs,
     * share state, previews) that are parsed and thrown away on every scan.
     */
    const val PROPFIND_BODY: String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<d:propfind xmlns:d="DAV:"><d:prop>""" +
            """<d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getcontenttype/>""" +
            """</d:prop></d:propfind>"""

    /** One entry from a PROPFIND listing. [path] is server-absolute and already decoded. */
    data class Entry(
        val path: String,
        val name: String,
        val isDir: Boolean,
        val sizeBytes: Long,
        val modifiedEpochMs: Long,
        val contentType: String?,
    )

    /**
     * Normalize a user-typed server URL to an origin with no trailing slash.
     * Accepts `nas.local`, `https://nas.local/`, `https://nas.local/remote.php/dav/`.
     * A bare host defaults to HTTPS: this carries a password on every request, so
     * defaulting to cleartext would be the wrong way to be forgiving.
     */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val withScheme =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
            else "https://$trimmed"
        return withScheme.trimEnd('/')
    }

    /**
     * Standard Nextcloud/ownCloud files endpoint for [user]. Servers that are plain
     * WebDAV (not Nextcloud) take a directly configured root instead — see
     * [buildCollectionUrl] — so this is only a convenience default.
     */
    fun nextcloudFilesRoot(user: String): String =
        "/remote.php/dav/files/" + encodePath(user.trim())

    /** Absolute URL for a collection or file path below the configured root. */
    fun buildCollectionUrl(baseUrl: String, rootPath: String, relativePath: String = ""): String {
        val base = normalizeBaseUrl(baseUrl)
        val root = normalizePath(rootPath)
        val rel = normalizePath(relativePath)
        val joined = (root + rel).ifEmpty { "/" }
        return base + encodePath(joined)
    }

    /** Collapse duplicate separators and guarantee a single leading slash, no trailing one. */
    fun normalizePath(raw: String): String {
        val cleaned = raw.trim().replace('\\', '/').split('/').filter { it.isNotEmpty() }
        return if (cleaned.isEmpty()) "" else "/" + cleaned.joinToString("/")
    }

    /** Percent-encode each path segment, leaving separators intact. */
    fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(segment: String): String {
        val sb = StringBuilder(segment.length)
        for (b in segment.toByteArray(Charsets.UTF_8)) {
            // Mask first: Kotlin's Byte is signed, so a UTF-8 continuation byte such as
            // 0xC3 is negative, and its toChar() reports as a letter — which would emit
            // the raw byte instead of escaping it and corrupt every non-ASCII name.
            val v = b.toInt() and 0xFF
            val c = v.toChar()
            if (v < 0x80 && (c.isLetterOrDigit() || c in "-_.~")) sb.append(c)
            else sb.append('%').append("%02X".format(Locale.ROOT, v))
        }
        return sb.toString()
    }

    /** Decode a percent-encoded href back to a readable path. */
    fun decodePath(raw: String): String {
        if ('%' !in raw) return raw
        val out = java.io.ByteArrayOutputStream(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%' && i + 2 < raw.length) {
                val hex = raw.substring(i + 1, i + 3)
                val value = hex.toIntOrNull(16)
                if (value != null) { out.write(value); i += 3; continue }
            }
            out.write(c.code)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * Parse a `multistatus` PROPFIND response into entries.
     *
     * [requestPath] is the decoded server path that was listed; the entry describing the
     * collection itself is dropped, since Depth:1 always echoes it back and indexing it
     * would make every folder contain itself.
     *
     * Returns null when the payload is not a multistatus document at all, which is how
     * an HTML error page or a login redirect is distinguished from an empty folder.
     */
    fun parsePropfind(xml: String, requestPath: String): List<Entry>? =
        ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)).use { input ->
            val listing = parsePropfind(input, requestPath)
            val entries = listing.entries.toList()
            if (listing.isValid) entries else null
        }

    /**
     * Single-use, pull-style streamed listing. Iteration retains only the current
     * `<response>` block and updates [isValid] after the root closes cleanly.
     */
    class PropfindListing internal constructor(
        input: InputStream,
        requestPath: String,
        maximumWireBytes: Long,
        maximumResponseBytes: Int,
    ) {
        @Volatile var isValid: Boolean = false
            private set

        val entries: Sequence<Entry> = sequence {
            val reader = InputStreamReader(
                WireLimitedInputStream(input, maximumWireBytes),
                Charsets.UTF_8,
            )
            val chars = CharArray(READ_BUFFER_CHARS)
            val buffer = StringBuilder(READ_BUFFER_CHARS)
            val self = normalizePath(requestPath)
            var eof = false
            var sawRoot = false
            var closedRoot = false

            fun fill(): Boolean {
                val count = reader.read(chars)
                if (count < 0) {
                    eof = true
                    return false
                }
                buffer.append(chars, 0, count)
                return true
            }

            try {
                while (!closedRoot) {
                    if (!sawRoot) {
                        val root = multistatusOpen.find(buffer)
                        if (root == null) {
                            if (eof) break
                            retainTagBoundary(buffer)
                            fill()
                            continue
                        }
                        val rootTag = buffer.substring(root.range.first, root.range.last + 1)
                        buffer.delete(0, root.range.last + 1)
                        sawRoot = true
                        if (rootTag.trimEnd().endsWith("/>")) {
                            closedRoot = true
                            break
                        }
                    }

                    val response = responseOpen.find(buffer)
                    val rootEnd = multistatusClose.find(buffer)
                    if (rootEnd != null && (response == null || rootEnd.range.first < response.range.first)) {
                        closedRoot = true
                        break
                    }

                    if (response == null) {
                        if (eof) break
                        retainTagBoundary(buffer)
                        fill()
                        continue
                    }

                    if (response.range.first > 0) buffer.delete(0, response.range.first)
                    val responseEnd = responseClose.find(buffer)
                    if (responseEnd == null) {
                        if (utf8Length(buffer, buffer.length) > maximumResponseBytes) {
                            throw WebDavLimitException("response_element_too_large")
                        }
                        if (eof) break
                        fill()
                        continue
                    }

                    val endExclusive = responseEnd.range.last + 1
                    if (utf8Length(buffer, endExclusive) > maximumResponseBytes) {
                        throw WebDavLimitException("response_element_too_large")
                    }
                    val block = buffer.substring(0, endExclusive)
                    buffer.delete(0, endExclusive)
                    val entry = parseEntryBlock(block, self)
                    if (entry != null) yield(entry)
                }
                isValid = sawRoot && closedRoot
            } catch (t: Throwable) {
                isValid = false
                throw t
            }
        }
    }

    fun parsePropfind(
        input: InputStream,
        requestPath: String,
        maximumWireBytes: Long = MAX_PROPFIND_BYTES,
        maximumResponseBytes: Int = MAX_RESPONSE_BYTES,
    ): PropfindListing {
        require(maximumWireBytes > 0L)
        require(maximumResponseBytes > 0)
        return PropfindListing(input, requestPath, maximumWireBytes, maximumResponseBytes)
    }

    private fun parseEntryBlock(block: String, self: String): Entry? {
        val href = tagValue(block, "href") ?: return null
        val path = normalizePath(decodePath(hrefToPath(href)))
        if (path.equals(self, ignoreCase = true)) return null
        val name = path.substringAfterLast('/')
        if (name.isEmpty()) return null
        return Entry(
            path = path,
            name = name,
            isDir = block.contains("collection", ignoreCase = true),
            sizeBytes = tagValue(block, "getcontentlength")?.trim()?.toLongOrNull() ?: 0L,
            modifiedEpochMs = parseHttpDate(tagValue(block, "getlastmodified")) ?: 0L,
            contentType = tagValue(block, "getcontenttype")?.trim()?.ifBlank { null },
        )
    }

    private fun retainTagBoundary(buffer: StringBuilder) {
        if (buffer.length > TAG_BOUNDARY_CHARS) {
            buffer.delete(0, buffer.length - TAG_BOUNDARY_CHARS)
        }
    }

    private fun utf8Length(chars: CharSequence, endExclusive: Int): Int {
        var bytes = 0
        var index = 0
        while (index < endExclusive) {
            val ch = chars[index]
            when {
                ch.code < 0x80 -> bytes += 1
                ch.code < 0x800 -> bytes += 2
                Character.isHighSurrogate(ch) && index + 1 < endExclusive &&
                    Character.isLowSurrogate(chars[index + 1]) -> {
                    bytes += 4
                    index++
                }
                else -> bytes += 3
            }
            index++
        }
        return bytes
    }

    /**
     * Reduce an href to its path. The spec permits either a full URL or an absolute
     * path, and servers use both — Nextcloud sends a path, some proxies rewrite it to an
     * absolute URL — so both have to work.
     */
    fun hrefToPath(href: String): String {
        val trimmed = href.trim()
        val schemeIndex = trimmed.indexOf("://")
        if (schemeIndex < 0) return trimmed
        val afterAuthority = trimmed.indexOf('/', schemeIndex + 3)
        return if (afterAuthority < 0) "/" else trimmed.substring(afterAuthority)
    }

    /** First value of a tag, ignoring any namespace prefix. */
    fun tagValue(xml: String, localName: String): String? {
        val re = Regex(
            "<(?:[A-Za-z0-9_.-]+:)?$localName[^>]*>(.*?)</(?:[A-Za-z0-9_.-]+:)?$localName>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return re.find(xml)?.groupValues?.get(1)?.let(::unescapeXml)
    }

    private fun unescapeXml(raw: String): String = raw
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")

    /** RFC 1123 date as sent in `getlastmodified`, or null when absent/unparseable. */
    fun parseHttpDate(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /**
     * Map an HTTP status onto the shared taxonomy (spec §8.2).
     *
     * 401 and 403 are deliberately different: 401 means the credentials are wrong and
     * the user must re-enter them, while 403 means they are right but the account cannot
     * read this folder — telling someone to retype a correct password is worse than
     * unhelpful. 423 (Locked) is mapped to a retryable error rather than a hard failure
     * because Nextcloud returns it transiently during server-side file operations.
     */
    fun mapStatus(status: Int): SourceError = when (status) {
        in 200..299 -> SourceError.Unknown  // caller should not be mapping a success
        401 -> SourceError.AuthFailed
        403 -> SourceError.PermissionDenied
        404 -> SourceError.FileGone
        405 -> SourceError.ProtocolError    // PROPFIND not supported: not a WebDAV endpoint
        423 -> SourceError.Timeout
        in 500..599 -> SourceError.ProviderError
        else -> SourceError.ProtocolError
    }

    fun isSuccess(status: Int): Boolean = status in 200..299

    /**
     * Remove userinfo from a URL before it reaches a log or an error message. Credentials
     * normally travel in an `Authorization` header, but a user may paste a URL of the
     * form `https://user:pass@host/`, and that must never reach diagnostics (§17.2).
     */
    fun redactUserInfo(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val afterScheme = schemeEnd + 3
        val at = url.indexOf('@', afterScheme)
        if (at < 0) return url
        val slash = url.indexOf('/', afterScheme)
        if (slash in 0 until at) return url   // '@' is in the path, not the authority
        return url.substring(0, afterScheme) + "***@" + url.substring(at + 1)
    }
}

class WebDavLimitException(reason: String) : IOException(reason)

private class WireLimitedInputStream(
    input: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) account(count)
        return count
    }

    private fun account(count: Int) {
        consumed += count.toLong()
        if (consumed > maximumBytes) throw WebDavLimitException("propfind_response_too_large")
    }
}
