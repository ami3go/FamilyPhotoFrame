package com.example.familyphotoframe.data.source

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Trust-on-first-use certificate pinning for NAS sources (ROADMAP.md: "HTTPS +
 * certificate-trust choice"; `SourceError.CertUntrusted`).
 *
 * **Why this exists.** DSM ships a self-signed certificate by default, so a typical
 * Synology user fails the system trust check. Without this, their only workaround is
 * plain `http://`, which puts their NAS password in cleartext on the LAN — strictly
 * worse than a self-signed certificate they have explicitly verified.
 *
 * **What this is NOT.** This is deliberately not a "trust all certificates" switch, and
 * must never become one. [PinnedCertTrustManager] accepts a chain only if the platform
 * already trusts it *or* the leaf certificate matches the one exact SHA-256 fingerprint
 * the user personally approved for that host. An attacker substituting a different
 * certificate still fails, so this keeps MITM protection after the initial approval —
 * which blanket trust-all would throw away permanently.
 */
object CertPinning {

    /**
     * Renders a SHA-256 fingerprint as colon-separated uppercase hex pairs
     * (`AB:CD:...`) — the same presentation DSM, browsers, and `openssl` use, so a user
     * can compare what the frame shows against what their NAS admin page shows.
     */
    fun formatFingerprint(bytes: ByteArray): String =
        bytes.joinToString(":") { b ->
            val v = b.toInt() and 0xFF
            "${"0123456789ABCDEF"[v shr 4]}${"0123456789ABCDEF"[v and 0xF]}"
        }

    /** SHA-256 over a certificate's DER encoding, formatted by [formatFingerprint]. */
    fun fingerprintOf(cert: X509Certificate): String =
        formatFingerprint(MessageDigest.getInstance("SHA-256").digest(cert.encoded))

    /**
     * Normalizes a fingerprint for storage/comparison: uppercase hex only, separators
     * dropped. Lets a value pasted from DSM (colons), from a browser (spaces), or from
     * our own UI compare equal without the caller worrying about formatting.
     */
    fun normalize(fingerprint: String?): String =
        fingerprint?.uppercase()?.filter { it in "0123456789ABCDEF" }.orEmpty()

    /**
     * Constant-time-ish equality on normalized fingerprints. Length is compared first
     * and the loop always runs the full length, so this does not leak the position of
     * the first differing character through timing.
     */
    fun matches(expected: String?, actual: String?): Boolean {
        val a = normalize(expected)
        val b = normalize(actual)
        // A blank pin must never match anything — otherwise "not configured" would read
        // as "trust whatever turns up".
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    /** True when the fingerprint is a complete, well-formed SHA-256 value (32 bytes). */
    fun isValidSha256(fingerprint: String?): Boolean = normalize(fingerprint).length == 64

    /** The platform's default trust manager, used as the first check before any pin. */
    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * An [SSLSocketFactory] that trusts the platform CAs plus, optionally, one pinned
     * leaf certificate. Passing a null/blank [pinnedSha256] yields ordinary platform
     * validation with no relaxation at all.
     */
    fun socketFactory(pinnedSha256: String?): SSLSocketFactory {
        val delegate = systemTrustManager()
        val tm: TrustManager = PinnedCertTrustManager(delegate, pinnedSha256)
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory
    }

    /**
     * Captures the leaf certificate presented by a host **without trusting it**, so the
     * UI can show the user a fingerprint to approve. Every chain is rejected; the
     * certificate is recorded on the way past.
     */
    class CapturingTrustManager : X509TrustManager {
        @Volatile var captured: X509Certificate? = null
            private set

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("client certs not supported")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            captured = chain?.firstOrNull()
            // Always reject: this exists to *inspect*, never to authorise.
            throw CertificateException("capture only")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

/**
 * Accepts a chain the platform already trusts, or one whose leaf matches exactly one
 * user-approved SHA-256 fingerprint. See [CertPinning] for the rationale and for why
 * this must not be widened into a trust-all implementation.
 */
class PinnedCertTrustManager(
    private val delegate: X509TrustManager,
    private val pinnedSha256: String?,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            delegate.checkServerTrusted(chain, authType)
            return // normal CA-signed certificate: nothing further to do
        } catch (e: CertificateException) {
            val leaf = chain?.firstOrNull() ?: throw e
            if (!CertPinning.matches(pinnedSha256, CertPinning.fingerprintOf(leaf))) throw e
            // Pinned certificate still has to be currently valid: an approved-once
            // certificate that has since expired should fail like any other.
            leaf.checkValidity()
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
