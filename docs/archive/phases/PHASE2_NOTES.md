# Phase 2 — Premium slideshow features

Spec §4 (Phase 2), gated by **§22.4**. Phase 2 is the "make it look premium" phase; the
hard constraint throughout is spec §2.2 and §10.2–10.3: old GPUs and 1 GB devices are
explicit targets, so no full-resolution blur, no 3D transitions, and no per-frame
recomposition.

## Increment 1 — rendering core ✅ (this drop)

- **`fit_blur` aspect mode** (spec §10.4). The letterbox is filled with a blurred copy
  of the same photo. Per spec §10.2 the blur is produced from a bitmap downsampled to
  **64 px** on its longest edge (budget allows ≤ 256), blurred at that size with a
  separable box blur, then stretched by the GPU. It is never computed at display
  resolution.
- **Ken Burns motion**. A slow zoom (1.00 → 1.08) with a diagonal pan whose direction is
  derived deterministically from the photo id. The pan is mathematically bounded by the
  zoom (`|translate| ≤ (scale − 1) / 2`), so an edge can never be exposed mid-move. The
  animation is read **inside a `graphicsLayer` lambda**, so it is GPU-composited and does
  not recompose the slideshow per frame (spec §10.3).
- **Additional transitions**: crossfade (existing), **slide**, and **none**, selectable in
  Settings and over the web API.
- **Defaults deliberately unchanged**: `fit_color` + crossfade + no motion. Spec §10.3
  says fit-blur is Phase 2 "after profiling" and warns against heavy effects on weak
  hardware, so the premium modes are **opt-in** until the performance budget below is
  measured on real low-end hardware.
- **Tests**: `ImageBlurTest` (8 cases: identity, peak spread, alpha preservation, byte
  range, oversized radius, non-square, spec budget) and `KenBurnsTest` (8 cases:
  endpoints, clamping, determinism, direction spread, monotonic zoom, and the
  never-expose-an-edge invariant across seeds and progress).

## Fix — short intervals showed a black screen (found on device, v16)

At a 3-second interval the slideshow blinked a photo then went dark. Three compounding
defects, all in increment 1's rendering:

1. **Transition not scaled to the interval.** The default 1200 ms transition consumed
   ~40 % of every 3 s cycle. Now capped at a quarter of the interval.
2. **Crossfade dipped through black.** `fadeIn(tween) togetherWith fadeOut(tween)` leaves
   both layers semi-transparent over the black background at once, so the composite
   genuinely darkens mid-transition. The outgoing photo now holds at full opacity
   *underneath* and snaps away after the new one has faded in on top.
3. **The preload never hit the cache.** The preload request carried no size, so Coil
   resolved it to `Size.ORIGINAL` while the display request was sized by the layout node
   — different sizes give different memory-cache keys, so every slide decoded from
   scratch at display time. Both requests now decode at the physical display size, which
   is also what spec §10.2 requires.

Also added `contentKey = { it?.id }` so an equal-but-new model instance cannot restart a
transition.

## Increment 2 — sleep schedule ✅ (this drop)

- **Quiet hours** (spec §20 `schedule`): start/end times, a night-brightness preset, and
  an enable toggle in Settings → Quiet hours, plus the same fields over the web API.
- **`SLEEPING` engine state** (spec §9.1). While asleep the engine stops advancing and
  blocks on its command channel, so there are no timers, decodes, or network fetches —
  the current photo simply stays put while the window dims.
- **Dimming** uses a per-window brightness override, not a system setting, so it needs no
  permission and is released automatically with the Activity. Brightness is floored just
  above zero: a frame at 0 looks broken rather than asleep.
- **Recovery is inherent.** The state is derived from the wall clock on every evaluation
  rather than tracked with a running timer, so a reboot, process death, or a device
  suspended across the transition all resolve correctly on the next tick. Waits are
  additionally capped at 15 minutes so clock or timezone changes are noticed promptly.
- **Tests**: `SleepScheduleTest` (16 cases) covering parsing and rejection of malformed
  times, same-day windows, the midnight wrap, inclusive-start/exclusive-end boundaries,
  the zero-length window (which must mean *never* asleep, not always), next-transition
  timing, a full-day walk asserting exactly 8 h asleep, and the brightness floor.

## Increment 3 — encrypted portable bundle ✅ (this drop)

The plain config export deliberately strips every credential, which makes moving a
configured frame to new hardware tedious. This is the sanctioned way to carry secrets
(spec §14.4 option 2):

- **AES-256-GCM** over a payload of settings **plus** the SMB password read back from
  the Keystore, under a key derived from a user-chosen passphrase.
- **Envelope records its KDF and cipher metadata** — algorithm, salt, iterations, key
  size, nonce, tag length — as §14.4 requires, so parameters can be raised later without
  breaking existing files.
- **A wrong passphrase and a tampered file report the same error**, because
  distinguishing them would leak whether the passphrase was correct.
- On import the secret is **re-encrypted under the importing device's own Keystore key**;
  the bundle's key material never becomes the device's key material.
- The passphrase is memory-only: local to the composable, passed straight to the call,
  cleared after the file operation. It is never persisted, logged, or put in UI state.
