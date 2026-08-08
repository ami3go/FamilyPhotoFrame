# Folder-Balanced Shuffle v52.2 — Device Acceptance Gate

Use this procedure after the offline gate passes. Record device model, Android version, source types, app version, database version, and diagnostics bundle name.

## 1. Build and automated Android tests

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest lintDebug assembleDebug
./gradlew --no-configuration-cache connectedDebugAndroidTest
./gradlew --no-configuration-cache installDebug
```

Required: all tasks pass. The debug-signing warning is acceptable for local testing. Unstripped third-party `.so` warnings are informational unless packaging fails.

## 2. Core fairness

Use at least 20 eligible folders with uneven sizes, including one-photo and 100+ photo folders.

- Run two complete folder cycles.
- Confirm every available folder resolves exactly once per cycle.
- Confirm no folder repeats before all other current-cycle folders are presented or explicitly skipped.
- Confirm the first folder of a new cycle differs from the previous cycle's last presented folder when more than one folder exists.
- Complete at least two photo cycles in three representative folders and verify no in-cycle photo repeat.

## 3. Duplicate identity

Place two byte-identical files with different filenames in one direct directory, and one identical copy in another directory.

- Wait for background content hashing/reconciliation.
- Confirm the same-folder copies represent one shuffle member.
- Confirm the copy in the other folder remains eligible independently.
- Confirm no queue reset or replay occurs when fallback identity upgrades to SHA-256.

## 4. Manual folder actions

- Use **Preview this folder once** from Android and Web Control.
- Confirm the preview appears, then normal shuffle resumes without consuming a folder turn.
- Use **Use this folder in playlist**.
- Confirm exact source/directory eligibility changes and a new eligibility-revision scope activates.
- Confirm equal folder names on different sources are not merged.

## 5. History and restart boundaries

- Navigate Previous several times, then Next through forward history.
- Confirm queues do not rewind and no new reservation occurs until newest history is reached.
- Force-stop with an active reservation and restart; confirm it is released safely.
- Force-stop immediately after a commit and before expected display; confirm the committed presentation is not repeated.
- Reboot and confirm active scope, cycles, and history survive.

## 6. Collage behavior

- Test 1, 2, and 3+ compatible portrait members.
- Confirm lookahead never exceeds 12 pending members.
- Confirm collage members come from one folder and one photo cycle.
- Confirm a failed secondary tile yields a smaller valid presentation.
- Confirm a failed anchor retries another photo in the same folder turn.

## 7. Dynamic reconciliation

During playback:

- add and remove folders;
- add, delete, Hide, Undo, Favorite, and un-Favorite photos;
- repeat the same rescan twice;
- switch playlists and scheduled playlists;
- reset active scope, all scopes, and history.

Required: no duplicate insertion, no invalid reservation, no current-image loss during reset, and independent scope/history restoration.

## 8. Source outage and folder failure

With local plus SMB/WebDAV/Synology sources active:

- disconnect one network source;
- confirm one source-level deferral and no per-folder retry storm;
- confirm healthy-source folders continue;
- observe 30 s, 2 min, 5 min, and capped 15 min backoff as applicable;
- reconnect and confirm pending folders return without unrelated scope reset;
- inject a folder-local error while the source remains healthy;
- confirm at most two retries followed by one explicit `SKIPPED` entry.

## 9. Performance and memory

- Confirm `SHUFFLE_SELECTION_TIMING` diagnostics normally remain below 50 ms for cached/local selection and below 250 ms for network metadata selection, excluding media download.
- Run at least six hours with Web Control preview enabled and disabled in separate sessions.
- Run a 24-hour dynamic endurance session with periodic rescans, Previous/Next, source outage/recovery, and at least one reboot.
- Confirm no OOM, no stalled slideshow while a healthy folder exists, bounded Room rows/history, and no steadily rising post-GC heap floor.

## 10. Release evidence

Export the diagnostics bundle and record:

- folder-cycle uniqueness;
- photo-cycle uniqueness;
- explicit skips and reasons;
- source defer/backoff/recovery events;
- reservation recovery;
- selection latency percentiles;
- database row counts;
- peak and post-GC heap;
- Android unit/instrumentation/lint results.

Release only when all applicable checks pass on API 21 or the oldest supported production device and on the primary deployment tablet.
