# FamilyPhotoFrame diagnostics schema v2

Status: implemented in v52.9 (`0.12.7-prerelease`). Schema-v1 JSONL remains readable by the bundled analyzers.

## Event envelope

Every schema-v2 event is one JSON object on one line and contains:

| Field | Contract |
|---|---|
| `schemaVersion` | Integer `2` |
| `sequence` | Positive, process-session sequence number |
| `atEpochMs` | Wall-clock event time |
| `elapsedRealtimeMs` | Monotonic process time used for ordering and duration |
| `sessionId` | Privacy-safe process-session identity |
| `severity` | `DEBUG`, `INFO`, `WARN`, `ERROR`, or `FATAL` |
| `category` | Catalog-defined subsystem |
| `code` | Registered event code |
| `origin` | `APP`, `ANDROID_UI`, `WEB_UI`, `SCHEDULER`, `RECOVERY`, `SYSTEM`, or `INTERNAL` |
| `operationId` | Correlated operation identity or `null` |
| `parentOperationId` | Parent operation identity or `null` |
| `fields` | Catalog-allowlisted, privacy-filtered object |

The event catalog in `DiagnosticEventSpec.kt` owns category, severity, standard/bulk routing, permitted fields, rate policy, operation requirements, and crash-envelope eligibility. Unknown production codes are converted into a bounded `DIAGNOSTICS_UNKNOWN_EVENT`; release verification fails when an unregistered call site is found.

## Ordering and correlation

- `(sessionId, sequence)` is the durable identity and stable paging cursor.
- `elapsedRealtimeMs` orders work inside one process without trusting wall-clock changes.
- `operationId` follows rebuild, source apply/test/health, recovery, and scan work across coroutine boundaries.
- `parentOperationId` connects child health/scan operations to their source refresh.
- Terminal outcomes distinguish completion, failure, cancellation, coalescing, supersession, incomplete scans, and reconciliation decisions.

## Privacy boundary

All events pass through one allowlist and redaction policy before entering memory, files, web responses, crash evidence, or exports. Raw names, paths, URIs, URLs, hosts, usernames, credentials, tokens, PINs, OTPs, GPS, and EXIF text are not permitted. Identifiers are installation-specific HMAC tokens such as `folder_<digest>` and cannot be correlated across installations. The identity key and crash evidence are excluded from Android backup.

## Streams and retention

- Standard: critical lifecycle, source, scan, health, memory, crash, and writer evidence.
- Bulk: repetitive slide, transition, shuffle, preview, and similar high-volume evidence.
- Combined retained budget: at most 18 MiB.
- Queue depth, drops, sink errors/recovery, flush timeouts, bytes, generations, sequence ranges, last writes, rotations, privacy drops, and crash-envelope state are exposed through `DiagnosticsHealthSnapshot`.
- Aggregation/rate control prevents repetitive events from evicting critical standard evidence.

## Streamed bundle records

The support export is JSON Lines and is streamed without constructing the full result in memory. Non-event metadata records use `recordType` and include:

- `bundleMetadata`
- `runtimeSnapshot`
- `diagnosticsHealth`
- `streamBoundary`
- `bundleEnd`

`bundleMetadata.evidenceIncomplete` and retention health must be consulted before drawing conclusions. A missing `bundleEnd` means the export was truncated.

## Consumers

- The web API uses stable cursors and server-side severity, category, session, code, trigger, operation, origin, and text filters.
- The on-device screen shows bounded structured recent evidence and exports the same full stream.
- `scripts/analyze-diagnostics.py` and `scripts/analyze-transition-diagnostics.py` accept v1, v2, and mixed bundles, emit Markdown and JSON reports, reconstruct operations, scan privacy, and fail release gates for invalid or incomplete critical evidence.

