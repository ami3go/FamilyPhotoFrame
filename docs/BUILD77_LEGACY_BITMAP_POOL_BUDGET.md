# Build 77 legacy bitmap-pool budget

Build 76 completed 10.81 continuous hours on the V80 without a crash, ANR,
process restart, ownership imbalance, or unbounded native/resource growth. It
nevertheless failed both managed-memory gates:

- Java-heap floor growth was +5.47 MiB over six post-warm-up hours (limit
  5.00 MiB).
- Total PSS projected +19.32 MiB over 24 hours (limit below 10 MiB), while
  native PSS projected only +1.63 MiB.

The aggregate bitmap-pool evidence identifies the existing byte budget as the
binding constraint. At 10.81 hours the six-entry pool held only two buffers / 3.63
MiB, after 5,338 requests, 3,720 hits, 1,618 misses, and 1,612 evictions. The pool
therefore remained within its count limit but could not retain enough of the common
1-2 MiB buffers to bridge three-photo collage bursts. Only one decoded bitmap was
logically active at the final sample, and native PSS, ownership, FDs, and threads
remained bounded, excluding an application ownership leak as the reason to retain
the old four-MiB cap.

Build 77 changes only the pool byte budget from 4 MiB to 8 MiB. The six-entry cap,
best-fit reuse, demand-weighted admission, API-21..25 low-memory scope, image quality,
collage policy, display-list retirement boundary, maintenance-GC cadence, diagnostics,
and analyzer thresholds are unchanged. The additional storage is a bounded four-MiB
baseline trade for fewer fresh managed bitmap allocations; it does not hide growth
by weakening a validation limit.

Acceptance requires a fresh V80 session and unchanged Phase 4 thresholds. The pool
must remain at or below six buffers / eight MiB, reuse must improve without decoder
rejection or render disruption, Java-heap growth must remain below 5.00 MiB over six
post-warm-up hours, and total/native PSS projections must remain below 10 MiB/24h.
