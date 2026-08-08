# FamilyPhotoFrame v51.4 — OOM and Folder-Balanced Shuffle Fix

**Application version:** `0.11.4-prerelease` (`versionCode 18`)
**Source baseline:** FamilyPhotoFrame v51.3 legacy refactor

## Scope

This maintenance release addresses the two release-blocking defects reproduced in the
uploaded diagnostics log:

1. Java-heap exhaustion after approximately two hours of continuous slideshow playback.
2. Folder-balanced shuffle repeatedly selecting the same folder despite a large eligible
   folder pool.

## Memory and web-preview corrections

- Web previews are generated only while an authenticated browser has been active during
  the preceding 90 seconds. Merely enabling Web Control no longer creates a second image
  pipeline for every slide.
- Preview generation is keyed only by committed presentation content and display settings;
  transition metadata changes no longer trigger duplicate JPEG generation.
- `WebPreviewStore` retains one immutable JPEG frame and no longer copies the complete
  byte array on publish and on every status/presentation/preview request.
- Replacing or stopping Web Control releases the retained preview.
- Preview output and temporary blur bitmaps are released from `finally` blocks.
- Transition-blur and image decode allocation failures are converted into controlled
  failures rather than escaping as an uncaught `OutOfMemoryError`.
- An image decode OOM clears the Coil memory cache and the retained web preview and writes
  a structured `DECODE_OOM_RECOVERY` diagnostic event.

## Folder-balanced selection corrections

- `LEAST_RECENT_RANDOM` now uses a strict outer folder cycle. Every eligible folder is
  selected once before any folder repeats; least-recent random selection remains active
  inside the selected folder.
- `SHUFFLE_NO_REPEAT` now uses two independent levels of state:
  - an outer randomized folder cycle;
  - one no-repeat photo queue per folder.
- Collage companions are consumed from their folder's photo queue without consuming
  additional folder turns.
- The preloaded `next` photo is reserved and subsequently displayed instead of being
  silently consumed and skipped by a second selection call.
- Folder and photo queue state is reset when the source pool, selected folders, favorites
  filter, cache-only policy, or selection mode changes.

## Verification completed

- Real `SlideshowEngine` and `PhotoDao` type-check harness: PASS.
- Folder cycle: 106 eligible folders visited exactly once in the first 106 turns: PASS.
- Folder-cycle boundary does not repeat the previous folder: PASS.
- Per-folder photo cycle does not repeat a photo until that folder's pool is exhausted: PASS.
- Room SQL preparation: 60 queries, all PASS.
- Room migration verification: PASS.
- Existing pure-logic regression suite: PASS.
- Project consistency checker: PASS.
- Standalone `WebPreview` and `WebSecurity` Kotlin compilation: PASS.

## Remaining release gate

A full Android Gradle build could not be executed in the isolated environment because the
Gradle 8.9 wrapper distribution was not cached and network access to `services.gradle.org`
was unavailable. Before installing the release, run:

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest assembleDebug
```

Then perform a six-hour device soak with Web Control both idle and actively displaying
previews. Heap usage after garbage collection must stabilize, and the diagnostic log must
contain no uncaught `OutOfMemoryError`.