- **Tests**: `PortableBundleTest` (16 cases) — round trip including the password, the
  password not being recoverable from the file, wrong passphrase, tamper detection,
  indistinguishability of those two, fresh salt/nonce per export, metadata presence,
  rejection of foreign/garbage/too-new files, and Base64 across every padding case.

**Algorithm note.** `PBKDF2WithHmacSHA1` and a hand-rolled Base64 are used because
`PBKDF2WithHmacSHA256` and `java.util.Base64` are both API 26+, while this build supports
API 21. A bundle has to import on the *older* device too, so the lowest common
denominator wins; the recorded metadata is what makes an upgrade possible later. The
Base64 implementation was verified byte-for-byte against a reference encoder.

## Increment 4 — weather overlay ✅ (this drop)

- **Disabled by default** (spec §11). Enable it in Settings → Weather and type your
  latitude/longitude: **the app never requests a location permission**, which also keeps
  the permission audit clean.
- **Failures never touch the slideshow.** Fetching runs in its own coroutine with a hard
  timeout; every error collapses to a null result. A failed refresh *keeps* the previous
  reading (so a brief outage shows slightly old data rather than flickering the overlay
  away), marks it stale after a threshold, and hides it only once it exceeds the maximum
  age. Repeated failures back off up to two hours instead of hammering a dead endpoint.
- **Endpoint and API key are configurable**; the key is stored in the Keystore, never in
  the settings file. This exists because the default provider's *free* tier is licensed
  for non-commercial use only even though its data is CC BY 4.0 — see
  **WEATHER_LICENSING.md** for the release gate that must be signed off before shipping
  weather commercially.
- **Tests**: `WeatherTest` (17 cases) — parsing valid/partial/malformed payloads, URL
  construction with and without a key, the hidden→visible→stale→hidden lifecycle,
  Celsius/Fahrenheit conversion, rounding including negatives, and code labels.

## Increment 5 — photo date / caption / location overlays ✅ (this drop)

- **EXIF extraction wired up** (`data/index/ExifExtractor.kt`). The `width`/`height`/
  `exifOrientation`/`dateTakenEpochMs` columns added back in the v1→v2 migration existed
  but were never populated — `Indexer` now opens each file once during indexing (via the
  existing `PhotoSource.openStream`) and reads `androidx.exifinterface`, storing
  date-taken, a cleaned caption (`ImageDescription`), and GPS lat/lon. New v3→v4 columns:
  `photos.caption`, `photos.gpsLat`, `photos.gpsLon` (`MIGRATION_3_4`, additive-only).
- **Best-effort, never blocking.** Each EXIF read is capped at `exifTimeoutMs` (4 s
  default) via `withTimeoutOrNull` and any failure (corrupt/missing EXIF, slow SMB file,
  etc.) just leaves those columns null — it is **not** counted as a scan error and never
  fails indexing of the file itself, matching the existing "failures never touch the
  slideshow" posture (weather overlay, SMB recovery). An aggregate `exifMisses` count is
  logged once per scan at `SCAN_DONE`; no per-file EXIF content is ever logged.
- **Pure/impure split for testability**, same shape as `ImageBlur`/`KenBurns`: date
  parsing, caption cleaning, and GPS-string formatting live in `ExifParsing` with no
  Android dependency (`ExifParsingTest`, 12 cases — offset/no-offset parsing, blank and
  zeroed placeholder dates, malformed input, placeholder-caption filtering, truncation,
  and coordinate formatting across all four quadrants); only the thin
  `androidx.exifinterface` call site in `ExifExtractor` is untested at unit level.
