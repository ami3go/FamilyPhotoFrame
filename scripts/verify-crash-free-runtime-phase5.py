#!/usr/bin/env python3
"""Executable regressions for the crash-free Phase 5 qualification gate."""
from __future__ import annotations

import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from crash_free_qualification import FAIL, NO_DATA, PASS, qualify  # noqa: E402
from diagnostics_analysis import load_bundle  # noqa: E402


def event(sequence: int, at_ms: int, code: str, fields: dict | None = None, category: str = "MEMORY") -> dict:
    return {
        "schemaVersion": 2,
        "sequence": sequence,
        "atEpochMs": at_ms,
        "elapsedRealtimeMs": at_ms,
        "sessionId": "phase5-session",
        "severity": "INFO",
        "category": category,
        "code": code,
        "origin": "INTERNAL",
        "operationId": None,
        "parentOperationId": None,
        "fields": fields or {},
    }


def fixture(path: Path, failing: bool = False, incomplete: bool = False) -> None:
    start = 1_800_000_000_000
    records: list[dict] = [
        {
            "recordType": "bundleMetadata", "bundleSchemaVersion": 1,
            "eventSchemaVersions": [2], "retentionStatus": "COMPLETE",
            "evidenceIncomplete": False,
        },
        {
            "recordType": "diagnosticsHealth", "droppedTotal": 0,
            "fieldsDropped": 0, "standard": {"retentionStatus": "COMPLETE"},
            "bulk": {"retentionStatus": "COMPLETE"},
        },
    ]
    sequence = 1

    def add(at_ms: int, code: str, fields: dict | None = None, category: str = "MEMORY") -> None:
        nonlocal sequence
        records.append(event(sequence, at_ms, code, fields, category))
        sequence += 1

    add(start, "SESSION_START", {
        "appVersion": "26.43.1", "versionCode": "43", "processStartKind": "FIRST_OBSERVATION",
        "memoryClassMb": "100",
    }, "APP")
    add(start + 1_000, "ACTIVITY_STARTED", {}, "LIFECYCLE")
    stop_at = start + 3 * 3_600_000
    add(stop_at, "ACTIVITY_STOPPED", {}, "LIFECYCLE")
    add(stop_at + 60_000, "ACTIVITY_STARTED", {}, "LIFECYCLE")
    loss_at = start + 4 * 3_600_000
    add(loss_at, "SOURCE_UNAVAILABLE", {"sourceKind": "SMB"}, "SOURCE")
    add(loss_at + 120_000, "SOURCE_RECOVERED", {"sourceKind": "SMB"}, "SOURCE")
    add(loss_at + 130_000, "SLIDE_RENDERED", {"presentationToken": "presentation_" + "a" * 24}, "ENGINE")

    for minute in range(25 * 60 + 1):
        at_ms = start + minute * 60_000
        growth = minute * (24 if failing else 0.15)
        fd_growth = minute // 60 if failing else 0
        fields = {
            "heapUsedKb": str(30_000 + (minute % 7) * 10),
            "heapMaxKb": "102400",
            "pssKb": str(int(80_000 + growth)),
            "nativePssKb": str(int(30_000 + growth)),
            "nativeHeapKb": str(int(25_000 + growth / 2)),
            "openFdCount": str(32 + fd_growth),
            "threadCount": str(18 + fd_growth),
            "smbActiveStreams": "0", "smbStreamsOpened": str(minute),
            "smbStreamsClosed": str(minute), "smbOldestStreamAgeMs": "0",
            "smbTrackingSaturated": "false",
            "mediaActiveTransfers": "0", "mediaTransfersStarted": str(minute),
            "mediaTransfersFinished": str(minute), "mediaOldestTransferAgeMs": "0",
            "mediaTrackingSaturated": "false",
            "preparedSlideCount": "3", "renderedSlideCount": "1",
            "pendingDisposals": "0", "economyBaseline": "true",
        }
        add(at_ms, "HEAP_SAMPLE", fields)

    transition_start = start + 5 * 3_600_000
    for index in range(600):
        at_ms = transition_start + index * 90_000
        generation = str(index + 1)
        shared = {"hostGeneration": "1", "transitionGeneration": generation}
        add(at_ms, "TRANSITION_STARTED", shared, "ENGINE")
        if failing and index < 80:
            add(at_ms + 1_000, "TRANSITION_CANCELLED", {
                **shared, "cancellationInitiator": "NEW_PRESENTATION",
            }, "ENGINE")
        else:
            add(at_ms + 1_000, "TRANSITION_COMPLETED", shared, "ENGINE")
        add(at_ms + 2_000, "SLIDE_RENDERED", {
            "presentationToken": "presentation_" + f"{index:024x}"[-24:],
        }, "ENGINE")
    if failing:
        for index in range(10):
            add(transition_start + index * 60_000, "RENDER_ACK_TIMEOUT", {}, "ENGINE")
        add(start + 20 * 3_600_000, "MEMORY_SELF_RECOVERY_GC")
        add(start + 20 * 3_600_000 + 60_000, "MEMORY_SELF_RECOVERY_GC")
    if not incomplete:
        records.append({"recordType": "bundleEnd", "bundleSchemaVersion": 1, "complete": True})
    else:
        records[1]["standard"]["retentionStatus"] = "UNKNOWN"
    path.write_text("\n".join(json.dumps(record) for record in records) + "\n", encoding="utf-8")


