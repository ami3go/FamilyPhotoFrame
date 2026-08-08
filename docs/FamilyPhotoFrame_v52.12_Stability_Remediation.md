# v52.12 stability remediation

Status: prerelease; source remediation complete, Android build and physical-device qualification pending.

Version: `0.12.10-prerelease` (`versionCode 30`), web revision `v52120`.

This increment applies the actionable findings from the v52.11 stability audit while preserving
the API-22 memory guard, portrait-only collage behavior, shuffle state, web-preview stability,
and Web Control layout.

## Remediations

| Audit item | Change |
|---|---|
| STAB-01 remote source recovery | One synchronized `SourceRecoveryCoordinator` now reconciles each health check with the actual playback pool. Playback read failures invalidate in-flight checks, wake healthy polling immediately, and require promotion revalidation after indexing. |
| STAB-03 factory reset | Reset is application-owned, single-flight, non-cancellable after admission, and awaited before success. Producers and uploads are quiesced first; Room tables are cleared transactionally; settings and caches are reset; completed uploaded photo files are preserved; success/failure is logged before a controlled restart. |
| STAB-04 web bounds | NanoHTTPD now uses four workers and an eight-connection queue. Excess connections receive HTTP 503. Upload session purge/count/insert is one synchronized admission transaction. |
| STAB-05 cancellation | Source activation rethrows `CancellationException` and converts only ordinary failures to fallback values. |
| STAB-06 web lifecycle | Settings changes and manual restarts use one lifecycle lock plus generation checks, preventing stale stop/start callbacks from reopening or replacing a newer server. |
| STAB-07 browser gate | Geometry checks ignore deliberately hidden controls and exercise both hidden and authenticated remembered-browser states. |
| STAB-08 diagnostics identity | Process sessions retain the full 128-bit UUID instead of an eight-hex-character prefix. |
| STAB-09 release evidence | Current documentation is versioned accurately, recovery integration is executable, and the historical 205-file count is labelled as total rather than production-only. |

## Factory-reset contract

Factory reset removes settings, source credentials, remembered-browser trust, Room photo/index
rows, shuffle state, and memory/disk caches. Completed files in the app-managed Local uploads
library are deliberately preserved and will be re-indexed when the user configures playback
again. In-progress upload/session staging is cancelled and removed.

Room owns one transaction for all database tables. DataStore and filesystem caches cannot join
that SQLite transaction, so the complete operation is idempotent, awaited, and followed by a
process restart. A failure returns a categorical message and also restarts into the persisted
state; it is never reported as success.

## Verification boundary

Required before promotion from prerelease:

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug installDebug`
- Instrumented Room/database and web lifecycle tests on an emulator/device.
- Six-hour API-22/approximately-100-MiB heap soak from `API22_MEMORY_SOAK_TEST.md`.
- Real NAS playback read failure followed by healthy recovery and exactly one promotion.
- Saturation test with slow/idle LAN clients, confirming bounded threads and HTTP 503 rejection.

The delivery environment cannot download Gradle 8.9, so Android/KSP compilation, lint, APK
assembly, and generated Room schema export are not claimed here. `app/schemas` must be populated
and reviewed from a successful local Gradle build before release.
