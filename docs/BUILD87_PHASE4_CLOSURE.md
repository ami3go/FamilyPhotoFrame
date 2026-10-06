# Build 87: Phase 4 closure candidate

Build 87 contains the evidence-driven managed-memory and diagnostics changes from Build 86 and
registers the final diagnostic field identified by Build 86's clean hardware startup.

The V80 bundle attributed all three startup rejections to
`COLLAGE_SELECTION_EVALUATED:collageFillWithOtherOrientations`. This is a bounded boolean
optimizer decision already emitted by the collage-selection path; it is now part of the engine
catalog and is covered by the catalog unit test. No privacy rule or rejection behavior changed.

The memory fix remains narrowly scoped: on API 21-25 low-memory devices, legacy bitmap heap
maintenance may cross a `NATIVE_PSS_GROWTH` critical latch only below 70% Java-heap occupancy,
with zero OOMs, bounded live bitmap ownership, no pending disposal, no active media transfer,
the existing growth/churn evidence, and the existing rate limit. Other critical causes remain
blocked.

All Gate 4 thresholds and scenario requirements remain unchanged. Build 86 was an attributable
diagnostic window and cannot close Build 87; Build 87 requires a fresh V80-only session with
zero rejected fields and the full post-warmup memory window.