errors: list[str] = []


def require(value: bool, message: str) -> None:
    if not value:
        errors.append(message)


with tempfile.TemporaryDirectory(prefix="fpf-phase5-") as temporary:
    temp = Path(temporary)
    healthy_path = temp / "healthy.jsonl"
    failing_path = temp / "failing.jsonl"
    incomplete_path = temp / "incomplete.jsonl"
    fixture(healthy_path)
    fixture(failing_path, failing=True)
    fixture(incomplete_path, incomplete=True)

    healthy = qualify(load_bundle(healthy_path), "day")
    require(healthy["status"] == PASS, f"healthy qualification was {healthy['status']}")
    healthy_gates = {gate["key"]: gate for gate in healthy["gates"]}
    for key in (
        "coverage", "diagnostics", "heap", "pss", "native_pss", "fds", "threads",
        "resources", "bitmaps", "render_ack", "transition_rate", "transition_correlation",
        "source_recovery", "lifecycle",
    ):
        require(healthy_gates[key]["status"] == PASS, f"healthy {key} gate did not pass")

    failing = qualify(load_bundle(failing_path), "day", load_bundle(healthy_path))
    require(failing["status"] == FAIL, "known-bad qualification did not fail")
    failing_gates = {gate["key"]: gate for gate in failing["gates"]}
    for key in ("pss", "native_pss", "fds", "threads", "render_ack", "transition_rate", "self_recovery"):
        require(failing_gates[key]["status"] == FAIL, f"known-bad {key} gate did not fail")
    require(failing["comparison"], "A/B comparison was not produced")

    incomplete = qualify(load_bundle(incomplete_path), "day")
    incomplete_gates = {gate["key"]: gate for gate in incomplete["gates"]}
    require(incomplete_gates["diagnostics"]["status"] == FAIL, "incomplete bundle did not fail integrity")

    legacy_path = temp / "legacy.jsonl"
    legacy_path.write_text(json.dumps({"t": 1, "sid": "old", "cat": "APP", "code": "SESSION_START"}) + "\n")
    legacy = qualify(load_bundle(legacy_path), "accelerated")
    require(legacy["status"] in {FAIL, "INCOMPLETE"}, "legacy/no-data bundle incorrectly passed")
    require(any(gate["status"] == NO_DATA for gate in legacy["gates"]), "legacy bundle did not retain NO DATA")

build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
notes = (ROOT / "docs/CRASH_FREE_RUNTIME_PHASE5_QUALIFICATION.md").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
pure_gate = (ROOT / "scripts/verify-pure-logic.sh").read_text(encoding="utf-8")
build_number_match = re.search(r"val buildNumber = (\d+)", build)
build_number = int(build_number_match.group(1)) if build_number_match else 0
require(build_number >= 43, "Phase 5 versionCode is not install-distinct")
require("26.43.1" in notes and "physical-device qualification pending" in notes, "Phase 5 notes are incomplete")
require("verify-crash-free-runtime-phase5.py" in workflow, "CI does not execute the Phase 5 analyzer regression")
require("testDebugUnitTest lintDebug assembleDebug" in workflow, "CI omits a required Android build gate")
require("verify-crash-free-runtime-phase5.py" in pure_gate, "offline gate does not execute Phase 5 regression")

if errors:
    for error in errors:
        print(f"  FAIL  {error}")
    raise SystemExit(1)
print("Crash-free runtime Phase 5 qualification checks passed")
