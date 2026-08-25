#!/usr/bin/env bash
# Type-check the engine + persistence layer, not just parse it.
#
# The pure-logic harness compiles only dependency-free files. Everything else in this
# project has ever been *parsed* and never type-checked, because a real Android build
# needs services.gradle.org / dl.google.com / Maven Central, all of which are blocked
# here. That leaves the highest-risk code — DAO call sites, engine state transitions —
# completely unverified.
#
# It turns out very little of that code actually needs Android:
#   * Room contributes only annotations, which are trivially stubbed (codegen is not
#     needed to type-check *call sites*);
#   * kotlinx-coroutines ships inside the Kotlin compiler distribution already used here;
#   * DiagnosticsLog and the domain classes are plain Kotlin.
#
# So this compiles the real engine and the real DAO against stub annotations. It cannot
# validate generated Room SQL — only `./gradlew` can — but it does catch wrong argument
# counts and types at every `dao.*` call, which is precisely the class of mistake that
# recent query changes could introduce.
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
COROUTINES="$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar"
[ -f "$COROUTINES" ] || { echo "coroutines jar missing from Kotlin distribution"; exit 1; }

SRC="app/src/main/java/com/example/familyphotoframe"
TC="$WORK/typecheck"
rm -rf "$TC"; mkdir -p "$TC/stubs"

# ---- stubs -----------------------------------------------------------------
# Room annotations only; enough for the compiler to accept the real declarations.
cat > "$TC/stubs/Room.kt" <<'EOF'
package androidx.room

@Target(AnnotationTarget.CLASS) annotation class Dao
@Target(AnnotationTarget.CLASS) annotation class Database(
    val entities: Array<kotlin.reflect.KClass<*>> = [],
    val version: Int = 1,
    val exportSchema: Boolean = true,
)
@Target(AnnotationTarget.CLASS) annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = [],
)
annotation class Index(
    val value: Array<String> = [],
    val unique: Boolean = false,
    val name: String = "",
)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class Query(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Insert(val onConflict: Int = 1)
@Target(AnnotationTarget.FUNCTION) annotation class Update(val onConflict: Int = 1)
@Target(AnnotationTarget.FUNCTION) annotation class Delete
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS) annotation class Transaction
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class TypeConverters(val value: Array<kotlin.reflect.KClass<*>> = [])
@Target(AnnotationTarget.FUNCTION) annotation class TypeConverter
object OnConflictStrategy {
    const val REPLACE = 1
    const val ABORT = 3
    const val IGNORE = 5
}
open class RoomDatabase
suspend inline fun <R> RoomDatabase.withTransaction(crossinline block: suspend () -> R): R = block()
EOF

# ExifInterface is used through a very small surface: two getters and a handful of tag
# constants. Stubbing it brings the whole indexing path into the type check; the real
# tag *parsing* logic is separately exercised by the pure-logic harness.
cat > "$TC/stubs/ExifInterface.kt" <<'EOF'
package androidx.exifinterface.media

import java.io.InputStream

class ExifInterface(input: InputStream) {
    fun getAttribute(tag: String): String? = null
    fun getAttributeInt(tag: String, defaultValue: Int): Int = defaultValue
    fun getAttributeDouble(tag: String, defaultValue: Double): Double = defaultValue
    val latLong: DoubleArray? get() = null

    companion object {
        const val TAG_DATETIME = "DateTime"
        const val TAG_DATETIME_ORIGINAL = "DateTimeOriginal"
        const val TAG_OFFSET_TIME = "OffsetTime"
        const val TAG_OFFSET_TIME_ORIGINAL = "OffsetTimeOriginal"
        const val TAG_IMAGE_DESCRIPTION = "ImageDescription"
        const val TAG_IMAGE_WIDTH = "ImageWidth"
        const val TAG_IMAGE_LENGTH = "ImageLength"
        const val TAG_ORIENTATION = "Orientation"
        const val ORIENTATION_UNDEFINED = 0
    }
}
EOF

cat > "$TC/stubs/AndroidCache.kt" <<'EOF'
package android.content

import java.io.File

open class Context {
    open val filesDir: File = File(System.getProperty("java.io.tmpdir"))
}
EOF

cat > "$TC/stubs/Bitmap.kt" <<'EOF'
package android.graphics

import java.io.OutputStream

open class Bitmap {
    open val isRecycled: Boolean = false
    open fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean = true
    enum class CompressFormat { JPEG }
}

object BitmapFactory {
    class Options {
        var inJustDecodeBounds: Boolean = false
        var outWidth: Int = 1
        var outHeight: Int = 1
    }
    fun decodeFile(path: String, options: Options): Bitmap? = null
}
EOF

