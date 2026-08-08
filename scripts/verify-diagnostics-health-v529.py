#!/usr/bin/env python3
"""Static Phase-6 stream, aggregation, storage, and health contracts."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/com/example/familyphotoframe/data/diagnostics"


def main() -> int:
    failures: list[str] = []
    catalog = (BASE / "DiagnosticEventSpec.kt").read_text()
    rate = (BASE / "DiagnosticRateController.kt").read_text()
    log = (BASE / "DiagnosticsLog.kt").read_text()
    health = (BASE / "DiagnosticsHealthSnapshot.kt").read_text()
    sink = (BASE / "FileDiagnosticsSink.kt").read_text()
    services = (ROOT / "app/src/main/java/com/example/familyphotoframe/ServiceLocator.kt").read_text()
    checks = (ROOT / "scripts/verify/DiagnosticsRateAndHealthChecks.kt").read_text()

    def need(text: str, value: str, label: str) -> None:
        if value not in text:
            failures.append(f"missing {label}: {value}")

    for code in (
        "SLIDE_SELECTED", "SLIDE_RENDERED", "SLIDE_SHOWN", "TRANSITION_SELECTED",
        "TRANSITION_STARTED", "TRANSITION_COMPLETED", "TRANSITION_CANCELLED",
        "TRANSITION_FALLBACK", "SHUFFLE_SELECTION_TIMING", "FOLDER_RESERVED",
        "PHOTO_RESERVED", "PHOTO_CONSUMED", "FOLDER_PRESENTED",
        "PRESENTATION_COMMITTED", "PRESENTATION_RELEASED", "PRESENTATION_PREPARED_COMMIT",
    ):
        need(catalog, f'"{code}"', f"catalog code {code}")
    need(catalog, "if (it in bulkEngineCodes) DiagnosticStream.BULK", "catalog stream routing")
    if "isHighVolume(" in log:
        failures.append("hard-coded high-volume routing remains")
    for marker in (
        "BRIGHTNESS_HEARTBEAT_MS = 15L", "FIRST_DECODE_EVENTS = 3",
        '"DECODE_FAILURE_SUMMARY"', '"PREVIEW_HIT_SUMMARY"', "DEFAULT_CAPACITY = 64",
    ):
        need(rate, marker, "bounded aggregation contract")
    for field in (
        "queueCapacity", "queueDepth", "droppedTotal", "droppedSinceLastReport",
        "retainedBytes", "retainedGenerations", "oldestKnownSessionId",
        "newestKnownSequence", "lastSuccessfulWriteEpochMs", "lastSuccessfulFlushEpochMs",
        "lastAppendErrorClass", "lastFlushTimeoutMs", "fieldsDropped",
        "crashEnvelopePresent", "crashEnvelopeBytes",
    ):
        need(health, field, f"health field {field}")
    for code in (
        "DIAGNOSTICS_QUEUE_OVERFLOW", "DIAGNOSTICS_SINK_FAILED",
        "DIAGNOSTICS_SINK_RECOVERED", "DIAGNOSTICS_FLUSH_TIMEOUT",
    ):
        need(log + catalog, f'"{code}"', f"writer health event {code}")
    need(log, "never recurse through a failing sink", "non-recursive sink reporting")
    need(sink, "retainedGenerations", "rotation generation stats")
    need(checks, "repeat(100_000)", "100,000-event endurance test")
    need(checks, "all failed enqueue attempts counted", "exact saturation assertion")

    # Default production budget: 3 * 2 MiB standard + 3 * 4 MiB bulk = 18 MiB.
    need(services, 'FileDiagnosticsSink(File(appContext.filesDir, "diagnostics"))', "2 MiB standard sink")
    need(services, "maxBytes = 4L * 1024 * 1024", "4 MiB bulk sink")
    need(services, "keepGenerations = 2", "three bulk generations")

    if failures:
        print("V52.9 DIAGNOSTICS HEALTH CONTRACT FAILED")
        for failure in failures:
            print("  -", failure)
        return 1
    print("  stream routing, aggregation, 18 MiB budget, and health contracts passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
