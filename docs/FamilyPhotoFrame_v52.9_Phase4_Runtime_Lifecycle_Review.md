# Family Photo Frame v52.9 — Phase 4 review

## Scope

Phase 4 adds process/runtime context, Activity and slideshow-surface state changes,
screen/display state, and correlated fullscreen recovery evidence. It does not add
per-frame or per-inset logging.

## Implemented contracts

- `SESSION_START` records the required build, SDK/device, process, heap, memory-class,
  cache, retention, queue, drop, and sink fields without blocking application startup.
- `RUNTIME_CONTEXT_READY` is emitted from background work after indexed count, source,
  settings revision, and engine state are available.
- Activity, slideshow surface, engine, screen-interactive, and display-configuration
  events are emitted only when their observable state changes.
- Immersive-mode request, apply, loss, retry, restore, and user-exit events include a
  bounded activity token, safe reason, lifecycle state, UI flags, retry count, elapsed
  duration, and legacy/modern path marker.
- Screen receiver registration is API-safe and paired with Activity start/stop.

## Review findings and fixes

- The initial immersive callback could have classified the first non-immersive state
  as a loss. The controller now requires a previously applied state before emitting
  `IMMERSIVE_LOST`.
- Multiple visibility/focus callbacks could have scheduled duplicate recovery work.
  Loss state and retry scheduling are now deduplicated, and a successful restore
  cancels the pending retry.
- Existing fullscreen regression checks assumed the former constructor and no-argument
  recovery call. The check now verifies the new contextual API without weakening the
  original Settings/back-button contract.

## Verification

- `verify-lifecycle-logging-v529.py`: 10 lifecycle and 6 immersive event contracts.
- `verify-fullscreen-settings-back.py`: fullscreen recovery and Settings back path.
- `verify-logging-v529.py`: schema/catalog and emitted-code coverage.
- All 191 Kotlin sources pass structural and parser checks.
- Engine and persistence sources pass the offline Kotlin type-check harness.

## Gate result

Phase 4 is accepted for integration. Android instrumentation remains part of the final
release gate because Activity/insets behavior cannot be exercised by the offline JVM
harness.
