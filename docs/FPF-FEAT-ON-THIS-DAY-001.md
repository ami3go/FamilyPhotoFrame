# FamilyPhotoFrame — "On This Day" Memory Playlist

**Status:** Phases 1, 2, and 4 implemented (pure selection/schedule logic, single-photo
playback wiring, and Settings UI + web parity — enable toggle, times/day, duration,
minimum years ago, and a "Preview now" button on both surfaces). Phase 3 (multi-year
collage) and Phase 5 (device validation) remain. Also fixed a pre-existing bug found
while wiring this in: `onSettings()` never mirrored `localThumbnailCache` into
`SlideshowUiState`, so that toggle/slider always showed stale defaults regardless of the
persisted value — fixed alongside the new `onThisDay` mirroring, since both go through
the same `_state.update` block.

## 0. Decisions locked in

1. **Exact calendar day only** — no ±N day tolerance window. Consequence: most days for
   a modest library will have zero matches, so **"skip silently when nothing qualifies"
   (§5.2) is promoted from a brainstormed idea to a required Phase 1 behavior**, not an
   optional nicety. `OnThisDaySettings` does not expose a day-window field at all in v1.
2. **Default cadence: 3 times a day, 5 minutes each** (`timesPerDay = 3`,
   `durationMinutes = 5`).
3. **Never preempts an active override.** If a manual playlist activation or an active
   `PlaylistScheduleRule` window is already in effect when an On This Day window comes
   due, that occurrence is skipped entirely (not deferred/retried) — the next scheduled
   occurrence gets its own chance. This reuses the exact `overrideActive` check already
   computed in `restartPlaylistScheduleWatcher()`, so no new "is something else active"
   logic needs to be written.

## 1. Objective

Periodically during the day, the frame briefly switches to a small, auto-assembled set
of photos taken on this same calendar day in previous years ("3 years ago today"),
optionally shown as a side-by-side collage with one tile per year, each tile labelled
with its source folder name. After a short interval, playback returns to whatever was
showing before.

## 2. Current state (verified against the code, not assumed)

- `SlideshowPlaylist` (`data/settings/AppSettings.kt`) filters by `sourceIds`,
  `folderNames`, `favoritesOnly`, `localUploadsOnly` — all **static** predicates over
  stored columns. There is no date-relative predicate ("taken on month/day X, any year").
- `PlaylistScheduleRule` activates one playlist for a **contiguous local-time window**
  (start/end time, days of week) — built for "playlist X plays from 08:00–20:00", not
  for "insert a 5-minute interlude every few hours."
- `RescanSchedule` / `SleepSchedule` (`domain/schedule/`) already solve a related but
  different problem: pure wall-clock "is this recurring thing due now" arithmetic with a
  grace window, so a sleeping/off device catches up instead of silently skipping a day.
  This is the right template for "periodically during the day," not the schedule-rule
  window model.
- `dateTakenEpochMs` is a nullable indexed column on `photos`
  (`data/db/PhotoItemEntity.kt`); `DATE_TAKEN_NEWEST`/`OLDEST` ordering already exists,
  but nothing groups photos by "same month/day across years."
- Portrait collage (`PortraitCollagePolicy.kt`, wired in `SlideshowPreparation.kt`)
  already composites 2–3 photos into one frame — but selection is same-folder,
  closest-in-time, and the *reason* for collaging is portrait-orientation fallback, not
  "one representative photo per year." It's the closest existing rendering template, not
  a reusable selection mechanism.
- The folder-name overlay (`overlays.folderShow` / `folderPosition`,
  `SlideshowViewModel.setShowFolder`/`setFolderPosition`) renders one folder name for the
  single current photo. There is no per-tile equivalent for a multi-photo collage.
- Manual playlist activation already has a bounded-duration override:
  `PlaylistSettings.manualOverrideUntilEpochMs`, set by `playlistAction 'play'` with an
  `overrideMinutes` body field (`WebServerController.kt`), and unwound automatically by
  `restartPlaylistScheduleWatcher()` in `SlideshowViewModel.kt` once the timestamp
  passes. This is a working "temporarily switch playlists, then revert" mechanism
  already in production — On This Day should reuse it, not invent a second one.

## 3. Why this needs new mechanisms, not just a new playlist row

- "Same day-of-year, any year" isn't a fixed predicate like a folder name — the eligible
  set changes every 24 hours and must be derived from `dateTakenEpochMs` at query time
  (`strftime('%m-%d', ...)` in SQLite), not stored.
- "Periodically during the day" is a recurring, short interruption, not a scheduled
  window — closer to `RescanSchedule`'s due/grace model than
  `PlaylistScheduleRule`'s start/end model.
- The multi-year collage groups *one photo per year*, a variable count (however many
  years have a match, capped), unlike the portrait collage's fixed 2–3 same-subject
  tiling driven by orientation.

## 4. Design sketch

### 4.1 Selection: today's "on this day" set

