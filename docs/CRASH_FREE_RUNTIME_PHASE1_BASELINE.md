# Crash-free runtime: Phase 1 baseline

Status: Phase 1 baseline established; runtime behavior is unchanged on this branch.

## Immutable comparison points

| Role | Git reference | Commit |
|---|---|---|
| Current-main control | `origin/main` | `dd0369506934fef1873fd933e367d433b3352d69` |
| Long-term hardening base | `origin/fix/long-term-stability` | `d9bbecd94b8fb33e56f6936d74c67cb3c0d8f535` |
| Crash-free development branch | `fix/crash-free-runtime` | starts at `d9bbecd94b8fb33e56f6936d74c67cb3c0d8f535` |

Do not move the control reference during the first A/B run. New changes belong on
`fix/crash-free-runtime` until the hardware qualification gates pass.

## Field-failure baseline

Source evidence: `FamilyPhotoFrame-diagnostics-20260825T043220952Z.jsonl`, generated from the
Onda V80 Plus/API 22 test device.

The current-main session ran for approximately 20 hours 51 minutes. It ended without a Java
uncaught exception, recorded decode OOM, crash envelope, or device reboot. Android API 22 does
not expose a definitive modern process-exit reason, so the exact final mechanism remains
unproven. The strongest evidence is sustained system memory pressure combined with rising native
memory/process PSS.

| Baseline signal | Observed value |
|---|---:|
| Java heap at final sample | 41.8 MiB of 178.2 MiB |
| Maximum Java heap observed | 61.3 MiB |
| Final process PSS | 130.9 MiB |
| Final native memory | 58.9 MiB |
| Native growth regression | approximately 14.4 KiB per presentation |
| `LOW_MEMORY` callbacks | 45 |
| Render acknowledgement timeouts | 211 |
| Transition cancellations | 1,182 of 2,219 (53.3%) |
| SMB health checks | 126 of 126 healthy |

This is not the former per-photo Java/SMB `CIFSContext` leak: Java heap stayed bounded and the
shared SMB context remained healthy. The remaining investigation must identify the owner of the
native/PSS increase and the cause of render/transition churn.

## Previous build report clarification

The previously supplied build log did not contain a Kotlin or Java compilation error. It showed
two independent failures:

1. `:app:testDebugUnitTest` failed. The individual failing test names must be recovered by rerunning
   the task or opening `app/build/reports/tests/testDebugUnitTest/index.html`.
2. `:app:installDebug` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, which means the installed
   application was signed with a different certificate. Use the same signing key, or back up the
   diagnostics/settings and uninstall the incompatible APK before installing the new debug build.

Do not treat an uninstall as part of a normal A/B upgrade: uninstalling clears app-owned data and
would change the test conditions.

## Controlled A/B configuration

Both builds must use the same:

- Onda V80 Plus tablet, charger, Android build, display brightness, and thermal placement;
- approximately 28,000-photo SMB library and NAS account;
- Wi-Fi access point and NAS path;
- slideshow dwell time, portrait-only collage settings, transition configuration, and web polling;
- cache/database starting state where signing compatibility permits an in-place upgrade;
- observation duration and diagnostics sampling configuration.

Run the current-main control first, followed by `fix/crash-free-runtime`. Record UTC start/end
times and any deliberate NAS/network interruptions.

## Qualification gates

After a two-hour warm-up, a candidate must satisfy all of these before promotion:

- no uncaught Java/native crash or unexplained process restart;
- no recurring low-memory callback storm during steady playback;
- native/PSS trend statistically flat, with less than 10 MiB growth over 24 hours;
- no file descriptor, thread, SMB-stream, transfer, bitmap, or transition count that grows without
  returning to its steady-state range;
- render acknowledgement timeout rate below 0.1%;
- transition cancellation rate below 5%, excluding explicitly classified user/lifecycle events;
- recovery from NAS disconnect, corrupt/oversized media, cache pressure, and Activity stop/start;
- seven continuous days on the API-22 tablet before merge to `main`, followed by a 30-day
  unattended qualification run.

## Phase 1 completion checklist

- [x] Preserve `origin/main` as the initial control at `dd036950...`.
- [x] Base crash-free work on the reviewed long-term hardening commit `d9bbecd...`.
- [x] Create `fix/crash-free-runtime` without altering runtime behavior.
- [x] Record the field baseline and the experimental controls.
- [x] Define objective promotion gates.
- [ ] Rerun the full Gradle test/build sequence in an environment with Android SDK and Gradle 8.9.
- [ ] Resolve every named unit-test failure before installing a candidate.
- [ ] Resolve signing compatibility without silently changing A/B test data.

## Phase 2 handoff

Phase 2 adds observability before changing runtime policy: PSS category breakdown, file descriptor
and thread counts, active SMB stream/transfer counters, persistent in-flight operation
breadcrumbs, and complete transition-generation fields in the diagnostic schema.
