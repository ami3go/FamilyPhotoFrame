# FamilyPhotoFrame — Folder-Balanced Non-Repeating Shuffle

**Document ID:** FPF-FEAT-SHUFFLE-002  
**Version:** 1.1  
**Status:** Implementation-ready  
**Target baseline:** FamilyPhotoFrame v51.3 or later  
**Minimum supported OS:** Android 5.0 / API 21  
**Primary objective:** Randomize folders fairly and prevent repeated folders or photos until their current shuffle cycles are complete.

---

## 1. Objective

Replace the current simple random-photo behavior with a **two-level folder-balanced shuffle**.

The slideshow must:

1. Randomize the order of eligible folders.
2. Present or explicitly skip every eligible folder once before any folder repeats.
3. Maintain an independent shuffled photo queue inside every folder.
4. Avoid showing the same photo from a folder until all eligible photos in that folder have been consumed.
5. Preserve shuffle progress across activity recreation, app restart, and device reboot.
6. Work with playlists, Favorites, hidden photos, schedules, collages, manual Previous/Next, web controls, and source rescans.
7. Serialize every ordering action through one coordinator.
8. Fail safely when a source, folder, or photo is unavailable.

This feature intentionally prioritizes **folder fairness** rather than weighting folders by photo count.

Example:

```text
Folder A: 100 photos
Folder B: 10 photos
Folder C: 2 photos
```

A valid folder cycle:

```text
B → A → C
```

The next cycle may be:

```text
A → C → B
```

No available folder may be presented twice in one folder cycle.

Each time a folder is selected, the slideshow consumes the next unused photo or collage members from that folder’s own photo cycle.

---

## 2. Changes from task v1.0

Version 1.1 resolves the implementation ambiguities identified during review:

- defines a concrete reservation state machine;
- defines exactly when folder and photo cursors advance;
- distinguishes presented folders from explicitly skipped unavailable folders;
- limits duplicate suppression to copies inside the same folder;
- defines a folder as the indexed directory that directly contains the photo;
- removes collage settings from shuffle-scope identity;
- adds database uniqueness constraints and bounded cleanup;
- defines persistent presentation history and restart behavior;
- defines bounded collage lookahead without starving non-collage photos;
- makes persisted queues authoritative instead of relying on a persisted random seed;
- makes dynamic reconciliation idempotent;
- defines reset behavior;
- adds source-level outage handling and backoff.

---

## 3. User-visible playback mode

Add:

```text
Folder-balanced shuffle
```

Description:

```text
Randomizes folders first. Each available folder is used once before a folder repeats.
Photos inside each folder also do not repeat until that folder's photo cycle is complete.
```

Keep existing modes:

```text
Sequential
Global photo shuffle
Folder-balanced shuffle
```

Recommended default for new installations:

```text
Folder-balanced shuffle
```

Upgrade behavior:

- existing `Sequential` remains `Sequential`;
- existing random mode maps to `Global photo shuffle`;
- do not change an existing user’s playback order silently;
- new playlists use `Folder-balanced shuffle` unless copied from another playlist.

---

## 4. Definitions

### 4.1 Folder cycle

One randomized, non-repeating ordering of all folders eligible for the active shuffle scope.

A folder-cycle entry finishes as either:

```text
PRESENTED
```

or:

```text
SKIPPED with a recorded reason
```

Under normal source availability, every eligible folder must be presented once before a folder repeats.

A folder may be skipped only after the defined source/folder failure policy is exhausted.

### 4.2 Photo cycle

One randomized, non-repeating ordering of all eligible photo members inside one folder for one shuffle scope.

A photo-cycle entry finishes as:

```text
CONSUMED
REMOVED
QUARANTINED
```

Only `CONSUMED` means it was successfully committed to a presentation.

### 4.3 Folder turn

One folder-cycle entry.

A folder turn is consumed only when:

- a presentation from that folder is committed successfully; or
- the folder is explicitly skipped after its retry/failure policy is exhausted.

A photo decode failure does not consume the folder turn.

### 4.4 Committed presentation

A successfully prepared presentation whose shuffle advancement and history record have been committed atomically before it becomes the visible current presentation.

A committed presentation may contain:

- one photo; or
- a same-folder collage.

---

## 5. Core algorithm

Use two nested non-repeating shuffle bags.

### 5.1 Folder shuffle bag

At the beginning of a folder cycle:

1. obtain the canonical eligible-folder set;
2. shuffle it with Fisher–Yates;
3. prevent an immediate boundary repeat when possible;
4. persist the generated order;
5. mark every entry `PENDING`;
6. select the first pending folder;
7. start a new cycle only after all entries are `PRESENTED`, `SKIPPED`, or `REMOVED`.

Invariant:

```text
UNIQUE(scopeKey, folderCycle, folderKey)
```

A folder appears at most once in one folder cycle.

### 5.2 Per-folder photo shuffle bag

Each folder has its own independent photo cycle.

When a folder is selected:

