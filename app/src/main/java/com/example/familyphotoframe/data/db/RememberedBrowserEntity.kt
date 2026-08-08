package com.example.familyphotoframe.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent authorization record for one remembered browser profile.
 *
 * Only HMAC token hashes are persisted. The raw browser credential, pairing PIN,
 * active session token and CSRF token are never stored in Room.
 */
@Entity(tableName = "remembered_browsers")
data class RememberedBrowserEntity(
    @PrimaryKey val id: String,
    val currentTokenHash: ByteArray,
    val previousTokenHash: ByteArray? = null,
    val previousTokenValidUntilEpochMs: Long? = null,
    /** One additional retired hash lets us detect replay after a two-tab rotation race. */
    val retiredTokenHash: ByteArray? = null,
    val label: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val revokedAtEpochMs: Long? = null,
    val browserSummary: String? = null,
    val osSummary: String? = null,
    val lastTrustedWallClockEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RememberedBrowserEntity) return false
        return id == other.id &&
            currentTokenHash.contentEquals(other.currentTokenHash) &&
            nullableBytesEqual(previousTokenHash, other.previousTokenHash) &&
            previousTokenValidUntilEpochMs == other.previousTokenValidUntilEpochMs &&
            nullableBytesEqual(retiredTokenHash, other.retiredTokenHash) &&
            label == other.label && createdAtEpochMs == other.createdAtEpochMs &&
            lastUsedAtEpochMs == other.lastUsedAtEpochMs &&
            expiresAtEpochMs == other.expiresAtEpochMs &&
            revokedAtEpochMs == other.revokedAtEpochMs &&
            browserSummary == other.browserSummary && osSummary == other.osSummary &&
            lastTrustedWallClockEpochMs == other.lastTrustedWallClockEpochMs
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + currentTokenHash.contentHashCode()
        result = 31 * result + (previousTokenHash?.contentHashCode() ?: 0)
        result = 31 * result + (previousTokenValidUntilEpochMs?.hashCode() ?: 0)
        result = 31 * result + (retiredTokenHash?.contentHashCode() ?: 0)
        result = 31 * result + label.hashCode()
        result = 31 * result + createdAtEpochMs.hashCode()
        result = 31 * result + lastUsedAtEpochMs.hashCode()
        result = 31 * result + (expiresAtEpochMs?.hashCode() ?: 0)
        result = 31 * result + (revokedAtEpochMs?.hashCode() ?: 0)
        result = 31 * result + (browserSummary?.hashCode() ?: 0)
        result = 31 * result + (osSummary?.hashCode() ?: 0)
        result = 31 * result + lastTrustedWallClockEpochMs.hashCode()
        return result
    }

    private fun nullableBytesEqual(a: ByteArray?, b: ByteArray?): Boolean = when {
        a == null -> b == null
        b == null -> false
        else -> a.contentEquals(b)
    }
}
