# API-22 low-memory slideshow soak

Use this procedure to qualify v52.15 on the failure-class device: Android 5.1/API 22 with an
approximately 100 MiB Java heap and a large SMB-backed library.

## Preconditions

- Install a debug APK built from `0.12.13-prerelease` / `versionCode 33`.
- Start from a clean app process; preserve the photo database/cache unless the scenario says
  otherwise.
- Confirm diagnostics show API 22, the expected heap maximum, and schema v2.
- Use the same 15-second interval and portrait-collage settings that reproduced the field OOM.
- Keep the device powered, awake, thermally unobstructed, and connected to the same LAN.

## Four-hour primary gate

1. Run mixed single-photo and portrait-collage playback for at least four continuous hours.
2. During hour two, download a diagnostics bundle while playback continues.
3. During hour three, briefly disconnect and restore the NAS once.
4. Record whether protection enters guarded, critical, circuit-open, or recovery states.
5. Do not manually force GC or clear app data during the gate.
6. Once during the run, leave the Activity (or turn the screen off long enough to produce
   `ACTIVITY_STOPPED`) and return. A decode already inside a blocking legacy/provider call may
   finish, but no stale presentation may start a transition or be reported rendered before the
   host starts again.
7. Generate a new Web pairing PIN, including several quick repeated taps. Only one generation
   may run at once and diagnostics must not report a main-thread stall from PIN work.
8. Once, deliberately reproduce a decode OOM. After the cooldown, open Settings and return to the
   slideshow. If pressure has fallen below 92%, a conservative replacement must render instead of
   leaving a permanent black frame. If pressure remains at or above 92%, allow the recovery
   watchdog to run without manually forcing GC.

Acceptance:

- No uncaught OOM, ANR, unexpected process restart, permanent black frame, or recycled-bitmap draw
  error. A single controlled v52.15 memory-recovery restart is allowed only after the conditions
  below are met.
- No immediate decode retry storm after `DECODE_OOM_RECOVERY`.
- An OOM, if deliberately induced, opens the circuit, preserves the visible frame, and does
  not create `DECODE_FAILED`/`COLLAGE_CANDIDATE_FAILED` for the same heap event.
- `preparedSlideCount` stays bounded, `pendingDisposals` returns to zero, and active decoded
  bytes fall after each transition/circuit cleanup.
- The rolling post-GC heap floor does not show the monotonic v52.8 pattern. Investigate if the
  30-minute minimum grows by more than 5% of maximum heap for three consecutive windows.
- At 85–91% post-cooldown pressure, playback resumes conservatively with one selected decode. At
  92%+ pressure the circuit remains closed rather than attempting another allocation.
- After a real decode OOM remains at 92%+ for one minute, `MEMORY_SELF_RECOVERY_GC` may occur once.
  If a later sample remains critical for at least 10 more seconds,
  `MEMORY_PROCESS_RESTART_SCHEDULED` may occur once and must be followed after relaunch by
  `MEMORY_PROCESS_RECOVERY_COMPLETED`. No second automatic restart may occur inside 15 minutes.
- After `ACTIVITY_STOPPED`, there is no `TRANSITION_STARTED`, `SLIDE_RENDERED`, or
  `COLLAGE_RENDERED` until the Activity starts again. A decode completion from work that was
  already blocking at stop time is acceptable only when its result is discarded.
- Each minute's `HEAP_SAMPLE` contains `batteryTelemetryStatus`. When it is `OK`, retain
  `batteryLevelPct`, `batteryStatus`, `powerSource`, `batteryVoltageMv`, and any available
  `batteryCurrentUa`/`batteryChargeCounterUah` so a reboot can be correlated with power state.

## Six-hour release soak

After the four-hour gate passes, repeat for six hours with the approximately 28,000-photo SMB
library, three-photo collages enabled, web preview polling, and one diagnostics download. This
is the release evidence required by the integrated qualification document.

## Optional debug-only heap capture

If the heap floor still rises, attach `adb` before the process reaches critical pressure and
capture a heap dump only on a private test device. Do not include the dump in a support bundle
or share it without reviewing it for file paths, URLs, and credentials. Capture the matching
diagnostics bundle and note the monotonic timestamp so retained bitmap owners can be correlated.

## Evidence to retain

- Complete timestamped JSONL support bundle.
- App version, build type, device model, API level, heap maximum, display resolution.
- Start/end UTC times and any NAS interruption times.
- Whether the process restarted and the Android exit reason when available.
- Any `MEMORY_SELF_RECOVERY_GC`, `MEMORY_PROCESS_RESTART_*`, or
  `MEMORY_PROCESS_RECOVERY_COMPLETED` events and whether playback resumed afterward.
- Screenshot or written observation for any black frame or visual corruption.
