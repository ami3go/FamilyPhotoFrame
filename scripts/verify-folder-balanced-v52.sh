#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

KOTLIN_VERSION="2.0.21"
WORK="${TMPDIR:-/tmp}/ffv"
mkdir -p "$WORK"
# Prefer an explicitly configured or already installed compiler. Download the pinned
# version only as a last resort, matching verify-engine-types.sh, so this gate does not
# hard-fail under `set -e` on a machine (or container) without kotlinc on PATH.
if [ -n "${KOTLINC:-}" ] && [ -x "$KOTLINC" ]; then
  :
elif command -v kotlinc >/dev/null 2>&1; then
  KOTLINC="$(command -v kotlinc)"
elif [ -x "$WORK/kotlinc/bin/kotlinc" ]; then
  KOTLINC="$WORK/kotlinc/bin/kotlinc"
else
  printf '%s\n' "==> No Kotlin compiler found; fetching pinned Kotlin $KOTLIN_VERSION"
  curl -fLsS -o "$WORK/kc.zip" \
    "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"
  ( cd "$WORK" && unzip -q -o kc.zip && chmod +x kotlinc/bin/* )
  KOTLINC="$WORK/kotlinc/bin/kotlinc"
fi
OUT="${TMPDIR:-/tmp}/fpf-folder-balanced-v52"
rm -rf "$OUT"; mkdir -p "$OUT"

printf '%s\n' '==> Static, SQL, migration, and engine checks'
python3 scripts/check-consistency.py
python3 scripts/verify-sql.py
python3 scripts/verify-migrations.py
bash scripts/verify-engine-types.sh

printf '%s\n' '==> Targeted folder-balanced endurance checks'
cp app/src/main/java/com/example/familyphotoframe/data/index/CanonicalPhotoPath.kt "$OUT/CanonicalPhotoPath.kt"
sed -i 's/^package .*//' "$OUT/CanonicalPhotoPath.kt"
for file in ShuffleModels.kt ShuffleRandom.kt ShuffleCycleGenerator.kt SourceAvailabilityTracker.kt; do
  sed 's/^package .*//' "app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/$file" > "$OUT/$file"
done
sed -i '/^import com.example.familyphotoframe.data.index.CanonicalPhotoPath$/d' "$OUT/ShuffleModels.kt"
sed 's/^package .*//' app/src/main/java/com/example/familyphotoframe/util/Hex.kt > "$OUT/Hex.kt"
sed -e 's/^package .*//' \
    -e '/^import com.example.familyphotoframe.data.settings.SelectionMode$/d' \
    -e '/^import com.example.familyphotoframe.util.toHexString$/d' \
    app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleScopeKeyFactory.kt \
    > "$OUT/ShuffleScopeKeyFactory.kt"
cat > "$OUT/SelectionMode.kt" <<'KT'
enum class SelectionMode {
    SEQUENTIAL, LEAST_RECENT_RANDOM, SHUFFLE_NO_REPEAT, FOLDER_BALANCED_SHUFFLE,
    DATE_TAKEN_NEWEST, DATE_TAKEN_OLDEST,
}
KT
cp scripts/verify/FolderBalancedPersistentChecks.kt "$OUT/"
cat > "$OUT/Main.kt" <<'KT'
fun check(name: String, expected: Any?, actual: Any?) {
    kotlin.check(expected == actual) { "$name: expected=$expected actual=$actual" }
    println("  PASS  $name")
}
fun main() { runFolderBalancedPersistentChecks() }
KT
"$KOTLINC" "$OUT"/*.kt -include-runtime -d "$OUT/checks.jar" -nowarn
java -jar "$OUT/checks.jar"

printf '%s\n' '==> Web and release compatibility checks'
python3 scripts/verify-web-ui-modernisation.py
python3 scripts/verify-stability-v528.py
python3 scripts/verify-user-features-phase5.py
python3 scripts/verify-folder-balanced-phase6.py
python3 scripts/verify-folder-balanced-phase7.py
python3 scripts/verify-folder-balanced-dynamic.py
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
else
  printf '%s\n' '  note: git diff check skipped (release ZIP has no .git metadata)'
fi
printf '%s\n' 'All offline folder-balanced shuffle v52 gates passed'
