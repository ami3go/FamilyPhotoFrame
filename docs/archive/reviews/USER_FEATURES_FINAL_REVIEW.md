# FamilyPhotoFrame v0.11.0 — Final Five-Phase Review

**Baseline:** v50.6.5  
**Resulting version:** v51.0 / `0.11.0-prerelease`  
**Minimum Android:** API 21  
**Verdict:** Source-level implementation complete; hardware acceptance still required.

## Phase gates

| Phase | Scope | Review result |
|---|---|---|
| 1 | Data/domain foundations | PASS |
| 2 | Touch controls, Favorites, Hide, Undo | PASS |
| 3 | Playlists and scheduled switching | PASS |
| 4 | Brightness/night mode and health | PASS |
| 5 | Bulk upload and hardening | PASS |

## Final connected-issue review

- Curation uses the committed presentation and persists through rescans.
- Showing touch controls pauses only the dwell timer, not the visible playback state.
- Playlist filters influence single-photo and collage selection consistently.
- Local uploads are part of source planning and are indexed only after atomic commit.
- Brightness sensor lifecycle coexists with immersive fullscreen recovery.
- Upload work is bounded to two streams and one reconciliation indexer.
- Upload sessions are authenticated, CSRF-protected, browser-owned, bounded, and expiring.
- Browser queue controls are connected to the rendered markup and remain mobile usable.
- Existing web asset cache-busting remains at revision `v5110`.

## Automated/offline evidence

Passed:

- Phase 1–5 feature contract scripts.
- Project consistency checks.
- 51 Room SQL query preparations.
- Room migration replay from v1 through v5.
- Embedded web JavaScript parsing; payload approximately 76 KiB.
- Web UI modernisation contract.
- Transition Phase 1–5 checks and synthetic 1,000-transition diagnostics.
- Fullscreen/Settings-back contract.
- Engine and persistence type-check.
- Targeted `WebUploadManager` Kotlin type compilation with stubs.
- Targeted Kotlin parse checks for modified Android/web/controller files.
- 26-hour diagnostics simulation and forced-rotation scenario.

Not completed in the delivery environment:

- Full Gradle/KSP/Compose build of v0.11.0.
- Device install and runtime test of this increment.
- Real browser upload and low-storage test.
- Real ambient-light sensor validation.
- 24-hour combined slideshow/upload endurance test.

## Known release constraints

- The web interface remains plain HTTP. Upload is disabled by default and is for a trusted private LAN only.
- App-managed Local uploads may be removed on uninstall.
- HEIC conversion, resumable upload, and writable-SAF upload destination are not included.
- Release signing still falls back to the debug key when production material is absent; public CI must be changed to fail closed.
- `targetSdk` remains 35; the separate API-36 security/remediation plan is not part of this feature increment.

## Required local gate

```bash
chmod +x gradlew
./gradlew clean testDebugUnitTest installDebug
```

Then execute `USER_FEATURES_MANUAL_TEST.md` on the API 23 frame before treating the package as device-validated.
