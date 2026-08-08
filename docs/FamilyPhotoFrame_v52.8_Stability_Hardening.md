# FamilyPhotoFrame v52.8 — stability hardening

Version: `0.12.6-prerelease` (`versionCode 26`)  
Web asset revision: `v5280`

## Implemented fixes

### Slideshow memory

- Coil's fixed 32 MiB memory cache is replaced by a heap-aware 10% budget bounded to
  8–16 MiB. A device with a 100 MiB Java heap receives a 10 MiB cache.
- Prepared slides and blurred backdrops do not duplicate their retained bitmap inside
  Coil's memory cache.
- Blur allocation failures fall back to the existing solid background.
- Display-bound backdrop bitmaps are never manually recycled.
- Heap pressure is checked every 10 seconds while one-minute durable samples are retained.
  Dangerous pressure clears reusable Coil and web-preview caches.

### Diagnostics

- Diagnostic events enter a bounded queue and are appended by one daemon writer, so
  slideshow and transition callbacks do not perform file I/O.
- Queue overflow is recorded as a privacy-safe aggregate event.
- Diagnostics downloads stream the header and retained JSONL files through a chunked HTTP
  response. The former multi-copy 18 MiB string path is removed.
- Flush barriers serialize downloads, clearing, rotation, and prior queued writes.

### Configuration imports

- Plain and encrypted files are read incrementally with a 4 MiB limit.
- File access runs on the I/O dispatcher; JSON parsing and PBKDF2/AES-GCM work run on the
  background dispatcher.
- Encrypted envelopes are rejected before key derivation unless their algorithm, iteration
  count, key size, tag size, salt, nonce, ciphertext, and total envelope size are safe.
- Legitimate older bundle versions remain readable.

### NAS indexing

- PROPFIND responses are parsed from the HTTP stream one `<response>` element at a time.
- The parser enforces a 32 MiB wire limit and a 256 KiB UTF-8 limit per response record.
- HTTP bodies disconnect on success, error, cancellation, and malformed input.
- The `LOCAL_ONLY` EXIF policy now opens only SAF, bundled, and local-upload photos.
  SMB, Synology, and WebDAV metadata remains eligible for display-time backfilling.
- Existing indexed rows remain preserved after incomplete remote scans.

### Folder and shuffle state

- The folder manager is a dedicated searchable `LazyColumn`, so only visible rows are
  composed. Room is queried on entry or explicit refresh, not after each checkbox.
- A checkbox produces one atomic DataStore update for the complete selection.
- Legacy folder-name selections are normalized in linear time; the 10,000-folder contract
  executes in the JVM release harness.
- Restored shuffle-scope diagnostics use a 128-key bounded tracker, and deleting a Room
  scope removes its process-local key.

## Verification completed in this environment

- All 182 main Kotlin files passed syntax and structural parsing.
- The engine, WebDAV source/parser, diagnostics, indexer, Room call sites, and shuffle
  persistence layer passed Kotlin type checking against the offline dependency stubs.
- All dependency-free JVM assertions passed, including streamed WebDAV limits, bounded
  imports, heap budgeting, 10,000-folder selection, and bounded shuffle tracking.
- All 20 Python release verifiers passed.
- All 95 Room queries prepared successfully against SQLite; migrations 1→8 replayed and
  all 13 migrated tables matched their entities.
- Diagnostics passed a 26-hour simulation and forced 32/64 KiB rotation scenario.
- The dynamic shuffle simulation committed 1,425 presentations without cycle repeats.
- Embedded JavaScript parsed with Node and all responsive-web contracts passed.

## Remaining release qualification

The Gradle wrapper could not reach `services.gradle.org`, and Gradle 8.9 was not present in
the environment. Therefore `testDebugUnitTest`, `lintDebug`, and `assembleDebug` could not
start; this source package is not represented as a fully qualified Android release.

On a connected development machine, run:

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest lintDebug assembleDebug
```

Then complete the API 21/API 22 device gate: 1,000 transitions, blurred portrait fallback,
diagnostics download during playback, oversized import rejection, failed Synology → SMB →
Rebuild, a large flat WebDAV folder, a six-hour heap soak, and the existing 24-hour NAS
recovery test.