1. restore that folder’s current photo cycle;
2. create a new cycle if no active pending photo remains;
3. obtain the canonical eligible member set for that folder;
4. shuffle it with Fisher–Yates;
5. prevent an immediate boundary repeat when possible;
6. persist every entry as `PENDING`;
7. reserve the next pending photo or collage candidates;
8. commit consumed members only after successful preparation.

Invariant:

```text
UNIQUE(scopeKey, folderKey, photoCycle, folderPhotoKey)
```

A folder-photo member appears at most once in one photo cycle.

### 5.3 Boundary-repeat protection

New folder cycle:

```text
If eligible folder count > 1:
first folder must not equal the previous cycle's last presented folder.
```

New photo cycle:

```text
If eligible member count > 1:
first member must not equal that folder's previous cycle last consumed member.
```

If the shuffled first item conflicts, swap it with any non-conflicting later item.

Boundary protection is best-effort only when more than one eligible item exists.

### 5.4 Random-source abstraction

Use:

```kotlin
interface ShuffleRandom {
    fun nextInt(bound: Int): Int
}
```

Production:

```text
Kotlin Random or java.util.Random
```

Tests:

```text
deterministic fixed-seed implementation
```

The persisted shuffled order is authoritative.

Do not rely on regenerating an existing cycle from a stored seed. A diagnostic cycle seed may be recorded, but it must not be required for restart recovery.

Do not use cryptographic randomness.

---

## 6. Folder definition and identity

A shuffle folder is:

```text
the actual indexed directory that directly contains at least one eligible photo
```

Example:

```text
Selected root: /Family

Photos exist in:
/Family
/Family/2024
/Family/2025/Birthday
```

These are three distinct folder units if each directory directly contains eligible photos.

Use the indexer’s existing canonical path normalization. Do not create a second path-normalization implementation.

Recommended key:

```kotlin
data class FolderKey(
    val sourceId: String,
    val canonicalRelativeDirectory: String,
)
```

Rules:

- preserve source identity;
- use canonical separators and path rules already used by the indexer;
- use a stable reserved value for the source root;
- do not identify folders only by display name;
- do not merge equal paths from different sources.

Examples:

```text
local:/Family/2024
smb-main:/Family/2024
webdav-backup:/Family/2024
```

These are distinct folder keys.

---

## 7. Photo identity and duplicate policy

### 7.1 Content identity

Preferred identity priority:

1. existing content SHA-256, when already available;
2. stable database photo ID where source identity is durable;
3. fallback composite:

```text
source ID
canonical relative path
file size
last-modified time
```

Do not calculate a full hash synchronously during slideshow rendering.

### 7.2 Folder-photo member identity

Use:

```kotlin
data class FolderPhotoKey(
    val folderKey: FolderKey,
    val contentIdentity: String,
)
```

### 7.3 Duplicate policy

Version 1 behavior:

```text
Suppress duplicate content entries inside the same folder.
Treat identical copies located in different folders as separate folder members.
```

Reason:

- the requested guarantee applies to photos inside each folder;
- cross-folder suppression could make one folder appear empty;
- assigning one duplicate group to one folder would undermine folder fairness.

Global duplicate suppression across folders is outside this task and may be added later as a separate optional policy.

---

## 8. Eligibility

A folder is eligible when:

- its source is enabled by the active playlist;
- its canonical directory matches playlist folder rules;
- it directly contains at least one eligible folder-photo member;
- it is not excluded by the active eligibility policy;
- it is not permanently removed;
- its source is available or still inside its bounded retry policy.

A folder-photo member is eligible when:

- it belongs to the folder;
- it matches active playlist filters;
- it is not hidden;
- it is not deleted or missing;
- it satisfies Favorites-only behavior where enabled;
- it uses a supported format;
- it is not quarantined after repeated preparation failure.

Eligibility must come from one canonical provider shared by initial cycle creation and reconciliation.

---

## 9. Shuffle scope

Every independent eligibility configuration has independent shuffle state.

Recommended stable inputs:

```text
playlist ID
playlist eligibility revision
playback-order mode
```

The playlist eligibility revision must change only when selection eligibility changes, including:

- enabled sources;
- included/excluded folder rules;
- Favorites-only rule;
- hidden-photo rule;
- date or recent-photo filters;
- other photo-selection filters.

The following must not change the shuffle scope:

- playlist rename;
- interval;
- transition;
- image fit;
- brightness;
- collage enable/disable;
- collage size;
- collage layout;
- web UI presentation settings.

Changing collage settings affects how many current-cycle photos a presentation consumes, but it does not create a new photo eligibility universe.

Suggested type:

```kotlin
data class ShuffleScopeKey(
    val playlistId: String,
    val eligibilityRevision: Long,
    val playbackOrder: PlaybackOrder,
)
```

Rules:

- each playlist keeps independent cycles;
- scheduled playlist switching restores the target playlist’s scope;
- manual playlist override restores that playlist’s scope;
- renaming does not reset progress;
- duplicating a playlist creates a new playlist ID and new scope;
- stale inactive scopes are removed after 30 days;
- retain at most 32 inactive scopes using least-recently-used cleanup.