# kotlinx-serialization ships only its *compiler plugin* in the Kotlin distribution, not
# its runtime, so the settings layer needs stubs. These cover exactly the surface used:
# the annotations, and Json encode/decode. Enough to type-check the settings data model
# and the config-transfer logic that handles secrets.
cat > "$TC/stubs/Serialization.kt" <<'EOF'
package kotlinx.serialization

import kotlin.reflect.KClass
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Serializable(val with: KClass<*> = Nothing::class)
@Target(AnnotationTarget.PROPERTY) annotation class SerialName(val value: String)
@Target(AnnotationTarget.PROPERTY) annotation class Transient
interface KSerializer<T> {
    val descriptor: SerialDescriptor
    fun serialize(encoder: Encoder, value: T)
    fun deserialize(decoder: Decoder): T
}
EOF

cat > "$TC/stubs/SerializationDescriptors.kt" <<'EOF'
package kotlinx.serialization.descriptors

interface SerialDescriptor
class PrimitiveSerialDescriptor(val name: String, val kind: PrimitiveKind) : SerialDescriptor
enum class PrimitiveKind { STRING }
EOF

cat > "$TC/stubs/SerializationEncoding.kt" <<'EOF'
package kotlinx.serialization.encoding

interface Encoder { fun encodeString(value: String) }
interface Decoder { fun decodeString(): String }
EOF

cat > "$TC/stubs/Json.kt" <<'EOF'
package kotlinx.serialization.json

class JsonBuilder {
    var ignoreUnknownKeys: Boolean = false
    var encodeDefaults: Boolean = false
    var prettyPrint: Boolean = false
    var isLenient: Boolean = false
    var explicitNulls: Boolean = true
    var coerceInputValues: Boolean = false
}

open class Json {
    inline fun <reified T> encodeToString(value: T): String = ""
    inline fun <reified T> decodeFromString(string: String): T = throw NotImplementedError()

    companion object Default : Json()
}

fun Json(from: Json = Json.Default, builderAction: JsonBuilder.() -> Unit): Json {
    JsonBuilder().builderAction()
    return Json.Default
}
EOF

# ---- real sources ----------------------------------------------------------
mkdir -p "$TC/src"
copy() { mkdir -p "$TC/src/$(dirname "$1")"; cp "$SRC/$1" "$TC/src/$1"; }

copy data/db/PhotoItemEntity.kt
copy data/db/FolderSummary.kt
copy data/db/PhotoDao.kt
copy data/db/FolderSelectionSql.kt
copy data/db/Phase1Entities.kt
copy data/db/Phase1Daos.kt
copy data/db/LocalThumbnailCacheEntity.kt
copy data/db/LocalThumbnailCacheDao.kt
copy data/db/ShufflePhotoRow.kt
copy data/db/ShuffleEntities.kt
copy data/db/ShuffleDao.kt
copy data/diagnostics/DiagnosticsLog.kt
copy data/diagnostics/DiagnosticsJsonl.kt
copy data/diagnostics/FileDiagnosticsSink.kt
copy data/diagnostics/DiagnosticEventSpec.kt
copy data/diagnostics/DiagnosticOperationTracker.kt
copy data/diagnostics/DiagnosticIdentityHasher.kt
copy data/diagnostics/DiagnosticPrivacyPolicy.kt
copy data/diagnostics/DiagnosticRateController.kt
copy data/diagnostics/DiagnosticsHealthSnapshot.kt
copy data/diagnostics/DiagnosticRuntimeState.kt
copy data/diagnostics/DiagnosticsBundle.kt
copy data/diagnostics/CrashEnvelopeStore.kt
copy data/diagnostics/MainThreadStallDetector.kt
copy data/diagnostics/ProcessExitReasonMapper.kt
copy data/diagnostics/RuntimeResourceTracker.kt
copy data/source/BuiltInSourceIds.kt
copy domain/randomize/PlaybackQueue.kt
copy domain/engine/EngineState.kt
copy domain/engine/DecodeFailure.kt
copy domain/engine/DecodeSuppressionPolicy.kt
copy domain/engine/RecoveryPolicy.kt
copy domain/engine/PlaybackMemoryPolicy.kt
copy domain/engine/PlaybackMemoryGuard.kt
copy domain/engine/PlaybackPoolCachePolicy.kt
copy domain/engine/SourcePoolPolicy.kt
copy domain/engine/SlideshowEngine.kt
copy slideshow/shuffle/ShuffleModels.kt
copy slideshow/shuffle/ShuffleRandom.kt
copy slideshow/shuffle/ShuffleCycleGenerator.kt
copy slideshow/shuffle/ShuffleScopeKeyFactory.kt
copy slideshow/shuffle/SourceAvailabilityTracker.kt
copy slideshow/shuffle/BoundedScopeLogTracker.kt
copy slideshow/shuffle/ShuffleEligibilityProvider.kt
copy slideshow/shuffle/ShuffleRepository.kt
copy slideshow/shuffle/FolderBalancedShuffleCoordinator.kt

