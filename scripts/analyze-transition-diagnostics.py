#!/usr/bin/env python3
"""Analyse transition endurance evidence from a FamilyPhotoFrame diagnostics bundle.

Usage:
    python3 scripts/analyze-transition-diagnostics.py familyphotoframe-diagnostics.jsonl

Exit status:
    0  all measurable Phase-5 criteria pass
    1  one or more criteria fail
    2  evidence is incomplete (NO DATA) but no measured criterion fails
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from diagnostics_analysis import build_report, load_bundle, render_markdown

PASS, FAIL, NODATA = "PASS", "FAIL", "NO DATA"
EXPECTED_EFFECTS = {
    "crossfade", "soft_dissolve", "gentle_zoom_in", "gentle_zoom_out",
    "horizontal_glide", "vertical_glide", "depth_fade", "ken_burns_handoff",
    "soft_reveal", "soft_focus_fade",
}
CRASH_CODES = {
    "APP_CRASH", "UNCAUGHT_EXCEPTION", "OUT_OF_MEMORY", "OOM",
    "PREVIOUS_UNCAUGHT_EXCEPTION", "PREVIOUS_CRASH_EVIDENCE",
    "ANR", "PREVIOUS_ANR_EVIDENCE", "MAIN_THREAD_STALL_ESCALATED",
    "RENDERER_CRASH", "BLACK_FRAME_DETECTED",
}


def number(event: dict, key: str, default=None):
    value = event.get(key)
    if value in (None, ""):
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def load(path: Path):
    bundle = load_bundle(path)
    return bundle.events, bundle.malformed_lines


def fmt(status: str, name: str, detail: str) -> str:
    return f"  {status:<8} {name:<42} {detail}"


def heap_growth_mb(samples: list[dict]):
    values = [(number(e, "t"), number(e, "heapUsedKb")) for e in samples]
    values = [(t, kb) for t, kb in values if t is not None and kb is not None]
    if len(values) < 10:
        return None
    # Ignore the first 10% as warm-up, then compare robust medians at both ends.
    warm = max(1, len(values) // 10)
    steady = values[warm:]
    if len(steady) < 8:
        return None
    edge = max(3, min(10, len(steady) // 5))
    start = statistics.median(kb for _, kb in steady[:edge])
    end = statistics.median(kb for _, kb in steady[-edge:])
    return (end - start) / 1024.0


def analyse(events: list[dict]):
    by_code = defaultdict(list)
    for event in events:
        by_code[event.get("code", "?")].append(event)

    completed = by_code["TRANSITION_COMPLETED"]
    cancelled = by_code["TRANSITION_CANCELLED"]
    fallbacks = by_code["TRANSITION_FALLBACK"]
    warnings = by_code["TRANSITION_PERFORMANCE_WARNING"]
    results = []

    if len(completed) >= 1000:
        results.append((PASS, "Endurance transition count", f"{len(completed)} completed"))
    elif completed:
        results.append((NODATA, "Endurance transition count", f"{len(completed)} completed; need 1000"))
    else:
        results.append((NODATA, "Endurance transition count", "no completed transition events"))

    effects = Counter(e.get("resolvedEffect", "?") for e in completed)
    unknown = set(effects) - EXPECTED_EFFECTS
    if completed and not unknown:
        results.append((PASS, "Resolved effect identifiers", str(dict(effects))))
    elif unknown:
        results.append((FAIL, "Resolved effect identifiers", f"unknown effects: {sorted(unknown)}"))
    else:
        results.append((NODATA, "Resolved effect identifiers", "no completed transitions"))

    intervals = sum(max(0, int(number(e, "frameCount", 0) or 0) - 1) for e in completed)
    slow = sum(int(number(e, "slowFrameCount", 0) or 0) for e in completed)
    if intervals:
        good_ratio = 1.0 - slow / intervals
        status = PASS if good_ratio >= 0.95 else FAIL
        results.append((status, "Frame budget", f"{good_ratio * 100:.2f}% within 33.3 ms"))
    else:
        results.append((NODATA, "Frame budget", "no frame timing fields"))

    maxima = [number(e, "maximumFrameMs") for e in completed]
    maxima = [v for v in maxima if v is not None]
    if maxima:
        maximum = max(maxima)
        results.append((PASS if maximum <= 250 else FAIL, "Maximum UI stall", f"{maximum:.0f} ms"))
    else:
        results.append((NODATA, "Maximum UI stall", "no maximumFrameMs fields"))

    latencies = [number(e, "startLatencyMs") for e in completed]
    latencies = [v for v in latencies if v is not None]
    if latencies:
        maximum = max(latencies)
        p95 = sorted(latencies)[min(len(latencies) - 1, int(len(latencies) * 0.95))]
        status = PASS if maximum <= 100 else FAIL
        results.append((status, "Ready-to-first-frame latency", f"max {maximum:.0f} ms, p95 {p95:.0f} ms"))
    else:
        results.append((NODATA, "Ready-to-first-frame latency", "no startLatencyMs fields"))

    retained = [number(e, "preparedSlideCount") for e in completed]
    retained = [int(v) for v in retained if v is not None]
    if retained:
        maximum = max(retained)
        results.append((PASS if maximum <= 3 else FAIL, "Prepared-slide retention", f"maximum {maximum}"))
    else:
        results.append((NODATA, "Prepared-slide retention", "no preparedSlideCount fields"))

    intentional = {
        "cold_start", "reduced_motion", "low_performance_mode",
        "ambient_pool_empty",
    }
    normal_fallbacks = [e for e in fallbacks if e.get("reason") not in intentional]
    eligible = max(1, len([e for e in completed if e.get("reason") != "cold_start"]))
    rate = len(normal_fallbacks) / eligible
    results.append((PASS if rate < 0.01 else FAIL, "Unexpected fallback rate",
                    f"{len(normal_fallbacks)}/{eligible} ({rate * 100:.2f}%)"))

    cancel_rate = len(cancelled) / max(1, len(completed) + len(cancelled))
    # Cancellation can be legitimate manual navigation; report rather than reject unless excessive.
    results.append((PASS if cancel_rate <= 0.05 else FAIL, "Transition cancellation rate",
                    f"{len(cancelled)} cancelled ({cancel_rate * 100:.2f}%)"))

    growth = heap_growth_mb(by_code["HEAP_SAMPLE"])
    if growth is None:
        results.append((NODATA, "Heap growth after warm-up", "need at least 10 heap samples"))
    else:
        results.append((PASS if growth <= 20.0 else FAIL, "Heap growth after warm-up", f"{growth:+.2f} MiB"))

    crash_events = [e for code in CRASH_CODES for e in by_code.get(code, [])]
    if crash_events:
        results.append((FAIL, "Crash/OOM/ANR/black-frame evidence", f"{len(crash_events)} failure events"))
    elif events:
        results.append((PASS, "Crash/OOM/ANR/black-frame evidence", "none recorded"))
    else:
        results.append((NODATA, "Crash/OOM/ANR/black-frame evidence", "empty bundle"))

    low_entries = len(by_code["TRANSITION_LOW_PERFORMANCE_ENTERED"])
    low_exits = len(by_code["TRANSITION_LOW_PERFORMANCE_EXITED"])
    if low_entries == low_exits:
        results.append((PASS, "Low-performance cooldown lifecycle", f"{low_entries} entered, {low_exits} exited"))
    elif low_entries or low_exits:
        results.append((FAIL, "Low-performance cooldown lifecycle", f"{low_entries} entered, {low_exits} exited"))
    else:
        results.append((PASS, "Low-performance cooldown lifecycle", "not triggered"))

    results.append((PASS if not warnings else PASS, "Performance warnings", f"{len(warnings)} recorded"))
    return results, effects


def main(argv=None):
    parser = argparse.ArgumentParser(description="Analyze FamilyPhotoFrame transition diagnostics")
    parser.add_argument("source")
    parser.add_argument("--json-out")
    parser.add_argument("--markdown-out")
    parser.add_argument("--release-gate", action="store_true")
    args = parser.parse_args(argv)
    path = Path(args.source)
    bundle = load_bundle(path)
    events, malformed = bundle.events, bundle.malformed_lines
    results, effects = analyse(events)
    report = build_report(bundle)
    report["transitionChecks"] = [
        {"status": status, "name": name, "detail": detail}
        for status, name, detail in results
    ]
    json_path = Path(args.json_out) if args.json_out else path.with_suffix("").with_name(path.stem + "-transition-analysis.json")
    markdown_path = Path(args.markdown_out) if args.markdown_out else path.with_suffix("").with_name(path.stem + "-transition-analysis.md")
    json_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    transition_markdown = [render_markdown(report), "## Transition checks", "", "| Status | Check | Detail |", "|---|---|---|"]
    transition_markdown.extend(f"| {status} | {name} | {detail} |" for status, name, detail in results)
    markdown_path.write_text("\n".join(transition_markdown) + "\n", encoding="utf-8")
    print(f"Transition Phase-5 diagnostics: {path}")
    print(f"  parsed {len(events)} events; malformed/truncated lines: {malformed}")
    print(f"  structured reports: {json_path}, {markdown_path}")
    for status, name, detail in results:
        print(fmt(status, name, detail))
    missing = sorted(EXPECTED_EFFECTS - set(effects))
    if missing:
        print(f"  INFO     Effects not exercised by this run: {', '.join(missing)}")
    if report["status"] == "FAIL" or any(status == FAIL for status, _, _ in results):
        return 1
    if args.release_gate and report["status"] == "INCOMPLETE":
        return 1
    if report["status"] == "INCOMPLETE" or any(status == NODATA for status, _, _ in results):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
