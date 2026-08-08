# Capability manifest

Machine-checked inventory of what this app actually implements.

`scripts/check-consistency.py` verifies every row: each capability names a symbol or file
that must exist in the source tree. If a capability is removed, the check fails; if a
doc claims something is "not implemented" while its row still passes, the doc is wrong.

**Why this file exists.** `README.md`, `KNOWN_LIMITATIONS.md` and `ROADMAP.md` have each
described this project inaccurately more than once — claiming features were missing that
had already shipped (QR pairing, R8/minification, release signing, WebDAV, folder
filters, recovery-policy tests). Prose drifts silently; this table cannot, because the
build fails when it does. Prefer this file over any narrative doc, and prefer the code
over this file.

Format: `capability | evidence` where evidence is `path::symbol` or `path`.

<!-- CAPABILITIES:BEGIN -->
| Capability | Evidence |
| --- | --- |
| Local SAF folder source | `app/src/main/java/com/example/familyphotoframe/data/source/SafPhotoSource.kt` |
| Bundled sample photos | `app/src/main/java/com/example/familyphotoframe/data/source/AppPrivateFallbackSource.kt` |
| SMB/CIFS source | `app/src/main/java/com/example/familyphotoframe/data/source/SmbPhotoSource.kt` |
| Synology File Station source | `app/src/main/java/com/example/familyphotoframe/data/source/SynologyFileStationSource.kt` |
| WebDAV / Nextcloud source | `app/src/main/java/com/example/familyphotoframe/data/source/WebDavPhotoSource.kt` |
| Remote byte cache (disk LRU) | `app/src/main/java/com/example/familyphotoframe/data/cache/MediaCache.kt` |
| Stale-cache playback when offline | `app/src/main/java/com/example/familyphotoframe/data/cache/MediaCache.kt::getIfCached` |
| Merged multi-source primary pool | `app/src/main/java/com/example/familyphotoframe/domain/engine/SourcePoolPolicy.kt` |
| Shuffle with no repeats | `app/src/main/java/com/example/familyphotoframe/domain/randomize/PlaybackQueue.kt` |
| Persistent folder-balanced non-repeating shuffle | `app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/FolderBalancedShuffleCoordinator.kt` |
| Atomic shuffle reservation and history | `app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleRepository.kt::commitPrepared` |
| Date-taken ordering | `app/src/main/java/com/example/familyphotoframe/data/db/PhotoDao.kt::displayableIdsByDateTakenDesc` |
| Favourite / hide curation | `app/src/main/java/com/example/familyphotoframe/data/db/PhotoDao.kt::setFavorite` |
| Folder ("album") selection | `app/src/main/java/com/example/familyphotoframe/data/db/PhotoDao.kt::folderSummaries` |
| Portrait collage playback | `app/src/main/java/com/example/familyphotoframe/domain/engine/PortraitCollagePolicy.kt` |
| Source recovery with backoff | `app/src/main/java/com/example/familyphotoframe/domain/engine/RecoveryPolicy.kt` |
| Scheduled automatic index refresh | `app/src/main/java/com/example/familyphotoframe/domain/schedule/RescanSchedule.kt` |
| Quiet hours / sleep schedule | `app/src/main/java/com/example/familyphotoframe/domain/schedule/SleepSchedule.kt` |
| Weather overlay | `app/src/main/java/com/example/familyphotoframe/data/weather/WeatherRepository.kt` |
| On-device web control panel | `app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt` |
| Web pairing QR code | `app/src/main/java/com/example/familyphotoframe/web/QrCodes.kt` |
| Keystore-backed secret storage | `app/src/main/java/com/example/familyphotoframe/data/secret/KeystoreSecretStore.kt` |
| TLS certificate pinning | `app/src/main/java/com/example/familyphotoframe/data/source/CertPinning.kt` |
| Plain + encrypted config transfer | `app/src/main/java/com/example/familyphotoframe/data/settings/PortableBundle.kt` |
| Boot auto-start | `app/src/main/java/com/example/familyphotoframe/platform/BootReceiver.kt` |
| Engine/DAO type-check harness | `scripts/verify-engine-types.sh` |
| Room SQL validation (real SQLite) | `scripts/verify-sql.py` |
| Migration chain validation | `scripts/verify-migrations.py` |
| Durable JSONL diagnostics log | `app/src/main/java/com/example/familyphotoframe/data/diagnostics/FileDiagnosticsSink.kt` |
| Diagnostics redaction at the sink | `app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsJsonl.kt::redact` |
| Heap/uptime sampling | `app/src/main/java/com/example/familyphotoframe/data/diagnostics/RuntimeSampler.kt` |
| Diagnostics end-to-end harness | `scripts/verify-diagnostics-e2e.sh` |
| Phase 1 evidence analyser | `scripts/analyze-diagnostics.py` |
| Bounded redacted diagnostics | `app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsLog.kt` |
<!-- CAPABILITIES:END -->

## Not implemented

Verified absent at the time of writing. These are the real remaining gaps:

- **Synology Photos (`SYNO.Foto`) albums** — File Station only. Reverse-engineered and
  DSM-version-sensitive; unverifiable without hardware.
- **QuickConnect relay** — `SourceError.QuickConnectUnavailable` exists in the taxonomy
  but is never produced.
- **Immich source** — not started.
- **Two sources of the same kind** (e.g. two SMB shares) — the built-in source ids are
  fixed constants, so this needs per-source generated ids.
- **Reverse geocoding** for the location overlay — deliberately declined (network call
  plus a new permission).
