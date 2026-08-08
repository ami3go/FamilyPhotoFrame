#!/usr/bin/env python3
"""Focused mixed-schema, operation, privacy, and report checks for v52.9 analyzers."""
from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from diagnostics_analysis import build_report, load_bundle, write_reports  # noqa: E402

errors: list[str] = []


def require(value: bool, message: str) -> None:
    if not value:
        errors.append(message)


legacy = load_bundle(ROOT / "scripts/fixtures/diagnostics-v1.jsonl")
require(len(legacy.events) == 4, "schema-v1 fixture was not retained")
require({event["schemaVersion"] for event in legacy.events} == {1}, "schema-v1 fixture normalized incorrectly")
require(not legacy.schema_errors, "schema-v1 fixture produced validation errors")

with tempfile.TemporaryDirectory(prefix="fpf-analyzer-") as temp:
    source = Path(temp) / "mixed.jsonl"
    records = [
        {"recordType": "bundleMetadata", "bundleSchemaVersion": 1, "evidenceIncomplete": False, "retentionStatus": "COMPLETE"},
        {"t": 900, "sid": "legacy", "cat": "APP", "code": "SESSION_START"},
        {"schemaVersion": 2, "sequence": 1, "atEpochMs": 1000, "elapsedRealtimeMs": 10,
         "sessionId": "new", "severity": "INFO", "category": "SOURCE", "code": "SOURCE_REFRESH_REQUESTED",
         "origin": "WEB_UI", "operationId": "op-1", "parentOperationId": None,
         "fields": {"trigger": "REBUILD_WEB_UI", "sourceKind": "SMB"}},
        {"schemaVersion": 2, "sequence": 2, "atEpochMs": 1100, "elapsedRealtimeMs": 110,
         "sessionId": "new", "severity": "INFO", "category": "SOURCE", "code": "SOURCE_REFRESH_COMPLETED",
         "origin": "WEB_UI", "operationId": "op-1", "parentOperationId": None,
         "fields": {"trigger": "REBUILD_WEB_UI", "sourceKind": "SMB", "outcome": "SUCCESS"}},
        {"recordType": "bundleEnd", "bundleSchemaVersion": 1, "complete": True},
    ]
    source.write_text("\n".join(json.dumps(item) for item in records) + "\n", encoding="utf-8")
    mixed = load_bundle(source)
    report = build_report(mixed)
    require({event["schemaVersion"] for event in mixed.events} == {1, 2}, "mixed schema bundle not supported")
    require(report["status"] == "PASS", "complete mixed-schema bundle did not pass")
    require(report["operations"][0]["terminalCode"] == "SOURCE_REFRESH_COMPLETED", "operation outcome not reconstructed")
    json_out, markdown_out = Path(temp) / "report.json", Path(temp) / "report.md"
    write_reports(report, json_out, markdown_out)
    require(json.loads(json_out.read_text())["status"] == "PASS", "JSON report invalid")
    require("Operation" in markdown_out.read_text(), "Markdown report missing operations")

    bad = Path(temp) / "bad.jsonl"
    bad.write_text(json.dumps({
        "schemaVersion": 2, "sequence": 1, "atEpochMs": 1, "elapsedRealtimeMs": 1,
        "sessionId": "bad", "severity": "INFO", "category": "SOURCE", "code": "SOURCE_REFRESH_REQUESTED",
        "origin": "WEB_UI", "operationId": "critical-op", "parentOperationId": None,
        "fields": {"host": "192.168.1.44", "trigger": "REBUILD_WEB_UI"},
    }) + "\n", encoding="utf-8")
    bad_report = build_report(load_bundle(bad))
    require(bad_report["status"] == "FAIL", "privacy violation did not fail analysis")
    require(bad_report["summary"]["incompleteCriticalOperations"] == 1, "incomplete critical operation not reported")

if errors:
    for error in errors:
        print(f"  ERROR: {error}")
    raise SystemExit(1)
print("  diagnostics analyzer v1/v2/mixed-schema checks passed")