- New Room query, e.g. `onThisDayCandidates(monthDay: String, sourceIds, minYearsAgo,
  allowHeif, limit)`, matching
  `strftime('%m-%d', dateTakenEpochMs / 1000, 'unixepoch', 'localtime') = :monthDay`
  exactly (§0.1 — no day-window tolerance), `isHidden = 0`, `decodeFailureCount` under
  threshold, HEIF policy respected — the same guard clauses `collageCandidates` already
  uses.
  - This can't use an index (a function on the column defeats one), but it runs at most
    a few times a day against a per-source photo count in the thousands, not millions —
    a full scan is fine; no index needed.
- Group results by calendar year (derived from `dateTakenEpochMs`), one representative
  photo per year (closest to today's exact date, favorites preferred on a tie), capped
  at a max tile count (default 6).
- Compute once per local calendar day and cache the result (id list keyed by "computed
  for date X") rather than re-querying Room on every periodic trigger.
- `minYearsAgo` (default 1) excludes "this year" from counting as a memory.

### 4.2 Playback mechanism: periodic temporary insertion

New `OnThisDaySettings` (shape, not final):

```kotlin
data class OnThisDaySettings(
    val enabled: Boolean = false,
    val timesPerDay: Int = 3,        // default per §0.2; user-configurable
    val durationMinutes: Int = 5,    // default per §0.2; user-configurable
    val minYearsAgo: Int = 1,
    val collageMode: Boolean = true, // one-per-year collage vs. one-at-a-time
    val lastTriggeredEpochMs: Long = 0L,
)
```

- New pure `OnThisDaySchedule` object mirrors `RescanSchedule`: divide the day into
  `timesPerDay` evenly spaced windows, reuse the same is-due + grace-window arithmetic
  so a device that's asleep at the exact trigger time still catches up once, rather than
  skipping the day — but never fires more than once per window, so a multi-day-off catch
  up doesn't burst-fire. Before firing, it also checks `overrideActive` (§0.3); if
  something else already has the floor, this occurrence is skipped, not deferred.
- The pool is modeled as a **fourth built-in playlist**
  (`PlaylistSettings.PLAYLIST_ON_THIS_DAY`), joining `PLAYLIST_ALL`,
  `PLAYLIST_FAVORITES`, and `PLAYLIST_RECENT_UPLOADS`. Unlike those three, though, its
  photo set can't be expressed as a `SlideshowPlaylist` filter field (`sourceIds`/
  `folderNames`/`favoritesOnly`) — the pool is an explicit, app-computed id list that
  changes daily. **As actually implemented:** a new `SelectionMode.ON_THIS_DAY` value
  plus `SlideshowEngine.setOnThisDayPool(ids)` (a plain field the engine's `pickFrom()`
  reads instead of querying Room, reusing the existing `PlaybackQueue` cycling — the same
  mechanism `SEQUENTIAL`/`DATE_TAKEN_*` already use for an ordered id list). The built-in
  playlist's `selectionMode = ON_THIS_DAY` is what switches the engine into reading that
  pool once activated. The playlist itself is created with `enabled = false` so it never
  appears in the ordinary playlist picker/schedule-rule UI (which filters to `enabled`
  playlists) or via the generic "play any playlist" API path (which requires `enabled`)
  — the *only* way to activate it is `SlideshowViewModel.triggerOnThisDay()`, which sets
  `activePlaylistId` directly, bypassing that check by design.
  `SelectionMode.ON_THIS_DAY` is also rejected by `WebSettingsPatchApplier` as a direct
  value for the general `selectionMode` field, so it can't be set (accidentally or
  otherwise) with an empty pool behind it.
- When due: push the pool into the engine, then set
  `activePlaylistId = PLAYLIST_ON_THIS_DAY` and
  `manualOverrideUntilEpochMs = now + durationMinutes` in the same settings update —
  exactly like the existing `playPlaylist()` override path.
  `restartPlaylistScheduleWatcher()` already reverts to the schedule/default playlist
  once that timestamp passes, and switching `activePlaylistId` away from
  `ON_THIS_DAY` naturally switches `selectionMode` back too — no new revert logic
  needed. A useful side effect already in the engine: changing `selectionMode` via
  `setPlayback()` triggers an immediate `Command.Reselect`, so both the scheduled
  trigger and "Preview now" show a memory photo right away rather than waiting for the
  next scheduled advance.
- "Preview now" (Phase 2) is wired as a `preview_on_this_day` maintenance action
  (`WebServerController`/`FrameControls`, same pattern as the thumbnail cache's
  clean/rebuild actions) plus a `SlideshowViewModel.previewOnThisDay()` entry point —
  both call the same `triggerOnThisDay(preview = true)`, which bypasses the
  enabled/schedule/override checks the automatic path applies.

### 4.3 Collage mode: one tile per year

- New `OnThisDayCollagePolicy` (parallel to, not a branch of, `PortraitCollagePolicy` —
  the selection and layout rules are unrelated: year-grouping vs. orientation fallback).
  Variable-size grid sized to however many distinct years matched today (2 up to the
  cap), not the fixed 2/3-column layout portrait collage uses.
