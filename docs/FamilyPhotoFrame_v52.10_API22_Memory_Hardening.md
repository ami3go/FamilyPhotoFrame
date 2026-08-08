# FamilyPhotoFrame v52.10 API-22 memory hardening

Version: `0.12.8-prerelease` (`versionCode 28`).

## Reason for the increment

A field diagnostics bundle from an ONDA V80 PLUS running Android 5.1/API 22 with a
100 MiB Java heap showed a steadily rising post-GC heap followed by 93 recoverable bitmap
allocation failures and a fatal main-thread `OutOfMemoryError`. The Coil memory cache was
already empty, so another cache-size reduction could not address the retained allocations.

This increment mitigates the confirmed failure modes while keeping the currently visible
presentation stable.

## Implemented changes

- `ImageRequest` creation, model resolution, decode, transition-blur construction, web
  preview construction, and blurred-backdrop construction are all inside OOM boundaries.
- A process-wide `PlaybackMemoryGuard` applies hysteretic degradation:

  | State | Trigger | Preparation policy |
  |---|---|---|
  | Normal | Heap below 85% and no recovery window | Current + next preload; up to 3-photo collage |
  | Guarded | Heap at least 85% | No next preload; at most 2 photos; 80% decode target |
  | Critical | Heap at least 92% | Current photo only; 68% decode target; no blur/preview |
  | Circuit open | Decode/allocation OOM or severe system pressure | No selected decode for 1–5 minutes |
  | Recovery | Circuit closed but ten-minute recovery active | Current photo only; 70% target; simple transition |

- OOM cooldown doubles for repeated failures and remains open after its timer if heap is
  still at least 85%. A weaker Android memory callback cannot downgrade a stronger state.
- Heap exhaustion is treated as a process condition, not a bad-photo result. It does not
  suppress a photo, consume a shuffle candidate, or immediately advance into another decode.
- Compose snapshot state now stores only small `PreparedSlideHandle` values. Bitmap-bearing
  presentations live in an ordinary bounded registry, closing the old-snapshot retention
  path on legacy Compose/Android runtimes.
- Every temporary decoded bitmap starts as uncommitted ownership. Rejected orientation
  probes, unsuitable collage candidates, unchosen third tiles, cancelled preparations, and
  failed builds are retired explicitly.
- Never-rendered temporary software bitmaps are recycled immediately. Rendered bitmaps are
  removed from all strong owners first, then recycled after a one-second display-list grace
  period only on API 21–25. Newer Android versions remain GC-managed.
- The first OOM prunes preloads and obsolete presentations, waits for legacy retirement, and
  requests one background GC. There is no retry/GC loop.
- Under pressure, soft-focus transitions, blurred portrait backdrops, Ken-Burns transition
  complexity, and web preview generation are suppressed; transitions fall back to crossfade.
- Runtime samples and synchronous crash envelopes now include prepared/rendered slide counts,
  decoded/app bitmap counts, active decoded bytes, pending disposals, protection state,
  pressure percentage, and cumulative OOM count.

## Review findings fixed

The implementation review found and corrected:

1. A `RUNNING_LOW` callback could downgrade an active OOM circuit to guarded mode.
2. OOM decode failures were still being counted against otherwise healthy photos.
3. Cancellation while awaiting the engine commit could leave a newly inserted registry
   entry without an owning Compose handle.
4. Rejected landscape collage candidates and unused third tiles needed immediate ownership
   release rather than waiting for a later registry sweep.

## Deliberate boundary

The production app does not automatically write an HPROF heap dump. Creating one on a nearly
full 100 MiB heap can itself stall or kill the process, consumes substantial storage, and can
contain private photo paths or credentials. The new bounded inventory fields provide safe
field evidence. A manual debug-only heap capture is included in the device soak procedure.

This source remains prerelease until the Android build and API-22 device soak in
`FamilyPhotoFrame_v52.10_Release_Qualification.md` pass.
