# v52.9 Phase 2 review — correlated source and scan diagnostics

## Implemented behavior

- Every persisted-source activation now has a schema-v2 refresh operation with an
  explicit trigger, origin, privacy-safe configuration revision, parent/child IDs,
  stages, elapsed durations, and one guarded terminal outcome.
- Android and web Rebuild actions emit `REBUILD_REQUESTED` before work is queued.
- Source tests and health checks have bounded child operations and stable error codes;
  provider messages, server values, and credentials are not recorded.
- Physical scans preserve their owner operation. Same-signature requests identify that
  owner when coalescing; source changes identify the replacement when superseding.
- Scan progress requires both five elapsed seconds and 1,000 newly found files. Normal,
  partial, cancelled, failed, and missing-`Finished` paths retain final counts and one
  terminal event. Incomplete scans still skip reconciliation and preserve cached rows.
- First-run selection, credential changes, configuration imports, filter changes,
  scheduled refreshes, recovery, and local uploads propagate distinct triggers.

## Review findings corrected in this phase

1. A settings save could be observed by DataStore before the explicit credential/import
   refresh, creating a generic duplicate operation. Explicit mutations now suppress only
   their own observer echo and enqueue exactly one requested trigger.
2. A coalesced scan originally retained only its total count. The single-flight result
   now carries found, total, error, EXIF-miss, reconciliation, and completion fields to
   every waiter.
3. A scan cancelled before entering its body could remain in the active-operation
   registry. Supersession cleanup now finishes the operation defensively.
4. The single-flight key used a mutable UI snapshot. It now uses the immutable request
   signature and filters that own the scan.
5. Local-upload indexing originally had no failed or cancelled refresh terminal. All
   three terminal outcomes are now recorded and cancellation is rethrown.
6. The concurrent schema test could fill the writer queue and count legitimate overflow
   markers as ordering failures. Its queue is now deliberately sized for the ordering
   test; saturation remains covered by the dedicated writer tests.

## Verification evidence

- `scripts/verify-source-logging-v529.py`
- Full Kotlin source parse and structure checks (185 files at this phase)
- Pure JVM diagnostics and policy suite
- Engine/persistence type check
- 95 Room queries and the complete migration chain
- Diagnostics 26-hour and forced-rotation simulations

## Qualification still required

Gradle unit tests, Android lint, APK assembly, and the task's Android device scenarios
remain final release gates. In particular, the failed Synology to SMB switch, repeated
Rebuild coalescing, source-switch cancellation, and scheduled/web overlap must be
confirmed on-device with their exported operation timelines.
