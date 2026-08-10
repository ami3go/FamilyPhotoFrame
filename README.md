<p align="center">
  <img src="docs/assets/github-banner.jpg" alt="FamilyPhotoFrame — Turn any Android device into a private digital photo frame" width="100%">
</p>

# FamilyPhotoFrame

**A privacy-first digital photo frame for Android.** Turn an old phone, tablet, Android TV, or dedicated Android display into a continuously updating family photo frame — using photos from local storage or your own NAS.

**Local-first · No account · No cloud upload · No ads · No analytics · Free & open source**

> **Status: `0.12.13-prerelease`.** FamilyPhotoFrame is actively developed and already has a substantial feature set, but it has not yet completed hardware soak testing or been signed for a public release build. Treat it as pre-release software. See [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) for the current qualification status.

## Why FamilyPhotoFrame?

Most "digital photo frame" apps depend on a cloud account, subscription, or broad photo/media permissions. FamilyPhotoFrame is designed the other way around:

- **No broad storage/media permission is ever requested.** Local folders are accessed through Android's Storage Access Framework (a scoped grant to only the folder you choose), verified on every build by a permission-audit gate. See [`PERMISSIONS_JUSTIFICATION.md`](PERMISSIONS_JUSTIFICATION.md).
- **No cloud dependency.** NAS/SMB, Synology, and WebDAV credentials and photo bytes stay on your local network.
- **Secrets stay in the Android Keystore**, not in plain settings files.
- **The optional web control panel is LAN-only by design.** See [`WEB_SECURITY.md`](WEB_SECURITY.md) for the exact threat model and limitations.
- **Designed for always-on use.** Folder-balanced playback, schedules, transitions, remote control, diagnostics, and boot auto-start make the app suitable for a dedicated photo-frame device.

## Features

- **Multiple photo sources**, mixable into one merged pool: local SAF folder, SMB/CIFS share, Synology File Station, WebDAV/Nextcloud, plus bundled sample photos as a no-permission fallback.
- **Folder-balanced shuffle** with a no-repeat-per-cycle guarantee, or ordering by date taken, filename, or folder.
- **Favourites and hidden photos**, with per-photo curation directly from the slideshow.
- **Portrait collage layouts** with two or three portrait photos in one frame, avoiding large empty side areas.
- **Ken Burns motion, crossfades, and multiple slide transitions.**
- **Weather, clock, date, and folder overlays** with configurable position and opacity.
- **Schedules** for quiet hours, brightness timelines, and automatic re-scan intervals.
- **On-device web control panel** for setup and remote control from a phone or laptop, including QR-code pairing, live status, settings, diagnostics, and encrypted backup/restore.
- **Boot auto-start** for dedicated photo-frame devices (best effort; newer Android versions impose background-start restrictions).
- **D-pad friendly** operation for Android TV and set-top boxes as well as touch devices.

See [`CAPABILITIES.md`](CAPABILITIES.md) for the machine-checked, always-current feature inventory. Every row there is tied to a real symbol in the source tree and fails the build if it goes stale.

## Photo sources

| Source | Status | Notes |
|---|---|---|
| Local Android folder | ✅ | Storage Access Framework; no broad media permission |
| SMB / CIFS | ✅ | Direct LAN access to NAS/shared folders |
| Synology File Station | ✅ | Raw file browsing |
| WebDAV / Nextcloud | ✅ | Remote/local WebDAV storage |
| Bundled sample photos | ✅ | No-permission fallback |
| Synology Photos albums | 🚧 | Planned; see roadmap |
| Immich | 🚧 | Planned |

## What's not implemented yet

- Synology **Photos** albums (only File Station/raw file browsing is currently supported) — see [`ROADMAP.md`](ROADMAP.md).
- QuickConnect relay support for Synology.
- An Immich source.
- Two sources of the same protocol at once (for example, two separate SMB shares).
- Reverse geocoding for the location overlay; this is deliberately omitted to avoid an additional network call or permission just to obtain a place name.

## Requirements

- Android 5.0 (API 21) or newer to install.
- **API 26+ is the validated tier.** API 21–23 works but is currently treated as an unvalidated experimental range; see [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md).
- No permissions beyond `INTERNET` / `ACCESS_NETWORK_STATE` for NAS sources and the local web panel, plus optional `RECEIVE_BOOT_COMPLETED`.

## Building

```bash
git clone https://github.com/ami3go/FamilyPhotoFrame.git
cd FamilyPhotoFrame

./gradlew testDebugUnitTest
./gradlew assembleDebug
```

A release build falls back to debug signing unless you configure a real keystore. See [`BUILD_NOTES.md`](BUILD_NOTES.md). **Do not distribute a debug-signed APK.**

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module ownership and architectural boundaries.
- [`CAPABILITIES.md`](CAPABILITIES.md) — machine-checked feature inventory.
- [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) — what remains unvalidated and why.
- [`ROADMAP.md`](ROADMAP.md) — deferred and not-yet-started work.
- [`MANUAL_TEST_CHECKLIST.md`](MANUAL_TEST_CHECKLIST.md) — device/hardware acceptance procedure.
- [`WEB_SECURITY.md`](WEB_SECURITY.md) — threat model for the on-device web control panel.
- [`BUILD_NOTES.md`](BUILD_NOTES.md) — build and signing notes.

## Project philosophy

FamilyPhotoFrame is intended to make photos you already own visible again without requiring them to pass through somebody else's cloud. A spare Android device and your existing photo library should be enough to build a capable, private digital frame.

## License

[GNU GPLv3](LICENSE).
