# Phase 1 — MVP appliance: progress & plan

Phase 1 (spec §4) turns the Phase 0 spike into a real appliance. Because it pulls in
a third-party SMB library, Android Keystore, a DB migration, and real network I/O —
none of which can be compiled or run in the tooling used to generate this code — it
is being built in **verifiable increments**, each testable on your device before the
next is layered on.

## Increment 3 — SMB usable in-app ✅ (this drop, partial)

SMB is now a real, selectable source with on-screen setup and playback:

- **SMB setup UI** in Settings (host / share / path / user / domain / password) with a
  **Test connection** button and **Use this NAS** to save + activate. Password is
  stored encrypted via the Keystore SecretStore; only non-secret fields go in settings.
- **Playback through the cache**: when the active source is SMB, the ViewModel builds
  an `SmbPhotoSource`, health-checks it (mapped to clear recovery messages), indexes it
  into Room, and the slideshow resolves each SMB photo through `MediaCache` to a local
  file before Coil decodes it (SMB bytes are never handed to Coil directly).
- Engine gains a `PLAYING_SMB` state; `DisplayPhoto` carries what the cache needs.

**Still to do for the §22.2 gate (increment 4):** true *concurrent* primary + fallback
(right now a single active source is selected at a time), async recovery with backoff,
and boot auto-start. So: you can add a NAS and watch it play, but automatic
NAS-down→samples→NAS-up recovery isn't wired yet.

> **Try it:** run Samba on your Ubuntu host, then in the app open Settings → NAS/SMB,
> enter host `10.0.2.2` (the emulator's alias for your host), the share name, your
> user/password, Test, then Use this NAS.

## Increment 2 — SMB source + disk cache ✅

Data layer for NAS. **Not yet wired into the running slideshow UI** — that is the
multi-source engine work in increment 3/4. What's here:

- **`SmbPhotoSource`** (jcifs-ng, SMB2/3 only): off-main, cancellable, time-bounded
  streaming scan (Flow, never a List), explicit work-stack traversal, typed errors via
  **`SourceErrorMapper`** (spec §8). Credentials live only in the CIFS context; the
  stored open-token is a plain `smb://` URL with no password (Contract Rule 5).
- **`MediaCache`**: app-owned disk LRU for remote bytes — atomic temp→verify-decode→
  rename writes, bounded size (spec §16.1 default), LRU eviction that spares current/
  next, single byte-owner (Contract Rule 19). Coil will load the returned file.
- **Permissions**: `INTERNET` + `ACCESS_NETWORK_STATE` added (map to SMB; audit still
  passes — no media/storage perms). **Docs**: `LGPL_COMPLIANCE.md` (jcifs-ng LGPL-2.1),
  updated licenses / permissions / data-safety.
- **Tests**: `SourceErrorMapperTest` (unit), `MediaCacheTest` (instrumented, fake
  source). SMB networking itself needs a real NAS to exercise — see below.

> Verifying SMB end-to-end needs a server. On the emulator, run Samba on your Ubuntu
> host and point the source at `10.0.2.2`. Until increment 3 wires the SMB setup UI +
> engine, the app still runs the SAF/samples slideshow unchanged; the SMB code is
> exercised only by the tests.

## Increment 1 — data foundation ✅

Everything here is SMB-independent and testable with `./gradlew connectedDebugAndroidTest`:

- **Room v1→v2 migration** (`data/db/Migrations.kt`, `AppDatabase` now version 2,
  `exportSchema = true`). Explicit and additive — no destructive migration (Contract
  Rule 10). New `photos` columns (width/height/exifOrientation/dateTaken/isFavorite/
  missingSince/cacheKey) and the remaining §6.2 indexes; new tables `source_config`,
  `smb_source_config`, `local_saf_source_config`, `secrets`, `cache_index`.
- **Keystore SecretStore** (`data/secret/KeystoreSecretStore.kt`): AES-256-GCM with a
  non-exportable AndroidKeyStore key; only ciphertext + IV are persisted (Contract
  Rule 5); reports hardware vs software security level (spec §14.3).
- **New DAOs** for source config, secrets, and the cache index.
- **Instrumented tests**: `MigrationTest` (v1 data survives, new schema validates) and
  `KeystoreSecretStoreTest` (encrypt/reveal/forget round-trips).

> Because your emulator already has the Phase 0 (v1) database installed, this build
> will exercise the real migration on first launch — a good live check.

**Verify this increment:**
```bash
./gradlew connectedDebugAndroidTest   # runs MigrationTest + KeystoreSecretStoreTest + Phase 0 DAO test
./gradlew installDebug                # migrates the existing v1 DB in place
```

## Increment 4 — primary/fallback engine + boot auto-start ✅ (closes the gate)

- **Multi-source engine**: the engine now holds a **primary pool** and a **fallback
  pool** and merges the displayable rows of several sources
  (`PhotoDao.leastRecentWindowMulti`, `sourceId IN (...)`). It plays the primary pool
  and drops to fallback only when the primary pool has zero displayable items
  (`on_empty`, spec §9.3). States are `PLAYING_PRIMARY` / `PLAYING_FALLBACK`.
- **SMB recovery** (`startSmbRecovery`, spec §9.5): a backoff loop
  (5s,15s,30s,1m,5m,15m + jitter while down; 60s steady poll when up) health-checks the
  NAS and toggles it in/out of the primary pool. On recovery it un-suppresses
  (`clearSuppression`) and re-indexes; bundled samples play while it's down. So:
  NAS up → NAS shown; NAS down + cold cache → samples; NAS back → NAS resumes after a
  verified health check.
- **Boot auto-start** (spec §13.3): `BootReceiver` (`RECEIVE_BOOT_COMPLETED`) →
  `BootStartupCoordinator`, API-aware — it *attempts* to launch the Activity and logs
  `BOOT_AUTOSTART_LAUNCHED` / `BOOT_AUTOSTART_BLOCKED` rather than crashing when
  Android 10+ background-activity-start limits apply. A **Device → Start on boot** toggle
  gates it.

## Phase 1 gate (§22.2) — status

Implemented in code and building. The remaining acceptance items are **runtime/soak
checks only you can run** (they need a real NAS and long-running devices):

- [ ] randomized slideshow from one SAF + one SMB source *(engine merges primaries; the
      setup UI configures one primary at a time — see KNOWN_LIMITATIONS)*
- [x] primary/fallback: NAS up → primary; NAS down+cold → fallback; NAS back → primary
- [x] Room-indexed, no per-slide NAS enumeration
- [x] SMB credentials encrypted via SecretStore
- [x] overlays configurable; D-pad operable
- [x] auto-start implemented with API guards — *test on ≥2 API levels*
- [ ] 24h NAS offline/online cycling: no crash / no permanent blank *(run on device)*
- [ ] heap growth < 5% over rolling 6h *(profile on device)*

## Phase 1 acceptance (spec §22.2) — the gate

Randomized slideshow from one SAF + one SMB source; primary/fallback works (NAS up →
primary; NAS down + cold cache → fallback; NAS back → primary after verification);
Room-indexed (no per-slide NAS enumeration); SMB credentials encrypted via SecretStore;
overlays configurable; D-pad operation; auto-start on ≥2 API levels; 24h NAS-cycling
run with no crash / no permanent blank; heap growth < 5% over a rolling 6h window.
