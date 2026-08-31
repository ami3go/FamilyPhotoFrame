# Native-memory Phase 2B HIL test

## Phase 4 follow-up: SMB content-hash read churn (build 26.58.1)

The build 26.57.1 V80 qualification run kept total PSS flat and rendered continuously,
but native PSS grew by about 5.4 MiB/hour. A fresh `HOLD_COMMITTED_FRAME` process then
reproduced faster growth while slide selection and rendering remained fixed at two frames.
Resource telemetry showed one balanced, non-overdue SMB stream at a time, always with
purpose `CONTENT_HASH`; 83 streams opened in the first 50 minutes while bitmap,
media-transfer, transition, FD, and thread ownership remained flat.

The content-hash reader still used Kotlin's generic 8 KiB buffer. jCIFS maps each caller
read to a bounded SMB request, so this path created about eight times as many short-lived
request/response objects as the existing 64 KiB media-transfer path. Build 26.58.1 applies
the same 64 KiB policy to remote content hashing without changing the hash, timeout,
batching, or database semantics. Repeat `HOLD_COMMITTED_FRAME` with a fresh process before
resuming the normal Phase 4 gate; native PSS must remain flat while `CONTENT_HASH` stream
count and indexed bytes continue to advance.

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

## Phase 3B interlude check — build 26.48.1

The Phase 3 log showed that an On This Day override could wake only on the normal playlist-watch
cadence, leaving a configured five-minute interlude active for up to fifteen minutes. It also
reselected the final memory every normal slide interval after the finite pool was exhausted.

For one scheduled or manually previewed On This Day interlude, verify that:

- exactly one `ON_THIS_DAY_POOL_EXHAUSTED` event is emitted after the final distinct photo is
  visibly rendered;
- no further `SLIDE_SELECTED selectionMode=ON_THIS_DAY` events occur before the override exits;
- `PLAYLIST_SCHEDULE_SWITCHED` returns to the normal/default playlist at the configured override
  expiry, rather than waiting for the normal fifteen-minute watcher cadence; and
- normal shuffle resumes without a `RENDER_ACK_TIMEOUT` or a new SMB transfer for the held image.

This is a bounded control-flow correction. It does not claim to resolve the native-PSS slope;
run the mode matrix above before attributing that slope to decode or rendering.

## Phase 3C render-ack trace — build 26.49.1

Phase 3B HIL evidence retained two general `RENDER_ACK_TIMEOUT` events, but did not record how
far each selected slide reached in the Compose preparation and transition handoff. Phase 3C keeps
the existing 30-second recovery unchanged and records a bounded `PRESENTATION_STAGE` trail for the
selected photo.

For each timeout, inspect `reason`, `lastPresentationStage`, `selectionAgeMs`, and `stageAgeMs`,
then join `PRESENTATION_STAGE` events using `photoToken`:

- `PREPARATION_NOT_STARTED`: the UI never began preparation.
- `PREPARATION_STALLED`: preparation began but never produced a prepared slide.
- `TRANSITION_NOT_STARTED`: preparation/commit finished but the renderer never began it.
- `TRANSITION_STALLED`: a transition started but did not complete.
- `PRESENTATION_ABORTED`: cancellation or stale/rejected work ended the handoff.

No timeout-policy or slideshow-advance behavior changes are included in this build; this trace is
the evidence gate for the next behavioral correction.
