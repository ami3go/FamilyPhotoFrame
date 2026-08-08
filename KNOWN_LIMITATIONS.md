# Known Limitations

These document real scope boundaries and unvalidated areas of the current v0.12.13
prerelease build, not necessarily defects. Historical phase labels below
mark when each area was introduced; they are not a claim about current phase.

## v0.12.13 qualification boundary

- v52.15 fixes recovery from an expired OOM circuit, adds a rate-limited API-21–25 process
  self-recovery path for critically pinned heaps, and fixes scheduled-brightness time persistence
  and malformed-timeline handling while retaining the v52.14 and earlier corrections.
- All 33 checked-in Python verifier entry points and the executable pure-Kotlin/SQL/migration
  gates pass in the delivery environment.
- Gradle 8.9 was unavailable in the delivery environment, so current Android unit tests,
  lint, and APK assembly did not start.
- The v52.15 package has not yet completed the required six-hour API-22/100-MiB-heap
  slideshow soak. API 30+ crash/ANR/process-exit, NAS, and 24-hour recovery gates also
  remain pending. See `docs/FamilyPhotoFrame_v52.15_Memory_Brightness_Recovery.md` and
  `docs/API22_MEMORY_SOAK_TEST.md`.

## Still not implemented

- **On-disk photo caching only covers remote sources.** `MediaCache` caches SMB/Synology
  bytes; local SAF/fallback photos rely on Coil's in-memory cache only (current and next
  bitmaps), with no persistent thumbnail index for them.
- **EXIF rotation handling beyond Coil defaults is still deferred.** Date-taken *is* now
  used for ordering (see the new `DATE_TAKEN_*` selection modes), but orientation is left
  to Coil.
- **Synology Photos albums are not integrated.** The application now has its own named
  playlists, but it does not import DSM/Synology Photos album objects.

## New in this increment (implemented, not hardware-validated)

- **`shuffle_no_repeat`** (spec §9.6) via `domain/randomize/PlaybackQueue.kt`: every
  displayable photo shows once per cycle, including across the cycle seam, and a rescan
  mid-cycle preserves progress rather than restarting. The guarantee is asserted over
  whole cycles in `PlaybackQueueTest` and in the offline harness.
- **Date-taken ordering** (`DATE_TAKEN_NEWEST` / `DATE_TAKEN_OLDEST`). Photos with no
  EXIF date sort last and are ordered among themselves by file-modified time, so an
  undated photo has a stable position instead of moving between rescans.
- **Curation** (spec §9.4): favourite/hide per photo, favourites-only playback, and
  "un-hide all". This activates the `isHidden`/`isFavorite` columns, which had been in
  the schema since v2 with indexes but no writer.
- **Stale-cache playback** (spec §9.3 `on_unreachable`): when a NAS drops, the frame can
  keep showing the NAS photos already in `MediaCache` instead of dropping to bundled
  samples. Backed by the previously dormant `photos.cacheKey` column, so it needs **no
  schema migration**; falls through to samples when the cache is cold.
- **Web parity for overlay position/opacity**, plus the new playback settings — closing
  the last "on-device only" gap listed in `DPAD_WEB_PARITY.md`.

Caveats specific to these: none of it has been compiled by Gradle or run on a device.
The new Room queries (`displayableIds*`, the filtered window, and the curation/cacheKey
writes) are unverified against a real Room annotation processor, and stale-cache playback
has never been exercised against a real NAS outage.

## Spike-posture build choices

- **Release signing is not fail-closed.** R8/resource shrinking are enabled, but when no
  production keystore is configured the release variant falls back to the debug key with
  a warning. Such an artifact is for private testing only.
- **The current v0.11.0 increment has not completed a local Gradle build in the delivery
  environment.** Schema export is enabled and migrations through v5 are verified offline,
  but Room/KSP and Compose must be compiled again on the development workstation.


## v0.11.0 user-feature limitations

- Bulk upload currently targets the app-managed Local uploads library. A writable SAF
  destination is not implemented. App-managed uploads may be removed on uninstall.
- Upload accepts JPEG, PNG, and WebP. HEIC/HEIF conversion and resumable/chunked upload
  are deliberately not included in the first bounded protocol.
- Queue pause stops new files from starting; already active file transfers finish.
- Playlists capture current folder/source/playback settings at creation time. The web UI
  does not yet provide a thumbnail-heavy per-photo playlist editor.
- Brightness uses permission-free per-window control. True hardware display power-off is
  device/OEM dependent; black-screen and slideshow-pause modes are the portable fallback.
- Ambient-light behaviour requires a real sensor and remains unvalidated on the target frame.
- Source-level and offline regression gates pass, but a fresh `./gradlew clean
  testDebugUnitTest installDebug`, browser upload test, low-storage test, and 24-hour
  combined slideshow/upload soak remain mandatory.

