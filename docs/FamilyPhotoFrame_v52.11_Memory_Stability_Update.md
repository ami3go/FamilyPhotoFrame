# FamilyPhotoFrame v52.11 memory stability update

Version: `0.12.9-prerelease` (`versionCode 29`).

This focused update follows the API-22 v52.10 soak-log review. It preserves the
portrait-only collage rule, folder-balanced shuffle, web-preview anti-flicker fix,
and the latest Web Control/Diagnostics layout.

## Changes

- Guarded mode enters at 85% heap and can leave only after the heap remains below
  70% continuously for three minutes.
- Critical mode enters at 92% heap and cannot relax until heap remains below 80%
  continuously for five minutes. It relaxes to Guarded, so preload is not
  immediately re-enabled.
- Activity stop cancels selected and next-presentation preparation through an
  observed lifecycle state. Cancellation continues to retire uncommitted bitmap
  graphs.
- `appBitmapCount` now reports every distinct application-owned bitmap in the
  prepared-slide registry instead of only generated transition-blur bitmaps.
- Healthy remote-source checks run every ten minutes and are classified as
  `PERIODIC_HEALTH_MONITOR`; outage recovery retains its fast escalating backoff.
- Decode failures always carry a categorical `errorClass`, using `UNKNOWN` when
  the decoder supplies no class.
- Rotated support bundles report `PARTIAL_RETENTION` globally and per stream;
  they no longer describe retained fragments as a complete session history.

## Qualification boundary

Pure Kotlin, diagnostics E2E, SQL, migration, source-contract, and archive tests
must pass before packaging. Android Gradle compilation and an API-22 six-hour soak
remain required on a machine with the Gradle 8.9 distribution and the target
tablet available.
