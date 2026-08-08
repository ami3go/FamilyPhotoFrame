# Verification Notes — v50.4 Transition Phases 1–2

## Passed in the delivery environment

- Kotlin syntax parsing for all main and unit-test sources.
- Project consistency and capability audit.
- All 45 Room SQL statements prepared successfully against SQLite.
- Database migrations 1→2→3→4→5 verified.
- All Android XML resources parsed.
- Paired web setup JavaScript parsed by Node.js.
- Transition Phase 1–2 source-contract verification.
- Transition renderer/type model compiled against focused Compose/Kotlin stubs.
- Frame invariants executed for all six effects:
  - valid alpha range;
  - combined opacity never below full coverage;
  - incoming final alpha = 1;
  - incoming final scale = 1;
  - incoming final translation = 0.
- Diagnostics end-to-end harness passed its complete 26-hour simulated scenario.

## Not available in the delivery environment

`./gradlew clean testDebugUnitTest compileDebugKotlin` could not run because the
Gradle 8.9 distribution was not cached and outbound network access is blocked.
Run the real build on the development workstation before installing on the frame.

```bash
./gradlew clean testDebugUnitTest installDebug
```
