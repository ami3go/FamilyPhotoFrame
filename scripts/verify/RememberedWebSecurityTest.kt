import com.example.familyphotoframe.web.WebSecurity
import java.security.SecureRandom

private class FakeClock(var now: Long = 1_000_000L)

fun runRememberedWebSecurityChecks() {
    val clock = FakeClock()
    val security = WebSecurity(
        idleTimeoutMs = 30 * 60_000L,
        absoluteSessionLifetimeMs = 12 * 60 * 60_000L,
        maxSessions = 3,
        maxSessionsPerRememberedBrowser = 2,
        random = SecureRandom(byteArrayOf(1,2,3,4)),
        clock = { clock.now },
    )
    val pin = security.regeneratePin()
    check(pin.length == 8)
    val paired = security.pair(pin) as WebSecurity.PairResult.Ok
    val a = security.issueRememberedSession("browser-a")
    clock.now += 1
    val b = security.issueRememberedSession("browser-a")
    clock.now += 1
    val c = security.issueRememberedSession("browser-a")
    check(security.authenticate(a.token) == null) { "oldest per-browser session was not evicted" }
    check(security.authenticate(b.token) != null)
    check(security.authenticate(c.token) != null)
    val d = security.issueRememberedSession("browser-b")
    check(security.authenticate(d.token) != null)
    check(security.authenticate(paired.token) == null) { "global session cap did not evict oldest" }
    clock.now += 31 * 60_000L
    check(security.authenticate(c.token) == null) { "idle expiry failed" }

    val absoluteSecurity = WebSecurity(
        idleTimeoutMs = 24L * 60L * 60_000L,
        absoluteSessionLifetimeMs = 12L * 60L * 60_000L,
        random = SecureRandom(byteArrayOf(5,6,7,8)),
        clock = { clock.now },
    )
    absoluteSecurity.regeneratePin()
    val absolute = absoluteSecurity.issueRememberedSession("browser-c")
    clock.now += 11L * 60L * 60_000L
    check(absoluteSecurity.authenticate(absolute.token) != null)
    clock.now += 61L * 60_000L
    check(absoluteSecurity.authenticate(absolute.token) == null) { "absolute expiry failed" }
    println("Remembered WebSecurity tests passed")
}
