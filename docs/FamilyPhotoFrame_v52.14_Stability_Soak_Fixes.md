# v52.14 stability-soak fixes

Status: prerelease; source implementation complete, Android build and physical-device
qualification pending.

Version: `0.12.12-prerelease` (`versionCode 32`), web revision `v52140`.

This increment addresses the two software defects and one observability gap found in the
day-long API-22 field log. It preserves the v52.13 web-server reliability fix, v52.12 NAS/reset
hardening, the low-memory circuit breaker, portrait-only collages, and folder-balanced shuffle.

## 1. Activity-stop presentation fencing

- `HostLifecycleGate` advances a generation at every Activity start and stop.
- The ViewModel also publishes that generation as lifecycle-aware state. Even when a collector
  cannot observe the transient stopped Boolean, the later start has a newer generation and
  restarts preparation/transition effects.
- Selected and next-slide work capture the current generation before preparation and revalidate
  it after blocking decode/provider work and before the engine prepared-commit boundary.
- A decoded bitmap graph that returns after cancellation is retired without becoming a Compose
  candidate.
- Transition coordination now observes `hostActive`. Its non-cancellable cleanup commits a
  target only when the captured host generation is still current; an Activity stop keeps the
  previous committed frame and records `TRANSITION_CANCELLED` with `host_stopped` instead.
- A new start creates a different token, so a stop/resume that happens while legacy decoding is
  still blocked cannot accidentally make the old result valid again.

## 2. Web PIN work off the main thread

- The Compose intent returns immediately and runs PIN reset through the application's Default
  dispatcher.
- Remembered-browser revocation and the 20,000-iteration PBKDF2 calculation therefore no longer
  execute inline on the UI thread.
- An atomic single-flight gate rejects repeated taps while one regeneration is active, and the
  button is disabled until completion.
- `WebSecurity` computes the replacement hash before publishing new salt/hash state and clears
  the temporary `PBEKeySpec` password after derivation.

## 3. Battery and charger evidence

The existing one-minute `HEAP_SAMPLE` now also records privacy-safe power telemetry from Android's
sticky `ACTION_BATTERY_CHANGED` state:

- `batteryLevelPct`
- `batteryStatus` (`CHARGING`, `DISCHARGING`, `FULL`, `NOT_CHARGING`, or `UNKNOWN`)
- `powerSource` (`AC`, `USB`, `WIRELESS`, `NONE`, or `OTHER`)
- `batteryVoltageMv`, `batteryTempDeciC`, `batteryHealth`, and `batteryPresent`
- `batteryCurrentUa` and `batteryChargeCounterUah` when the API-21/OEM BatteryManager exposes them

No new polling loop or permission is introduced; these fields are sampled at the same one-minute
cadence already used for heap evidence.

## Qualification still required

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug installDebug`
- A six-hour continuous API-22/~100 MiB heap soak using `docs/API22_MEMORY_SOAK_TEST.md`
- Confirm no presentation is committed/rendered while the Activity is stopped.
- Confirm repeated New PIN taps create one derivation and no `MAIN_THREAD_STALL_*` event.
- Correlate battery level/status/source with any unexpected tablet reboot.

These checks are release gates; passing source/offline tests alone does not promote v52.14 from
prerelease.

## Delivery verification

Passed in the delivery environment:

- all 32 `scripts/verify-*.py` verifier entry points;
- executable pure-Kotlin logic, including stop/restart generation fencing;
- all 95 Room SQL statements and migrations 1→8;
- 26-hour diagnostics endurance/rotation simulation;
- complete remembered-browser phase suite and prepared-bitmap/API-22 retirement test;
- Kotlin parser/structure checks across all 214 source/test files and Node parsing of the embedded
  web client.

Android Gradle evaluation could not start because the environment cannot reach the Gradle 8.9
distribution host. The rendered Chromium browser gate is also unavailable in this environment;
v52.14 changes no Web Control layout/CSS, but that gate must still be rerun before promotion.
