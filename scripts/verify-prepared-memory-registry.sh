#!/usr/bin/env bash
# Compile and execute the real PreparedSlide registry/reclaimer against tiny Android stubs.
set -euo pipefail
cd "$(dirname "$0")/.."

WORK="${TMPDIR:-/tmp}/ffv/prepared-memory"
mkdir -p "$WORK/stubs"

if [ -n "${KOTLINC:-}" ] && [ -x "$KOTLINC" ]; then
  :
elif command -v kotlinc >/dev/null 2>&1; then
  KOTLINC="$(command -v kotlinc)"
elif [ -x "${TMPDIR:-/tmp}/ffv/kotlinc/bin/kotlinc" ]; then
  KOTLINC="${TMPDIR:-/tmp}/ffv/kotlinc/bin/kotlinc"
else
  echo "Kotlin compiler unavailable; run scripts/verify-pure-logic.sh first" >&2
  exit 1
fi

cat > "$WORK/stubs/Bitmap.kt" <<'KT'
package android.graphics
open class Bitmap(
    val allocationByteCount: Int = 4,
    val byteCount: Int = allocationByteCount,
) {
    var isRecycled: Boolean = false
    fun recycle() { isRecycled = true }
}
KT

cat > "$WORK/stubs/Handler.kt" <<'KT'
package android.os
class Handler(private val acceptsTasks: Boolean = true) {
    val tasks = mutableListOf<Runnable>()
    fun postDelayed(task: Runnable, delayMs: Long): Boolean {
        if (!acceptsTasks) return false
        tasks += task
        return true
    }
}
KT

cat > "$WORK/stubs/PreparedModels.kt" <<'KT'
package com.example.familyphotoframe.ui.slideshow
import android.graphics.Bitmap

data class Photo(val id: Long)
data class PreparedTile(val bitmap: Bitmap)
sealed interface PreparedSlide {
    val anchor: Photo
    val photos: List<Photo>
    val transitionBlurredBitmap: Bitmap?
    data class Single(
        override val anchor: Photo,
        val bitmap: Bitmap,
        override val transitionBlurredBitmap: Bitmap? = null,
    ) : PreparedSlide { override val photos = listOf(anchor) }
    data class Collage(
        override val anchor: Photo,
        val tiles: List<PreparedTile>,
        override val transitionBlurredBitmap: Bitmap? = null,
    ) : PreparedSlide { override val photos = List(tiles.size) { Photo(anchor.id + it) } }
}
KT

cat > "$WORK/stubs/Checks.kt" <<'KT'
package com.example.familyphotoframe.ui.slideshow
import android.graphics.Bitmap
import android.os.Handler
import com.example.familyphotoframe.data.diagnostics.BitmapLifecycleTracker

fun main() {
    val handler = Handler()
    val tracker = BitmapLifecycleTracker()
    var now = 100L
    val pendingChanges = mutableListOf<PendingBitmapDisposals>()
    val reclaimer = LegacyBitmapReclaimer(
        22,
        handler,
        tracker,
        graceMs = 10,
        elapsedRealtimeMs = { now },
        onPendingChanged = pendingChanges::add,
    )
    val registry = PreparedSlideRegistry(reclaimer::retire)
    val oldBitmap = Bitmap(100)
    val currentBitmap = Bitmap(200)
    tracker.recordAllocation(BitmapLifecycleTracker.Kind.DECODED, 100)
    tracker.recordAllocation(BitmapLifecycleTracker.Kind.DECODED, 200)
    val old = registry.put(PreparedSlide.Single(Photo(1), oldBitmap))
    val current = registry.put(PreparedSlide.Single(Photo(2), currentBitmap))
    check(old != current)
    check(registry.latest(2) == current)
    registry.retain(setOf(current))
    check(registry.size == 1)
    check(reclaimer.pendingBitmapCount() == 1)
    check(reclaimer.pendingDisposals().oldestStartedAtElapsedMs == 100L)
    check(pendingChanges.last().count == 1)
    check(!oldBitmap.isRecycled)
    handler.tasks.forEach(Runnable::run)
    check(oldBitmap.isRecycled)
    check(pendingChanges.last().count == 0)
    check(tracker.snapshot().activeCount == 1)
    check(!currentBitmap.isRecycled)
    val bounded = PreparedSlideRegistry(reclaimer::retire, maxEntries = 2)
    val protectedBitmap = Bitmap(10)
    val secondBitmap = Bitmap(11)
    val thirdBitmap = Bitmap(12)
    listOf(protectedBitmap, secondBitmap, thirdBitmap).forEach {
        tracker.recordAllocation(BitmapLifecycleTracker.Kind.DECODED, it.allocationByteCount.toLong())
    }
    val protected = bounded.put(PreparedSlide.Single(Photo(10), protectedBitmap))
    bounded.put(PreparedSlide.Single(Photo(11), secondBitmap), setOf(protected))
    bounded.put(PreparedSlide.Single(Photo(12), thirdBitmap), setOf(protected))
    check(bounded.size == 2)
    check(bounded.get(protected) != null)
    val failures = BoundedLongSet(2)
    failures += 1L; failures += 2L; failures += 3L
    check(failures.size == 2 && 1L !in failures)
    val rejectedBitmap = Bitmap(20)
    val rejectingTracker = BitmapLifecycleTracker().also {
        it.recordAllocation(BitmapLifecycleTracker.Kind.GENERATED, 20)
    }
    val rejectingReclaimer = LegacyBitmapReclaimer(
        22,
        Handler(false),
        rejectingTracker,
        graceMs = 10,
    )
    rejectingReclaimer.retireDisplayBitmap(rejectedBitmap)
    check(rejectedBitmap.isRecycled)
    check(rejectingReclaimer.pendingBitmapCount() == 0)
    check(rejectingTracker.snapshot().activeCount == 0)
    val inventory = registry.inventory(setOf(current), reclaimer.pendingDisposals())
    check(inventory.preparedSlideCount == 1)
    check(inventory.renderedSlideCount == 1)
    check(inventory.decodedBitmapCount == 1)
    check(inventory.activeDecodedBytes == 200L)
    val modernBitmap = Bitmap(30)
    val modernTracker = BitmapLifecycleTracker().also {
        it.recordAllocation(BitmapLifecycleTracker.Kind.GENERATED, 30)
    }
    LegacyBitmapReclaimer(30, Handler(), modernTracker).retireDisplayBitmap(modernBitmap)
    check(modernTracker.snapshot().activeCount == 0)
    check(!modernBitmap.isRecycled)
    println("PreparedSlide registry and API-22 delayed reclaim checks passed")
}
KT

"$KOTLINC" \
  app/src/main/java/com/example/familyphotoframe/data/diagnostics/BitmapLifecycleTracker.kt \
  app/src/main/java/com/example/familyphotoframe/ui/slideshow/PreparedSlideMemory.kt \
  "$WORK/stubs"/*.kt -include-runtime -d "$WORK/checks.jar" -nowarn
java -jar "$WORK/checks.jar"
