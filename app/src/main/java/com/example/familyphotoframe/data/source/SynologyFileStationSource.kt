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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.coroutineContext

/** Like [runCatching], but never converts structured-concurrency cancellation into a failure value. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(t)
}

/** Non-secret connection settings for a Synology NAS (mirrors the SMB equivalent). */
data class SynologyConnection(
    val baseUrl: String,
    /** Share-relative root to scan, e.g. `/photo` or `/photo/Family`. */
    val folderPath: String = "/photo",
    /** Request server-generated thumbnails instead of full-res originals. */
    val useThumbnails: Boolean = true,
    val thumbnailSize: String = "large",
    val connectionTimeoutMs: Long = 8_000,
    val readTimeoutMs: Long = 20_000,
    /**
     * SHA-256 fingerprint of a self-signed certificate the user explicitly approved
     * (ROADMAP.md "certificate-trust choice"). Null means ordinary platform validation
     * with no relaxation — see [CertPinning] for why this is a pin and not a
     * trust-all switch.
     */
    val pinnedCertSha256: String? = null,
)

/** Resolved just before use; never persisted here as plaintext (Contract Rule 5). */
data class SynologyCredentials(val user: String, val password: String, val otpCode: String? = null)

/** A text response plus its HTTP status, so transport and API errors stay distinguishable. */
data class HttpTextResponse(val status: Int, val body: String)

/**
 * The seam the roadmap asks for: "build a thin, mockable HTTP layer so the *mapping*
 * logic can be tested without a live NAS." Everything protocol-shaped lives in
 * [SynologyApi]; everything network-shaped lives behind this interface.
 */
interface SynologyHttpClient {
    suspend fun getText(url: String, timeoutMs: Long): HttpTextResponse
    suspend fun openStream(url: String, timeoutMs: Long): InputStream
}