## Behavioral notes

- Changing the photo interval applies on the next auto-advance; it does not cut the
  current photo short.
- The folder picker is the system SAF picker; its exact look varies by device/OEM.
- If a device has no SAF picker at all, the app falls back to sample photos.
- Very large folders index progressively; the slideshow can start before indexing
  completes and the list fills in.

## Not yet automated

- The 12-hour stability soak and the StrictMode-clean assertion are **manual**
  (see `MANUAL_TEST_CHECKLIST.md`). A macrobenchmark/automated soak harness is future
  work.

## Phase 1 limitations

- **Boot auto-start is best-effort.** On Android 10+, launching the slideshow directly
  from a boot broadcast is restricted by background-activity-start limits. The app
  attempts it and logs `BOOT_AUTOSTART_BLOCKED` if the OS refuses; on many devices the
  user must grant an OEM "auto-start"/battery exemption for it to work. Marketed always-on
  boot behavior must be validated per device class before it is claimed (spec §12).
- **One primary source at a time in the setup UI.** The engine supports a merged pool of
  multiple primaries, but the current settings screen configures a single primary (SAF
  *or* SMB), always with bundled samples as the fallback pool. Simultaneous SAF+SMB
  primaries need a multi-source management UI (a later increment).
- **Fallback pool is the bundled samples.** When the NAS is down (and its cache is cold),
  the slideshow shows the bundled sample images, not stale NAS images. Stale-cache
  playback (`on_unreachable`, spec §9.3) is not enabled by default.
- **SMB3 encryption not guaranteed.** jcifs-ng handles SMB2/3 with signing; shares that
  *require* SMB3 encryption may need additional crypto config and are unvalidated.

## Phase 1.5 limitations

- **Web traffic is plain HTTP.** Pairing, sessions, CSRF, Host/Origin checks, and bounded
  upload sessions are enforced, but previews, settings, session headers, and uploaded
  family photos are not transport-encrypted. Use only on a trusted private LAN; public
  builds should keep web upload disabled until encrypted transport is implemented.
- ~~QR pairing is not implemented~~ — **superseded**: `WebSecurity.issueQrToken()`,
  `WebServerController.pairingUrl()`, `web/QrCodes.kt` and the on-screen QR in
  `SettingsScreen` implement the §15.3 path. It has not been scanned on real hardware.
- **The server needs a private network.** It binds to a site-local IPv4 address; on a
  device with no private interface it refuses to start and logs `WEB_NO_LAN`.
- **Remote-only (Android TV) setup is not yet demonstrated on hardware**, so headless/TV
  setup must not be marketed until that test matrix passes (Contract Rule 12).

## Phase 2 limitations

- ~~Overlay position pickers exist on-device only.~~ **Superseded**: the web setup page
  now exposes every overlay anchor, the shared opacity, and the scrim toggle, validated
  as a group so one bad value cannot half-apply.
- **Location overlay shows raw coordinates, not a place name.** GPS is deliberately never
  reverse-geocoded (no network call, no new permission) — see docs/archive/phases/PHASE2_NOTES.md increment 5.
- **Remote photos have no date/caption/location overlay on first display.** Since
  increment 8, SMB sources skip EXIF during scanning (it cost one network round-trip per
  photo) and are backfilled when displayed instead. The ViewModel warms the preloaded
  next photo, so in normal playback the overlay is ready in time — but the very first
  photo after a fresh scan, or a photo reached by rapid D-pad skipping, may show no EXIF
  overlay until it comes round again. Setting `ExifScanPolicy.ALL_SOURCES` restores the
  old eager behaviour at the old scan cost.
- **`ExifBackfiller` has no automated test.** It needs a fake `PhotoSource` plus
  in-memory Room, so it belongs in androidTest; only the v5 migration is covered today.

- **Synology source has never run against real DSM hardware.** It is now reachable from
  Settings and its mapping logic is tested, but the roadmap notes the protocol cannot be
  meaningfully unit-tested; only the pure layer is verified. Treat first use as a field test.
- **Synology 2FA accounts will need the code re-entered after a session loss.** The sid is
  held in memory only and a one-time code cannot be stored, so if the NAS reboots or
  invalidates the session, re-auth fails until someone enters a fresh code on the frame.
  A dedicated non-2FA account (or a DSM application password) is the practical setup for
  an appliance that runs unattended for weeks.
- **The web UI cannot create a Synology (or SMB) source from scratch**, only edit an
  existing one — initial setup must happen on the frame, where the password and
  certificate steps have to happen anyway.
- **Remote-source recovery now has an executable integration boundary.**
  `SourceRecoveryCoordinatorTest` and the offline `WebDavChecks` harness cover the audited
  read-failure race: an active source is demoted, the next successful health check promotes
  it exactly once, and a newer read failure invalidates an in-flight promotion. A real NAS
  outage/recovery remains part of the device qualification matrix.

