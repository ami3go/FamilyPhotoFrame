# Collage pipeline: lookahead preparation (build 26.99.1)

## Symptom

Collages were configured but never appeared on the V80 (Android 5.1, SMB library of
~29k photos, folder-balanced shuffle). In build 26.97.1/26.98.1 field logs every one of
1,000+ presentations was `COLLAGE_FALLBACK_SINGLE reason=selected_anchor_transfer`.

## Root causes

Four independent gates each made a collage impossible on this device:

1. **No lookahead in folder-balanced shuffle.** `setCurrent` never pre-selected a `next`
   presentation in `FOLDER_BALANCED_SHUFFLE` mode, so nothing was ever preloaded. Every
   presentation was prepared only once it was already due.
2. **Low-memory tier disabled preload outright.** The V80 is `DeviceMemoryTier.LOW`
   (100 MiB heap class, 1.9 GB RAM). `allowNextPreload` required `!lowMemoryTier`, and
   `maxCollagePhotos` was capped at two even at `NORMAL`.
3. **False memory pressure.** The native-growth trend took its baseline from the first
   sample at process start (~1.6 MiB), so normal warm-up to ~23 MiB latched `CRITICAL`
   after 15 minutes. Separately, process pressure compared *total* PSS — including ~50 MiB
   of clean, shared dex/oat/.so mappings — against the 100 MiB memory class, holding the
   device at `GUARDED`. Both levels disable preload and cap collage size.
4. **Anchor transfer forces a single.** With no preload, the anchor of nearly every slide
   (≈300 of 29k photos are cached) had to be fetched inside the selected deadline, and
   `SelectedAnchorPresentationPolicy` then (correctly) chose a single photo.

## Changes

- **Engine lookahead** (`SlideshowEngine.reserveFolderLookahead`): once the current
  presentation has rendered and its turn is committed, the next folder-balanced turn is
  reserved before the dwell countdown and published as `next` with
  `nextReservedCandidateIds`. `ShuffleRepository.reserve` returns an existing reservation
  for the scope unchanged, so the ordinary forward pick later receives exactly this turn;
  history replay, one-folder preview, and an uncommitted current turn skip lookahead.
  Reselect/reset release it. `SHUFFLE_LOOKAHEAD_RESERVED` records each lookahead.
- **Candidates for the preloaded turn** come from `nextReservedCandidateIds`, so a preload
  can only compose photos the reservation owns (commit validation is unchanged).
- **Memory policy**: one-ahead preload and three-photo frames are allowed on the low tier at
  `NORMAL` (tiles decode at tile size, so a collage costs one frame's pixels). The first
  native-growth window after process start is warm-up and only rebaselines. Process
  pressure uses app-owned PSS (Java + native heaps), both in the guard and `HEAP_SAMPLE`.
- **Preparation** (`prepareSlide`): every collage member is resolved before any decode, the
  anchor tile is reused across companion retries when its tile size is unchanged, and a
  presentation that is already due resolves companions from the media cache only
  (`ModelResolutionRequest.cacheOnly`). A cache miss is skipped silently; it is not a photo
  fault, starts no NAS transfer inside the anchor's deadline, and can no longer be
  attributed to the anchor's transfer trace (which previously could retire a healthy anchor).
- **Preload handoff** (`SlideshowScreen`): the next presentation is prepared in a job owned
  outside its `LaunchedEffect` and admits its own result to the slide registry. When the
  dwell ends before a slow preload completes, the selected effect joins that job (bounded by
  the selected deadline) instead of cancelling it and re-fetching from scratch.

## First V80 run (26.99.1)

After the cold-start slide, every presentation was a collage composed during the previous
dwell (`COLLAGE_READY` 6–11 s before `SLIDE_SELECTED`, 3.9–7.0 s preparation), with mixed
two- and three-column layouts and crossfades starting ~1 s after selection.
