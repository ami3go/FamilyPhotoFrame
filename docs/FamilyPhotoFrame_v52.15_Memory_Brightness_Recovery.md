# v52.15 memory and scheduled-brightness recovery

Status: prerelease; source implementation complete, Android build and physical-device
qualification pending.

Version: `0.12.13-prerelease` (`versionCode 33`), web revision `v52150`.

This increment follows the long-running Android 5/6 field failures and the report that scheduled
brightness edits were not taking effect. It retains the v52.14 lifecycle/PIN/power fixes and
v52.13 web-server reliability correction.

## Memory recovery

The field evidence showed a decode OOM followed by `CIRCUIT_OPEN` while Java heap pressure stayed
near the process limit. Opening Settings then disposed the last rendered bitmap; returning to the
slideshow could not decode a replacement because the old circuit policy kept selected decoding
disabled at any pressure of 85% or higher.

- After an OOM cooldown expires, 85–91% pressure now enters conservative `RECOVERY` and permits
  one 70%-scale single-photo decode. Critical pressure at 92% or higher keeps the circuit closed.
- A repeat OOM immediately opens the circuit again with the existing exponential cooldown.
- On API 21–25 only, a recent real decode OOM plus sustained 92%+ heap pressure starts a last-resort
  watchdog. It waits 60 seconds, clears reusable image/preview caches and requests one GC, then
  waits for a later sample for 10 seconds.
- If the Java heap is still critically pinned, the app schedules a local Activity relaunch and
  terminates the exhausted process. A persisted 15-minute rate gate prevents restart loops.
- Diagnostics distinguish GC, scheduled restart, rate-limit suppression, restart failure, and
  successful process recovery across the process boundary.

This makes the previously deterministic post-Settings black screen recoverable. It does **not**
claim that the underlying long-run heap retention seen in the field logs has been identified or
removed; a new low-memory soak is still required to determine the retained owner.

## Scheduled brightness

Two independent defects are fixed:

- Editing a brightness period's start time in the on-device Settings screen now persists the
  value as soon as a complete valid `HH:mm` is entered. Previously the text field was local state
  only, so the new time was lost unless the user subsequently changed that period's slider or
  night action.
- Persisted/imported malformed period times are ignored by a pure brightness timeline. If every
  period is malformed, scheduled brightness safely falls back to manual brightness rather than
  throwing from `last()` and terminating the long-lived brightness watcher coroutine.
- The ViewModel validates and canonicalizes period times before persistence. Boundary, midnight,
  mixed-validity, and all-invalid cases are covered by executable regression checks.

## Qualification required

- Run `./gradlew clean testDebugUnitTest lintDebug assembleDebug` with Android/Gradle repositories
  available.
- Repeat the dedicated API-22/~100 MiB heap soak in `docs/API22_MEMORY_SOAK_TEST.md`, including the
  Settings → slideshow return after a deliberately induced decode OOM.
- If critical heap remains pinned after the recovery GC, verify the single controlled restart is
  followed by `MEMORY_PROCESS_RECOVERY_COMPLETED` and playback resumes; no restart loop is allowed.
- Cross at least one real configured brightness boundary without touching Settings. Confirm the
  window brightness and night action change within 30 seconds and survive a process restart.

Until those device gates pass, v52.15 remains a prerelease package.

## Delivery verification

Passed in the delivery environment:

- all 33 checked-in `scripts/verify-*.py` contracts, including the 11 new v52.15 contracts;
- full Kotlin syntax/structure parsing plus the executable pure-logic suite, including the new
  OOM watchdog and brightness-timeline boundary cases;
- all 95 Room SQL statements and migrations 1→8;
- 26-hour diagnostics endurance/rotation simulation;
- prepared-bitmap/API-22 delayed-reclaim checks and the complete remembered-browser phase suite;
- embedded Web UI JavaScript parsing.

Gradle 8.9 evaluation could not start because the delivery environment cannot reach
`services.gradle.org`. Consequently the new Android/JUnit tests, lint, and APK assembly remain
pending even though the dependency-free executable gates pass.
