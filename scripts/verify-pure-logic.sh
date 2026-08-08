#!/usr/bin/env bash
# Offline verification harness for the pure-Kotlin logic in this project.
#
# Why this exists: a full ./gradlew build needs services.gradle.org and
# dl.google.com. In a restricted/offline environment those are unreachable, so the
# code cannot be built at all. The Kotlin compiler, however, is downloadable from
# GitHub releases, and the project's pure logic (no Android imports) can be compiled
# and RUN against real assertions without any Android SDK.
#
# This is NOT a substitute for a real build. It catches syntax errors and verifies
# pure logic; it cannot type-check anything touching Android, Compose, Room or
# kotlinx.coroutines.
#
# Runs, in order: static cross-file consistency checks (scripts/check-consistency.py),
# a Kotlin parse of all main sources, then the pure-logic assertions.
#
# Usage:  ./scripts/verify-pure-logic.sh [--syntax-only]
set -euo pipefail

KOTLIN_VERSION="2.0.21"     # keep in sync with gradle/libs.versions.toml
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

echo "==> Static consistency checks (resources, callbacks, Room schema)"
python3 scripts/check-consistency.py || exit 1

# Runs before the parse step below on purpose: it catches statements accidentally joined
# onto the previous line, which parse cleanly and are only rejected by the type checker —
# invisible here, where the Android/Compose compiler cannot run.
echo "==> Kotlin structure checks (joined statements, brace balance)"
python3 scripts/verify-kotlin-structure.py || exit 1

if [ "${SKIP_SYNTAX:-0}" != "1" ]; then
  echo "==> Parsing all main sources (syntax check)"
  find app/src/main/java -name '*.kt' > "$WORK/srcs.txt"
  set +e
  "$KOTLINC" "@$WORK/srcs.txt" -d "$WORK/out" -nowarn 2>"$WORK/kc.log"
  set -e
  # Without the Android/Compose/coroutines classpath, unresolved-symbol errors are
  # expected and are NOT failures. Genuine parse errors are what we look for.
  if grep -E "error: (expecting|unexpected token|expected )" "$WORK/kc.log" | grep . ; then
    echo "!! Parse errors found (above)"; exit 1
  fi
  echo "    no parse errors"
fi

[ "${1:-}" = "--syntax-only" ] && exit 0

echo "==> Running pure-logic checks"
PURE="$WORK/pure"; rm -rf "$PURE"; mkdir -p "$PURE"
# ExifParsing has no Android dependencies; lift it out of its file and exercise it.
{ echo 'import java.time.*'; echo 'import java.time.format.*'
  echo 'import java.util.Locale'; echo 'import kotlin.math.abs'
  sed -n '/^object ExifParsing/,/^}/p' \
    app/src/main/java/com/example/familyphotoframe/data/index/ExifExtractor.kt
} > "$PURE/ExifParsing.kt"
# SynologyApi is likewise pure; it only needs the SourceError enum, stubbed here.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/source/SynologyApi.kt > "$PURE/SynologyApi.kt"
# CertPinning's fingerprint/matching logic is pure; the TLS plumbing below it is not, so
# only the object's dependency-free prefix is lifted (up to systemTrustManager).
sed -n '/^object CertPinning/,/^    private fun systemTrustManager/p' \
  app/src/main/java/com/example/familyphotoframe/data/source/CertPinning.kt \
  | sed '$d' > "$PURE/CertPinning.kt"
{ cat "$PURE/CertPinning.kt"; echo '}'; } > "$PURE/CertPinning.tmp" && mv "$PURE/CertPinning.tmp" "$PURE/CertPinning.kt"
# fingerprintOf needs X509Certificate; stub it out of the pure slice.
sed -i 's/fun fingerprintOf(cert: X509Certificate): String =/fun fingerprintOfUnused(): String =/; s/formatFingerprint(MessageDigest.getInstance("SHA-256").digest(cert.encoded))/""/' "$PURE/CertPinning.kt"
# FrameStats is pure arithmetic; lift it whole.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/perf/FrameStats.kt > "$PURE/FrameStats.kt"
# Source routing and credential identity are pure policy and protect the remote-source fixes.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/source/BuiltInSourceIds.kt > "$PURE/BuiltInSourceIds.kt"
# Hex keeps its real package: WebSecurity below is copied verbatim (the remembered-session
# checks import com.example.familyphotoframe.web.WebSecurity) and uses toHexString, so it
# needs a real import target. Files flattened to the default package can still import it.
cp app/src/main/java/com/example/familyphotoframe/util/Hex.kt "$PURE/Hex.kt"
sed 's/^package .*//' \
    app/src/main/java/com/example/familyphotoframe/data/settings/CredentialPolicy.kt > "$PURE/CredentialPolicy.kt"
