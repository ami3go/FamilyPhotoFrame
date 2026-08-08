# FamilyPhotoFrame v51.3 — Legacy Code Review and Refactor Report

**Baseline reviewed:** FamilyPhotoFrame v51.2  
**Refactored release:** FamilyPhotoFrame v51.3 / `0.11.3-prerelease`  
**Minimum Android version:** Android 5.0 / API 21  
**Review type:** Static architecture, compatibility, maintainability, source validation,
and behaviour-preserving refactor

## 1. Verdict

The accumulated legacy structure has been materially improved. The release is suitable
for local build and hardware regression testing. The refactor deliberately preserves
Android 5 support, current routes, persisted settings, slideshow behaviour, and web API
compatibility.

The largest remaining maintenance risk is `SlideshowViewModel.kt`, which still combines
source orchestration, scheduling, imports/exports, weather, brightness, health, security,
and user intents. It should be split in a later release only after a complete Gradle and
real-device baseline is available.

This review does **not** declare the application ready for unrestricted public release.
The existing NanoHTTPD/plain-HTTP security remediation remains a separate release gate.

## 2. Before and after

| Hotspot | v51.2 | v51.3 owning file | Change |
|---|---:|---:|---:|
| `SettingsScreen.kt` | 1,927 lines | 314 lines | −1,613 |
| `SlideshowScreen.kt` | 1,636 lines | 1,106 lines | −530 |
| `WebServerController.kt` | 1,358 lines | 842 lines | −516 |
| `WebUiAssets.kt` | 170 lines / ~89 KiB | 12 lines | assets extracted |
| `MainActivity.kt` | 362 lines | 297 lines | compatibility extracted |
| Main Kotlin files | 87 | 101 | responsibilities split |
| Main Kotlin lines | 19,703 | 20,423 | explicit modules/tests added |

The total line count increased slightly because previously implicit responsibilities are
now explicit, independently testable modules. Concentration and review-diff size were
reduced substantially.

## 3. Findings and corrections

### 3.1 Runtime transition aliases were obsolete

**Finding:** `NONE` and `SLIDE` remained live transition enum members even though the UI
and renderer support ten modern transitions. Runtime code repeatedly normalised those
aliases.

**Correction:** Historical values are now translated only in
`TransitionMode.fromStorage`:

```text
none  → crossfade
slide → horizontal glide
```

The obsolete values no longer exist in the runtime enum, selector, or renderer.

### 3.2 Embedded browser assets were coupled to Kotlin server code

**Finding:** CSS and JavaScript were embedded in one large Kotlin object, producing large,
hard-to-review source diffs.

**Correction:** Assets were split into:

- `WebUiCss.kt`
- `WebUiScript.kt`
- a 12-line `WebUiAssets.kt` compatibility facade

The server-facing API and cache-revision mechanism remain unchanged.

### 3.3 Web routes mixed unrelated security and feature paths

**Finding:** Public pairing, legacy compatibility, remembered-browser management,
versioned reads, writes, and uploads were interleaved in one large router.

**Correction:** `WebConfigServer` now delegates to explicit route groups:

- public;
- compatibility;
- remembered browser;
- versioned read;
- versioned write;
- upload.

Authentication, CSRF, request limits, routes, and response formats are preserved.

### 3.4 Settings validation lived inside server lifecycle code

**Finding:** `WebServerController` mixed lifecycle, JSON parsing, validation, credential
scope rules, settings persistence, and JSON projection.

**Correction:** Added:

- `WebSettingsPatchApplier.kt` for parsing, validation, credential protection, and writes;
- `WebSettingsJson.kt` for redacted configuration projection.

`WebServerController` returned to lifecycle and backend orchestration.

### 3.5 Production backend defaults hid missing implementations

**Finding:** `WebBackend` supplied placeholder defaults, allowing production code to
compile while a new endpoint silently returned “unavailable” at runtime.

**Correction:** The production interface is strict. Every capability must be implemented.
`WebBackendAdapter` contains defaults only for deliberately partial test doubles.

### 3.6 Android settings were a monolithic Compose file

**Finding:** One 1,927-line file owned navigation and every source, playback, schedule,
display, security, and backup control.

**Correction:** `SettingsScreen.kt` now owns only navigation/layout. Seven cohesive files
own their respective setting groups and common controls.

### 3.7 Slideshow composition mixed controls and decoding