- **The TLS handshake path is unexercised.** Certificate pinning is implemented and its
  fingerprint logic is unit-verified, but no real handshake against a self-signed DSM
  certificate has ever run. The `probe → compare → trust` flow needs a device test.
- **No QuickConnect support.** Synology setup requires a reachable host/port; the
  QuickConnect relay id listed as an option in ROADMAP.md is not implemented, and
  `SourceError.QuickConnectUnavailable` is declared but never produced.

- **The performance budget is still unmeasured.** A tool and procedure now exist
  (PERFORMANCE_BUDGET.md), but no run on the reference low-end device has happened, so
  `fit_blur` and Ken Burns remain opt-in and must not be called the premium default. The
  frame-timing statistics are unit-verified; the `Choreographer` sampling layer has never
  executed, so the first person to run the procedure is also testing the tool.

## Android 5.0–6.0 (API 21–23) — experimental, unvalidated tier

The app now **installs** on Android 5.0+, but this range has not passed a test matrix
and must not be marketed as supported (spec §2, Contract Rule 12). Specifically:

- **SMB on old devices is unverified.** jcifs-ng's SMB2/3 implementation depends on
  crypto providers whose behaviour differs on older Android releases. SMB may fail or
  fall back in ways not yet characterised on API 21–22.
- **Weaker credential protection on API 21–22.** Secrets are encrypted with an
  RSA-wrapped AES key rather than a keystore-resident symmetric key, and there is no
  API before 23 to report whether the key is hardware-backed — diagnostics show
  `securityLevel: unknown` there.
- **The current memory containment is not device-qualified yet** on API 21/22. A v52.8 field log
  from an API-22 tablet exposed the retained-heap failure this increment addresses, but
  the corrected package still needs the dedicated soak in `docs/API22_MEMORY_SOAK_TEST.md`.
- **Old GPUs and 1 GB RAM** are exactly the class the spec warns about for Compose
  rendering budgets (§2.2, §10.3); crossfade performance on such devices is unmeasured.

Before claiming Android 5 support: run the Phase 0 and Phase 1 acceptance checks on a
real API 21 or 22 device, including an SMB source and a reboot cycle.


## Verification status (v41)

Corrected, because earlier revisions of this file overstated what was unverified and
understated what was checkable:

- **Room SQL is now validated.** Every `@Query` is prepared against a real SQLite engine.
  This does not run Room's annotation processor, so projection/return-type agreement and
  Room's own schema hash are still unchecked.
- **The migration chain is now executed**, from a pinned v1 schema through v5, and the
  result is diffed against the `@Entity` declarations. An entity column that no migration
  adds now fails the build — the single most common cause of upgrade crashes.
- **The engine, DAO, indexer and settings model are type-checked.**
- **Still parse-only: the entire UI and ViewModel layer.** `SlideshowViewModel` holds the
  largest untyped refactor in the project. Its Android surface is small enough to stub,
  but its transitive closure (ServiceLocator, Room database, Coil, NanoHTTPD, DataStore)
  is not, and stubbing that far starts producing errors that are artifacts rather than
  defects.
- `ConfigTransfer` and `PortableBundle` are excluded from type-checking: they use
  `.serializer()` functions generated by the kotlinx-serialization compiler plugin, which
  runtime stubs cannot model.


## Build status (v44) — first successful build

`./gradlew installDebug` now **succeeds and installs**, on Honor PLK-L01 running
Android 6.0 (**API 23**). Previous revisions of this file said the project had never been
built; that is no longer true and the claim has been retired.

What this newly proves, beyond the offline harness:

- The whole project compiles, including `SlideshowViewModel` and all Compose UI — the
  code the offline harness could never type-check.
- **Room's annotation processor ran clean.** Every `@Query` passed real KSP codegen,
  including the folder-filtered selection queries and `folderSummaries`. The
  SQLite-based validation in `verify-sql.py` and the migration replay in
  `verify-migrations.py` are now corroborated by the real processor rather than standing
  alone.
- Packaging and installation work end to end.

What it still does not prove: **anything at runtime**. Nothing has been observed
executing. Compilation says the code is well-formed, not that the slideshow plays, the
NAS connects, or the app survives a night.

### API 23 is the unvalidated tier

The install target is API 23, which sits inside the API 21–23 band this file already
flags as unvalidated, and precisely on the boundary that matters for secrets:
`KeystoreSecretStore` branches at API 23 because AES in the Android Keystore begins
there. So this device takes the modern path, but at its earliest possible version —
worth watching when SMB or WebDAV credentials are first saved.

Android 6.0 is also the first runtime-permissions release; SAF folder picking should be
unaffected, but it has not been observed.
