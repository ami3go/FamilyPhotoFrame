# Feature Notes v48 — Grouped Settings and Playback Interval

## Implemented

### Two-level Android-style settings navigation

The settings root now contains only these group entries:

- Photos
- Playback
- Display
- Schedule
- Device
- Backup and restore
- About

Each group opens a dedicated submenu. The visible back arrow, Android back gesture,
and remote/keyboard Back key return to the group list. Back from the group list
returns to the slideshow.

All controls from the previous flat settings page remain available in one of the
submenus.

### Playback interval control

- Canonical range: 3–600 seconds (10 minutes).
- Button step: 5 seconds.
- Added a Material slider covering the complete range.
- Slider, displayed value, and buttons share optimistic local state, so button taps
  update immediately while persistence completes asynchronously.
- The selected value is still written through `SettingsRepository`; the existing
  settings flow applies it to `SlideshowEngine.setTiming(...)`.
- Added shared interval clamping/adjustment policy and JVM unit tests for normal and
  boundary behavior.

## Verification performed

- `python3 scripts/check-consistency.py` — passed.
- Parsed all `app/src/main/**/*.xml` files — passed.
- Compiled and executed the pure Kotlin interval policy checks — passed.
- Kotlin parser checks found no syntax diagnostics in the modified Compose screen or
  activity.

A full Gradle Android build was attempted but could not run in the offline sandbox:
Gradle 8.9 was not cached and `services.gradle.org` was unreachable.
