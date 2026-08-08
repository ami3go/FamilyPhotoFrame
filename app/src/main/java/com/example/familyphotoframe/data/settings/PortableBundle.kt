package com.example.familyphotoframe.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted portable bundle (spec §14.4 option 2).
 *
 * The plain [ConfigTransfer] export deliberately strips every credential, which makes
 * moving a configured frame to new hardware tedious. This bundle is the sanctioned way
 * to carry secrets between devices: the payload — settings plus NAS credentials and
 * the optional weather API key read back from the Keystore — is encrypted with
 * AES-256-GCM under a key derived from
 * a passphrase the user types. Without the passphrase the file is inert.
 *
 * The envelope stores its KDF and cipher metadata (algorithm, salt, iterations, nonce,
 * tag length) as spec §14.4 requires, so a future build can raise the parameters and
 * still read old bundles.
 *
 * **Algorithm choice.** `PBKDF2WithHmacSHA1` and a hand-rolled Base64 are used because
 * `PBKDF2WithHmacSHA256` and `java.util.Base64` are both API 26+, while this app
 * supports API 21 (see BUILD_NOTES.md). A bundle must import on the *older* device too,
 * so the lowest common denominator wins; the recorded metadata is what allows an
 * upgrade later without breaking existing files.
 */
object PortableBundle {

    const val KIND = "family_photo_frame_portable"
    const val BUNDLE_VERSION = 3

    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA1"
    const val KDF_ITERATIONS = 120_000
    const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    internal const val MIN_KDF_ITERATIONS = 100_000
    internal const val MAX_KDF_ITERATIONS = 600_000
    // Leave room for Base64 expansion and envelope metadata inside the 4 MiB file limit.
    internal const val MAX_CIPHERTEXT_BYTES = 3 * 1024 * 1024 - 64 * 1024
    internal const val MAX_ENVELOPE_CHARS = BoundedTextInput.MAX_IMPORT_BYTES
    private const val MAX_CIPHERTEXT_BASE64_CHARS = ((MAX_CIPHERTEXT_BYTES + 2) / 3) * 4

    /**
     * Decrypted contents. Nullable defaults keep version-1 bundles readable: fields
     * added in version 2 simply deserialize as absent.
     */
    @Serializable
    data class Payload(
        val settings: AppSettings = AppSettings(),
        val smbPassword: String? = null,
        val synologyPassword: String? = null,
        /** Added in bundle version 3; absent in older bundles, hence nullable. */
        val webDavPassword: String? = null,
        val weatherApiKey: String? = null,
    )

    @Serializable
    data class Kdf(
        val algorithm: String = KDF_ALGORITHM,
        val salt: String = "",
        val iterations: Int = KDF_ITERATIONS,
        val keyBits: Int = KEY_BITS,
    )

    @Serializable
    data class CipherMeta(
        val algorithm: String = CIPHER_ALGORITHM,
        val nonce: String = "",
        val tagBits: Int = GCM_TAG_BITS,
    )

    @Serializable
    data class Envelope(
        val kind: String = KIND,
        val bundleVersion: Int = BUNDLE_VERSION,
        val exportedAtEpochMs: Long = 0L,
        val kdf: Kdf = Kdf(),
        val cipher: CipherMeta = CipherMeta(),
        val ciphertext: String = "",
    )

    sealed interface OpenResult {
        data class Ok(val payload: Payload) : OpenResult
        data class Failed(val reason: Reason) : OpenResult
        enum class Reason {
            NOT_A_BUNDLE,
            TOO_NEW,
            WRONG_PASSPHRASE_OR_CORRUPT,
            UNSUPPORTED_ALGORITHM,
            UNSAFE_PARAMETERS,
            TOO_LARGE,
            EMPTY_PASSPHRASE,
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Encrypt [payload] under [passphrase]. Returns the envelope JSON, or null if the passphrase is blank. */
    fun seal(payload: Payload, passphrase: String, nowEpochMs: Long, random: SecureRandom = SecureRandom()): String? {
        if (passphrase.isBlank()) return null
        val plaintext = json.encodeToString(Payload.serializer(), payload).toByteArray(Charsets.UTF_8)
        if (plaintext.size > MAX_CIPHERTEXT_BYTES - GCM_TAG_BITS / 8) return null
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt, KDF_ITERATIONS, KEY_BITS, KDF_ALGORITHM)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
        val ct = cipher.doFinal(plaintext)

        val envelope = Envelope(
            exportedAtEpochMs = nowEpochMs,
            kdf = Kdf(salt = Base64.encode(salt)),
            cipher = CipherMeta(nonce = Base64.encode(nonce)),
            ciphertext = Base64.encode(ct),
        )
        return json.encodeToString(Envelope.serializer(), envelope)
    }

