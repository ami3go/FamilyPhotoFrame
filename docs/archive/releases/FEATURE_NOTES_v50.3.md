# Family Photo Frame v50.3 — portrait collage playback

## Added

- Portrait photos can be grouped with 1–2 compatible portrait photos from the exact same indexed folder.
- Automatic layout scoring selects two or three equal-width columns by estimated centre-crop loss.
- Android settings and the paired web panel expose collage mode, maximum photo count, single-photo fallback, and tile gap.
- Collages are fully resolved and decoded before presentation; the previous slide stays visible during SMB/cache/decode work.
- Every collage member is marked shown and consumed from shuffle/date queues so it is not replayed immediately.
- Candidate failures are logged and suppressed without advancing or blanking the current anchor photo.
- Diagnostics include COLLAGE_PRELOAD_STARTED, COLLAGE_READY, COLLAGE_DOWNGRADED, COLLAGE_FALLBACK_SINGLE, COLLAGE_CANDIDATE_FAILED, and COLLAGE_RENDERED.

## Playback defaults

- Mode: Automatic
- Maximum photos: 3
- Candidate scope: exact same source and normalized parent folder
- Single portrait fallback: blurred background
- Gap: small

## Version

- versionCode: 4
- versionName: 0.10.3-prerelease
