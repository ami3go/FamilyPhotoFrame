# FamilyPhotoFrame v52.0 — Folder-Balanced Shuffle Implementation

**Task:** FPF-FEAT-SHUFFLE-002 v1.1  
**Application:** `0.12.0-prerelease` (`versionCode 20`)  
**Database:** Room schema v7  
**Minimum Android:** API 21

## Result

The slideshow now has a persistent `Folder-balanced shuffle` playback mode. It uses a Room-backed, two-level shuffle coordinator rather than the previous process-local queue:

- folders are shuffled without replacement;
- each folder owns an independent photo cycle;
- generated order and entry state survive process death and reboot;
- selection is reserved before decode/preparation;
- the folder turn and selected photo members advance only after an atomic commit;
- failed preparation returns the folder turn to pending and retires only the failed member;
- committed presentation history supports persistent Previous and forward-Next navigation;
- source outages defer all affected folders at source level while healthy sources continue;
- reconciliation inserts/removes folders and photos idempotently;
- queue/history/scope retention is bounded.

## Phase 1 — Domain model and pure algorithm

Implemented under `slideshow/shuffle/`:

- source-aware `FolderKey` based on the indexed photo's direct parent directory;
- per-folder `FolderPhotoKey`;
- `ShuffleRandom` abstraction and deterministic fixed-seed implementation;
- Fisher–Yates folder/photo cycle generation;
- immediate boundary-repeat avoidance;
- eligibility-only revision and scope-key factory;
- pure source-backoff policy.

The persisted generated order is authoritative; a random seed is never required to restore a cycle.

## Phase 2 — Persistence and reservation state machine

Room schema v7 adds:

- `shuffle_scopes`;
- `folder_shuffle_entries`;
- `folder_photo_cycles`;
- `photo_shuffle_entries`;
- `shuffle_reservations`;
- `presentation_history`.

Uniqueness indexes enforce one folder per folder cycle and one member per folder photo cycle. Migration `MIGRATION_6_7` is additive and non-destructive.

Transactions implement:

- reserve without advancing;
- commit exactly once before visibility;
- release to safe pending state;
- startup recovery of interrupted reservations;
- history cursor persistence;
- active/all/history reset;
- playlist-state deletion;
- 100-record history retention;
- 30-day / 32-inactive-scope cleanup.

## Phase 3 — Slideshow and history integration

`SlideshowEngine` delegates folder-balanced ordering to one `FolderBalancedShuffleCoordinator` guarded by a Kotlin `Mutex`.

Integrated paths include:

- automatic, touch, keyboard/D-pad, and web Next/Previous;
- same-folder collage lookahead limited to 12 pending members;
- commit after the slide is prepared but before it becomes the visible transition candidate;
- smaller collage commit when a secondary tile fails;
- same-folder retry after anchor decode failure;
- persistent Previous/forward history;
- missing or hidden history-member skipping;
- Favorites-only changes, Hide, Undo/reconciliation, playlist switch, and playlist deletion.

The existing global no-repeat, least-recent, and date-ordered modes remain available and are not migrated silently.

## Phase 4 — Reconciliation and source availability

The canonical Room eligibility query is shared by cycle creation and reconciliation.

Implemented behavior:

- repeated identical snapshots are idempotent;
- newly eligible folders/photos are inserted once into the unresolved suffix;
- removed or hidden members become terminal and cannot be selected;
- restored members re-enter once when appropriate;
- preparation failures are retained across replacement cycles and quarantine at three failures;
- a source outage is represented once at source level;
- unavailable folders are deferred while healthy sources continue;
- after bounded recovery exhaustion, each remaining affected folder is marked `SKIPPED` once for that cycle;
- source recovery preserves unrelated folder/photo progress;
- cached-only fallback does not create a new eligibility revision.

## Phase 5 — Android UI, Web Control, diagnostics, reset, and hardening

Added:

- Android and Web Control playback selector entry;
- new-playlist default of folder-balanced shuffle;
- folder/photo resolved progress;
- presented/skipped/removed/quarantined status;
- current folder and unavailable-source metrics;
- reset active, reset all, and reset-and-clear-history actions with confirmation;
- diagnostics for scope/cycle/reservation/commit/release/recovery/reconciliation/reset events;
- web asset revision `v5200` so immutable browser caches receive the new controls.

## Verification completed in this package

Passed offline:

- engine and persistence Kotlin type check against Room stubs;
- all 96 Room SQL statements prepared against SQLite;
- complete v1→v7 migration replay with 13 resulting tables;
- source/resource/callback/Room consistency checks;
- web UI JavaScript parse and modernisation contract;
- bulk-upload/hardening compatibility contract;
- pure algorithm and regression suite;
- 106-folder uniqueness check;
- 100 complete 50-folder fairness cycles;
- ten complete photo cycles for each simulated folder;
- 10,000-member photo-cycle completeness/performance check;
- source backoff/recovery checks;
- reconciliation uniqueness/idempotence checks;
- `git diff --check`.

Android instrumentation tests were added for reservation stability, exactly-once commit, failure release, startup recovery, source skip/healthy continuation, playlist reset/deletion, and persistent history cursor.

The complete offline release gate can be repeated with:

```bash
./scripts/verify-folder-balanced-v52.sh
```

## Required local build and device gates

This environment does not contain the Gradle 8.9 distribution and cannot download it. Run on the development machine:

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest installDebug
./gradlew connectedDebugAndroidTest
```

Then complete the task's device acceptance gates:

- reboot during an active reservation;
- process kill after commit and before visual display;
- simultaneous touch/Web Next;
- SMB/WebDAV disconnect and recovery;
- reset during a transition;
- 24-hour dynamic simulation;
- six-hour memory soak using the diagnostic bundle.

## Identity policy note

The current index does not synchronously hash photo bytes. Folder-member identity therefore follows the task's permitted priority using the existing durable `stableId`, with the source/path/size/modified composite as fallback. Duplicate queue rows with the same identity are suppressed inside one folder; identical copies in different folders remain independent. A future source-provided content SHA-256 can replace the identity input without changing the queue schema.
