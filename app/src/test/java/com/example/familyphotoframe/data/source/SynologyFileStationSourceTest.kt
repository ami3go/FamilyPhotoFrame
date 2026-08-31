package com.example.familyphotoframe.data.source

import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynologyFileStationSourceTest {
    private class FakeHttpClient : SynologyHttpClient {
        val textUrls = Collections.synchronizedList(mutableListOf<String>())
        val streamUrls = Collections.synchronizedList(mutableListOf<String>())
        var loginCount = 0
        var loginResponse: suspend (Int) -> HttpTextResponse = { count ->
            HttpTextResponse(200, """{"success":true,"data":{"sid":"SID-$count"}}""")
        }
        var streamResponse: suspend (String) -> HttpStreamResponse = { imageResponse() }

        override suspend fun getText(url: String, timeoutMs: Long): HttpTextResponse {
            textUrls += url
            return when {
                "method=login" in url -> {
                    loginCount++
                    loginResponse(loginCount)
                }
                "method=logout" in url -> HttpTextResponse(200, """{"success":true}""")
                "method=list" in url -> HttpTextResponse(
                    200,
                    """{"success":true,"data":{"files":[],"total":0}}""",
                )
                else -> HttpTextResponse(500, "")
            }
        }

        override suspend fun openStream(url: String, timeoutMs: Long): HttpStreamResponse {
            streamUrls += url
            return streamResponse(url)
        }
    }

    @Test fun shutdownLogsOutTheOwnedSessionExactlyOnce() = runBlocking {
        val http = FakeHttpClient()
        val source = source(http)

        assertEquals(SourceHealth.Ok, source.healthCheck(5_000))
        source.shutdown()
        source.shutdown()

        assertEquals(1, http.textUrls.count { "method=login" in it })
        assertEquals(1, http.textUrls.count { "method=logout" in it })
        assertTrue(http.textUrls.single { "method=logout" in it }.contains("_sid=SID-1"))
    }

    @Test fun successfulHttpJsonSessionErrorIsReauthenticatedAndNeverReturnedAsImage() = runBlocking {
        val http = FakeHttpClient()
        http.streamResponse = { url ->
            if ("_sid=SID-1" in url) {
                jsonResponse("""{"success":false,"error":{"code":119}}""")
            } else {
                imageResponse(byteArrayOf(1, 2, 3, 4))
            }
        }
        val source = source(http)

        val bytes = source.openStream(item(), OpenOptions(timeoutMs = 5_000)).use { it.readBytes() }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes)
        assertEquals(2, http.loginCount)
        assertTrue(http.streamUrls.any { "_sid=SID-1" in it })
        assertTrue(http.streamUrls.any { "_sid=SID-2" in it })
        source.shutdown()
    }

    @Test fun thumbnailApiFailureFallsBackToOriginalBytes() = runBlocking {
        val http = FakeHttpClient()
        http.streamResponse = { url ->
            if ("FileStation.Thumb" in url) {
                jsonResponse("""{"success":false,"error":{"code":414}}""")
            } else {
                imageResponse(byteArrayOf(9, 8, 7))
            }
        }
        val source = source(http)

        val bytes = source.openStream(item(), OpenOptions(timeoutMs = 5_000)).use { it.readBytes() }
        assertArrayEquals(byteArrayOf(9, 8, 7), bytes)
        assertEquals(2, http.streamUrls.size)
        assertTrue("FileStation.Thumb" in http.streamUrls.first())
        assertTrue("FileStation.Download" in http.streamUrls.last())
        source.shutdown()
    }

    @Test fun concurrentExpiryResponsesReuseOneReplacementSession() = runBlocking {
        val http = FakeHttpClient()
        val expiredRequests = AtomicInteger(0)
        val bothExpiredRequestsStarted = CompletableDeferred<Unit>()
        http.streamResponse = { url ->
            if ("_sid=SID-1" in url) {
                if (expiredRequests.incrementAndGet() == 2) bothExpiredRequestsStarted.complete(Unit)
                bothExpiredRequestsStarted.await()
                jsonResponse("""{"success":false,"error":{"code":119}}""")
            } else {
                imageResponse(byteArrayOf(4, 2))
            }
        }
        val source = source(http)

        val results = coroutineScope {
            (1..2).map {
                async(Dispatchers.Default) {
                    source.openStream(item(), OpenOptions(timeoutMs = 5_000)).use { it.readBytes() }
                }
            }.awaitAll()
        }

        assertTrue(results.all { it.contentEquals(byteArrayOf(4, 2)) })
        assertEquals(2, http.loginCount)
        source.shutdown()
    }

    @Test fun cancelledLoginStillRetainsSidForLogout() = runBlocking {
        val http = FakeHttpClient()
        val loginStarted = CompletableDeferred<Unit>()
        val releaseLogin = CompletableDeferred<Unit>()
        http.loginResponse = { count ->
            loginStarted.complete(Unit)
            releaseLogin.await()
            HttpTextResponse(200, """{"success":true,"data":{"sid":"SID-$count"}}""")
        }
        val source = source(http)

        val attempt = async(Dispatchers.Default) { source.healthCheck(5_000) }
        loginStarted.await()
        attempt.cancel()
        releaseLogin.complete(Unit)
        attempt.join()
        source.shutdown()

        assertEquals(1, http.textUrls.count { "method=login" in it })
        assertEquals(1, http.textUrls.count { "method=logout" in it })
    }

    private fun source(http: SynologyHttpClient) = SynologyFileStationSource(
        id = SourceId("synology"),
        conn = SynologyConnection("https://nas.invalid", "/photo"),
        credentials = SynologyCredentials("user", "password", "123456"),
        io = Dispatchers.Default,
        http = http,
    )

    private fun item() = PhotoItem(
        stableId = "photo-1",
        sourceId = SourceId("synology"),
        normalizedPath = "album/a.jpg",
        folderName = "album",
        fileName = "a.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 4,
        fileModifiedEpochMs = 1,
        openToken = "/photo/album/a.jpg",
    )

    private companion object {
        fun imageResponse(bytes: ByteArray = byteArrayOf(1)): HttpStreamResponse = HttpStreamResponse(
            status = 200,
            contentType = "image/jpeg",
            contentLength = bytes.size.toLong(),
            body = ByteArrayInputStream(bytes),
        )

        fun jsonResponse(json: String): HttpStreamResponse = HttpStreamResponse(
            status = 200,
            contentType = "application/json",
            contentLength = json.length.toLong(),
            body = ByteArrayInputStream(json.toByteArray()),
        )
    }
}