/** Default transport on the existing stack — no new third-party dependency. */
class UrlConnectionHttpClient(
    private val io: CoroutineDispatcher,
    private val pinnedCertSha256: String? = null,
) : SynologyHttpClient {

    /** Built once: constructing an SSLContext per request is needless work on a frame. */
    private val socketFactory by lazy {
        if (pinnedCertSha256.isNullOrBlank()) null else CertPinning.socketFactory(pinnedCertSha256)
    }

    override suspend fun getText(url: String, timeoutMs: Long): HttpTextResponse = withContext(io) {
        val conn = open(url, timeoutMs)
        try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            HttpTextResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Caller-owned stream. Closing it also disconnects the underlying HTTP connection,
     * preventing a long-running frame from leaking sockets after repeated photo loads.
     */
    override suspend fun openStream(url: String, timeoutMs: Long): InputStream = withContext(io) {
        val conn = open(url, timeoutMs)
        val status = try {
            conn.responseCode
        } catch (t: Throwable) {
            conn.disconnect()
            throw t
        }
        if (status !in 200..299) {
            conn.disconnect()
            throw java.io.IOException("HTTP $status")
        }
        val body = try {
            conn.inputStream
        } catch (t: Throwable) {
            conn.disconnect()
            throw t
        }
        object : FilterInputStream(body) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun open(url: String, timeoutMs: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            instanceFollowRedirects = true
            // Only applied to HTTPS, and only when the user approved a specific cert.
            // Hostname verification is left at the platform default deliberately.
            val sf = socketFactory
            if (sf != null && this is javax.net.ssl.HttpsURLConnection) sslSocketFactory = sf
        }
}

/**
 * Synology **File Station** source (ROADMAP.md → "Network photo-app sources", step 1).
 *
 * Chosen ahead of the `SYNO.Foto.*` album API because File Station is official and
 * documented, while `SYNO.Foto` is reverse-engineered and DSM-version-sensitive. This
 * delivers the two wins that matter most — server-side thumbnails (the NAS returns a
 * right-sized JPEG rather than a full-res original or an undecodable HEIC/RAW) and
 * HTTP/S transport — without the album UX, which is the follow-on step.
 *
 * Follows the same posture as [SmbPhotoSource]: all I/O off-main, cancellable and
 * time-bounded; the scan streams [ScanEvent]s and never builds a List (Contract Rule 3);
 * traversal uses an explicit work-stack rather than recursion so deep trees are safe.
 *
 * Secrets: the password lives only in [credentials]; the session id is held in memory,
 * never persisted, and never placed in an [PhotoItem.openToken] or a diagnostics string
 * — [PhotoItem.openToken] is the plain NAS-relative file path, and any URL that reaches
 * an error message is passed through [SynologyApi.redactSid] first.
 */
class SynologyFileStationSource(
    override val id: SourceId,
    private val conn: SynologyConnection,
    private val credentials: SynologyCredentials,
    private val io: CoroutineDispatcher,
    private val http: SynologyHttpClient = UrlConnectionHttpClient(io, conn.pinnedCertSha256),
) : PhotoSource {

    override val type: SourceType = SourceType.SYNOLOGY_FILE_STATION

    private val sidLock = Mutex()
    @Volatile private var sid: String? = null

    // ---- session ---------------------------------------------------------------

    /** Logs in if there is no live session. Returns null when authentication fails. */
    private suspend fun ensureSession(): String? = sidLock.withLock {
        sid ?: run {
            val res = runCatchingCancellable {
                http.getText(
                    SynologyApi.buildAuthUrl(conn.baseUrl, credentials.user, credentials.password, credentials.otpCode),
                    conn.connectionTimeoutMs,
                )
            }.getOrNull() ?: return null
            SynologyApi.parseSid(res.body)?.also { sid = it }
        }
    }

    /** Drops the cached session so the next call re-authenticates. */
    private suspend fun invalidateSession() = sidLock.withLock { sid = null }

    /**
     * Runs [block] with a live session, retrying once after a re-auth if the API reports
     * the session expired — the single most common long-running-frame failure, since a
     * NAS may be rebooted or time the session out while the slideshow is idle.
     */
    private suspend fun <T> withSession(block: suspend (String) -> Pair<T?, SourceError?>): Pair<T?, SourceError?> {
        val first = ensureSession() ?: return null to SourceError.AuthFailed
        val (value, error) = block(first)
        if (error != SourceError.SessionExpired) return value to error
        invalidateSession()
        val second = ensureSession() ?: return null to SourceError.AuthFailed
        return block(second)
    }

    // ---- health ----------------------------------------------------------------

    /**
     * Verifies the NAS is reachable, authenticated, and the configured folder is
     * listable.
     *
     * Reuses an existing session where possible and only logs in when there isn't one.
     * That matters because the recovery loop polls this every 60 seconds while healthy:
     * re-authenticating each time would pile up sessions, risk tripping DSM's auto-block
     * (which surfaces as error 407), and fail outright on a 2FA account, since a fresh
     * login needs a one-time code that is no longer available.
     */
    override suspend fun healthCheck(timeoutMs: Long): SourceHealth = withContext(io) {
        withTimeoutOrNull(timeoutMs) {
            runCatchingCancellable {
                val (health, error) = withSession { liveSid ->
                    val list = runCatchingCancellable {
                        http.getText(
                            SynologyApi.buildListUrl(conn.baseUrl, liveSid, conn.folderPath, 0, 1),
                            timeoutMs,
                        )
                    }.getOrElse { return@withSession null to transportError(it) }

                    if (SynologyApi.isSuccess(list.body)) {
                        SourceHealth.Ok to null
                    } else {
                        val mapped = SynologyApi.mapError(SynologyApi.errorCode(list.body))
                        // Surfaced so withSession can re-auth and retry once.
                        if (mapped == SourceError.SessionExpired) null to mapped
                        else when (mapped) {
                            SourceError.FileGone -> SourceHealth.Missing to null
                            SourceError.PermissionDenied -> SourceHealth.NeedsPermission to null
                            else -> SourceHealth.ProviderError("folder_unreadable") to null
                        }
                    }
                }
                health ?: when (error) {
                    SourceError.AuthFailed -> authFailureHealth()
                    SourceError.CertUntrusted -> SourceHealth.ProviderError("CertUntrusted")
                    SourceError.HostUnreachable -> SourceHealth.ProviderError("HostUnreachable")
                    SourceError.Timeout -> SourceHealth.Unavailable
                    else -> SourceHealth.ProviderError("api_error")
                }
            }.getOrElse { SourceHealth.ProviderError(transportError(it).name) }
        } ?: SourceHealth.Unavailable
    }

    /**
     * Distinguishes "wrong password" from "this account needs a 2FA code" by looking at
     * the login response directly — [withSession] collapses both into a failed login,
     * but the two need very different instructions on screen.
     */
    private suspend fun authFailureHealth(): SourceHealth {
        val res = runCatchingCancellable {
            http.getText(
                SynologyApi.buildAuthUrl(conn.baseUrl, credentials.user, credentials.password, credentials.otpCode),
                conn.connectionTimeoutMs,
            )
        }.getOrNull() ?: return SourceHealth.ProviderError("auth_failed")
        return when (SynologyApi.mapError(SynologyApi.errorCode(res.body))) {
            SourceError.TwoFactorRequired -> SourceHealth.ProviderError("two_factor_required")
            SourceError.PermissionDenied -> SourceHealth.NeedsPermission
            else -> SourceHealth.ProviderError("auth_failed")
        }
    }

    // ---- scan ------------------------------------------------------------------

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val root = conn.folderPath
        // Explicit work-stack, not recursion: a deep NAS tree must not risk the JVM stack.
        val pending = ArrayDeque<String>()
        pending.addLast(root)
        var scanned = 0L

        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val folder = pending.removeLast()
            var offset = 0

            while (true) {
                coroutineContext.ensureActive()
                val (page, error) = withSession { liveSid ->
                    val res = runCatchingCancellable {
                        http.getText(
                            SynologyApi.buildListUrl(conn.baseUrl, liveSid, folder, offset),
                            conn.readTimeoutMs,
                        )
                    }.getOrElse { return@withSession null to transportError(it) }
                    val parsed = SynologyApi.parseListPage(res.body)
                    if (parsed == null) null to SynologyApi.mapError(SynologyApi.errorCode(res.body))
                    else parsed to null
                }

                if (error != null || page == null) {
                    emit(ScanEvent.Error(folder, error ?: SourceError.ProtocolError))
                    break
                }

                for (entry in page.entries) {
                    coroutineContext.ensureActive()
                    if (entry.isDir) {
                        if (options.allowsFolder(entry.name)) {
                            pending.addLast(entry.path)
                        }
                        continue
                    }
                    if (!options.allowsFile(entry.name)) continue
                    if (!SupportedFormats.isSupported(entry.name, null)) continue

                    val normalized = entry.path.removePrefix(root).trimStart('/')
                    emit(
                        ScanEvent.FileFound(
                            PhotoItem(
                                stableId = StableId.of(id.value, normalized, entry.sizeBytes, entry.modifiedEpochMs),
                                sourceId = id,
                                normalizedPath = normalized,
                                folderName = entry.path.substringBeforeLast('/').substringAfterLast('/'),
                                fileName = entry.name,
                                mimeType = null,
                                sizeBytes = entry.sizeBytes,
                                fileModifiedEpochMs = entry.modifiedEpochMs,
                                // No sid here: the token must stay secret-free and must
                                // survive a session change (Contract Rule 5).
                                openToken = entry.path,
                            ),
                        ),
                    )
                }

                scanned += page.entries.size
                emit(ScanEvent.DirectoryProgress(folder, scanned))

                offset += page.entries.size
                if (page.entries.isEmpty() || offset >= page.total) break
            }
        }
        emit(ScanEvent.Finished(ScanCursor("")))
    }.flowOn(io)

    // ---- bytes -----------------------------------------------------------------

    /**
     * Opens the displayable bytes for an item. Prefers the server-side thumbnail — the
     * central reason to use File Station over SMB — and falls back to the original if
     * the NAS has not generated one (common for a freshly copied folder).
     */
    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream {
        val (stream, error) = withSession { liveSid ->
            val urls = buildList {
                if (conn.useThumbnails && !options.preferOriginal) {
                    add(SynologyApi.buildThumbnailUrl(conn.baseUrl, liveSid, item.openToken, conn.thumbnailSize))
                }
                add(SynologyApi.buildDownloadUrl(conn.baseUrl, liveSid, item.openToken))
            }
            var lastError: SourceError? = null
            for (url in urls) {
                val attempt = runCatchingCancellable { http.openStream(url, options.timeoutMs) }
                attempt.getOrNull()?.let { return@withSession it to null }
                lastError = transportError(attempt.exceptionOrNull() ?: Exception())
            }
            null to (lastError ?: SourceError.ProtocolError)
        }
        return stream ?: throw java.io.IOException("synology_open_failed:${error?.name}")
    }

    /** Maps transport-level exceptions onto the typed taxonomy (spec §8.2). */
    private fun transportError(t: Throwable): SourceError = when (t) {
        is SSLHandshakeException -> SourceError.CertUntrusted
        is UnknownHostException -> SourceError.HostUnreachable
        is InterruptedIOException -> SourceError.Timeout
        else -> SourceError.ProtocolError
    }

    /**
     * Retrieves the SHA-256 fingerprint of the certificate this host presents, **without
     * trusting it**, so the setup UI can show the user a value to compare against their
     * DSM admin page before approving it (ROADMAP.md "certificate-trust choice").
     *
     * Returns null for plain HTTP, an unreachable host, or a host whose certificate the
     * platform already trusts — in the last case there is nothing for the user to
     * approve, so offering them a pin would be misleading.
     */
    suspend fun probeCertificateFingerprint(timeoutMs: Long = conn.connectionTimeoutMs): String? =
        withContext(io) {
            withTimeoutOrNull(timeoutMs) {
                runCatchingCancellable {
                    val base = SynologyApi.normalizeBaseUrl(conn.baseUrl)
                    if (!base.startsWith("https://")) return@runCatchingCancellable null
                    val capturer = CertPinning.CapturingTrustManager()
                    val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                        .apply { init(null, arrayOf<javax.net.ssl.TrustManager>(capturer), null) }
                    val c = (URL(base).openConnection() as HttpURLConnection).apply {
                        connectTimeout = timeoutMs.toInt()
                        readTimeout = timeoutMs.toInt()
                        if (this is javax.net.ssl.HttpsURLConnection) sslSocketFactory = ctx.socketFactory
                    }
                    try {
                        // Expected to throw: CapturingTrustManager rejects every chain.
                        c.connect()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // ignored — the certificate is captured on the way past
                    } finally {
                        c.disconnect()
                    }
                    capturer.captured?.let { CertPinning.fingerprintOf(it) }
                }.getOrNull()
            }
        }
}
