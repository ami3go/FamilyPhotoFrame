# Build 75 legacy bitmap-pool contingency

Status: activated after build 74 completed 13.85 continuous hours and failed the
authoritative total-PSS gate at a 20.09 MiB/24 h projection. Build 74 passed Java heap
narrowly at +4.93 MiB/6 h and passed native/resource/ownership gates, isolating the
remaining target to managed bitmap reuse and Dalvik committed high-water. Build 75.1
proved the adaptive runtime path but encoded its aggregate buckets with a delimiter that
the privacy boundary intentionally transformed; its brief window is not qualification
evidence. The corrected, install-distinct validation candidate is build 26.76.1.

## Why this note exists

Build 73 introduced a bounded pool for fully retired, mutable bitmap storage on API
21-25 low-memory devices. Build 74 kept the six-buffer/four-MiB limits and changed pool
trimming from FIFO eviction to removal of the smallest buffer dominated by another
same-config buffer.

The build-74 change is effective after warm-up. At comparable 3.53-hour checkpoints:

| Metric | Build 73 | Build 74 |
|---|---:|---:|
| Selections | 640 | 649 |
| Rendered singles | 331 | 369 |
| Rendered collages | 309 | 279 |
| Pool hits | 716 | 894 |
| Pool misses | 670 | 453 |
| Pool evictions | 664 | 447 |
| Cumulative hit rate | 51.7% | 66.4% |

The stronger signal is the interval from approximately 2.1 to 3.5 hours, after the pool
had learned the workload. Build 73 produced 372 hits and 412 misses (47.4% hit rate),
while build 74 produced 639 hits and 114 misses (84.9% hit rate). Build 74 therefore
reduced interval misses and evictions by about 72% without increasing the hard pool
limits.

Build 74 was also stable at that checkpoint: one continuous process/session, no crash,
ANR, escalated main-thread stall, render timeout, selected deadline, ownership failure,
or malformed transition generation. Five maintenance collections completed within the
existing rate limit and reclaimed about 58 MiB cumulatively without render disruption.

These results mean build 75 must not replace build 74 merely to chase a higher hit rate.
The remaining release question is whether build 74 passes the full post-warm-up Java
heap and total/native PSS gates.

## Activation gate

Proceed with this plan only when at least one of the following is true:

1. Build 74 completes a valid six-hour post-warm-up window and fails Java heap growth
   (limit below 5.00 MiB) or total/native PSS projection (limit below 10 MiB/24 h), with
   the failure correlating with continued pool misses or bitmap allocation churn.
2. A later matched checkpoint shows build-74 rolling reuse materially below build 73,
   after controlling for selections, rendered photo members, and collage mix.
3. The pool violates its six-buffer/four-MiB bounds, triggers decoder reuse failures, or
   causes a crash, timeout, ownership imbalance, or render-quality regression.

Do not activate build 75 for a short-window slope alone. In particular, a provisional
native-PSS projection is insufficient when the absolute native PSS remains comparable
to the matched build-73 checkpoint.

## Missing evidence

Current telemetry exposes aggregate hits, misses, offers, evictions, count, and bytes.
It does not reveal which allocation-size/config classes generate misses, whether an
offered buffer improves future request coverage, or how often a vendor decoder rejects
an otherwise compatible `inBitmap` candidate. Another eviction heuristic without this
information would be speculative.

## Bounded telemetry

Add fixed-size counters to `LegacyBitmapReusePool`; never log one event per decode.

- Divide reusable allocations into eight fixed size classes up to the existing four-MiB
  ceiling, separately for RGB_565 and ARGB_8888.
- Count requests, hits, misses, accepted offers, rejected offers, and evictions per
  class/config.
- Count decoder `IllegalArgumentException` retries after an `inBitmap` attempt.
- Track the minimum and maximum retained allocation size and the number of distinct
  occupied classes.
- Publish counters only in the existing periodic `HEAP_SAMPLE` event. Use primitive
  arrays internally and compact serialized fields at sample time so the decode path
  creates no diagnostic event or collection.
