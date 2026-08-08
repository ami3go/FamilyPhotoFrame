#!/usr/bin/env python3
"""Shared schema-v1/v2 JSONL loading, validation, correlation, and report rendering."""
from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

SUPPORTED_SCHEMAS = {1, 2}
TERMINAL_SUFFIXES = ("_COMPLETED", "_FAILED", "_CANCELLED")
TERMINAL_CODES = {"SCAN_ABORTED", "SOURCE_RECOVERED", "SOURCE_BACKOFF_EXHAUSTED"}
CRASH_CODES = {"UNCAUGHT_EXCEPTION", "PREVIOUS_UNCAUGHT_EXCEPTION", "PREVIOUS_CRASH_EVIDENCE"}
ANR_CODES = {"MAIN_THREAD_STALL_ESCALATED", "PREVIOUS_ANR_EVIDENCE", "ANR"}
EXIT_CODES = {"PROCESS_EXIT_RECORDED"}
CRITICAL_PREFIXES = ("SCAN_", "SOURCE_", "REBUILD_")
PRIVATE_KEYS = {
    "password", "passwd", "pass", "secret", "credential", "credentials", "authorization",
    "cookie", "apikey", "api_key", "pin", "otp", "path", "fullpath", "uri", "url",
    "host", "hostname", "username", "user", "share", "gps", "latitude", "longitude",
    "lat", "lon", "album",
}
PRIVATE_VALUE_PATTERNS = (
    re.compile(r"https?://", re.I),
    re.compile(r"(?<![A-Za-z0-9])(?:\d{1,3}\.){3}\d{1,3}(?![A-Za-z0-9])"),
    re.compile(r"(?:^|\s)(?:/[\w .-]+){2,}"),
    re.compile(r"[A-Za-z]:\\"),
    re.compile(r"[?&](?:token|key|password|auth|user)=", re.I),
    re.compile(r"[^\s/\\]+\.(?:jpe?g|png|gif|webp|heic|heif|avif)(?:\s|$)", re.I),
)


@dataclass
class BundleData:
    events: list[dict[str, Any]] = field(default_factory=list)
    metadata: list[dict[str, Any]] = field(default_factory=list)
    malformed_lines: int = 0
    schema_errors: list[str] = field(default_factory=list)
    duplicate_events: int = 0
    privacy_violations: list[dict[str, Any]] = field(default_factory=list)


def _event_view(raw: dict[str, Any], line_number: int) -> dict[str, Any] | None:
    schema = raw.get("schemaVersion", 1)
    if schema not in SUPPORTED_SCHEMAS:
        return None
    if schema == 2:
        fields = raw.get("fields")
        if not isinstance(fields, dict):
            fields = {}
        event = dict(fields)
        event.update({
            "schemaVersion": 2,
            "sequence": _integer(raw.get("sequence")),
            "t": _integer(raw.get("atEpochMs")),
            "atEpochMs": _integer(raw.get("atEpochMs")),
            "elapsedRealtimeMs": _integer(raw.get("elapsedRealtimeMs")),
            "sid": str(raw.get("sessionId") or ""),
            "sessionId": str(raw.get("sessionId") or ""),
            "severity": str(raw.get("severity") or "INFO"),
            "cat": str(raw.get("category") or "?"),
            "category": str(raw.get("category") or "?"),
            "code": str(raw.get("code") or "?"),
            "origin": str(raw.get("origin") or "INTERNAL"),
            "operationId": raw.get("operationId"),
            "parentOperationId": raw.get("parentOperationId"),
            "message": str(raw.get("message") or ""),
            "fields": fields,
            "_line": line_number,
            "_raw": raw,
        })
        return event
    fields = raw.get("fields") if isinstance(raw.get("fields"), dict) else {
        key: value for key, value in raw.items()
        if key not in {"t", "sid", "cat", "code", "msg", "schemaVersion"}
    }
    event = dict(raw)
    event.update({
        "schemaVersion": 1,
        "sequence": _integer(raw.get("sequence")),
        "t": _integer(raw.get("t", raw.get("atEpochMs"))),
        "atEpochMs": _integer(raw.get("t", raw.get("atEpochMs"))),
        "elapsedRealtimeMs": _integer(raw.get("elapsedRealtimeMs")),
        "sid": str(raw.get("sid", raw.get("sessionId", "")) or ""),
        "sessionId": str(raw.get("sid", raw.get("sessionId", "")) or ""),
        "cat": str(raw.get("cat", raw.get("category", "?")) or "?"),
        "category": str(raw.get("cat", raw.get("category", "?")) or "?"),
        "severity": str(raw.get("severity") or "INFO"),
        "origin": str(raw.get("origin") or "LEGACY"),
        "operationId": raw.get("operationId"),
        "parentOperationId": raw.get("parentOperationId"),
        "message": str(raw.get("msg", raw.get("message", "")) or ""),
        "fields": fields,
        "_line": line_number,
        "_raw": raw,
    })
    return event