# PlaybackQueue is pure Kotlin (no Room/Android), and its no-repeat guarantee is exactly
# the kind of claim that needs asserting rather than trusting.
{ echo 'import java.util.ArrayDeque'
  sed 's/^package .*//' \
    app/src/main/java/com/example/familyphotoframe/domain/randomize/PlaybackQueue.kt
} > "$PURE/PlaybackQueue.kt"
{ echo 'import java.util.ArrayDeque'
  sed 's/^package .*//' \
    app/src/main/java/com/example/familyphotoframe/domain/randomize/FolderCycleQueue.kt
} > "$PURE/FolderCycleQueue.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/randomize/FolderBalancedPlaybackQueue.kt \
  > "$PURE/FolderBalancedPlaybackQueue.kt"
# Persistent folder-balanced shuffle domain/generator/backoff are pure Kotlin.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/index/CanonicalPhotoPath.kt \
  > "$PURE/CanonicalPhotoPath.kt"
for file in ShuffleModels.kt ShuffleRandom.kt ShuffleCycleGenerator.kt SourceAvailabilityTracker.kt; do
  sed 's/^package .*//' \
    "app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/$file" \
    > "$PURE/$file"
done
sed -i '/^import com.example.familyphotoframe.data.index.CanonicalPhotoPath$/d' "$PURE/ShuffleModels.kt"
# Scope identity depends only on the enum name; use a shape-equivalent enum in this harness.
sed -e 's/^package .*//' \
    -e '/^import com.example.familyphotoframe.data.settings.SelectionMode$/d' \
    app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleScopeKeyFactory.kt \
    > "$PURE/ShuffleScopeKeyFactory.kt"
cat > "$PURE/SelectionModeStub.kt" <<'KT'
enum class SelectionMode {
    SEQUENTIAL, LEAST_RECENT_RANDOM, SHUFFLE_NO_REPEAT, FOLDER_BALANCED_SHUFFLE,
    DATE_TAKEN_NEWEST, DATE_TAKEN_OLDEST,
}
KT
# WebDavApi is pure protocol logic (no Android/network); its tolerance of real-world
# namespace prefixes is exactly what needs asserting without a server.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/source/WebDavApi.kt > "$PURE/WebDavApi.kt"
# ScanOptions' filter policy is pure; lift it with a stub for the enums it shares a file
# with, so what gets indexed is asserted rather than assumed.
sed -n '/^data class ScanOptions/,/^}/p' \
  app/src/main/java/com/example/familyphotoframe/data/source/PhotoSource.kt > "$PURE/ScanOptions.kt"
{ echo 'import java.util.Locale'
  sed -n '/^object SupportedFormats/,/^}/p' \
    app/src/main/java/com/example/familyphotoframe/util/StableId.kt
} > "$PURE/SupportedFormats.kt"
sed -n '/^data class FilterSettings/,/^}/p' \
  app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt \
  | sed 's/^@Serializable$//' > "$PURE/FilterSettings.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/settings/SourceRuntimeSignature.kt \
  > "$PURE/SourceRuntimeSignature.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/util/Glob.kt > "$PURE/Glob.kt"
# RecoveryPolicy is pure arithmetic extracted from the previously untestable loop.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/RecoveryPolicy.kt > "$PURE/RecoveryPolicy.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/SourceRecoveryCoordinator.kt \
  > "$PURE/SourceRecoveryCoordinator.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/HostLifecycleGate.kt \
  > "$PURE/HostLifecycleGate.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryPolicy.kt \
  > "$PURE/PlaybackMemoryPolicy.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryGuard.kt \
  > "$PURE/PlaybackMemoryGuard.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/MemorySelfRecoveryPolicy.kt \
  > "$PURE/MemorySelfRecoveryPolicy.kt"
# SourcePoolPolicy holds the merged-pool rules lifted out of SlideshowViewModel, which
# cannot be compiled here at all. Getting them type-checked and executed is the point.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/engine/SourcePoolPolicy.kt > "$PURE/SourcePoolPolicy.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/schedule/RescanSchedule.kt > "$PURE/RescanSchedule.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/schedule/SleepSchedule.kt > "$PURE/SleepSchedule.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/domain/schedule/BrightnessTimeline.kt > "$PURE/BrightnessTimeline.kt"
# Platform image-format capability policy is pure and protects old frames from
# repeatedly downloading HEIC files they cannot decode.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/util/ImageFormatSupport.kt > "$PURE/ImageFormatSupport.kt"
# ScanMergePolicy is pure, but its production row type carries Room annotations. Use a
# shape-equivalent stub so the actual merge implementation is compiled and exercised.
sed -e 's/^package .*//' -e '/^import com.example.familyphotoframe.data.db.PhotoItemEntity$/d' \
  app/src/main/java/com/example/familyphotoframe/data/index/ScanMergePolicy.kt > "$PURE/ScanMergePolicy.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/index/ScanCompletionPolicy.kt \
  > "$PURE/ScanCompletionPolicy.kt"
