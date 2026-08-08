# FamilyPhotoFrame v50.6.2 — Photos Web Layout Fix

## Fixed

- Replaced the automatic 280 px Photos-card grid that produced four cramped columns.
- Added a deliberate two-column desktop layout with natural card heights.
- Arranged cards in horizontal pairs: SMB + discovered folders, Synology + WebDAV, indexing filters + index maintenance.
- Added a one-column breakpoint at 1199 px so form fields cannot overlap neighbouring cards.
- Kept labels and controls horizontally aligned on wide cards and stacked them on mobile.
- Added overflow protection for inputs, certificate fingerprints, folder search controls, and card contents.
- Added Web UI contract checks for the Photos layout.

## Version

- versionCode: 10
- versionName: 0.10.6.2-prerelease
