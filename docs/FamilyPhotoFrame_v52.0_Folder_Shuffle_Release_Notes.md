# FamilyPhotoFrame v52.0 — Release Notes

## Version

- Package: `v52.0`
- Application: `0.12.0-prerelease`
- Version code: `20`
- Room schema: `7`
- Web asset revision: `v5200`

## Main feature

Added a persistent folder-balanced, non-repeating shuffle mode. Every eligible folder receives one turn before a folder repeats, and every folder maintains its own non-repeating photo cycle.

## Reliability

- Atomic reserve/prepare/commit state machine.
- Restart and reboot recovery for interrupted reservations.
- Persistent, bounded presentation history.
- Same-folder retry after decode failure.
- Three-failure quarantine.
- Idempotent rescan reconciliation.
- Source-level outage deferral and bounded skip behavior.
- Healthy sources continue while another source is unavailable.

## Controls and diagnostics

- Android and Web Control playback settings.
- Folder/photo cycle progress and health details.
- Reset active, reset all, and reset-and-clear-history actions.
- New shuffle lifecycle diagnostics.

## Compatibility

Existing playback selections are retained. Existing global shuffle and date/least-recent modes remain available. New user-created playlists default to folder-balanced shuffle.

## Build verification

Offline source, SQL, migration, web, consistency, and endurance checks pass. A complete Gradle/Android build must be run on a machine with Gradle 8.9 available; see `docs/FOLDER_BALANCED_SHUFFLE_IMPLEMENTATION_v52.0.md`.
