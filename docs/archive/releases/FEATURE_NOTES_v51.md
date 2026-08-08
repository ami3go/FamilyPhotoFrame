# FamilyPhotoFrame v51.0 / 0.11.0-prerelease — Feature Notes

## Phase 1 — Data and domain foundations

- Persistent named playlists and built-in All photos, Favorites, and Recent uploads playlists.
- Local-time playlist schedule rules, priorities, date ranges, and manual overrides.
- Brightness automation and night-action settings.
- Web-upload policy and app-managed Local uploads source.
- Transactional batch favorite/hide operations using the existing Room curation columns.
- No destructive database migration; schema remains v5.

Review corrections:

- Reused existing favorite/hidden Room storage instead of introducing duplicate tables.
- Preserved curation through normal scan reconciliation.
- Used typed DataStore defaults so older settings files migrate non-destructively.

## Phase 2 — Touch controls, Favorites, and Hide

- Temporary Previous, Favorite, Hide, and Next overlay.
- Four-second inactivity timeout.
- Eight-second Undo for soft hiding.
- Explicit collage-member selector for favorite/hide actions.
- Keyboard and D-pad support.

Review corrections:

- Added an interaction-only dwell hold instead of changing the visible playback state to Paused.
- Bound curation to the committed prepared presentation.
- Kept the current presentation visible until a replacement was fully prepared.

## Phase 3 — Playlists and scheduled switching

- Playlist create, rename, duplicate, enable/disable, reorder, default, delete, and Play now.
- Playlist-specific source/folder, Favorites, selection, timing, transition, and collage overrides.
- Scheduled switching with weekdays, overnight ranges, priorities, optional date ranges, and rule enable/disable.
- Manual override until the next schedule boundary or until cancelled.
- Local uploads included in merged source planning.

Review corrections:

- Added Local uploads to the complete source pool rather than only configured remote slots.
- Made portrait-collage candidate filtering use the effective active playlist.
- Included playlist enable and date-range fields in schedule-change detection.

## Phase 4 — Brightness, night mode, and health

- Manual, scheduled, ambient, and scheduled-plus-ambient window brightness.
- Dim, pause slideshow, or black-screen night actions.
- Temporary touch wake.
- Android and web health views covering photos, curation, uploads, storage, source state, playlist state, and warnings.
- Redacted support-report export entry point.

Review corrections:

- Merged ambient-sensor lifecycle handling into the existing immersive-mode lifecycle.
- Retained legacy quiet-hours behaviour when brightness automation remains Manual.
- Kept the black-screen wake surface touchable.

## Phase 5 — Bulk upload and hardening

- Multi-file browser picker and drag-and-drop.
- Two-worker bounded queue with per-file progress.
- Pause pending work, resume, cancel, retry failures, and clear completed queue.
- One file per authenticated streaming request; no whole-file byte array and no multipart parser.
- App-private staging, SHA-256 duplicate detection, magic-byte and dimension checks, atomic commit, and replacement rollback.
- Storage preflight, 250 MiB reserve, bounded sessions, per-browser ownership, expiry, and one indexing worker.
- JPEG, PNG, and WebP support; HEIC/HEIF rejected on the Android 5/6 baseline.

Review corrections:

- Bound sessions to the browser session that created them.
- Made rejected/cancelled file slots terminal so a batch can finish cleanly.
- Excluded completed sessions from active-session capacity.
- Added atomic file-slot reservation and overflow-safe byte accounting.
- Connected all rendered queue controls and cancellation during session creation.

## Version

```text
versionCode 14
versionName 0.11.0-prerelease
minSdk 21
compileSdk 35
targetSdk 35
```
