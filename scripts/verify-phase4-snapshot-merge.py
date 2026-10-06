#!/usr/bin/env python3
"""Regression check for the Phase-4 snapshot merger and real bundle envelope."""

from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SESSION = "00000000-0000-4000-8000-000000000083"


def write(path: Path, records: list[dict]) -> None:
    path.write_text("".join(json.dumps(item, separators=(",", ":")) + "\n" for item in records))


with tempfile.TemporaryDirectory(prefix="fpf-phase4-merge-") as temporary:
    base = Path(temporary)
    first = base / "20261006T000000Z"
    second = base / "20261006T010000Z"
    first.mkdir()
    second.mkdir()
    start = {
        "schemaVersion": 2, "sequence": 1, "atEpochMs": 1_000, "sessionId": SESSION,
        "code": "SESSION_START", "fields": {"appVersion": "26.83.1", "versionCode": "83"},
    }
    heap = {
        "schemaVersion": 2, "sequence": 3, "atEpochMs": 2_000, "sessionId": SESSION,
        "code": "HEAP_SAMPLE", "fields": {"heapUsedKb": "1", "heapMaxKb": "2"},
    }
    slide = {
        "schemaVersion": 2, "sequence": 2, "atEpochMs": 1_500, "sessionId": SESSION,
        "code": "SLIDE_SELECTED", "fields": {},
    }
    write(first / "standard-combined.jsonl", [start])
    write(first / "slides-combined.jsonl", [slide])
    write(second / "standard-combined.jsonl", [start, heap])
    write(second / "slides-combined.jsonl", [slide])
    write(
        second / "durable-bundle.jsonl",
        [
            {"recordType": "bundleMetadata", "bundleSchemaVersion": 1, "appVersion": "26.83.1", "versionCode": 83},
            {"recordType": "runtimeSnapshot"},
            {"recordType": "diagnosticsHealth", "droppedTotal": 0, "fieldsDropped": 0,
             "standard": {"retentionStatus": "COMPLETE"}, "bulk": {"retentionStatus": "PARTIAL_RETENTION"}},
            start, slide, heap,
            {"recordType": "bundleEnd", "evidenceIncomplete": False},
        ],
    )
    subprocess.run(
        ["python3", str(ROOT / "scripts/merge-phase4-snapshots.py"), str(base), "--session", SESSION],
        check=True,
        capture_output=True,
        text=True,
    )
    bundle = [json.loads(line) for line in (base / "merged-expected-bundle.jsonl").read_text().splitlines()]
    assert [item.get("recordType") for item in bundle[:3]] == [
        "bundleMetadata", "runtimeSnapshot", "diagnosticsHealth",
    ]
    assert [item.get("sequence") for item in bundle if "sequence" in item] == [1, 2, 3]
    assert bundle[-1]["recordType"] == "bundleEnd"
    assert len([item for item in bundle if item.get("sequence") == 1]) == 1

print("Phase 4 snapshot merge checks passed")
