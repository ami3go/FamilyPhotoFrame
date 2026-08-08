# FamilyPhotoFrame v52.1 — Folder-Balanced Shuffle Hardening

**Task:** FPF-FEAT-SHUFFLE-002 v1.1, post-implementation phase 6  
**Application:** `0.12.1-prerelease` (`versionCode 21`)  
**Database:** Room schema v7 (unchanged)  
**Minimum Android:** API 21

## Purpose

This phase hardens the v52.0 reservation and history integration after a requirement-to-code audit. It does not change the persisted schema or the user-facing shuffle algorithm.

## Corrections

### Strict reservation commit validation

A committed presentation must now be an ordered subset of the exact persisted reservation:

- the first committed ID must be the reserved anchor;
- every committed ID must exist in `photoIdsJson`;
- every committed entry must still be in `RESERVED` state;
- persisted positions and IDs must still match their Room rows;
- a failed or removed collage tile cannot be committed accidentally.

Rejected commits leave the reservation and folder turn unchanged and emit `PRESENTATION_COMMIT_REJECTED`.

### No collage escape from the photo cycle

Folder-balanced preparation now treats the coordinator-provided reservation/history list as authoritative even when it contains only the anchor. It never falls back to the general collage candidate query. This prevents:

- selecting a companion outside the reservation;
- crossing the current photo-cycle boundary;
- rebuilding a historical single photo as a new collage;
- consuming an unreserved photo while leaving it pending in Room.

### At-most-once startup recovery

On process startup, the history cursor is restored to the newest committed presentation, but that committed presentation is not selected again. The engine reserves the next shuffle entry instead. This implements the task's commit-before-display, at-most-once recovery rule.

Playlist/scope switches after startup still restore the newest valid committed presentation. Missing or hidden newest entries are skipped backward through bounded history.

### Failed collage candidate cleanup

A failed secondary tile is removed immediately from the live candidate list, preventing recomposition from attempting the same failed tile again in the active preparation.

### Extracted-ZIP release gate

`verify-folder-balanced-v52.sh` no longer fails merely because an extracted release has no `.git` directory. `git diff --check` runs when repository metadata is available; all functional/static gates run in either case.

## Added verification

- instrumentation test rejecting an unreserved collage companion;
- instrumentation test rejecting a companion already marked failed;
- instrumentation test preventing the candidate-failure API from retiring the anchor;
- focused phase-6 source-contract verifier;
- existing SQL, migration, type, web, endurance, and compatibility gates retained.

## Local Android gates

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest installDebug
./gradlew connectedDebugAndroidTest
```
