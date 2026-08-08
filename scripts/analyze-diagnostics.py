#!/usr/bin/env python3
"""Analyse a downloaded diagnostics bundle against the Phase 1 gate (spec §22.2).

Usage:
    python3 scripts/analyze-diagnostics.py familyphotoframe-diagnostics.jsonl

Download the bundle from the frame's web panel (Diagnostics -> "Download full log"),
then run this. It reports each Phase 1 acceptance criterion as PASS / FAIL / NO DATA.

**NO DATA is not PASS.** The distinction is the whole point: a criterion with no
supporting events means the run did not exercise it, and reporting that as success would
be worse than useless. Several criteria can only be satisfied by a long run with a real
NAS being switched off and on.
"""
import argparse
import json
import sys
from collections import Counter, defaultdict
from diagnostics_analysis import build_report, default_report_paths, load_bundle, write_reports

PASS, FAIL, NODATA = "PASS", "FAIL", "NO DATA"


def load(path):
    bundle = load_bundle(path)
    return bundle.events, bundle.malformed_lines


def by_code(events):
    d = defaultdict(list)
    for e in events:
        d[e.get("code", "?")].append(e)
    return d


def hours(ms):
    return ms / 3_600_000.0


def fmt(status, name, detail):
    return "  %-8s %-46s %s" % (status, name, detail)



def rendered_slides(codes):
    """Verified single/collage presentations; accept legacy SLIDE_SHOWN bundles."""
    verified = codes.get("SLIDE_RENDERED", []) + codes.get("COLLAGE_RENDERED", [])
    return sorted(verified, key=lambda e: e.get("t", 0)) or codes.get("SLIDE_SHOWN", [])


def event_source(event):
    return event.get("sourceKind") or event.get("source") or "?"


def presentation_identity(event):
    return event.get("presentationToken") or event.get("photoId") or event.get("id")

def check_merged_sources(codes, truncated):
    """Randomized slideshow from one SAF + one SMB source."""
    slides = rendered_slides(codes)
    if not slides:
        return NODATA, "no slides recorded"
    sources = Counter(event_source(s) for s in slides)
    distinct = [s for s in sources if s not in ("fallback", "?")]
    pools = codes.get("POOL_CONFIGURED", [])
    merged = [p for p in pools if int(p.get("primaryCount", 0) or 0) >= 2]
    detail = "sources played: %s" % dict(sources)
    if len(distinct) >= 2 and merged:
        return PASS, detail
    if len(distinct) >= 2 and not pools:
        # Two sources demonstrably played, so the pool must have been merged even though
        # the record of it is not in the retained log.
        return PASS, detail + " (pool record not retained, but two sources played)"
    if merged:
        return FAIL, detail + " (pool merged but only one source ever played)"
    if not pools:
        return NODATA, detail + " (no pool record retained; cannot tell)"
    return FAIL, detail + " (no merged multi-source pool was configured)"


def check_randomized(codes):
    slides = rendered_slides(codes)
    if len(slides) < 20:
        return NODATA, "only %d slides; need a longer run" % len(slides)
    ids = [presentation_identity(s) for s in slides]
    distinct = len(set(ids))
    immediate_repeats = sum(1 for a, b in zip(ids, ids[1:]) if a == b)
    detail = "%d slides, %d distinct, %d immediate repeats" % (len(ids), distinct, immediate_repeats)
    if distinct < 2:
        return FAIL, detail + " (same photo throughout)"
    if immediate_repeats:
        return FAIL, detail + " (a photo repeated back-to-back)"
    return PASS, detail


def check_no_per_slide_enumeration(codes):
    """Room-indexed playback: scans must not accompany every slide."""
    slides = len(rendered_slides(codes))
    scans = len(codes.get("SCAN_COMPLETED", [])) + len(codes.get("SCAN_DONE", [])) + len(codes.get("SCAN_START", []))
    if not slides:
        return NODATA, "no slides recorded"
    if scans and scans >= slides * 0.5:
        return FAIL, "%d scan events for %d slides (looks like per-slide enumeration)" % (scans, slides)
    return PASS, "%d scan events for %d slides" % (scans, slides)


