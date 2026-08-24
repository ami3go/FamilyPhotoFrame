package com.example.familyphotoframe.data.source

import com.example.familyphotoframe.util.Glob
import com.example.familyphotoframe.util.StableId
import com.example.familyphotoframe.util.SupportedFormats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FilterInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.coroutineContext

/** Non-secret connection settings for a WebDAV / Nextcloud server. */
data class WebDavConnection(
    val baseUrl: String,
    /**
     * Server-absolute root of the DAV endpoint, e.g. `/remote.php/dav/files/alice` for
     * Nextcloud or `/dav` for a plain Apache mount. Kept separate from [folderPath] so a
     * user can browse different folders without re-deriving the endpoint.
     */
    val rootPath: String,
    /** Folder below [rootPath] to scan; empty means the root itself. */
    val folderPath: String = "",
    val connectionTimeoutMs: Long = 8_000,
    val readTimeoutMs: Long = 20_000,
    /** SHA-256 fingerprint of a self-signed certificate the user explicitly approved. */
    val pinnedCertSha256: String? = null,
)

/** Resolved just before use; never persisted here as plaintext (Contract Rule 5). */
data class WebDavCredentials(val user: String, val password: String)

/**
 * The mockable seam, matching [SynologyHttpClient]: protocol logic lives in [WebDavApi],
 * network logic lives behind this.
 */
interface WebDavHttpClient {
    suspend fun propfind(url: String, depth: Int, timeoutMs: Long): WebDavStreamResponse
    suspend fun openStream(url: String, timeoutMs: Long): InputStream
}

data class WebDavStreamResponse(val status: Int, val body: InputStream)

/**
 * Default transport on the existing stack — no new third-party dependency, and so no
 * additional license obligation (contrast jcifs-ng, see LGPL_COMPLIANCE.md).
 *
 * Credentials go in an `Authorization: Basic` header on every request. WebDAV has no
 * session concept, so unlike Synology there is no sid to hold, expire, or leak — which
 * removes a whole class of long-running failure, at the cost of sending the credential
 * each time. That is exactly why the setup UI pushes HTTPS.
 */
