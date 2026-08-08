#!/usr/bin/env python3
"""Source-level release contracts for the API-22 memory hardening increment."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


preparation = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowPreparation.kt")
screen = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt")
preview_renderer = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/WebPreviewRenderer.kt")
memory = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/PreparedSlideMemory.kt")
policy = read("app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryPolicy.kt")
app = read("app/src/main/java/com/example/familyphotoframe/App.kt")
vm = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
runtime = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticRuntimeState.kt")
crash = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/CrashEnvelopeStore.kt")
catalog = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt")
build = read("app/build.gradle.kts")
assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")

checks = {
    "request construction is inside OOM boundary":
        preparation.index("return try {") < preparation.index("val request = ImageRequest.Builder"),
    "whole preparation path catches OOM":
        "java_heap_exhausted_during_slide_preparation" in preparation,
    "temporary decode ownership is explicit":
        "val uncommitted = Collections.newSetFromMap" in preparation and
        "releaseUncommitted()" in preparation and "fun discard(tile: PreparedTile)" in preparation,
    "candidate OOM stops the collage loop":
        "return memoryFallback(\"candidate_decode_oom\")" in preparation and
        "return memoryFallback(\"candidate_model_oom\")" in preparation,
    "bitmap payloads are outside Compose snapshot state":
        "PreparedSlideRegistry" in screen and
        "mutableStateMapOf<Long, PreparedSlide>" not in screen and
        "mutableStateOf<PreparedSlide?>" not in screen,
    "cancelled selected preparation retires orphaned registry entry":
        "var uncommittedHandle: PreparedSlideHandle? = null" in screen and
        "uncommittedHandle?.let(registry::remove)" in screen,
    "legacy rendered bitmaps use delayed recycle":
        "sdkInt !in LEGACY_MIN_SDK..LEGACY_MAX_SDK" in memory and
        "handler.postDelayed" in memory and "DEFAULT_GRACE_MS" in memory,
    "guard enters at 85 percent":
        "GUARDED_ENTER_PERCENT = 85" in policy,
    "critical guard enters at 92 percent":
        "CRITICAL_ENTER_PERCENT = 92" in policy,
    "OOM circuit has bounded exponential backoff":
        "FIRST_OOM_COOLDOWN_MS = 60_000L" in policy and
        "MAX_OOM_COOLDOWN_MS = 5L * 60_000L" in policy,
    "circuit remains open while heap stays high":
        "previous.level == PlaybackMemoryLevel.CIRCUIT_OPEN" in policy and
        "percent >= GUARDED_ENTER_PERCENT" in policy,
    "weaker system pressure cannot downgrade OOM circuit":
        "nowElapsedMs < previous.circuitOpenUntilElapsedMs" in policy and
        "previous.level == PlaybackMemoryLevel.CRITICAL" in policy,
    "pressure disables next preload":
        "if (!memoryProtection.allowNextPreload)" in screen,
    "pressure bounds three-photo collage":
        "memoryProtection.maxCollagePhotos" in screen and "maxCollagePhotos: Int get()" in policy,
    "pressure scales decode target":
        "memoryProtection.decodeScale" in screen,
    "pressure suppresses blur and preview":
        "memoryProtection.allowBlurredBackdrop" in screen and
        "memoryProtection.allowWebPreview" in screen,
    "web preview allocation path is OOM-bounded":
        "catch (_: OutOfMemoryError)" in preview_renderer and
        "onOutOfMemory()" in preview_renderer and
        "output?.takeIf { !it.isRecycled }?.recycle()" in preview_renderer,
    "one GC follows reference pruning":
        screen.index("registry.retain(") < screen.index("Runtime.getRuntime().gc()"),
    "application samples the guard every pressure tick":
        "playbackMemoryGuard.recordHeap" in app,
    "OOM immediately opens the shared guard":
        "playbackMemoryGuard.recordDecodeOom" in vm,
    "OOM is not recorded as a defective photo":
        'if (failure.exceptionClass == "OutOfMemoryError")' in vm and
        "Keep the current frame and reservation intact" in vm and
        "Do not count a healthy candidate as failed" in vm,
    "runtime captures bitmap ownership":
        "data class BitmapInventory" in runtime and "updateBitmapInventory" in runtime,
    "crash envelope retains bitmap ownership":
        all(field in crash for field in [
            "preparedSlideCount", "decodedBitmapCount", "activeDecodedBytes",
            "pendingDisposals", "memoryProtectionLevel", "oomCount",
        ]),
    "memory protection events are registered":
        "MEMORY_PROTECTION_CHANGED" in catalog and "MEMORY_CLEANUP_REQUESTED" in catalog,
    "current release metadata is consistent":
        "versionCode = 33" in build and "0.12.13-prerelease" in build and
        'REVISION: String = "v52150"' in assets,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"  {'PASS' if ok else 'FAIL'}  {name}")
if failed:
    print(f"memory hardening verification failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)
print(f"  {len(checks)} API-22 memory-hardening contracts passed")
