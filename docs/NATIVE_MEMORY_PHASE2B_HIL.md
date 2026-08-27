# Native-memory Phase 2B HIL test

Build `26.46.1` adds aggregate native-allocation attribution and four explicit test modes.
The counters retain no image, path, or photo identifier. They are intended to determine whether
the observed native-PSS slope comes from source/decode work, generated bitmaps, or rendering.

## Before each run

- Export diagnostics and restart the application, so every run has one new process session.
- Use the same playlist, interval, screen brightness, network, and web-browser state.
- Keep the web page open without pressing **Get picture**; preview capture remains on demand.
- Let the display remain visible. Do not open opaque Settings during a measured run.

## Test order

| Mode in Settings → Memory | Duration | Expected allocation work | Purpose |
|---|---:|---|---|
| Hold frame | 2 h | No automatic slide, preload, blur, motion, or transition | Baseline for idle native growth outside slideshow work. |
| Single instant | 2 h | One decoded photo; no collage, preload, blur, motion, or animated transition | Separates decode/upload cost from animation. |
| Single crossfade | 2 h | Single-photo decode plus a crossfade; no collage, preload, blur, or motion | Measures transition/render contribution. |
| Normal | 4 h minimum | Production slideshow behavior | Confirms the attributed contributor under normal use. |

Use one freshly restarted process per row. Stop and export diagnostics at the end of every row.

## What to compare

Inspect `HEAP_SAMPLE` fields over the stable portion of each run (after the first 30 minutes):

- `nativePssKb`, `nativeHeapKb`, `pssKb`, `processPressurePercent`.
- `nativeDecode*`, `nativeBoundsProbe*`, `nativeCacheVerify*`, `nativeGenerated*`, and
  `nativeTransition*` counters. `NetDeltaKb` is contextual evidence, not an exclusive allocation
  stack; compare it between modes rather than interpreting one operation in isolation.
- `nativeCriticalLatched`, `nativeCriticalAgeMs`, and `nativeStableWindowStreak`.
- SMB `smbActiveContexts`, `smbActiveStreams`, `smbOldestStreamPurpose`,
  `smbOverdueStreams`, and `smbStreamDeadlineExpirations`.

## Pass/fail interpretation

- If **Hold frame** continues to grow materially, slideshow decoding and transitions are not the
  primary cause; inspect system/UI/native libraries next.
- If **Single instant** grows but Hold frame does not, the decode or GPU upload path is implicated.
- If **Single crossfade** is materially worse than Single instant, transition/render resources are
  implicated.
- If only **Normal** grows, compare `nativeGenerated*`, `nativeBoundsProbe*`, and collage use to
  identify the extra producer.
- SMB counters must remain balanced; an overdue stream or deadline expiration is evidence of a
  separate source problem, not bitmap retention.

On sustained native growth at at least 120% of the process budget, the app latches `CRITICAL`,
requests at most one GC after 30 minutes, and can schedule one controlled legacy-device restart
after another two minutes. Native-pressure restarts are limited to once per four hours.