# v52.8 stability policies are dependency-free and must be executed at release time.
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/util/ImageMemoryBudget.kt \
  > "$PURE/ImageMemoryBudget.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/settings/BoundedTextInput.kt \
  > "$PURE/BoundedTextInput.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/BoundedScopeLogTracker.kt \
  > "$PURE/BoundedScopeLogTracker.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/data/db/FolderSummary.kt \
  > "$PURE/FolderSummary.kt"
sed -e 's/^package .*//' \
  -e '/^import com.example.familyphotoframe.data.db.FolderSummary$/d' \
  app/src/main/java/com/example/familyphotoframe/ui/settings/FolderSelectionPolicy.kt \
  > "$PURE/FolderSelectionPolicy.kt"
cat > "$PURE/PhotoItemEntityStub.kt" <<'KT'
data class PhotoItemEntity(
    val id: Long = 0, val stableId: String, val sourceId: String,
    val normalizedPath: String, val folderName: String, val fileName: String,
    val mimeType: String?, val sizeBytes: Long, val fileModifiedEpochMs: Long,
    val openToken: String, val indexedAtEpochMs: Long, val isHidden: Boolean = false,
    val lastShownAtEpochMs: Long? = null, val decodeFailureCount: Int = 0,
    val lastDecodeFailureAtEpochMs: Long? = null, val width: Int? = null,
    val height: Int? = null, val exifOrientation: Int = 0,
    val dateTakenEpochMs: Long? = null, val isFavorite: Boolean = false,
    val missingSinceEpochMs: Long? = null, val cacheKey: String? = null,
    val caption: String? = null, val gpsLat: Double? = null, val gpsLon: Double? = null,
    val exifScannedAtEpochMs: Long? = null,
    val canonicalDirectory: String = "@root", val contentSha256: String? = null,
    val contentHashScannedAtEpochMs: Long? = null,
)
KT

# WebSecurity is dependency-free and the remembered-session checks import its real package.
cp app/src/main/java/com/example/familyphotoframe/web/WebSecurity.kt "$PURE/WebSecurity.kt"

# Diagnostics is pure JVM code. Compile the real schema, catalog, operation registry,
# identity primitive, bounded writer and privacy boundary together.
for file in DiagnosticEventSpec.kt DiagnosticOperationTracker.kt DiagnosticIdentityHasher.kt \
  DiagnosticPrivacyPolicy.kt DiagnosticRateController.kt DiagnosticsHealthSnapshot.kt \
  DiagnosticRuntimeState.kt DiagnosticsBundle.kt CrashEnvelopeStore.kt MainThreadStallDetector.kt \
  ProcessExitReasonMapper.kt DiagnosticsLog.kt DiagnosticsJsonl.kt FileDiagnosticsSink.kt; do
  sed 's/^package .*//' \
    "app/src/main/java/com/example/familyphotoframe/data/diagnostics/$file" > "$PURE/$file"
done
sed -e 's/^package .*//' \
  -e '/^import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog$/d' \
  app/src/main/java/com/example/familyphotoframe/web/DiagnosticsPaging.kt \
  > "$PURE/DiagnosticsPaging.kt"
sed 's/^package .*//' \
  app/src/main/java/com/example/familyphotoframe/web/DiagnosticsDownloadNaming.kt \
  > "$PURE/DiagnosticsDownloadNaming.kt"
cp scripts/verify/*.kt "$PURE/"
COROUTINES="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
"$KOTLINC" "$PURE"/*.kt -cp "$COROUTINES" -include-runtime -d "$PURE/v.jar" -nowarn 2>/dev/null
java -cp "$PURE/v.jar:$COROUTINES" DriverKt

# Type-check the engine + persistence layer as well. Executing the pure rules proves the
# rules; this proves the code around them actually compiles against the real DAO.
echo
echo "==> Validating Room SQL against a real SQLite engine"
python3 scripts/verify-sql.py

echo
echo "==> Replaying the migration chain against a real SQLite engine"
python3 scripts/verify-migrations.py

if [ "${SKIP_E2E:-0}" != "1" ]; then
  echo
  ./scripts/verify-diagnostics-e2e.sh
fi

if [ "${SKIP_TYPECHECK:-0}" != "1" ]; then
  echo
  ./scripts/verify-engine-types.sh
fi
