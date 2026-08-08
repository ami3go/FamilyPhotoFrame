# FamilyPhotoFrame v52.6 — legacy HEIF playback crash fix

## Diagnostic conclusion

The supplied bundle contains two process sessions from an ONDA V80 PLUS running
Android API 22 and FamilyPhotoFrame `0.12.2-prerelease`.

- SMB indexing completed normally with 28,133 photos and zero scan errors. The failure
  was not caused by SMB discovery, authentication, or index rebuilding.
- Playback recorded 234 permanent HEIC/HEIF capability failures over 357.7 seconds.
  Android 5.1 has no platform HEIF decoder, but those rows were still eligible for
  shuffle and portrait-collage selection.
- The first affected portrait fallback spent 12,053 ms testing same-folder candidates,
  rejected the HEIC companions, and displayed JPG photo 20139 as a single blurred
  portrait fallback.
- Its crossfade completed at `1785735707439`, `SLIDE_RENDERED` was recorded at
  `1785735707476`, and the main thread terminated with `RuntimeException` at
  `1785735707496` — 20 ms after render. The next process recorded the previous crash.
- Just before that fallback, the Java heap was 76,961 KiB of 102,400 KiB and the image
  cache held 30,505 KiB. The repeated unsupported selections created avoidable work and
  memory pressure, although the terminal exception was `RuntimeException`, not
  `OutOfMemoryError`.

The original recorder intentionally omitted exception messages and stack frames, so the
exact Canvas message is not present in the bundle. Source review found the matching
failure mechanism: blurred bitmaps handed to Compose were manually recycled when the
transition branch changed to the committed branch. Android explicitly warns that drawing
a bitmap after `recycle()` raises `Canvas: trying to use a recycled bitmap`. The 20 ms
render-to-crash interval and the fact that this was the first blurred single-photo
fallback make this the high-confidence crash cause.

## Implemented correction

1. Runtime playback queries now exclude HEIC/HEIF on API 21–25 before folder, photo,
   history, preview, collage, or date-order selection. The files remain indexed and
   become playable on HEIF-capable Android versions.
2. The platform format capability is part of the persistent shuffle identity, preventing
   an incompatible reservation from being restored after a device/capability change.
3. The expensive all-folder eligibility snapshot is reused between selections and is
   explicitly invalidated after index/configuration changes. The supplied log showed all
   250 selections above the 250 ms target (332.6 ms mean, 795 ms maximum); repeated HEIC
   rejection no longer repeats that global query.
4. Compose-visible backdrop and transition-blur bitmaps are no longer manually recycled.
   Their references are dropped and normal garbage collection owns reclamation. Temporary
   off-screen bitmaps that are never displayed remain explicitly recyclable.
5. Health counts distinguish runtime-playable photos from indexed-but-unsupported files.
6. Crash diagnostics now retain privacy-safe cause and class/method origin fields, without
   exception messages, paths, URIs, credentials, or photo names.
7. The slideshow's first-run/recovery/status surfaces were split into their own file to
   keep the render coordinator below its existing maintainability limit.

## Regression coverage

- Added platform-capability and capability-signature unit checks.
- Added Room tests proving legacy playback excludes HEIC by extension and MIME while
  retaining every row in the index.
- Added folder-balanced tests proving HEIC-only folders and HEIC collage members never
  enter a legacy-device reservation.
- Updated transition contracts to reject manual recycling of display-bound bitmaps.
- Room SQL preparation, migration replay, Kotlin structure, engine/DAO type checking,
  pure logic, transition diagnostics, and folder-balanced endurance checks pass.
- The final modeled dynamic shuffle run committed 1,424 presentations with no
  folder/photo repeat violations.

## Remaining device verification

Install `0.12.4-prerelease` on the API 22 frame, clear old diagnostics, and run the SMB
library for at least one hour. Expected evidence: zero `DECODE_UNSUPPORTED` events for
HEIC/HEIF, no rapid selection loop, no `UNCAUGHT_EXCEPTION`, and ordinary JPG/PNG/WebP
slides continuing through HEIC-heavy folders.

The Android Gradle test was not runnable in the packaging environment because Gradle 8.9
was not cached and `services.gradle.org` was unreachable. All repository-owned offline
gates completed successfully.
