# Verification Notes — v50.5 Transition Phases 3–5

## Automated checks completed

- Project consistency and resource audit.
- Kotlin main-source syntax parsing.
- Engine and persistence type-check against Room/serialization stubs.
- All 45 Room queries prepared against SQLite.
- Migration chain replayed through schema version 5.
- JavaScript syntax validation for the paired web settings page.
- Transition phase 1–2 source contract regression.
- Transition phase 3–5 source contract verification.
- Actual production TransitionSelector and TransitionPerformance classes compiled and exercised for 1,000 deterministic selections.
- Immediate-repeat, motion-heavy-run, reduced-motion, fallback-chain, and low-performance cooldown checks.
- Synthetic 1,000-transition diagnostics run passed frame, latency, retention, fallback, cancellation, heap-growth, and crash-marker criteria.
- Existing 26-hour diagnostics writer/analyser end-to-end test passed, including forced log rotation.

## Hardware gate still required

The offline environment cannot run an Android/Compose build or the Huawei PLK-L01 endurance test. Run:

```bash
./gradlew clean testDebugUnitTest installDebug
```

Then follow `docs/TRANSITIONS_PHASE5_HARDWARE_TEST.md` on the Huawei Android 6 frame. A real 1,000-transition run is required before changing the production default from Fixed Crossfade to Ambient Random.