- Each tile: the photo, center-cropped to its cell (same crop approach the portrait
  collage tiles already use), plus a caption strip showing **folder name and year**
  together (e.g. "Living room · 2019"). Folder name alone is ambiguous once several
  tiles are on screen at once — the year is what makes the "different years" framing
  legible, so it's bundled in even though only the folder name was explicitly asked for.
- If `collageMode = false`, or only one year matched today, the interlude just plays as
  a normal one-photo-at-a-time sub-playlist using the existing single-photo path —
  collaging only makes sense with 2+ distinct years.

### 4.4 Settings UI + web parity

Following this session's established pattern — every feature gets both surfaces:

- **Android:** a new "On this day" card (`SettingsPlaybackSections.kt` or a new
  schedule-adjacent section) — enable toggle, times/day, duration, min-years-ago,
  collage on/off, and a **"Preview now"** button.
- **Web:** the same fields through the existing settings-patch pattern
  (`WebSettingsJson`/`WebSettingsPatchApplier`), plus a "Preview 'On this day' now"
  maintenance-style action so it can be verified without waiting for the next scheduled
  trigger.
- A short on-screen notice when an interlude starts (reusing the existing
  `transientNotice` pattern from the cache-cleared banner), e.g. "Showing memories from
  2019, 2021, 2023" — this also gives the folder/year captions context.

## 5. Other useful options (brainstormed — not decided, for discussion)

1. **"Preview now" trigger** (above) — test/enjoy it on demand, independent of schedule.
2. **"X years ago" overlay text outside collage mode too** — reuses the existing
   `OverlayPosition` anchor system already used for clock/date/caption.
3. **Hidden photos/folders stay excluded** — already implicit via `isHidden = 0`, worth
   stating explicitly since it's an easy regression to introduce later.
4. **Early-exit control.** D-pad/web "next" during an interlude should behave like
   normal advance (move to the next memory photo); a separate "end on this day now"
   playback control lets a viewer skip back to the regular slideshow without waiting out
   `durationMinutes`.
5. **Must not fire during `sleepEnabled` quiet hours** — same as any other playback
   activity; no special-casing needed, just don't forget to check it.
6. **Best-photo-per-year tie-break.** If a year has multiple matches inside the day
   window, prefer favorites, then the closest exact date — same `ORDER BY` idiom
   `collageCandidates` already uses, not new logic.
7. **A browsable "On this day" view in the web panel**, independent of the live trigger
   — look at past/other days' assembled sets on demand, similar to opening "memories" in
   a phone gallery app rather than only seeing it once it auto-fires.
8. **Source scope = whatever the engine already resolves as active**, not a separate
   scoping knob — reuses `sourceIdsFor(settings)` rather than adding new configuration
   surface.
9. **Midnight/sleep edge case.** If the frame is asleep exactly at local midnight,
   "today's" pool must be recomputed at first wake, not served stale from yesterday —
   fold "computed for date X" into the same due-check `OnThisDaySchedule` already does,
   rather than a second cache-invalidation mechanism.

## 6. Non-goals (v1)

- No ML-based "best photo" scoring (faces, smiles, composition) — deterministic ordering
  only (favorites, then closest date).
- No cross-device/shared "family memories" — single-frame local feature, like everything
  else in the app.
- No dedicated curation UI beyond the existing favorite/hide actions.

## 7. Phased delivery plan

- **Phase 1 — pure selection logic.** `OnThisDaySchedule` (wall-clock due/grace,
  mirroring `RescanSchedule`) plus the Room query, both unit-tested; no playback wiring.
  **Gate:** pure-logic tests pass for exact-day matching, year-grouping, the
  overrideActive skip (§0.3), and due/grace timing including the midnight-catch-up case.
- **Phase 2 — single-photo playback wiring.** Dynamic built-in playlist id, override-
  duration reuse, "Preview now" action. **Gate:** triggering (via preview) switches
  playback to the assembled set and reverts automatically after `durationMinutes`.
- **Phase 3 — collage rendering.** Multi-year tiles with folder/year captions.
  **Gate:** a day with 2+ eligible years renders as a labeled multi-tile collage.
- **Phase 4 — Settings UI + web parity.** Both surfaces, per this session's convention.
- **Phase 5 — device validation.** Confirm on the reference low-end device that a
  collage frame's memory/decode cost doesn't regress the existing performance budget.

## 8. Open questions — resolved

1. ~~Day tolerance?~~ **Exact calendar day only.** See §0.1.
2. ~~Priority vs. existing overrides?~~ **Never preempts.** See §0.3.
3. ~~Default cadence?~~ **3 times a day, 5 minutes each.** See §0.2.

## 9. Remaining open question

4. **Collage cap:** how many year-tiles at once feels right before it gets cluttered?
   Proposed default: 6 (oldest-through-newest matching years, capped). Since day
   matching is exact-day-only (§0.1), most days will have 0–2 matches in practice; the
   cap mainly matters for libraries with many years of history on culturally
   photo-heavy dates (birthdays, holidays).
