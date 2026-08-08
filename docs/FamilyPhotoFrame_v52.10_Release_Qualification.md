# v52.10 integrated review and release qualification

Status: implementation complete; release qualification remains incomplete until Android Gradle
and physical-device gates run.

Version: `0.12.8-prerelease` (`versionCode 28`), web revision `v52100`.

## Automated evidence

| Gate | Result |
|---|---|
| Kotlin source parsing/structure | PASS — 205 total Kotlin files (149 production, 49 JVM test, 7 instrumented test) |
| Repository source contracts | PASS — all 29 Python verifiers |
| API-22 memory source contracts | PASS |
| Playback memory policy/state machine | PASS, including concurrent OOM updates and pressure priority |
| Prepared-slide registry/reclaimer executable harness | PASS |
| Pure diagnostics/schema/privacy/rate/crash/watchdog/bundle checks | PASS |
| Diagnostics E2E | PASS — complete simulated 26-hour run; forced rotation reports incomplete evidence honestly |
| Engine and persistence type checks | PASS |
| Room SQL | PASS — 95 queries prepared against SQLite |
| Migration replay | PASS — migrations 1→8, 13 resulting tables verified |
| Historical NAS/source-switch/rebuild/shuffle/transition/web contracts | PASS |
| Folder-balanced shuffle | PASS — deterministic 1,425 committed presentations |
| Rendered web geometry | PASS — 1440×900, 1024×768, 800×1280, and 390×844 |
| Gradle unit tests | BLOCKED before project evaluation — Gradle 8.9 download failed with `Network is unreachable` |
| Android lint | BLOCKED before project evaluation — same Gradle dependency |
| Debug APK assembly | BLOCKED before project evaluation — same Gradle dependency |
| API-22 four/six-hour memory soak | PENDING — requires physical hardware |

## Required device evidence

- Four-hour primary and six-hour release runs in `API22_MEMORY_SOAK_TEST.md`.
- The ONDA V80 PLUS or equivalent API-22 device with an approximately 100 MiB heap.
- Normal single photos plus two- and three-photo portrait collages.
- Diagnostics download, NAS interruption/recovery, and sustained web-preview polling.
- No fatal OOM, ANR, restarted process, black frame, or recycled-bitmap rendering failure.
- Bounded prepared bitmap inventory and a non-growing post-GC heap floor.

## Qualification conclusion

The source contains the targeted mitigations for the observed v52.8 failure, but source-level
evidence cannot prove legacy Android Canvas/Compose disposal timing or long-run heap stability.
Keep the package prerelease until Gradle compilation, unit tests, lint, APK assembly, and the
physical API-22 soak all pass.
