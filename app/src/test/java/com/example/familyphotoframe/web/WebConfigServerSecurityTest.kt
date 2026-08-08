package com.example.familyphotoframe.web

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * End-to-end web security test required by spec §22.5: proves that an unauthenticated
 * client on the LAN cannot read configuration or control the frame, that CSRF is
 * enforced on state changes, and that the config endpoint never discloses secrets.
 *
 * Runs a **real** [WebConfigServer] on loopback and drives it over real HTTP, so it
 * exercises the actual routing and header checks rather than mocks. Lives in the JVM
 * test source set because the HTTP layer has no Android dependencies.
 */
class WebConfigServerSecurityTest {

    private lateinit var server: WebConfigServer
    private lateinit var security: WebSecurity
    private var port = 0
    private var controlCalls = 0

    /** Minimal backend; records whether control actions actually reached the app. */
    private inner class FakeBackend : WebBackendAdapter() {
        override suspend fun statusJson(): JsonObject = buildJsonObject { put("engineState", "PLAYING_PRIMARY") }

        override suspend fun redactedConfigJson(): JsonObject = buildJsonObject {
            put("intervalSeconds", 15)
            put("smbHost", "nas.local")
            put("smbUser", "frame")
            put("smbPasswordSet", true)      // boolean only: never the secret itself
        }

        override suspend fun applyConfig(patch: JsonObject): String? = null
        override suspend fun control(action: String): String? { controlCalls++; return null }
        override suspend fun testSavedSource(): String = "ok"
        override suspend fun diagnosticsJson(): JsonObject = buildJsonObject { put("appVersion", "test") }
        override suspend fun diagnosticsBundle(): InputStream =
            ByteArrayInputStream("{\"recordType\":\"bundleMetadata\"}\n{\"code\":\"TEST\"}\n".toByteArray())
        override suspend fun settingsRevision(): Long = 42L
    }

    @Before fun setUp() {
        port = ServerSocket(0).use { it.localPort }          // free ephemeral port
        security = WebSecurity(idleTimeoutMs = 60_000L)
        server = WebConfigServer(
            "127.0.0.1", port, security, FakeBackend(), nowEpochMs = { 0L },
        ) { _, _ -> }
        server.start(2_000, true)
    }

    @After fun tearDown() { server.stop() }

    // ---- helpers ----

    private class Result(
        val code: Int,
        val body: String,
        val contentDisposition: String = "",
        val contentType: String = "",
        val cacheControl: String = "",
    )