def load_bundle(path: str | Path) -> BundleData:
    result = BundleData()
    seen: set[tuple[Any, ...]] = set()
    sequences: dict[str, set[int]] = defaultdict(set)
    with Path(path).open(encoding="utf-8", errors="replace") as handle:
        for line_number, raw_line in enumerate(handle, 1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                raw = json.loads(line)
            except json.JSONDecodeError:
                result.malformed_lines += 1
                continue
            if not isinstance(raw, dict):
                result.schema_errors.append(f"line {line_number}: record is not an object")
                continue
            if raw.get("recordType"):
                result.metadata.append(raw)
                continue
            schema = raw.get("schemaVersion", 1)
            if schema not in SUPPORTED_SCHEMAS:
                result.schema_errors.append(f"line {line_number}: unsupported schemaVersion {schema!r}")
                continue
            if not raw.get("code"):
                result.schema_errors.append(f"line {line_number}: event code is missing")
                continue
            if schema == 2:
                for key in ("sequence", "atEpochMs", "elapsedRealtimeMs", "sessionId", "severity", "category", "origin", "fields"):
                    if key not in raw:
                        result.schema_errors.append(f"line {line_number}: schema-v2 field {key} is missing")
                if not isinstance(raw.get("fields"), dict):
                    result.schema_errors.append(f"line {line_number}: fields is not an object")
            event = _event_view(raw, line_number)
            if event is None:
                continue
            sid, sequence = event["sessionId"], event["sequence"]
            identity = (schema, sid, sequence) if schema == 2 and sid and sequence > 0 else (
                schema, event["t"], event["code"], json.dumps(event["fields"], sort_keys=True),
            )
            if identity in seen:
                result.duplicate_events += 1
                continue
            seen.add(identity)
            if schema == 2 and sid:
                if sequence <= 0:
                    result.schema_errors.append(f"line {line_number}: sequence must be positive")
                elif sequence in sequences[sid]:
                    result.schema_errors.append(f"line {line_number}: duplicate sequence {sid}#{sequence}")
                sequences[sid].add(sequence)
            result.events.append(event)
    # Identity is (sessionId, sequence); wall time only interleaves sessions for display.
    result.events.sort(key=lambda event: (event.get("t", 0), event.get("sessionId", ""), event.get("sequence", 0)))
    result.privacy_violations = privacy_violations(result.events)
    return result


def privacy_violations(events: list[dict[str, Any]]) -> list[dict[str, Any]]:
    violations: list[dict[str, Any]] = []
    for event in events:
        fields = event.get("fields") or {}
        for key, value in fields.items():
            normalized = str(key).lower().replace("_", "")
            if str(key).lower() in PRIVATE_KEYS or normalized in {item.replace("_", "") for item in PRIVATE_KEYS}:
                violations.append({"line": event.get("_line"), "code": event.get("code"), "reason": f"private field {key}"})
                continue
            text = str(value)
            if key.endswith("Token") or re.fullmatch(r"[a-z0-9]{1,20}_[0-9a-f]{24}", text):
                continue
            if any(pattern.search(text) for pattern in PRIVATE_VALUE_PATTERNS):
                violations.append({"line": event.get("_line"), "code": event.get("code"), "reason": f"private-shaped value in {key}"})
        message = str(event.get("message") or "")
        if message and any(pattern.search(message) for pattern in PRIVATE_VALUE_PATTERNS):
            violations.append({"line": event.get("_line"), "code": event.get("code"), "reason": "private-shaped message"})
    return violations


def operation_report(events: list[dict[str, Any]]) -> list[dict[str, Any]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        operation_id = event.get("operationId")
        if operation_id:
            grouped[str(operation_id)].append(event)
    operations = []
    for operation_id, items in grouped.items():
        items.sort(key=lambda event: (event.get("elapsedRealtimeMs", 0), event.get("sequence", 0)))
        first, last = items[0], items[-1]
        terminal = str(last.get("code", "")).endswith(TERMINAL_SUFFIXES) or last.get("code") in TERMINAL_CODES
        codes = [str(event.get("code")) for event in items]
        critical = any(code.startswith(CRITICAL_PREFIXES) for code in codes)
        operations.append({
            "operationId": operation_id,
            "parentOperationId": first.get("parentOperationId"),
            "sessionId": first.get("sessionId"),
            "origin": first.get("origin"),
            "trigger": (first.get("fields") or {}).get("trigger", ""),
            "startedAtEpochMs": first.get("t", 0),
            "lastAtEpochMs": last.get("t", 0),
            "durationMs": max(0, _integer(last.get("elapsedRealtimeMs")) - _integer(first.get("elapsedRealtimeMs"))),
            "terminalCode": last.get("code") if terminal else None,
            "outcome": (last.get("fields") or {}).get("outcome") or (last.get("fields") or {}).get("completionState"),
            "incomplete": not terminal,
            "critical": critical,
            "codes": codes,
        })
    return sorted(operations, key=lambda item: (item["startedAtEpochMs"], item["operationId"]))


def build_report(bundle: BundleData) -> dict[str, Any]:
    codes = Counter(str(event.get("code")) for event in bundle.events)
    schemas = Counter(int(event.get("schemaVersion", 1)) for event in bundle.events)
    sessions = sorted({str(event.get("sessionId")) for event in bundle.events if event.get("sessionId")})
    operations = operation_report(bundle.events)
    health = next((item for item in bundle.metadata if item.get("recordType") == "diagnosticsHealth"), {})
    metadata = next((item for item in bundle.metadata if item.get("recordType") == "bundleMetadata"), {})
    has_bundle_envelope = bool(metadata)
    has_bundle_end = any(item.get("recordType") == "bundleEnd" for item in bundle.metadata)
    sequence_gaps: dict[str, list[list[int]]] = {}
    for session in sessions:
        values = sorted({
            _integer(event.get("sequence")) for event in bundle.events
            if event.get("schemaVersion") == 2 and event.get("sessionId") == session and _integer(event.get("sequence")) > 0
        })
        gaps = [[left + 1, right - 1] for left, right in zip(values, values[1:]) if right > left + 1]
        if gaps:
            sequence_gaps[session] = gaps
    crashes = [failure_summary(event, bundle.events) for event in bundle.events if event.get("code") in CRASH_CODES]
    anrs = [failure_summary(event, bundle.events) for event in bundle.events if event.get("code") in ANR_CODES]
    exits = [event_summary(event) for event in bundle.events if event.get("code") in EXIT_CODES]
    incomplete_critical = [item for item in operations if item["incomplete"] and item["critical"]]
    evidence_incomplete = bool(metadata.get("evidenceIncomplete")) or _integer(health.get("droppedTotal")) > 0 or (has_bundle_envelope and not has_bundle_end)
    failures = []
    if bundle.schema_errors:
        failures.append("schema validation failed")
    if bundle.privacy_violations:
        failures.append("privacy validation failed")
    if crashes:
        failures.append("crash evidence present")
    if anrs:
        failures.append("ANR evidence present")
    incomplete = []
    if bundle.malformed_lines:
        incomplete.append("malformed or truncated JSONL lines")
    if has_bundle_envelope and not has_bundle_end:
        incomplete.append("stream ended before the bundleEnd record")
    if sequence_gaps and metadata.get("retentionStatus") == "COMPLETE" and _integer(health.get("droppedTotal")) == 0:
        incomplete.append("schema-v2 sequence gaps are unexplained by retention or drops")
    if incomplete_critical:
        incomplete.append("critical operations lack terminal events")
    if evidence_incomplete:
        incomplete.append("bundle metadata marks evidence incomplete")
    status = "FAIL" if failures else ("INCOMPLETE" if incomplete else "PASS")
    return {
        "reportSchemaVersion": 1,
        "status": status,
        "summary": {
            "eventCount": len(bundle.events),
            "metadataRecordCount": len(bundle.metadata),
            "schemas": {str(key): value for key, value in sorted(schemas.items())},
            "sessions": len(sessions),
            "sessionIds": sessions,
            "malformedLines": bundle.malformed_lines,
            "duplicateEventsRemoved": bundle.duplicate_events,
            "sequenceGaps": sequence_gaps,
            "crashes": len(crashes),
            "anrs": len(anrs),
            "processExits": len(exits),
            "incompleteCriticalOperations": len(incomplete_critical),
        },
        "validation": {
            "schemaErrors": bundle.schema_errors,
            "privacyViolations": bundle.privacy_violations,
            "failures": failures,
            "incompleteReasons": incomplete,
        },
        "retention": {
            "retentionStatus": metadata.get("retentionStatus", "UNKNOWN"),
            "evidenceIncomplete": evidence_incomplete,
            "droppedTotal": _integer(health.get("droppedTotal")),
            "fieldsDropped": _integer(health.get("fieldsDropped")),
            "standard": health.get("standard", {}),
            "bulk": health.get("bulk", {}),
        },
        "operations": operations,
        "crashes": crashes,
        "anrs": anrs,
        "processExits": exits,
        "codeCounts": dict(sorted(codes.items())),
    }


def event_summary(event: dict[str, Any]) -> dict[str, Any]:
    return {
        "sessionId": event.get("sessionId"),
        "sequence": event.get("sequence"),
        "atEpochMs": event.get("t"),
        "code": event.get("code"),
        "severity": event.get("severity"),
        "operationId": event.get("operationId"),
        "fields": event.get("fields") or {},
    }


def failure_summary(event: dict[str, Any], events: list[dict[str, Any]]) -> dict[str, Any]:
    summary = event_summary(event)
    fields = event.get("fields") or {}
    target_session = str(fields.get("previousSessionId") or event.get("sessionId") or "")
    target_sequence = _integer(fields.get("lastSequence"))
    candidates = [item for item in events if str(item.get("sessionId") or "") == target_session and item is not event]
    correlated = None
    if target_sequence > 0:
        correlated = next((item for item in candidates if _integer(item.get("sequence")) == target_sequence), None)
    if correlated is None:
        before = [item for item in candidates if _integer(item.get("t")) <= _integer(event.get("t"))]
        correlated = before[-1] if before else None
    summary["correlatedPriorEvent"] = event_summary(correlated) if correlated else None
    operation_id = event.get("operationId") or fields.get("activeOperationId")
    summary["correlatedOperationCodes"] = [
        str(item.get("code")) for item in events if operation_id and item.get("operationId") == operation_id
    ]
    return summary


def write_reports(report: dict[str, Any], json_path: str | Path, markdown_path: str | Path) -> None:
    Path(json_path).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    Path(markdown_path).write_text(render_markdown(report), encoding="utf-8")


def render_markdown(report: dict[str, Any]) -> str:
    summary = report["summary"]
    validation = report["validation"]
    lines = [
        "# FamilyPhotoFrame diagnostics analysis",
        "",
        f"Status: **{report['status']}**",
        "",
        "## Summary",
        "",
        f"- Events: {summary['eventCount']}",
        f"- Schemas: {summary['schemas']}",
        f"- Sessions: {summary['sessions']}",
        f"- Crashes / ANRs / exits: {summary['crashes']} / {summary['anrs']} / {summary['processExits']}",
        f"- Incomplete critical operations: {summary['incompleteCriticalOperations']}",
        f"- Malformed lines: {summary['malformedLines']}",
        "",
        "## Validation",
        "",
    ]
    if not validation["failures"] and not validation["incompleteReasons"]:
        lines.append("- No schema, privacy, crash, ANR, or completeness failures found.")
    for item in validation["failures"]:
        lines.append(f"- FAIL: {item}")
    for item in validation["incompleteReasons"]:
        lines.append(f"- INCOMPLETE: {item}")
    lines += ["", "## Operations", ""]
    if not report["operations"]:
        lines.append("No correlated operations were recorded.")
    else:
        lines.append("| Operation | Trigger | Terminal result | Duration |")
        lines.append("|---|---|---|---:|")
        for item in report["operations"]:
            terminal = item["terminalCode"] or "INCOMPLETE"
            lines.append(f"| `{item['operationId']}` | {item['trigger'] or '—'} | {terminal} | {item['durationMs']} ms |")
    lines += ["", "## Retention", "", f"```json\n{json.dumps(report['retention'], indent=2, sort_keys=True)}\n```", ""]
    return "\n".join(lines)


def default_report_paths(source: str | Path) -> tuple[Path, Path]:
    path = Path(source)
    stem = path.with_suffix("")
    return Path(str(stem) + "-analysis.json"), Path(str(stem) + "-analysis.md")


def _integer(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0
