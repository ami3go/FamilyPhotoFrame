# Crash-free runtime: Phase 4 adaptive stability controller

Status: implemented on `fix/crash-free-runtime`; physical-device soak pending.

Version: `26.42.1` (`versionCode 42`).

## Purpose

Phase 3 bounded decoded-image ownership and made process/system memory pressure sticky. Phase 4
closes the remaining control gap from the August 25 field run: native PSS growth, repeated render
acknowledgement stalls, and overdue transfers were observable but could not change playback policy.

All signals now enter the existing process-wide `PlaybackMemoryPolicy`. There is no second
controller that can fight the memory guard. The plan's names map to the established API as follows:

| Phase 4 behavior | Runtime level | Effect |
|---|---|---|
| Normal | `NORMAL` | configured quality, with Phase 3's LOW-device economy baseline |
| Constrained | `GUARDED` | no speculative preload/blur/preview, two-photo maximum, reduced decode |
| Emergency | `CRITICAL` | one selected photo, simple transition, lower decode scale |

`CIRCUIT_OPEN` remains separate and is still entered only after a caught decode
`OutOfMemoryError`. Operational pressure never blocks the selected decode, so a stalled pipeline
cannot turn a visible frame into an avoidable blank screen.

## Signal policy

| Signal | Constrained | Emergency |
|---|---:|---:|
| Render acknowledgement timeouts | 3 in a bounded 5-minute window | 6 in the same window |
| Oldest active media transfer | 2 minutes | 4 minutes |
| Concurrent media transfers | — | more than the invariant maximum of 2 |
| Native PSS trend | at least 512 KiB and 16 KiB/min over at least 15 minutes | at least 4 MiB and 64 KiB/min, or 3 consecutive positive windows |

Native trend windows close after a positive classification or after 30 minutes. A flat/declining
completed window clears the positive-window streak. State contains only bounded counters,
timestamps, and primitive measurements; no bitmap, path, URI, host, or photo identity is retained.

Fresh native/transfer values are accepted with the one-minute process sample. Ten-second heap
checks retain those readings for diagnostics but cannot treat them as fresh and extend their hold.
Render timeouts are counted at the engine event and evaluated by the next Application-owned
control-loop sample, keeping cache cleanup and protection-change logging on one owner.

## Hysteresis and recovery

- Emergency signals hold `CRITICAL` for 10 minutes and keep `GUARDED` for 15 minutes.
- Constrained signals hold `GUARDED` for 15 minutes.
- Repeated evidence at the same effective level extends the deadline but does not change
  `decisionVersion`; preparation and transitions are not restarted for every sample.
- Java-heap exit thresholds and the Phase 3 guarded/critical recovery holds still apply.
- Explicit GC remains a last resort after a real decode OOM and sustained pinned Java heap. GC is
  now globally rate-limited to one request per 10 minutes across separate OOM attempts.
- The existing legacy API 21–25 process relaunch remains gated by a real recent decode OOM, a
  post-GC verification sample, and its separate 15-minute relaunch limit.

## Diagnostic evidence

`HEAP_SAMPLE` and `MEMORY_PROTECTION_CHANGED` can expose:

- `nativeGrowthKb`, `nativeGrowthRateKbPerMin`, and `nativeGrowthStreak`;
- `renderTimeoutWindowCount` and `renderTimeoutTotal`;
- active/oldest media-transfer evidence;
- the explicit `NATIVE_PSS_GROWTH`, `RENDER_ACK_TIMEOUTS`, or `OVERDUE_TRANSFER` source;
- the same critical/guarded hold deadlines and bounded playback decisions added in Phase 3.

## Verification completed

- Full offline Kotlin policy suite, including render-stall, overdue/excess-transfer, repeated native
  growth, plateau reset, selected-decode continuity, and GC-rate-limit scenarios.
- A 1,000-sample render-pressure endurance scenario verifies bounded level changes, no decode
  circuit opening, exact counters, and a stable return to Normal after the signal stops.
- Existing Phase 3 callback-storm, PSS/system pressure, real-OOM circuit, LOW-tier, and concurrency
  regressions remain green.
- Whole-main-source syntax/structure checks, SQL and migration verification, diagnostics retention
  simulations, and engine/persistence type checks pass.
- Source contracts verify the render-timeout wiring, fresh-sample boundary, diagnostic allowlist,
  version, and the rule that operational signals never own the decode-circuit deadline.

`testDebugUnitTest` could not start here because the Gradle 8.9 distribution is not cached and
network access to `services.gradle.org` is unavailable. The real Gradle/Android build and hardware
behavior are therefore not proven by the offline gate.

## Remaining release gate

Build with Gradle 8.9 and the Android SDK, install on the Onda/API-22 device with a compatible
signature, and repeat the six-hour accelerated test followed by the 24-hour A/B soak. In addition
to the Phase 1–3 gates, verify:

- repeated timeout/transfer signals produce one stable degradation, not alternating levels;
- the selected image remains visible in `GUARDED` and `CRITICAL`;
- native-growth streaks reset after a genuine plateau;
- no stale transfer sample extends a hold;
- render-ack timeout rate is below 0.1% and transition cancellation rate is below 5%;
- no GC loop or unexplained process restart occurs;
- total PSS/native growth remains below the Phase 1 24-hour limits.
