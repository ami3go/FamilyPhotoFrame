package com.example.familyphotoframe.data.source

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SourceErrorMapperTest {

    @Test fun networkTypes_mapByException() {
        assertEquals(SourceError.HostUnreachable, SourceErrorMapper.map(UnknownHostException("nas.local")))
        assertEquals(SourceError.Timeout, SourceErrorMapper.map(SocketTimeoutException("read timed out")))
        assertEquals(SourceError.HostUnreachable, SourceErrorMapper.map(ConnectException("refused")))
    }

    @Test fun wrappedCause_isInspected() {
        val wrapped = IOException("smb failure", UnknownHostException("nas"))
        assertEquals(SourceError.HostUnreachable, SourceErrorMapper.map(wrapped))
    }

    @Test fun deeplyWrappedTransportCause_isInspected() {
        val connect = ConnectException("connection refused")
        val transport = IOException("transport failed", connect)
        val protocol = IOException("jcifs request failed", transport)
        val outer = IllegalStateException("provider error", protocol)
        assertEquals(SourceError.HostUnreachable, SourceErrorMapper.map(outer))
        assertEquals(SourceHealth.Unavailable, SourceErrorMapper.toHealth(outer))
    }

    @Test fun deeplyWrappedTimeout_isUnavailable() {
        val timeout = SocketTimeoutException("read timed out")
        val outer = IOException("request failed", IOException("transport", timeout))
        assertEquals(SourceError.Timeout, SourceErrorMapper.map(outer))
        assertEquals(SourceHealth.Unavailable, SourceErrorMapper.toHealth(outer))
    }

    @Test fun ntStatusMessages_mapByText() {
        assertEquals(SourceError.AuthFailed, SourceErrorMapper.map(RuntimeException("Logon failure: unknown user or bad password")))
        assertEquals(SourceError.ShareNotFound, SourceErrorMapper.map(RuntimeException("The specified network name cannot be found")))
        assertEquals(SourceError.PermissionDenied, SourceErrorMapper.map(RuntimeException("Access is denied.")))
        assertEquals(SourceError.FileGone, SourceErrorMapper.map(RuntimeException("no such file")))
    }

    /**
     * Servers phrase "missing" several ways. A missing *share* is a source-level
     * failure (drives fallback/recovery); a missing *file* is per-photo. Regression
     * test: "cannot be found" once fell through to FileGone.
     */
    @Test fun shareLevelMisses_outrankFileLevelMisses() {
        assertEquals(SourceError.ShareNotFound, SourceErrorMapper.map(RuntimeException("The specified network name cannot be found")))
        assertEquals(SourceError.ShareNotFound, SourceErrorMapper.map(RuntimeException("The specified network name cannot find")))
        assertEquals(SourceError.ShareNotFound, SourceErrorMapper.map(RuntimeException("Share name not found on server")))
        assertEquals(SourceError.ShareNotFound, SourceErrorMapper.map(RuntimeException("Bad network name")))
        // No share/network wording -> per-file classification.
        assertEquals(SourceError.FileGone, SourceErrorMapper.map(RuntimeException("File not found")))
        assertEquals(SourceError.FileGone, SourceErrorMapper.map(RuntimeException("photo.jpg cannot be found")))
    }

    @Test fun shareNotFound_reportsSourceMissingHealth() {
        assertEquals(SourceHealth.Missing, SourceErrorMapper.toHealth(RuntimeException("The specified network name cannot be found")))
    }

    @Test fun unknown_isFallback() {
        assertEquals(SourceError.Unknown, SourceErrorMapper.map(RuntimeException("something odd")))
    }

    @Test fun toHealth_classifies() {
        assertEquals(SourceHealth.Unavailable, SourceErrorMapper.toHealth(SocketTimeoutException("t")))
        assertEquals(SourceHealth.NeedsPermission, SourceErrorMapper.toHealth(RuntimeException("Access is denied.")))
        assertEquals(SourceHealth.Missing, SourceErrorMapper.toHealth(RuntimeException("bad network name")))
    }
}
