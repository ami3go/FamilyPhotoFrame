# Family Photo Frame v50.2 — decoded look-ahead playback

## Black-screen elimination

- The slideshow now keeps the currently rendered bitmap visible until the next photo is fully resolved and decoded.
- One next photo is proactively downloaded/cached and decoded at the physical display size during the current photo dwell period.
- Transitions consume an already decoded bitmap; no SMB, file-cache, or decoder work remains on the transition path.
- If a preload is late, the previous photo remains visible instead of exposing the black window background.
- If decoding fails, the previous photo remains visible while the engine skips the bad item.

## Timing

- The playback interval now starts only after the selected photo is visibly presented.
- The engine blocks automatic advancement while a selected photo is still preparing, but manual next/previous commands continue to work.

## Memory bound

- The UI retains only a small decoded buffer: visible, selected, next, plus one temporary outgoing transition entry.
- The existing 32 MiB Coil cache and memory-pressure trimming remain active.

## Version

- versionCode: 3
- versionName: 0.10.2-prerelease
