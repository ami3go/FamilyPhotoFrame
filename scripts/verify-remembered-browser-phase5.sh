#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

WORK="${TMPDIR:-/tmp}/fpf-remembered-phase5"
rm -rf "$WORK"
mkdir -p "$WORK/stubs"
KOTLIN_VERSION="2.0.21"
KOTLIN_CACHE="${TMPDIR:-/tmp}/ffv"
mkdir -p "$KOTLIN_CACHE"
# Prefer an explicitly configured or already installed compiler. Download the pinned
# version only as a last resort, matching verify-engine-types.sh, so this gate does not
# hard-fail under `set -e` on a machine (or container) without kotlinc on PATH.
if [ -n "${KOTLINC:-}" ] && [ -x "$KOTLINC" ]; then
  :
elif command -v kotlinc >/dev/null 2>&1; then
  KOTLINC="$(command -v kotlinc)"
elif [ -x "$KOTLIN_CACHE/kotlinc/bin/kotlinc" ]; then
  KOTLINC="$KOTLIN_CACHE/kotlinc/bin/kotlinc"
else
  printf '%s\n' "==> No Kotlin compiler found; fetching pinned Kotlin $KOTLIN_VERSION"
  curl -fLsS -o "$KOTLIN_CACHE/kc.zip" \
    "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"
  ( cd "$KOTLIN_CACHE" && unzip -q -o kc.zip && chmod +x kotlinc/bin/* )
  KOTLINC="$KOTLIN_CACHE/kotlinc/bin/kotlinc"
fi
KOTLIN_HOME="$(cd "$(dirname "$KOTLINC")/.." && pwd)"
COROUTINES="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"

cat > "$WORK/stubs/AndroidBase64.kt" <<'KT'
package android.util
object Base64 {
    const val URL_SAFE = 8
    const val NO_WRAP = 2
    const val NO_PADDING = 1
    fun encodeToString(bytes: ByteArray, flags: Int): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun decode(value: String, flags: Int): ByteArray = java.util.Base64.getUrlDecoder().decode(value)
}
KT

cat > "$WORK/stubs/Db.kt" <<'KT'
package com.example.familyphotoframe.data.db

data class RememberedBrowserEntity(
    val id: String,
    val currentTokenHash: ByteArray,
    val previousTokenHash: ByteArray? = null,
    val previousTokenValidUntilEpochMs: Long? = null,
    val retiredTokenHash: ByteArray? = null,
    val label: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val revokedAtEpochMs: Long? = null,
    val browserSummary: String? = null,
    val osSummary: String? = null,
    val lastTrustedWallClockEpochMs: Long,
)

interface RememberedBrowserDao {
    suspend fun put(record: RememberedBrowserEntity)
    suspend fun get(id: String): RememberedBrowserEntity?
    suspend fun all(): List<RememberedBrowserEntity>
    suspend fun activeCount(now: Long): Int
    suspend fun revoke(id: String, now: Long): Int
    suspend fun revokeAllExcept(keepId: String, now: Long): Int
    suspend fun revokeAll(now: Long): Int
    suspend fun purgeOld(expiredBefore: Long): Int
    suspend fun deleteAll()
}
KT

cat > "$WORK/stubs/Settings.kt" <<'KT'
package com.example.familyphotoframe.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

enum class RememberExpiryMode { SESSION_ONLY, ONE_HOUR, ONE_DAY, ONE_WEEK, ONE_MONTH, ONE_YEAR, FOREVER, CUSTOM }
enum class CustomExpiryUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

data class RememberedBrowserPolicy(
    val enabled: Boolean = false,
    val defaultExpiry: RememberExpiryMode = RememberExpiryMode.SESSION_ONLY,
    val maxExpirySeconds: Long = 366L * 24L * 60L * 60L,
    val allowForever: Boolean = false,
    val maxRememberedBrowsers: Int = 8,
    val requireStepUpForSensitiveActions: Boolean = true,
    val rotateOnExchange: Boolean = true,
    val rotationGraceSeconds: Int = 30,
) {
    fun normalized() = copy(
        maxExpirySeconds = maxExpirySeconds.coerceIn(600L, 10L * 365L * 24L * 60L * 60L),
        maxRememberedBrowsers = maxRememberedBrowsers.coerceIn(1, 32),
        rotationGraceSeconds = rotationGraceSeconds.coerceIn(5, 120),
        defaultExpiry = when {
            !enabled -> RememberExpiryMode.SESSION_ONLY
            defaultExpiry == RememberExpiryMode.FOREVER && !allowForever -> RememberExpiryMode.SESSION_ONLY
            else -> defaultExpiry
        },
    )
}
data class WebSettings(val rememberedBrowsers: RememberedBrowserPolicy = RememberedBrowserPolicy())
data class AppSettings(val web: WebSettings = WebSettings())

class SettingsRepository(initial: AppSettings) {
    private val flow = MutableStateFlow(initial)
    val settings: Flow<AppSettings> = flow
    suspend fun update(transform: (AppSettings) -> AppSettings) { flow.value = transform(flow.value) }
}
KT

cat > "$WORK/stubs/Secrets.kt" <<'KT'
package com.example.familyphotoframe.data.secret
class KeystoreSecretStore {
    private val values = linkedMapOf<String, String>()
    suspend fun reveal(ref: String): String? = values[ref]
    suspend fun store(ref: String, type: String, plaintext: String) { values[ref] = plaintext }
    suspend fun forget(ref: String) { values.remove(ref) }
    fun loseAllKeysForTest() { values.clear() }
}
KT

cat > "$WORK/stubs/Diagnostics.kt" <<'KT'
package com.example.familyphotoframe.data.diagnostics
class DiagnosticsLog {
    enum class Category { APP }
    val codes = mutableListOf<String>()
    fun log(category: Category, code: String, message: String = "") { codes += code }
    fun log(category: Category, code: String, vararg fields: Pair<String, String>) { codes += code }
}
fun diagnosticToken(value: String, type: String = "id"): String =
    type + "_" + value.hashCode().toUInt().toString(16).padStart(8, '0')
KT

cat > "$WORK/Driver.kt" <<'KT'
import com.example.familyphotoframe.data.db.*
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.secret.KeystoreSecretStore
import com.example.familyphotoframe.data.settings.*
import com.example.familyphotoframe.web.*
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.TimeZone

private class MemoryDao : RememberedBrowserDao {
    val records = linkedMapOf<String, RememberedBrowserEntity>()
    override suspend fun put(record: RememberedBrowserEntity) { records[record.id] = record }
    override suspend fun get(id: String) = records[id]
    override suspend fun all() = records.values.toList()
    override suspend fun activeCount(now: Long) = records.values.count {
        it.revokedAtEpochMs == null && (it.expiresAtEpochMs == null || it.expiresAtEpochMs > now)
    }
    override suspend fun revoke(id: String, now: Long): Int {
        val row = records[id] ?: return 0
        if (row.revokedAtEpochMs != null) return 0
        records[id] = row.copy(revokedAtEpochMs = now); return 1
    }
    override suspend fun revokeAllExcept(keepId: String, now: Long): Int {
        var n=0; records.keys.toList().filter { it != keepId }.forEach { n += revoke(it, now) }; return n
    }
    override suspend fun revokeAll(now: Long): Int {
        var n=0; records.keys.toList().forEach { n += revoke(it, now) }; return n
    }
    override suspend fun purgeOld(expiredBefore: Long): Int {
        val ids=records.values.filter {
            (it.expiresAtEpochMs != null && it.expiresAtEpochMs < expiredBefore) ||
            (it.revokedAtEpochMs != null && it.revokedAtEpochMs < expiredBefore)
        }.map { it.id }
        ids.forEach(records::remove); return ids.size
    }
    override suspend fun deleteAll() { records.clear() }
}

private fun manager(
    dao: MemoryDao,
    secrets: KeystoreSecretStore,
    settings: SettingsRepository,
    diagnostics: DiagnosticsLog,
    now: () -> Long,
) = RememberedBrowserManager(dao, secrets, settings, diagnostics, clock = now)

fun main() = runBlocking {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    var now = 1_700_000_000_000L
    val dao = MemoryDao()
    val secrets = KeystoreSecretStore()
    val diagnostics = DiagnosticsLog()
    val policy = RememberedBrowserPolicy(enabled=true, allowForever=true, maxRememberedBrowsers=3)
    val settings = SettingsRepository(AppSettings(WebSettings(policy)))
    val manager = manager(dao, secrets, settings, diagnostics) { now }

    val created = manager.create(RememberedBrowserManager.CreateRequest(RememberExpiryMode.ONE_HOUR, label="Office PC"))
    check(created.credential.startsWith(created.id + "."))
    check(dao.records[created.id]!!.currentTokenHash.toString(Charsets.UTF_8) != created.credential)

    val first = manager.exchange(created.credential) as RememberedBrowserManager.ExchangeResult.Ok
    check(first.rotatedCredential != created.credential)
    now += 1_000L
    val grace = manager.exchange(created.credential)
    check(grace is RememberedBrowserManager.ExchangeResult.Ok) { "previous token should work inside grace" }

    val replayCreated = manager.create(RememberedBrowserManager.CreateRequest(RememberExpiryMode.ONE_DAY, label="Replay test"))
    manager.exchange(replayCreated.credential)
    now += 31_000L
    val replay = manager.exchange(replayCreated.credential)
    check(replay is RememberedBrowserManager.ExchangeResult.Rejected && replay.revokeSessions)
    check(dao.records[replayCreated.id]!!.revokedAtEpochMs != null)

    val rollbackCreated = manager.create(RememberedBrowserManager.CreateRequest(RememberExpiryMode.ONE_DAY, label="Clock test"))
    val rollbackCurrent = (manager.exchange(rollbackCreated.credential) as RememberedBrowserManager.ExchangeResult.Ok).rotatedCredential
    now -= 11L * 60_000L
    val rollback = manager.exchange(rollbackCurrent)
    check(rollback is RememberedBrowserManager.ExchangeResult.ClockRollback)
    now += 12L * 60_000L

    val keyCreated = manager.create(RememberedBrowserManager.CreateRequest(RememberExpiryMode.ONE_DAY, label="Key loss"))
    secrets.loseAllKeysForTest()
    val keyLoss = manager.exchange(keyCreated.credential)
    check(keyLoss is RememberedBrowserManager.ExchangeResult.Rejected && keyLoss.revokeSessions)
    check(dao.records.values.all { it.revokedAtEpochMs != null })
    check("REMEMBERED_BROWSER_KEY_LOST" in diagnostics.codes)

    val limitDao=MemoryDao(); val limitSecrets=KeystoreSecretStore(); val limitSettings=SettingsRepository(
        AppSettings(WebSettings(policy.copy(maxRememberedBrowsers=1)))
    )
    val limitManager=manager(limitDao,limitSecrets,limitSettings,DiagnosticsLog()){now}
    limitManager.create(RememberedBrowserManager.CreateRequest(RememberExpiryMode.ONE_DAY))
    check(runCatching { limitManager.create(RememberedBrowserManager.CreateRequest(RememberExpiryMode.ONE_DAY)) }.isFailure)

    val monthStart=Calendar.getInstance().apply { clear(); set(2024,Calendar.JANUARY,31,12,0,0) }.timeInMillis
    val monthEnd=RememberedBrowserExpiry.calculate(monthStart,
        RememberedBrowserExpiry.Request(RememberExpiryMode.ONE_MONTH), policy)!!
    val c=Calendar.getInstance().apply { timeInMillis=monthEnd }
    check(c.get(Calendar.MONTH)==Calendar.FEBRUARY && c.get(Calendar.DAY_OF_MONTH)==29)

    println("Remembered-browser manager hardening tests passed")
}
KT

"$KOTLINC" -classpath "$COROUTINES" \
  "$WORK/stubs" \
  app/src/main/java/com/example/familyphotoframe/web/RememberedBrowserExpiry.kt \
  app/src/main/java/com/example/familyphotoframe/web/RememberedBrowserManager.kt \
  "$WORK/Driver.kt" -d "$WORK/out.jar"
"$KOTLINC" -version >/dev/null
KOTLIN_RUN="$(dirname "$KOTLINC")/kotlin"
"$KOTLIN_RUN" -classpath "$WORK/out.jar:$COROUTINES" DriverKt

python3 - <<'PY'
from pathlib import Path
root=Path('.')
backup=(root/'app/src/main/res/xml/backup_rules.xml').read_text()
extract=(root/'app/src/main/res/xml/data_extraction_rules.xml').read_text()
for text,name in [(backup,'legacy backup'),(extract,'data extraction')]:
    assert 'path="frame.db"' in text, f'{name}: frame database not excluded'
manager=(root/'app/src/main/java/com/example/familyphotoframe/web/RememberedBrowserManager.kt').read_text()
for forbidden in ['println(rawCredential)', 'credentialHash" to', 'rawCredential" to']:
    assert forbidden not in manager, forbidden
server=(root/'app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt').read_text()
assert 'REMEMBERED_GLOBAL_RATE_LIMIT = 200' in server
assert 'Cache-Control", "no-store"' in server
controller=(root/'app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt').read_text()
assert 'REMEMBERED_CLEANUP_INTERVAL_MS' in controller
print('Remembered-browser backup, rate-limit, and cleanup contracts passed')
PY
