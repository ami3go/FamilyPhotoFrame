# FamilyPhotoFrame v50.4 — Transition Framework, Phases 1–2

Implemented against `FPF-FEAT-TRANSITIONS-001 v1.1`.

## Phase 1

- Stable lower-case transition persistence with migration from legacy `CROSSFADE`,
  `SLIDE`, and `NONE` values.
- Explicit transition lifecycle model: preparing, ready, animating, committed, paused.
- A single prepared-slide transition coordinator owns the only active animation.
- The renderer animates complete prepared presentations and performs no I/O or decode.
- Cold start uses a 300 ms Crossfade from the configured background.
- The engine is notified only after animation completion, so the full dwell interval
  starts when the incoming presentation is fully visible.
- Added transition selected, started, completed, and cancelled diagnostics.

## Phase 2

Implemented six effects:

1. Crossfade
2. Soft Dissolve
3. Gentle Zoom In
4. Gentle Zoom Out
5. Horizontal Glide
6. Vertical Glide

All effects operate on the complete single-photo or collage presentation. The current
slide remains behind the incoming slide throughout zoom/glide effects to prevent an
uncovered/black frame.

## Settings

- On-device Playback settings expose all six effects.
- Paired web settings expose all six effects and a 300–2000 ms base duration.
- Legacy web values remain accepted.

## Version

- `versionCode = 6`
- `versionName = 0.10.4-prerelease`