cat > "$TC/src/data/db/AppDatabase.kt" <<'EOF'
package com.example.familyphotoframe.data.db
class AppDatabase(
    private val shuffle: ShuffleDao,
) : androidx.room.RoomDatabase() {
    fun shuffleDao(): ShuffleDao = shuffle
}
EOF

# Everything below is likewise Android-free once Room is stubbed. The settings layer is
# deliberately absent: it needs the kotlinx-serialization *runtime*, and the Kotlin
# distribution ships only the compiler plugin.
copy data/source/PhotoSource.kt
copy data/source/DeadlineInputStream.kt
copy data/source/DeferredCloseResource.kt
copy data/source/WebDavApi.kt
copy data/source/CertPinning.kt
copy data/source/WebDavPhotoSource.kt
copy data/source/SynologyApi.kt
copy data/source/SynologyFileStationSource.kt
copy data/cache/CancellableStreamCopy.kt
copy data/cache/MediaCache.kt
copy data/cache/LocalThumbnailCache.kt
copy data/index/CanonicalPhotoPath.kt
copy data/index/Indexer.kt
copy data/index/ScanCompletionPolicy.kt
copy data/index/ContentHashBackfiller.kt
copy data/index/ScanMergePolicy.kt
copy data/index/ExifExtractor.kt
copy domain/schedule/SleepSchedule.kt
copy util/Glob.kt
copy util/StableId.kt
copy util/ImageFormatSupport.kt
copy util/Hex.kt
copy data/weather/Weather.kt
copy data/settings/AppSettings.kt
copy data/settings/AppSettingsCanonicalizer.kt
copy data/settings/PlaybackInterval.kt
copy data/settings/CredentialPolicy.kt
mkdir -p "$TC/src/data/source"
cp scripts/typecheck/SourceLifecycleChecks.kt "$TC/src/data/source/SourceLifecycleChecks.kt"

# The offline compiler bundle can lag the Gradle toolchain. Patch only the temporary
# copies so its older stdlib/coroutines API can type-check the same production logic.
for file in PlaybackQueue.kt; do
  sed -i '/^package com.example.familyphotoframe.domain.randomize/a import java.util.ArrayDeque'     "$TC/src/domain/randomize/$file"
done
sed -i '/^package com.example.familyphotoframe.domain.engine/a import java.util.ArrayDeque'   "$TC/src/domain/engine/SlideshowEngine.kt"
sed -i 's/backStack.removeLastOrNull()/backStack.pollLast()/; s/forwardStack.removeLastOrNull()/forwardStack.pollLast()/'   "$TC/src/domain/engine/SlideshowEngine.kt"
sed -i '/^package com.example.familyphotoframe.data.index/a import kotlinx.coroutines.flow.collect'   "$TC/src/data/index/Indexer.kt"
# ConfigTransfer and PortableBundle are deliberately excluded: both call the
# `.serializer()` functions that the kotlinx-serialization *compiler plugin* generates,
# which these runtime stubs cannot model. Including them produced errors that were
# artifacts of the stubs rather than real defects, and the right response to that is to
# exclude the files -- not to reshape working code to satisfy a fake dependency. Their
# credential-scoping logic is covered instead by the pure-logic harness (CredentialPolicy)
# and by ConfigTransferTest under Gradle.

# SelectionMode now arrives with the real AppSettings.kt copied above.

echo "==> Type-checking engine + persistence layer against stubbed Room"
"$KOTLINC" -classpath "$COROUTINES" -nowarn \
  "$TC/stubs" "$TC/src" -d "$TC/out" 2>&1 | tee "$TC/log" | grep -E "error:|warning: unre" || true

if grep -q "error:" "$TC/log"; then
  echo
  echo "TYPE CHECK FAILED"
  exit 1
fi
echo "    no type errors"
"$(dirname "$KOTLINC")/kotlin" -classpath "$TC/out:$COROUTINES" \
  com.example.familyphotoframe.data.source.SourceLifecycleChecksKt
echo
echo "ALL TYPE CHECKS PASSED"
