package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.settings.RememberedBrowserPolicy
import com.example.familyphotoframe.data.settings.RememberExpiryMode
import com.example.familyphotoframe.data.settings.CustomExpiryUnit
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/** Everything the embedded HTTP layer may ask from the application. */
interface WebBackend {
    suspend fun statusJson(): JsonObject
    suspend fun redactedConfigJson(): JsonObject
    suspend fun applyConfig(patch: JsonObject): String?
    suspend fun control(action: String): String?
    suspend fun testSavedSource(): String
    suspend fun diagnosticsJson(): JsonObject
    suspend fun diagnosticsBundle(): InputStream
    suspend fun clearDiagnostics()
    suspend fun settingsRevision(): Long
    suspend fun foldersJson(): JsonArray
    suspend fun folderAction(body: JsonObject): JsonObject
    suspend fun presentationJson(): JsonObject
    fun previewSnapshot(): WebPreviewFrame?
    suspend fun diagnosticsEvents(query: DiagnosticsQuery): JsonObject
    suspend fun diagnosticsSummary(): JsonObject
    suspend fun backupExport(): String
    suspend fun backupValidate(text: String): JsonObject
    suspend fun backupImport(text: String): String?
    suspend fun maintenance(action: String): String?
    suspend fun playlistAction(body: JsonObject): JsonObject
    suspend fun playlistScheduleAction(body: JsonObject): JsonObject
    suspend fun uploadCreate(ownerToken: String, body: JsonObject): JsonObject
    suspend fun uploadStatus(ownerToken: String, sessionId: String): JsonObject
    suspend fun uploadFile(
        ownerToken: String,
        sessionId: String,
        clientId: String,
        encodedName: String,
        declaredSize: Long,
        input: InputStream,
    ): JsonObject
    suspend fun uploadComplete(ownerToken: String, sessionId: String): JsonObject
    suspend fun uploadCancel(ownerToken: String, sessionId: String): JsonObject
}

/**
 * Convenience adapter for deliberately partial test doubles.
 *
 * Production implementations should implement [WebBackend] directly so newly added
 * routes become compile-time work rather than silently returning placeholder data.
 */
abstract class WebBackendAdapter : WebBackend {
    override suspend fun diagnosticsBundle(): InputStream = ByteArrayInputStream(ByteArray(0))
    override suspend fun clearDiagnostics() = Unit
    override suspend fun settingsRevision(): Long = 0L
    override suspend fun foldersJson(): JsonArray = buildJsonArray { }
    override suspend fun folderAction(body: JsonObject): JsonObject = buildJsonObject { put("ok", false) }
    override suspend fun presentationJson(): JsonObject = buildJsonObject { }
    override fun previewSnapshot(): WebPreviewFrame? = null
    override suspend fun diagnosticsEvents(query: DiagnosticsQuery): JsonObject = diagnosticsJson()
    override suspend fun diagnosticsSummary(): JsonObject = buildJsonObject { }
    override suspend fun backupExport(): String = ""
    override suspend fun backupValidate(text: String): JsonObject = buildJsonObject { put("valid", false) }
    override suspend fun backupImport(text: String): String? = "Backup import is unavailable"
    override suspend fun maintenance(action: String): String? = "Maintenance action is unavailable"
    override suspend fun playlistAction(body: JsonObject): JsonObject = buildJsonObject { put("ok", false) }
    override suspend fun playlistScheduleAction(body: JsonObject): JsonObject = buildJsonObject { put("ok", false) }
    override suspend fun uploadCreate(ownerToken: String, body: JsonObject): JsonObject =
        throw IllegalArgumentException("Web upload is unavailable")
    override suspend fun uploadStatus(ownerToken: String, sessionId: String): JsonObject =
        throw IllegalArgumentException("Web upload is unavailable")
    override suspend fun uploadFile(
        ownerToken: String,
        sessionId: String,
        clientId: String,
        encodedName: String,
        declaredSize: Long,
        input: InputStream,
    ): JsonObject = throw IllegalArgumentException("Web upload is unavailable")
    override suspend fun uploadComplete(ownerToken: String, sessionId: String): JsonObject =
        throw IllegalArgumentException("Web upload is unavailable")
    override suspend fun uploadCancel(ownerToken: String, sessionId: String): JsonObject =
        throw IllegalArgumentException("Web upload is unavailable")
}


/**
 * Embedded LAN-only setup/control server.
 *
 * Public routes contain only static UI assets and the pairing endpoint. Every data route
 * requires a session, and every state-changing route also requires the CSRF token.
 */
