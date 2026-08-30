# Phase 4 selected-transfer test

Build `26.52.1` adds a bounded cache-transfer path for the currently selected
presentation. It does not change the native-memory allocation strategy; the Phase 2B
HIL matrix is still required to identify that owner.

## Device run

1. Install build `26.52.1` / version code `52`.
2. Use normal playback with the real SMB source for 2–4 hours.
3. Export diagnostics after the run. If a selected cache transfer stalls, keep the
   device running long enough to observe the next presentation recover.

## Expected behavior

A selected cache operation shares one 18-second budget from presentation selection,
including any cache-key or transfer-slot wait. Background preload retains the existing
two-minute budget. The selected deadline is intentionally two seconds earlier than the
20-second preparation watchdog, so the current slide advances without waiting for the
broader 30-second render-ack fallback.

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
