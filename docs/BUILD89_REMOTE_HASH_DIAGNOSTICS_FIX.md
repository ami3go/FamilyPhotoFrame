# Build 26.89.1: remote hash and diagnostics retention correction

## Evidence and root cause

Build 88's fresh NAS run stayed functionally stable, but total PSS projected
35.72 MiB/24 h against the 20 MiB limit. Native PSS, bitmap ownership, SQLite,
activities, views, file descriptors, and threads remained bounded. The dominant
growth was Dalvik PSS. Telemetry also showed that display-time content hashing
opened the remote source while `MediaCache` was already downloading the same
photo. This contradicted the Build 62 contract that remote identities are derived
from verified app-owned cache files.

The same run exposed two diagnostics defects: the emitted
`REMOTE_CONTENT_HASH_DEFERRED_TO_CACHE` event was not registered, and a harmless
file rotation was reported as partial retention before any evidence was deleted.

## Changes

- Remote photos are hashed only from verified local cache files after a cache
  commit or hit. Hash work is single-flight per stable photo identity and never
  reopens NAS/WebDAV/Synology content.
- Persisted hash updates are exposed as a bounded flow. The slideshow reconciles
  shuffle identity after 32 updates or five minutes, whichever comes first.
- Local-source display-time hashing is unchanged.
- `PHOTO_IDENTITY_RECONCILED` is recorded only when the persisted queue identity
  or ordering actually changes.
- The remote-deferred event is registered in the diagnostic catalog.
- Diagnostics now distinguish file rotation from actual oldest-generation
  eviction. Multiple retained files remain `COMPLETE`; only an eviction makes
  evidence `PARTIAL_RETENTION`.
- Partial evidence yields `NO DATA`, not a measured runtime failure. Required
  `NO DATA` gates still prevent Phase 4 closure.

## Why this should work

The removed path was the only identified duplicate full remote read correlated
with Build 88's high SMB stream, transfer, hashing-yield, and Dalvik allocation
activity. Reusing the verified cache file removes that network/buffer lifetime
without changing image quality, collage frequency, caching, or ownership rules.
Batching limits database/shuffle churn while preserving eventual content-identity
deduplication.

## Validation contract

Build 89 must pass the unchanged accelerated Phase 4 thresholds on a fresh V80
session: Java heap below 10 MiB/6 h, total PSS below 20 MiB/24 h, bounded native
PSS/resources/ownership, no static-collage panel motion, and no crash, ANR,
escalated stall, timeout, or source-priority violation. NAS recovery,
activity-stop fencing, and a durable `bundleEnd` remain required for closure.
