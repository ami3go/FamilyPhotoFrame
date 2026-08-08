package com.example.familyphotoframe.data.source

import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps jcifs-ng / network exceptions to the typed [SourceError] taxonomy (spec §8.2).
 *
 * Classification prefers exception *type* (stable across jcifs versions) and falls
 * back to message inspection for NT-status cases, rather than referencing jcifs
 * NtStatus constants directly — that keeps the mapping robust if the library's
 * internal constant names change between versions.
 */
object SourceErrorMapper {

    fun map(t: Throwable): SourceError {
        val msg = (t.message ?: "").lowercase()
        val cause = t.cause
        // Servers phrase this several ways ("cannot be found", "cannot find",
        // "not found"); classify share-level misses before per-file ones.
        val notFound = "not found" in msg || "cannot find" in msg || "cannot be found" in msg
        val shareLevel = "network name" in msg || "share name" in msg
        return when {
            t is SmbAuthException -> SourceError.AuthFailed
            t is UnknownHostException || cause is UnknownHostException -> SourceError.HostUnreachable
            t is NoRouteToHostException || cause is NoRouteToHostException -> SourceError.HostUnreachable
            t is ConnectException || cause is ConnectException -> SourceError.HostUnreachable
            t is SocketTimeoutException || cause is SocketTimeoutException -> SourceError.Timeout
            "logon failure" in msg || "login failure" in msg -> SourceError.AuthFailed
            "bad network name" in msg || (shareLevel && notFound) -> SourceError.ShareNotFound
            "access is denied" in msg || "access denied" in msg -> SourceError.PermissionDenied
            "timed out" in msg || "timeout" in msg -> SourceError.Timeout
            "unreachable" in msg -> SourceError.HostUnreachable
            notFound || "no such file" in msg -> SourceError.FileGone
            t is SmbException -> SourceError.ProtocolError
            else -> SourceError.Unknown
        }
    }

    /** Health classification for a failed [healthCheck] (spec §7.11 / §9). */
    fun toHealth(t: Throwable): SourceHealth = when (map(t)) {
        SourceError.AuthFailed, SourceError.PermissionDenied -> SourceHealth.NeedsPermission
        SourceError.ShareNotFound -> SourceHealth.Missing
        SourceError.HostUnreachable, SourceError.Timeout -> SourceHealth.Unavailable
        else -> SourceHealth.ProviderError(map(t).name)
    }
}