---

## 10. Persistent state model

Shuffle state must survive:

- activity recreation;
- process restart;
- frame reboot;
- temporary source disconnect;
- web-server changes that do not alter eligibility.

Persist generated queue order and entry state. Do not persist only an in-memory list.

### 10.1 Folder scope

```kotlin
@Entity
data class ShuffleScopeEntity(
    @PrimaryKey val scopeKey: String,
    val activeFolderCycle: Long,
    val lastPresentedFolderKey: String?,
    val lastUsedAtEpochMs: Long,
    val eligibilityRevision: Long,
    val reconciliationRevision: Long,
)
```

### 10.2 Folder entries

```kotlin
@Entity(
    primaryKeys = ["scopeKey", "folderCycle", "position"],
    indices = [
        Index(
            value = ["scopeKey", "folderCycle", "folderKey"],
            unique = true,
        ),
    ],
)
data class FolderShuffleEntryEntity(
    val scopeKey: String,
    val folderCycle: Long,
    val position: Int,
    val folderKey: String,
    val state: FolderEntryState,
    val retryCount: Int,
    val skipReason: String?,
)
```

States:

```text
PENDING
RESERVED
PRESENTED
SKIPPED
REMOVED
```

### 10.3 Per-folder photo cycle

```kotlin
@Entity(
    primaryKeys = ["scopeKey", "folderKey"],
)
data class FolderPhotoCycleEntity(
    val scopeKey: String,
    val folderKey: String,
    val activePhotoCycle: Long,
    val lastConsumedPhotoKey: String?,
    val reconciliationRevision: Long,
)
```

### 10.4 Photo entries

```kotlin
@Entity(
    primaryKeys = ["scopeKey", "folderKey", "photoCycle", "position"],
    indices = [
        Index(
            value = ["scopeKey", "folderKey", "photoCycle", "folderPhotoKey"],
            unique = true,
        ),
    ],
)
data class PhotoShuffleEntryEntity(
    val scopeKey: String,
    val folderKey: String,
    val photoCycle: Long,
    val position: Int,
    val folderPhotoKey: String,
    val photoId: Long,
    val state: PhotoEntryState,
    val failureCount: Int,
)
```

States:

```text
PENDING
RESERVED
CONSUMED
REMOVED
QUARANTINED
```

### 10.5 Reservation

Allow at most one active reservation per shuffle scope.

```kotlin
@Entity
data class ShuffleReservationEntity(
    @PrimaryKey val scopeKey: String,
    val reservationId: String,
    val folderCycle: Long,
    val folderPosition: Int,
    val folderKey: String,
    val photoCycle: Long,
    val photoPositionsJson: String,
    val photoIdsJson: String,
    val createdAtEpochMs: Long,
)
```

### 10.6 Presentation history

```kotlin
@Entity(
    indices = [
        Index(value = ["scopeKey", "sequence"], unique = true),
    ],
)
data class PresentationHistoryEntity(
    @PrimaryKey val presentationId: String,
    val scopeKey: String,
    val sequence: Long,
    val folderKey: String,
    val presentationType: String,
    val photoIdsJson: String,
    val committedAtEpochMs: Long,
)
```

Store metadata only. Never store bitmaps.

Retain at most:

```text
100 committed presentations per scope
```

---

## 11. Reservation state machine

The ordering guarantee is **at-most-once after commit**.

State flow:

```text
PENDING
→ RESERVED
→ PRESENTED / CONSUMED
```

Failure flow:

```text
RESERVED
→ PENDING
```

or:

```text
RESERVED
→ REMOVED / QUARANTINED
```

### 11.1 Reserve transaction

One Room transaction must:

1. verify no active reservation exists for the scope;
2. select the next `PENDING` folder entry;
3. select the next `PENDING` photo entry or bounded collage candidates;
4. mark selected folder and photos `RESERVED`;
5. create `ShuffleReservationEntity`;
6. commit without advancing the folder turn.

### 11.2 Prepare outside transaction

Decode and prepare the reserved photo or collage outside the database transaction and outside the UI thread.

The visible presentation must not change yet.

### 11.3 Commit transaction

After successful preparation, one Room transaction must:

1. verify reservation identity;
2. mark reserved photo entries `CONSUMED`;
3. mark reserved folder entry `PRESENTED`;
4. update last consumed photo;
5. update last presented folder;
6. write presentation-history metadata;
7. delete the reservation;
8. determine whether the folder or photo cycle has completed;
9. create the next cycle only when required.

Only after this transaction succeeds may the new presentation become visible.

### 11.4 Release transaction

After preparation failure:

1. verify reservation identity;
2. restore the folder entry to `PENDING`;
3. return usable unfailed photos to `PENDING`;
4. mark failed photo `REMOVED` or `QUARANTINED` according to failure count;
5. delete the reservation;
6. retry another photo from the same folder.

