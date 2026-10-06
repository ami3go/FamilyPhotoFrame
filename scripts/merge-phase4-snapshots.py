#!/usr/bin/env python3
"""Build one authoritative Phase-4 bundle from periodic V80 snapshots.

Standard and slide events are deduplicated independently because both streams share one
process-wide sequence. The envelope and terminal bundleEnd are copied from the newest
successful on-device debug-provider export, so writer health is never invented by the host.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Iterable


STAMP = re.compile(r"^\d{8}T\d{6}Z$")
ERROR_FILES = (
    "diagnostics-list-error.txt",
    "diagnostics-pull-error.txt",
    "diagnostics-slides-list-error.txt",
    "diagnostics-slides-pull-error.txt",
    "decompress-error.txt",
    "durable-bundle-error.txt",
)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"{path.name}:{number}: malformed JSON: {error.msg}") from error
        if not isinstance(item, dict):
            raise ValueError(f"{path.name}:{number}: record is not an object")
        records.append(item)
    return records


def write_jsonl(path: Path, records: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as output:
        for record in records:
            output.write(json.dumps(record, separators=(",", ":"), sort_keys=False))
            output.write("\n")


def event_key(record: dict[str, Any]) -> tuple[str, int]:
    return str(record.get("sessionId") or ""), int(record.get("sequence") or 0)


def event_order(record: dict[str, Any]) -> tuple[int, int]:
    return int(record.get("atEpochMs") or 0), int(record.get("sequence") or 0)


def merge_stream(paths: list[Path], session_id: str) -> list[dict[str, Any]]:
    by_key: dict[tuple[str, int], dict[str, Any]] = {}
    for path in paths:
        for record in read_jsonl(path):
            if record.get("sessionId") != session_id:
                continue
            key = event_key(record)
            if key[1] <= 0:
                continue
            by_key.setdefault(key, record)
    return sorted(by_key.values(), key=event_order)


def latest_envelope(snapshot_dirs: list[Path]) -> list[dict[str, Any]]:
    for directory in reversed(snapshot_dirs):
        path = directory / "durable-bundle.jsonl"
        if not path.is_file():
            continue
        records = read_jsonl(path)
        if not records or records[-1].get("recordType") != "bundleEnd":
            raise ValueError(f"{directory.name}: durable bundle lacks terminal bundleEnd")
        selected: list[dict[str, Any]] = []
        for kind in ("bundleMetadata", "runtimeSnapshot", "diagnosticsHealth"):
            match = next((item for item in records if item.get("recordType") == kind), None)
            if match is None:
                raise ValueError(f"{directory.name}: durable bundle lacks {kind}")
            selected.append(match)
        selected.append(records[-1])
        return selected
    raise ValueError("no successful on-device durable bundle export found")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("base", type=Path, help="monitoring directory containing timestamped snapshots")
    parser.add_argument("--session", required=True)
    parser.add_argument("--prefix", default="merged-expected")
    args = parser.parse_args()

    snapshots = sorted(
        path for path in args.base.iterdir()
        if path.is_dir() and STAMP.fullmatch(path.name)
    )
    data_snapshots = [
        path for path in snapshots
        if (path / "standard-combined.jsonl").is_file() and (path / "slides-combined.jsonl").is_file()
    ]
    if not data_snapshots:
        raise SystemExit("no successful snapshots found")
    for directory in data_snapshots:
        for name in ERROR_FILES:
            path = directory / name
            if path.is_file() and path.stat().st_size:
                raise SystemExit(f"{directory.name}: non-empty {name}")

    standard = merge_stream(
        [path / "standard-combined.jsonl" for path in data_snapshots], args.session,
    )
    slides = merge_stream(
        [path / "slides-combined.jsonl" for path in data_snapshots], args.session,
    )
    if not standard or not any(item.get("code") == "SESSION_START" for item in standard):
        raise SystemExit("expected session has no SESSION_START")

    envelope = latest_envelope(data_snapshots)
    metadata = envelope[:3]
    end = envelope[-1]
    events = sorted(standard + slides, key=event_order)
    prefix = args.base / args.prefix
    write_jsonl(prefix.with_name(prefix.name + "-standard.jsonl"), standard)
    write_jsonl(prefix.with_name(prefix.name + "-slides.jsonl"), slides)
    write_jsonl(prefix.with_name(prefix.name + "-bundle.jsonl"), [*metadata, *events, end])
    print(
        f"session={args.session} standard={len(standard)} slides={len(slides)} "
        f"bundle={len(events) + len(metadata) + 1} envelope=verified"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