class UrlConnectionWebDavClient(
    private val io: CoroutineDispatcher,
    private val credentials: WebDavCredentials,
    private val pinnedCertSha256: String? = null,
) : WebDavHttpClient {

    private val socketFactory by lazy {
        if (pinnedCertSha256.isNullOrBlank()) null else CertPinning.socketFactory(pinnedCertSha256)
    }

    /** Precomputed: rebuilding the Base64 header per request is needless work on a frame. */
    private val authHeader: String by lazy {
        val raw = "${credentials.user}:${credentials.password}".toByteArray(Charsets.UTF_8)
        "Basic " + Base64Compat.encode(raw)
    }

    override suspend fun propfind(url: String, depth: Int, timeoutMs: Long): WebDavStreamResponse =
        withContext(io) {
            val conn = open(url, timeoutMs, "PROPFIND")
            try {
                conn.setRequestProperty("Depth", depth.toString())
                conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                conn.doOutput = true
                conn.outputStream.use { it.write(WebDavApi.PROPFIND_BODY.toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode
                val stream = if (WebDavApi.isSuccess(status)) conn.inputStream else conn.errorStream
                val body = stream ?: ByteArrayInputStream(ByteArray(0))
                val ownedBody = object : FilterInputStream(body) {
                    override fun close() {
                        try { super.close() } finally { conn.disconnect() }
                    }
                }
                WebDavStreamResponse(status, DeadlineInputStream(ownedBody, timeoutMs))
            } catch (t: Throwable) {
                conn.disconnect()
                throw t
            }
        }

    /**
     * Caller-owned stream that disconnects its connection on close, so a frame running
     * for weeks does not accumulate sockets — the same leak that was fixed for Synology.
     */
    override suspend fun openStream(url: String, timeoutMs: Long): InputStream = withContext(io) {
        val conn = open(url, timeoutMs, "GET")
        val status = try {
            conn.responseCode
        } catch (t: Throwable) {
            conn.disconnect(); throw t
        }
        if (!WebDavApi.isSuccess(status)) {
            conn.disconnect()
            throw IOException("HTTP $status")
        }
        val body = try {
            conn.inputStream
        } catch (t: Throwable) {
            conn.disconnect(); throw t
        }
        val ownedBody = object : FilterInputStream(body) {
            override fun close() {
                try { super.close() } finally { conn.disconnect() }
            }
        }
        DeadlineInputStream(ownedBody, timeoutMs)
    }

    private fun open(url: String, timeoutMs: Long, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs.toSocketTimeoutMillis()
            readTimeout = timeoutMs.toSocketTimeoutMillis()
            instanceFollowRedirects = true
            setRequestProperty("Authorization", authHeader)
            val sf = socketFactory
            if (sf != null && this is javax.net.ssl.HttpsURLConnection) sslSocketFactory = sf
        }

    /** `java.util.Base64` is API 26+ and this app supports API 21 (see BUILD_NOTES.md). */
    private object Base64Compat {
        private const val A = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        fun encode(bytes: ByteArray): String {
            val out = StringBuilder((bytes.size + 2) / 3 * 4)
            var i = 0
            while (i + 2 < bytes.size) {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8) or (bytes[i + 2].toInt() and 0xFF)
                out.append(A[(n ushr 18) and 63]).append(A[(n ushr 12) and 63])
                    .append(A[(n ushr 6) and 63]).append(A[n and 63])
                i += 3
            }
            when (bytes.size - i) {
                1 -> {
                    val n = (bytes[i].toInt() and 0xFF) shl 16
                    out.append(A[(n ushr 18) and 63]).append(A[(n ushr 12) and 63]).append("==")
                }
                2 -> {
                    val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                    out.append(A[(n ushr 18) and 63]).append(A[(n ushr 12) and 63])
                        .append(A[(n ushr 6) and 63]).append('=')
                }
            }
            return out.toString()
        }
    }
}

/**
 * WebDAV / Nextcloud photo source (ROADMAP.md → "other candidate sources").
 *
 * Chosen as the second network source because WebDAV is a *standard*: unlike
 * `SYNO.Foto`, it is specified, stable, and implemented by Nextcloud, ownCloud, Apache,
 * Synology's own WebDAV Server package, and most NAS firmware. One implementation
 * therefore reaches many devices without taking on per-vendor version drift.
 *
 * What it does not give, honestly: no albums (WebDAV exposes folders, not collections
 * with metadata) and **no server-side thumbnails**, so unlike Synology File Station the
 * frame downloads full-resolution originals. `MediaCache` absorbs the repeat cost, but
 * first display of a large photo is slower and HEIC/RAW files the device cannot decode
 * stay undecodable. That trade is why this is an alternative to File Station, not a
 * replacement for it.
 *
 * Posture matches the other remote sources: all I/O off-main, cancellable and
 * time-bounded, the scan streams [ScanEvent]s and never builds a List (Contract Rule 3),
 * and traversal uses an explicit work-stack so deep trees cannot exhaust the JVM stack.
 */
class WebDavPhotoSource(
    override val id: SourceId,
    private val conn: WebDavConnection,
    credentials: WebDavCredentials,
    private val io: CoroutineDispatcher,
    private val http: WebDavHttpClient =
        UrlConnectionWebDavClient(io, credentials, conn.pinnedCertSha256),
) : PhotoSource {

    override val type: SourceType = SourceType.WEBDAV

    /** Server-absolute path of the configured scan root. */
    private val scanRoot: String =
        WebDavApi.normalizePath(conn.rootPath) + WebDavApi.normalizePath(conn.folderPath)

    // ---- health ----------------------------------------------------------------

    override suspend fun healthCheck(timeoutMs: Long): SourceHealth = withContext(io) {
        withTimeoutOrNull(timeoutMs) {
            val url = WebDavApi.buildCollectionUrl(conn.baseUrl, conn.rootPath, conn.folderPath)
            try {
                val res = http.propfind(url, depth = 0, timeoutMs = timeoutMs)
                res.body.use { body ->
                    if (!WebDavApi.isSuccess(res.status)) {
                        return@withTimeoutOrNull when (WebDavApi.mapStatus(res.status)) {
                            SourceError.AuthFailed -> SourceHealth.ProviderError("auth_failed")
                            SourceError.PermissionDenied -> SourceHealth.NeedsPermission
                            SourceError.FileGone -> SourceHealth.Missing
                            SourceError.ProtocolError -> SourceHealth.ProviderError("not_webdav")
                            else -> SourceHealth.ProviderError("server_error")
                        }
                    }
                    val listing = WebDavApi.parsePropfind(body, scanRoot)
                    listing.entries.forEach { /* Depth:0 normally yields only the root. */ }
                    if (listing.isValid) SourceHealth.Ok else SourceHealth.ProviderError("not_webdav")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Exception) {
                if (t is WebDavLimitException) {
                    return@withTimeoutOrNull SourceHealth.ProviderError("not_webdav")
                }
                return@withTimeoutOrNull when (transportError(t)) {
                    SourceError.CertUntrusted -> SourceHealth.ProviderError("CertUntrusted")
                    SourceError.HostUnreachable -> SourceHealth.ProviderError("HostUnreachable")
                    SourceError.Timeout -> SourceHealth.Unavailable
                    else -> SourceHealth.ProviderError("transport")
                }
            }
        } ?: SourceHealth.Unavailable
    }

    // ---- scan ------------------------------------------------------------------

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val pending = ArrayDeque<String>()
        pending.addLast(scanRoot)
        var scanned = 0L

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val folder = pending.removeLast()
            val url = WebDavApi.normalizeBaseUrl(conn.baseUrl) + WebDavApi.encodePath(folder)

            val res = try {
                http.propfind(url, depth = 1, timeoutMs = conn.readTimeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Exception) {
                emit(ScanEvent.Error(folder, transportError(t)))
                continue
            }
            if (!WebDavApi.isSuccess(res.status)) {
                try { res.body.close() } catch (_: Exception) { /* status is authoritative */ }
                emit(ScanEvent.Error(folder, WebDavApi.mapStatus(res.status)))
                continue
            }

            var folderEntries = 0L
            var validListing = false
            try {
                res.body.use { body ->
                    val listing = WebDavApi.parsePropfind(body, folder)
                    for (entry in listing.entries) {
                        coroutineContext.ensureActive()
                        folderEntries++
                        if (entry.isDir) {
                            if (options.allowsFolder(entry.name)) {
                                pending.addLast(entry.path)
                            }
                            continue
                        }
                        if (!options.allowsFile(entry.name)) continue
                        if (!SupportedFormats.isSupported(entry.name, entry.contentType)) continue

                        val normalized = entry.path.removePrefix(scanRoot).trimStart('/')
                        emit(
                            ScanEvent.FileFound(
                                PhotoItem(
                                    stableId = StableId.of(
                                        id.value, normalized, entry.sizeBytes, entry.modifiedEpochMs,
                                    ),
                                    sourceId = id,
                                    normalizedPath = normalized,
                                    folderName = entry.path.substringBeforeLast('/').substringAfterLast('/'),
                                    fileName = entry.name,
                                    mimeType = entry.contentType,
                                    sizeBytes = entry.sizeBytes,
                                    fileModifiedEpochMs = entry.modifiedEpochMs,
                                    openToken = entry.path,
                                ),
                            ),
                        )
                    }
                    validListing = listing.isValid
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(
                    ScanEvent.Error(
                        folder,
                        if (e is WebDavLimitException) SourceError.ProtocolError else transportError(e),
                    )
                )
                continue
            }
            if (!validListing) {
                emit(ScanEvent.Error(folder, SourceError.ProtocolError))
                continue
            }

            scanned += folderEntries
            emit(ScanEvent.DirectoryProgress(folder, scanned))
        }
        emit(ScanEvent.Finished(ScanCursor("")))
    }.flowOn(io)

    // ---- bytes -----------------------------------------------------------------

    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream {
        val url = WebDavApi.normalizeBaseUrl(conn.baseUrl) + WebDavApi.encodePath(item.openToken)
        return http.openStream(url, options.timeoutMs)
    }

    /** Maps transport-level exceptions onto the typed taxonomy (spec §8.2). */
    private fun transportError(t: Throwable): SourceError = when (t) {
        is SSLHandshakeException -> SourceError.CertUntrusted
        is UnknownHostException -> SourceError.HostUnreachable
        is InterruptedIOException -> SourceError.Timeout
        else -> SourceError.ProtocolError
    }
}
