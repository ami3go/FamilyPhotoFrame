package com.example.familyphotoframe.web

import com.example.familyphotoframe.util.toHexString
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** In-memory PIN pairing, bounded active sessions, idle expiry, absolute expiry and CSRF. */
class WebSecurity(
    private val idleTimeoutMs: Long,
    private val absoluteSessionLifetimeMs: Long = 12L * 60L * 60_000L,
    private val maxSessions: Int = 8,
    private val maxSessionsPerRememberedBrowser: Int = 2,
    private val maxPinAttempts: Int = 5,
    private val lockoutMs: Long = 15 * 60_000L,
    private val qrTokenTtlMs: Long = 5 * 60_000L,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Session(
        val token: String,
        val csrfToken: String,
        val createdAtMs: Long,
        var lastSeenAtMs: Long,
        val rememberedBrowserId: String? = null,
    )

    sealed interface PairResult {
        data class Ok(val token: String, val csrfToken: String) : PairResult
        data object Rejected : PairResult
        data class LockedOut(val retryAfterMs: Long) : PairResult
    }

    private val lock = Any()
    private var pinHash: ByteArray? = null
    private var pinSalt: ByteArray? = null
    private var currentPin: String? = null
    private val sessions = LinkedHashMap<String, Session>()
    private var failedAttempts = 0
    private var lockedUntilMs = 0L
    private var qrToken: String? = null
    private var qrTokenExpiresAtMs = 0L

    fun regeneratePin(): String = synchronized(lock) {
        val pin = buildString { repeat(PIN_DIGITS) { append(random.nextInt(10)) } }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val newHash = hashPin(pin, salt)
        pinSalt = salt
        pinHash = newHash
        currentPin = pin
        sessions.clear()
        failedAttempts = 0
        lockedUntilMs = 0L
        qrToken = null
        qrTokenExpiresAtMs = 0L
        pin
    }

    fun issueQrToken(): String? = synchronized(lock) {
        if (pinHash == null) return null
        randomToken().also {
            qrToken = it
            qrTokenExpiresAtMs = clock() + qrTokenTtlMs
        }
    }

    fun pairWithToken(candidate: String): PairResult = synchronized(lock) {
        val now = clock()
        if (now < lockedUntilMs) return PairResult.LockedOut(lockedUntilMs - now)
        val expected = qrToken
        if (expected == null || now > qrTokenExpiresAtMs) return PairResult.Rejected
        if (!secureStringEquals(expected, candidate)) {
            recordFailedAttempt(now)
            return PairResult.Rejected
        }
        qrToken = null
        qrTokenExpiresAtMs = 0L
        failedAttempts = 0
        val session = issueSessionLocked(now, null)
        PairResult.Ok(session.token, session.csrfToken)
    }

    fun visiblePin(): String? = synchronized(lock) { currentPin }
    fun isPaired(): Boolean = synchronized(lock) { sessions.isNotEmpty() }

    fun reset() = synchronized(lock) {
        sessions.clear()
        pinHash = null
        pinSalt = null
        currentPin = null
        failedAttempts = 0
        lockedUntilMs = 0L
        qrToken = null
        qrTokenExpiresAtMs = 0L
    }

    /** Verify a recent owner PIN for step-up without creating another session. */
    fun verifyPin(candidatePin: String): Boolean = synchronized(lock) {
        val now = clock()
        if (now < lockedUntilMs) return false
        val hash = pinHash ?: return false
        val salt = pinSalt ?: return false
        val candidate = hashPin(candidatePin, salt)
        if (!MessageDigest.isEqual(hash, candidate)) {
            recordFailedAttempt(now)
            return false
        }
        failedAttempts = 0
        true
    }

    fun pair(candidatePin: String): PairResult = synchronized(lock) {
        val now = clock()
        if (now < lockedUntilMs) return PairResult.LockedOut(lockedUntilMs - now)
        val hash = pinHash
        val salt = pinSalt
        if (hash == null || salt == null) return PairResult.Rejected
        val candidate = hashPin(candidatePin, salt)
        if (!MessageDigest.isEqual(hash, candidate)) {
            recordFailedAttempt(now)
            return PairResult.Rejected
        }
        failedAttempts = 0
        val session = issueSessionLocked(now, null)
        PairResult.Ok(session.token, session.csrfToken)
    }

    /** Mint a normal bounded active session after persistent trust was independently verified. */
    fun issueRememberedSession(rememberedBrowserId: String): Session = synchronized(lock) {
        val now = clock()
        purgeExpired(now)
        issueSessionLocked(now, rememberedBrowserId)
    }

    fun authenticate(token: String?): Session? = synchronized(lock) {
        if (token.isNullOrEmpty()) return null
        val now = clock()
        purgeExpired(now)
        val session = sessions[token] ?: return null
        session.lastSeenAtMs = now
        session
    }

    fun authorizeStateChange(token: String?, csrfHeader: String?): Session? {
        val session = authenticate(token) ?: return null
        if (csrfHeader.isNullOrEmpty()) return null
        return if (secureStringEquals(session.csrfToken, csrfHeader)) session else null
    }

    fun revoke(token: String?) = synchronized(lock) {
        if (token != null) sessions.remove(token)
        Unit
    }

    fun revokeSessionsForRememberedBrowser(id: String): Int = synchronized(lock) {
        val tokens = sessions.values.filter { it.rememberedBrowserId == id }.map { it.token }
        tokens.forEach(sessions::remove)
        tokens.size
    }

    fun revokeAllSessions(): Int = synchronized(lock) {
        val count = sessions.size
        sessions.clear()
        count
    }

    fun rememberedBrowserIdForSession(token: String?): String? = authenticate(token)?.rememberedBrowserId

    private fun issueSessionLocked(now: Long, rememberedBrowserId: String?): Session {
        purgeExpired(now)
        if (rememberedBrowserId != null) {
            while (sessions.values.count { it.rememberedBrowserId == rememberedBrowserId } >= maxSessionsPerRememberedBrowser) {
                val oldest = sessions.values.filter { it.rememberedBrowserId == rememberedBrowserId }
                    .minByOrNull { it.lastSeenAtMs } ?: break
                sessions.remove(oldest.token)
            }
        }
        while (sessions.size >= maxSessions) {
            val oldest = sessions.values.minByOrNull { it.lastSeenAtMs } ?: break
            sessions.remove(oldest.token)
        }
        return Session(randomToken(), randomToken(), now, now, rememberedBrowserId).also {
            sessions[it.token] = it
        }
    }

    private fun recordFailedAttempt(now: Long) {
        failedAttempts++
        if (failedAttempts >= maxPinAttempts) {
            lockedUntilMs = now + lockoutMs
            failedAttempts = 0
        }
    }

    private fun purgeExpired(now: Long) {
        val it = sessions.entries.iterator()
        while (it.hasNext()) {
            val session = it.next().value
            if (now - session.lastSeenAtMs > idleTimeoutMs || now - session.createdAtMs > absoluteSessionLifetimeMs) {
                it.remove()
            }
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return bytes.toHexString()
    }

    private fun secureStringEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val PIN_DIGITS = 8
        private const val PBKDF2_ITERATIONS = 20_000
        const val HEADER_SESSION = "x-frame-session"
        const val HEADER_CSRF = "x-frame-csrf"
    }
}
