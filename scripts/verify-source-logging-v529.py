#!/usr/bin/env python3
"""Focused Phase-2 contracts for correlated source/rebuild diagnostics."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/familyphotoframe"
VM = (MAIN / "ui/slideshow/SlideshowViewModel.kt").read_text(encoding="utf-8")
INDEXER = (MAIN / "data/index/Indexer.kt").read_text(encoding="utf-8")
UPLOAD = (MAIN / "web/WebUploadManager.kt").read_text(encoding="utf-8")
CATALOG = (MAIN / "data/diagnostics/DiagnosticEventSpec.kt").read_text(encoding="utf-8")


def require(text: str, needle: str, label: str, failures: list[str]) -> None:
    if needle not in text:
        failures.append(f"missing {label}: {needle}")


def main() -> int:
    failures: list[str] = []
    triggers = {
        "INITIAL_SETTINGS_LOAD", "FIRST_RUN_CONFIGURATION", "REBUILD_ANDROID_UI",
        "REBUILD_WEB_UI", "SOURCE_SETTINGS_CHANGED", "CREDENTIAL_UPDATED",
        "CONFIG_IMPORTED", "ENCRYPTED_BUNDLE_IMPORTED", "FILTERS_CHANGED",
        "SCHEDULED_RESCAN", "RECOVERY_PROMOTION", "LOCAL_UPLOAD_CHANGED",
    }
    for trigger in sorted(triggers):
        require(CATALOG, trigger, f"trigger {trigger}", failures)

    for hop in (
        "SourceApplyRequest", "requestSourceRefresh", "applySourceRequest",
        "applySource(source: ActiveSource, request: SourceApplyRequest)",
        "indexSource(", "ScanDiagnosticContext(",
    ):
        require(VM, hop, "source trigger propagation hop", failures)
    require(INDEXER, "diagnosticContext: ScanDiagnosticContext", "Indexer correlation context", failures)

    require(VM, "SourceRefreshTrigger.REBUILD_ANDROID_UI", "Android rebuild origin", failures)
    require(VM, "SourceRefreshTrigger.REBUILD_WEB_UI", "web rebuild origin", failures)
    require(VM, '"REBUILD_REQUESTED"', "rebuild request event", failures)
    require(VM, "updateSettingsAndRefresh", "single explicit settings refresh", failures)
    require(VM, "explicitSourceChange", "DataStore echo suppression", failures)

    source_codes = {
        "SOURCE_REFRESH_REQUESTED", "SOURCE_APPLY_QUEUED", "SOURCE_APPLY_STARTED",
        "SOURCE_POOL_CONFIGURED", "SOURCE_REFRESH_COMPLETED", "SOURCE_REFRESH_CANCELLED",
        "SOURCE_REFRESH_FAILED", "SOURCE_TEST_STARTED", "SOURCE_TEST_COMPLETED",
        "SOURCE_TEST_FAILED", "SOURCE_HEALTH_CHECK_STARTED",
        "SOURCE_HEALTH_CHECK_COMPLETED", "SOURCE_HEALTH_CHECK_FAILED",
        "SOURCE_RECOVERY_STARTED", "SOURCE_RECOVERED", "SOURCE_UNAVAILABLE",
        "SOURCE_BACKOFF", "SOURCE_BACKOFF_EXHAUSTED",
    }
    scan_codes = {
        "SCAN_STARTED", "SCAN_PROGRESS", "SCAN_COALESCED", "SCAN_SUPERSEDED",
        "SCAN_COMPLETED", "SCAN_ABORTED", "SCAN_RECONCILIATION_COMPLETED",
        "SCAN_RECONCILIATION_SKIPPED",
    }
    combined = VM + INDEXER + UPLOAD
    for code in sorted(source_codes | scan_codes):
        require(combined, f'"{code}"', f"event {code}", failures)

    for field in (
        "sourceKind", "trigger", "configRevision", "refreshToken", "stage",
        "durationMs", "outcome", "found", "total", "errors", "exifMisses",
        "reconciled", "completionState", "cancellationReason",
    ):
        require(combined, f'"{field}"', f"structured field {field}", failures)

    if "append('|').append(lastSettings?.source)" in VM or "append('|').append(lastSettings?.filters)" in VM:
        failures.append("scan single-flight key still uses a mutable UI snapshot")
    if "Deferred<Int>" in VM:
        failures.append("scan single-flight does not retain final counts for coalesced requests")
    if "health.toString()" in combined:
        failures.append("raw SourceHealth text reaches diagnostics")
    if re.search(r"diagnostics\.(?:log|logEvent)\([^)]*health\.detail", combined, re.S):
        failures.append("ProviderError.detail reaches diagnostics")
    if 'throw cancelled' not in INDEXER:
        failures.append("Indexer cancellation is not rethrown")
    require(INDEXER, "PROGRESS_INTERVAL_MS = 5_000L", "5-second progress bound", failures)
    require(INDEXER, "PROGRESS_FILE_INTERVAL = 1_000", "1,000-file progress bound", failures)
    require(INDEXER, "if (!receivedFinished)", "missing-Finished terminal handling", failures)
    require(INDEXER, '"SCAN_RECONCILIATION_SKIPPED"', "partial reconciliation protection", failures)
    require(VM, "coalescedWithOperationId", "single-flight owner correlation", failures)
    require(VM, "supersededByOperationId", "latest-wins replacement correlation", failures)
    require(VM, "terminalLogged.compareAndSet(false, true)", "exactly-once refresh terminal", failures)

    # The local-upload refresh is a second production entry point and must have all
    # three terminal paths rather than leaking an active operation on failure.
    for code in ("SOURCE_REFRESH_COMPLETED", "SOURCE_REFRESH_CANCELLED", "SOURCE_REFRESH_FAILED"):
        require(UPLOAD, f'"{code}"', f"upload terminal {code}", failures)
    require(UPLOAD, "finally", "upload operation cleanup", failures)

    if failures:
        print("V52.9 SOURCE LOGGING CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print(f"  note: {len(triggers)} triggers and {len(source_codes | scan_codes)} lifecycle events verified")
    print("  v52.9 correlated source/rebuild contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
