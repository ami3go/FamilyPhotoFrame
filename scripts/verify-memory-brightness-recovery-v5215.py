#!/usr/bin/env python3
"""Release contracts for v52.15 memory recovery and scheduled brightness fixes."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


build = read("app/build.gradle.kts")
assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")
app = read("app/src/main/java/com/example/familyphotoframe/App.kt")
memory_policy = read("app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackMemoryPolicy.kt")
self_recovery = read("app/src/main/java/com/example/familyphotoframe/domain/engine/MemorySelfRecoveryPolicy.kt")
brightness_policy = read("app/src/main/java/com/example/familyphotoframe/domain/schedule/BrightnessPolicy.kt")
brightness_timeline = read("app/src/main/java/com/example/familyphotoframe/domain/schedule/BrightnessTimeline.kt")
settings_ui = read("app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsDisplaySections.kt")
vm = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
catalog = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt")
pure_gate = read("scripts/verify-pure-logic.sh")
memory_checks = read("scripts/verify/MemorySelfRecoveryChecks.kt")
brightness_checks = read("scripts/verify/BrightnessTimelineChecks.kt")
brightness_junit = read("app/src/test/java/com/example/familyphotoframe/domain/schedule/BrightnessPolicyTest.kt")
notes = read("docs/FamilyPhotoFrame_v52.15_Memory_Brightness_Recovery.md")

checks = {
    "v52.15 metadata and web revision":
        "versionCode = 33" in build and "0.12.13-prerelease" in build and
        'REVISION: String = "v52150"' in assets,
    "expired OOM circuit admits conservative recovery below critical pressure":
        "previous.level == PlaybackMemoryLevel.CIRCUIT_OPEN" in memory_policy and
        "percent >= CRITICAL_ENTER_PERCENT -> PlaybackMemoryLevel.CIRCUIT_OPEN" in memory_policy and
        "val allowSelectedDecode: Boolean get() = level != PlaybackMemoryLevel.CIRCUIT_OPEN" in memory_policy,
    "legacy watchdog requires recent real OOM and critical heap":
        "oomAgeMs in 0L..OOM_ELIGIBILITY_WINDOW_MS" in self_recovery and
        "pressurePercent >= RESTART_PRESSURE_PERCENT" in self_recovery and
        "SUSTAINED_PRESSURE_BEFORE_GC_MS = 60_000L" in self_recovery and
        "POST_GC_VERIFY_MS = 10_000L" in self_recovery,
    "process recovery is confined to API 21-25 and restart-loop guarded":
        "LEGACY_RECOVERY_MIN_SDK = 21" in app and "LEGACY_RECOVERY_MAX_SDK = 25" in app and
        "MEMORY_RESTART_MIN_INTERVAL_MS = 15L * 60_000L" in app and
        "setExactAndAllowWhileIdle" in app and "Process.killProcess(Process.myPid())" in app,
    "memory restart evidence survives the process boundary":
        "KEY_MEMORY_RESTART_PENDING" in app and "reportCompletedMemoryProcessRecovery" in app and
        all(code in catalog for code in [
            "MEMORY_SELF_RECOVERY_GC", "MEMORY_PROCESS_RESTART_SCHEDULED",
            "MEMORY_PROCESS_RESTART_SUPPRESSED", "MEMORY_PROCESS_RESTART_FAILED",
            "MEMORY_PROCESS_RECOVERY_COMPLETED",
        ]),
    "brightness timeline ignores malformed persisted times":
        "mapIndexedNotNull" in brightness_timeline and "if (valid.isEmpty()) return null" in brightness_timeline and
        "BrightnessTimeline.activeIndex" in brightness_policy and
        "?: return Decision(normalized.manualBrightness" in brightness_policy,
    "on-device brightness time edit persists as soon as HH:mm is valid":
        "candidate.length == 5" in settings_ui and "SleepSchedule.parseMinutes(candidate) != null" in settings_ui and
        "vm.setBrightnessPeriod(index, candidate" in settings_ui,
    "ViewModel refuses malformed brightness time persistence":
        "val startMinutes = SleepSchedule.parseMinutes(startTime) ?: return" in vm and
        "startTime = normalizedStart" in vm,
    "both fixes execute in the offline regression gate":
        "MemorySelfRecoveryPolicy.kt" in pure_gate and "BrightnessTimeline.kt" in pure_gate and
        "pinned post-GC heap requests process restart" in memory_checks and
        "all-invalid schedule safely has no active period" in brightness_checks,
    "brightness policy has Android/JUnit regression coverage":
        "allInvalidTimesFallBackWithoutCrashingWatcher" in brightness_junit and
        "scheduleChangesExactlyAtPeriodBoundary" in brightness_junit,
    "release notes preserve the field-qualification boundary":
        all(text in notes for text in [
            "v52.15", "CIRCUIT_OPEN", "scheduled brightness", "API 21–25",
            "underlying long-run heap retention", "prerelease",
        ]),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")
if failed:
    print(f"v52.15 memory/brightness recovery verification failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)
print(f"v52.15 memory/brightness recovery verification: PASS ({len(checks)} checks)")
