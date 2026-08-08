package com.example.familyphotoframe.data.diagnostics

import android.content.Context
import java.security.SecureRandom

/** App-private, non-backed-up installation key for diagnostics identity HMACs. */
class DiagnosticIdentityKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun loadOrCreate(): ByteArray {
        preferences.getString(KEY_MATERIAL, null)?.let(::decodeHex)?.let { existing ->
            if (existing.size == KEY_BYTES) return existing
        }
        val generated = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        // Return the generated key even if storage is temporarily unavailable. Tokens
        // remain safe for this process and persistence is retried on the next startup.
        preferences.edit().putString(KEY_MATERIAL, generated.toHex()).commit()
        return generated
    }

    /**
     * Replace the installation key with a fresh one, so every pseudonymized diagnostic
     * token (folder names, hostnames, paths, ...) computed after this call is unlinkable
     * from tokens computed before it. Callers must also re-install the new key into
     * [DiagnosticIdentityHasher] for it to take effect without a process restart.
     */
    @Synchronized
    fun rotate(): ByteArray {
        val generated = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        preferences.edit().putString(KEY_MATERIAL, generated.toHex()).commit()
        return generated
    }

    private fun decodeHex(value: String): ByteArray? {
        if (value.length != KEY_BYTES * 2 || value.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(KEY_BYTES) { index ->
            ((value[index * 2].digitToInt(16) shl 4) or value[index * 2 + 1].digitToInt(16)).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val PREFERENCES_NAME = "diagnostics_identity"
        private const val KEY_MATERIAL = "hmac_key_v1"
        private const val KEY_BYTES = 32
    }
}