    /**
     * Decrypt an envelope. A wrong passphrase and a tampered file are deliberately
     * reported as the *same* reason: GCM authentication fails identically for both, and
     * distinguishing them would leak whether the passphrase was correct.
     */
    fun open(text: String, passphrase: String): OpenResult {
        if (passphrase.isBlank()) return OpenResult.Failed(OpenResult.Reason.EMPTY_PASSPHRASE)
        if (text.length > MAX_ENVELOPE_CHARS) return OpenResult.Failed(OpenResult.Reason.TOO_LARGE)
        val envelope = runCatching { json.decodeFromString(Envelope.serializer(), text) }
            .getOrElse { return OpenResult.Failed(OpenResult.Reason.NOT_A_BUNDLE) }

        if (envelope.kind != KIND) return OpenResult.Failed(OpenResult.Reason.NOT_A_BUNDLE)
        if (envelope.bundleVersion > BUNDLE_VERSION) return OpenResult.Failed(OpenResult.Reason.TOO_NEW)
        if (envelope.kdf.algorithm != KDF_ALGORITHM || envelope.cipher.algorithm != CIPHER_ALGORITHM) {
            return OpenResult.Failed(OpenResult.Reason.UNSUPPORTED_ALGORITHM)
        }

        if (envelope.kdf.iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS ||
            envelope.kdf.keyBits != KEY_BITS ||
            envelope.cipher.tagBits != GCM_TAG_BITS ||
            envelope.kdf.salt.length > 64 ||
            envelope.cipher.nonce.length > 64 ||
            envelope.ciphertext.isBlank() ||
            envelope.ciphertext.length > MAX_CIPHERTEXT_BASE64_CHARS
        ) {
            return OpenResult.Failed(OpenResult.Reason.UNSAFE_PARAMETERS)
        }

        val decoded = runCatching {
            Triple(
                Base64.decode(envelope.kdf.salt),
                Base64.decode(envelope.cipher.nonce),
                Base64.decode(envelope.ciphertext),
            )
        }.getOrElse { return OpenResult.Failed(OpenResult.Reason.WRONG_PASSPHRASE_OR_CORRUPT) }
        val (salt, nonce, ct) = decoded
        if (salt.size != SALT_BYTES || nonce.size != NONCE_BYTES ||
            ct.size !in (GCM_TAG_BITS / 8)..MAX_CIPHERTEXT_BYTES
        ) {
            return OpenResult.Failed(OpenResult.Reason.UNSAFE_PARAMETERS)
        }

        return runCatching {
            val key = deriveKey(
                passphrase, salt, envelope.kdf.iterations, envelope.kdf.keyBits, envelope.kdf.algorithm,
            )
            val cipher = Cipher.getInstance(envelope.cipher.algorithm).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(envelope.cipher.tagBits, nonce))
            }
            val plaintext = String(cipher.doFinal(ct), Charsets.UTF_8)
            OpenResult.Ok(json.decodeFromString(Payload.serializer(), plaintext))
        }.getOrElse { OpenResult.Failed(OpenResult.Reason.WRONG_PASSPHRASE_OR_CORRUPT) }
    }

    /** Suggested filename, e.g. `photo-frame-backup-20260724.fpfbundle`. */
    fun suggestedFileName(dateStamp: String) = "photo-frame-backup-$dateStamp.fpfbundle"

    private fun deriveKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        keyBits: Int,
        algorithm: String,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyBits)
        return try {
            val bytes = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Minimal Base64. `java.util.Base64` is API 26+ and `android.util.Base64` is not
     * available to JVM unit tests, so this keeps the format both API-21-safe and
     * directly testable.
     */
    internal object Base64 {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        fun encode(bytes: ByteArray): String {
            val out = StringBuilder((bytes.size + 2) / 3 * 4)
            var i = 0
            while (i + 2 < bytes.size) {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                    (bytes[i + 2].toInt() and 0xFF)
                out.append(ALPHABET[(n ushr 18) and 63]).append(ALPHABET[(n ushr 12) and 63])
                    .append(ALPHABET[(n ushr 6) and 63]).append(ALPHABET[n and 63])
                i += 3
            }
            when (bytes.size - i) {
                1 -> {
                    val n = (bytes[i].toInt() and 0xFF) shl 16
                    out.append(ALPHABET[(n ushr 18) and 63]).append(ALPHABET[(n ushr 12) and 63]).append("==")
                }
                2 -> {
                    val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                    out.append(ALPHABET[(n ushr 18) and 63]).append(ALPHABET[(n ushr 12) and 63])
                        .append(ALPHABET[(n ushr 6) and 63]).append('=')
                }
            }
            return out.toString()
        }

        fun decode(text: String): ByteArray {
            val clean = text.filter { !it.isWhitespace() && it != '=' }
            val out = ByteArray(clean.length * 3 / 4)
            var buffer = 0
            var bits = 0
            var index = 0
            for (ch in clean) {
                val value = ALPHABET.indexOf(ch)
                require(value >= 0) { "invalid base64 character" }
                buffer = (buffer shl 6) or value
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out[index++] = ((buffer ushr bits) and 0xFF).toByte()
                }
            }
            return if (index == out.size) out else out.copyOf(index)
        }
    }
}
