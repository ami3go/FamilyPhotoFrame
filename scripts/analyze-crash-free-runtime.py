#!/usr/bin/env python3
"""Grade a FamilyPhotoFrame endurance bundle against crash-free Phase 5 gates."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from crash_free_qualification import FAIL, PROFILES, qualify, render_markdown
from diagnostics_analysis import load_bundle


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="Analyze a FamilyPhotoFrame crash-free endurance bundle",
    )
    parser.add_argument("source", help="candidate diagnostics JSONL bundle")
    parser.add_argument("--baseline", help="optional pre-change diagnostics JSONL bundle")
    parser.add_argument("--profile", choices=sorted(PROFILES), default="day")
    parser.add_argument("--json-out")
    parser.add_argument("--markdown-out")
    parser.add_argument(
        "--release-gate", action="store_true",
        help="return failure rather than the distinct incomplete status for required NO DATA",
    )
    args = parser.parse_args(argv)

    source = Path(args.source)
    report = qualify(
        load_bundle(source),
        profile_key=args.profile,
        baseline_bundle=load_bundle(args.baseline) if args.baseline else None,
    )
    json_path = Path(args.json_out) if args.json_out else source.with_name(source.stem + "-crash-free.json")
    markdown_path = (
        Path(args.markdown_out) if args.markdown_out
        else source.with_name(source.stem + "-crash-free.md")
    )
    json_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    markdown_path.write_text(render_markdown(report), encoding="utf-8")

    print(
        f"Crash-free Phase 5 qualification: {report['candidate']['appVersion']} "
        f"({report['profile']['label']})"
    )
    print(f"  status: {report['status']}")
    print(f"  reports: {json_path}, {markdown_path}")
    for gate in report["gates"]:
        requirement = "" if gate["required"] else " (optional)"
        print(f"  {gate['status']:<8} {gate['name']}{requirement:<11} {gate['detail']}")
    summary = report["summary"]
    print(
        f"  {summary['pass']} pass, {summary['fail']} fail, "
        f"{summary['noData']} no-data"
    )

    if report["status"] == FAIL:
        return 1
    if report["status"] == "INCOMPLETE":
        return 1 if args.release_gate else 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
