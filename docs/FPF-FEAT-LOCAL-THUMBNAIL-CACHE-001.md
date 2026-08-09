# FamilyPhotoFrame — Persistent Thumbnail Cache for Local Sources

**Document ID:** FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001
**Version:** 1.1
**Status:** Phases 1–2 implemented (single-photo decode path, Settings UI, web control-panel
UI/API for enable/disable, size, Clean, Rebuild). Phase 3 (collage tiles) deliberately
deferred — see §6. Phase 4 (device validation) not yet run.
**Target baseline:** main @ `a8787c0` or later
**Tracks:** `KNOWN_LIMITATIONS.md` → "On-disk photo caching only covers remote sources"

---

## 0. Decisions locked in (v1.1)

Resolves §7's open questions with concrete requirements from the feature owner:

- **Size policy:** user-configurable, clamped to `[1 GiB, availableSpace - 2 GiB]`. The
  2 GiB reserve for the rest of the device is a hard floor, not a suggestion — the
  effective maximum recomputes against live free space, the same way
  `MediaCache.defaultMaxBytes()` already reads `context.filesDir.usableSpace`.
- **Storage format:** re-encoded JPEG, not raw/lossless bitmap dumps. A raw 4K bitmap
  can run tens of MB; JPEG keeps thousands of thumbnails inside a realistic budget,
  which matters directly for the size policy above actually being enforceable.
- **Table:** a new, separate Room table/DAO rather than extending `MediaCache`'s
  `CacheIndexEntity`/`CacheIndexDao` — keeps "avoid a network re-fetch" (remote) and
  "avoid a repeat CPU decode" (local) as separately reasoned-about budgets, since they
  have different growth characteristics (remote is bounded by what's actively selected
  for playback; local can in principle cover an entire large library).
- **Controls required on both surfaces** (on-device Settings and the web control panel):
  - Enable/disable toggle.
  - Max size control, respecting the size policy above.
  - **Clean** — deletes all cached thumbnails immediately (passive: cache repopulates
    lazily as photos are shown again, same as it would from empty on first run).
  - **Rebuild** — Clean, then actively walk the local photo index and populate the
    cache now, as a bounded background job (not waited-for by the caller), instead of
    waiting for photos to be shown again naturally. Distinct from Clean specifically
    because eager repopulation is a meaningfully heavier operation the user should be
    able to trigger deliberately, not get for free every time they just want to clear
    space.

---

## 1. Objective

Give local SAF-folder and bundled-fallback/local-upload photos a persistent, on-disk decoded thumbnail cache — closing the one asymmetry left in the caching model, where remote sources (`MediaCache`) never repeat a network fetch but local sources repeat a full CPU decode on every single display, including the *same* photo shown again in a later shuffle cycle.

## 2. Current state (verified against the code, not assumed)

- `SlideshowViewModel.resolveModel()` sends SAF photos straight to Coil as a `content://` `Uri`, and fallback/local-upload photos straight to Coil as a `File` — neither path touches `MediaCache`. Only sources with `needsCache == true` (SMB/Synology/WebDAV) are routed through it.
- Every decode request (`SlideshowPreparation.kt`, `SlideshowBackdrop.kt`) explicitly sets `.memoryCachePolicy(CachePolicy.DISABLED)` on Coil, because the decoded `Bitmap` is deliberately owned solely by `PreparedSlide` — Coil's own memory cache is configured in `ServiceLocator` but never actually populated on the real decode path (`imageCacheKb` in diagnostics is always `0` for this reason). So there is currently **no cache of any kind** — memory or disk — for a local photo once its `PreparedSlide` is retired.
- `StableId.of(sourceId, normalizedPath, sizeBytes, lastModifiedEpochMs)` (`util/StableId.kt`) is already computed identically for SAF items as it is for remote ones (`SafPhotoSource.kt`), and is size/mtime-sensitive, so it already behaves as a correct cache-invalidation key if a local file changes.

## 3. Why this is a different problem than `MediaCache`, not a copy of it

`MediaCache` exists to avoid re-fetching **original bytes** over a network. For a local SAF file, the bytes are already on local storage — copying them again to app-private storage would just waste disk space for no benefit. The actual repeated cost for local photos is **CPU decode + downscale time**, paid in full on every display, with no reuse across:

- the next shuffle cycle showing the same photo again,
- manual Previous/Next revisiting an already-seen photo,
- portrait-collage tiles decoded at their (smaller) tile size, which currently get thrown away just like full-frame decodes.

