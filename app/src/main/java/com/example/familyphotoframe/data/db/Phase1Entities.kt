package com.example.familyphotoframe.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted source configuration (spec §6.1). Common fields for every source; the
 * type-specific details live in [SmbSourceConfigEntity] / [LocalSafSourceConfigEntity].
 * [credentialRef] points at a [SecretEntity]; it never contains a password.
 */
@Entity(tableName = "source_config")
data class SourceConfigEntity(
    @PrimaryKey val id: String,
    val type: String,                 // local_saf | smb | app_private
    val displayName: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val role: String,                 // primary | fallback
    val credentialRef: String? = null,
    @ColumnInfo(defaultValue = "1") val includeSubfolders: Boolean = true,
    val includeGlobsCsv: String = "*.jpg,*.jpeg,*.png,*.webp,*.heic,*.heif",
    val excludeGlobsCsv: String = ".*,Thumbs.db,@eaDir/**",
    val priority: Int = 0,
)

/** SMB-specific config (spec §6.1). Password is NOT here; see [credentialRef]. */
@Entity(tableName = "smb_source_config")
data class SmbSourceConfigEntity(
    @PrimaryKey val sourceId: String,
    val host: String,
    val share: String,
    val path: String,
    val user: String,
    val domain: String,
    @ColumnInfo(defaultValue = "5000") val connectionTimeoutMs: Long = 5_000,
    @ColumnInfo(defaultValue = "15000") val readTimeoutMs: Long = 15_000,
    @ColumnInfo(defaultValue = "15000") val listTimeoutMs: Long = 15_000,
)

/** Local SAF config (spec §6.1). Stores the persisted tree URI, never a raw path. */
@Entity(tableName = "local_saf_source_config")
data class LocalSafSourceConfigEntity(
    @PrimaryKey val sourceId: String,
    val treeUri: String,
    val permissionState: String,      // ok | needs_permission | revoked | unavailable
)

/**
 * Encrypted secret (spec §6.1, §14). The blob is AES-GCM ciphertext produced by an
 * Android-Keystore key; [iv] is the GCM nonce. The plaintext secret is never stored
 * or logged (Contract Rule 5).
 */
@Entity(tableName = "secrets")
data class SecretEntity(
    @PrimaryKey val credentialRef: String,
    val type: String,                 // smb_password | web_pairing_secret | ...
    val encryptedSecretBlob: ByteArray,
    val iv: ByteArray,
    /**
     * AES key wrapped by a keystore RSA key — only used on API 21–22, where
     * AndroidKeyStore cannot hold symmetric keys. Null means the key lives in the
     * keystore itself (API 23+). See KeystoreSecretStore.
     */
    val wrappedKey: ByteArray? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val securityLevel: String,        // hardware_backed | software_keystore | unknown
) {
    // ByteArray needs explicit equals/hashCode for data-class correctness.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretEntity) return false
        return credentialRef == other.credentialRef &&
            type == other.type &&
            encryptedSecretBlob.contentEquals(other.encryptedSecretBlob) &&
            iv.contentEquals(other.iv) &&
            (wrappedKey?.contentEquals(other.wrappedKey ?: ByteArray(0)) ?: (other.wrappedKey == null)) &&
            createdAtEpochMs == other.createdAtEpochMs &&
            updatedAtEpochMs == other.updatedAtEpochMs &&
            securityLevel == other.securityLevel
    }

    override fun hashCode(): Int {
        var result = credentialRef.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + encryptedSecretBlob.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + (wrappedKey?.contentHashCode() ?: 0)
        result = 31 * result + createdAtEpochMs.hashCode()
        result = 31 * result + updatedAtEpochMs.hashCode()
        result = 31 * result + securityLevel.hashCode()
        return result
    }
}

/**
 * Cache index for remote (SMB) bytes (spec §6.1, §16). Exactly one owner of full
 * remote bytes — this app-owned cache — per Contract Rule 19. [verifiedDecodeOk]
 * distinguishes "file exists" from "decodes successfully" (spec §16.1).
 */
@Entity(
    tableName = "cache_index",
    indices = [Index(value = ["photoStableId"])],
)
data class CacheIndexEntity(
    @PrimaryKey val cacheKey: String,
    val photoStableId: String,
    val localFilePathPrivate: String,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
    val lastAccessedAtEpochMs: Long,
    @ColumnInfo(defaultValue = "0") val verifiedDecodeOk: Boolean = false,
)
