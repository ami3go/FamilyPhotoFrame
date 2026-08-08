# FamilyPhotoFrame v50.5 — Transition Phases 3–5

## Advanced visual effects

Added the remaining four prepared-slide transitions:

- Depth Fade
- Ken Burns Handoff
- Soft Reveal
- Soft Focus Fade

Soft Reveal uses API-23-compatible clipping, off-screen compositing, and a feathered alpha mask. It does not use runtime shaders. Soft Focus Fade uses application-owned quarter-dimension blur layers generated before animation; no blur bitmap is generated per frame.

## Selection and settings

- Added Fixed and Ambient Random modes.
- Ambient Random uses deterministic weighted selection with a three-effect history.
- Immediate repeats and three consecutive motion-heavy effects are prevented.
- Soft Reveal and Soft Focus Fade remain outside the default random pool pending hardware endurance results.
- Added reduced-motion mode, which resolves only Crossfade or Soft Dissolve.
- Added all ten effects, duration controls, and reduced-motion controls to Android and paired web settings.
- Existing installations remain on Fixed Crossfade by default.

## Performance and reliability

- Records per-transition frame timing, maximum frame interval, first-frame latency, retained prepared-slide count, and active decoded bytes.
- Enters Crossfade-only low-performance mode for 20 transitions when three of the previous five transitions exceed the frame budget.
- Added bounded diagnostics analysis for the 1,000-transition hardware gate.
- Added explicit fallback chains for advanced effects.
- Suspends legacy display-time Ken Burns motion while a transition owns the presentation transform.
- Corrected cancellation propagation during Soft Focus preparation.
- Corrected deferred release of app-owned blur layers when settings replace a slide that is still visible.
- Transition fallback and frame-warning records use the high-volume diagnostics stream so intentional per-slide fallback cannot evict rare lifecycle evidence.

## Version

- `versionCode = 7`
- `versionName = 0.10.5-prerelease`
