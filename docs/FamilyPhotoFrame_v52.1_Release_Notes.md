# FamilyPhotoFrame v52.1 — Release Notes

## Version

- Package: `v52.1`
- Application: `0.12.1-prerelease`
- Version code: `21`
- Room schema: `7` (unchanged)
- Web asset revision: `v5200` (unchanged)

## Folder-balanced shuffle hardening

- Rejects commits containing unreserved, reordered-anchor, failed, or stale reservation members.
- Prevents collage selection from escaping the coordinator's same-folder photo-cycle reservation.
- Implements at-most-once startup behavior after commit-before-display recovery.
- Removes failed secondary collage members from the active candidate list immediately.
- Skips invalid newest history entries when restoring a playlist after startup.
- Makes the offline verification script work from a normal extracted release ZIP.

## Compatibility

No Room migration is required from v52.0. Existing shuffle cycles, reservations, history, playlists, and settings remain compatible.

## Verification

Run:

```bash
./scripts/verify-folder-balanced-v52.sh
```

Then run the Android build and instrumentation gates documented in `FOLDER_BALANCED_SHUFFLE_HARDENING_v52.1.md`.
