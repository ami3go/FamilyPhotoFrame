#!/usr/bin/env python3
"""Static contracts for Build 85 Phase-4 evidence closure."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    failures: list[str] = []
    build = (ROOT / "app/build.gradle.kts").read_text()
    catalog = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticEventSpec.kt").read_text()
    rate = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticRateController.kt").read_text()
    log = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsLog.kt").read_text()
    jsonl = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsJsonl.kt").read_text()
    bundle = (ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsBundle.kt").read_text()
    provider = (ROOT / "app/src/debug/java/com/example/familyphotoframe/DebugDiagnosticsProvider.kt").read_text()
    manifest = (ROOT / "app/src/debug/AndroidManifest.xml").read_text()
    merger = (ROOT / "scripts/merge-phase4-snapshots.py").read_text()

    checks = {
        "install-distinct versionCode 85": "val buildNumber = 85" in build,
        "folder skips use aggregate policy": (
            "FOLDER_SKIP_AGGREGATE" in catalog and '"FOLDER_SKIP_SUMMARY"' in catalog
            and "FIRST_FOLDER_SKIP_EVENTS = 3" in rate
        ),
        "raw scope is tokenized before validation": (
            'candidateFields["scope"]' in log and '"scopeToken" to diagnosticToken' in log
            and '"scopeToken"' in catalog
        ),
        "privacy transforms are distinct from rejected fields": (
            "val transformed: Int" in jsonl and "fieldsTransformed" in log
            and '"fieldsTransformed" to health.fieldsTransformed' in bundle
        ),
        "debug export streams the real durable bundle": (
            "openDurableBundle(" in provider and "input.copyTo(output)" in provider
            and "DiagnosticsBundleContext(" in provider
        ),
        "debug export is protected by shell DUMP permission": (
            'android:readPermission="android.permission.DUMP"' in manifest
            and 'android:exported="true"' in manifest
        ),
        "host merger requires real envelope and bundleEnd": (
            'recordType") != "bundleEnd"' in merger
            and '"diagnosticsHealth"' in merger
            and "latest_envelope" in merger
        ),
    }
    failures.extend(label for label, passed in checks.items() if not passed)
    if failures:
        print("BUILD 85 DIAGNOSTICS CLOSURE CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("Build 85 diagnostics closure contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
