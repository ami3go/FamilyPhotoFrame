#!/usr/bin/env python3
"""Static contracts for Build 87's Phase-4 closure candidate."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    failures: list[str] = []
    build = (ROOT / "app/build.gradle.kts").read_text()
    policy = (ROOT / "app/src/main/java/com/example/familyphotoframe/domain/engine/LegacyBitmapHeapMaintenancePolicy.kt").read_text()
    app = (ROOT / "app/src/main/java/com/example/familyphotoframe/App.kt").read_text()
    catalog = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt").read_text()
    log = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsLog.kt").read_text()
    bundle = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsBundle.kt").read_text()
    boot = (ROOT / "app/src/main/java/com/example/familyphotoframe/platform/BootReceiver.kt").read_text()

    checks = {
        "install-distinct versionCode 87": "val buildNumber = 87" in build,
        "critical exception is native-growth-specific": (
            "memoryPressureSource == PlaybackMemoryPressureSource.NATIVE_PSS_GROWTH" in policy
            and "MAX_CRITICAL_HEAP_OCCUPANCY_PERCENT = 70" in policy
        ),
        "critical exception retains ownership and OOM fences": (
            "oomCount == 0L" in policy and "pendingDisposals == 0" in policy
            and "activeMediaTransfers == 0" in policy
        ),
        "live controller source reaches policy": (
            "memoryPressureSource = protection.pressureSource" in app
        ),
        "boot model uses registered privacy key": (
            '"deviceModel" to' in boot and '"device" to' not in boot
        ),
        "observed collage decision field is registered": (
            '"collageFillWithOtherOrientations"' in catalog
        ),
        "folder retry counter is registered": '"retryCount"' in catalog,
        "boot network events are registered": (
            '"SOURCE_HEALTH_DEFERRED_NETWORK"' in catalog
            and '"SOURCE_HEALTH_NETWORK_GATE_RELEASED"' in catalog
        ),
        "rejected code-key evidence is bounded and exported": (
            "MAX_REJECTION_SIGNATURES = 64" in log
            and "MAX_REJECTION_SUMMARY_LENGTH = 4_096" in log
            and '"fieldRejectionSummary" to health.fieldRejectionSummary' in bundle
        ),
    }
    failures.extend(label for label, passed in checks.items() if not passed)
    if failures:
        print("BUILD 87 PHASE 4 CLOSURE CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("Build 87 Phase 4 closure contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
