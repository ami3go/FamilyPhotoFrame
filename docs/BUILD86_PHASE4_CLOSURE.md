# Build 86: Phase 4 closure candidate

Build 86 preserves Build 85 playback, image quality, collage frequency, bitmap arena,
transport, recovery, ownership, and validation limits. It changes the one memory-policy
interaction and the diagnostic catalog defects demonstrated by the Build 85 V80 window.

## Evidence and fix

- The post-warmup total-PSS slope was about +1,013 KiB/hour. Dalvik PSS contributed
  about +946 KiB/hour, other PSS about +81 KiB/hour, and native PSS was flat.
- Java heap still passed its six-hour limit, ownership was balanced, and the fixed bitmap arena
  reused four allocated slots while more than 5 GiB of logical decoded traffic retired.
- The legacy bitmap-maintenance predicates had sufficient churn and heap-floor growth, but no
  request ran because an early `NATIVE_PSS_GROWTH` latch held the controller at `CRITICAL` after
  native PSS had stabilized.
- Maintenance may now cross only that native-growth critical latch, only below 70% Java-heap
  occupancy, with zero OOMs, safe bitmap ownership, no pending disposal, no active media
  transfer, the existing three-sample growth requirement, and the existing twenty-minute rate
  limit. Every other critical source remains ineligible.

## Diagnostic integrity

- Boot autostart now uses the catalogued `deviceModel` field.
- Shuffle retry counts and the two bounded boot-network health events are explicitly catalogued.
- Rejected diagnostics retain a bounded code-and-key-only summary in bundle health. It cannot
  contain values, paths, credentials, source locations, or unbounded signatures. This makes any
  remaining catalog defect immediately attributable without relaxing rejection.

## Qualification

All limits remain unchanged: Java-heap six-hour growth below 10 MiB, total/native PSS robust
edge or projected 24-hour growth below 20 MiB, at most four controller changes per hour, and at
least ten minutes between GC requests. Crash, continuity, ownership, bitmap, timeout,
cancellation, resource, NAS recovery, lifecycle fencing, and durable-bundle requirements are
unchanged. Build 85 evidence remains preserved but cannot close Build 86.
