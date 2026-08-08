# v52.9 Phase 3 review — crash, ANR, and process-exit evidence

## Implemented behavior

- Uncaught exceptions synchronously write a fixed-schema, checksum-protected envelope
  before the platform/default handler is invoked. The record is capped at 16 KiB and
  excludes exception messages and arbitrary serialization.
- The envelope contains bounded class/method/file/line frames, current session/sequence,
  sampled memory, playback state, current presentation/source/layout/transition tokens,
  active operation, queue depth, drop count, and sink status.
- Startup recovers crash or unrecovered-stall evidence on a background thread. Evidence
  is removed only after the recovery event reaches a healthy durable sink, and a compare
  check prevents recovery from clearing a newer crash.
- A two-second main-thread heartbeat records a five-second warning, ten-second
  escalation, recovery, debugger suppression, and at-most-once-per-minute stack capture.
  Envelope I/O and flushes run on the watchdog thread, never on the main thread.
- API 30 and newer read the newest platform process-exit record, map reason and
  description to fixed safe codes, include PSS/RSS and importance, and commit a
  fingerprint only after the event is flushed. API 21–29 do not guess an exit reason.
- Crash/exit preferences are excluded from cloud backup and device transfer.

## Review findings corrected in this phase

1. Recovery initially cleared an envelope unconditionally after flush, which could erase
   a newer watchdog/crash write. The store now compares an internal checksum under one
   lock before clearing.
2. Startup recovery originally waited for disk flush on the main thread. Recovery,
   process-exit inspection, legacy migration, and their flushes now run in the
   application background scope.
3. A recovered ANR envelope was initially cleared as soon as the heartbeat returned.
   It is now cleared only after `MAIN_THREAD_STALL_RECOVERED` is durably flushed.
4. Early crashes could precede the first one-minute runtime sample. Application startup
   now seeds a safe heap/native/PSS snapshot before installing the crash handler.
5. Debugger pauses could leave false ANR evidence. Entering debugger suppression now
   resets the detector and removes only an envelope belonging to the current session.
6. Focused tests now prove synchronous crash evidence remains writable while the async
   queue is full and while the normal sink is throwing.

## Verification evidence

- Pure JVM crash-store round trips, checksum corruption, nested causes, stack bounds,
  newer-evidence preservation, queue saturation, and failing-sink tests
- Fake-clock stall start, escalation, recovery, cooldown, and debugger tests
- Stable process-exit reason/description mapping tests
- API-guard/static crash-path contract verifier
- Full Kotlin parse (191 files at this phase), engine type check, Room SQL/migrations,
  all Python contracts, transition simulation, and 1,425-presentation shuffle run

## Qualification still required

The debug crash/stall device scenarios, API-30 `ApplicationExitInfo` cases, Android 5.1
watchdog behavior, Gradle unit tests, lint, and APK assembly remain final release gates.
