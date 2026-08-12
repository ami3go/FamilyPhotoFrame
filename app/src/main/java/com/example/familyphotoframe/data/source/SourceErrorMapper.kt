package com.example.familyphotoframe.data.source

import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Maps jcifs-ng / network exceptions to the typed [SourceError] taxonomy (spec §8.2).
 *
 * Classification prefers exception *type* (stable across jcifs versions) and falls
 * back to message inspection for NT-status cases, rather than referencing jcifs
 * NtStatus constants directly — that keeps the mapping robust if the library's
 * internal constant names change between versions.
 */
object SourceErrorMapper {

    /**
     * jcifs transport exceptions are commonly wrapped by two or more layers. Keep this
     * bounded and identity-safe so a malformed causal cycle can never hang diagnostics.
     */
    private fun causeChain(t: Throwable): List<Throwable> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val result = ArrayList<Throwable>(4)
        var current: Throwable? = t
        while (current != null && result.size < 8 && seen.add(current)) {
            result += current
            current = current.cause
        }
        return result
    }

    fun map(t: Throwable): SourceError {
        val chain = causeChain(t)
        val msg = chain.joinToString(" | ") { it.message.orEmpty() }.lowercase()
        // Servers phrase this several ways ("cannot be found", "cannot find",
        // "not found"); classify share-level misses before per-file ones.
        val notFound = "not found" in msg || "cannot find" in msg || "cannot be found" in msg
        val shareLevel = "network name" in msg || "share name" in msg
        return when {
            chain.any { it is SmbAuthException } -> SourceError.AuthFailed
            chain.any { it is UnknownHostException || it is NoRouteToHostException || it is ConnectException } ->
                SourceError.HostUnreachable
            chain.any { it is SocketTimeoutException } -> SourceError.Timeout
            "logon failure" in msg || "login failure" in msg -> SourceError.AuthFailed
            "bad network name" in msg || (shareLevel && notFound) -> SourceError.ShareNotFound
            "access is denied" in msg || "access denied" in msg -> SourceError.PermissionDenied
            "timed out" in msg || "timeout" in msg -> SourceError.Timeout
            "unreachable" in msg || "connection reset" in msg || "connection refused" in msg ->
                SourceError.HostUnreachable
            notFound || "no such file" in msg -> SourceError.FileGone
            chain.any { it is SmbException } -> SourceError.ProtocolError
            else -> SourceError.Unknown
        }
    }

    /** Health classification for a failed [healthCheck] (spec §7.11 / §9). */
    fun toHealth(t: Throwable): SourceHealth {
        val error = map(t)
        return when (error) {
            SourceError.AuthFailed, SourceError.PermissionDenied -> SourceHealth.NeedsPermission
            SourceError.ShareNotFound -> SourceHealth.Missing
            SourceError.HostUnreachable, SourceError.Timeout -> SourceHealth.Unavailable
            else -> SourceHealth.ProviderError(error.name)
        }
    }
}
