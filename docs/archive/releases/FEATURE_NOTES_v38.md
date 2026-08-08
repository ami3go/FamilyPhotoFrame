# Implementation Notes — five roadmap features

Implemented after the v37 review pass. Each item was chosen to be verifiable without
Gradle, an Android SDK, or NAS hardware, none of which this environment has.

## 1. Shuffle with no repeats (spec §9.6)

`domain/randomize/PlaybackQueue.kt` — pure Kotlin, no Room/Android/clock.

`LeastRecentRandom` samples a DAO-supplied *window*, which biases away from recent
photos but can never promise "every photo before any repeat": a photo just outside the
window can come back early. The queue takes the whole displayable id pool and consumes
it, which is what makes the guarantee hold.

Design points worth stating:

- **Ids, not rows.** Materializing every row to build a cycle would defeat §5.1
  ("never enumerate storage"). A `Long` per photo is affordable; the row is fetched by
  `byId` only when the photo is actually shown.
- **A rescan does not restart the cycle.** `sync` keeps already-shown photos shown, drops
  ids that vanished, and folds new ids into the *current* cycle. On a frame whose NAS is
  rescanned while it plays, restarting would visibly reshow photos.
- **Seam handling.** The first pick of a new cycle is nudged away from the last pick of
  the old one by a swap (deterministic, cannot loop). With a one-photo pool the repeat
  stands, because there is nothing else to show.
- **Bounded miss budget.** A queued id whose row was deleted or suppressed since is
  skipped, up to `MAX_QUEUE_MISSES = 8`. Each miss is a DB round-trip, so spinning here
  would be worse than re-syncing on the next selection.

**A bug was caught by these tests, not by review:** `reset()` cleared progress but left
`pool` intact, so the following `sync()` short-circuited on an unchanged pool, left the
queue empty, and made the next `next()` count a phantom completed cycle. `reset()` now
clears `pool` too. This is the argument for the harness in miniature — the logic read
correctly and was wrong.

## 2. Date-taken ordering

`SelectionMode.DATE_TAKEN_NEWEST` / `DATE_TAKEN_OLDEST`, served by two new DAO queries
and replayed through the same `PlaybackQueue` in sequential mode.

Undated photos sort **last** (`ORDER BY (dateTakenEpochMs IS NULL) ASC, ...`) rather than
being interleaved as epoch 0, and are ordered among themselves by file-modified time.
Otherwise every photo lacking EXIF would clump at one end of the timeline and shuffle
position between rescans.

`Room` cannot parameterize `ORDER BY`, hence three sibling queries rather than one
with a sort argument.

## 3. Stale-cache playback (spec §9.3 `on_unreachable`)

When a NAS drops, `UnreachablePolicy.STALE_CACHE` keeps playing the NAS photos already in
`MediaCache` instead of dropping to bundled samples — which is what a family actually
wants during a router reboot.

- Backed by **`photos.cacheKey`**, a column that has been in the schema with an index
  since v2 and never had a writer. `MediaCache` now stamps it on download and clears it
  on eviction/clear. **No migration is needed**, and the "which photos can I show
  offline?" query is a single indexed predicate rather than a join against `cache_index`
  with a thousand-element `IN (...)` list (which would also hit SQLite's variable limit).
- `MediaCache.getIfCached()` never downloads. With the source known down, attempting a
  fetch would buy nothing and stall the slideshow for a connect timeout *per photo*.
- **Falls through to samples when the cache is cold**, so the policy can never leave the
  frame with nothing to show.
- The three previously duplicated "NAS is down" sites (SMB start-up, Synology start-up,
  and the shared recovery loop) now call one `configureForUnreachable` helper. Recovery
  clears stale mode, so live fetching resumes at full quality.
- The UI says so quietly (`msg_stale_playback`) rather than letting an offline NAS look
  like everything is fine.

## 4. Curation: favourite / hide (spec §9.4)

Activates `isFavorite` and `isHidden` — also present with indexes since v2, also never
written. Again no migration.

- **Curation is written straight to the index, not sent through the engine's command
  channel.** That channel is `Channel.CONFLATED`; a favourite queued behind a `Next`
  would be silently dropped. Conflation is right for "advance the slideshow" and wrong
  for "the user marked this photo."
- **Enabling favourites-only with zero favourites is refused**, on-device *and* over the
  web, with a message. Otherwise the engine finds an empty primary pool and quietly drops
  to samples, which reads as a bug from across the room.
- Un-favouriting the photo on screen while in favourites-only mode advances, rather than
  leaving a now-ineligible photo up.
- **Hide is reversible** (`unhideAll`) and deliberately *not* bound to a bare D-pad key —
  it is disruptive enough that an accidental press should be hard. Favourite is on
  `F` / Channel-Up / Bookmark; hide is on `H`.

## 5. Web parity for overlay position and opacity

The web page now exposes all seven overlay anchors, the shared opacity, and the scrim,
plus the new playback settings.

- **All enum-valued fields are validated before anything is written**, so a typo in one
  anchor cannot leave the other overlays half-updated.
- The 9-grid option list is generated from one array in the page, in the same order as
  the `OverlayPosition` enum, so the two surfaces cannot drift.
- Unchanged: the web API still accepts no password, no 2FA code, and no certificate pin
  (§15.6). Per-photo favourite/hide stays on-device because it acts on the photo
  currently displayed, and the web page has no photo browser.

## Verification

Passing: `check-consistency.py`, Kotlin parse of all main sources, and the full
pure-logic harness including **22 new PlaybackQueue assertions**. A mirrored
`PlaybackQueueTest` was added so the same guarantees run under
`./gradlew testDebugUnitTest` once a real toolchain is available.

One compile break was caught by inspection rather than tooling: adding a parameter after
`MediaCache`'s `maxBytesProvider` would have stolen the trailing lambda in
`MediaCacheTest`. The new parameter was moved ahead of it.

**Not verified.** No Gradle build, no device, no NAS. Specifically unproven:

- the new Room queries against a real annotation processor — the highest-risk item here;
- `photos.cacheKey` staying consistent with `cache_index` across real eviction;
- stale-cache playback across a genuine NAS outage and recovery;
- whether `KEYCODE_F` / `KEYCODE_CHANNEL_UP` / `KEYCODE_BOOKMARK` are reachable on the
  target remote — this needs the hardware, and the binding may need revisiting;
- Compose layout of the new settings rows on a real screen.

Run `./gradlew --no-configuration-cache clean testDebugUnitTest assembleDebug` first;
that is where the Room queries will be validated.
