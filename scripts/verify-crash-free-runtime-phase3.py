#!/usr/bin/env python3
"""Source-level contracts for the crash-free runtime Phase 3 policy cut."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


policy = read("app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryPolicy.kt")
guard = read("app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryGuard.kt")
color = read("app/src/main/java/com/example/familyphotoframe/domain/engine/DecodeColorPolicy.kt")
screen = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt")
registry = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/PreparedSlideMemory.kt")
app = read("app/src/main/java/com/example/familyphotoframe/App.kt")
locator = read("app/src/main/java/com/example/familyphotoframe/ServiceLocator.kt")
catalog = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt")
manifest = read("app/src/main/AndroidManifest.xml")
build = read("app/build.gradle.kts")
notes = read("docs/CRASH_FREE_RUNTIME_PHASE3_MEMORY_POLICY.md")

system_pressure = policy[
    policy.index("fun systemPressure(") : policy.index("private fun externalSignal(")
]

checks = {
    "real decode OOM exclusively owns the circuit deadline":
        "fun decodeOom(" in policy and "circuitOpenUntilElapsedMs = nowElapsedMs + cooldown" in policy and
        "circuitOpenUntilElapsedMs =" not in system_pressure,
    "platform pressure has sticky critical and guarded holds":
        "EXTERNAL_CRITICAL_HOLD_MS = 10L * 60_000L" in policy and
        "EXTERNAL_GUARDED_HOLD_MS = 15L * 60_000L" in policy,
    "fresh PSS and system headroom participate in decisions":
        "PROCESS_CRITICAL_ENTER_PERCENT = 125" in policy and
        "SYSTEM_GUARDED_HEADROOM_PERCENT = 200" in policy and
        "fun recordMemory(" in guard and "playbackMemoryGuard.recordMemory(" in app,
    "stale PSS samples cannot refresh an external hold":
        "val externalRefreshed = processPssBytes != null" in policy and
        "FULL_MEMORY_SAMPLE_FRESHNESS_MS" in app,
    "low-tier baseline disables speculative allocations":
        "level == PlaybackMemoryLevel.NORMAL && !lowMemoryTier" in policy and
        "PlaybackMemoryState(lowMemoryTier = true)" in read(
            "scripts/verify/PlaybackMemoryPolicyChecks.kt"
        ),
    "low-tier decode uses RGB565 and bounded registry":
        "lowMemoryTier -> DecodeColorChoice.RGB_565" in color and
        "PreparedSlideRegistry.LOW_MEMORY_MAX_ENTRIES" in screen and
        "LOW_MEMORY_MAX_ENTRIES = 3" in registry,
    "ordinary heap replaces the large-heap request":
        "android:largeHeap" not in manifest and "activityManager?.memoryClass" in locator,
    "policy interpretation survives the diagnostics allowlist":
        all(field in catalog for field in (
            "processMemoryBudgetKb", "processPressurePercent", "systemHeadroomPercent",
            "memoryPressureSource", "economyBaseline", "externalCriticalRemainingMs",
            "externalGuardedRemainingMs",
        )),
    "Phase 3 has an install-distinct version and qualification notes":
        "val buildNumber = 41" in build and "val testingVariant: Int? = 1" in build and
        all(term in notes for term in (
            "26.41.1", "physical-device soak pending", "CIRCUIT_OPEN", "Total process PSS",
        )),
}

for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")

failed = [name for name, passed in checks.items() if not passed]
if failed:
    print(f"Phase 3 verification failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)

print(f"Crash-free runtime Phase 3 contracts passed ({len(checks)} checks)")