A failed photo must not consume the folder turn.

### 11.5 Startup recovery

On process startup:

- find active reservations;
- release them to safe pending states;
- do not assume an uncommitted presentation was shown;
- emit a recovery diagnostic.

Because database commit occurs before visual display, a crash after commit but before rendering may skip one presentation after restart. It must not repeat that committed presentation.

This at-most-once behavior is preferred over possible duplicate display.

---

## 12. Folder-turn consumption and failure policy

Folder turn flow:

```text
select folder
→ reserve one or more photos from that folder
→ prepare
→ commit presentation
→ mark folder PRESENTED
```

Decode failure flow:

```text
select folder
→ reserved photo fails
→ remove/quarantine failed photo
→ keep folder PENDING
→ try another unused photo from the same folder
```

Skip flow:

```text
folder/source remains unavailable
or no usable photo remains
→ exhaust retry policy
→ mark folder SKIPPED with reason
→ continue to next folder
```

A folder may not be selected again in the same folder cycle after it becomes `PRESENTED` or `SKIPPED`.

---

## 13. Single-photo and collage behavior

### 13.1 Single photo

```text
selected folder
→ reserve first pending folder-photo member
→ prepare
→ commit
→ consume one folder turn
```

### 13.2 Collage entry rule

Always begin with the next pending photo in queue order.

If the first photo is not eligible for portrait collage:

```text
present it as a normal single photo
```

If the first photo is portrait-collage eligible:

1. search forward through at most 12 remaining pending entries in the same folder;
2. select additional compatible portrait photos up to the configured collage size;
3. leave unselected entries pending in their original relative order;
4. never select from another folder;
5. never select from a new photo cycle to fill the collage.

### 13.3 Collage size rules

- one selected member: normal single-photo presentation;
- two selected members: two-photo collage;
- three or more: configured collage layout;
- no duplicate member inside one collage;
- no one-tile collage mode;
- if a selected tile fails decode, release only that tile and allow a smaller valid presentation;
- if the first photo fails, retry the same folder with its next pending photo.

Example:

```text
Requested collage size: 3
Unused compatible photos before cycle boundary: 2
Result: 2-photo collage
```

Landscape photos and non-selected portrait photos remain pending and must not be starved indefinitely.

---

## 14. Folder fairness and guarantee wording

Folder selection is not weighted by photo count.

For three eligible folders:

```text
Every folder cycle has three folder entries.
```

Example:

```text
A: 500 photos
B: 20 photos
C: 1 photo
```

Valid cycle:

```text
C → A → B
```

A one-photo folder necessarily repeats its only photo on its next photo cycle.

Formal guarantee:

```text
Under normal source availability, every eligible folder is successfully
presented once before any folder repeats.

If a folder or source is unavailable or contains no usable photo, that folder
may be explicitly skipped once for the current cycle after the retry policy.
```

The no-repeat guarantee applies only when at least two eligible items exist.

---

## 15. Presentation history and Previous/Next

### 15.1 Previous

```text
Previous
→ move backward through committed presentation history
→ do not rewind folder or photo cycle state
```

If a history presentation references photos that are now missing or hidden:

- skip that history entry;
- continue to the next valid prior entry;
- do not modify shuffle queues.

### 15.2 Next after Previous

Maintain a per-scope history cursor.

```text
Previous
→ Next
→ move forward through already committed history
```

Only when the cursor reaches the newest committed presentation may Next reserve a new shuffle candidate.

### 15.3 Restart behavior

History metadata is persistent.

After restart:

- restore current scope history cursor to the newest committed presentation;
- Previous may navigate stored history;
- Next after Previous follows forward history;
- missing/hidden historical photos are skipped;
- each playlist/scope keeps independent history.

### 15.4 Playlist switch

Switching playlists:

- persists current scope state;
- activates target scope;
- restores target history at its newest committed item;
- does not merge histories.

---

## 16. Dynamic reconciliation

Reconciliation must be idempotent.

Processing the same canonical eligibility snapshot twice must produce no second insertion or duplicate row.

### 16.1 Reconciliation transaction

For each active scope:

1. read the canonical eligible folder set;
2. mark no-longer-eligible pending folder entries `REMOVED`;
3. find newly eligible folders absent from all entries in the active cycle;
4. insert each new folder exactly once at a random unconsumed position;
5. repeat the same process for changed folders’ photo cycles;
6. update reconciliation revision only after the transaction commits.

Unique indexes are the final protection against duplicate insertion.

### 16.2 New folder during active cycle

Insert exactly once at a random position among remaining unconsumed positions.

The folder must be presented or explicitly skipped before the active cycle ends.

### 16.3 Removed or disabled folder

Mark remaining entry `REMOVED` immediately.

Preserve that folder’s independent photo-cycle state until scope cleanup in case it returns.

### 16.4 New photo inside a folder

Insert exactly once at a random remaining position after already consumed entries.

It must be consumed, removed, or quarantined before that photo cycle completes.

### 16.5 Hidden, deleted, or filtered photo

