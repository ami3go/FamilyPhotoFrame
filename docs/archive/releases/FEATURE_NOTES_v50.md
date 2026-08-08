# Family Photo Frame v50 — Reliability fixes

## Fixed from diagnostics

- Bounded Coil's decoded-image memory cache to 32 MiB on low-memory frames.
- Added one-minute heap/PSS/native/cache telemetry and memory-pressure cache trimming.
- Made next-photo preloading coroutine-bound so obsolete requests are cancelled.
- Recycles generated blurred backdrops when a slide leaves composition.
- Keeps HEIC/HEIF indexed but marks it unsupported before download on Android versions below 8.0 (API 26), preventing repeated SMB downloads and decode loops.
- Permanently suppresses a device-incompatible/corrupt image from the playback pool while preserving it in the index.
- Advances immediately after a current-photo decode failure.
- Serializes physical indexing and coalesces duplicate scans for the same source/configuration.
- Preserves favourites, hidden state, last-shown history, decode suppression, cache keys, and backfilled EXIF across Room rescans; changed file bytes reset only content-dependent state.
- Splits slideshow evidence into `SLIDE_SELECTED`, `SLIDE_RENDERED`, `DECODE_FAILED`, and `DECODE_UNSUPPORTED`.
- Marks a photo shown only after Coil reports successful rendering.
- Adds structured decode stage, extension, MIME type, exception class, and source fields.
- Adds structured weather HTTP/network/parse/timeout diagnostics and correct cancellation handling.
- Adds structured web route/method/exception diagnostics.
- Records uncaught exceptions across process restarts without logging exception messages or secrets.

## HEIC behavior on old frames

Android 6 cannot decode HEIC/HEIF through the platform image decoder. Such files remain discoverable and indexed, but are excluded from playback on that device. Android 8.0 and newer retain platform HEIC playback. Displaying HEIC on Android 6 would require a bundled native decoder or server-side JPEG conversion, neither of which is included in this offline patch.

## Verification performed

- Project consistency audit passed.
- Kotlin source parser found no syntax errors.
- Room SQL preparation passed for all queries.
- Room migration replay passed through schema version 5.
- All Android XML resources parsed.
- Focused HEIC capability regression checks passed.
- Diagnostics analyzer remains backward-compatible with legacy `SLIDE_SHOWN` logs and prefers `SLIDE_RENDERED` for new logs.

A complete Android/Gradle build still requires Gradle 8.9 and dependency artifacts, which were not cached in the offline environment.
