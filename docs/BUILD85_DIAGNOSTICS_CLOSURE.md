# Build 85: Phase 4 diagnostics closure

Build 85 preserves Build 82 playback, bitmap-arena, image-quality, motion, recovery,
transport, ownership, and memory policy. It changes only the evidence path that prevented a
valid Phase 4 window.

## Evidence-driven fixes

- Build 82 emitted 83,874 `FOLDER_SKIPPED` records, all for
  `source_retry_exhausted`. Those records consumed the standard diagnostics retention budget,
  rotated away one-minute memory samples, and made a later controller transition appear to be a
  duplicate because the intervening transition was missing.
- Repeated folder skips now retain the first three records and emit one exact count summary per
  active minute. The aggregation map remains bounded and expires inactive reasons.
- Legacy raw shuffle-scope fields are normalized to installation-specific `scopeToken` values at
  the diagnostics boundary. This preserves correlation without leaking scope contents or
  incrementing the rejected-field health counter.
- Privacy-safe field tokenization is counted separately from true catalog rejection. A
  transformed field remains available as evidence; only rejected keys and removed variable
  messages increment `fieldsDropped` and fail diagnostics integrity.
- The exact 15 startup rejections observed on Build 84 are now registered bounded engine
  fields: prepared-photo counts, selection target/result, and shuffle cycle/reservation counts.
- Debug builds expose the application's real streamed diagnostics bundle to the Android shell
  under the privileged `android.permission.DUMP` permission. Release APKs do not contain this
  provider, and the paired web API remains unchanged.
- The host snapshot merger deduplicates standard and slide streams separately, orders the merged
  session chronologically, and uses only a verified on-device envelope and terminal `bundleEnd`.
  It never invents writer health.

## Qualification contract

The revised dedicated-frame memory limits remain unchanged: Java-heap six-hour growth must be
below 10% of maximum heap, and total/native PSS robust edge or projected 24-hour growth must be
below 20 MiB. All crash, continuity, controller, ownership, resource, timeout, cancellation,
GC/restart-rate, and scenario gates remain unchanged.

Build 82 through Build 84 evidence remain preserved but cannot close Build 85. A fresh Build 85
window must be collected hourly so both high-volume slide evidence and low-volume standard
evidence survive rotation. An ESD-correlated external interruption reported by the operator
ended the last Build 82 process and is retained as a continuity break, not relabeled as an
application exception.
