#!/usr/bin/env python3
"""Contracts for v52.14 lifecycle, PIN-threading, and power-telemetry fixes."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


build = read("app/build.gradle.kts")
assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")
gate = read("app/src/main/java/com/example/familyphotoframe/domain/engine/HostLifecycleGate.kt")
gate_test = read("app/src/test/java/com/example/familyphotoframe/domain/engine/HostLifecycleGateTest.kt")
screen = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt")
vm = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
ui_state = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowUiState.kt")
settings_ui = read("app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsDeviceSections.kt")
registry = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/PreparedSlideMemory.kt")
security = read("app/src/main/java/com/example/familyphotoframe/web/WebSecurity.kt")
battery = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/BatteryTelemetry.kt")
locator = read("app/src/main/java/com/example/familyphotoframe/ServiceLocator.kt")
catalog = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt")
soak = read("docs/API22_MEMORY_SOAK_TEST.md")
notes = read("docs/FamilyPhotoFrame_v52.14_Stability_Soak_Fixes.md")

stale_transition = screen.index('"host_stopped"')
normal_transition_commit = screen.index("committedHandle = targetHandle", stale_transition)

checks = {
    "v52.14 metadata and web cache revision":
        "versionCode = 33" in build and "0.12.13-prerelease" in build and
        'REVISION: String = "v52150"' in assets,
    "host lifecycle uses an advancing generation":
        "generation++" in gate and "fun tokenIfActive(): Long?" in gate and
        "fun isCurrent(token: Long)" in gate,
    "ViewModel invalidates generation at Activity boundaries":
        "hostLifecycleGate.start()" in vm and "hostLifecycleGate.stop()" in vm and
        "val hostGeneration: StateFlow<Long>" in vm and
        vm.index("hostLifecycleGate.stop()") < vm.index("_hostActive.value = false"),
    "generation semantics have a real JUnit regression":
        "stopAndRestartNeverRevalidateOldWork" in gate_test and
        "assertFalse(gate.isCurrent(first))" in gate_test,
    "selected and preload results revalidate after blocking work":
        screen.count("currentCoroutineContext().isActive") >= 5 and
        screen.count("isHostPlaybackTokenCurrent(lifecycleToken)") >= 8 and
        screen.count("hostGeneration,") >= 4,
    "stale decoded bitmap graphs are explicitly retired":
        "fun retireUnowned(slide: PreparedSlide)" in registry and
        screen.count("registry.retireUnowned(result.slide)") >= 2,
    "Activity stop cannot use the non-cancellable transition commit":
        "hostActive," in screen and stale_transition < normal_transition_commit and
        'event(\n                            "TRANSITION_CANCELLED",\n                            "host_stopped"' in screen,
    "PIN regeneration is off-main and single-flight":
        "webPinRegenerationInFlight.compareAndSet(false, true)" in vm and
        "withContext(services.dispatchers.default)" in vm and
        "services.webServer.regeneratePin" in vm,
    "PIN button reports and blocks in-flight work":
        "webPinRegenerationInProgress" in ui_state and
        "enabled = !state.webPinRegenerationInProgress" in settings_ui and
        "web_new_pin_working" in settings_ui,
    "PIN hash replacement is publish-after-derive and clears password material":
        security.index("val newHash = hashPin(pin, salt)") < security.index("pinSalt = salt") and
        "spec.clearPassword()" in security,
    "battery telemetry queries the API-21-compatible system state":
        "Intent.ACTION_BATTERY_CHANGED" in battery and
        "BATTERY_PROPERTY_CURRENT_NOW" in battery and
        "BATTERY_PROPERTY_CHARGE_COUNTER" in battery,
    "battery telemetry rides the existing bounded runtime sample":
        "putAll(batteryTelemetry.fields())" in locator and
        all(field in catalog for field in [
            "batteryTelemetryStatus", "batteryLevelPct", "batteryStatus", "powerSource",
            "batteryVoltageMv", "batteryCurrentUa", "batteryChargeCounterUah",
        ]),
    "soak procedure verifies all three field findings":
        all(text in soak for text in [
            "0.12.13-prerelease", "versionCode 33", "ACTIVITY_STOPPED",
            "batteryTelemetryStatus", "batteryCurrentUa", "main-thread stall",
        ]),
    "release notes preserve the qualification boundary":
        all(text in notes for text in [
            "HostLifecycleGate", "single-flight", "ACTION_BATTERY_CHANGED",
            "six-hour", "prerelease",
        ]),
}

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")
if failed:
    print(f"v52.14 stability-soak verification failed: {len(failed)} contract(s)", file=sys.stderr)
    sys.exit(1)
print(f"v52.14 stability-soak verification: PASS ({len(checks)} checks)")
