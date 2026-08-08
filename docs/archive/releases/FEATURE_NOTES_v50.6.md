# FamilyPhotoFrame v50.6 — Web UI Modernisation

Implements FPF-FEAT-WEBUI-001 phases 1–5.

## Highlights

- Responsive nine-tab web application shell.
- Grouped Photos, Playback, Display, Schedule, Device, Diagnostics, Backup, and About settings.
- Shared low-resolution preview generated from the committed prepared slide.
- Authenticated preview blob with revision/ETag reuse and hidden-tab polling reduction.
- Previous, Play/Pause, Next, Restart interval, and Rescan controls.
- Revisioned settings writes and structured validation/conflict responses.
- SMB, Synology, WebDAV, discovered-folder, filter, collage, and transition controls.
- Paginated diagnostics with severity/category/text filters and bounded browser retention.
- Settings backup validation, import, export, and rollback of the latest import.
- Confirmed maintenance actions and pairing/CSRF enforcement.
- Responsive accessibility, reduced-motion, request-size, and security hardening.

Version: `0.10.6-prerelease` (`versionCode 8`).
