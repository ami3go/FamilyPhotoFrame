package com.example.familyphotoframe.data.source

import com.example.familyphotoframe.util.Glob
import com.example.familyphotoframe.util.StableId
import com.example.familyphotoframe.util.SupportedFormats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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

/** Streaming response metadata must remain available so API JSON is never decoded as a photo. */
data class HttpStreamResponse(
    val status: Int,
    val contentType: String?,
    val contentLength: Long,
    val body: InputStream,
)

private const val MAX_HTTP_TEXT_CHARS = 4 * 1024 * 1024

/** A NAS response is untrusted input; never let an error page or API bug grow the heap. */
private fun InputStream.readUtf8TextBounded(maxChars: Int = MAX_HTTP_TEXT_CHARS): String =
    reader(Charsets.UTF_8).use { reader ->
        val result = StringBuilder(minOf(maxChars, 64 * 1024))
        val buffer = CharArray(8 * 1024)
        while (true) {
            val remaining = maxChars - result.length
            if (remaining == 0) {
                if (reader.read() != -1) throw IOException("synology_response_too_large")
                break
            }
            val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            result.append(buffer, 0, count)
        }
        result.toString()
    }

/**
 * The seam the roadmap asks for: "build a thin, mockable HTTP layer so the *mapping*
 * logic can be tested without a live NAS." Everything protocol-shaped lives in
 * [SynologyApi]; everything network-shaped lives behind this interface.
 */
