#!/usr/bin/env python3
"""Focused Phase-4 contracts for runtime, activity, surface, and fullscreen context."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/com/example/familyphotoframe"
APP = (BASE / "App.kt").read_text(encoding="utf-8")
MAIN = (BASE / "MainActivity.kt").read_text(encoding="utf-8")
ACTIVITY_DIAGNOSTICS = (BASE / "ActivityDiagnosticsController.kt").read_text(encoding="utf-8")
IMMERSIVE = (BASE / "ImmersiveModeController.kt").read_text(encoding="utf-8")
VM = (BASE / "ui/slideshow/SlideshowViewModel.kt").read_text(encoding="utf-8")


def main() -> int:
    failures: list[str] = []

    def need(text: str, value: str, label: str) -> None:
        if value not in text:
            failures.append(f"missing {label}: {value}")

    for field in (
        "appVersion", "versionCode", "buildType", "sdkInt", "deviceModel",
        "processId", "heapMaxKb", "memoryClassMb", "lowRam", "imageCacheMaxKb",
        "retainedBytes", "queueDepth", "droppedTotal", "sinkStatus",
    ):
        need(APP, f'"{field}"', f"SESSION_START field {field}")
    need(APP, '"SESSION_START"', "canonical session event")
    need(VM, '"RUNTIME_CONTEXT_READY"', "background runtime context")
    for field in ("sourceKind", "indexedCount", "configRevision", "engineState"):
        need(VM, f'"{field}"', f"runtime context field {field}")

    lifecycle = {
        "ACTIVITY_CREATED", "ACTIVITY_STARTED", "ACTIVITY_RESUMED",
        "ACTIVITY_PAUSED", "ACTIVITY_STOPPED", "ACTIVITY_DESTROYED",
        "SLIDESHOW_SURFACE_CHANGED", "ENGINE_STATE_CHANGED",
        "SCREEN_INTERACTIVE_CHANGED", "DISPLAY_CONFIGURATION_CHANGED",
    }
    for code in sorted(lifecycle):
        need(ACTIVITY_DIAGNOSTICS, f'"{code}"', f"lifecycle event {code}")
    for callback in ("onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"):
        need(MAIN, f"override fun {callback}", f"activity callback {callback}")
    need(MAIN, "ActivityDiagnosticsController(this, services)", "activity diagnostics owner")
    need(ACTIVITY_DIAGNOSTICS, "if (lastActivityState == state) return", "activity state dedup")
    need(ACTIVITY_DIAGNOSTICS, "if (previous == current) return", "surface state dedup")
    need(ACTIVITY_DIAGNOSTICS, "if (previous == state) return", "engine state dedup")
    need(ACTIVITY_DIAGNOSTICS, "if (lastInteractive == interactive) return", "interactive state dedup")
    need(ACTIVITY_DIAGNOSTICS, "if (lastDisplaySignature == signature) return", "display state dedup")
    for surface in (
        "FIRST_RUN", "RECOVERY", "EMPTY_INDEX", "PLAYING", "SETTINGS", "BLACK_SCREEN",
    ):
        need(ACTIVITY_DIAGNOSTICS, f'"{surface}"', f"surface {surface}")
    need(ACTIVITY_DIAGNOSTICS, "ContextCompat.registerReceiver", "API-safe screen receiver")

    immersive_codes = {
        "IMMERSIVE_REQUESTED", "IMMERSIVE_APPLIED", "IMMERSIVE_LOST",
        "IMMERSIVE_RETRY_SCHEDULED", "IMMERSIVE_RESTORED", "IMMERSIVE_EXITED_BY_USER",
    }
    for code in sorted(immersive_codes):
        need(IMMERSIVE, f'"{code}"', f"immersive event {code}")
    for field in ("reason", "activityState", "systemUiFlags", "retryCount", "durationMs", "legacyPath"):
        need(IMMERSIVE, f'"{field}"', f"immersive field {field}")
    need(IMMERSIVE, "barsLostAtMs == null", "visibility callback dedup")
    need(IMMERSIVE, "if (retryScheduled) return", "retry callback dedup")
    need(IMMERSIVE, "Build.VERSION.SDK_INT < 30", "legacy path marker")
    need(MAIN, "immersiveMode.onWindowFocusChanged(hasFocus)", "focus-return recovery")

    if failures:
        print("V52.9 LIFECYCLE LOGGING CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print(f"  note: {len(lifecycle)} lifecycle and {len(immersive_codes)} immersive events verified")
    print("  v52.9 runtime/lifecycle/fullscreen contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
