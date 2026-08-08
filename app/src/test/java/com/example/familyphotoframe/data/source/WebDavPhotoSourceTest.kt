package com.example.familyphotoframe.data.source

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPhotoSourceTest {
    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FakeClient(private val response: WebDavStreamResponse) : WebDavHttpClient {
        override suspend fun propfind(url: String, depth: Int, timeoutMs: Long): WebDavStreamResponse = response
        override suspend fun openStream(url: String, timeoutMs: Long): InputStream =
            throw IOException("not expected")
    }

    @Test fun scanStreamsFilesAndClosesResponse() = runBlocking {
        val xml = """<d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/dav/photos/</d:href><d:resourcetype><d:collection/></d:resourcetype></d:response>
            <d:response><d:href>/dav/photos/a.jpg</d:href><d:getcontentlength>7</d:getcontentlength></d:response>
            </d:multistatus>"""
        val stream = TrackingInputStream(xml.toByteArray())
        val source = WebDavPhotoSource(
            SourceId("webdav"),
            WebDavConnection("https://example.invalid", "/dav/photos"),
            WebDavCredentials("u", "p"),
            Dispatchers.Unconfined,
            FakeClient(WebDavStreamResponse(207, stream)),
        )

        val events = source.scan(null, ScanOptions()).toList()
        assertTrue(events.any { it is ScanEvent.FileFound && it.item.fileName == "a.jpg" })
        assertTrue(events.last() is ScanEvent.Finished)
        assertTrue(stream.closed)
    }

    @Test fun malformedListingClosesResponseAndReportsError() = runBlocking {
        val stream = TrackingInputStream(
            "<d:multistatus><d:response><d:href>/dav/photos/a.jpg</d:href>".toByteArray(),
        )
        val source = WebDavPhotoSource(
            SourceId("webdav"),
            WebDavConnection("https://example.invalid", "/dav/photos"),
            WebDavCredentials("u", "p"),
            Dispatchers.Unconfined,
            FakeClient(WebDavStreamResponse(207, stream)),
        )

        val events = source.scan(null, ScanOptions()).toList()
        assertTrue(events.any { it is ScanEvent.Error && it.error == SourceError.ProtocolError })
        assertTrue(stream.closed)
    }
}
