# Phase 4 selected-transfer test

Build `26.57.1` adds a bounded cache-transfer path for the currently selected
presentation. It does not change the native-memory allocation strategy; the Phase 2B
HIL matrix is still required to identify that owner.

## Device run

1. Install build `26.57.1` / version code `57`.
2. Use normal playback with the real SMB source for 2–4 hours.
3. Export diagnostics after the run. If a selected cache transfer stalls, keep the
   device running long enough to observe the next presentation recover.

## Expected behavior

A selected cache operation shares one 58-second budget from presentation selection,
including any cache-key or transfer-slot wait. Background preload retains the existing
two-minute budget. A freshly transferred selected anchor is rendered as a single frame rather
than spending its remaining budget on optional collage probes and companion downloads. The
70-second preparation watchdog leaves 12 seconds for anchor decode and transition after the
transfer ceiling, and remains below the broader 80-second render-ack fallback.

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

## Fourth hardware cycle finding

The first `26.56.1` phone run completed a 3,344,324-byte selected transfer, but only 2.5 seconds
remained when optional collage candidate lookup began. The 60-second preparation watchdog then
advanced the selection. A cached retry eventually rendered, but it took another 43 seconds while
collage probes and companion work ran, reproducing a visible blank/retry cycle.

Build `26.57.1` renders a freshly transferred selected anchor as an immediate single-photo frame.
Cached and background-preloaded anchors may still build collages. The selected transfer remains
bounded at 58 seconds, while the preparation and final render-ack guards move to 70 and 80 seconds
to leave a measured 12-second decode/transition margin. Validation must show that a successful
fresh transfer reaches `SLIDE_RENDERED` without an intervening preparation watchdog.

## Twelfth-hour build-60 finding

The V80 build `26.60.1` Normal run rendered continuously for 12.06 hours without a crash, ANR,
render-ack timeout, overdue resource, FD/thread growth, or ownership imbalance. Native PSS was
flat for the first 9.8 hours. When source throughput degraded, selected transfers began reaching
their 58-second deadline while a low-priority `CONTENT_HASH` stream remained active. Samples
repeatedly showed one media transfer and two or three SMB streams, with content hashing holding
the oldest stream for up to about 59 seconds. Native PSS then stepped upward by about 6.9 MiB;
some allocator pages were reclaimed after source recovery, but the accelerated projection still
failed. The same contention explains the supplementary phone's zero-render run: selected media
and hash traffic shared an already slow SMB path.

Build `26.61.1` makes content hashing cooperatively preemptible. Before each 64 KiB read it checks
whether a media cache transfer is active; if so, it closes the hash stream, waits without marking
the photo as failed, and retries that row after selected media becomes idle. A cumulative
`contentHashYieldsToMediaTransfers` counter provides privacy-safe hardware evidence. The 64 KiB
buffer remains unchanged: the problem was concurrent low-priority traffic under degraded source
throughput, not insufficient application read size.