- Keep counters saturating or periodically halve them to prevent overflow and to let
  recent workload dominate old history.

The diagnostic schema must remain bounded and privacy-safe: no photo identity, filename,
source location, dimensions, or path is recorded.

## Adaptive policy

Build 75 retains best-fit selection in `take()`. It replaces only the trim/admission
decision, and only after 128 requests have populated the bounded histogram.

1. Form the candidate set from the currently retained buffers plus the newly offered
   buffer. At most seven entries are considered.
2. For each config and request-size class, use the recent request histogram as demand.
3. Score each valid subset that satisfies both existing caps. A buffer contributes when
   its allocation can satisfy a demanded class. Apply diminishing benefit to duplicate
   coverage so the pool retains useful depth without filling entirely with the largest
   allocation.
4. Prefer the subset with the greatest demand coverage. Break ties by fewer retained
   bytes, then deterministic age/order.
5. Until a minimum request count is available, retain the build-74 dominated-smallest
   policy as the fallback.
6. Recycle every buffer excluded from the selected subset exactly once and preserve all
   current ownership checks.

Because the candidate set is at most seven buffers, an exhaustive subset evaluation is
bounded to 128 combinations per offer. It runs on the existing off-main decode/retirement
path. If profiling shows that cost is material, use an equivalent bounded greedy
marginal-utility selection.

## Explicit non-goals

- Do not raise the six-buffer or four-MiB pool limits. More retained storage could hide
  misses while worsening the PSS gate.
- Do not reduce image quality, target dimensions, collage frequency, or transition
  behavior.
- Do not recycle a bitmap before the existing display-list grace and ownership boundary.
- Do not weaken maintenance-GC spacing, analyzer thresholds, diagnostics, or recovery.
- Do not add per-photo or per-decode persistent logging.

## Offline verification before installation

Add deterministic trace-replay tests covering:

- the observed single/collage workload mix;
- narrow repeated sizes, alternating small/large sizes, and multiple configs;
- a working set larger than four MiB;
- transient outlier sizes that must not displace high-demand classes;
- decoder rejection and candidate return;
- exact count/byte caps and single-recycle ownership;
- fallback behavior before the demand histogram is warm;
- counter saturation/decay without allocation on `take()`.

For every trace, compare FIFO, build-74 dominated-smallest, and the candidate adaptive
policy. The adaptive policy is eligible only if it never exceeds the caps and does not
regress build-74 misses or evictions by more than 5% on any representative trace. It
should improve the failing V80-derived trace materially, not merely synthetic cases.

Run the complete debug unit suite, `assembleDebug`, diff checks, and all Phase 4/Phase 5
source-contract verifiers before installation.

## Device acceptance

Start a genuinely fresh V80 session and preserve all earlier builds separately. Keep the
existing thresholds unchanged.

- Pool count <= 6 and bytes <= 4 MiB.
- Rolling post-warm-up hit rate should remain at least as good as build 74 under a matched
  workload; misses and evictions must not regress materially.
- Java-heap six-hour robust growth < 5.00 MiB.
- Total- and native-PSS 24-hour projections < 10 MiB.
- Maintenance-GC requests remain at least ten minutes apart.
- FDs, threads, prepared/rendered bitmaps, HWUI, SMB streams, media transfers, and source
  ownership remain bounded.
- No crash, ANR, escalated main-thread stall, render/preparation timeout, selected
  deadline, priority violation, static-collage panel motion, or process discontinuity.
- Preserve recurring collages, genuine three-photo motion, image quality, partial resume,
  cache-only slow-link behavior, continuous cached rendering, deferred-item completion,
  NAS recovery, activity-stop fencing, and durable `bundleEnd` evidence.

## Decision rule

If build 74 passes the authoritative memory and resource gates, close the pool work and
leave this document as a contingency. If build 74 fails, use the bounded telemetry and
trace-replay criteria above to justify build 75; do not tune the policy solely from a
single instantaneous PSS value.
