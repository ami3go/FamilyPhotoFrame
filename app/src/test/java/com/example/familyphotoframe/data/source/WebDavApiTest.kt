package com.example.familyphotoframe.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** Mirrors scripts/verify/WebDavChecks.kt so these also run under Gradle. */
class WebDavApiTest {

    @Test fun normalizesUrlsAndDefaultsToHttps() {
        assertEquals("https://nas.local", WebDavApi.normalizeBaseUrl("nas.local"))
        assertEquals("http://nas.local", WebDavApi.normalizeBaseUrl("http://nas.local"))
        assertEquals("https://nas.local", WebDavApi.normalizeBaseUrl("https://nas.local/"))
    }

    /** Regression: signed Byte arithmetic once emitted non-ASCII bytes unescaped. */
    @Test fun percentEncodesNonAsciiBytes() {
        assertEquals("/caf%C3%A9", WebDavApi.encodePath("/café"))
        assertEquals("/My%20Photos", WebDavApi.encodePath("/My Photos"))
        assertEquals("/a b/ünïcode", WebDavApi.decodePath(WebDavApi.encodePath("/a b/ünïcode")))
    }

    @Test fun parsesNextcloudListingAndDropsTheCollectionItself() {
        val xml = """<d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/dav/Photos/</d:href>
              <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
            </d:response>
            <d:response><d:href>/dav/Photos/beach%20day.jpg</d:href>
              <d:propstat><d:prop><d:resourcetype/>
                <d:getcontentlength>2048</d:getcontentlength>
                <d:getlastmodified>Mon, 15 Jul 2024 10:30:00 GMT</d:getlastmodified>
                <d:getcontenttype>image/jpeg</d:getcontenttype>
              </d:prop></d:propstat>
            </d:response></d:multistatus>"""
        val entries = WebDavApi.parsePropfind(xml, "/dav/Photos")!!
        assertEquals(1, entries.size)
        assertEquals("beach day.jpg", entries[0].name)
        assertEquals(2048L, entries[0].sizeBytes)
        assertEquals(1721039400000L, entries[0].modifiedEpochMs)
    }

    /** Servers disagree about namespace prefixes; a parser bound to one breaks on the rest. */
    @Test fun toleratesForeignNamespacePrefixes() {
        val xml = """<D:multistatus xmlns:D="DAV:" xmlns:lp1="DAV:">
            <D:response><D:href>/dav/x/</D:href>
              <D:propstat><D:prop><lp1:resourcetype><D:collection/></lp1:resourcetype></D:prop></D:propstat></D:response>
            <D:response><D:href>/dav/x/a.png</D:href>
              <D:propstat><D:prop><lp1:resourcetype/>
                <lp1:getcontentlength>7</lp1:getcontentlength></D:prop></D:propstat></D:response>
            </D:multistatus>"""
        val entries = WebDavApi.parsePropfind(xml, "/dav/x")!!
        assertEquals(1, entries.size)
        assertEquals("a.png", entries[0].name)
    }

    @Test fun nonMultistatusPayloadIsRejectedRatherThanTreatedAsEmpty() {
        assertNull(WebDavApi.parsePropfind("<html>Login</html>", "/x"))
        assertEquals(0, WebDavApi.parsePropfind("""<d:multistatus xmlns:d="DAV:"/>""", "/x")?.size)
    }

    @Test fun streamedParserYieldsEntriesAndCompletesValidRoot() {
        val responses = (1..2_000).joinToString("") { index ->
            "<d:response><d:href>/dav/x/$index.jpg</d:href><d:getcontentlength>$index</d:getcontentlength></d:response>"
        }
        val xml = "<d:multistatus xmlns:d=\"DAV:\">$responses</d:multistatus>"
        val listing = WebDavApi.parsePropfind(ByteArrayInputStream(xml.toByteArray()), "/dav/x")
        assertEquals(2_000, listing.entries.count())
        assertTrue(listing.isValid)
    }

    @Test fun streamedParserRejectsTruncatedAndOversizedResponses() {
        val truncated = WebDavApi.parsePropfind(
            ByteArrayInputStream("<d:multistatus><d:response><d:href>/x/a.jpg</d:href>".toByteArray()),
            "/x",
        )
        assertEquals(0, truncated.entries.count())
        assertFalse(truncated.isValid)

        val oversized = "<d:multistatus><d:response><d:href>/x/a.jpg</d:href>" +
            "x".repeat(1_024) + "</d:response></d:multistatus>"
        val listing = WebDavApi.parsePropfind(
            ByteArrayInputStream(oversized.toByteArray()),
            "/x",
            maximumResponseBytes = 256,
        )
        var rejected = false
        try {
            listing.entries.count()
        } catch (_: WebDavLimitException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(listing.isValid)
    }

    @Test fun streamedParserEnforcesWireLimit() {
        val xml = "<d:multistatus>" + " ".repeat(1_024) + "</d:multistatus>"
        val listing = WebDavApi.parsePropfind(
            ByteArrayInputStream(xml.toByteArray()),
            "/x",
            maximumWireBytes = 128,
        )
        var rejected = false
        try {
            listing.entries.count()
        } catch (_: WebDavLimitException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test fun distinguishesBadCredentialsFromInsufficientPermission() {
        assertEquals(SourceError.AuthFailed, WebDavApi.mapStatus(401))
        assertEquals(SourceError.PermissionDenied, WebDavApi.mapStatus(403))
        assertEquals(SourceError.ProtocolError, WebDavApi.mapStatus(405))
    }

    @Test fun redactsCredentialsButNotAtSignsInPaths() {
        assertEquals("https://***@h/dav", WebDavApi.redactUserInfo("https://u:p@h/dav"))
        assertEquals("https://h/dav/a@b.jpg", WebDavApi.redactUserInfo("https://h/dav/a@b.jpg"))
    }
}
