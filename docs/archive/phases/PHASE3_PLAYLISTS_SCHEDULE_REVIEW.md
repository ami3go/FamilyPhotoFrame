# Phase 3 Review — Playlists and Scheduled Switching

## Delivered
- Built-in All photos, Favorites, and Recent uploads playlists.
- User playlist creation/deletion and activation.
- Playlist-specific source, folder, favorite, interval, transition, collage and selection overrides.
- Local uploads source merged into the playback pool.
- Local-time scheduled switching, overnight rules, priority resolution, and manual overrides.
- Android Playback and Schedule controls.

## Review corrections
1. The first pool plan ignored `local_uploads` because it planned from configured source slots only. It now plans from the complete merged pool.
2. Portrait collage candidate selection used the raw global `favoritesOnly` setting. It now uses the effective active-playlist state.
3. Playlist changes trigger source reconfiguration only when source filters change; timing and visual overrides apply without replacing the current slide.
4. Scheduling uses bounded minute-based wakeups and API-21-safe `Calendar` logic rather than second polling.

## Gate result
PASS subject to full Android Gradle compilation on the development workstation.
