# FamilyPhotoFrame v52.4 — Remote source start fix

## Fixed

- The first-run screen now offers **Connect NAS or remote server** alongside the
  local-folder and sample-photo choices.
- The same recovery action is available when a configured source fails or a scanned
  folder contains no usable photos.
- The action opens the existing Photos source setup directly, where SMB, Synology File
  Station, and WebDAV/Nextcloud are supported.
- Remote source forms now appear before the unrelated browser-upload controls.

## Targeted source audit

- Confirmed that SMB, Synology File Station, and WebDAV source implementations remain
  wired through the settings screen, ViewModel save/test actions, secret storage, source
  construction, health checks, indexing, and slideshow activation.
- The observed regression was a missing first-run navigation route; the remote-source
  implementations themselves had not been removed.

## Verification note

Static call-site, resource, and archive-integrity checks passed. The Gradle unit-test run
could not start in the isolated build environment because Gradle 8.9 was not cached and
external Gradle distribution downloads were unavailable.
