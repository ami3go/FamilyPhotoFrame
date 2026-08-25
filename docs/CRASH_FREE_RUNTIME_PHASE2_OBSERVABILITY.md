# Crash-free runtime: Phase 2 observability

Status: implemented on `fix/crash-free-runtime`; Android build and physical-device validation pending.

Version: `26.40.3` (`versionCode 40`).

Phase 2 intentionally changes evidence collection, not slideshow or memory-protection policy. It
targets the gaps found in `FamilyPhotoFrame-diagnostics-20260825T043220952Z.jsonl`: total PSS rose
while Java heap stayed bounded, transition fields were silently removed by the catalog, and the
process disappeared without a durable in-flight stage.

## Added evidence

| Area | New evidence |
|---|---|
| Process memory | Dalvik/native/other PSS, total PSS, native allocator bytes |
| System memory | Available bytes, Android low-memory threshold and low-memory flag |
| Process resources | Open file descriptors and threads from procfs |
| SMB ownership | Active/peak/opened/closed streams and oldest active stream age |
| Cache transfers | Active/peak/started/finished transfers and oldest active transfer age |
| Presentation pipeline | App-private persistent operation/stage/active breadcrumb |
| Transitions | Host and transition generation, complete timing fields, tokenized endpoints, cancellation initiator |

The resource timestamp registries are hard-bounded at 1,024 active entries. Active and cumulative
counters remain visible if this diagnostic bound is reached, and the sample sets an explicit
saturation flag rather than pretending that the oldest-age value is complete.

## Breadcrumb lifecycle

The presentation breadcrumb records these important states:

- `PREPARE_STARTED`, `PREPARED`, preparation failure/stale/cancellation;
- engine prepared-commit accepted or rejected;
- transition selected, started, completed, or cancelled;
- `RENDERED` terminal state.

Updates are app-private and contain no path, filename, host, account, or credential. Presentation
and session values are installation-specific diagnostic tokens. The current breadcrumb is flushed
synchronously at the one-minute process marker, severe memory callbacks, and Java crash capture.
On the following startup, `PREVIOUS_RUNTIME_BREADCRUMB` exposes whether the previous process ended
with an active stage.

## Interpretation

- `smbActiveStreams > 0` between transfers, increasing `smbStreamsOpened - smbStreamsClosed`, or a
  rising oldest-stream age points to an unclosed/stalled SMB read.
- `mediaActiveTransfers > 2` is an invariant violation; an oldest transfer above the two-minute
  deadline indicates cancellation/close failure.
- Rising `nativePssKb` with stable Dalvik PSS and stable stream/transfer/FD/thread counts moves the
  investigation toward graphics/decoder/Skia ownership.
- A rising FD/thread count or resource counter identifies a provider/transport lifecycle leak even
  when Java heap remains flat.
- Transition analyzer results are `NO DATA` when required fields are absent. New logs correlate one
  start and one terminal result per `(sessionId, hostGeneration, transitionGeneration)`.

## Verification completed

- Kotlin structure and whole-main-source syntax parsing.
- Pure production-logic checks, including resource leases, bounded counters, procfs sampling,
  breadcrumb recovery/privacy, and diagnostic field allowlists.
- Engine/persistence type check against offline Android/Room stubs.
- Diagnostics 26-hour complete-retention simulation and forced-rotation scenario.
- Transition synthetic endurance analysis with 1,000 correlated generations.
- The previous field log now reports missing transition fields as `NO DATA`, not a false pass.

## Remaining gate before device installation

Run the real Android toolchain in an environment with Gradle 8.9 and the Android SDK:

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest lintDebug assembleDebug
```

Resolve every named unit-test failure. Install with the same signing certificate as the existing
application. If that certificate is unavailable, export diagnostics/settings before uninstalling;
an uninstall changes application state and must not be presented as an in-place A/B upgrade.

After installation, perform a six-hour accelerated run before the 24-hour A/B gate. Confirm that
the new fields are present and that their counter invariants hold before using them to decide the
Phase 3 memory-policy changes.
