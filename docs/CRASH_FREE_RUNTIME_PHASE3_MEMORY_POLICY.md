# Crash-free runtime: Phase 3 bounded-memory policy

Status: implemented on `fix/crash-free-runtime`; physical-device soak pending.

Version: `26.41.1` (`versionCode 41`).

## Why Phase 3 changes policy

The August 25 diagnostic bundle does not show a Java exception or a Java-heap OOM at the final
process disappearance. Java heap remained bounded while total PSS/native memory rose and Android
repeatedly delivered low-memory callbacks. The previous callback policy treated every severe
system signal like a decode OOM:

1. enter `CIRCUIT_OPEN` and cancel preparation;
2. reopen after 30 seconds as `RECOVERY`;
3. receive another platform callback and repeat.

That loop changed the preparation decision repeatedly, cancelled useful work, and increased
transition churn while the selected slide's Java heap still had ample room. Only an actual caught
decode `OutOfMemoryError` proves that selected decoding must stop.

## Runtime policy

The guard now evaluates three independent signals:

| Signal | Guarded | Critical |
|---|---:|---:|
| Java heap, ordinary heap | 85% | 92% |
| Java heap, <=128 MiB heap | 75% | 85% |
| Total process PSS / ordinary memory class | 100% | 125% |
| Available system RAM / Android LMK threshold | <=200% | <=100% |
| Android `lowMemory` or severe callback | — | Critical |

Fresh process/system readings arrive once per minute. A ten-second Java-heap check retains the
last readings but cannot extend their deadline. Critical external pressure is sticky for ten
minutes after the latest signal and remains guarded for fifteen minutes, preventing callback
flapping. Repeated callbacks at the same effective level extend the deadline without changing
`decisionVersion`, so they do not restart preparation or transitions.

`CIRCUIT_OPEN` is now reserved for a caught decode OOM. System pressure enters `CRITICAL`, which
keeps the committed slide visible, permits one selected decode, disables speculative work, uses a
single-photo presentation, and lowers decode resolution. A real OOM retains the existing bounded
exponential circuit and conservative recovery behavior.

## Low-memory startup profile

Devices classified LOW no longer wait for a pressure callback. Their NORMAL baseline now:

- uses RGB_565 for `AUTO` colour depth;
- requests 82% linear decode size in addition to the existing 1.5-megapixel cap;
- retains at most three prepared-slide entries;
- prepares no speculative next slide;
- limits collages to two photos;
- forces the bounded crossfade path;
- disables soft-focus blur, blurred backdrop generation, and web-preview JPEG generation.

Explicit Full colour remains an override. The selected slide is always allowed unless a real
decode OOM has opened the circuit.

The manifest no longer requests `android:largeHeap`. The field device reported a 100 MiB ordinary
memory class but a 178 MiB large-heap limit; the larger limit hid process pressure and encouraged
less conservative heap growth. The LOW baseline bounds decoded ownership before returning to the
ordinary heap.

## New effectiveness evidence

Each `HEAP_SAMPLE` and protection transition can now expose:

- process budget and PSS pressure percentage;
- system headroom percentage and Android's low-memory flag;
- the active pressure source;
- whether the LOW economy baseline is active;
- remaining external critical and guarded hold times.

The acceptance comparison is behavioral, not merely "no crash": callback storms must no longer
produce alternating `CIRCUIT_OPEN`/`RECOVERY` states, `decisionVersion` must remain stable for
repeated same-level signals, prepared slides must remain within the LOW limit, and transition
cancellation/ack-timeout rates must fall without a blank frame.

## Verification completed

- Full offline pure-logic, SQL, migration, diagnostics-retention, and engine/persistence type gate.
- A 45-severe-callback regression scenario: one decision change, selected decode still enabled.
- PSS-critical, system-critical/guarded, sticky decay, real-OOM circuit, and concurrent guard tests.
- LOW-tier AUTO colour-depth tests and prepared-slide registry/delayed-reclaim checks.
- Transition selector/performance checks plus a 1,000-transition synthetic endurance analysis.
- Diagnostics v1/v2/mixed analyzer compatibility and Phase 3 field-allowlist contracts.

`testDebugUnitTest` could not start here because the Gradle 8.9 distribution is not cached and
network access to `services.gradle.org` is unavailable. That is an environment limitation, not a
passing Android build result.

## Remaining release gate

This change is not proof of a crash-free application until it runs on the affected Onda/API-22
hardware. Build with the Android SDK, install as an in-place update, then run a six-hour accelerated
test followed by the 24-hour offline/online soak. Compare PSS/native slope, callback count,
protection transitions, prepared bitmap inventory, transition cancellation rate, render-ack
timeouts, process-session continuity, previous-runtime breadcrumb, and any process-exit evidence
against the August 25 baseline.
