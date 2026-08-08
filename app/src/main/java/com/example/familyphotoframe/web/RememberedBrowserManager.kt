package com.example.familyphotoframe.web

import android.util.Base64
import com.example.familyphotoframe.data.db.RememberedBrowserDao
import com.example.familyphotoframe.data.db.RememberedBrowserEntity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.secret.KeystoreSecretStore
import com.example.familyphotoframe.data.settings.CustomExpiryUnit
import com.example.familyphotoframe.data.settings.RememberExpiryMode
import com.example.familyphotoframe.data.settings.RememberedBrowserPolicy
import com.example.familyphotoframe.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Persistent, revocable trust layer used only to mint normal short-lived web sessions. */
class RememberedBrowserManager(
    private val dao: RememberedBrowserDao,
    private val secretStore: KeystoreSecretStore,
    private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsLog,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class CreateRequest(
        val mode: RememberExpiryMode,
        val amount: Int? = null,
        val unit: CustomExpiryUnit? = null,
        val confirmForever: Boolean = false,
        val label: String = "",
        val browserSummary: String? = null,
        val osSummary: String? = null,
    )

    data class Created(
        val id: String,
        val credential: String,
        val expiresAtEpochMs: Long?,
        val label: String,
    )

    sealed interface ExchangeResult {
        data class Ok(
            val id: String,
            val rotatedCredential: String,
            val expiresAtEpochMs: Long?,
        ) : ExchangeResult
        data class Rejected(val browserId: String? = null, val revokeSessions: Boolean = false) : ExchangeResult
        data class ClockRollback(val browserId: String) : ExchangeResult
        data object KeyUnavailable : ExchangeResult
    }

    data class BrowserView(
        val id: String,
        val label: String,
        val createdAtEpochMs: Long,
        val lastUsedAtEpochMs: Long,
        val expiresAtEpochMs: Long?,
        val revokedAtEpochMs: Long?,
        val browserSummary: String?,
        val osSummary: String?,
    )

    private val mutex = Mutex()

    suspend fun policy(): RememberedBrowserPolicy =
        settings.settings.first().web.rememberedBrowsers.normalized()

    suspend fun updatePolicy(policy: RememberedBrowserPolicy) {
        val normalized = policy.normalized()
        settings.update { current ->
            current.copy(web = current.web.copy(rememberedBrowsers = normalized))
        }
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "REMEMBERED_BROWSER_POLICY_CHANGED",
            "enabled" to normalized.enabled.toString(),
            "allowForever" to normalized.allowForever.toString(),
            "maxBrowsers" to normalized.maxRememberedBrowsers.toString(),
        )
    }

    suspend fun create(request: CreateRequest): Created = mutex.withLock {
        require(request.mode != RememberExpiryMode.SESSION_ONLY) { "Session-only pairing does not create trust" }
        val policy = policy()
        require(policy.enabled) { "Remembered browsers are disabled on this frame" }
        val now = clock()
        purgeLocked(now)
        require(dao.activeCount(now) < policy.maxRememberedBrowsers) {
            "This frame already remembers the maximum number of browsers"
        }
        val expiresAt = RememberedBrowserExpiry.calculate(
            now,
            RememberedBrowserExpiry.Request(
                request.mode,
                request.amount,
                request.unit,
                request.confirmForever,
            ),
            policy,
        )
        val label = sanitizeLabel(request.label, request.browserSummary)
        val id = UUID.randomUUID().toString()
        val secret = randomSecret()
        val hash = tokenHash("$id.$secret")?.hash
            ?: throw IllegalStateException("Remembered-browser security key is unavailable")
        dao.put(
            RememberedBrowserEntity(
                id = id,
                currentTokenHash = hash,
                label = label,
                createdAtEpochMs = now,
                lastUsedAtEpochMs = now,
                expiresAtEpochMs = expiresAt,
                browserSummary = request.browserSummary?.take(80),
                osSummary = request.osSummary?.take(80),
                lastTrustedWallClockEpochMs = now,
            )
        )
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "REMEMBERED_BROWSER_CREATED",
            "browserToken" to diagnosticToken(id, "browser"),
            "expiryMode" to request.mode.name,
            "expiresAt" to (expiresAt?.toString() ?: "forever"),
        )
        Created(id, "$id.$secret", expiresAt, label)
    }

    suspend fun exchange(rawCredential: String): ExchangeResult = mutex.withLock {
        val parsed = parseCredential(rawCredential) ?: return@withLock ExchangeResult.Rejected()
        val now = clock()
        purgeLocked(now)
        val record = dao.get(parsed.first) ?: return@withLock ExchangeResult.Rejected()
        val policy = policy()
        if (!policy.enabled) return@withLock ExchangeResult.Rejected(record.id, revokeSessions = true)
        if (record.revokedAtEpochMs != null) return@withLock ExchangeResult.Rejected()
        if (record.expiresAtEpochMs != null && now >= record.expiresAtEpochMs) {
            dao.revoke(record.id, now)
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                "REMEMBERED_BROWSER_EXPIRED",
                "browserToken" to diagnosticToken(record.id, "browser"),
            )
            return@withLock ExchangeResult.Rejected()
        }
        if (now + CLOCK_ROLLBACK_TOLERANCE_MS < record.lastTrustedWallClockEpochMs) {
            dao.revoke(record.id, now)
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                "REMEMBERED_BROWSER_CLOCK_ROLLBACK",
                "browserToken" to diagnosticToken(record.id, "browser"),
            )
            return@withLock ExchangeResult.ClockRollback(record.id)
        }
        val presented = tokenHash(rawCredential)
            ?: return@withLock ExchangeResult.Rejected(record.id, revokeSessions = true)
        if (presented.resetExistingTrust) {
            return@withLock ExchangeResult.Rejected(record.id, revokeSessions = true)
        }
        val presentedHash = presented.hash
        val currentMatch = secureEquals(record.currentTokenHash, presentedHash)
        val previousMatch = record.previousTokenHash?.let { secureEquals(it, presentedHash) } == true
        val retiredMatch = record.retiredTokenHash?.let { secureEquals(it, presentedHash) } == true

        if (retiredMatch || (previousMatch && now > (record.previousTokenValidUntilEpochMs ?: 0L))) {
            dao.put(record.copy(revokedAtEpochMs = now, lastTrustedWallClockEpochMs = now))
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                "REMEMBERED_BROWSER_TOKEN_REPLAYED",
                "browserToken" to diagnosticToken(record.id, "browser"),
            )
            return@withLock ExchangeResult.Rejected(record.id, revokeSessions = true)
        }
        if (!currentMatch && !previousMatch) return@withLock ExchangeResult.Rejected()

        val newSecret = randomSecret()
        val newCredential = "${record.id}.$newSecret"
        val newHash = tokenHash(newCredential)?.hash ?: run {
            dao.revoke(record.id, now)
            return@withLock ExchangeResult.Rejected(record.id, revokeSessions = true)
        }
        val rotated = record.copy(
            currentTokenHash = newHash,
            previousTokenHash = record.currentTokenHash,
            previousTokenValidUntilEpochMs = now + policy.rotationGraceSeconds * 1_000L,
            retiredTokenHash = record.previousTokenHash,
            lastUsedAtEpochMs = now,
            lastTrustedWallClockEpochMs = now,
        )
        dao.put(rotated)
        diagnostics.log(
            DiagnosticsLog.Category.APP,
            "REMEMBERED_BROWSER_TOKEN_ROTATED",
            "browserToken" to diagnosticToken(record.id, "browser"),
        )
        ExchangeResult.Ok(record.id, newCredential, record.expiresAtEpochMs)
    }

    suspend fun list(): List<BrowserView> = dao.all().map {
        BrowserView(
            it.id, it.label, it.createdAtEpochMs, it.lastUsedAtEpochMs,
            it.expiresAtEpochMs, it.revokedAtEpochMs, it.browserSummary, it.osSummary,
        )
    }

    suspend fun revoke(id: String): Boolean = mutex.withLock {
        val changed = dao.revoke(id, clock()) > 0
        if (changed) diagnostics.log(
            DiagnosticsLog.Category.APP,
            "REMEMBERED_BROWSER_REVOKED",
            "browserToken" to diagnosticToken(id, "browser"),
        )
        changed
    }

    suspend fun revokeAllExcept(id: String): Int = mutex.withLock {
        val count = dao.revokeAllExcept(id, clock())
        if (count > 0) diagnostics.log(DiagnosticsLog.Category.APP, "REMEMBERED_BROWSER_REVOKE_OTHERS", "count" to count.toString())
        count
    }

    suspend fun revokeAll(): Int = mutex.withLock {
        val count = dao.revokeAll(clock())
        diagnostics.log(DiagnosticsLog.Category.APP, "REMEMBERED_BROWSER_REVOKE_ALL", "count" to count.toString())
        count
    }

    suspend fun purge(): Int = mutex.withLock { purgeLocked(clock()) }

    private suspend fun purgeLocked(now: Long): Int = dao.purgeOld(now - EXPIRED_RETENTION_MS)

    /**
     * Loads the Keystore-protected HMAC secret. If the key was lost while records exist,
     * trust fails closed: records are revoked and a fresh secret is created for future pairing.
     */
    private data class SecretMaterial(
        val bytes: ByteArray,
        val resetExistingTrust: Boolean,
    )

    private data class HashResult(
        val hash: ByteArray,
        val resetExistingTrust: Boolean,
    )

    private suspend fun hmacSecret(): SecretMaterial? {
        val revealed = secretStore.reveal(HMAC_SECRET_REF)
        if (revealed != null) {
            val decoded = decode(revealed)
            if (decoded != null && decoded.size == 32) return SecretMaterial(decoded, false)
        }
        val hadRecords = dao.all().isNotEmpty()
        if (hadRecords) {
            dao.revokeAll(clock())
            diagnostics.log(DiagnosticsLog.Category.APP, "REMEMBERED_BROWSER_KEY_LOST")
        }
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val encoded = encode(bytes)
        return runCatching {
            secretStore.store(HMAC_SECRET_REF, HMAC_SECRET_TYPE, encoded)
            SecretMaterial(bytes, hadRecords)
        }.getOrNull()
    }

    private suspend fun tokenHash(rawCredential: String): HashResult? {
        val material = hmacSecret() ?: return null
        return try {
            runCatching {
                HashResult(
                    hash = Mac.getInstance("HmacSHA256").run {
                        init(SecretKeySpec(material.bytes, "HmacSHA256"))
                        doFinal(rawCredential.toByteArray(Charsets.UTF_8))
                    },
                    resetExistingTrust = material.resetExistingTrust,
                )
            }.getOrNull()
        } finally {
            material.bytes.fill(0)
        }
    }

    private fun parseCredential(raw: String): Pair<String, String>? {
        if (raw.length !in 40..180) return null
        val dot = raw.indexOf('.')
        if (dot <= 0 || dot != raw.lastIndexOf('.')) return null
        val id = raw.substring(0, dot)
        val secret = raw.substring(dot + 1)
        if (runCatching { UUID.fromString(id) }.isFailure) return null
        if (secret.length !in 32..80 || secret.any { !(it.isLetterOrDigit() || it == '-' || it == '_') }) return null
        return id to secret
    }

    private fun sanitizeLabel(label: String, browserSummary: String?): String {
        val clean = label.trim().filterNot(Char::isISOControl).take(64)
        return clean.ifBlank { browserSummary?.trim()?.take(64).orEmpty().ifBlank { "Browser" } }
    }

    private fun randomSecret(): String = encode(ByteArray(32).also { random.nextBytes(it) })
    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun decode(value: String): ByteArray? = runCatching {
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }.getOrNull()
    private fun secureEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    companion object {
        private const val HMAC_SECRET_REF = "web_remembered_browser_hmac_v1"
        private const val HMAC_SECRET_TYPE = "web_remembered_browser_hmac"
        private const val CLOCK_ROLLBACK_TOLERANCE_MS = 10L * 60_000L
        private const val EXPIRED_RETENTION_MS = 7L * 24L * 60L * 60_000L
    }
}
