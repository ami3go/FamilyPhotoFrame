#!/usr/bin/env python3
"""Crash-free runtime endurance qualification for schema-v1/v2 bundles.

This module deliberately keeps collection and judgement separate.  The Android app
records bounded evidence; this host-side code applies the Phase 1 promotion limits to
the latest build represented in a downloaded bundle.  Missing evidence is reported as
NO DATA, never silently converted into a pass.
"""
from __future__ import annotations

import statistics
from collections import Counter, defaultdict, deque
from dataclasses import asdict, dataclass, field
from typing import Any, Iterable

from diagnostics_analysis import BundleData

PASS = "PASS"
FAIL = "FAIL"
NO_DATA = "NO DATA"

MIB_KB = 1024.0
MIN_PHASE5_VERSION_CODE = 43


@dataclass(frozen=True)
class QualificationProfile:
    key: str
    label: str
    minimum_hours: float
    warmup_hours: float
    minimum_presentations: int
    require_source_cycle: bool = True
    require_lifecycle_cycle: bool = True


PROFILES = {
    "accelerated": QualificationProfile(
        "accelerated", "six-hour accelerated gate", 6.0, 2.0, 100,
    ),
    "day": QualificationProfile(
        "day", "24-hour A/B gate", 24.0, 2.0, 500,
    ),
    "seven-day": QualificationProfile(
        "seven-day", "seven-day promotion gate", 168.0, 2.0, 3_000,
    ),
}


@dataclass
class GateResult:
    key: str
    name: str
    status: str
    detail: str
    required: bool = True
    metrics: dict[str, Any] = field(default_factory=dict)


def _value(event: dict[str, Any], key: str, default: Any = None) -> Any:
    if key in event:
        return event[key]
    fields = event.get("fields")
    if isinstance(fields, dict):
        return fields.get(key, default)
    return default


def _number(event: dict[str, Any], key: str, default: float | None = None) -> float | None:
    value = _value(event, key)
    if value in (None, ""):
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _integer(event: dict[str, Any], key: str, default: int = 0) -> int:
    value = _number(event, key)
    return default if value is None else int(value)


def _boolean(event: dict[str, Any], key: str) -> bool | None:
    value = _value(event, key)
    if isinstance(value, bool):
        return value
    if value in (None, ""):
        return None
    normalized = str(value).strip().lower()
    if normalized in {"true", "1", "yes"}:
        return True
    if normalized in {"false", "0", "no"}:
        return False
    return None


def _time(event: dict[str, Any]) -> int:
    return int(_number(event, "t", _number(event, "atEpochMs", 0.0)) or 0)