So the right artifact to persist is the **already-downscaled decoded thumbnail** (at or near current display target size), not a second copy of the original file.

## 4. Design sketch

- A new small component (working name `LocalThumbnailCache`), disk-backed under app-private storage, keyed by `StableId` — same key scheme as `MediaCache`, so no new ID logic is needed.
- Populated the same way `MediaCache` already validates its writes: decode → verify → atomic temp-file-then-rename commit, so a cancelled/corrupt write never becomes a valid cache entry.
- Cache key must include the **target decode dimensions** (or a coarse size bucket) alongside `StableId`, since the same photo can legitimately need different decode sizes (full-frame vs. a portrait-collage tile vs. a different `memoryProtection.decodeScale` under memory pressure). Storing one entry per (stableId, sizeBucket) avoids serving a wrong-resolution thumbnail.
- Eviction: LRU, same shape as `MediaCache.evictIfNeeded()`, with the currently-displayed and next-preloaded photo protected from eviction — reuse that existing policy rather than inventing a second one.
- Integration point: `SlideshowPreparation.kt`'s `prepareSlide()`/`build()` path gains a cache-check-then-populate step for local-source items, symmetric with how `MediaCache.resolve()` already gates the remote path. `resolveModel()` itself likely does **not** need to change — the cache check belongs at the decode step, not the model-resolution step, since what's being cached is the *decoded* output, not an alternate model to hand to Coil.
- Respect the existing memory-pressure system: under `PlaybackMemoryLevel.RECOVERY`/`CIRCUIT_OPEN`, either skip populating the cache (to avoid adding disk I/O during a pressure event) or skip reading it (if decoding a smaller size than what's cached would be cheaper) — needs a concrete decision in Phase 1, not left implicit.

## 5. Explicit non-goals

- This does **not** change `MediaCache` or the remote-source path at all.
- This does **not** cache full-resolution originals for local sources — only decoded/downscaled thumbnails at the sizes the slideshow actually displays.
- This does not attempt to pre-warm the cache ahead of first display (e.g. during initial folder scan) — populate lazily, on first real display, same as `MediaCache` does today.

## 6. Phased delivery plan

### Phase 1 — Design decisions and cache primitive

- Decide the exact cache key shape: `StableId + decode width/height (or a small enum of size buckets: FULL, COLLAGE_TILE, DECODE_SCALED)`.
- Decide the on-pressure behavior from §4's last bullet.
- Implement `LocalThumbnailCache` as a standalone, independently unit-testable class (pure key/eviction logic testable without Android, mirroring how `MediaCache`'s policy pieces are tested today) plus the disk I/O shell.
- **Gate 1:** pure-logic unit tests for key derivation and eviction pass; no wiring into the real decode path yet.

### Phase 2 — Wire into the decode path

- Integrate into `SlideshowPreparation.kt` for the single-photo path first (not collage tiles yet).
- Add diagnostics counters analogous to `MediaCache`'s (`imageCacheKb`-style visibility) so a cache hit vs. miss is observable in the existing diagnostics bundle, not a black box.
- **Gate 2:** an androidTest confirms a local photo's second display within the same session is a cache hit (no full decode), using a fake/small test folder.

### Phase 3 — Collage tiles and portrait-panel decodes

- Extend the same cache to portrait-collage tile decodes, which currently re-decode on every collage composition even more frequently than single photos.
- **Gate 3:** collage re-composition of the same photo set within a session shows cache hits for unchanged tiles.

### Phase 4 — Device validation

- Run on the reference low-end device (per `PERFORMANCE_BUDGET.md`'s existing procedure) with a local folder large enough to complete a full shuffle cycle, confirming: lower average decode time on second-cycle displays, no unbounded disk growth, and no interaction with the existing memory-pressure circuit breaker that wasn't accounted for in Phase 1.

## 7. Open questions — resolved in v1.1

1. ~~Shared table vs. separate?~~ **Separate table.** See §0.
2. ~~JPEG vs. raw?~~ **JPEG.** See §0.
3. ~~Own size budget?~~ **Yes — user-configurable `[1 GiB, availableSpace - 2 GiB]`.** See §0.

## 8. Remaining open question

4. "Rebuild" walks the entire local photo index and decodes every eligible photo eagerly.
   For a very large library this could take a long time and compete with live playback
   decode for CPU/IO. Phase 2 implements it as a cancellable, lower-priority background
   job that yields to playback and respects the existing memory-pressure signals — if
   that's not aggressive enough (or too aggressive) once it's actually running on
   hardware, the throttling strategy is the thing to revisit, not the existence of the
   feature.
