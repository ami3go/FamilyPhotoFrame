#!/usr/bin/env python3
"""Static contracts for Build 88's fallback-selection closure candidate."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    failures: list[str] = []
    build = (ROOT / "app/build.gradle.kts").read_text()
    engine = (ROOT / "app/src/main/java/com/example/familyphotoframe/domain/engine/SlideshowEngine.kt").read_text()
    probe = (ROOT / "app/src/main/java/com/example/familyphotoframe/domain/engine/PlaybackPoolProbePolicy.kt").read_text()
    test = (ROOT / "app/src/test/java/com/example/familyphotoframe/domain/engine/PlaybackPoolProbePolicyTest.kt").read_text()

    checks = {
        "install-distinct versionCode 88": "val buildNumber = 88" in build,
        "engine delegates primary eligibility to bounded policy": (
            "PlaybackPoolProbePolicy.shouldProbePrimary(primaryIds.size)" in engine
        ),
        "empty primary no longer probes unavailable history": (
            "selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE && unavailableSourceIds.isNotEmpty()"
            not in engine
        ),
        "probe requires configured primary membership": (
            "configuredPrimarySourceCount > 0" in probe
        ),
        "fallback-only regression is executable": (
            "fallback-only configuration does not probe stale primary history" in test
            and "configuredPrimarySourceCount = 0" in test
        ),
        "healthy and stale-cache primary remain eligible": (
            "healthy or stale-cache primary remains eligible" in test
            and "configuredPrimarySourceCount = 1" in test
        ),
    }
    failures.extend(label for label, passed in checks.items() if not passed)
    if failures:
        print("BUILD 88 PHASE 4 CLOSURE CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("Build 88 Phase 4 closure contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
