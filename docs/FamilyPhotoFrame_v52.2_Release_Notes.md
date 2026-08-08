# FamilyPhotoFrame v52.2 Release Notes

## Release identity

- Package: v52.2
- Application: 0.12.2-prerelease
- Version code: 22
- Room database: v8
- Web assets: v5220
- Specification: FPF-FEAT-SHUFFLE-002 v1.1

## Summary

v52.2 completes the remaining source-level requirements of the folder-balanced non-repeating shuffle specification. It extends the persistent v52.0/v52.1 coordinator with exact duplicate identity, canonical direct-folder actions, folder-local retry/skip handling, complete diagnostics and health reporting, correct new-install and upgrade mode mapping, and folder-lazy selection that does not load the entire eligible photo library into memory.

## Added and corrected

- Explicit Sequential, Global photo shuffle, and Folder-balanced shuffle compatibility behavior.
- Folder-balanced default for fresh installations and newly created playlists.
- One-time migration of legacy least-recent random settings to global no-repeat shuffle.
- Additive Room v7→v8 migration.
- Persisted canonical direct-directory identity and background SHA-256 content identity.
- Same-folder byte-identical duplicate suppression without cross-folder collapse.
- Original-byte hashing for local, SMB, WebDAV, and Synology sources.
- Android and Web Control actions for Preview this folder once and Use this folder in playlist.
- Two-attempt folder-local retry followed by one explicit skip.
- Required 30 s / 2 min / 5 min / 15 min source backoff.
- Missing scope-restored and folder-deferred diagnostics.
- Complete Android/Web shuffle health fields.
- Folder metadata query plus current-folder-only member loading.
- Per-selection timing evidence for the 50 ms cached/local and 250 ms network-metadata targets.
- Expanded unit, instrumentation, migration, SQL, deterministic endurance, and compatibility gates.

## Verification

Offline release gates pass, including all 101 Room queries, migration replay through v8, 32 task-completion contracts, 100 folder cycles, ten photo cycles per folder, a 10,000-member cycle, a deterministic 24-hour-equivalent dynamic simulation, Web Control parsing, and the full pure-logic suite.

The complete Android Gradle and connected-device gates must be run locally because this build environment cannot download the uncached Gradle 8.9 distribution and has no Android device attached.

```bash
./scripts/verify-folder-balanced-v52.sh
./scripts/verify-folder-balanced-android.sh
```

See `docs/FOLDER_BALANCED_SHUFFLE_DEVICE_ACCEPTANCE_v52.2.md` for the hardware and network endurance procedure.
