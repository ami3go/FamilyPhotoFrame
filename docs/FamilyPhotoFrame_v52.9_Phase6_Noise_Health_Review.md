# Family Photo Frame v52.9 — Phase 6 review

## Scope

Phase 6 keeps high-volume playback evidence from displacing troubleshooting history,
aggregates repetitive events, and exposes bounded writer/retention health.

## Implemented contracts

- Catalog metadata alone routes standard and bulk events; there is no code-name heuristic.
- Per-slide selection/render/collage/transition/shuffle events use the bulk stream.
- Brightness logs only on rounded level/action/mode/period changes, plus a 15-minute
  unchanged heartbeat.
- Identical decode failures retain their first three events, then emit one-minute
  summaries. Preview hits aggregate, and scan progress deduplicates by operation/bucket.
- Decode, scan, and preview aggregation state is bounded and inactive decode signatures
  expire.
- `DiagnosticsHealthSnapshot` exposes queue, drops, privacy drops, per-stream bytes,
  generations/rotations, known sequence range, write/flush times, errors/timeouts, crash
  envelope state, and bounded rate-state size.
- Sink failures/recovery create state-change events in memory without recursively writing
  through the failing sink. Queue overflow and flush timeout remain explicit errors/warns.
- Production rotation remains 6 MiB standard plus 12 MiB bulk (18 MiB total).

## Review findings and fixes

- The decode LRU could temporarily grow to 65 entries because trimming occurred after
  the early-return path for the first three failures. It now trims immediately after
  insertion, so its configured 64-entry bound is never exceeded.
- The engine type-check harness was extended with the rate and health dependencies.
- The end-to-end writer/analyzer harness was extended with all schema-v2 privacy/rate/
  health sources so it compiles the actual production pipeline.
- `FileDiagnosticsSink` initially lacked an explicit return in the new stats helper;
  the compiler gate found and corrected it before acceptance.

## Verification

- State-change, 15-minute heartbeat, first-three, one-minute summary, preview aggregate,
  and scan-bucket assertions.
- Forced append failure/recovery, flush timeout, queue saturation with exact drop counts,
  rotation stats, and crash-envelope health.
- 100,000 slide events with bounded memory/rate state and retained standard session data.
- Concurrent export, clear, and detach/shutdown flush.
- Complete and rotation-starved writer/analyzer end-to-end scenarios.
- Kotlin parsing and engine/persistence type checks pass.

## Gate result

Phase 6 is accepted for integration. Android filesystem fault injection and long device
soak testing remain part of final qualification.
