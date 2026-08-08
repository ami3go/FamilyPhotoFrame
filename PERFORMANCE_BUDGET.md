# Performance budget — how to measure it

The §22.4 acceptance checklist has one item that has stayed open through every increment:

> **Fit-blur, Ken Burns, and transitions pass the performance budget** — implemented;
> *must still be measured* (spec §10.3: ≥ 30 fps crossfade on the reference low-end device).

It stayed open because "must be measured" had no procedure attached, and no measurement
existed to attach one to. This document is that procedure. It cannot be run in CI or on a
workstation — the whole point is the reference low-end device — so it is written to be run
by a person in about ten minutes.

## What the gate actually is

From spec §10.3 and docs/archive/phases/PHASE2_NOTES.md, with `fit_blur` + Ken Burns + slide all enabled:

1. crossfade/transition sustains **≥ 30 fps**;
2. no dropped frames attributable to blur generation (it runs off the main thread);
3. heap does not grow across a long run — the blur backdrop must not be retained beyond
   the current slide (spec §10.2: only current + next decoded bitmaps);
4. confirm behaviour on a 1 GB-RAM device before enabling by default.

Items 1 and 2 are what the in-app tool measures. Items 3 and 4 need Android Studio's
memory profiler and the right hardware respectively; they are **not** automated here and
must not be reported as passing on the strength of the frame-timing number alone.

## The tool

`Settings → Performance measurement → Show frame timing on screen` draws a one-line
readout at the top of the slideshow:

```
PASS  mean 58.7fps  p95 52.1fps  worst 21.4ms  jank 3/0  n=1240
```

- **verdict** — `SAMPLING` until 90 frames are collected, then `PASS`/`FAIL`.
- **mean** — average across the sample window.
- **p95** — the frame rate implied by the 95th-percentile frame *interval*. **This is the
  number the gate uses**, not the mean: a transition that averages 45 fps while stalling
  for 200 ms mid-crossfade would pass on the mean and look broken on a TV.
- **worst** — longest single frame interval in the window.
- **jank / severe** — frames over one / two 60 Hz budgets beyond expected.
- **n** — frames in the window (a 3,600-frame ring, ≈60 s at 60 Hz, so a long run reports
  recent behaviour rather than start-up).

`Record a sample to diagnostics` writes the current summary into the diagnostics log as
`PERF_SAMPLE`, so it travels in a support bundle and can be attached to the sign-off.

## Procedure

1. Install on the **reference low-end device** — not an emulator, not a flagship phone.
   Emulator frame timing measures the host, so a pass there means nothing.
2. Configure the worst realistic case, which is what the budget is about:
   - `Settings → Display`: aspect **Fit + blurred background**, transition **Crossfade**,
     motion **Ken Burns**;
   - interval **5 s**, so transitions happen often;
   - a real photo source with large originals (a NAS share, not the bundled samples —
     samples are small and will flatter the result).
3. Turn on `Show frame timing on screen`.
4. Let it run **at least 5 minutes** without touching the remote. Transitions are the load;
   a static slide will read ~60 fps and prove nothing.
5. Watch the readout **across transitions specifically**. Record the worst verdict seen,
   not the best.
6. Press `Record a sample to diagnostics`, then export the support bundle.
7. For budget items 3 and 4, attach Android Studio memory-profiler evidence over a run of
   at least 30 minutes, confirming heap returns to baseline between slides.

## Recording the result

| Field | Value |
|---|---|
| Device (model, RAM, Android version) | |
| Build / commit | |
| Settings used (aspect / transition / motion / interval) | |
| Source type and typical photo size | |
| Run duration | |
| Worst verdict observed | |
| p95 fps at worst | |
| Severe jank count | |
| Heap stable over 30 min? (profiler) | |
| Verdict: may fit-blur/Ken Burns become defaults? | |
| Signed off by / date | |

## Until this is filled in

`fit_blur` and Ken Burns stay **opt-in** and must not be described as the premium default
(spec §10.3, and the note on `AppSettings.transition`/`motion`). A measurement on the
wrong hardware does not discharge the gate.

## What is verified vs what is not

The statistics themselves are unit-verified in `scripts/verify-pure-logic.sh` (27 checks),
including the case the design exists for: a run that averages acceptably but stalls
repeatedly correctly **fails** the gate. The Choreographer sampling layer
(`FrameStatsCollector`) is thin Android glue and, like the rest of the Android surface in
this project, has never been executed — the first person to run this procedure is also
testing the tool.