def check_primary_fallback(codes):
    """NAS up -> primary; down + cold -> fallback; back -> primary."""
    lost = codes.get("SOURCE_UNAVAILABLE", []) + [e for c, v in codes.items() if c.endswith("_LOST") for e in v]
    recovered = codes.get("SOURCE_RECOVERED", []) + [
        e for c, v in codes.items() if c.endswith("_RECOVERED") and c != "SOURCE_RECOVERED" for e in v
    ]
    fallback = codes.get("SOURCE_FALLBACK_ACTIVE", []) + [
        e for c, v in codes.items() if c.endswith("_FALLBACK_ACTIVE") and c != "SOURCE_FALLBACK_ACTIVE" for e in v
    ]
    stale = codes.get("SOURCE_STALE_CACHE_ACTIVE", []) + [
        e for c, v in codes.items() if c.endswith("_STALE_CACHE_ACTIVE") and c != "SOURCE_STALE_CACHE_ACTIVE" for e in v
    ]
    if not lost and not recovered:
        return NODATA, "no source up/down transitions; switch the NAS off and on during a run"
    detail = "%d lost, %d recovered, %d fallback, %d stale-cache" % (
        len(lost), len(recovered), len(fallback), len(stale))
    if lost and not (fallback or stale):
        return FAIL, detail + " (source lost but nothing took over)"
    if lost and not recovered:
        return FAIL, detail + " (never recovered after loss)"
    return PASS, detail


def check_no_permanent_blank(events, codes):
    """After any source loss, a slide must appear again.

    Losses that happened before the oldest retained slide are skipped rather than
    measured. Rotation removes old slides but keeps the loss events, so measuring across
    that boundary produces a gap of many hours that reflects the log budget, not the
    frame's behaviour -- an alarming number that means nothing.
    """
    losses = codes.get("SOURCE_UNAVAILABLE", []) + [e for c, v in codes.items() if c.endswith("_LOST") for e in v]
    if not losses:
        return NODATA, "no source-loss events to check recovery against"
    slides = rendered_slides(codes)
    if not slides:
        return NODATA, "no slides retained to check against"
    first_slide_t = slides[0]["t"]
    measurable = [l for l in losses if l.get("t", 0) >= first_slide_t]
    skipped = len(losses) - len(measurable)
    if not measurable:
        return NODATA, "all %d loss event(s) predate the retained slide history" % len(losses)
    worst = None
    for loss in measurable:
        after = [s for s in slides if s.get("t", 0) > loss.get("t", 0)]
        if not after:
            return FAIL, "no slide shown after a source loss (permanent blank)"
        gap = (after[0]["t"] - loss["t"]) / 1000.0
        worst = gap if worst is None else max(worst, gap)
    note = "" if not skipped else " (%d older loss(es) not measurable after rotation)" % skipped
    return PASS, "recovered after every measurable loss (worst gap %.0fs)%s" % (worst, note)


def check_crash_free(codes, events):
    sessions = codes.get("SESSION_START", [])
    boots = codes.get("BOOT_AUTOSTART", [])
    if not sessions:
        return NODATA, "no session records"
    # A session start not preceded by a boot suggests the process died and restarted.
    boot_times = {b.get("t") for b in boots}
    unexplained = 0
    for s in sessions[1:]:
        if not any(abs(s.get("t", 0) - bt) < 120_000 for bt in boot_times):
            unexplained += 1
    explicit = len(codes.get("UNCAUGHT_EXCEPTION", [])) + len(codes.get("PREVIOUS_UNCAUGHT_EXCEPTION", []))
    detail = "%d session(s), %d boot autostart(s), %d unexplained restart(s), %d crash marker(s)" % (
        len(sessions), len(boots), unexplained, explicit)
    if unexplained or explicit:
        return FAIL, detail
    return PASS, detail


def check_duration(events):
    """Span of the whole bundle. The event stream is sized not to rotate, so this
    reflects the real run length even when slide detail has aged out."""
    if len(events) < 2:
        return NODATA, "not enough events"
    span = hours(events[-1]["t"] - events[0]["t"])
    if span >= 24:
        return PASS, "%.1f h covered" % span
    return FAIL, "%.1f h covered; the gate asks for 24 h" % span


def check_heap(codes):
    """Heap growth < 5% over a rolling 6h window."""
    samples = [(e["t"], int(e.get("heapUsedKb", 0))) for e in codes.get("HEAP_SAMPLE", [])
               if e.get("heapUsedKb")]
    if len(samples) < 4:
        return NODATA, "only %d heap samples" % len(samples)
    span = hours(samples[-1][0] - samples[0][0])
    if span < 6:
        return NODATA, "%.1f h of samples; need 6 h for the rolling window" % span
    worst, worst_at = 0.0, None
    window_ms = 6 * 3_600_000
    for i, (t0, v0) in enumerate(samples):
        if v0 <= 0:
            continue
        for t1, v1 in samples[i + 1:]:
            if t1 - t0 > window_ms:
                break
            growth = (v1 - v0) / float(v0) * 100.0
            if growth > worst:
                worst, worst_at = growth, t1
    detail = "%d samples over %.1f h; worst 6h growth %.1f%%" % (len(samples), span, worst)
    return (PASS if worst < 5.0 else FAIL), detail


