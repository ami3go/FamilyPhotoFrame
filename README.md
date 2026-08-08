# FamilyPhotoFrame

**A privacy-first digital photo frame for Android.** It plays photos from a folder you
choose — on the device, on your NAS, or over WebDAV — over your **local network only**.
No account, no cloud upload, no ads, no analytics vendor. Free.

> **Status: `0.12.13-prerelease`.** This is a real, actively developed project with a
> substantial feature set and an unusually thorough internal review process (see
> [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md)) — but it has not yet completed hardware
> soak testing or been signed for a public release build. Treat it as pre-release
> software, not a finished consumer app.

## Why

Most "digital photo frame" apps want a cloud account, a subscription, or broad photo/media
permissions. This one is built the other way around:

- **No broad storage/media permission is ever requested.** Local folders are accessed
  through Android's Storage Access Framework (a scoped grant to the one folder you pick),
  verified on every build by a permission-audit gate (see
  [`PERMISSIONS_JUSTIFICATION.md`](PERMISSIONS_JUSTIFICATION.md)).
- **No cloud dependency.** NAS/SMB, Synology, and WebDAV credentials and photo bytes never
  leave your LAN.
- **Secrets stay in the Android Keystore**, not in plain settings files.
- **The optional web control panel is LAN-only by design** — see
  [`WEB_SECURITY.md`](WEB_SECURITY.md) for exactly what that does and doesn't protect
  against.

## Features

- **Multiple photo sources**, mixable into one merged pool: local SAF folder, SMB/CIFS
  share, Synology File Station, WebDAV/Nextcloud, plus bundled sample photos as a
  no-permission fallback.
- **Folder-balanced shuffle** with a no-repeat-per-cycle guarantee, or ordering by
  date-taken, filename, or folder.
- **Favourites and hidden photos**, per-photo curation from the slideshow itself.
- **Portrait collage layouts** (two- or three-up) so portrait photos don't play alone
  with huge letterboxing.
- **Ken Burns motion, crossfades, and a full set of slide transitions.**
- **Weather overlay**, clock/date/folder overlays, configurable position and opacity.
- **Schedules**: quiet hours, brightness timelines, automatic re-scan intervals.
- **On-device web control panel** for setup and remote control from a phone or laptop —
  QR-code pairing, live status, settings, diagnostics, and encrypted backup/restore.
- **Boot auto-start** (best-effort; Android's background-start restrictions apply on
  newer OS versions).
- **D-pad friendly** for Android TV / set-top boxes, not just touch.

See [`CAPABILITIES.md`](CAPABILITIES.md) for the machine-checked, always-current list —
every row there is tied to a real symbol in the source tree and fails the build if it
goes stale.

## What's not implemented (honestly)

- Synology **Photos** albums (only File Station, i.e. raw file browsing) — see
  [`ROADMAP.md`](ROADMAP.md).
- QuickConnect relay support for Synology.
- An Immich source.
- Two sources of the same protocol at once (e.g. two separate SMB shares).
- Reverse geocoding for the location overlay (deliberately: no extra network call or
  permission for a place name).

## Requirements

- Android 5.0 (API 21) or newer to install. **API 26+ is the validated tier** — API 21–23
  works but is explicitly flagged as an unvalidated experimental range in
  [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md).
- No permissions beyond `INTERNET` / `ACCESS_NETWORK_STATE` (for NAS sources and the local
  web panel) and an optional `RECEIVE_BOOT_COMPLETED`.

## Building

```bash
git clone https://github.com/ami3go/FamilyPhotoFrame.git
cd FamilyPhotoFrame
./gradlew testDebugUnitTest   # pure-logic + JVM tests
./gradlew assembleDebug       # debug APK
```

A release build falls back to debug signing unless you configure a real keystore — see
[`BUILD_NOTES.md`](BUILD_NOTES.md). **Do not distribute a debug-signed APK.**

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module ownership and boundaries.
- [`CAPABILITIES.md`](CAPABILITIES.md) — the machine-checked feature inventory.
- [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) — what's genuinely unvalidated, and why.
- [`ROADMAP.md`](ROADMAP.md) — deferred, not-yet-started work.
- [`MANUAL_TEST_CHECKLIST.md`](MANUAL_TEST_CHECKLIST.md) — the device/hardware acceptance
  procedure this project runs before calling anything done.
- [`WEB_SECURITY.md`](WEB_SECURITY.md) — the threat model for the on-device web panel.

## License

[GNU GPLv3](LICENSE).