Mark pending entry `REMOVED` immediately.

If reserved, cancel/release the reservation before removal.

### 16.6 Unhidden or restored photo

Treat as newly eligible and insert exactly once after consumed entries.

### 16.7 Favorites-only playlist

Removing Favorite removes a pending member.

Adding Favorite inserts the member once after consumed entries.

---

## 17. Source and folder availability

### 17.1 Source-level failure

Connection-level failures must be handled at source level.

Example:

```text
SMB authentication failure
network unreachable
WebDAV host unavailable
```

On the first confirmed connection-level failure:

1. mark the source temporarily unavailable;
2. defer all remaining folders from that source;
3. avoid retrying every folder independently;
4. start bounded source-level backoff;
5. continue with folders from available sources.

Recommended backoff:

```text
30 seconds
2 minutes
5 minutes
15 minutes maximum
```

### 17.2 Folder-level failure

Use folder-level retry only when the source is otherwise available, for example:

```text
path removed
permission denied for one directory
folder metadata inconsistent
```

Retry a folder at most twice during one folder cycle.

After exhaustion:

```text
mark folder SKIPPED with reason
```

### 17.3 Source recovery

When source recovery is confirmed:

- if the current folder cycle is still active, return its deferred pending folders to the remaining cycle;
- if that cycle has already completed, include them in the next cycle;
- preserve unrelated folders and photo cycles;
- do not reset the scope.

---

## 18. Decode and preparation failures

On photo preparation failure:

1. release the reservation;
2. increment the photo’s consecutive failure count;
3. keep the folder turn pending;
4. try the next pending photo from the same folder;
5. quarantine after three consecutive failures;
6. continue until a valid presentation is committed or no usable member remains.

If no usable member remains:

```text
mark folder SKIPPED for this folder cycle
surface a health recommendation
```

A decode failure must not:

- advance to another folder prematurely;
- create a repeat;
- block the slideshow indefinitely.

---

## 19. Favorites, Hide, and Undo

### 19.1 Favorite

For normal playlists, Favorite state does not alter the current queue.

For Favorites-only scope:

- removing Favorite removes pending eligibility;
- adding Favorite inserts once after consumed entries.

### 19.2 Hide

Hide must:

- remove the photo from pending and future cycles;
- cancel a reservation containing it if needed;
- preserve committed presentation history for Undo;
- advance using the normal coordinator;
- never delete the source file.

For a collage, hide applies to the explicitly selected collage member.

### 19.3 Undo hide

Undo within the existing undo window:

- restores eligibility;
- reconciles the member once into the remaining photo cycle;
- does not force immediate display;
- does not duplicate an already queued member.

---

## 20. Playlist and schedule integration

Each playlist maintains independent:

- folder cycle;
- per-folder photo cycles;
- presentation history;
- active reservation;
- diagnostics status.

Scheduled switch:

```text
finish or cancel pending preparation safely
→ persist current scope
→ activate target playlist scope
→ restore or initialize target scope
```

Rules:

- rename does not reset state;
- eligibility changes increment `eligibilityRevision`;
- non-eligibility presentation changes do not increment it;
- duplicate playlist gets a new scope;
- deleted playlist state is removed by confirmed cleanup.

---

## 21. Manual folder selection and web controls

Two explicit manual-folder actions:

### 21.1 Preview this folder once

```text
show a temporary presentation from the selected folder
→ do not mutate active shuffle queues
→ return to active shuffle afterward
```

### 21.2 Use this folder in playlist

```text
change playlist eligibility
→ increment eligibility revision
→ create or restore the resulting scope
```

The UI must distinguish these actions.

Touch, keyboard, D-pad, web Next, and web Previous must all call the same coordinator.

There must be only one ordering authority.

---

## 22. Concurrency

Use one `FolderBalancedShuffleCoordinator` guarded by a Kotlin `Mutex`.

Serialize:

- auto advance;
- touch Next;
- web Next;
- keyboard/D-pad Next;
- Previous and forward-history navigation;
- playlist switching;
- schedule switching;
- rescan reconciliation;
- Favorite/Hide/Undo;
- source recovery;
- decode-failure handling;
- reset;
- startup restoration.

Only one active reservation may exist per scope.

No two callers may reserve the same folder or photo entries.

---

## 23. Reset behavior

### 23.1 Reset active shuffle progress

Confirmation:

```text
Reset folder and photo shuffle progress for the active playlist?
This does not delete or modify any photo files.
```

Behavior:

1. keep the current committed presentation visible;
2. cancel and release any pending reservation;
3. clear active folder and photo cycle rows for that scope;
4. clear forward-history position by moving the history cursor to the newest committed item;
5. retain backward committed history;
6. create a new folder cycle on the next advance;
7. do not change photo metadata, Favorite, Hidden, or playlist settings.

### 23.2 Reset all playlist shuffle progress

- apply the same operation to every inactive scope;
- retain current visible presentation;
- retain backward history unless the user selects `Reset and clear history`.

