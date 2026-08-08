# FamilyPhotoFrame v52.9 better logging release notes

Version: `0.12.7-prerelease` (`versionCode 27`).

## Diagnostics

- Added catalog-governed schema-v2 events with explicit severity, sequence, monotonic time, origin, correlation IDs, and structured fields.
- Added complete correlated logging for Android/web Rebuild, settings/source activation, health checks, scans, cancellation, coalescing, supersession, reconciliation, and terminal outcomes.
- Added synchronous crash envelopes, main-thread stall evidence, and guarded API-30+ process-exit classification.
- Added Activity, slideshow surface, engine-state, screen-interactive, display, and immersive-mode state-change evidence.
- Added installation-specific HMAC identity tokens and one privacy policy for memory, disk, crash, web, and export surfaces.
- Split standard and bulk evidence, added aggregation/rate limiting, bounded writer state, and writer/drop/retention health.
- Added a streamed JSONL support bundle, stable web cursors and filters, corrected summaries, operation timelines, and v1/v2/mixed offline analyzers.

## Web interface

- Removed the duplicate About diagnostics button.
- Consolidated configuration export/import/rollback into one Backup card.
- Moved all four diagnostics list actions above the event list and added request-time timestamped JSONL downloads.
- Reorganized Web control into Identity, Storage and memory, Maintenance, and one combined Web server/remembered-browser card.
- Replaced Playback’s paired-row layout with independent compact columns.

## Integrated review fixes

- Added explicit bundle-end validation and retained-generation loss reporting.
- Corrected analyzer and legacy compiler harnesses after schema/privacy changes.
- Extracted Activity diagnostics into `ActivityDiagnosticsController` to restore separation of concerns.
- Fixed one rendered Playback interval-control overflow.
- Removed Python set-order dependence from the shuffle endurance gate; it now produces exactly 1,425 presentations under different hash seeds.

## Qualification status

All executable offline gates pass, including 200-source Kotlin parsing, pure JVM diagnostics tests, engine/persistence type checks, 95 Room SQL queries, migrations, source/rebuild contracts, privacy/endurance tests, remembered-browser security, transitions, the 1,425-presentation shuffle simulation, and four Chromium viewports.

This remains a prerelease. Gradle 8.9 could not be downloaded in the delivery environment, so Android unit tests, lint, APK assembly, and the required device/soak matrix remain pending. See `FamilyPhotoFrame_v52.9_Phase8_Release_Qualification.md`.