interface SynologyHttpClient {
    suspend fun getText(url: String, timeoutMs: Long): HttpTextResponse
    suspend fun openStream(url: String, timeoutMs: Long): HttpStreamResponse
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
            HttpTextResponse(
                status,
                stream?.let { DeadlineInputStream(it, timeoutMs).readUtf8TextBounded() }.orEmpty(),
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Caller-owned stream. Closing it also disconnects the underlying HTTP connection,
     * preventing a long-running frame from leaking sockets after repeated photo loads.
     */
    override suspend fun openStream(url: String, timeoutMs: Long): HttpStreamResponse = withContext(io) {
        val conn = open(url, timeoutMs)
        val status = try {
            conn.responseCode
        } catch (t: Throwable) {
            conn.disconnect()
            throw t
        }
        val rawBody = try {
            if (status in 200..299) conn.inputStream else conn.errorStream
        } catch (t: Throwable) {
            conn.disconnect()
            throw t
        }
        if (rawBody == null) {
            val contentType = conn.contentType
            val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            conn.disconnect()
            return@withContext HttpStreamResponse(
                status = status,
                contentType = contentType,
                contentLength = contentLength,
                body = ByteArrayInputStream(ByteArray(0)),
            )
        }
        val body = object : FilterInputStream(rawBody) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    conn.disconnect()
                }
            }
        }
        HttpStreamResponse(
            status = status,
            contentType = conn.contentType,
            // getContentLengthLong() is API 24; parsing the header keeps API 21–23 safe.
            contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L,
            body = DeadlineInputStream(body, timeoutMs),
        )
    }

    private fun open(url: String, timeoutMs: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs.toSocketTimeoutMillis()
            readTimeout = timeoutMs.toSocketTimeoutMillis()
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
 * Secrets: the password and one-use OTP live only in this source; the session id is held in memory,
 * never persisted, and never placed in an [PhotoItem.openToken] or a diagnostics string
 * — [PhotoItem.openToken] is the plain NAS-relative file path, and any URL that reaches
 * an error message is passed through [SynologyApi.redactSid] first.
 */
class SynologyFileStationSource(
    override val id: SourceId,
    private val conn: SynologyConnection,
    credentials: SynologyCredentials,
    private val io: CoroutineDispatcher,
    private val http: SynologyHttpClient = UrlConnectionHttpClient(io, conn.pinnedCertSha256),
) : PhotoSource {

    override val type: SourceType = SourceType.SYNOLOGY_FILE_STATION

    private val user = credentials.user
    private val password = credentials.password
    private val pendingOtp = AtomicReference(credentials.otpCode)
    private val sidLock = Mutex()
    private val sid = AtomicReference<String?>(null)
    private val closed = AtomicBoolean(false)
    private val cleanupScope = CoroutineScope(SupervisorJob() + io)
    private val logoutJobs = ConcurrentLinkedQueue<Job>()
    @Volatile private var lastAuthError: SourceError = SourceError.AuthFailed
    private val operationOwner = DeferredCloseResource(
        factory = { Unit },
        closer = { sid.getAndSet(null)?.let(::scheduleLogout) },
    )

    // ---- session ---------------------------------------------------------------

    /** Logs in if there is no live session. Returns null when authentication fails. */
    private suspend fun ensureSession(): String? = sidLock.withLock {
        if (closed.get()) return null
        sid.get() ?: run {
            // OTP values are one-use secrets. Clear the retained copy before network I/O
            // so neither a successful session nor a later re-auth reuses the old code.
            val otp = pendingOtp.getAndSet(null)
            val res = try {
                // DSM creates the session before returning its SID. Complete this one
                // bounded request across caller cancellation so its SID is retained and
                // can be logged out instead of becoming an untracked server session.
                withContext(NonCancellable) {
                    http.getText(
                        SynologyApi.buildAuthUrl(conn.baseUrl, user, password, otp),
                        conn.connectionTimeoutMs,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                lastAuthError = transportError(t)
                return null
            }
            if (res.status !in 200..299) {
                lastAuthError = if (res.status == 401) SourceError.AuthFailed else mapHttpStatus(res.status)
                return null
            }
            val parsed = SynologyApi.parseSid(res.body)
            if (parsed == null) {
                lastAuthError = SynologyApi.mapError(SynologyApi.errorCode(res.body))
                return null
            }
            if (closed.get()) {
                // The operation lease defers logout until this request returns/stream
                // closes, avoiding source replacement invalidating in-flight bytes.
                sid.set(parsed)
                return null
            }
            lastAuthError = SourceError.Unknown
            sid.set(parsed)
            parsed
        }
    }

    /** Drops the cached session so the next call re-authenticates. */
    private suspend fun invalidateSession(expiredSid: String) = sidLock.withLock {
        // Another request may already have replaced the expired session. Never erase
        // that newer SID: doing so would create an untracked DSM session on every pair
        // of concurrent expiry responses.
        sid.compareAndSet(expiredSid, null)
    }

    /**
     * Reject new work immediately, then perform the DSM logout on the source's I/O
     * dispatcher. Registry retirement is synchronous, so [close] must not block its
     * caller; one-shot tests and orderly teardown use [shutdown] to await completion.
     */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        operationOwner.close()
    }

    override suspend fun shutdown() {
        close()
        val fullyClosed = operationOwner.awaitClosed(SHUTDOWN_WAIT_MS)
        if (!fullyClosed) return
        while (true) {
            val pending = logoutJobs.filterNot { it.isCompleted }
            if (pending.isEmpty()) break
            pending.forEach { it.join() }
        }
        cleanupScope.cancel()
    }

    private fun scheduleLogout(sessionId: String) {
        if (sessionId.isBlank()) return
        val job = cleanupScope.launchCatchingLogout(sessionId)
        logoutJobs += job
    }

    private fun CoroutineScope.launchCatchingLogout(sessionId: String): Job =
        launch {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) {
                runCatchingCancellable {
                    http.getText(
                        SynologyApi.buildLogoutUrl(conn.baseUrl, sessionId),
                        LOGOUT_TIMEOUT_MS,
                    )
                }
            }
        }

    /**
     * Runs [block] with a live session, retrying once after a re-auth if the API reports
     * the session expired — the single most common long-running-frame failure, since a
     * NAS may be rebooted or time the session out while the slideshow is idle.
     */
    private suspend fun <T> withSession(block: suspend (String) -> Pair<T?, SourceError?>): Pair<T?, SourceError?> {
        val first = ensureSession() ?: return null to if (closed.get()) SourceError.Cancelled else lastAuthError
        val (value, error) = block(first)
        if (error != SourceError.SessionExpired) return value to error
        invalidateSession(first)
        val second = ensureSession()
            ?: return null to if (closed.get()) SourceError.Cancelled else lastAuthError
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
        val lease = operationOwner.acquire()
        try {
            withTimeoutOrNull(timeoutMs) {
                runCatchingCancellable {
                    val (health, error) = withSession { liveSid ->
                        val list = runCatchingCancellable {
                            http.getText(
                                SynologyApi.buildListUrl(conn.baseUrl, liveSid, conn.folderPath, 0, 1),
                                timeoutMs,
                            )
                        }.getOrElse { return@withSession null to transportError(it) }

                        if (list.status !in 200..299) {
                            null to mapHttpStatus(list.status)
                        } else if (SynologyApi.isSuccess(list.body)) {
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
        } finally {
            lease.close()
        }
    }

    /** Maps the already-observed login failure without issuing a duplicate DSM login. */
    private fun authFailureHealth(): SourceHealth {
        return when (lastAuthError) {
            SourceError.TwoFactorRequired -> SourceHealth.ProviderError("two_factor_required")
            SourceError.PermissionDenied -> SourceHealth.NeedsPermission
            else -> SourceHealth.ProviderError("auth_failed")
        }
    }

    // ---- scan ------------------------------------------------------------------

    override fun scan(previousCursor: ScanCursor?, options: ScanOptions): Flow<ScanEvent> = flow {
        val lease = operationOwner.acquire()
        try {
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
                        if (res.status !in 200..299) {
                            null to mapHttpStatus(res.status)
                        } else {
                            val parsed = SynologyApi.parseListPage(res.body)
                            if (parsed == null) {
                                null to SynologyApi.mapError(SynologyApi.errorCode(res.body))
                            } else {
                                parsed to null
                            }
                        }
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
                                    stableId = StableId.of(
                                        id.value,
                                        normalized,
                                        entry.sizeBytes,
                                        entry.modifiedEpochMs,
                                    ),
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

                    if (offset > Int.MAX_VALUE - page.entries.size) {
                        emit(ScanEvent.Error(folder, SourceError.ProtocolError))
                        break
                    }
                    offset += page.entries.size
                    if (page.entries.isEmpty() || offset >= page.total) break
                }
            }
            emit(ScanEvent.Finished(ScanCursor("")))
        } finally {
            lease.close()
        }
    }.flowOn(io)

    // ---- bytes -----------------------------------------------------------------

    /**
     * Opens the displayable bytes for an item. Prefers the server-side thumbnail — the
     * central reason to use File Station over SMB — and falls back to the original if
     * the NAS has not generated one (common for a freshly copied folder).
     */
    override suspend fun openStream(item: PhotoItem, options: OpenOptions): InputStream {
        val lease = operationOwner.acquire()
        try {
            val (stream, error) = withSession { liveSid ->
                val urls = buildList<Pair<String, Boolean>> {
                    if (conn.useThumbnails && !options.preferOriginal) {
                        add(
                            SynologyApi.buildThumbnailUrl(
                                conn.baseUrl,
                                liveSid,
                                item.openToken,
                                conn.thumbnailSize,
                            ) to true,
                        )
                    }
                    add(SynologyApi.buildDownloadUrl(conn.baseUrl, liveSid, item.openToken) to false)
                }
                var lastError: SourceError? = null
                for ((url, isThumbnail) in urls) {
                    val (candidate, candidateError) = checkedStream(url, options.timeoutMs)
                    candidate?.let { return@withSession it to null }
                    lastError = candidateError ?: SourceError.ProtocolError
                    // Let withSession re-authenticate before trying any URL with an expired
                    // SID. Other thumbnail failures deliberately fall through to original.
                    if (lastError == SourceError.SessionExpired) {
                        return@withSession null to lastError
                    }
                    if (!isThumbnail) break
                }
                null to (lastError ?: SourceError.ProtocolError)
            }
            return stream?.let {
                // The transport deadline closes the socket even while probing the response.
                // This outer deadline additionally releases the operation lease if a caller
                // abandons the returned stream without closing it.
                DeadlineInputStream(LeaseReleasingInputStream(it, lease), options.timeoutMs)
            } ?: throw IOException("synology_open_failed:${error?.name}")
        } catch (error: Throwable) {
            lease.close()
            throw error
        }
    }

    private class LeaseReleasingInputStream(
        input: InputStream,
        private val lease: DeferredCloseResource.Lease<Unit>,
    ) : FilterInputStream(input) {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                super.close()
            } finally {
                lease.close()
            }
        }
    }

    /**
     * Validates both the HTTP envelope and File Station's JSON envelope. DSM reports
     * many API failures with HTTP 200; those bytes must never enter the image cache.
     */
    private suspend fun checkedStream(url: String, timeoutMs: Long): Pair<InputStream?, SourceError?> {
        val response = runCatchingCancellable { http.openStream(url, timeoutMs) }
            .getOrElse { return null to transportError(it) }
        val buffered = if (response.body is BufferedInputStream) {
            response.body
        } else {
            BufferedInputStream(response.body, API_RESPONSE_PROBE_BYTES)
        }
        val apiError = runCatchingCancellable {
            probeApiError(buffered, response.contentType)
        }.getOrElse {
            runCatching { buffered.close() }
            return null to transportError(it)
        }
        if (response.status !in 200..299 || apiError != null) {
            runCatching { buffered.close() }
            return null to (apiError ?: mapHttpStatus(response.status))
        }
        return buffered to null
    }

    /** Reads and rewinds only a small prefix; valid image bytes remain untouched. */
    private fun probeApiError(stream: BufferedInputStream, contentType: String?): SourceError? {
        stream.mark(API_RESPONSE_PROBE_BYTES)
        val prefix = ByteArray(API_RESPONSE_PROBE_BYTES)
        var count = 0
        while (count < prefix.size) {
            val read = stream.read(prefix, count, prefix.size - count)
            if (read <= 0) break
            count += read
        }
        stream.reset()
        if (count <= 0) return SourceError.ProtocolError

        val explicitlyJson = contentType.orEmpty().lowercase().let { type ->
            type.contains("json") || type.startsWith("text/")
        }
        val text = prefix.decodeToString(endIndex = count).trimStart()
        val looksJson = text.firstOrNull()?.code == 123 // opening JSON object delimiter
        if (!explicitlyJson && !looksJson) return null
        if (!looksJson) return SourceError.ProtocolError
        return if (SynologyApi.isSuccess(text)) {
            // A successful JSON envelope is still metadata, never image bytes.
            SourceError.ProtocolError
        } else {
            SynologyApi.mapError(SynologyApi.errorCode(text))
        }
    }

    private fun mapHttpStatus(status: Int): SourceError = when (status) {
        401 -> SourceError.SessionExpired
        403 -> SourceError.PermissionDenied
        404, 410 -> SourceError.FileGone
        408, 504 -> SourceError.Timeout
        else -> SourceError.ProtocolError
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
            val lease = operationOwner.acquire()
            try {
                withTimeoutOrNull(timeoutMs) {
                    runCatchingCancellable {
                        val base = SynologyApi.normalizeBaseUrl(conn.baseUrl)
                        if (!base.startsWith("https://")) return@runCatchingCancellable null
                        val capturer = CertPinning.CapturingTrustManager()
                        val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
                            .apply { init(null, arrayOf<javax.net.ssl.TrustManager>(capturer), null) }
                        val c = (URL(base).openConnection() as HttpURLConnection).apply {
                            connectTimeout = timeoutMs.toSocketTimeoutMillis()
                            readTimeout = timeoutMs.toSocketTimeoutMillis()
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
                    }
                        .getOrNull()
                }
            } finally {
                lease.close()
            }
        }

    private companion object {
        private const val API_RESPONSE_PROBE_BYTES = 8 * 1024
        private const val LOGOUT_TIMEOUT_MS = 3_000L
        private const val SHUTDOWN_WAIT_MS = 5_000L
    }
}
