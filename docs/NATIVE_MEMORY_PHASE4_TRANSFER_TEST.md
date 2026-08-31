# Phase 4 selected-transfer test

Build `26.56.1` adds a bounded cache-transfer path for the currently selected
presentation. It does not change the native-memory allocation strategy; the Phase 2B
HIL matrix is still required to identify that owner.

## Device run

1. Install build `26.56.1` / version code `56`.
2. Use normal playback with the real SMB source for 2–4 hours.
3. Export diagnostics after the run. If a selected cache transfer stalls, keep the
   device running long enough to observe the next presentation recover.

## Expected behavior

A selected cache operation shares one 58-second budget from presentation selection,
including any cache-key or transfer-slot wait. Background preload retains the existing
two-minute budget. The selected deadline is intentionally two seconds earlier than the
60-second preparation watchdog, so the current slide advances without waiting for the
broader 70-second render-ack fallback.

Routine progress is kept only in the bounded in-memory presentation trace. It will not
rotate the bulk diagnostic log.

## What to inspect if a selected transfer expires

`PREPARATION_CANCELLED_RECOVERED` should include these fields:

| Field | Meaning |
|---|---|
| `preparationSubstage` | Last blocking boundary, such as `MEDIA_CACHE_TRANSFER_COPY` or `MEDIA_CACHE_TRANSFER_SLOT_WAIT`. |
| `mediaTransferState` | `SELECTED_DEADLINE` for the selected-transfer budget. |
| `mediaTransferCopiedBytes` / `mediaTransferExpectedBytes` | Bytes observed before cancellation. |
| `mediaTransferProgressAgeMs` | Time since the last accepted transfer progress update. |
| `mediaTransferStreamCloseRequested` | Whether cancellation asked the active source stream to close. |
| `mediaTransferStreamCloseSucceeded` | Result of that close request, or `UNKNOWN` when no stream was open. |
| `mediaTransferSlotReleased` | Whether the cache transfer slot was released before recovery. |

Healthy recovery has a `SLIDE_SELECTED` event immediately after the cancellation record,
with no `RENDER_ACK_TIMEOUT`. A timeout during `MEDIA_CACHE_TRANSFER_COPY` with zero
or stale progress supports an I/O-stall diagnosis; continuously increasing bytes points
instead to a slow source or an overly small selected deadline.

## First hardware cycle finding

The first `26.52.1` V80 run exposed a same-photo retry gap after a selected transfer reached
its deadline. The engine selected the same anchor again, but Compose keyed preparation only by
photo id, so the replacement attempt produced no new preparation effect. That caused repeated
`RENDER_ACK_TIMEOUT` records with `PREPARATION_NOT_STARTED` until another state change happened.

Build `26.53.1` publishes a monotonically increasing selection generation to the UI, keys both
selected preparation and its watchdog by that generation, and rejects stage, transfer,
cancellation, and watchdog callbacks from an older attempt. The new hardware cycle must show that
a selected deadline is followed by a real new attempt or a different selected photo, without a
same-token `PREPARATION_NOT_STARTED` timeout loop.

## Second hardware cycle finding

The three-hour `26.53.1` V80 run remained crash-free and eliminated the unobservable
acknowledgement timeout, but exposed a second recovery gap. One 11.6 MB SMB photo exceeded the
18-second selected-transfer budget and was immediately reserved again 45 times in about 14
minutes. Every attempt continued making progress, released its transfer slot, and recovered, but
the repeated same-anchor reservation stalled slideshow progress and created avoidable transfer
and native-memory pressure.

Build `26.54.1` treats `SELECTED_DEADLINE` as an anchor failure in the active folder-balanced
shuffle cycle. The failed anchor is removed from that cycle before advancing, while ordinary UI
preparation cancellation still releases its reservation without penalizing the photo. The reset
hardware cycle must show a different photo after a selected deadline and must not reproduce a
same-anchor deadline retry storm.

## Third hardware cycle finding

The supplementary Android 6 phone run exposed a systematic remote-copy throughput failure that
also appeared on the V80 when source throughput degraded. The app was reachable, indexing
completed, and transfer progress remained fresh, but Kotlin's generic 8 KiB copy buffer caused
one bounded jCIFS read for every small chunk. The phone copied only about 0.4–0.7 MB during each
18-second selection budget: 80 of 86 preparations expired and no slide rendered.

Build `26.55.1` used a fixed 64 KiB buffer for app-owned remote cache copies. That matched the
practical SMB2 read size and raised the phone's copied bytes to about 1.04 MB per selected window,
but the unchanged 18-second budget still could not finish the smallest observed 2.5 MB file.

Build `26.56.1` therefore keeps the 64 KiB buffer and gives a selected transfer 58 seconds, with
the preparation watchdog at 60 seconds and the final render-ack fallback at 70 seconds. All three
bounds remain below the existing two-minute background ceiling. Validation must show successful
rendering on both devices without overdue streams, timeout loops, or unbounded resource growth.