### 23.3 Reset during transition

Finish or snap to one committed presentation first.

Never reset while an uncommitted visual candidate is partially displayed.

---

## 24. Cleanup and bounded storage

Retention rules:

- keep only the current active folder cycle for each scope;
- after a replacement cycle is committed, delete prior folder-cycle entries;
- keep only the current photo cycle for each folder/scope;
- after replacement photo cycle creation commits, delete prior photo-cycle entries;
- keep at most one active reservation per scope;
- keep at most 100 history records per scope;
- keep at most 32 inactive scopes;
- remove inactive scopes after 30 days;
- cleanup must never remove the currently active scope.

Cleanup must be transactional, bounded, cancellable, and run off the UI thread.

---

## 25. Proposed architecture

Create:

```text
slideshow/shuffle/
```

Suggested files:

```text
FolderBalancedShuffleCoordinator.kt
ShuffleScopeKeyFactory.kt
ShuffleRepository.kt
ShuffleModels.kt
ShuffleRandom.kt
ShuffleEligibilityProvider.kt
ShuffleCycleReconciler.kt
PresentationHistoryRepository.kt
ShuffleReservationRecovery.kt
SourceAvailabilityTracker.kt
```

Responsibilities:

### `FolderBalancedShuffleCoordinator`

- only public ordering authority;
- serializes actions;
- reserves, commits, and releases;
- coordinates history;
- coordinates folder and photo cycles.

### `ShuffleRepository`

- Room persistence;
- unique constraints;
- atomic state transitions;
- cycle cleanup;
- history persistence.

### `ShuffleEligibilityProvider`

- canonical eligible folders and folder members;
- reuses indexer path normalization;
- applies playlist/Favorite/Hidden/date filters.

### `ShuffleCycleReconciler`

- idempotent merge of new/removed eligibility;
- preserves consumed state;
- updates reconciliation revision.

### `PresentationHistoryRepository`

- bounded metadata history;
- per-scope cursor;
- missing-entry skip behavior.

### `SourceAvailabilityTracker`

- source-level outage classification;
- backoff;
- recovery events.

Do not add the algorithm directly to `SlideshowViewModel.kt`.

`SlideshowViewModel` should call the coordinator through a narrow interface.

---

## 26. Android and web UI

Playback settings:

```text
Playback order
○ Sequential
○ Global photo shuffle
● Folder-balanced shuffle
```

Status:

```text
Folder cycle: 7 of 24 resolved
Current folder: Family/2024
Photo cycle: 18 of 126 resolved
Source state: Available
```

Use `resolved` rather than always saying `shown`, because entries may be explicitly skipped or removed.

Optional details:

```text
Folder cycle number: 12
Photo cycle number: 3
Folders presented: 6
Folders skipped: 1
```

Actions:

```text
Reset active shuffle progress
Reset all playlist shuffle progress
Reset and clear shuffle history
```

---

## 27. Health dashboard

Add:

- active playback order;
- active playlist;
- eligible folder count;
- presented, pending, skipped, and removed folder counts;
- current folder;
- pending photo count in current folder;
- unavailable sources;
- skipped folders;
- quarantined photos;
- active reservation age;
- last persisted commit;
- last reconciliation;
- last startup reservation recovery.

Recommendations:

```text
One source is unavailable.
The slideshow is continuing with other folders.

Three photos could not be decoded.
Review quarantined photos.

An interrupted shuffle reservation was recovered.
No duplicate was introduced.

Shuffle state could not be saved.
Progress may skip or repeat after restart.
```

---

# 28. Five-phase implementation plan

## Phase 1 — Domain model and pure algorithm

Implement:

- playback-order enum;
- folder definition and key;
- folder-photo member identity;
- duplicate policy;
- scope-key and eligibility revision;
- injectable random source;
- pure folder and photo cycle generators;
- boundary-repeat protection;
- deterministic tests;
- compatibility mapping from current playback settings.

Review gate:

- no duplicate folder in a generated cycle;
- no duplicate folder-photo member in its cycle;
- boundary repeat avoided when possible;
- one-item cycles behave predictably;
- fixed seed produces deterministic new-cycle order;
- persisted order remains authoritative;
- existing settings map correctly.

## Phase 2 — Persistent repository and reservation state machine

Implement:

- Room entities;
- unique indexes;
- schema migration;
- reservation entity;
- presentation history;
- reserve/commit/release transactions;
- startup recovery;
- bounded cleanup;
- inactive-scope retention.

Review gate:

- reserve does not consume a folder turn;
- commit advances exactly once;
- release restores safe pending state;
- one reservation per scope;
- process restart restores exact next pending state;
- crash after commit cannot repeat committed entries;
- database growth remains bounded;
- migration is non-destructive;
- API 21 Room tests pass.

## Phase 3 — Slideshow integration and history

Implement:

- coordinator;
- single-photo selection;
- bounded same-folder collage lookahead;
- auto advance;
- touch/web/keyboard controls;
- persistent Previous/Next history;
- decode-failure retry;
- Favorite/Hide/Undo;
- playlist switching.

