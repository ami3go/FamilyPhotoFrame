# FamilyPhotoFrame v52.2 — FPF-FEAT-SHUFFLE-002 v1.1 Completion Report

**Package:** FamilyPhotoFrame v52.2  
**Application:** 0.12.2-prerelease (versionCode 22)  
**Database:** Room v8  
**Web assets:** v5220  
**Target specification:** `FPF-FEAT-SHUFFLE-002`, version 1.1

## Completion verdict

All identified source-level deviations from the original folder-balanced shuffle task are implemented. The v52.2 implementation retains the persistent reservation/commit core introduced in v52.0 and hardened in v52.1, then closes the remaining requirements around exact folder actions, folder failures, duplicate identity, diagnostics, health reporting, compatibility mapping, source backoff, and bounded selection memory.

The only outstanding release evidence is execution on real Android hardware and real network sources. The package includes deterministic offline gates, Android unit/instrumentation tests, and a device acceptance procedure, but a container without the Gradle 8.9 distribution or connected Android device cannot produce that hardware evidence.

## Closed deviations

### Playback modes and upgrade behavior

- Added explicit `Sequential`, `Global photo shuffle`, and `Folder-balanced shuffle` behavior.
- New installations default to folder-balanced shuffle.
- New playlists default to folder-balanced shuffle.
- Existing sequential and explicit modes remain unchanged.
- Legacy least-recent random settings migrate once to global no-repeat shuffle rather than silently changing to folder-balanced mode.

### Canonical folder and photo identity

- A folder is the canonical indexed directory that directly contains the photo.
- Folder identity preserves source ID, so equal paths from different sources remain distinct.
- `PhotoItemEntity` now persists `canonicalDirectory`, `contentSha256`, and hash-scan time.
- Room migration 7→8 is additive and non-destructive.
- SHA-256 is calculated incrementally in a background worker from original source bytes, never synchronously in slideshow rendering.
- Synology hashing explicitly bypasses thumbnails.
- Byte-identical content is collapsed only inside the same canonical direct folder.
- Identical content in different folders or sources remains independent.
- Queue reconciliation maps fallback identities to later SHA-256 identities without replaying already-consumed photos.

### Manual folder actions

Android and Web Control now expose two distinct operations:

- **Preview this folder once:** prepares a temporary presentation without consuming or resetting the active folder/photo queues, then returns to the active shuffle.
- **Use this folder in playlist:** updates exact source/directory eligibility and creates the appropriate eligibility-revision scope.

Web folder keys are URL-encoded and decoded explicitly to preserve separators and non-ASCII names.

### Folder failure policy

- Source-level connection failures are deferred once per source rather than retried once per folder.
- Backoff follows 30 seconds, 2 minutes, 5 minutes, then 15 minutes maximum.
- Folder-local failures retry at most twice while the source is healthy.
- Exhausted folder failures become one explicit `SKIPPED` terminal state for that folder cycle, with a recorded reason.
- A failed photo remains within the current folder turn and does not advance folder fairness.

### Diagnostics and health reporting

Added or completed:

- `SHUFFLE_SCOPE_RESTORED`
- `FOLDER_DEFERRED`
- source outage/recovery and explicit skip evidence
- eligible, pending, presented, skipped, and removed folder counts
- pending photos and quarantined photos
- active reservation age
- last commit, reconciliation, and startup-recovery timestamps
- selection timing diagnostics with the task targets: 50 ms cached/local and 250 ms network metadata, excluding media download

The high-volume selection timing event is written to the bounded slideshow diagnostics stream.

### Bounded memory and query behavior

Production folder-balanced selection no longer materializes every eligible photo across every folder.

It now:

1. queries lightweight grouped folder metadata;
2. reserves the next persisted folder turn;
3. loads only that selected folder's eligible member rows;
4. keeps history bounded to metadata only;
5. performs hashing and Room work away from the Compose UI thread.

This satisfies the task's architectural requirement to load the active folder-cycle metadata and only the required current-folder photo entries.

## Persistent-state behavior retained from v52.0/v52.1

- persisted Fisher–Yates folder and per-folder photo cycles;
- boundary-repeat protection;
- unique database constraints;
- one active reservation per scope;
- atomic reserve/prepare/commit/release state machine;
- at-most-once recovery after commit;
- startup release of interrupted reservations;
- same-folder collage reservation with bounded lookahead 12;
- no cross-cycle or cross-folder collage fill;
- persistent Previous/forward history without queue rewind;
- idempotent additions, removals, Hide, Undo, and Favorites-only reconciliation;
- independent playlist scopes and histories;
- bounded history, cycles, reservations, and inactive-scope cleanup;
- reset active, reset all, and reset-and-clear-history operations;
- one coordinator for automatic, touch, keyboard/D-pad, and Web Control ordering actions.

## Verification supplied

The extracted source package passes:

- engine and persistence Kotlin type checks;
- Room v8 consistency checks;
- all 101 DAO SQL statements against SQLite;
- migration replay from v1 through v8;
- folder-balanced phase 6 hardening checks;
- 32 phase 7 task-completion checks;
- 100 complete 50-folder cycles;
- at least ten photo cycles per folder;
- 10,000-member photo-cycle validation;
- deterministic 24-hour-equivalent additions/removals/outages/restarts/resets simulation;
- web JavaScript parsing and compatibility checks;
- complete pure-logic regression suite;
- archive integrity verification.

## Device release gate

Run from the project root:

```bash
./scripts/verify-folder-balanced-v52.sh
./scripts/verify-folder-balanced-android.sh
```

Then execute the manual procedure in:

```text
docs/FOLDER_BALANCED_SHUFFLE_DEVICE_ACCEPTANCE_v52.2.md
```

A production release requires a configured release keystore. Debug signing is valid only for local installation and test.