    private fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        session: String? = null,
        csrf: String? = null,
    ): Result {
        val conn = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            session?.let { setRequestProperty(WebSecurity.HEADER_SESSION, it) }
            csrf?.let { setRequestProperty(WebSecurity.HEADER_CSRF, it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val contentDisposition = conn.getHeaderField("Content-Disposition").orEmpty()
        val contentType = conn.getHeaderField("Content-Type").orEmpty()
        val cacheControl = conn.getHeaderField("Cache-Control").orEmpty()
        conn.disconnect()
        return Result(code, text, contentDisposition, contentType, cacheControl)
    }

    /**
     * Raw HTTP over a socket.
     *
     * [HttpURLConnection] silently drops restricted headers, `Origin` among them, so the
     * cross-site check cannot be exercised through it: the server would never see the
     * header and would correctly allow the request.
     */
    private fun rawRequest(
        path: String,
        method: String,
        body: String,
        session: String,
        csrf: String,
        origin: String,
    ): Result = java.net.Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 5_000
        val request = buildString {
            append("$method $path HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            append("${WebSecurity.HEADER_SESSION}: $session\r\n")
            append("${WebSecurity.HEADER_CSRF}: $csrf\r\n")
            append("Origin: $origin\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${body.toByteArray().size}\r\n")
            append("Connection: close\r\n\r\n")
            append(body)
        }
        socket.getOutputStream().apply { write(request.toByteArray()); flush() }
        val text = socket.getInputStream().bufferedReader().readText()
        val status = text.lineSequence().first().split(" ").getOrNull(1)?.toIntOrNull() ?: -1
        Result(status, text.substringAfter("\r\n\r\n", ""))
    }

    /**
     * Simulates the persistent HTTP/1.1 sockets opened by a browser. Reading to EOF
     * completes only when the server closes the connection after the response; without
     * that policy NanoHTTPD keeps a bounded worker blocked waiting for another request.
     */
    private fun rawKeepAliveGet(path: String): String = java.net.Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 2_000
        val request = buildString {
            append("GET $path HTTP/1.1\r\n")
            append("Host: 127.0.0.1:$port\r\n")
            append("Connection: keep-alive\r\n\r\n")
        }
        socket.getOutputStream().apply { write(request.toByteArray()); flush() }
        socket.getInputStream().bufferedReader().readText()
    }

    private fun pairSuccessfully(): Pair<String, String> {
        val pin = security.regeneratePin()
        val r = request("/api/pair", "POST", """{"pin":"$pin"}""")
        assertEquals(200, r.code)
        val obj = Json.parseToJsonElement(r.body) as JsonObject
        return obj["session"]!!.jsonPrimitive.content to obj["csrf"]!!.jsonPrimitive.content
    }

    // ---- unauthenticated access ----

    @Test fun unauthenticated_cannotReadConfig() {
        security.regeneratePin()
        assertEquals(401, request("/api/config").code)
    }

    @Test fun unauthenticated_cannotReadStatusOrDiagnostics() {
        security.regeneratePin()
        assertEquals(401, request("/api/status").code)
        assertEquals(401, request("/api/diagnostics").code)
        assertEquals(401, request("/api/diagnostics/bundle").code)
    }

    @Test fun authenticatedDiagnosticsBundleIsStreamedCompletely() {
        val (session, _) = pairSuccessfully()
        val result = request("/api/diagnostics/bundle", session = session)
        assertEquals(200, result.code)
        assertTrue(result.body.contains("\"recordType\":\"bundleMetadata\""))
        assertTrue(result.body.contains("\"code\":\"TEST\""))
        assertEquals(
            "attachment; filename=\"FamilyPhotoFrame-diagnostics-19700101T000000000Z.jsonl\"",
            result.contentDisposition,
        )
        assertTrue(result.contentType.startsWith("application/x-ndjson"))
        assertEquals("no-store", result.cacheControl)
    }

    @Test fun unauthenticated_cannotControlTheFrame() {
        security.regeneratePin()
        val r = request("/api/control", "POST", """{"action":"next"}""")
        assertEquals(401, r.code)
        assertEquals("no control action reached the app", 0, controlCalls)
    }

    @Test fun unauthenticated_cannotWriteConfig() {
        security.regeneratePin()
        assertEquals(401, request("/api/config", "POST", """{"intervalSeconds":99}""").code)
    }

    @Test fun setupPageIsServedWithoutAuth() {
        // The page itself is public; it holds no data until the client pairs.
        val r = request("/")
        assertEquals(200, r.code)
        assertTrue(r.body.contains("Photo Frame setup"))
    }

    @Test fun browserKeepAliveSocketsCannotStarveBoundedWorkers() {
        // More connections than the complete worker + queue capacity must remain
        // serviceable because each completed response releases its worker immediately.
        repeat(BoundedHttpAsyncRunner.DEFAULT_WORKERS + BoundedHttpAsyncRunner.DEFAULT_QUEUE_CAPACITY + 2) {
            val response = rawKeepAliveGet("/")
            assertTrue(response.startsWith("HTTP/1.1 200"))
            assertTrue(response.contains("Connection: close", ignoreCase = true))
            assertTrue(response.contains("Photo Frame setup"))
        }

        // Prove that a normal browser/API request still succeeds after the burst.
        val (session, _) = pairSuccessfully()
        assertEquals(200, request("/api/v1/status", session = session).code)
    }

    // ---- pairing ----

    @Test fun wrongPinIsRejected() {
        val pin = security.regeneratePin()
        val wrong = if (pin.startsWith("0")) "9" + pin.drop(1) else "0" + pin.drop(1)
        assertEquals(401, request("/api/pair", "POST", """{"pin":"$wrong"}""").code)
    }

    @Test fun correctPinGrantsAccess() {
        val (session, _) = pairSuccessfully()
        assertEquals(200, request("/api/status", session = session).code)
    }

    @Test fun qrTokenPairsOnceThenIsConsumed() {
        security.regeneratePin()
        val token = security.issueQrToken()!!
        assertEquals(200, request("/api/pair", "POST", """{"token":"$token"}""").code)
        // Replaying the same token must fail (single use).
        assertEquals(401, request("/api/pair", "POST", """{"token":"$token"}""").code)
    }

    @Test fun forgedSessionTokenIsRejected() {
        pairSuccessfully()
        assertEquals(401, request("/api/config", session = "f".repeat(64)).code)
    }

    // ---- CSRF ----

    @Test fun stateChangeWithoutCsrfIsRejected() {
        val (session, _) = pairSuccessfully()
        val r = request("/api/control", "POST", """{"action":"next"}""", session = session)
        assertEquals(401, r.code)
        assertEquals(0, controlCalls)
    }

    @Test fun stateChangeWithWrongCsrfIsRejected() {
        val (session, _) = pairSuccessfully()
        val r = request("/api/control", "POST", """{"action":"next"}""", session = session, csrf = "nope")
        assertEquals(401, r.code)
        assertEquals(0, controlCalls)
    }

    @Test fun stateChangeWithSessionAndCsrfSucceeds() {
        val (session, csrf) = pairSuccessfully()
        val r = request("/api/control", "POST", """{"action":"next"}""", session = session, csrf = csrf)
        assertEquals(200, r.code)
        assertEquals(1, controlCalls)
    }

    // ---- disclosure & origin ----

    @Test fun configResponseContainsNoSecrets() {
        val (session, _) = pairSuccessfully()
        val body = request("/api/config", session = session).body
        assertTrue(body.contains("smbHost"))
        assertFalse("password must not be disclosed", body.contains("\"smbPassword\""))
        assertFalse("credential ref must not be disclosed", body.contains("credentialRef"))
        // Synology (Phase 2 increment 15): neither the password nor the 2FA code may
        // ever appear, in any casing.
        assertFalse("synology password must not be disclosed", body.lowercase().contains("synpassword"))
        assertFalse("2FA code must not be disclosed", body.lowercase().contains("otp"))
    }

    @Test fun crossSiteOriginIsRejected() {
        val (session, csrf) = pairSuccessfully()
        val r = rawRequest(
            "/api/control", "POST", """{"action":"next"}""",
            session = session, csrf = csrf, origin = "http://evil.example",
        )
        assertEquals(403, r.code)
        assertEquals(0, controlCalls)
    }

    @Test fun logoutRevokesTheSession() {
        val (session, csrf) = pairSuccessfully()
        assertEquals(200, request("/api/logout", "POST", "{}", session = session, csrf = csrf).code)
        assertEquals(401, request("/api/config", session = session).code)
    }
    @Test fun versionedSettingsRequireAuthenticationAndCsrf() {
        security.regeneratePin()
        assertEquals(401, request("/api/v1/settings").code)
        val (session, csrf) = pairSuccessfully()
        assertEquals(200, request("/api/v1/settings", session = session).code)
        assertEquals(
            401,
            request(
                "/api/v1/settings", "POST",
                """{"revision":42,"settings":{"intervalSeconds":18}}""",
                session = session,
            ).code,
        )
        assertEquals(
            200,
            request(
                "/api/v1/settings", "POST",
                """{"revision":42,"settings":{"intervalSeconds":18}}""",
                session = session,
                csrf = csrf,
            ).code,
        )
    }

    @Test fun staleSettingsRevisionIsRejectedStructurally() {
        val (session, csrf) = pairSuccessfully()
        val r = request(
            "/api/v1/settings", "POST",
            """{"revision":41,"settings":{"intervalSeconds":18}}""",
            session = session,
            csrf = csrf,
        )
        assertEquals(400, r.code)
        assertTrue(r.body.contains("REVISION_CONFLICT"))
        assertTrue(r.body.contains("currentRevision"))
    }

    @Test fun staticAssetsArePublicButPreviewRequiresPairing() {
        assertEquals(200, request("/assets/app.css").code)
        assertEquals(200, request("/assets/app.js").code)
        assertEquals(401, request("/api/v1/preview").code)
    }

}
