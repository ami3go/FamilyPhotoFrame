#!/usr/bin/env bash
# Drive the real logging classes, then hand the result to the real analyser.
#
# The writer (Kotlin) and the reader (Python) are developed separately and only meet on a
# user's device after a 24-hour test run. That is a bad place to discover a format
# disagreement, so they are made to meet here instead.
set -euo pipefail
cd "$(dirname "$0")/.."

KOTLIN_VERSION="2.0.21"
WORK="${TMPDIR:-/tmp}/ffv"
mkdir -p "$WORK"
# Prefer an explicitly configured or already installed compiler. Download the pinned
# version only as a last resort; verification must remain usable on offline developer
# machines that already have Kotlin installed.
if [ -n "${KOTLINC:-}" ] && [ -x "$KOTLINC" ]; then
  :
elif command -v kotlinc >/dev/null 2>&1; then
  KOTLINC="$(command -v kotlinc)"
elif [ -x "$WORK/kotlinc/bin/kotlinc" ]; then
  KOTLINC="$WORK/kotlinc/bin/kotlinc"
else
  echo "==> No Kotlin compiler found; fetching pinned Kotlin $KOTLIN_VERSION"
  curl -fLsS -o "$WORK/kc.zip" \
    "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"
  ( cd "$WORK" && unzip -q -o kc.zip && chmod +x kotlinc/bin/* )
  KOTLINC="$WORK/kotlinc/bin/kotlinc"
fi
KOTLIN_HOME="$(cd "$(dirname "$KOTLINC")/.." && pwd)"

SRC="app/src/main/java/com/example/familyphotoframe/data/diagnostics"
E2E="$WORK/e2e"
rm -rf "$E2E"; mkdir -p "$E2E/src"

# Real production sources, package line stripped so they share a default package.
for f in DiagnosticEventSpec.kt DiagnosticOperationTracker.kt DiagnosticIdentityHasher.kt \
  DiagnosticPrivacyPolicy.kt DiagnosticRateController.kt DiagnosticsHealthSnapshot.kt \
  NativeAllocationStageTracker.kt DiagnosticRuntimeState.kt DiagnosticsBundle.kt DiagnosticsLog.kt DiagnosticsJsonl.kt \
  FileDiagnosticsSink.kt; do
  sed 's/^package .*//' "$SRC/$f" > "$E2E/src/$f"
done
# Kept outside scripts/verify/ on purpose: the pure-logic harness compiles that whole
# directory, and this driver depends on FileDiagnosticsSink, which is not part of the
# pure set. Leaving it there broke the pure harness.
cp scripts/e2e/DiagnosticsEndToEnd.kt "$E2E/src/"

echo "==> Compiling the real diagnostics classes"
"$KOTLINC" "$E2E/src" -include-runtime -d "$E2E/e2e.jar" -nowarn 2>&1 | grep -E "error:" && exit 1

# --- Scenario 1: realistic device budget. Nothing should rotate away, so every
# --- criterion must pass. This is the check that the writer and reader agree.
echo "==> Scenario 1: 26h run at the real 2 MB/4 MB log budget"
java -cp "$E2E/e2e.jar" DiagnosticsEndToEnd "$E2E/full" 2048
set +e
python3 scripts/analyze-diagnostics.py "$E2E/full/bundle.jsonl"
rc1=$?
set -e
if [ "$rc1" -ne 0 ]; then
  echo
  echo "END-TO-END FAILED: a complete run did not satisfy the analyser (exit $rc1)"
  exit 1
fi

# --- Scenario 2: starved budget, forcing heavy rotation. The evidence stream must
# --- survive, and anything that genuinely cannot be judged must report NO DATA rather
# --- than a confident wrong answer. Exit 2 means "incomplete", which is the correct
# --- outcome here; exit 1 would mean it drew a false conclusion.
echo
echo "==> Scenario 2: same run starved to 32 KB/64 KB, forcing rotation"
java -cp "$E2E/e2e.jar" DiagnosticsEndToEnd "$E2E/tiny" 32
set +e
python3 scripts/analyze-diagnostics.py "$E2E/tiny/bundle.jsonl" > "$E2E/tiny/report.txt"
rc2=$?
set -e
grep -E "WARNING|NO DATA|FAIL" "$E2E/tiny/report.txt" | head -5
if [ "$rc2" -eq 1 ]; then
  echo
  echo "END-TO-END FAILED: rotation caused a false FAIL rather than NO DATA"
  exit 1
fi
if ! grep -q "WARNING" "$E2E/tiny/report.txt"; then
  echo
  echo "END-TO-END FAILED: rotation was not detected or reported"
  exit 1
fi
grep -q "PASS     Primary/fallback transitions" "$E2E/tiny/report.txt" || {
  echo; echo "END-TO-END FAILED: evidence stream did not survive rotation"; exit 1; }

echo
echo "END-TO-END PASSED (complete run verified; starved run degrades honestly)"