Review gate:

- one ordering authority;
- concurrent Next calls cannot duplicate reservation;
- Previous never rewinds shuffle state;
- Next through history does not consume new items;
- collage never crosses folder or photo-cycle boundary;
- non-selected lookahead items remain ordered and pending;
- decode failure keeps the same folder turn;
- playlist states remain independent.

## Phase 4 — Idempotent reconciliation and availability recovery

Implement:

- canonical eligibility snapshots;
- idempotent folder insertion/removal;
- idempotent photo insertion/removal;
- reconciliation revision;
- source-level outage tracking;
- folder-level retry;
- source backoff and recovery;
- quarantine;
- eligibility revision changes;
- schedule integration.

Review gate:

- processing the same snapshot twice makes no duplicate;
- new folders/photos resolve before active cycle completion;
- removed/hidden entries cannot be selected;
- one source outage does not trigger per-folder retry storms;
- recovered source returns without resetting unrelated progress;
- unavailable folders are explicitly skipped only after policy exhaustion;
- filter changes produce a clean independent scope.

## Phase 5 — UI, diagnostics, reset, endurance, and hardening

Implement:

- Android and web playback settings;
- resolved/presented/skipped progress;
- reset active/all/history actions;
- health metrics;
- diagnostics;
- large-library performance tests;
- reboot tests;
- dynamic 24-hour simulation;
- documentation and manual acceptance test.

Review gate:

- status wording matches presented/skipped semantics;
- reset keeps current committed image and never modifies files;
- 10,000-photo simulation remains bounded;
- 100 complete folder cycles contain no within-cycle duplicate;
- each multi-photo folder completes at least 10 photo cycles without duplicate;
- source outage does not stall healthy sources;
- state and history survive reboot;
- Android 5/API 21 remains supported.

---

## 29. Unit tests

### Folder cycle

- zero folders;
- one folder;
- two folders;
- 100 folders;
- no within-cycle duplicate;
- boundary-repeat prevention;
- fixed-seed generation;
- new folder insertion;
- repeated reconciliation idempotence;
- removed folder;
- folder-level retry;
- explicit skip;
- source-level deferral.

### Photo cycle

- zero members;
- one member;
- two members;
- 10,000 members;
- no within-cycle duplicate;
- boundary-repeat prevention;
- duplicate content inside same folder suppressed;
- same content across different folders retained separately;
- new member insertion;
- repeated reconciliation idempotence;
- hidden member removal;
- quarantine;
- cycle restart.

### Reservation

- reserve leaves turn unconsumed;
- successful commit consumes once;
- release restores pending;
- failed photo removed while folder remains pending;
- stale startup reservation recovery;
- only one reservation per scope;
- commit-then-crash does not repeat.

### Collage

- requested 3, compatible 3;
- requested 3, compatible 2;
- requested 3, compatible 1 becomes single;
- first entry landscape becomes single;
- bounded lookahead 12;
- no cross-cycle fill;
- no duplicate tile;
- same-folder-only;
- failed secondary tile gives smaller collage;
- non-selected entries preserve order.

### History

- Previous once;
- Previous repeatedly;
- Next through forward history;
- Next after newest consumes one new item;
- restart with history;
- missing historical photo skipped;
- hidden historical photo skipped;
- independent playlist histories;
- reset retains backward history;
- reset-and-clear removes history.

### Scope

- rename does not change scope;
- transition change does not change scope;
- interval change does not change scope;
- collage change does not change scope;
- folder/source/Favorites/date-filter change increments eligibility revision;
- duplicate playlist creates independent scope.

---

## 30. Integration tests

- two playlists with independent cycles and histories;
- scheduled switching and restoration;
- manual override;
- SMB source disconnect/reconnect;
- WebDAV source disconnect/reconnect;
- 200-folder source outage without retry storm;
- local upload during active cycle;
- rescan during preparation;
- repeated same rescan;
- Favorites-only changes;
- Hide and Undo;
- touch Next and web Next simultaneously;
- process kill during reservation;
- process kill after commit before display;
- reboot after committed presentation;
- reset during transition;
- database migration from current schema.

---

## 31. Endurance tests

### Folder fairness simulation

Dataset:

```text
50 folders
1–500 photos per folder
100 complete folder cycles
```

Required under normal availability:

- every cycle contains each eligible folder exactly once;
- no presented folder repeats before cycle completion;
- boundary repeat avoided when folder count > 1.

With injected outages:

- each unavailable folder resolves as `SKIPPED` at most once per cycle;
- healthy folders continue;
- no folder appears in two terminal states in one cycle.

### Photo completeness simulation

For every folder:

```text
Complete at least 10 photo cycles.
```

Required:

- no member repeats within one folder photo cycle;
- cycle size equals eligible unique member count for that folder;
- boundary repeat avoided when member count > 1;
- cross-folder identical content does not collapse folders.

### Dynamic 24-hour simulation