class WebConfigServer(
    private val boundHost: String,
    port: Int,
    private val security: WebSecurity,
    private val backend: WebBackend,
    private val rememberedBrowsers: RememberedBrowserManager? = null,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val onEvent: (code: String, fields: Map<String, String>) -> Unit,
) : NanoHTTPD(boundHost, port) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val rememberedExchangeAttempts = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val rememberedGlobalExchangeAttempts = ArrayDeque<Long>()

    init {
        setAsyncRunner(BoundedHttpAsyncRunner {
            onEvent(
                "WEB_CONNECTION_REJECTED",
                mapOf(
                    "reason" to "SERVER_BUSY",
                    "workerLimit" to BoundedHttpAsyncRunner.DEFAULT_WORKERS.toString(),
                    "queueLimit" to BoundedHttpAsyncRunner.DEFAULT_QUEUE_CAPACITY.toString(),
                ),
            )
        })
    }

    override fun createClientHandler(finalAccept: Socket, inputStream: InputStream): ClientHandler =
        RejectableClientHandler(inputStream, finalAccept)

    private inner class RejectableClientHandler(
        inputStream: InputStream,
        private val socket: Socket,
    ) : ClientHandler(inputStream, socket), ServiceUnavailableConnection {
        override fun rejectServiceUnavailable() {
            val body = "Server busy. Retry shortly.\n".toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 503 Service Unavailable\r\n")
                append("Content-Type: text/plain; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n")
                append("Retry-After: 1\r\n")
                append("Cache-Control: no-store\r\n\r\n")
            }.toByteArray(Charsets.US_ASCII)
            try {
                socket.getOutputStream().apply {
                    write(head)
                    write(body)
                    flush()
                }
            } catch (_: Exception) {
                // A slow or already-closed client needs no response; close() below is enough.
            } finally {
                close()
            }
        }
    }

    /**
     * Whether the current request's body was fully read by the route that handled it.
     *
     * NanoHTTPD serves a connection on a single thread for the whole request, so a
     * thread-local is the correct scope here.
     */
    private val requestBodyConsumed = ThreadLocal.withInitial { false }

    /**
     * [ThreadLocal.get] is a Java method, so Kotlin sees a platform type here. The
     * `withInitial` factory guarantees a value, so the fallback is unreachable.
     */
    private fun bodyWasConsumed(): Boolean = requestBodyConsumed.get() ?: false

    override fun serve(session: IHTTPSession): Response {
        requestBodyConsumed.set(false)
        val response = try {
            when {
                !hostAllowed(session) -> text(Response.Status.FORBIDDEN, "Bad host")
                !originAllowed(session) -> text(Response.Status.FORBIDDEN, "Bad origin")
                else -> route(session)
            }
        } catch (e: Exception) {
            onEvent(
                "WEB_ERROR",
                mapOf(
                    "errorClass" to e.javaClass.simpleName,
                    "action" to session.method.name,
                    "stage" to routeCategory(session.uri),
                ),
            )
            text(Response.Status.INTERNAL_ERROR, "Server error")
        }
        closeIfBodyUnread(session, response)

        /*
         * NanoHTTPD assigns one ClientHandler to an entire HTTP keep-alive
         * connection, not to one request. A fixed worker pool can therefore be
         * exhausted by idle browser sockets while CSS, JavaScript, or API requests
         * remain queued. End every response's connection so the bounded workers are
         * request-scoped and cannot starve the web UI.
         */
        response.closeConnection(true)

        if (session.uri == "/assets/app-${WebUiAssets.REVISION}.css" ||
            session.uri == "/assets/app-${WebUiAssets.REVISION}.js"
        ) {
            response.addHeader("Cache-Control", "public, max-age=31536000, immutable")
        } else if (session.uri.startsWith("/assets/")) {
            response.addHeader("Cache-Control", "no-cache, must-revalidate")
        } else if (session.uri != "/api/v1/preview") {
            response.addHeader("Cache-Control", "no-store")
        }
        if (session.uri == "/api/pair" || session.uri == "/api/v1/security/session/from-remembered") {
            response.addHeader("Pragma", "no-cache")
            response.addHeader("Expires", "0")
        }
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("X-Frame-Options", "DENY")
        response.addHeader("Referrer-Policy", "no-referrer")
        response.addHeader(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; " +
                "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
        )
        return response
    }

    private fun routeCategory(uri: String): String = uri.substringBefore('?')
        .split('/')
        .filter(String::isNotBlank)
        .take(2)
        .joinToString("_")
        .uppercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .take(40)
        .ifEmpty { "ROOT" }

    private fun route(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method

        publicRoute(session, uri, method)?.let { return it }

        val token = session.headers[WebSecurity.HEADER_SESSION]
        val csrf = session.headers[WebSecurity.HEADER_CSRF]

        compatibilityRoute(session, uri, method, token, csrf)?.let { return it }
        rememberedBrowserRoute(session, uri, method, token, csrf)?.let { return it }
        versionedReadRoute(session, uri, method, token)?.let { return it }
        versionedWriteRoute(session, uri, method, token, csrf)?.let { return it }
        uploadRoute(session, uri, method, token, csrf)?.let { return it }

        return text(Response.Status.NOT_FOUND, "Not found")
    }

    private fun publicRoute(session: IHTTPSession, uri: String, method: Method): Response? {
        if ((uri == "/" || uri == "/pair") && method == Method.GET) return html(SetupPage.HTML)
        if ((uri == "/assets/app-${WebUiAssets.REVISION}.css" || uri == "/assets/app.css") && method == Method.GET) {
            return asset("text/css; charset=utf-8", WebUiAssets.CSS)
        }
        if ((uri == "/assets/app-${WebUiAssets.REVISION}.js" || uri == "/assets/app.js") && method == Method.GET) {
            return asset("application/javascript; charset=utf-8", WebUiAssets.JS)
        }
        if (uri == "/api/pair" && method == Method.POST) return handlePair(session)
        if (uri == "/api/v1/security/remembered-browser-policy/public" && method == Method.GET) {
            return rememberedPolicyPublic()
        }
        if (uri == "/api/v1/security/session/from-remembered" && method == Method.POST) {
            return handleRememberedExchange(session)
        }
        return null
    }

    private fun compatibilityRoute(
        session: IHTTPSession,
        uri: String,
        method: Method,
        token: String?,
        csrf: String?,
    ): Response? = when {
            uri == "/api/status" && method == Method.GET -> authed(token) {
                jsonOk(runBlocking { backend.statusJson() })
            }
            uri == "/api/config" && method == Method.GET -> authed(token) {
                jsonOk(runBlocking { backend.redactedConfigJson() })
            }
            uri == "/api/diagnostics" && method == Method.GET -> authed(token) {
                jsonOk(runBlocking { backend.diagnosticsJson() })
            }
            uri == "/api/diagnostics/bundle" && method == Method.GET -> authed(token) {
                downloadStream(
                    "application/x-ndjson",
                    DiagnosticsDownloadNaming.fileName(nowEpochMs()),
                    runBlocking { backend.diagnosticsBundle() },
                )
            }
            uri == "/api/diagnostics/clear" && method == Method.POST -> guarded(token, csrf) {
                runBlocking { backend.clearDiagnostics() }
                jsonOk(buildJsonObject { put("ok", true) })
            }
            uri == "/api/config" && method == Method.POST -> guarded(token, csrf) {
                val patch = readJsonObject(session) ?: return@guarded badJson()
                val error = runBlocking { backend.applyConfig(patch) }
                if (error == null) jsonOk(buildJsonObject { put("ok", true) })
                else text(Response.Status.BAD_REQUEST, error)
            }
            uri == "/api/control" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                val action = body.string("action") ?: return@guarded text(Response.Status.BAD_REQUEST, "Missing action")
                controlResponse(action)
            }
            uri == "/api/source/test" && method == Method.POST -> guarded(token, csrf) {
                jsonOk(buildJsonObject { put("result", runBlocking { backend.testSavedSource() }) })
            }
            uri == "/api/logout" && method == Method.POST -> guarded(token, csrf) {
                security.revoke(token)
                jsonOk(buildJsonObject { put("ok", true) })
            }
        else -> null
    }

    private fun rememberedBrowserRoute(
        session: IHTTPSession,
        uri: String,
        method: Method,
        token: String?,
        csrf: String?,
    ): Response? = when {
            uri == "/api/v1/security/remembered-browsers" && method == Method.GET -> authed(token) {
                rememberedBrowserList(token)
            }
            uri == "/api/v1/security/remembered-browser-policy" && method == Method.GET -> authed(token) {
                rememberedPolicyPrivate()
            }
            uri == "/api/v1/security/remembered-browser-policy" && method == Method.POST -> guarded(token, csrf) {
                updateRememberedPolicy(session)
            }
            uri == "/api/v1/security/remembered-browsers/current/revoke" && method == Method.POST -> guarded(token, csrf) {
                revokeCurrentRememberedBrowser(token)
            }
            uri == "/api/v1/security/remembered-browsers/revoke-others" && method == Method.POST -> guarded(token, csrf) {
                revokeOtherRememberedBrowsers(token)
            }
            uri == "/api/v1/security/remembered-browsers/revoke-all" && method == Method.POST -> guarded(token, csrf) {
                revokeAllRememberedBrowsers(session)
            }
            uri.startsWith("/api/v1/security/remembered-browsers/") && uri.endsWith("/revoke") && method == Method.POST -> guarded(token, csrf) {
                val id = uri.removePrefix("/api/v1/security/remembered-browsers/").removeSuffix("/revoke")
                revokeRememberedBrowser(id)
            }
        else -> null
    }

    private fun versionedReadRoute(
        session: IHTTPSession,
        uri: String,
        method: Method,
        token: String?,
    ): Response? = when {
            uri == "/api/v1/status" && method == Method.GET -> authed(token) {
                v1Ok(runBlocking { backend.statusJson() })
            }
            uri == "/api/v1/settings" && method == Method.GET -> authed(token) {
                val revision = runBlocking { backend.settingsRevision() }
                v1Ok(runBlocking { backend.redactedConfigJson() }, revision)
            }
            uri == "/api/v1/folders" && method == Method.GET -> authed(token) {
                v1Ok(runBlocking { backend.foldersJson() })
            }
            uri == "/api/v1/presentation/current" && method == Method.GET -> authed(token) {
                val data = runBlocking { backend.presentationJson() }
                if (data.isEmpty()) v1Ok(JsonNull) else v1Ok(data)
            }
            uri == "/api/v1/preview" && method == Method.GET -> authed(token) {
                previewResponse(session)
            }
            uri == "/api/v1/diagnostics/summary" && method == Method.GET -> authed(token) {
                v1Ok(runBlocking { backend.diagnosticsSummary() })
            }
            uri == "/api/v1/diagnostics/events" && method == Method.GET -> authed(token) {
                val limit = query(session, "limit")?.toIntOrNull()?.coerceIn(1, 200) ?: 100
                val diagnosticsQuery = DiagnosticsQuery(
                    cursor = query(session, "cursor")?.take(96),
                    limit = limit,
                    severity = query(session, "severity")?.take(16),
                    category = query(session, "category")?.take(24),
                    sessionId = query(session, "sessionId")?.take(40),
                    code = query(session, "code")?.take(64),
                    trigger = query(session, "trigger")?.take(64),
                    operationId = query(session, "operationId")?.take(80),
                    origin = query(session, "origin")?.take(24),
                    search = query(session, "search")?.take(120),
                )
                v1Ok(runBlocking { backend.diagnosticsEvents(diagnosticsQuery) })
            }
            uri == "/api/v1/backup/export" && method == Method.GET -> authed(token) {
                downloadText(
                    "application/json",
                    "familyphotoframe-settings.json",
                    runBlocking { backend.backupExport() },
                )
            }
        else -> null
    }

    private fun versionedWriteRoute(
        session: IHTTPSession,
        uri: String,
        method: Method,
        token: String?,
        csrf: String?,
    ): Response? = when {
            uri == "/api/v1/settings" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                val expectedRevision = body["revision"]?.jsonPrimitive?.longOrNull
                    ?: return@guarded v1Error(Response.Status.BAD_REQUEST, "MISSING_REVISION", "Missing settings revision")
                val currentRevision = runBlocking { backend.settingsRevision() }
                if (expectedRevision != currentRevision) {
                    return@guarded v1Error(
                        Response.Status.BAD_REQUEST,
                        "REVISION_CONFLICT",
                        "Settings changed on another client.",
                        extra = buildJsonObject { put("currentRevision", currentRevision) },
                    )
                }
                val patch = body["settings"] as? JsonObject
                    ?: return@guarded v1Error(Response.Status.BAD_REQUEST, "BAD_SETTINGS", "Missing settings object")
                val error = runBlocking { backend.applyConfig(patch) }
                if (error != null) {
                    val field = inferField(error, patch)
                    return@guarded v1Error(Response.Status.BAD_REQUEST, "VALIDATION_FAILED", error, field)
                }
                val revision = runBlocking { backend.settingsRevision() }
                v1Ok(runBlocking { backend.redactedConfigJson() }, revision)
            }
            uri.startsWith("/api/v1/playback/") && method == Method.POST -> guarded(token, csrf) {
                val action = uri.substringAfterLast('/')
                controlResponse(action)
            }
            uri == "/api/v1/backup/validate" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session, MAX_BACKUP_CHARS) ?: return@guarded badJson()
                val backup = body.string("backup")
                    ?: return@guarded v1Error(Response.Status.BAD_REQUEST, "MISSING_BACKUP", "Missing backup data")
                try {
                    v1Ok(runBlocking { backend.backupValidate(backup) })
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.BAD_REQUEST, "BACKUP_REJECTED", e.message ?: "Backup rejected")
                }
            }
            uri == "/api/v1/backup/import" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session, MAX_BACKUP_CHARS) ?: return@guarded badJson()
                val backup = body.string("backup")
                    ?: return@guarded v1Error(Response.Status.BAD_REQUEST, "MISSING_BACKUP", "Missing backup data")
                val error = runBlocking { backend.backupImport(backup) }
                if (error != null) v1Error(Response.Status.BAD_REQUEST, "BACKUP_REJECTED", error)
                else v1Ok(runBlocking { backend.redactedConfigJson() }, runBlocking { backend.settingsRevision() })
            }
            uri == "/api/v1/maintenance" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                val action = body.string("action")
                    ?: return@guarded v1Error(Response.Status.BAD_REQUEST, "MISSING_ACTION", "Missing maintenance action")
                val error = runBlocking { backend.maintenance(action) }
                onEvent("WEB_MAINTENANCE_ACTION", mapOf("action" to action, "ok" to (error == null).toString()))
                if (error == null) v1Ok(buildJsonObject { put("action", action) })
                else v1Error(Response.Status.BAD_REQUEST, "MAINTENANCE_FAILED", error)
            }
            uri == "/api/v1/source/test" && method == Method.POST -> guarded(token, csrf) {
                v1Ok(buildJsonObject { put("result", runBlocking { backend.testSavedSource() }) })
            }
            uri == "/api/v1/folders/action" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                try {
                    v1Ok(runBlocking { backend.folderAction(body) }, runBlocking { backend.settingsRevision() })
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.BAD_REQUEST, "FOLDER_ACTION_REJECTED", e.message ?: "Folder action rejected")
                }
            }
            uri == "/api/v1/playlists" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                try {
                    v1Ok(runBlocking { backend.playlistAction(body) }, runBlocking { backend.settingsRevision() })
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.BAD_REQUEST, "PLAYLIST_REJECTED", e.message ?: "Playlist request rejected")
                }
            }
            uri == "/api/v1/schedules/playlists" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                try {
                    v1Ok(runBlocking { backend.playlistScheduleAction(body) }, runBlocking { backend.settingsRevision() })
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.BAD_REQUEST, "SCHEDULE_REJECTED", e.message ?: "Schedule request rejected")
                }
            }
        else -> null
    }

    private fun uploadRoute(
        session: IHTTPSession,
        uri: String,
        method: Method,
        token: String?,
        csrf: String?,
    ): Response? {
        val uploadParts = uri.trim('/').split('/').filter { it.isNotEmpty() }
        return when {
            uri == "/api/v1/uploads" && method == Method.POST -> guarded(token, csrf) {
                val body = readJsonObject(session) ?: return@guarded badJson()
                try {
                    v1Ok(runBlocking { backend.uploadCreate(token.orEmpty(), body) })
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.BAD_REQUEST, "UPLOAD_REJECTED", e.message ?: "Upload rejected")
                }
            }
            uploadParts.size == 4 && uploadParts.take(3) == listOf("api", "v1", "uploads") && method == Method.GET -> authed(token) {
                try {
                    v1Ok(runBlocking { backend.uploadStatus(token.orEmpty(), uploadParts[3]) })
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.NOT_FOUND, "UPLOAD_NOT_FOUND", e.message ?: "Upload not found")
                }
            }
            uploadParts.size == 5 && uploadParts.take(3) == listOf("api", "v1", "uploads") &&
                uploadParts[4] == "files" && method == Method.POST -> guarded(token, csrf) {
                if (!session.headers["transfer-encoding"].isNullOrBlank()) {
                    return@guarded v1Error(Response.Status.BAD_REQUEST, "TRANSFER_ENCODING_REJECTED", "Chunked upload bodies are not supported")
                }
                val contentType = session.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()
                if (contentType != "application/octet-stream") {
                    return@guarded v1Error(Response.Status.BAD_REQUEST, "UNSUPPORTED_CONTENT_TYPE", "Upload content type must be application/octet-stream")
                }
                val length = session.headers["content-length"]?.toLongOrNull()
                    ?: return@guarded v1Error(Response.Status.BAD_REQUEST, "LENGTH_REQUIRED", "Content-Length is required")
                if (length <= 0L) {
                    return@guarded v1Error(Response.Status.BAD_REQUEST, "INVALID_LENGTH", "Content-Length must be positive")
                }
                val clientId = query(session, "clientId").orEmpty()
                val encodedName = session.headers["x-upload-file-name"].orEmpty()
                if (clientId.isBlank() || encodedName.isBlank()) {
                    return@guarded v1Error(Response.Status.BAD_REQUEST, "UPLOAD_METADATA_MISSING", "File id and name are required")
                }
                try {
                    val uploaded = runBlocking {
                        backend.uploadFile(token.orEmpty(), uploadParts[3], clientId, encodedName, length, session.inputStream)
                    }
                    // Only on success: a mid-stream failure leaves the body partially read.
                    requestBodyConsumed.set(true)
                    v1Ok(uploaded)
                } catch (e: IllegalArgumentException) {
                    v1Error(Response.Status.BAD_REQUEST, "UPLOAD_REJECTED", e.message ?: "Upload rejected")
                } catch (e: IllegalStateException) {
                    v1Error(Response.Status.BAD_REQUEST, "UPLOAD_FAILED", e.message ?: "Upload failed")
                }
            }
            uploadParts.size == 5 && uploadParts.take(3) == listOf("api", "v1", "uploads") &&
                uploadParts[4] == "complete" && method == Method.POST -> guarded(token, csrf) {
                try { v1Ok(runBlocking { backend.uploadComplete(token.orEmpty(), uploadParts[3]) }) }
                catch (e: IllegalArgumentException) { v1Error(Response.Status.BAD_REQUEST, "UPLOAD_INCOMPLETE", e.message ?: "Upload incomplete") }
            }
            uploadParts.size == 5 && uploadParts.take(3) == listOf("api", "v1", "uploads") &&
                uploadParts[4] == "cancel" && method == Method.POST -> guarded(token, csrf) {
                try { v1Ok(runBlocking { backend.uploadCancel(token.orEmpty(), uploadParts[3]) }) }
                catch (e: IllegalArgumentException) { v1Error(Response.Status.NOT_FOUND, "UPLOAD_NOT_FOUND", e.message ?: "Upload not found") }
            }
            else -> null
        }
    }

    private fun controlResponse(action: String): Response {
        val normalized = when (action) {
            "play" -> "resume"
            "previous" -> "prev"
            "restart-interval", "restart_interval" -> "restart_interval"
            else -> action
        }
        val error = runBlocking { backend.control(normalized) }
        onEvent("WEB_CONTROL", mapOf("action" to normalized))
        return if (error == null) v1Ok(buildJsonObject { put("action", normalized) })
        else v1Error(Response.Status.BAD_REQUEST, "CONTROL_FAILED", error)
    }

    private fun previewResponse(session: IHTTPSession): Response {
        val frame = backend.previewSnapshot()
            ?: return v1Error(Response.Status.NOT_FOUND, "PREVIEW_UNAVAILABLE", "No committed preview is available yet")
        val requested = query(session, "revision")
        val etag = "\"${frame.revision}\""
        if (session.headers["if-none-match"] == etag || requested == frame.revision && query(session, "metadataOnly") == "true") {
            return newFixedLengthResponse(Response.Status.NOT_MODIFIED, "image/jpeg", "")
        }
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "image/jpeg",
            ByteArrayInputStream(frame.jpeg),
            frame.jpeg.size.toLong(),
        )
        response.addHeader("ETag", etag)
        response.addHeader("X-Preview-Revision", frame.revision)
        response.addHeader("Cache-Control", "private, max-age=2")
        return response
    }

    private fun handlePair(session: IHTTPSession): Response {
        val body = readJsonObject(session, MAX_PAIR_BODY_CHARS) ?: return badJson()
        val pin = body.string("pin").orEmpty()
        val qr = body.string("token").orEmpty()
        val result = if (qr.isNotEmpty()) security.pairWithToken(qr) else security.pair(pin)
        return when (val r = result) {
            is WebSecurity.PairResult.Ok -> {
                onEvent("WEB_PAIRED", emptyMap())
                val remember = body["remember"] as? JsonObject
                val requestedMode = remember?.string("mode")?.let {
                    runCatching { RememberExpiryMode.valueOf(it.uppercase()) }.getOrNull()
                } ?: RememberExpiryMode.SESSION_ONLY
                if (qr.isNotEmpty() || requestedMode == RememberExpiryMode.SESSION_ONLY || rememberedBrowsers == null) {
                    jsonOk(buildJsonObject {
                        put("session", r.token)
                        put("csrf", r.csrfToken)
                        put("remembered", false)
                    })
                } else {
                    try {
                        val unit = remember?.string("unit")?.let {
                            runCatching { CustomExpiryUnit.valueOf(it.uppercase()) }.getOrNull()
                        }
                        val created = runBlocking {
                            rememberedBrowsers.create(
                                RememberedBrowserManager.CreateRequest(
                                    mode = requestedMode,
                                    amount = remember?.get("amount")?.jsonPrimitive?.intOrNull,
                                    unit = unit,
                                    confirmForever = remember?.get("confirmForever")?.jsonPrimitive?.booleanOrNull == true,
                                    label = remember?.string("label").orEmpty(),
                                    browserSummary = browserName(session.headers["user-agent"]),
                                    osSummary = osName(session.headers["user-agent"]),
                                )
                            )
                        }
                        security.revoke(r.token)
                        val rememberedSession = security.issueRememberedSession(created.id)
                        jsonOk(buildJsonObject {
                            put("session", rememberedSession.token)
                            put("csrf", rememberedSession.csrfToken)
                            put("remembered", true)
                            put("rememberedCredential", created.credential)
                            put("rememberedBrowserId", created.id)
                            created.expiresAtEpochMs?.let { put("expiresAtEpochMs", it) } ?: put("expiresAtEpochMs", JsonNull)
                            put("browserLabel", created.label)
                        })
                    } catch (e: IllegalArgumentException) {
                        jsonOk(buildJsonObject {
                            put("session", r.token)
                            put("csrf", r.csrfToken)
                            put("remembered", false)
                            put("rememberError", e.message ?: "Persistent browser trust was rejected")
                        })
                    } catch (e: IllegalStateException) {
                        jsonOk(buildJsonObject {
                            put("session", r.token)
                            put("csrf", r.csrfToken)
                            put("remembered", false)
                            put("rememberError", "Persistent browser trust is unavailable")
                        })
                    }
                }
            }
            is WebSecurity.PairResult.LockedOut -> {
                onEvent("WEB_PAIR_LOCKED", emptyMap())
                text(Response.Status.FORBIDDEN, "Too many attempts. Try again later.")
            }
            is WebSecurity.PairResult.Rejected -> {
                onEvent("WEB_PAIR_REJECTED", emptyMap())
                text(Response.Status.UNAUTHORIZED, "Pairing failed. Check the PIN on the frame.")
            }
        }
    }

    private fun handleRememberedExchange(session: IHTTPSession): Response {
        val manager = rememberedBrowsers
            ?: return rememberedAuthFailure()
        if (!rememberedExchangeAllowed(session)) {
            val response = v1Error(Response.Status.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many authorization attempts")
            response.addHeader("Retry-After", "60")
            return response
        }
        val contentType = session.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase()
        if (contentType != "application/json") {
            return v1Error(Response.Status.BAD_REQUEST, "UNSUPPORTED_CONTENT_TYPE", "Content type must be application/json")
        }
        val body = readJsonObject(session, MAX_REMEMBERED_EXCHANGE_CHARS) ?: return badJson()
        val credential = body.string("rememberedCredential").orEmpty()
        if (credential.isBlank()) return rememberedAuthFailure()
        return when (val result = runBlocking { manager.exchange(credential) }) {
            is RememberedBrowserManager.ExchangeResult.Ok -> {
                val active = security.issueRememberedSession(result.id)
                onEvent(
                    "REMEMBERED_BROWSER_SESSION_CREATED",
                    mapOf("browserToken" to diagnosticToken(result.id, "browser")),
                )
                v1Ok(buildJsonObject {
                    put("session", active.token)
                    put("csrf", active.csrfToken)
                    put("sessionExpiresInMs", 12L * 60L * 60_000L)
                    put("rotatedRememberedCredential", result.rotatedCredential)
                    put("rememberedBrowserId", result.id)
                    result.expiresAtEpochMs?.let { put("expiresAtEpochMs", it) } ?: put("expiresAtEpochMs", JsonNull)
                })
            }
            is RememberedBrowserManager.ExchangeResult.Rejected -> {
                if (result.revokeSessions && result.browserId != null) {
                    security.revokeSessionsForRememberedBrowser(result.browserId)
                }
                rememberedAuthFailure()
            }
            is RememberedBrowserManager.ExchangeResult.ClockRollback -> {
                security.revokeSessionsForRememberedBrowser(result.browserId)
                rememberedAuthFailure()
            }
            RememberedBrowserManager.ExchangeResult.KeyUnavailable -> rememberedAuthFailure()
        }
    }

    private fun rememberedPolicyPublic(): Response {
        val policy = runBlocking { rememberedBrowsers?.policy() } ?: RememberedBrowserPolicy()
        return v1Ok(buildJsonObject {
            put("enabled", policy.enabled)
            put("defaultExpiry", policy.defaultExpiry.name)
            put("maxExpirySeconds", policy.maxExpirySeconds)
            put("allowForever", policy.allowForever)
            put("maxRememberedBrowsers", policy.maxRememberedBrowsers)
            put("plainHttp", true)
            put("warning", "Use remembered access only on a trusted private network.")
        })
    }

    private fun rememberedPolicyPrivate(): Response {
        val policy = runBlocking { rememberedBrowsers?.policy() } ?: RememberedBrowserPolicy()
        return v1Ok(buildJsonObject {
            put("enabled", policy.enabled)
            put("defaultExpiry", policy.defaultExpiry.name)
            put("maxExpirySeconds", policy.maxExpirySeconds)
            put("allowForever", policy.allowForever)
            put("maxRememberedBrowsers", policy.maxRememberedBrowsers)
            put("requireStepUpForSensitiveActions", policy.requireStepUpForSensitiveActions)
            put("rotateOnExchange", policy.rotateOnExchange)
            put("rotationGraceSeconds", policy.rotationGraceSeconds)
            put("plainHttp", true)
            put("warning", "Use remembered access only on a trusted private network.")
        })
    }

    private fun rememberedBrowserList(token: String?): Response {
        val manager = rememberedBrowsers ?: return v1Ok(buildJsonArray { })
        val currentId = security.rememberedBrowserIdForSession(token)
        val now = System.currentTimeMillis()
        val rows = runBlocking { manager.list() }
        return v1Ok(buildJsonArray {
            rows.forEach { row ->
                add(buildJsonObject {
                    put("id", row.id)
                    put("label", row.label)
                    put("createdAtEpochMs", row.createdAtEpochMs)
                    put("lastUsedAtEpochMs", row.lastUsedAtEpochMs)
                    row.expiresAtEpochMs?.let { put("expiresAtEpochMs", it) } ?: put("expiresAtEpochMs", JsonNull)
                    row.revokedAtEpochMs?.let { put("revokedAtEpochMs", it) } ?: put("revokedAtEpochMs", JsonNull)
                    put("browserSummary", row.browserSummary.orEmpty())
                    put("osSummary", row.osSummary.orEmpty())
                    put("current", row.id == currentId)
                    put("status", when {
                        row.revokedAtEpochMs != null -> "REVOKED"
                        row.expiresAtEpochMs != null && now >= row.expiresAtEpochMs -> "EXPIRED"
                        else -> "ACTIVE"
                    })
                })
            }
        })
    }

    private fun updateRememberedPolicy(session: IHTTPSession): Response {
        val manager = rememberedBrowsers
            ?: return v1Error(Response.Status.BAD_REQUEST, "REMEMBERED_UNAVAILABLE", "Remembered browsers are unavailable")
        val body = readJsonObject(session, MAX_PAIR_BODY_CHARS) ?: return badJson()
        val pin = body.string("pin").orEmpty()
        if (!security.verifyPin(pin)) {
            return v1Error(Response.Status.UNAUTHORIZED, "STEP_UP_REQUIRED", "The current frame PIN is required")
        }
        val current = runBlocking { manager.policy() }
        val defaultExpiry = body.string("defaultExpiry")?.let {
            runCatching { RememberExpiryMode.valueOf(it.uppercase()) }.getOrNull()
        } ?: current.defaultExpiry
        val policy = current.copy(
            enabled = body["enabled"]?.jsonPrimitive?.booleanOrNull ?: current.enabled,
            defaultExpiry = defaultExpiry,
            maxExpirySeconds = body["maxExpirySeconds"]?.jsonPrimitive?.longOrNull ?: current.maxExpirySeconds,
            allowForever = body["allowForever"]?.jsonPrimitive?.booleanOrNull ?: current.allowForever,
            maxRememberedBrowsers = body["maxRememberedBrowsers"]?.jsonPrimitive?.intOrNull ?: current.maxRememberedBrowsers,
            requireStepUpForSensitiveActions = body["requireStepUpForSensitiveActions"]?.jsonPrimitive?.booleanOrNull
                ?: current.requireStepUpForSensitiveActions,
        ).normalized()
        runBlocking { manager.updatePolicy(policy) }
        return rememberedPolicyPrivate()
    }

    private fun revokeCurrentRememberedBrowser(token: String?): Response {
        val id = security.rememberedBrowserIdForSession(token)
            ?: return v1Error(Response.Status.BAD_REQUEST, "NOT_REMEMBERED_SESSION", "This active session is not from a remembered browser")
        val changed = runBlocking { rememberedBrowsers?.revoke(id) } == true
        security.revokeSessionsForRememberedBrowser(id)
        return v1Ok(buildJsonObject { put("revoked", changed); put("id", id) })
    }

    private fun revokeOtherRememberedBrowsers(token: String?): Response {
        val id = security.rememberedBrowserIdForSession(token)
            ?: return v1Error(Response.Status.BAD_REQUEST, "NOT_REMEMBERED_SESSION", "This active session is not from a remembered browser")
        val count = runBlocking { rememberedBrowsers?.revokeAllExcept(id) } ?: 0
        // Manager does not expose every revoked id, so close all active sessions except current token.
        security.revokeAllSessions()
        val fresh = security.issueRememberedSession(id)
        return v1Ok(buildJsonObject {
            put("revokedCount", count)
            put("replacementSession", fresh.token)
            put("replacementCsrf", fresh.csrfToken)
        })
    }

    private fun revokeAllRememberedBrowsers(session: IHTTPSession): Response {
        val body = readJsonObject(session, MAX_PAIR_BODY_CHARS) ?: return badJson()
        if (!security.verifyPin(body.string("pin").orEmpty())) {
            return v1Error(Response.Status.UNAUTHORIZED, "STEP_UP_REQUIRED", "The current frame PIN is required")
        }
        val count = runBlocking { rememberedBrowsers?.revokeAll() } ?: 0
        security.revokeAllSessions()
        return v1Ok(buildJsonObject { put("revokedCount", count) })
    }

    private fun revokeRememberedBrowser(id: String): Response {
        if (id.length !in 16..80) return v1Error(Response.Status.BAD_REQUEST, "BAD_BROWSER_ID", "Invalid browser id")
        val changed = runBlocking { rememberedBrowsers?.revoke(id) } == true
        security.revokeSessionsForRememberedBrowser(id)
        return v1Ok(buildJsonObject { put("revoked", changed); put("id", id) })
    }

    private fun rememberedAuthFailure(): Response =
        v1Error(Response.Status.UNAUTHORIZED, "REMEMBERED_AUTH_FAILED", "Remembered browser authorization is no longer valid.")

    private fun rememberedExchangeAllowed(session: IHTTPSession): Boolean {
        val key = session.remoteIpAddress ?: "unknown"
        val now = System.currentTimeMillis()
        synchronized(rememberedGlobalExchangeAttempts) {
            while (rememberedGlobalExchangeAttempts.isNotEmpty() &&
                now - rememberedGlobalExchangeAttempts.first() > REMEMBERED_RATE_WINDOW_MS
            ) rememberedGlobalExchangeAttempts.removeFirst()
            if (rememberedGlobalExchangeAttempts.size >= REMEMBERED_GLOBAL_RATE_LIMIT) return false
        }
        val queue = rememberedExchangeAttempts.getOrPut(key) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && now - queue.first() > REMEMBERED_RATE_WINDOW_MS) queue.removeFirst()
            if (queue.size >= REMEMBERED_RATE_LIMIT) return false
            queue.addLast(now)
        }
        synchronized(rememberedGlobalExchangeAttempts) {
            rememberedGlobalExchangeAttempts.addLast(now)
        }
        if (rememberedExchangeAttempts.size > 128) {
            rememberedExchangeAttempts.entries.removeIf { (_, q) ->
                synchronized(q) { q.isEmpty() || now - q.last() > REMEMBERED_RATE_WINDOW_MS }
            }
        }
        return true
    }

    private fun browserName(userAgent: String?): String = when {
        userAgent.isNullOrBlank() -> "Browser"
        "Firefox" in userAgent -> "Firefox browser"
        "Edg/" in userAgent -> "Edge browser"
        "Chrome" in userAgent -> "Chrome browser"
        "Safari" in userAgent -> "Safari browser"
        else -> "Browser"
    }

    private fun osName(userAgent: String?): String? = when {
        userAgent.isNullOrBlank() -> null
        "Android" in userAgent -> "Android"
        "Windows" in userAgent -> "Windows"
        "Linux" in userAgent -> "Linux"
        "Mac OS" in userAgent -> "macOS"
        "iPhone" in userAgent || "iPad" in userAgent -> "iOS"
        else -> null
    }

    private inline fun authed(token: String?, block: () -> Response): Response =
        if (security.authenticate(token) == null) text(Response.Status.UNAUTHORIZED, "Pair first") else block()

    private inline fun guarded(token: String?, csrf: String?, block: () -> Response): Response =
        if (security.authorizeStateChange(token, csrf) == null) text(Response.Status.UNAUTHORIZED, "Pair first") else block()

    private fun hostAllowed(session: IHTTPSession): Boolean {
        val host = session.headers["host"] ?: return true
        val hostname = host.substringBefore(':')
        return hostname == boundHost || hostname == "localhost" || hostname == "127.0.0.1"
    }

    private fun originAllowed(session: IHTTPSession): Boolean {
        val origin = session.headers["origin"] ?: return true
        val host = session.headers["host"] ?: return false
        return origin.endsWith("//$host")
    }

    private fun query(session: IHTTPSession, name: String): String? = session.parameters[name]?.firstOrNull()

    /**
     * Prevent an unread request body from corrupting the next request.
     *
     * NanoHTTPD 2.3.1 leaves the body in the stream when a handler never reads it — its
     * own `HTTPSession.execute` carries an unimplemented TODO to skip it. On a keep-alive
     * connection those bytes are then parsed as the next request's start line, so a POST
     * rejected before [readJsonObject] (expired session, missing CSRF token, unknown
     * route) silently breaks whatever request follows it.
     *
     * Closing the connection is bounded work; draining would mean reading an arbitrarily
     * large body purely to discard it. `setKeepAlive` cannot be used here because
     * NanoHTTPD overwrites it after `serve` returns, but it honours `closeConnection`.
     */
    private fun closeIfBodyUnread(session: IHTTPSession, response: Response): Response {
        if (bodyWasConsumed()) return response
        val declared = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val chunked = session.headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        if (declared > 0L || chunked) response.closeConnection(true)
        return response
    }

    private fun readJsonObject(session: IHTTPSession, maxChars: Int = MAX_BODY_CHARS): JsonObject? {
        return try {
            if (!session.headers["transfer-encoding"].isNullOrBlank()) return null
            val declared = session.headers["content-length"]?.toLongOrNull() ?: return null
            if (declared > maxChars.toLong() * 4L) return null
            val files = HashMap<String, String>()
            session.parseBody(files)
            requestBodyConsumed.set(true)
            val raw = files["postData"].orEmpty()
            if (raw.length > maxChars) return null
            if (raw.isBlank()) buildJsonObject { } else json.parseToJsonElement(raw) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.let { if (it.isString) it.content else null }

    private fun inferField(error: String, patch: JsonObject): String? {
        val lower = error.lowercase()
        return patch.keys.firstOrNull { lower.contains(it.lowercase()) } ?: patch.keys.singleOrNull()
    }

    private fun v1Ok(data: kotlinx.serialization.json.JsonElement, revision: Long? = null): Response =
        jsonOk(buildJsonObject {
            put("ok", true)
            revision?.let { put("revision", it) }
            put("data", data)
        })

    private fun v1Error(
        status: Response.Status,
        code: String,
        message: String,
        field: String? = null,
        extra: JsonObject? = null,
    ): Response = json(status, buildJsonObject {
        put("ok", false)
        put("code", code)
        put("message", message)
        field?.let { put("field", it) }
        extra?.forEach { (key, value) -> put(key, value) }
    })

    private fun badJson(): Response = v1Error(Response.Status.BAD_REQUEST, "BAD_JSON", "Malformed or oversized JSON body")

    private fun jsonOk(obj: JsonObject): Response = json(Response.Status.OK, obj)
    private fun json(status: Response.Status, obj: JsonObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString())

    private fun text(status: Response.Status, message: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", message)

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun asset(type: String, body: String): Response =
        newFixedLengthResponse(Response.Status.OK, type, body)

    private fun downloadText(type: String, fileName: String, body: String): Response {
        val response = newFixedLengthResponse(Response.Status.OK, "$type; charset=utf-8", body)
        response.addHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
        return response
    }

    private fun downloadStream(type: String, fileName: String, body: InputStream): Response {
        val response = newChunkedResponse(Response.Status.OK, "$type; charset=utf-8", body)
        response.addHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Pragma", "no-cache")
        return response
    }

    private companion object {
        const val MAX_BODY_CHARS = 256 * 1024
        const val MAX_BACKUP_CHARS = 1024 * 1024
        const val MAX_PAIR_BODY_CHARS = 8 * 1024
        const val MAX_REMEMBERED_EXCHANGE_CHARS = 2 * 1024
        const val REMEMBERED_RATE_LIMIT = 20
        const val REMEMBERED_GLOBAL_RATE_LIMIT = 200
        const val REMEMBERED_RATE_WINDOW_MS = 10L * 60_000L
    }
}

private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else contentOrNullFallback()
private fun JsonPrimitive.contentOrNullFallback(): String? =
    booleanOrNull?.toString() ?: intOrNull?.toString() ?: content.ifBlank { null }
