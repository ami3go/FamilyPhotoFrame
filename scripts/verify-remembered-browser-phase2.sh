#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/fpf-remembered-phase2"
rm -rf "$OUT"; mkdir -p "$OUT"
KOTLIN_VERSION="2.0.21"
KOTLIN_CACHE="${TMPDIR:-/tmp}/ffv"
mkdir -p "$KOTLIN_CACHE"
# Prefer an explicitly configured or already installed compiler. Download the pinned
# version only as a last resort, matching verify-engine-types.sh, so this gate does not
# hard-fail on a machine (or container) without kotlinc on PATH.
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
cat > "$OUT/Main.kt" <<'KT'
fun main() { runRememberedWebSecurityChecks() }
KT
"$KOTLINC" \
  app/src/main/java/com/example/familyphotoframe/util/Hex.kt \
  app/src/main/java/com/example/familyphotoframe/web/WebSecurity.kt \
  scripts/verify/RememberedWebSecurityTest.kt \
  "$OUT/Main.kt" \
  -include-runtime -d "$OUT/test.jar" -nowarn
java -jar "$OUT/test.jar"
python3 - <<'PY'
from pathlib import Path
root=Path('.')
server=(root/'app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt').read_text()
manager=(root/'app/src/main/java/com/example/familyphotoframe/web/RememberedBrowserManager.kt').read_text()
security=(root/'app/src/main/java/com/example/familyphotoframe/web/WebSecurity.kt').read_text()
required_server=[
'/api/v1/security/session/from-remembered', 'Cache-Control', 'Pragma', 'Expires',
'REMEMBERED_RATE_LIMIT', 'issueRememberedSession', 'rotatedRememberedCredential',
'REMEMBERED_AUTH_FAILED', 'rememberedCredential', 'STEP_UP_REQUIRED',
]
required_manager=['REMEMBERED_BROWSER_TOKEN_ROTATED','REMEMBERED_BROWSER_TOKEN_REPLAYED','rotationGraceSeconds','policy.enabled']
required_security=['absoluteSessionLifetimeMs','maxSessionsPerRememberedBrowser','revokeSessionsForRememberedBrowser','verifyPin']
missing=[x for x in required_server if x not in server]+[x for x in required_manager if x not in manager]+[x for x in required_security if x not in security]
if missing:
    raise SystemExit('Phase 2 missing: '+', '.join(missing))
print('Phase 2 endpoint/security contract passed')
PY
