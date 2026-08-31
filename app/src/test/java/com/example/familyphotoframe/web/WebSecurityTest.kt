package com.example.familyphotoframe.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Verifies the web security model (spec §15.2): pairing, PIN rate limiting,
 * session validity, idle expiry, and CSRF enforcement on state changes.
 */
class WebSecurityTest {

    private var now = 1_000_000L
    private fun security(idleMs: Long = 60_000L) =
        WebSecurity(idleTimeoutMs = idleMs, random = SecureRandom(), clock = { now })

    @Test fun pinIsGeneratedAndPairsOnce() {
        val s = security()
        val pin = s.regeneratePin()
        assertEquals(WebSecurity.PIN_DIGITS, pin.length)
        assertTrue(pin.all { it.isDigit() })

        val ok = s.pair(pin)
        assertTrue(ok is WebSecurity.PairResult.Ok)
        val session = (ok as WebSecurity.PairResult.Ok)
        assertNotNull(s.authenticate(session.token))
    }

    @Test fun wrongPinIsRejected() {
        val s = security()
        val pin = s.regeneratePin()
        val wrong = if (pin.startsWith("0")) "1" + pin.drop(1) else "0" + pin.drop(1)
        assertTrue(s.pair(wrong) is WebSecurity.PairResult.Rejected)
    }

    @Test fun repeatedFailuresLockOut() {
        val s = security()
        s.regeneratePin()
        repeat(5) { s.pair("00000000x".take(8)) }
        // Even the correct PIN is refused while locked out.
        assertTrue(s.pair("12345678") is WebSecurity.PairResult.LockedOut)
    }

    @Test fun unknownTokenIsNotAuthenticated() {
        val s = security()
        s.regeneratePin()
        assertNull(s.authenticate("not-a-token"))
        assertNull(s.authenticate(null))
    }

    @Test fun sessionExpiresAfterIdleTimeout() {
        val s = security(idleMs = 30_000L)
        val pin = s.regeneratePin()
        val ok = s.pair(pin) as WebSecurity.PairResult.Ok
        now += 31_000L
        assertNull(s.authenticate(ok.token))
    }

    @Test fun activityRefreshesIdleDeadline() {
        val s = security(idleMs = 30_000L)
        val pin = s.regeneratePin()
        val ok = s.pair(pin) as WebSecurity.PairResult.Ok
        now += 20_000L
        assertNotNull(s.authenticate(ok.token))   // refreshes
        now += 20_000L
        assertNotNull(s.authenticate(ok.token))   // still alive
    }

    @Test fun stateChangeRequiresMatchingCsrfToken() {
        val s = security()
        val pin = s.regeneratePin()
        val ok = s.pair(pin) as WebSecurity.PairResult.Ok
        assertNotNull(s.authorizeStateChange(ok.token, ok.csrfToken))
        assertNull(s.authorizeStateChange(ok.token, "wrong"))
        assertNull(s.authorizeStateChange(ok.token, null))
    }

    @Test fun regeneratingPinInvalidatesExistingSessions() {
        val s = security()
        val ok = s.pair(s.regeneratePin()) as WebSecurity.PairResult.Ok
        val newPin = s.regeneratePin()
        assertNull(s.authenticate(ok.token))
        assertNotEquals(ok.token, (s.pair(newPin) as WebSecurity.PairResult.Ok).token)
    }


    @Test fun rememberedSessionHasAbsoluteLifetime() {
        val s = WebSecurity(
            idleTimeoutMs = 24L * 60L * 60_000L,
            absoluteSessionLifetimeMs = 12L * 60L * 60_000L,
            clock = { now },
        )
        val session = s.issueRememberedSession("browser-a")
        now += 12L * 60L * 60_000L + 1L
        assertNull(s.authenticate(session.token))
    }

    @Test fun rememberedBrowserKeepsAtMostTwoActiveSessions() {
        val s = WebSecurity(
            idleTimeoutMs = 60_000L,
            maxSessionsPerRememberedBrowser = 2,
            clock = { now },
        )
        val first = s.issueRememberedSession("browser-a")
        now += 1L
        val second = s.issueRememberedSession("browser-a")
        now += 1L
        val third = s.issueRememberedSession("browser-a")
        assertNull(s.authenticate(first.token))
        assertNotNull(s.authenticate(second.token))
        assertNotNull(s.authenticate(third.token))
    }

    @Test fun resetRevokesEverything() {
        val s = security()
        val ok = s.pair(s.regeneratePin()) as WebSecurity.PairResult.Ok
        s.reset()
        assertNull(s.authenticate(ok.token))
        assertNull(s.visiblePin())
        // With no PIN armed, pairing can never succeed.
        assertTrue(s.pair("12345678") is WebSecurity.PairResult.Rejected)
    }
}