**Finding:** `SlideshowScreen.kt` contained screen composition, touch curation dialogs,
bitmap decoding, collage preparation, and bitmap cleanup.

**Correction:** Added:

- `SlideshowTouchControls.kt`;
- `SlideshowPreparation.kt`.

The prepared-presentation contract, preload limits, curation behaviour, and transition
pipeline are unchanged.

### 3.8 Deprecated fullscreen calls were spread through MainActivity

**Finding:** Android 5/6 requires legacy decor flags, but those calls were mixed throughout
activity lifecycle code.

**Correction:** `ImmersiveModeController.kt` is now the single compatibility owner.
`MainActivity` delegates install, recovery, and release.

The deprecated calls are intentionally retained because `minSdk = 21`; removing them
would reduce fullscreen reliability on the target frame.

### 3.9 Verification tooling contained obsolete online assumptions

**Finding:** Some checks used a hard-coded `/tmp` compiler path or downloaded Kotlin even
when a compiler was already installed. One transition check therefore skipped real
compilation.

**Correction:** Checks now prefer:

1. explicit `KOTLINC`;
2. installed `kotlinc`;
3. cached pinned compiler;
4. download as a last resort.

This correction exposed a genuine invalid Kotlin expression in playlist normalisation:

```text
transition = transition?
```

It was corrected to:

```text
transition = transition
```

The engine/persistence type-check then passed.

### 3.10 Historical project evidence obscured current documentation

**Finding:** The repository root contained more than sixty Markdown files, including old
phase, fix, and version notes.

**Correction:** Historical evidence is retained under:

- `docs/archive/releases/`
- `docs/archive/phases/`
- `docs/archive/reviews/`

The root now contains current operational documentation. `docs/ARCHITECTURE.md` defines
current ownership boundaries.

## 4. Verification completed

The following passed after the final correction:

- v51.3 refactor architecture contract;
- project consistency audit;
- 57 Room SQL query preparations;
- migrations through schema version 6;
- web UI and JavaScript checks;
- user-feature phases 1–5;
- remembered-browser phases 1–5;
- fullscreen and Settings navigation contract;
- transition phases 1–5;
- actual transition selector/performance Kotlin compilation and 1,000-cycle test;
- engine and persistence Kotlin type compilation;
- 26-hour diagnostics writer/analyser simulation;
- forced diagnostics rotation scenario;
- source whitespace/diff checks.

## 5. Verification not completed

A complete Gradle build could not run in the packaging environment because the Gradle
8.9 wrapper distribution was not cached and `services.gradle.org` was unreachable.

Required local gate:

```bash
chmod +x gradlew
./gradlew clean testDebugUnitTest lintDebug installDebug
```

Required hardware checks:

- Android 5/API 21 smoke test;
- Huawei Android 6/API 23 slideshow soak;
- fullscreen recovery after system picker/dialog/keyboard;
- settings/web control regression;
- NAS source reconnect;
- bulk upload while slideshow plays;
- remembered-browser restart and expiry.

## 6. Remaining technical debt

### High priority

1. **`SlideshowViewModel.kt` remains approximately 2,844 lines.**  
   Future extraction candidates: source coordinator, transfer service, playlist scheduler,
   brightness coordinator, weather controller, health aggregator, and web-security facade.
   This should be done behind integration tests, not by mechanical splitting alone.

2. **Embedded HTTP security remains a release blocker.**  
   NanoHTTPD replacement, pre-parse body limits, and encrypted web transport are not part
   of this refactor.

3. **Full Android build evidence is still required.**  
   Source/type checks do not replace AGP, Compose, Room code generation, R8, and APK tests.

### Medium priority

4. `WebConfigServer.kt` remains large, although route ownership is now explicit.
5. API 21/22 Keystore compatibility uses deprecated platform APIs intentionally and needs
   continued real-device regression.
6. `compileSdk`/`targetSdk` remain 35; the separate API 36 migration plan is still pending.

## 7. Recommended next refactor

Do not start another broad mechanical cleanup immediately. First establish a successful
v51.3 Gradle build and hardware baseline. Then extract `SlideshowViewModel` in this order:

1. `SourceCoordinator`;
2. `PlaylistScheduleCoordinator`;
3. `BrightnessCoordinator`;
4. `TransferCoordinator`;
5. `HealthAggregator`.

Each extraction should retain the ViewModel as the UI-facing intent facade and have an
independent regression gate.
