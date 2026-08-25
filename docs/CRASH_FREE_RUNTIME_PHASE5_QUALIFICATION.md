# Crash-free runtime: Phase 5 endurance qualification

Status: qualification tooling implemented on `fix/crash-free-runtime`; physical-device qualification pending.

Version: `26.43.1` (`versionCode 43`).

## Purpose

Phase 4 connected native-PSS growth, render-ack stalls, and overdue transfers to the bounded
playback policy. Phase 5 deliberately does not add another runtime controller before that policy
has hardware evidence. It turns the Phase 1 promotion criteria into a repeatable, strict release
gate for the Onda V80 Plus/API-22 frame.

The analyzer selects the newest `versionCode` represented by `SESSION_START`, so retained events
from the previously installed build cannot contaminate the candidate interval. A missing field,
fault scenario, or duration is `NO DATA`; it is never counted as a pass.

## Automated evidence gates

| Area | Phase 5 decision |
|---|---|
| Candidate identity | Latest retained run must be `versionCode >= 43`. |
| Coverage | One-minute samples, no gap above five minutes, and the selected profile duration. |
| Process continuity | No crash, escalated main-thread stall, harmful API-30+ exit, or unexplained restart. A legacy-memory restart is accepted only when scheduled and followed by completion evidence. |
| Diagnostics | Complete standard stream, terminal bundle record, no malformed/schema/privacy errors, queue drops, rejected fields, flush timeout, or unrecovered sink failure. |
| Java heap | Worst robust six-hour heap-floor increase remains below 5% of maximum heap. |
| Total/native PSS | Robust edge growth and 24-hour slope projection each remain below 10 MiB. |
| Native resources | File-descriptor and thread growth remain bounded; SMB/media cumulative counters equal active ownership; tracking never saturates; media concurrency stays at or below two; no stream/transfer is older than two minutes. |
| Bitmap ownership | LOW devices retain at most three prepared slides and two rendered slides; delayed disposal returns to zero within five minutes. |
| Rendering | Render-ack timeout rate is below 0.1%. Unplanned transition cancellation rate is below 5%, and each transition generation has exactly one start and one terminal event. |
| Controller/recovery | No repeated same-level protection event, no more than four level changes per hour, GC spacing is at least ten minutes, restart spacing is at least fifteen minutes, and every scheduled restart completes. |
| Fault scenarios | A real NAS loss must recover and render again; an Activity stop/start interval must contain no stale render or transition. |

The slope calculation first reduces the one-minute series to 15-minute medians, then fits the
steady-state values after the two-hour warm-up. This prevents one active decode or one garbage
collection from being mistaken for a day-long trend. Six- and 24-hour window gates use 30-minute
edge medians for the same reason.

## Profiles and commands

Run the six-hour candidate first:

```bash
python3 scripts/analyze-crash-free-runtime.py candidate.jsonl \
  --baseline FamilyPhotoFrame-diagnostics-20260825T043220952Z.jsonl \
  --profile accelerated --release-gate
```

After it passes, run the controlled day comparison and then the pre-merge seven-day gate:

```bash
python3 scripts/analyze-crash-free-runtime.py candidate-24h.jsonl \
  --baseline FamilyPhotoFrame-diagnostics-20260825T043220952Z.jsonl \
  --profile day --release-gate

python3 scripts/analyze-crash-free-runtime.py candidate-7d.jsonl \
  --profile seven-day --release-gate
```

Each invocation writes adjacent `*-crash-free.json` and `*-crash-free.md` reports. Exit status is
zero only for a complete pass, one for measured failure, and two for incomplete required evidence
without `--release-gate`. With `--release-gate`, incomplete evidence also returns one.

## Required device sequence

Use the same Onda/API-22 tablet, charger, approximately 28,000-photo SMB library, 15-second dwell,
portrait-only collage configuration, display brightness, network, web-preview polling, and cache
state used for the August 25 baseline.

1. Install `26.43.1` as an in-place update with the compatible signing certificate.
2. Run six hours. During the run, download diagnostics, stop/start the Activity once, and disconnect
   and restore the NAS once.
3. If the accelerated report passes, run the 24-hour controlled A/B gate.
4. If the 24-hour report passes, run seven continuous days before merging to `main`.
5. Preserve the complete JSONL bundle and generated JSON/Markdown reports for each gate.

Do not accept a run whose slide count is flat, whose latest build identity is not 43, whose standard
diagnostic evidence is incomplete, or whose required fault scenarios were not exercised.

## Verification completed off-device

- Healthy 25-hour synthetic evidence passes every required gate.
- Synthetic PSS/native growth, FD/thread growth, render timeouts, transition churn, and GC looping
  each fail their own gate.
- Legacy/missing evidence remains `NO DATA` rather than producing a false pass.
- Optional A/B output compares PSS slopes, timeout/cancellation rates, and low-memory callback rate.
- The analyzer regression is part of the offline verification script and Android CI.

The real Gradle/Android build and the six-hour, 24-hour, and seven-day Onda runs remain mandatory.
No source-only test can prove the application crash-free on the failure-class device.