def check_autostart(codes):
    boots = codes.get("BOOT_AUTOSTART", [])
    if not boots:
        return NODATA, "no boot events; reboot the device during a run"
    levels = sorted({b.get("sdkInt", "?") for b in boots})
    detail = "%d boot(s) on API level(s) %s" % (len(boots), ", ".join(map(str, levels)))
    return (PASS if len(levels) >= 2 else FAIL), detail + ("" if len(levels) >= 2 else " (gate asks for >= 2)")


def check_secrets_absent(events):
    """The log must never carry credentials (spec §17.2)."""
    banned = ("password", "passwd", "secret=", "token=", "apikey", "api_key")
    hits = []
    for e in events:
        blob = json.dumps(e).lower()
        for b in banned:
            if b in blob:
                hits.append(e.get("code", "?"))
                break
    if hits:
        return FAIL, "possible secret material in: %s" % ", ".join(sorted(set(hits))[:5])
    return PASS, "no credential-shaped fields found"


def detect_truncation(events, codes):
    """Did log rotation discard the start of the run?

    The bundle concatenates two streams, and only the high-volume slide stream is
    expected to age out. Knowing this matters: a rotated-away record is not evidence of
    absence, and reporting it as failure would send someone debugging a phantom.
    """
    if not events:
        return False, ""
    slides = rendered_slides(codes)
    sessions = codes.get("SESSION_START", [])
    if not slides or not sessions:
        return False, ""
    first_event = events[0]["t"]
    first_slide = slides[0]["t"]
    # Slides starting well after the first recorded event means slide history was cut.
    gap_h = hours(first_slide - first_event)
    if gap_h > 0.5:
        return True, "slide detail truncated by rotation: first %.1f h has events but no slides" % gap_h
    return False, ""


def main(argv=None):
    parser = argparse.ArgumentParser(description="Analyze FamilyPhotoFrame schema-v1/v2 diagnostics")
    parser.add_argument("source")
    parser.add_argument("--json-out")
    parser.add_argument("--markdown-out")
    parser.add_argument("--release-gate", action="store_true", help="treat incomplete critical evidence as failure")
    args = parser.parse_args(argv)
    bundle = load_bundle(args.source)
    events, bad = bundle.events, bundle.malformed_lines
    if not events:
        print("No parseable events found.")
        return 1
    rich_report = build_report(bundle)
    default_json, default_markdown = default_report_paths(args.source)
    json_out = args.json_out or default_json
    markdown_out = args.markdown_out or default_markdown
    write_reports(rich_report, json_out, markdown_out)
    codes = by_code(events)
    truncated, trunc_note = detect_truncation(events, codes)

    times = [event.get("t", 0) for event in events]
    span = hours(max(times) - min(times)) if len(times) > 1 else 0
    print("Family Photo Frame — Phase 1 gate analysis (spec §22.2)")
    if truncated:
        print("  WARNING: %s" % trunc_note)
        print("           Slide-derived findings cover only the retained window.")
    print("  %d events, %.1f h span, %d session(s)%s" % (
        len(events), span, len({e.get("sessionId") for e in codes.get("SESSION_START", [])}),
        ", %d unparseable line(s)" % bad if bad else ""))
    print("  structured reports: %s, %s" % (json_out, markdown_out))
    print()

    checks = [
        ("Randomized slideshow, SAF + SMB merged", check_merged_sources(codes, truncated)),
        ("Photos actually vary (randomization)", check_randomized(codes)),
        ("Room-indexed, no per-slide enumeration", check_no_per_slide_enumeration(codes)),
        ("Primary/fallback transitions", check_primary_fallback(codes)),
        ("No permanent blank after a loss", check_no_permanent_blank(events, codes)),
        ("No crash across the run", check_crash_free(codes, events)),
        ("24 h continuous coverage", check_duration(events)),
        ("Heap growth < 5% over rolling 6 h", check_heap(codes)),
        ("Auto-start on >= 2 API levels", check_autostart(codes)),
        ("No secrets in the log (§17.2)", check_secrets_absent(events)),
    ]
    for name, (status, detail) in checks:
        print(fmt(status, name, detail))

    print()
    counts = Counter(s for _, (s, _) in checks)
    print("  %d pass, %d fail, %d no-data" % (
        counts[PASS], counts[FAIL], counts[NODATA]))
    print()
    print("  Not covered by any log: overlays configurable, D-pad operation, and")
    print("  SMB credential encryption. Those are UI/Keystore properties best confirmed")
    print("  by MANUAL_TEST_CHECKLIST.md rather than inferred from events.")

    if rich_report["status"] == "FAIL" or counts[FAIL]:
        return 1
    if args.release_gate and rich_report["status"] == "INCOMPLETE":
        return 1
    if rich_report["status"] == "INCOMPLETE" or counts[NODATA]:
        return 2   # incomplete evidence, deliberately distinct from failure
    return 0


if __name__ == "__main__":
    sys.exit(main())