def _by_code(events: Iterable[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    result: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        result[str(event.get("code") or "?")].append(event)
    return result


def _latest_phase_run(bundle: BundleData) -> tuple[list[dict[str, Any]], int | None, str, int]:
    """Select the newest app build so retained pre-upgrade evidence cannot pollute a run."""
    starts = sorted(
        (event for event in bundle.events if event.get("code") == "SESSION_START"),
        key=_time,
    )
    versioned = [
        (event, _integer(event, "versionCode", -1))
        for event in starts
        if _integer(event, "versionCode", -1) >= 0
    ]
    if not versioned:
        return list(bundle.events), None, "UNKNOWN", min((_time(e) for e in bundle.events), default=0)
    latest_version = max(version for _, version in versioned)
    matching = [event for event, version in versioned if version == latest_version]
    start_at = min(_time(event) for event in matching)
    app_version = str(_value(matching[-1], "appVersion", "UNKNOWN"))
    return [event for event in bundle.events if _time(event) >= start_at], latest_version, app_version, start_at


def _all_events_run(bundle: BundleData) -> tuple[list[dict[str, Any]], int | None, str, int]:
    starts = sorted(
        (event for event in bundle.events if event.get("code") == "SESSION_START"),
        key=_time,
    )
    version = max((_integer(event, "versionCode", -1) for event in starts), default=-1)
    app_version = str(_value(starts[-1], "appVersion", "UNKNOWN")) if starts else "UNKNOWN"
    start_at = min((_time(event) for event in bundle.events), default=0)
    return list(bundle.events), (version if version >= 0 else None), app_version, start_at


def _session_key(event: dict[str, Any]) -> str:
    return str(event.get("sid") or event.get("sessionId") or "")


def _primary_continuous_session(
    events: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], str, int]:
    """Select one uninterrupted process session for cadence and memory slopes.

    Resource counters reset at process start. Combining equal-version sessions creates a
    synthetic line across reboots/relaunches and can hide a strong in-session leak. Session and
    crash gates still receive the complete build run; only continuous-process measurements use
    this longest sampled session.
    """
    samples_by_session: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for event in events:
        if event.get("code") == "HEAP_SAMPLE":
            samples_by_session[_session_key(event)].append(event)
    if not samples_by_session:
        start = min((_time(event) for event in events), default=0)
        return events, "", start

    def score(item: tuple[str, list[dict[str, Any]]]) -> tuple[int, int, int]:
        _, samples = item
        ordered = sorted(samples, key=_time)
        duration = _time(ordered[-1]) - _time(ordered[0]) if len(ordered) >= 2 else 0
        return duration, len(ordered), _time(ordered[-1])

    session_id, selected_samples = max(samples_by_session.items(), key=score)
    if session_id:
        selected_events = [event for event in events if _session_key(event) == session_id]
    else:
        selected_events = events
    ordered_samples = sorted(selected_samples, key=_time)
    start = _time(ordered_samples[0]) if ordered_samples else min(
        (_time(event) for event in selected_events),
        default=0,
    )
    return selected_events, session_id, start


def _series(
    samples: list[dict[str, Any]], key: str, start_at: int = 0,
) -> tuple[list[tuple[int, float]], float]:
    eligible = [event for event in samples if _time(event) >= start_at]
    values = [
        (_time(event), value)
        for event in eligible
        if (value := _number(event, key)) is not None
    ]
    coverage = len(values) / len(eligible) if eligible else 0.0
    return values, coverage


def _bucket_medians(
    values: list[tuple[int, float]], bucket_ms: int = 15 * 60_000,
) -> list[tuple[int, float]]:
    if not values:
        return []
    origin = values[0][0]
    buckets: dict[int, list[float]] = defaultdict(list)
    for at_ms, value in values:
        buckets[(at_ms - origin) // bucket_ms].append(value)
    return [
        (origin + bucket * bucket_ms, float(statistics.median(items)))
        for bucket, items in sorted(buckets.items())
    ]


def _trend(values: list[tuple[int, float]]) -> dict[str, float] | None:
    points = _bucket_medians(values)
    if len(points) < 6:
        return None
    origin = points[0][0]
    xs = [(at_ms - origin) / 3_600_000.0 for at_ms, _ in points]
    ys = [value for _, value in points]
    mean_x = statistics.fmean(xs)
    mean_y = statistics.fmean(ys)
    denominator = sum((x - mean_x) ** 2 for x in xs)
    slope = 0.0 if denominator == 0.0 else (
        sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys)) / denominator
    )
    edge = max(2, min(8, len(ys) // 5))
    start = float(statistics.median(ys[:edge]))
    end = float(statistics.median(ys[-edge:]))
    return {
        "slopePerHour": slope,
        "projected24h": max(0.0, slope * 24.0),
        "edgeDelta": end - start,
        "startMedian": start,
        "endMedian": end,
        "hours": xs[-1] - xs[0],
    }


def _max_window_growth(
    values: list[tuple[int, float]], window_ms: int,
) -> float | None:
    points = _bucket_medians(values, bucket_ms=30 * 60_000)
    if len(points) < 6:
        return None
    best: float | None = None
    for index, (start_at, _) in enumerate(points):
        candidates = [
            end_index for end_index in range(index + 1, len(points))
            if int(window_ms * 0.9) <= points[end_index][0] - start_at <= int(window_ms * 1.1)
        ]
        if not candidates:
            continue
        end_index = candidates[-1]
        edge = min(2, max(1, (end_index - index + 1) // 4))
        start_value = statistics.median(value for _, value in points[index:index + edge])
        end_value = statistics.median(value for _, value in points[end_index - edge + 1:end_index + 1])
        growth = float(end_value - start_value)
        best = growth if best is None else max(best, growth)
    return best


def _identity_gate(version_code: int | None, app_version: str) -> GateResult:
    if version_code is None:
        return GateResult("identity", "Phase 5 build identity", NO_DATA, "SESSION_START has no versionCode")
    passed = version_code >= MIN_PHASE5_VERSION_CODE
    return GateResult(
        "identity", "Phase 5 build identity", PASS if passed else FAIL,
        f"appVersion {app_version}, versionCode {version_code}; require >= {MIN_PHASE5_VERSION_CODE}",
        metrics={"appVersion": app_version, "versionCode": version_code},
    )


def _coverage_gate(events: list[dict[str, Any]], profile: QualificationProfile) -> GateResult:
    samples = sorted((event for event in events if event.get("code") == "HEAP_SAMPLE"), key=_time)
    if len(samples) < 2:
        return GateResult("coverage", "Continuous runtime coverage", NO_DATA, "fewer than two HEAP_SAMPLE events")
    sample_times = [_time(event) for event in samples]
    gaps = [right - left for left, right in zip(sample_times, sample_times[1:]) if right > left]
    duration_hours = (_time(samples[-1]) - _time(samples[0])) / 3_600_000.0
    median_gap_ms = statistics.median(gaps) if gaps else 0.0
    maximum_gap_ms = max(gaps, default=0)
    cadence_ok = median_gap_ms <= 90_000 and maximum_gap_ms <= 5 * 60_000
    duration_ok = duration_hours >= profile.minimum_hours
    detail = (
        f"{duration_hours:.2f} h, {len(samples)} samples, median gap "
        f"{median_gap_ms / 1000.0:.0f}s, maximum gap {maximum_gap_ms / 1000.0:.0f}s; "
        f"require {profile.minimum_hours:.0f} h"
    )
    return GateResult(
        "coverage", "Continuous runtime coverage", PASS if duration_ok and cadence_ok else FAIL,
        detail,
        metrics={
            "durationHours": duration_hours,
            "sampleCount": len(samples),
            "medianSampleGapMs": median_gap_ms,
            "maximumSampleGapMs": maximum_gap_ms,
        },
    )


def _integrity_gate(bundle: BundleData, events: list[dict[str, Any]]) -> GateResult:
    health = next(
        (item for item in reversed(bundle.metadata) if item.get("recordType") == "diagnosticsHealth"),
        None,
    )
    has_end = any(item.get("recordType") == "bundleEnd" for item in bundle.metadata)
    failures: list[str] = []
    missing: list[str] = []
    if bundle.schema_errors:
        failures.append(f"{len(bundle.schema_errors)} schema error(s)")
    if bundle.privacy_violations:
        failures.append(f"{len(bundle.privacy_violations)} privacy violation(s)")
    if bundle.malformed_lines:
        failures.append(f"{bundle.malformed_lines} malformed/truncated line(s)")
    if not has_end:
        failures.append("bundleEnd missing")
    if health is None:
        missing.append("diagnosticsHealth missing")
    else:
        dropped = int(health.get("droppedTotal") or 0)
        fields_dropped = int(health.get("fieldsDropped") or 0)
        standard_status = str((health.get("standard") or {}).get("retentionStatus") or "UNKNOWN")
        if dropped:
            failures.append(f"{dropped} queued event(s) dropped")
        if fields_dropped:
            failures.append(f"{fields_dropped} field(s) rejected by catalog")
        if standard_status != "COMPLETE":
            failures.append(f"standard evidence retention is {standard_status}")
    codes = _by_code(events)
    for code in ("DIAGNOSTICS_QUEUE_OVERFLOW", "DIAGNOSTICS_FLUSH_TIMEOUT"):
        if codes.get(code):
            failures.append(f"{len(codes[code])} {code} event(s)")
    sink_failures = codes.get("DIAGNOSTICS_SINK_FAILED", [])
    sink_recoveries = codes.get("DIAGNOSTICS_SINK_RECOVERED", [])
    if sink_failures and (not sink_recoveries or _time(sink_recoveries[-1]) < _time(sink_failures[-1])):
        failures.append("diagnostics sink did not recover")
    if failures:
        return GateResult("diagnostics", "Durable diagnostics integrity", FAIL, "; ".join(failures))
    if missing:
        return GateResult("diagnostics", "Durable diagnostics integrity", NO_DATA, "; ".join(missing))
    bulk_status = str((health.get("bulk") or {}).get("retentionStatus") or "UNKNOWN")
    return GateResult(
        "diagnostics", "Durable diagnostics integrity", PASS,
        f"schema/privacy/queue/sink clean; standard COMPLETE, bulk {bulk_status}",
    )


def _failure_gate(events: list[dict[str, Any]]) -> GateResult:
    codes = _by_code(events)
    hard_codes = {
        "UNCAUGHT_EXCEPTION", "PREVIOUS_UNCAUGHT_EXCEPTION", "PREVIOUS_CRASH_EVIDENCE",
        "MAIN_THREAD_STALL_ESCALATED", "PREVIOUS_ANR_EVIDENCE", "ANR",
        "BLACK_FRAME_DETECTED", "RENDERER_CRASH",
    }
    hard = [(code, event) for code in hard_codes for event in codes.get(code, [])]
    benign_exits = {
        "EXIT_SELF", "USER_REQUESTED", "USER_STOPPED", "PERMISSION_CHANGE",
        "PACKAGE_STATE_CHANGE", "PACKAGE_UPDATED", "FREEZER",
    }
    harmful_exits = [
        event for event in codes.get("PROCESS_EXIT_RECORDED", [])
        if str(_value(event, "exitReason", "UNKNOWN")) not in benign_exits
    ]
    if hard or harmful_exits:
        counts = Counter(code for code, _ in hard)
        if harmful_exits:
            counts["harmful process exit"] = len(harmful_exits)
        return GateResult(
            "failures", "Crash, ANR, and native-exit evidence", FAIL,
            ", ".join(f"{name}: {count}" for name, count in sorted(counts.items())),
            metrics={"failureCount": len(hard) + len(harmful_exits)},
        )
    return GateResult(
        "failures", "Crash, ANR, and native-exit evidence", PASS,
        "no crash, escalated stall, black-frame, or harmful exit evidence",
        metrics={"failureCount": 0},
    )


def _session_gate(events: list[dict[str, Any]]) -> GateResult:
    codes = _by_code(events)
    starts = sorted(codes.get("SESSION_START", []), key=_time)
    if not starts:
        return GateResult("sessions", "Process-session continuity", NO_DATA, "no SESSION_START event")
    scheduled = sorted(codes.get("MEMORY_PROCESS_RESTART_SCHEDULED", []), key=_time)
    completed = sorted(codes.get("MEMORY_PROCESS_RECOVERY_COMPLETED", []), key=_time)
    unexplained: list[str] = []
    explained = 0
    for start in starts[1:]:
        at_ms = _time(start)
        kind = str(_value(start, "processStartKind", "UNKNOWN"))
        prior = [event for event in scheduled if 0 <= at_ms - _time(event) <= 5 * 60_000]
        after = [event for event in completed if 0 <= _time(event) - at_ms <= 5 * 60_000]
        if kind == "PROCESS_RESTART_SAME_BOOT" and prior and after:
            explained += 1
        else:
            unexplained.append(f"{kind}@{at_ms}")
    if unexplained:
        return GateResult(
            "sessions", "Process-session continuity", FAIL,
            f"{len(starts)} sessions, {len(unexplained)} unexplained restart(s): {', '.join(unexplained[:3])}",
            metrics={"sessionCount": len(starts), "explainedRestarts": explained, "unexplainedRestarts": len(unexplained)},
        )
    return GateResult(
        "sessions", "Process-session continuity", PASS,
        f"{len(starts)} session(s), {explained} controlled memory-recovery restart(s)",
        metrics={"sessionCount": len(starts), "explainedRestarts": explained, "unexplainedRestarts": 0},
    )


def _heap_gate(samples: list[dict[str, Any]], steady_start: int) -> GateResult:
    values, coverage = _series(samples, "heapUsedKb", steady_start)
    maxima, max_coverage = _series(samples, "heapMaxKb", steady_start)
    if coverage < 0.9 or max_coverage < 0.9 or len(values) < 12:
        return GateResult(
            "heap", "Java-heap floor growth", NO_DATA,
            f"heapUsedKb coverage {coverage * 100:.1f}%, heapMaxKb coverage {max_coverage * 100:.1f}%",
        )
    growth = _max_window_growth(values, 6 * 3_600_000)
    if growth is None:
        return GateResult("heap", "Java-heap floor growth", NO_DATA, "less than one measurable six-hour window")
    heap_max = statistics.median(value for _, value in maxima)
    limit = heap_max * 0.05
    return GateResult(
        "heap", "Java-heap floor growth", PASS if growth < limit else FAIL,
        f"worst robust six-hour growth {growth / MIB_KB:+.2f} MiB; limit {limit / MIB_KB:.2f} MiB (5% heap)",
        metrics={"maximumSixHourGrowthKb": growth, "limitKb": limit, "coverage": coverage},
    )


def _memory_metric_gate(
    samples: list[dict[str, Any]], steady_start: int, key: str, gate_key: str, label: str,
) -> GateResult:
    values, coverage = _series(samples, key, steady_start)
    trend = _trend(values)
    if coverage < 0.9 or trend is None:
        return GateResult(
            gate_key, label, NO_DATA,
            f"{key} coverage {coverage * 100:.1f}% with {len(values)} usable samples",
        )
    observed_24h = _max_window_growth(values, 24 * 3_600_000)
    growth = max(0.0, trend["edgeDelta"], trend["projected24h"], observed_24h or 0.0)
    limit = 10 * 1024.0
    status = PASS if growth < limit else FAIL
    return GateResult(
        gate_key, label, status,
        f"slope {trend['slopePerHour']:+.1f} KiB/h, edge {trend['edgeDelta'] / MIB_KB:+.2f} MiB, "
        f"24 h projection {trend['projected24h'] / MIB_KB:.2f} MiB; limit <10 MiB",
        metrics={**trend, "maximumGrowthKb": growth, "limitKb": limit, "coverage": coverage},
    )


def _count_trend_gate(
    samples: list[dict[str, Any]], steady_start: int, key: str, gate_key: str, label: str,
    limit: float,
) -> GateResult:
    values, coverage = _series(samples, key, steady_start)
    trend = _trend(values)
    if coverage < 0.9 or trend is None:
        return GateResult(gate_key, label, NO_DATA, f"{key} coverage {coverage * 100:.1f}%")
    growth = max(0.0, trend["edgeDelta"], trend["projected24h"])
    return GateResult(
        gate_key, label, PASS if growth <= limit else FAIL,
        f"slope {trend['slopePerHour']:+.3f}/h, edge {trend['edgeDelta']:+.1f}, "
        f"24 h projection {trend['projected24h']:.1f}; limit <= {limit:.0f}",
        metrics={**trend, "maximumGrowth": growth, "limit": limit, "coverage": coverage},
    )


def _resource_gate(samples: list[dict[str, Any]], steady_start: int) -> GateResult:
    required = (
        "smbActiveStreams", "smbStreamsOpened", "smbStreamsClosed", "smbOldestStreamAgeMs",
        "mediaActiveTransfers", "mediaTransfersStarted", "mediaTransfersFinished",
        "mediaOldestTransferAgeMs", "smbTrackingSaturated", "mediaTrackingSaturated",
    )
    eligible = [event for event in samples if _time(event) >= steady_start]
    if not eligible:
        return GateResult("resources", "SMB and media-transfer ownership", NO_DATA, "no post-warm-up samples")
    missing = [
        key for key in required
        if sum(_value(event, key) not in (None, "") for event in eligible) / len(eligible) < 0.9
    ]
    if missing:
        return GateResult("resources", "SMB and media-transfer ownership", NO_DATA, f"missing coverage: {', '.join(missing)}")
    violations: list[str] = []
    max_smb_age = 0
    max_media_age = 0
    max_media_active = 0
    for event in eligible:
        smb_active = _integer(event, "smbActiveStreams")
        smb_outstanding = _integer(event, "smbStreamsOpened") - _integer(event, "smbStreamsClosed")
        media_active = _integer(event, "mediaActiveTransfers")
        media_outstanding = _integer(event, "mediaTransfersStarted") - _integer(event, "mediaTransfersFinished")
        if smb_outstanding != smb_active:
            violations.append("SMB opened-closed != active")
        if media_outstanding != media_active:
            violations.append("media started-finished != active")
        if media_active > 2:
            violations.append("more than two active media transfers")
        if _boolean(event, "smbTrackingSaturated") is True:
            violations.append("SMB tracking saturated")
        if _boolean(event, "mediaTrackingSaturated") is True:
            violations.append("media tracking saturated")
        max_smb_age = max(max_smb_age, _integer(event, "smbOldestStreamAgeMs"))
        max_media_age = max(max_media_age, _integer(event, "mediaOldestTransferAgeMs"))
        max_media_active = max(max_media_active, media_active)
    if max_smb_age > 120_000:
        violations.append("SMB stream older than two minutes")
    if max_media_age > 120_000:
        violations.append("media transfer older than two minutes")
    unique = sorted(set(violations))
    return GateResult(
        "resources", "SMB and media-transfer ownership", FAIL if unique else PASS,
        "; ".join(unique) if unique else (
            f"counter identities hold; max SMB age {max_smb_age / 1000:.0f}s, "
            f"max transfer age {max_media_age / 1000:.0f}s, max transfers {max_media_active}"
        ),
        metrics={
            "maximumSmbStreamAgeMs": max_smb_age,
            "maximumMediaTransferAgeMs": max_media_age,
            "maximumActiveMediaTransfers": max_media_active,
        },
    )


def _bitmap_gate(samples: list[dict[str, Any]], sessions: list[dict[str, Any]], steady_start: int) -> GateResult:
    eligible = [event for event in samples if _time(event) >= steady_start]
    keys = ("preparedSlideCount", "renderedSlideCount", "pendingDisposals")
    if not eligible or any(
        sum(_value(event, key) not in (None, "") for event in eligible) / len(eligible) < 0.9
        for key in keys
    ):
        return GateResult("bitmaps", "Prepared-bitmap ownership", NO_DATA, "bitmap inventory coverage is incomplete")
    low_memory = any(_boolean(event, "economyBaseline") is True for event in eligible) or any(
        0 < _integer(event, "memoryClassMb") <= 128 for event in sessions
    )
    prepared_limit = 3 if low_memory else 4
    maximum_prepared = max(_integer(event, "preparedSlideCount") for event in eligible)
    maximum_rendered = max(_integer(event, "renderedSlideCount") for event in eligible)
    pending_runs: list[int] = []
    pending_start: int | None = None
    previous_event: dict[str, Any] | None = None
    for event in eligible:
        if previous_event is not None and (
            event.get("sessionId") != previous_event.get("sessionId") or
            _time(event) - _time(previous_event) > 5 * 60_000
        ):
            if pending_start is not None:
                pending_runs.append(_time(previous_event) - pending_start)
                pending_start = None
        if _integer(event, "pendingDisposals") > 0 and pending_start is None:
            pending_start = _time(event)
        elif _integer(event, "pendingDisposals") == 0 and pending_start is not None:
            pending_runs.append(_time(event) - pending_start)
            pending_start = None
        previous_event = event
    if pending_start is not None:
        pending_runs.append(_time(eligible[-1]) - pending_start)
    maximum_pending_ms = max(pending_runs, default=0)
    violations = []
    if maximum_prepared > prepared_limit:
        violations.append(f"prepared slides reached {maximum_prepared} (limit {prepared_limit})")
    if maximum_rendered > 2:
        violations.append(f"rendered slides reached {maximum_rendered} (limit 2)")
    if maximum_pending_ms > 5 * 60_000:
        violations.append(f"pending disposals persisted {maximum_pending_ms / 1000:.0f}s")
    return GateResult(
        "bitmaps", "Prepared-bitmap ownership", FAIL if violations else PASS,
        "; ".join(violations) if violations else (
            f"max prepared {maximum_prepared}/{prepared_limit}, max rendered {maximum_rendered}/2, "
            f"longest pending-disposal run {maximum_pending_ms / 1000:.0f}s"
        ),
        metrics={
            "lowMemoryProfile": low_memory,
            "maximumPreparedSlides": maximum_prepared,
            "maximumRenderedSlides": maximum_rendered,
            "maximumPendingDisposalDurationMs": maximum_pending_ms,
        },
    )


def _render_gate(events: list[dict[str, Any]], start_at: int, minimum: int) -> GateResult:
    eligible = [event for event in events if _time(event) >= start_at]
    retained_rendered = [
        event for event in eligible
        if event.get("code") in {"SLIDE_RENDERED", "COLLAGE_RENDERED"}
    ]
    if not retained_rendered:
        return GateResult(
            "render_ack", "Render-ack timeout rate", NO_DATA,
            f"0 rendered presentations; require at least {minimum}",
            metrics={"renderedPresentations": 0, "timeouts": 0},
        )
    # RENDER_ACK_TIMEOUT is in the durable standard stream, while rendered events use
    # the high-volume stream and may rotate. Compare only their overlapping time range.
    overlap_start = min(_time(event) for event in retained_rendered)
    rendered = sum(_time(event) >= overlap_start for event in retained_rendered)
    timeouts = sum(
        event.get("code") == "RENDER_ACK_TIMEOUT" and _time(event) >= overlap_start
        for event in eligible
    )
    if rendered < minimum:
        return GateResult(
            "render_ack", "Render-ack timeout rate", NO_DATA,
            f"{rendered} rendered presentations; require at least {minimum}",
            metrics={"renderedPresentations": rendered, "timeouts": timeouts, "overlapStartEpochMs": overlap_start},
        )
    rate = timeouts / rendered
    return GateResult(
        "render_ack", "Render-ack timeout rate", PASS if rate < 0.001 else FAIL,
        f"{timeouts}/{rendered} ({rate * 100:.3f}%); limit <0.1%",
        metrics={
            "renderedPresentations": rendered, "timeouts": timeouts, "rate": rate,
            "overlapStartEpochMs": overlap_start,
        },
    )


def _transition_gates(events: list[dict[str, Any]], start_at: int, minimum: int) -> list[GateResult]:
    codes = _by_code(event for event in events if _time(event) >= start_at)
    completed = codes.get("TRANSITION_COMPLETED", [])
    cancelled = codes.get("TRANSITION_CANCELLED", [])
    minimum_transitions = max(50, minimum // 2)
    if len(completed) + len(cancelled) < minimum_transitions:
        rate_gate = GateResult(
            "transition_rate", "Unplanned transition cancellation rate", NO_DATA,
            f"{len(completed) + len(cancelled)} terminal transitions; require at least {minimum_transitions}",
        )
    else:
        intentional = {"HOST_LIFECYCLE", "USER_NAVIGATION", "ENGINE_PAUSE", "CONFIGURATION_CHANGE"}
        unexpected = [
            event for event in cancelled
            if str(_value(event, "cancellationInitiator", "")) not in intentional
        ]
        denominator = len(completed) + len(unexpected)
        rate = len(unexpected) / max(1, denominator)
        rate_gate = GateResult(
            "transition_rate", "Unplanned transition cancellation rate", PASS if rate < 0.05 else FAIL,
            f"{len(unexpected)} unplanned / {denominator} ({rate * 100:.2f}%); "
            f"{len(cancelled) - len(unexpected)} lifecycle/user cancellations excluded; limit <5%",
            metrics={
                "completed": len(completed), "cancelled": len(cancelled),
                "unexpectedCancelled": len(unexpected), "rate": rate,
            },
        )

    lifecycle_events = codes.get("TRANSITION_STARTED", []) + completed + cancelled
    if not lifecycle_events:
        correlation_gate = GateResult(
            "transition_correlation", "Transition generation correlation", NO_DATA,
            "no transition lifecycle events",
        )
    else:
        def key(event: dict[str, Any]) -> tuple[str, str, str] | None:
            values = (
                str(event.get("sessionId") or event.get("sid") or ""),
                str(_value(event, "hostGeneration", "")),
                str(_value(event, "transitionGeneration", "")),
            )
            return values if all(values) else None

        keyed = [(event, key(event)) for event in lifecycle_events]
        coverage = sum(item is not None for _, item in keyed) / len(keyed)
        started_counts = Counter(item for event, item in keyed if event.get("code") == "TRANSITION_STARTED" and item)
        terminal_counts = Counter(item for event, item in keyed if event.get("code") != "TRANSITION_STARTED" and item)
        malformed = {
            item for item in set(started_counts) | set(terminal_counts)
            if started_counts[item] != 1 or terminal_counts[item] != 1
        }
        passed = coverage >= 0.95 and not malformed
        correlation_gate = GateResult(
            "transition_correlation", "Transition generation correlation", PASS if passed else FAIL,
            f"key coverage {coverage * 100:.1f}%; {len(malformed)} unmatched/duplicate generation(s)",
            metrics={"coverage": coverage, "malformedGenerations": len(malformed)},
        )
    return [rate_gate, correlation_gate]


def _controller_gate(events: list[dict[str, Any]], start_at: int) -> GateResult:
    changes = sorted(
        (event for event in events if event.get("code") == "MEMORY_PROTECTION_CHANGED" and _time(event) >= start_at),
        key=_time,
    )
    if not changes:
        return GateResult(
            "controller", "Adaptive-controller stability", PASS,
            "no protection-level change was needed during the steady-state window",
            metrics={"changes": 0, "maximumChangesPerHour": 0},
        )
    duplicate_levels = sum(
        1 for left, right in zip(changes, changes[1:])
        if str(_value(left, "memoryProtectionLevel", "")) == str(_value(right, "memoryProtectionLevel", ""))
    )
    window: deque[int] = deque()
    maximum_per_hour = 0
    for event in changes:
        at_ms = _time(event)
        window.append(at_ms)
        while window and at_ms - window[0] > 3_600_000:
            window.popleft()
        maximum_per_hour = max(maximum_per_hour, len(window))
    passed = duplicate_levels == 0 and maximum_per_hour <= 4
    return GateResult(
        "controller", "Adaptive-controller stability", PASS if passed else FAIL,
        f"{len(changes)} level change(s), {duplicate_levels} consecutive duplicate level(s), "
        f"maximum {maximum_per_hour}/h; limit <=4/h",
        metrics={
            "changes": len(changes), "consecutiveDuplicateLevels": duplicate_levels,
            "maximumChangesPerHour": maximum_per_hour,
        },
    )


def _self_recovery_gate(events: list[dict[str, Any]]) -> GateResult:
    codes = _by_code(events)
    gc_events = sorted(codes.get("MEMORY_SELF_RECOVERY_GC", []), key=_time)
    restarts = sorted(codes.get("MEMORY_PROCESS_RESTART_SCHEDULED", []), key=_time)
    completed = sorted(codes.get("MEMORY_PROCESS_RECOVERY_COMPLETED", []), key=_time)
    failures = codes.get("MEMORY_PROCESS_RESTART_FAILED", [])
    suppressions = codes.get("MEMORY_PROCESS_RESTART_SUPPRESSED", [])
    violations: list[str] = []
    if any(_time(right) - _time(left) < 10 * 60_000 for left, right in zip(gc_events, gc_events[1:])):
        violations.append("GC requests occurred less than 10 minutes apart")
    if any(_time(right) - _time(left) < 15 * 60_000 for left, right in zip(restarts, restarts[1:])):
        violations.append("process restarts occurred less than 15 minutes apart")
    for restart in restarts:
        if not any(0 <= _time(done) - _time(restart) <= 10 * 60_000 for done in completed):
            violations.append("scheduled restart lacks completion evidence")
    if failures:
        violations.append(f"{len(failures)} restart failure event(s)")
    if suppressions:
        violations.append(f"{len(suppressions)} restart-loop suppression event(s)")
    return GateResult(
        "self_recovery", "GC and process-recovery rate limits", FAIL if violations else PASS,
        "; ".join(violations) if violations else (
            f"{len(gc_events)} GC request(s), {len(restarts)} controlled restart(s), "
            f"{len(completed)} completion event(s)"
        ),
        metrics={"gcRequests": len(gc_events), "scheduledRestarts": len(restarts), "completedRestarts": len(completed)},
    )


def _source_recovery_gate(events: list[dict[str, Any]], required: bool) -> GateResult:
    codes = _by_code(events)
    losses = sorted(codes.get("SOURCE_UNAVAILABLE", []), key=_time)
    recovered = sorted(codes.get("SOURCE_RECOVERED", []), key=_time)
    rendered = sorted(codes.get("SLIDE_RENDERED", []) + codes.get("COLLAGE_RENDERED", []), key=_time)
    if not losses or not recovered:
        return GateResult(
            "source_recovery", "NAS loss and recovery", NO_DATA,
            f"{len(losses)} loss event(s), {len(recovered)} recovery event(s); scenario not completed",
            required=required,
        )
    failures = 0
    worst_gap_ms = 0
    for loss in losses:
        recovery = next((event for event in recovered if _time(event) > _time(loss)), None)
        if recovery is None:
            failures += 1
            continue
        shown = next((event for event in rendered if _time(event) > _time(recovery)), None)
        if shown is None:
            failures += 1
            continue
        worst_gap_ms = max(worst_gap_ms, _time(shown) - _time(recovery))
    if worst_gap_ms > 15 * 60_000:
        failures += 1
    return GateResult(
        "source_recovery", "NAS loss and recovery", FAIL if failures else PASS,
        f"{len(losses)} loss event(s), {len(recovered)} recovery event(s), "
        f"worst recovery-to-render gap {worst_gap_ms / 1000:.0f}s",
        required=required,
        metrics={"losses": len(losses), "recoveries": len(recovered), "worstRenderGapMs": worst_gap_ms},
    )


def _lifecycle_gate(events: list[dict[str, Any]], required: bool) -> GateResult:
    ordered = sorted(events, key=_time)
    stops = [event for event in ordered if event.get("code") == "ACTIVITY_STOPPED"]
    if not stops:
        return GateResult(
            "lifecycle", "Activity-stop render fencing", NO_DATA,
            "no ACTIVITY_STOPPED interval was exercised", required=required,
        )
    forbidden = {"TRANSITION_STARTED", "SLIDE_RENDERED", "COLLAGE_RENDERED"}
    complete = 0
    violations: list[str] = []
    for stop in stops:
        start = next(
            (event for event in ordered if event.get("code") == "ACTIVITY_STARTED" and _time(event) > _time(stop)),
            None,
        )
        if start is None:
            violations.append("stopped interval has no later ACTIVITY_STARTED")
            continue
        complete += 1
        leaked = [
            event for event in ordered
            if _time(stop) < _time(event) < _time(start) and event.get("code") in forbidden
        ]
        if leaked:
            violations.append(f"{len(leaked)} render/transition event(s) while stopped")
    return GateResult(
        "lifecycle", "Activity-stop render fencing", FAIL if violations else PASS,
        "; ".join(violations) if violations else f"{complete} stopped interval(s), no stale render or transition",
        required=required,
        metrics={"completeIntervals": complete, "violations": len(violations)},
    )


def _summary_metrics(events: list[dict[str, Any]], warmup_hours: float) -> dict[str, Any]:
    codes = _by_code(events)
    samples = sorted(codes.get("HEAP_SAMPLE", []), key=_time)
    run_start = _time(samples[0]) if samples else min((_time(event) for event in events), default=0)
    steady_start = run_start + int(warmup_hours * 3_600_000)
    duration_hours = (
        (_time(samples[-1]) - _time(samples[0])) / 3_600_000.0 if len(samples) >= 2 else None
    )
    metrics: dict[str, Any] = {"durationHours": duration_hours}
    for key, label in (("pssKb", "pss"), ("nativePssKb", "nativePss"), ("nativeHeapKb", "nativeHeap")):
        values, _ = _series(samples, key, steady_start)
        trend = _trend(values)
        metrics[f"{label}SlopeKbPerHour"] = trend["slopePerHour"] if trend else None
        metrics[f"{label}Projected24hKb"] = trend["projected24h"] if trend else None
    rendered = len(codes.get("SLIDE_RENDERED", [])) + len(codes.get("COLLAGE_RENDERED", []))
    timeouts = len(codes.get("RENDER_ACK_TIMEOUT", []))
    completed = len(codes.get("TRANSITION_COMPLETED", []))
    cancelled = len(codes.get("TRANSITION_CANCELLED", []))
    metrics.update({
        "renderedPresentations": rendered,
        "renderAckTimeouts": timeouts,
        "renderAckTimeoutRate": timeouts / rendered if rendered else None,
        "transitionCompleted": completed,
        "transitionCancelled": cancelled,
        "transitionCancellationRate": cancelled / (completed + cancelled) if completed + cancelled else None,
        "lowMemoryCallbacks": len(codes.get("LOW_MEMORY", [])),
        "lowMemoryCallbacksPerHour": (
            len(codes.get("LOW_MEMORY", [])) / duration_hours if duration_hours and duration_hours > 0 else None
        ),
        "sessionCount": len(codes.get("SESSION_START", [])),
    })
    steady_samples = [sample for sample in samples if _time(sample) >= steady_start]
    if steady_samples:
        first, last = steady_samples[0], steady_samples[-1]
        for prefix, label in (
            ("nativeDecode", "decode"),
            ("nativeBoundsProbe", "boundsProbe"),
            ("nativeCacheVerify", "cacheVerify"),
            ("nativeGenerated", "generatedBitmap"),
            ("nativeTransition", "transition"),
        ):
            metrics[f"{label}Started"] = max(
                0,
                _integer(last, f"{prefix}Started") - _integer(first, f"{prefix}Started"),
            )
            metrics[f"{label}Completed"] = max(
                0,
                _integer(last, f"{prefix}Completed") - _integer(first, f"{prefix}Completed"),
            )
            metrics[f"{label}NetNativeDeltaKb"] = (
                _integer(last, f"{prefix}NetDeltaKb") -
                _integer(first, f"{prefix}NetDeltaKb")
            )
    return metrics


def _comparison(candidate: dict[str, Any], baseline: dict[str, Any] | None) -> list[dict[str, Any]]:
    if baseline is None:
        return []
    rows = []
    for key, label, percent in (
        ("pssProjected24hKb", "Projected total-PSS growth / 24 h", False),
        ("nativePssProjected24hKb", "Projected native-PSS growth / 24 h", False),
        ("renderAckTimeoutRate", "Render-ack timeout rate", True),
        ("transitionCancellationRate", "Raw transition cancellation rate", True),
        ("lowMemoryCallbacksPerHour", "LOW_MEMORY callbacks / h", False),
    ):
        before = baseline.get(key)
        after = candidate.get(key)
        if before is None or after is None:
            outcome = NO_DATA
        elif after < before:
            outcome = "IMPROVED"
        elif after > before:
            outcome = "WORSE"
        else:
            outcome = "UNCHANGED"
        rows.append({
            "metric": label,
            "baseline": before * 100.0 if percent and before is not None else before,
            "candidate": after * 100.0 if percent and after is not None else after,
            "unit": "%" if percent else ("KiB" if "growth" in label else "count/h"),
            "outcome": outcome,
        })
    return rows


def qualify(
    bundle: BundleData,
    profile_key: str = "day",
    baseline_bundle: BundleData | None = None,
) -> dict[str, Any]:
    profile = PROFILES[profile_key]
    build_events, version_code, app_version, _ = _latest_phase_run(bundle)
    events, selected_session_id, run_start = _primary_continuous_session(build_events)
    codes = _by_code(events)
    samples = sorted(codes.get("HEAP_SAMPLE", []), key=_time)
    sessions = sorted(codes.get("SESSION_START", []), key=_time)
    steady_start = run_start + int(profile.warmup_hours * 3_600_000)

    gates = [
        _identity_gate(version_code, app_version),
        _coverage_gate(events, profile),
        _integrity_gate(bundle, build_events),
        _failure_gate(build_events),
        _session_gate(build_events),
        _heap_gate(samples, steady_start),
        _memory_metric_gate(samples, steady_start, "pssKb", "pss", "Total-PSS growth"),
        _memory_metric_gate(samples, steady_start, "nativePssKb", "native_pss", "Native-PSS growth"),
        _count_trend_gate(samples, steady_start, "openFdCount", "fds", "Open-file-descriptor growth", 8),
        _count_trend_gate(samples, steady_start, "threadCount", "threads", "Thread-count growth", 8),
        _resource_gate(samples, steady_start),
        _bitmap_gate(samples, sessions, steady_start),
        _render_gate(events, steady_start, profile.minimum_presentations),
        *_transition_gates(events, steady_start, profile.minimum_presentations),
        _controller_gate(events, steady_start),
        _self_recovery_gate(build_events),
        _source_recovery_gate(events, profile.require_source_cycle),
        _lifecycle_gate(events, profile.require_lifecycle_cycle),
    ]
    required_failures = [gate for gate in gates if gate.required and gate.status == FAIL]
    required_missing = [gate for gate in gates if gate.required and gate.status == NO_DATA]
    status = FAIL if required_failures else ("INCOMPLETE" if required_missing else PASS)

    candidate_metrics = _summary_metrics(events, profile.warmup_hours)
    baseline_metrics = None
    if baseline_bundle is not None:
        baseline_build_events, _, _, _ = _all_events_run(baseline_bundle)
        baseline_events, _, _ = _primary_continuous_session(baseline_build_events)
        baseline_metrics = _summary_metrics(baseline_events, profile.warmup_hours)

    return {
        "reportSchemaVersion": 1,
        "status": status,
        "profile": asdict(profile),
        "candidate": {
            "appVersion": app_version,
            "versionCode": version_code,
            "runStartEpochMs": run_start,
            "selectedSessionId": selected_session_id,
            "eventCount": len(events),
            "buildEventCount": len(build_events),
            "metrics": candidate_metrics,
        },
        "baseline": {"metrics": baseline_metrics} if baseline_metrics is not None else None,
        "gates": [asdict(gate) for gate in gates],
        "comparison": _comparison(candidate_metrics, baseline_metrics),
        "summary": {
            "pass": sum(gate.status == PASS for gate in gates),
            "fail": sum(gate.status == FAIL for gate in gates),
            "noData": sum(gate.status == NO_DATA for gate in gates),
            "requiredFailures": len(required_failures),
            "requiredNoData": len(required_missing),
        },
    }


def render_markdown(report: dict[str, Any]) -> str:
    candidate = report["candidate"]
    profile = report["profile"]
    lines = [
        "# FamilyPhotoFrame crash-free qualification",
        "",
        f"Status: **{report['status']}**",
        "",
        f"Candidate: `{candidate['appVersion']}` (`versionCode {candidate['versionCode']}`)",
        f"Profile: {profile['label']}",
        "",
        "## Gates",
        "",
        "| Status | Required | Gate | Evidence |",
        "|---|---:|---|---|",
    ]
    for gate in report["gates"]:
        detail = str(gate["detail"]).replace("|", "\\|")
        lines.append(
            f"| {gate['status']} | {'yes' if gate['required'] else 'no'} | "
            f"{gate['name']} | {detail} |"
        )
    if report["comparison"]:
        lines += [
            "",
            "## A/B comparison",
            "",
            "| Metric | Baseline | Candidate | Outcome |",
            "|---|---:|---:|---|",
        ]
        for row in report["comparison"]:
            def display(value: Any) -> str:
                return "NO DATA" if value is None else f"{value:.3f} {row['unit']}"
            lines.append(
                f"| {row['metric']} | {display(row['baseline'])} | "
                f"{display(row['candidate'])} | {row['outcome']} |"
            )
    lines += [
        "",
        "## Interpretation",
        "",
        "A FAIL is measured evidence outside a bound. NO DATA means the bundle did not "
        "contain enough evidence to judge a required gate; it is not a pass.",
        "",
    ]
    return "\n".join(lines)
