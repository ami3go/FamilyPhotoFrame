# v52.9 Phase 8 integrated review and release qualification

Status: implementation complete; release qualification incomplete because Android Gradle and hardware gates could not run in this environment.

## Automated evidence

| Gate | Result |
|---|---|
| Kotlin source parsing/structure | PASS — 200 main Kotlin files |
| Pure diagnostics/schema/privacy/rate/crash/watchdog/bundle checks | PASS |
| Diagnostics E2E | PASS — complete simulated 26-hour run; forced-rotation run reported incomplete evidence |
| Engine and persistence type checks | PASS |
| Room SQL | PASS — 95 queries prepared against SQLite |
| Migration replay | PASS — migrations 1→8, 13 resulting tables verified |
| NAS/source-switch/rebuild contracts | PASS |
| Web/API/static contracts | PASS |
| Rendered web geometry | PASS — 1440×900, 1024×768, 800×1280, 390×844 |
| Transition endurance | PASS — 1,000 completed transitions |
| Folder-balanced shuffle | PASS — 1,425 committed presentations plus persistent-cycle checks |
| Remembered-browser security/manager suite | PASS |
| Source archive integrity/exclusions/fresh extraction | PASS |
| Gradle unit tests | BLOCKED before project evaluation — Gradle 8.9 distribution unavailable |
| Android lint | BLOCKED before project evaluation — same Gradle dependency |
| Debug APK assembly | BLOCKED before project evaluation — same Gradle dependency |

The wrapper was attempted with a writable `GRADLE_USER_HOME`; it tried to fetch `https://services.gradle.org/distributions/gradle-8.9-bin.zip` and failed with `Network is unreachable`. These three gates are not reported as passes.

## Integrated review findings corrected

- Added a required `bundleEnd` record to the complete mixed-schema fixture.
- Updated the schema compatibility verifier to inspect the shared parser that now owns v1/v2 loading.
- Corrected the remembered-browser compiler harness to provide the privacy token dependency.
- Extracted lifecycle logging from `MainActivity` into a dedicated controller; the Activity is again covered by a bounded-size architecture contract.
- Normalized diagnostics export format metadata so a field value cannot be mistaken for an emitted event code.
- Sorted set-derived shuffle fixtures before seeded randomization, making the 1,425-presentation release gate deterministic across Python hash seeds.

## Required device matrix — pending

No Android device or emulator is available in this environment. The following task gates must still be executed before promotion from prerelease:

- Android 5.1/API 22 low-memory tablet.
- API 30+ device/emulator for `ApplicationExitInfo`.
- Approximately 28,000-photo SMB library, including failed Synology → SMB → Rebuild.
- Large WebDAV scan, repeated source switches/rebuilds, and diagnostics export during playback.
- Fullscreen loss/recovery.
- Debug-only Java crash and controlled main-thread stall/recovery.
- API-30+ process kill/restart classification.
- Six-hour playback/heap soak and 24-hour NAS offline/recovery scenario.

## Acceptance conclusion

Acceptance criteria 1–16 and 18–22 are covered by implementation and executable offline evidence. Archive verification in criterion 17 passed, but that criterion remains incomplete until Gradle tests, lint, APK assembly, and the device matrix succeed. The source must therefore remain `prerelease` and must not be described as fully release-qualified.
