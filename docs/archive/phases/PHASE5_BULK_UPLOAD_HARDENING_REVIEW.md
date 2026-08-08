# Phase 5 Review — Bulk Upload Integration and Hardening

## Delivered

- App-managed `Local uploads` source, compatible with Android 5/API 21 and requiring no broad storage permission.
- Android setting that explicitly enables privileged web uploads and selects duplicate policy.
- Paired/CSRF-protected upload sessions with one bounded streaming request per file.
- Browser multi-file picker, two-worker queue, per-file progress, cancellation, completion, and indexing.
- App-private `.part` staging, SHA-256 duplicate detection, magic-byte validation, bounded dimension checks, and atomic commit/replace rollback.
- Storage preflight, 250 MiB reserve, per-file and per-batch limits, two simultaneous upload streams, one indexing worker, stale-part cleanup, and session expiry.
- Upload results join the local index only after batch completion; slideshow rendering remains independent.
- Web management for playlists, playlist schedules, brightness/night mode, health, and uploads.

## Review corrections

1. **Session ownership:** upload sessions are bound to a SHA-256 derivative of the paired browser session; another paired browser cannot inspect, complete, or cancel them.
2. **Rejected-file completion:** every accepted file slot reaches `COMPLETED`, `DUPLICATE`, `FAILED`, or `CANCELLED`, so one invalid image cannot leave the batch permanently incomplete.
3. **Session capacity:** only non-terminal sessions count against the four-session/two-per-browser limits; completed/cancelled sessions are retained briefly for status and then purged.
4. **Atomic file-count reservation:** concurrent requests cannot exceed the declared batch file count or reuse the same client file ID.
5. **Overflow-safe byte accounting:** declared bytes are compared against remaining expected bytes without signed-overflow addition.
6. **Transport validation:** file requests require `application/octet-stream`, a positive `Content-Length`, and reject chunked transfer encoding before streaming.
7. **Cancellation cleanup:** only temporary parts owned by the cancelled session are removed; committed photos and other sessions are untouched.
8. **Indexing pressure:** one reconciliation scan occurs after batch completion, not after every individual file.
9. **Playlist completeness:** final review added rename, duplicate, enable/disable, default, and reordering actions plus schedule weekday, priority, date range, and per-rule enable controls.

## Known limitations

- The current embedded control panel is plain HTTP. Upload is disabled by default and must be used only on a trusted private LAN.
- Locally uploaded photos in app-managed storage may be removed when the app is uninstalled.
- HEIC/HEIF upload is rejected on the Android 5/6 baseline; browser-side conversion is not included.
- Writable SAF upload destination remains a future optional enhancement; this phase uses the app-managed library.
- Resumable/chunked uploads are deliberately not supported in this first bounded protocol.

## Gate result

**PASS for source-level Phase 5 implementation and offline verification.** A real Gradle build, device installation, browser upload test, low-storage test, and long-running upload/slideshow endurance test remain mandatory before production deployment.

## Final usability corrections

10. **Rendered queue controls:** connected the actual web buttons for Pause, Resume, Cancel, Retry failed, and Clear list. The earlier markup exposed controls that were not all bound.
11. **Drag-and-drop:** the Photos upload card now accepts desktop drag-and-drop and keyboard activation while retaining the normal mobile multi-file picker.
12. **Queue pause semantics:** pause prevents additional files from starting; already-active bounded streams finish rather than being corrupted mid-file.
13. **Cancellation during session creation:** a cancel request made while the session is being created is honoured immediately after the server returns the session id, preventing an orphan active session.
14. **Control state:** Start is disabled until uploads are enabled and files are selected; Pause/Resume require a live session; retry is enabled only for failed items; queue clearing is blocked while a batch is active.