- **Three new overlays**, each off by default (unlike clock/date/folder, not every photo
  has this data, so they're opt-in rather than showing blank text): **photo date taken**
  (`overlays.photoDateShow`, distinct from the existing "today's date" clock overlay),
  **caption** (`overlays.captionShow`), and **location** (`overlays.locationShow` — GPS
  rendered as decimal-degree coordinates, e.g. `40.7128°N, 74.0060°W`; deliberately
  **no reverse geocoding / network lookup**, keeping the local-first, no-network-required
  posture and the "no new permissions" contract). Each has its own 9-grid position field,
  same as the existing overlays. Settings → Overlays gets three new toggles.
- **GPS never reaches diagnostics** (spec §17.2, unchanged contract): `DiagnosticsLog`
  and the web `/api/diagnostics` export only ever receive short, hand-built strings from
  call sites — neither was changed to touch photo rows, so the existing redaction
  guarantee holds without new special-casing.
- **Not yet done:** reverse geocoding to a place name (documented as a possible follow-on,
  not attempted here — it would need a network call or on-device geocoder and a caching
  strategy, out of scope for this increment); overlay position pickers in the D-pad
  Settings UI and the web setup page (pre-existing gap — none of the *existing* overlays
  have position pickers there either, see `KNOWN_LIMITATIONS.md`); performance of
  per-file EXIF reads has not been measured against a large SMB library.

## Increment 6 — overlay position pickers + weather overlay visibility ✅ (this drop)

Closes the gap flagged at the end of increment 5 and in `DPAD_WEB_PARITY.md`: every
overlay has had a 9-grid `...Position` field in `OverlaySettings` since Phase 0/1, but
none of them were ever user-editable — defaults were the only option.

- **On-device position pickers** (Settings → Overlays). Each enabled overlay (clock,
  today's date, folder, photo date-taken, caption, location) now shows a compact
  "◀ Position ▶" row beneath its toggle that cycles through the 9-grid anchor one step
  at a time — deliberately two single-press buttons rather than a 2-D grid picker, so it
  stays D-pad operable with the same interaction shape as the existing interval stepper
  (`IntervalStepper`).
- **Weather overlay visibility was missing entirely.** `overlays.weatherShow` existed and
  was read by `OverlayLayer`, but Settings only ever exposed `weather.enabled` (whether
  the frame *fetches* weather at all), not whether a successful fetch is *drawn* on
  screen. Settings → Weather now has a "Show weather overlay on screen" toggle plus its
  own position picker, shown once weather is enabled.
- **Web API parity, partial.** `GET /api/config` / `POST /api/config` now also carry
  `weatherShow`, `photoDateShow`, `captionShow`, `locationShow` (redacted config, no
  secrets involved). Position and opacity remain API/UI-inaccessible on both surfaces —
  updated `DPAD_WEB_PARITY.md` to reflect the current, narrower gap precisely rather than
  the previous blanket "not yet exposed."

## Increment 7 — overlay opacity control ✅ (this drop)

The last item from the increment 6 gap list: `overlays.opacity` existed and was applied
to every overlay's text alpha, but was not adjustable anywhere.

- **On-device**: Settings → Overlays gets a single "−/+ NN%" stepper
  (`OpacityStepper`) at the top of the section, shared across every overlay (matching
  the settings model — `OverlaySettings.opacity` is one value, not per-overlay).
  Steps in 10% increments and is **floored at 10%**, not 0%: an overlay at 0% looks like
  a rendering bug, not an intentionally hidden overlay — the existing show/hide toggles
  are the correct tool for actually hiding one.
- **Web API**: `GET`/`POST /api/config` now carry `overlayOpacity`, clamped server-side
  to the same 10–100% range as the on-device setter.
- `DPAD_WEB_PARITY.md` updated — no remaining "not yet exposed" overlay-customization
  gaps other than **position in the web UI**, which stays out of scope for now (would
  need a 9-grid or dropdown widget in the web setup page's plain HTML/JS).

## Post-review fixes (increments 5–7)

A read-through of the delivered increments 5–7 found four defects; three are fixed here.

1. **GPS coordinates were locale-dependent.** `"%.4f".format(...)` uses
   `Locale.getDefault()`, so a German/French device rendered `40,7128°N`. Now
   `String.format(Locale.ROOT, ...)`. Added `gpsCoordinateFormattingIsLocaleIndependent`,
   which flips the default locale to `Locale.GERMANY` and asserts the dot form — this
   would also have failed the pre-existing quadrant test on a European-locale CI machine.
2. **Long captions could swamp the photo.** `OverlayCluster`'s `Text` had no `maxLines`
   or `overflow`, and captions are accepted up to 280 chars of camera/user-supplied text.
   `OverlayPiece` now carries `maxLines` (1 for the fixed-shape clock/date/folder/
   coordinate pieces, 2 for captions) with `TextOverflow.Ellipsis`, and multi-line pieces
   are width-capped at 50% so a caption can't stretch across a wide panel.
3. **`exifOrientation` had two meanings for "unknown".** `ExifMetadata.orientation`
   defaulted to `ORIENTATION_NORMAL` (1) while `Indexer` wrote 0 for a missing EXIF block,
   so "no tag" and "tag says upright" were conflated depending on the path. Both now use
   `ORIENTATION_UNDEFINED` (0). Latent today (nothing reads the column) but a trap for
   whoever wires up rotation.

**Still open — item 4, EXIF read cost on SMB.** `SmbPhotoSource.openStream` is a raw,
uncached `SmbFile(...).inputStream`, so indexing now performs one extra network open per
photo: a 10k-photo NAS library incurs 10k additional round-trips per scan. Left as-is
pending a decision; `Indexer(extractExif = …, exifTimeoutMs = …)` already exposes the
knobs. See KNOWN_LIMITATIONS.md.

## Increment 8 — source-aware EXIF + display-time backfill ✅ (this drop)

Resolves the open performance item from the increments 5–7 review (options "A now,
B later"); both are implemented, and they compose into one design rather than two.

**A — scan-time EXIF is now source-aware.** New `ExifScanPolicy` enum
(`LOCAL_ONLY` default / `ALL_SOURCES` / `NEVER`). Under the default, `Indexer` reads EXIF
for local SAF and app-private sources but **skips SMB**, removing the ~10k extra network
opens a large NAS scan was paying. The chosen mode is recorded on the `SCAN_START`
diagnostics line.

**B — photos skipped at scan time are filled in when displayed.** New `ExifBackfiller`
plus a v4→v5 column, `photos.exifScannedAtEpochMs`:

- Null means "EXIF never attempted" — the queue signal. `Indexer` stamps it only when it
  actually read EXIF, so remote rows (and every pre-existing row) start queued.
- On each photo change the ViewModel backfills the current photo and warms `next`, so in
  steady-state playback the overlay is usually ready *before* the photo appears — which
  removes most of the "blank on first showing" cost option B was expected to carry.
- A row is stamped even when it has no EXIF at all, so photos genuinely lacking EXIF are
  not reopened on every showing. A *failed* read is deliberately **not** stamped: unlike
  "no EXIF", a failure may be transient (NAS asleep, Wi-Fi drop), so it stays queued.
- One read at a time via a `Mutex`, so holding down D-pad "next" cannot pile up
  concurrent network opens. Each read is timeout-bounded and never throws to the caller.
- Deliberately **not** placed in `SlideshowEngine`, which documents that it performs no
  disk or network I/O. It is driven from the ViewModel, and results are published into
  `SlideshowUiState.currentPhotoExif` — guarded by an "is this still the photo on screen?"
  check, since the slideshow can advance mid-read. The overlay layer prefers the indexed
  values and falls back to the backfilled ones.

**Not done:** no unit test for `ExifBackfiller` (it needs a fake `PhotoSource` + in-memory
Room, which is androidTest-shaped rather than JVM-shaped); the migration test covers the
v5 column but not the backfill path.

## Increment 9 — offline verification harness ✅ (this drop)

Increments 5–8 were all delivered uncompiled, because a Gradle build needs
`services.gradle.org` and `dl.google.com`. That assumption turned out to be too broad:
**the Kotlin compiler is downloadable from GitHub releases**, which is reachable.

`scripts/verify-pure-logic.sh` now parses every main source and executes the pure-Kotlin
logic against real assertions. See BUILD_NOTES.md for what it does and does not prove.

Results on this drop:

- **No parse errors** across all 49 files in `app/src/main/java`.
- **19/19 pure-logic checks pass**, actually executed rather than reasoned about.
- Two errors that looked like real defects in increment 8 code were run down and
  confirmed to be classpath artifacts, not bugs: the `'return' is prohibited here` in
  `ExifBackfiller` (reproduced standalone with an equivalent `inline` function — compiles
  clean, so the non-local return through `Mutex.withLock` is valid) and the `compareTo`
  error in `ExifExtractor` (`getAttributeInt` unresolved, so `it` has an error type).
- The v24 locale fix is now **empirically** confirmed rather than argued: the pre-fix
  expression renders `40,7128°N, 74,0060°W` under `de_DE`, the fixed one keeps the dot.

Still unverifiable offline, and unchanged from before: anything touching Android, Compose,
Room or coroutines — which is most of increments 6–8, including the v5 migration, the
ViewModel backfill hook and every Compose control.

## Increment 10 — Synology File Station source ✅ (this drop)

The first item of genuinely *planned* work from `ROADMAP.md` ("Network photo-app
sources"), rather than a further increment on the overlay track. The roadmap sequences
this deliberately: **File Station first** because it is the official, documented, stable
API, with the reverse-engineered, DSM-version-sensitive `SYNO.Foto.*` album API as a
follow-on. This drop is step 1 only.

**What it buys over SMB.** Server-side thumbnails are the point: the NAS returns an
already-generated, right-sized JPEG instead of the frame pulling full-res originals — or
HEIC/RAW the device may not decode at all. HTTP/S transport also removes the jcifs-ng
dependency from the path.

**Shape.** Exactly the split the roadmap asks for ("a thin, mockable HTTP layer so the
*mapping* logic can be tested without a live NAS"), mirroring the existing
`OpenMeteoParser` / `OpenMeteoProvider` precedent:

- `SynologyApi` — **pure**, dependency-free: URL building, percent-encoding, response
  parsing, and Synology error-code → `SourceError` mapping.
- `SynologyHttpClient` — the mockable seam; `UrlConnectionHttpClient` is the default
  transport on the existing stack, so **no new third-party dependency** and no new
  LGPL-style obligation.
- `SynologyFileStationSource : PhotoSource` — same posture as `SmbPhotoSource`: off-main,
  cancellable, time-bounded; streams `ScanEvent`s and never builds a List; explicit
  work-stack rather than recursion for deep trees.

**Session handling.** `withSession` retries once through a re-auth on `SessionExpired` —
the most likely long-running-frame failure, since a NAS can reboot or time out a session
while the frame sits idle for days. `openStream` prefers the thumbnail and falls back to
the original when the NAS has not generated one yet.

**Secrets (Contract Rule 5).** The sid is held in memory only, never persisted, and never
placed in an `openToken` — the token is the plain NAS-relative path, which also means it
survives a session change. `SynologyApi.redactSid` scrubs any URL before it can reach a
log or error string. New `SourceError` cases from the roadmap: `TwoFactorRequired`,
`CertUntrusted`, `SessionExpired`, `QuickConnectUnavailable`.

**Verified.** 34 new pure-logic checks, executed via `scripts/verify-pure-logic.sh`
(54 total, all passing) — auth URL construction with 2FA, sid redaction, error-code
mapping, and list-page parsing including spaces in names, escaped slashes, seconds→millis
`mtime` conversion, directory flags, and failure responses. Confirmed no exhaustive `when`
elsewhere breaks on the new enum cases.

**Not done — this is a source implementation, not a usable feature yet.** No settings UI
(D-pad or web), no `SourceConfigEntity`/DB persistence, no `ServiceLocator` wiring, no
`ActiveSourceKind.SYNOLOGY`. QuickConnect and explicit certificate-trust handling are
declared in the error taxonomy but not implemented. Per the roadmap's own caveat, the
protocol cannot be meaningfully validated without a real DSM device; only the mapping
logic is tested.

## Increment 11 — Synology source wired up end to end ✅ (this drop)

Increment 10 shipped a source nothing could construct. This makes it reachable.

- **Settings model**: `ActiveSourceKind.SYNOLOGY` + `SynologySettings` (baseUrl, folder,
  user, credentialRef, thumbnail prefs). The password goes to the Keystore SecretStore
  exactly as SMB's does; **the 2FA one-time code is deliberately not a field** — it is
  valid for seconds, so it is held in memory for one login and cleared.
- **Wiring**: `ServiceLocator.synologySource(...)` + `SOURCE_SYNOLOGY`; an `applySource`
  branch that indexes and plays, falling back to the sample pool when the NAS is
  unreachable rather than showing an error surface (same posture as SMB — an offline NAS
  must never blank the frame); and a `resolveSourceById` case so the display-time EXIF
  backfiller works for Synology photos too.
- **D-pad settings UI**: `SynologySection` with address / folder / user / password / 2FA
  code / thumbnails toggle, plus Test and Save, mirroring `SmbSection`. Test results
  reuse the shared result slot so only one connection test is ever in flight. Health
  states map to specific messages — a 2FA prompt reads differently from a bad password
  or an untrusted certificate.

**A real defect this surfaced.** `ConfigTransfer.redact()` began
`val smb = settings.source.smb ?: return settings` — fine while SMB was the only
credential-bearing source, but it means a **Synology `credentialRef` would be written
untouched into an exported config file**. `merge()` and `needsPasswordReentry()` had the
same single-source assumption. All three now handle both source types, and four
regression tests cover it (including one asserting the SMB path still works). This was a
silent gap: adding the enum case produced no compile error anywhere.

**Not done.** No web-UI parity for Synology setup (the web page still only knows SAF/SMB;
`/api/config` exposes `sourceKind` but no Synology fields), no recovery loop equivalent to
`startSmbRecovery`, no QuickConnect, no certificate-trust opt-in. Still never run against
real DSM hardware — only the pure mapping layer is verified.

## Increment 12 — recovery loop generalized; Synology session reuse ✅ (this drop)

Closes the most functionally important gap left by increment 11. Without a recovery loop,
a NAS that was down at boot meant the frame played bundled samples **forever**, until
someone walked over and re-saved the settings — unacceptable for an appliance that is
supposed to run unattended for weeks.

- **`startSmbRecovery` → `startSourceRecovery`.** The loop was already source-agnostic in
  substance; only the parameter type, the source-id constant and two log labels were
  SMB-specific. It is now shared rather than copy-pasted, taking `sourceId`, a diagnostics
  `label` and a health-check budget. SMB behaviour is unchanged (same backoff ladder, same
  `SMB_RECOVERED` / `SMB_LOST` codes).
- **Synology now recovers identically**: it keeps retrying whether or not the NAS was
  reachable at startup, swaps itself in and out of the engine's primary pool, and
  un-suppresses + reindexes on recovery, with samples playing throughout.

**A bug this exposed before it shipped.** The recovery loop polls `healthCheck` every 60
seconds while healthy, and `SynologyFileStationSource.healthCheck` performed a **full
login every call**. Left alone that would have:

  1. accumulated a new DSM session per minute;
  2. risked tripping DSM's auto-block for repeated logins, which comes back as error 407
     and would have looked like a permission problem rather than self-inflicted rate
     limiting; and
  3. **failed outright on any 2FA account** — a fresh login needs a one-time code, and by
     the time the loop is running there is no longer one available.

`healthCheck` now reuses the existing session and only logs in when there isn't one,
going through the same `withSession` path (which already re-authenticates once on
`SessionExpired`). A dedicated `authFailureHealth()` does one extra login *only* on the
failure path, to tell "wrong password" apart from "this account needs a 2FA code" — the
two need very different instructions on screen.

**Not done.** Still no web-UI parity for Synology setup, no QuickConnect, no
certificate-trust opt-in. The recovery loop itself has no automated test on either source
type — it needs a fake clock and a fake `PhotoSource`, which is androidTest-shaped.

## Increment 13 — open-source notices screen (two release gates) ✅ (this drop)

Both `LGPL_COMPLIANCE.md` and `WEATHER_LICENSING.md` documented an in-app "notices
screen" as a compliance requirement. Neither actually existed — a real gap, discovered
the same way the increment-11 credential leak was: by reading a doc's claim and checking
whether the code backed it up.

- **New `Settings → "Open-source licenses"` screen** (`NoticesDialog`, mirroring the
  existing `DiagnosticsDialog` pattern exactly): every runtime dependency from
  `THIRD_PARTY_LICENSES.md`, with its license name, a short original description of what
  that license permits, and a link to the license's canonical text plus, for jcifs-ng and
  a couple of others, a link to the upstream project.
- **Link to canonical text, not an embedded copy — a deliberate choice, not a shortcut.**
  `LGPL_COMPLIANCE.md` had asserted the "full LGPL-2.1 text" was bundled; reconstructing
  several thousand words of legal text accurately, from memory or from web fragments,
  risks a subtly wrong copy, which is worse for a compliance screen than a correct link.
  This also matches the Apache Software Foundation's own published guidance for its
  license: a canonical URL is sufficient in place of embedding the text. Both gate docs
  are corrected to describe what is actually true now instead of what was aspirationally
  claimed.
- **Weather CC BY 4.0 attribution**, added in Settings → Weather (shown whenever the
  overlay is enabled) and again in the notices screen — **deliberately not** on the live
  TV overlay itself, since CC BY requires attribution, not a permanent on-screen credit
  line sitting over family photos for as long as weather is on.
- Both gates' checklists updated: `LGPL_COMPLIANCE.md`'s notices-screen item and
  `WEATHER_LICENSING.md`'s attribution item are now checked off, each with an inline note
  flagging what a release owner should confirm before full sign-off.

**Explicitly not closed.** Both documents' remaining items are genuinely business/legal
decisions — an Open-Meteo commercial subscription or alternative provider, a signed
agreement, R8/shrinking keep-rule verification — and stay open on purpose; no amount of
code changes this.

## Increment 14 — HTTPS certificate-trust choice for Synology ✅ (this drop)

Completes the Setup UI scope the roadmap declared for step 1 ("host/port, **HTTPS +
certificate-trust choice**, username, password, optional 2FA one-time code"). Chosen over
the roadmap's step 2 (`SYNO.Foto` albums) because step 2's own entry says to adopt it
"only once the source type is proven" — and File Station has still never run against real
hardware, so jumping ahead would defy the roadmap's sequencing.

**Why this matters more than it sounds.** DSM ships a self-signed certificate by default,
so the *typical* Synology user fails platform validation. Before this drop their only
workaround was plain `http://` — which puts their NAS password in cleartext on the LAN.
The feature that looked like polish was actually the difference between an encrypted and
an unencrypted setup for most real users.

**Trust-on-first-use pinning, explicitly not trust-all.** `PinnedCertTrustManager`
accepts a chain if the platform already trusts it, **or** if the leaf matches the one
exact SHA-256 fingerprint the user personally approved. An attacker presenting a
different certificate still fails, so MITM protection survives the approval — which a
`trust-all` switch would destroy permanently. Pinned certificates are still checked for
expiry.

- `CertPinning.probeCertificateFingerprint` retrieves what the host presents **without
  trusting it** (`CapturingTrustManager` rejects every chain and records the leaf on the
  way past), so the UI can show a fingerprint to compare against DSM → Control Panel →
  Security → Certificate. Returns null when there is nothing to approve — plain HTTP,
  unreachable, or already platform-trusted — rather than inviting a pointless pin.
- Approval is a **separate, deliberate action** (`trustSynologyCertificate`), not a side
  effect of Test or Save: approving a certificate is a security decision. It is
  reversible (`clearSynologyCertificate`) and both are logged.
- Fingerprints are shown in full, never truncated — a truncated fingerprint cannot be
  meaningfully compared, which would make the whole verification step theatre.

**A second-order security hole this raised, closed here.** `ConfigTransfer.merge` would
have carried an imported file's `pinnedCertSha256` straight through, letting a hostile or
stale config **pre-approve an attacker's certificate** for a host the user was about to
configure. Merge now keeps only what *this* device already approved, and only on a
matching connection — never what the file claims. Two regression tests cover it. Note
this is the same function that leaked a credential in increment 11: config merge is
where per-device security state keeps getting silently widened, and is worth re-reading
whenever a new field is added.

**Verified:** 19 new pure-logic checks (73 total, all passing), concentrated on the
security-critical path — blank/null pins never match, prefixes never match, comparison is
format-insensitive but value-exact.

**Not done:** hostname verification is left at the platform default (deliberately — the
pin authenticates the certificate, and relaxing hostname checks too would widen the hole
this feature exists to close). No QuickConnect. No web-UI parity. Still no real DSM
hardware, so the handshake path itself is unexercised.

## Increment 15 — Synology setup in the web UI ✅ (this drop)

Completes the roadmap's declared "Setup UI (**D-pad + web**)" for the File Station
source; only the D-pad half existed. Mirrors the established SMB pattern exactly rather
than inventing a second shape.

- `/api/config` now carries `synBaseUrl`, `synFolderPath`, `synUser`, `synUseThumbnails`
  (read/write) and `synPinnedCert` (**read-only**).
- New "Synology NAS (File Station)" section on the setup page, with the same note the SMB
  section carries about what can only be done on the frame.

**Three things the web UI deliberately cannot do**, each for a distinct reason:

1. **Password** — a secret; spec §15.6 keeps secrets off cleartext HTTP. (Same as SMB.)
2. **2FA one-time code** — a secret, and additionally pointless to accept remotely: it is
   valid for seconds and is consumed by the very next login.
3. **Certificate approval** — *not* a secret, but a security decision. The whole point of
   increment 14's flow is that a human compares a fingerprint against DSM before trusting
   it; accepting a pin over cleartext HTTP would let anyone on the LAN silently pin a
   certificate of their choosing, which would undo that feature entirely. The current
   fingerprint is therefore shown read-only, so a remote admin can *see* what the frame
   trusts without being able to *change* it.

**Consistent with SMB, worth stating explicitly:** the web UI can only *edit* an existing
Synology source, not create one from scratch — `applyConfig` skips the block when
`source.synology` is null. Initial setup stays on the frame, where the password and
certificate steps have to happen anyway.

Extended the existing `configResponseContainsNoSecrets` test to assert neither a Synology
password nor an OTP ever appears in the config response, in any casing.

## Increment 16 — performance-budget measurement harness ✅ (this drop)

The one remaining §22.4 item has been open since Phase 2 began, not because the feature
was missing but because "must still be measured" had no procedure and no instrument. This
supplies both. It does **not** close the gate — that needs the reference low-end device —
but it converts an open-ended blocker into a ten-minute task with a results table.

- **`FrameStats`** — pure arithmetic over frame timestamps: nearest-rank percentiles,
  jank counts, and the pass/fail verdict. **The gate uses the p95 frame interval, not the
  mean**, which is the substantive design decision here: a transition averaging 45 fps
  while stalling 200 ms mid-crossfade passes on a mean and looks broken on a TV. A test
  asserts exactly that case fails.
- **`FrameStatsCollector`** — thin `Choreographer` glue. Costs nothing when stopped (no
  callback posted at all) and writes into a fixed 3,600-sample ring while running, so the
  instrument cannot grow the heap it is partly there to observe.
- **On-screen readout + `Settings → Performance measurement`**, off by default and
  labelled as a testing aid. The readout is deliberately unstyled plain text with no
  animation or background — a measurement aid must not cost frames in the run it measures.
- **`Record a sample to diagnostics`** writes a `PERF_SAMPLE` line so a result travels in
  a support bundle and can be attached to a sign-off.
- **`PERFORMANCE_BUDGET.md`** — the procedure: exact device/settings to use, why an
  emulator result is meaningless, why to run 5+ minutes and record the *worst* verdict
  rather than the best, and a results table to fill in.

**Deliberately honest about scope.** The budget has four clauses; this measures two.
Heap-growth and the 1 GB-RAM confirmation need the memory profiler and the right
hardware, and `PERFORMANCE_BUDGET.md` says so explicitly rather than letting a green fps
number imply the whole gate passed.

**Verified:** 27 new pure checks (100 total), covering percentile edge cases, a
non-monotonic clock not inflating fps, too-short runs never passing, and the
stall-with-good-mean case failing.

## Increment 17 — static consistency checks ✅ (this drop)

Not a feature. After twelve increments delivered without a real build, the accumulated
risk of an unverifiable change was worth more attention than another feature, and there
was a whole category of defect sitting between the two checks that already existed.

`verify-pure-logic.sh` caught **parse** errors and **pure-logic** bugs. Neither could see:

| Mistake | Consequence | Previously caught by |
|---|---|---|
| `R.string.x` with no resource | compile error | nothing |
| duplicate resource name | compile error | nothing |
| `vm::foo` with no such function | compile error | manual grep, by hand, each turn |
| entity column with no migration | **crash on launch when upgrading** | nothing |
| gap in the migration chain | **crash on launch when upgrading** | nothing |

The last two matter most: they are not compile errors, so even a successful Gradle build
would not surface them — they appear only when an existing install upgrades, which is
both the worst place to find a bug and the case least likely to be exercised in testing.
Across increments 5–16 this project added 4 columns and 2 migrations.

`scripts/check-consistency.py` now covers all five, and runs first in
`verify-pure-logic.sh`.

**Result on the current tree: clean.** All `R.string`/`R.array` references resolve, no
duplicates, every `vm::` callback resolves, and the migration chain is contiguous 1→5
with every migration registered — so the twelve unbuilt increments are at least sound on
these axes. That is a genuinely useful thing to know and was previously only ever
asserted from memory.

**Validated the checker rather than trusting it.** A checker that only prints "pass" is
worse than none, so each of the four failure classes was deliberately introduced and
confirmed to fail before the tree was restored.

**What this still does not do.** It cannot type-check, so anything touching Android,
Compose, Room's generated code, or coroutines remains unverified — the standing caveat is
unchanged. This narrows the gap; it does not close it.

## Increment 18 — real build defect + closed detection gap ✅ (this drop)

The v34 zip failed to compile on real hardware:

```
SlideshowViewModel.kt:214 'when' expression must be exhaustive.
Add the 'SYNOLOGY' branch or an 'else' branch.
```

Exactly the failure mode I documented in increments 11 and 12 — *"adding an enum case
produced no compile error anywhere. The gap was in an early-return, not a `when`."* —
except this time it produced a compile error at the site I hadn't grepped. The source
signature builder in `applySource` was a non-else `when` over `ActiveSourceKind` and
never got the SYNOLOGY branch six increments back. The consequence was a signature
collision, not a crash: whenever the user activated a Synology source, the signature
matched the last non-SYNOLOGY sig, so `applySource` was never called again.

- **Fixed the branch.** The Synology signature includes baseUrl / folder / user /
  credentialRef / pinnedCertSha256 — everything that should re-apply the source when it
  changes. Password is deliberately not in it: secrets do not belong in a diagnostics
  string, and `credentialRef` already covers "which stored password."
- **Swept every other `when` over `ActiveSourceKind` / `SourceType`.** Zero further
  offenders. The failure was one site, not systemic — but it *would* have been systemic
  next time a case landed, since nothing prevented it.
- **`scripts/check-consistency.py` now flags this class of mistake.** Any non-`else`
  `when` that references at least two values of a tracked enum and is missing others now
  fails offline verification. Two-or-more is the heuristic for "actually a switch over
  this enum," so an unrelated `when` that happens to name one enum constant does not
  produce noise. Validated by re-introducing the exact v34 defect and confirming the
  checker reports it at `SlideshowViewModel.kt:214` — the same line the Kotlin compiler
  named.

**What this changes about the standing caveat.** Previously the story was "syntax
verified, type checks not." That still holds — Android/Compose/Room references remain
unchecked, and this defect would have been caught by an ordinary Gradle build. What is
now closed is the *specific* class of defect I had explicitly named in two prior
increments as a known risk without adding detection for it. The next time someone adds a
sixth `ActiveSourceKind`, the failure will surface offline rather than at build time.

## Increment 19 — settings screen regrouped ✅ (this drop)

Not a feature — the settings screen had accumulated 12 top-level sections in the order
they were built, not the order a user would look for them, and the user asked to fix it.
Structure per the user's three choices (not mine):

  1. **Photos** — SAF folder, plus SMB and Synology as subsections; one top-level block
     rather than three separate ones.
  2. **Playback** — interval, aspect, transition, motion. Grouped because these are
     experienced as one thing (§10.3's performance budget is a joint budget over them).
  3. **Overlays** — clock, date, folder, photo date, caption, location. `Clock 24h` now
     lives *under* the Clock toggle it modifies (previously free-floating).
  4. **Weather** — its own top-level block, per the user's answer.
  5. **Schedule** — sleep window. Previously buried inside Device.
  6. **Device** — autostart, web control panel, the §22.4 performance overlay. Common
     trait: none of them changes what the slideshow looks like.
  7. **Backup** — export/import + encrypted portable bundle.
  8. **About** — diagnostics + open-source notices. Last because they exist for
     support/inspection, not day-to-day use.

Each section has a comment explaining *why* it groups what it does, so the next person
sees the reasoning rather than a flat list.

**What the increment-17 checker caught during this refactor.** After the first draft I
had left a duplicate perf/back-button block at the bottom (I moved the block up but
forgot to delete the source), and the `settings_motion` string was orphaned (I removed
the section label but not the entry). The parse check saw no errors — Kotlin was fine
with both — but the resource-usage check flagged `settings_motion` as newly unreferenced,
which prompted me to look and find both. Exactly the kind of regression the checker was
built for.

## §22.4 acceptance — status

- [ ] **Fit-blur, Ken Burns, and transitions pass the performance budget** — implemented;
      *must still be measured* (spec §10.3: ≥ 30 fps crossfade on the reference low-end
      device). **A measurement tool and a written procedure now exist — see
      PERFORMANCE_BUDGET.md** (increment 16); the item stays open because it needs the
      reference hardware, which no amount of code supplies. Until measured there, these
      stay opt-in and must not be described as the premium default.
- [x] JSON import/export validates schema and hot-applies *(Phase 1.5 increment 3)*
- [x] Redacted export never includes credentials *(Phase 1.5 increment 3)*
- [x] Weather overlay works but failure does not affect slideshow — *provider licensing gate is open, see WEATHER_LICENSING.md*
- [x] Encrypted portable bundle imports on another device with passphrase — *verify the cross-device round trip on real hardware*
- [x] Sleep schedule works and recovers correctly — *verify on device across a real boundary*

## Remaining increments

None outstanding for the Phase 2 feature list in this document; see "Not yet done" under
increment 5 above for follow-on polish (reverse geocoding, overlay position pickers in
the UI, EXIF-read performance on large SMB libraries).

## Performance budget to measure (do this before making the premium modes default)

On the reference low-end device, with `fit_blur` + Ken Burns + slide enabled:

- crossfade/transition sustains **≥ 30 fps**;
- no dropped frames attributable to blur generation (it runs off the main thread);
- heap does not grow across a long run — the blur backdrop must not be retained beyond
  the current slide (spec §10.2: only current + next decoded bitmaps);
- confirm behaviour on a 1 GB-RAM device before enabling by default.