Inject:

- folder additions/removals;
- photo additions/removals;
- repeated identical rescans;
- Favorite/Hide/Undo;
- source outages/recovery;
- playlist switches;
- periodic reboot;
- reset operations.

Required:

- no invalid reservation;
- no duplicate within active cycles;
- no retry storm;
- no stalled slideshow while healthy folders exist;
- bounded database growth;
- deterministic recovery of pending state.

---

## 32. Performance requirements

- do not load every photo from every folder into memory;
- load active folder-cycle metadata and only the required current-folder photo entries;
- keep history bounded to 100 metadata records per scope;
- cached local selection target: under 50 ms;
- network metadata selection target: under 250 ms excluding image download;
- incremental reconciliation;
- indexed queries for scope, folder, cycle, state, position, and member key;
- no shuffle or Room work on Compose UI thread;
- source outage handling must be O(number of sources), not repeated connection attempts per folder;
- cleanup must be bounded and cancellable.

---

## 33. Diagnostics

Events:

```text
SHUFFLE_SCOPE_CREATED
SHUFFLE_SCOPE_RESTORED
FOLDER_CYCLE_STARTED
FOLDER_RESERVED
FOLDER_PRESENTED
FOLDER_INSERTED
FOLDER_REMOVED
FOLDER_DEFERRED
FOLDER_SKIPPED
SOURCE_UNAVAILABLE
SOURCE_BACKOFF
SOURCE_RECOVERED
PHOTO_CYCLE_STARTED
PHOTO_RESERVED
PHOTO_CONSUMED
PHOTO_INSERTED
PHOTO_REMOVED
PHOTO_QUARANTINED
PRESENTATION_RESERVED
PRESENTATION_COMMITTED
PRESENTATION_RELEASED
RESERVATION_RECOVERED
SHUFFLE_RECONCILED
SHUFFLE_RESET
SHUFFLE_HISTORY_CLEARED
```

Never log:

- credentials;
- source passwords;
- full private paths when support-report redaction is enabled;
- bitmap content.

Use bounded IDs or hashes in support reports.

---

## 34. Acceptance criteria

- Folder-balanced shuffle is available in Android and web settings.
- Under normal source availability, every eligible folder is presented once before any folder repeats.
- An unavailable or unusable folder may be explicitly skipped once per cycle after retry policy exhaustion.
- Every folder has an independent non-repeating photo cycle.
- A folder-photo member does not repeat within its photo cycle.
- Duplicate content inside one folder is suppressed.
- Identical copies in different folders remain separate folder members.
- Boundary repeats are avoided when more than one eligible item exists.
- Folder selection is not weighted by folder size.
- A folder turn is consumed only by successful commit or explicit skip.
- Failed photo preparation retries within the same folder turn.
- Collages use one folder, bounded lookahead, and never cross a photo-cycle boundary.
- Previous does not rewind shuffle queues.
- Next follows forward history before consuming a new item.
- Persistent history survives restart.
- Shuffle progress survives restart and reboot.
- Each playlist has independent scope and history.
- Presentation-only settings do not reset shuffle state.
- Eligibility changes create a new scope revision.
- Reconciliation is idempotent.
- New folders and photos enter the remaining active cycle exactly once.
- Removed or hidden entries cannot be selected.
- Source-level outages do not cause per-folder retry storms.
- Healthy sources continue while another source is unavailable.
- Source recovery does not reset unrelated cycles.
- One active reservation per scope is enforced.
- Startup releases interrupted reservations safely.
- Reset keeps the current committed image visible and never modifies photo files.
- Database rows and histories remain bounded.
- Android 5/API 21 remains supported.
- Existing Sequential and Global photo shuffle modes remain functional.

---

## 35. Example sequence

Folders:

```text
Family: F1, F2, F3
Travel: T1, T2
Pets: P1, P2, P3, P4
```

Folder cycle 1:

```text
Travel → Family → Pets
```

Committed presentations:

```text
Travel/T2
Family/F1
Pets/P4
```

Folder cycle 2:

```text
Family → Pets → Travel
```

Committed presentations:

```text
Family/F3
Pets/P1
Travel/T1
```

Travel has now consumed both members in its first photo cycle.

Its next visit creates a new Travel photo cycle and avoids starting with `T1` when possible.

Family and Pets continue their independent unfinished photo cycles.

If Travel becomes unavailable during a later folder cycle:

```text
Travel → deferred → retry policy exhausted → SKIPPED
```

Family and Pets continue. Travel does not repeat in that cycle, and it returns after source recovery without resetting the other folders.

---

## 36. Review verdict

The task is now implementation-ready.

The governing rules are:

```text
Shuffle folders without replacement.
Inside each folder, shuffle folder-photo members without replacement.
Reserve before preparation.
Advance only after successful commit or explicit skip.
Persist generated order and entry state.
Reconcile library changes idempotently.
```

This design preserves folder fairness, prevents repeats within active cycles, survives restart, and remains robust when network sources or individual photos fail.
