package com.example.familyphotoframe.data.source

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

private class ExecutableFakeSynologyHttp : SynologyHttpClient {
    val loginCount = AtomicInteger(0)
    val logoutCount = AtomicInteger(0)
    var loginResponse: suspend (Int) -> HttpTextResponse = { count ->
        HttpTextResponse(200, """{"success":true,"data":{"sid":"SID-$count"}}""")
    }
    var streamResponse: suspend (String) -> HttpStreamResponse = {
        imageResponse(byteArrayOf(1))
    }

    override suspend fun getText(url: String, timeoutMs: Long): HttpTextResponse = when {
        "method=login" in url -> loginResponse(loginCount.incrementAndGet())
        "method=logout" in url -> {
            logoutCount.incrementAndGet()
            HttpTextResponse(200, """{"success":true}""")
        }
        "method=list" in url -> HttpTextResponse(
            200,
            """{"success":true,"data":{"files":[],"total":0}}""",
        )
        else -> HttpTextResponse(500, "")
    }

    override suspend fun openStream(url: String, timeoutMs: Long): HttpStreamResponse =
        streamResponse(url)
}

private fun source(http: SynologyHttpClient) = SynologyFileStationSource(
    id = SourceId("synology-check"),
    conn = SynologyConnection("https://nas.invalid", "/photo"),
    credentials = SynologyCredentials("user", "password", "123456"),
    io = Dispatchers.Default,
    http = http,
)

private fun item() = PhotoItem(
    stableId = "photo-1",
    sourceId = SourceId("synology-check"),
    normalizedPath = "album/a.jpg",
    folderName = "album",
    fileName = "a.jpg",
    mimeType = "image/jpeg",
    sizeBytes = 4,
    fileModifiedEpochMs = 1,
    openToken = "/photo/album/a.jpg",
)

private fun imageResponse(bytes: ByteArray): HttpStreamResponse = HttpStreamResponse(
    status = 200,
    contentType = "image/jpeg",
    contentLength = bytes.size.toLong(),
    body = ByteArrayInputStream(bytes),
)

private fun jsonResponse(json: String): HttpStreamResponse = HttpStreamResponse(
    status = 200,
    contentType = "application/json",
    contentLength = json.length.toLong(),
    body = ByteArrayInputStream(json.toByteArray()),
)

fun main() = runBlocking {
    val streamClosed = CountDownLatch(1)
    val deadline = DeadlineInputStream(
        object : InputStream() {
            override fun read(): Int = -1
            override fun close() = streamClosed.countDown()
        },
        timeoutMs = 20,
    )
    check(streamClosed.await(2, TimeUnit.SECONDS))
    runCatching { deadline.read() }
        .onSuccess { error("deadline stream remained readable") }

    val closeCount = AtomicInteger(0)
    val owner = DeferredCloseResource(factory = { Any() }) { closeCount.incrementAndGet() }
    val lease = owner.acquire()
    owner.close()
    check(closeCount.get() == 0)
    lease.close()
    check(owner.awaitClosed(100) && closeCount.get() == 1)

    val expiryHttp = ExecutableFakeSynologyHttp()
    expiryHttp.streamResponse = { url ->
        if ("_sid=SID-1" in url) {
            jsonResponse("""{"success":false,"error":{"code":119}}""")
        } else {
            imageResponse(byteArrayOf(4, 2))
        }
    }
    val expirySource = source(expiryHttp)
    val bytes = expirySource.openStream(item(), OpenOptions(timeoutMs = 5_000)).use { it.readBytes() }
    check(bytes.contentEquals(byteArrayOf(4, 2)))
    check(expiryHttp.loginCount.get() == 2)
    expirySource.shutdown()
    check(expiryHttp.logoutCount.get() == 1)

    val cancelledHttp = ExecutableFakeSynologyHttp()
    val loginStarted = CompletableDeferred<Unit>()
    val releaseLogin = CompletableDeferred<Unit>()
    cancelledHttp.loginResponse = { count ->
        loginStarted.complete(Unit)
        releaseLogin.await()
        HttpTextResponse(200, """{"success":true,"data":{"sid":"SID-$count"}}""")
    }
    val cancelledSource = source(cancelledHttp)
    val attempt = async(Dispatchers.Default) { cancelledSource.healthCheck(5_000) }
    loginStarted.await()
    attempt.cancel()
    releaseLogin.complete(Unit)
    attempt.join()
    cancelledSource.shutdown()
    check(cancelledHttp.loginCount.get() == 1)
    check(cancelledHttp.logoutCount.get() == 1)

    println("Source deadline, deferred close, and Synology session lifecycle checks passed")
}
