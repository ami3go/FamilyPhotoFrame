package com.example.familyphotoframe.data.diagnostics

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pure HMAC identity primitive installed from an app-private per-installation key. */
class DiagnosticIdentityHasher(private val key: ByteArray) {
    init { require(key.size >= 16) { "diagnostic identity key must be at least 128 bits" } }

    fun token(type: String, value: String): String {
        val prefix = type.lowercase().filter { it.isLetterOrDigit() }.take(20).ifEmpty { "id" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.copyOf(), "HmacSHA256"))
        val digest = mac.doFinal("$prefix\u0000$value".toByteArray(Charsets.UTF_8))
        return buildString(prefix.length + 1 + TOKEN_BYTES * 2) {
            append(prefix).append('_')
            for (index in 0 until TOKEN_BYTES) append("%02x".format(digest[index].toInt() and 0xff))
        }
    }

    companion object {
        private const val TOKEN_BYTES = 12
        private val processFallback by lazy {
            ByteArray(32).also(SecureRandom()::nextBytes).let(::DiagnosticIdentityHasher)
        }
        @Volatile private var installed: DiagnosticIdentityHasher? = null

        fun install(key: ByteArray) { installed = DiagnosticIdentityHasher(key.copyOf()) }
        fun current(): DiagnosticIdentityHasher = installed ?: processFallback
    }
}
