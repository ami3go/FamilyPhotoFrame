// KeyPairGeneratorSpec is deprecated but is the ONLY way to create an AndroidKeyStore
// key on API 21-22, which this build supports (see BUILD_NOTES.md "minSdk 21").
// The modern KeyGenParameterSpec path is used on API 23+; see sealModern/modernKey.
@file:Suppress("DEPRECATION")

package com.example.familyphotoframe.data.secret

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.example.familyphotoframe.data.db.SecretDao
import com.example.familyphotoframe.data.db.SecretEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/**
 * Android Keystore-backed secret storage (spec §14.2–14.3, Contract Rule 5).
 *
 * The secret is always stored as AES-256-GCM ciphertext; only *where the AES key
 * lives* differs by API level, because AndroidKeyStore did not support symmetric
 * keys before Android 6:
 *
 *  - **API 23+ (preferred):** the AES key is generated inside AndroidKeyStore and is
 *    non-exportable. Nothing but ciphertext + IV is persisted.
 *  - **API 21–22 (fallback):** AndroidKeyStore only supports RSA there, so a random
 *    AES key is generated per secret, used to encrypt the plaintext, and then
 *    *wrapped* with a non-exportable RSA key held in AndroidKeyStore. The wrapped
 *    key is persisted alongside the ciphertext; the bare AES key never touches disk.
 *
 * In both modes the plaintext is never persisted or logged and exists only
 * transiently in memory during [reveal]. The stored [SecretEntity.wrappedKey] is what
 * selects the unwrap path on read, so secrets written on one Android version keep
 * working if the database is restored on another.
 */
class KeystoreSecretStore(
    private val appContext: Context,
    private val secretDao: SecretDao,
    private val io: CoroutineDispatcher,
) {

    /** Encrypt and persist a secret under [ref]. Overwrites any existing value. */
    suspend fun store(ref: String, type: String, plaintext: String) = withContext(io) {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        val existing = secretDao.get(ref)
        val now = System.currentTimeMillis()

        // Explicit SDK_INT comparison (not a helper property) so lint can verify the
        // API 23+ calls below are guarded.
        val sealed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sealModern(bytes)
        else sealLegacy(bytes)

        secretDao.put(
            SecretEntity(
                credentialRef = ref,
                type = type,
                encryptedSecretBlob = sealed.ciphertext,
                iv = sealed.iv,
                wrappedKey = sealed.wrappedKey,
                createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
                securityLevel = sealed.securityLevel,
            )
        )
    }

    /**
     * Decrypt the secret for [ref], or null if none exists or the key can no longer
     * decrypt it (e.g. after device restore — callers treat this as
     * `needs_credential`, per spec §18.3, not a fatal error).
     */
    suspend fun reveal(ref: String): String? = withContext(io) {
        val row = secretDao.get(ref) ?: return@withContext null
        runCatching {
            val wrapped = row.wrappedKey
            val key: SecretKey = when {
                wrapped != null -> unwrapAesKey(wrapped)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> modernKey()
                // Written on a newer device and restored here: cannot decrypt, so the
                // caller re-prompts for the credential (spec §18.3).
                else -> return@runCatching null
            }
            val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, row.iv))
            }
            String(cipher.doFinal(row.encryptedSecretBlob), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun forget(ref: String) = withContext(io) { secretDao.delete(ref) }

    // ---- sealing ----

    private class Sealed(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val wrappedKey: ByteArray?,
        val securityLevel: String,
    )

    /** API 23+: AES key lives in AndroidKeyStore; no wrapped key is stored. */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun sealModern(plaintext: ByteArray): Sealed {
        val key = modernKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        return Sealed(cipher.doFinal(plaintext), cipher.iv, null, modernSecurityLevel(key))
    }

    /** API 21–22: random AES key, wrapped by a keystore-held RSA key. */
    private fun sealLegacy(plaintext: ByteArray): Sealed {
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, aes) }
        val ciphertext = cipher.doFinal(plaintext)
        val wrapped = wrapAesKey(aes)
        // There is no API to query hardware backing before API 23.
        return Sealed(ciphertext, cipher.iv, wrapped, "unknown")
    }

    // ---- key management ----

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** API 23+ symmetric key, created on first use. */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun modernKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Not tied to user auth: the frame runs unattended (spec §1).
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    /** API 21–22 RSA wrapping key, created on first use. */
    @Suppress("DEPRECATION")
    private fun legacyKeyPair(): KeyPair {
        val ks = keyStore()
        val entry = ks.getEntry(LEGACY_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (entry != null) return KeyPair(entry.certificate.publicKey, entry.privateKey)

        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = KeyPairGeneratorSpec.Builder(appContext)
            .setAlias(LEGACY_KEY_ALIAS)
            .setSubject(X500Principal("CN=$LEGACY_KEY_ALIAS"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(start.time)
            .setEndDate(end.time)
            .build()
        val generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private fun wrapAesKey(aes: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, legacyKeyPair().public)
        return cipher.doFinal(aes.encoded)
    }

    private fun unwrapAesKey(wrapped: ByteArray): SecretKey {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, legacyKeyPair().private)
        return SecretKeySpec(cipher.doFinal(wrapped), "AES")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun modernSecurityLevel(key: SecretKey): String = runCatching {
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        @Suppress("DEPRECATION")
        if (info.isInsideSecureHardware) "hardware_backed" else "software_keystore"
    }.getOrDefault("unknown")

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "frame_secret_key_v1"
        private const val LEGACY_KEY_ALIAS = "frame_secret_wrap_rsa_v1"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val GCM_TAG_BITS = 128
    }
}
