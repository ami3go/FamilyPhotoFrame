# Phase 1.5 — Headless / wall-mounted setup hardening

Spec §4 (Phase 1.5) + §15 (web config interface), gated by **§22.3**. Required before any
public beta that markets Android TV, SBC, or wall-mounted devices.

## Increment 1 — secure web server core ✅ (this drop)

- **Embedded HTTP server** (NanoHTTPD 2.3.1, BSD-3-Clause) bound to a single site-local
  IPv4 address, **disabled by default**, port configurable (default 8080).
- **Pairing**: 8-digit PIN shown on the frame; PBKDF2-salted-hash storage; constant-time
  compare; 5-attempt lockout; sessions with idle timeout; per-session CSRF token on every
  state-changing route. Full model in `WEB_SECURITY.md`.
- **REST surface** (spec §15.4): `GET /api/status`, `GET /api/config` (redacted),
  `POST /api/config`, `POST /api/control` (next/prev/pause/resume/rescan),
  `POST /api/source/test`, `GET /api/diagnostics`, `POST /api/pair`, `POST /api/logout`.
- **Setup page**: one self-contained HTML document (no CDN/framework — the frame may have
  no internet) with pairing, live status, controls, slideshow/overlay config, non-secret
  NAS fields, and a redacted diagnostics download.
- **Settings → Web setup**: enable toggle, LAN URL and PIN displayed large for reading at
  a distance, and "show a new PIN" (which signs out all web devices).
- **§15.6 decision — device-only credential entry.** No route accepts a password; the web
  UI edits only non-secret fields. This closes the §15.6 release blocker without shipping
  unproven HTTPS/PAKE.
- **Tests**: `WebSecurityTest` (unit) covers pairing, wrong PIN, lockout, unknown token,
  idle expiry, deadline refresh, CSRF enforcement, re-pair invalidation, reset.

**Try it:** Settings → Web setup → enable. Open the shown URL from a phone on the same
Wi-Fi and enter the PIN. On the emulator the frame's site-local address is reachable from
the host via the usual emulator port-forwarding rules rather than directly.

## Increment 2 — QR pairing + end-to-end security test ✅ (this drop)

- **QR pairing (spec §15.3).** Settings → Web setup now shows a scannable QR alongside
  the PIN, encoding `http://<lan-ip>:<port>/pair?t=<one-time token>`. The token is
  single-use, expires in 5 minutes, dies with the PIN or the server, and is stripped
  from the browser address bar on arrival. Encode-only (ZXing core) — no camera, no new
  permission.
- **End-to-end security test (spec §22.5).** `WebConfigServerSecurityTest` starts a
  **real server on loopback and drives it over real HTTP**: unauthenticated reads of
  config/status/diagnostics are refused, unauthenticated control never reaches the app,
  wrong PINs and forged sessions fail, QR tokens cannot be replayed, state changes
  without a valid CSRF token are refused, cross-site `Origin` is refused, logout
  revokes, and the config response contains no password or credential ref. It runs in
  `testDebugUnitTest` — no device required.

## Increment 3 — config backup/restore + parity audit ✅ (this drop)

- **SAF config export/import (spec §7.0).** Settings → Backup and restore writes the
  configuration to a document the user chooses and reads it back, so a frame can be
  backed up, cloned, or restored after a reset. No storage permission is involved.
  **Credential references are stripped on export** (Contract Rule 5); on import the
  stored secret is reused only when the very same share and user are already configured
  on that frame, otherwise the password must be re-entered on the device.
  Foreign, corrupt, and newer-version files are rejected with a clear message.
- **Support bundle export** to a SAF document, reusing the existing redacted renderer.
- **D-pad ↔ web parity audit** → `DPAD_WEB_PARITY.md`: every setup action is reachable
  from the remote; the web UI is deliberately a subset (no SAF grant, no password, no
  self-enable).
- **Tests**: `ConfigTransferTest` (12 cases) covering redaction, round-trip, rejection of
  invalid/foreign/too-new files, and the credential-reuse merge rules.

## §22.3 acceptance — status

- [x] Web UI pairs securely through **PIN or scanned QR** (single-use token)
- [x] `GET /api/config` never exposes secrets (redacted; only `smbPasswordSet` boolean)
- [x] State-changing APIs require an authenticated session **and** CSRF token
- [x] Web UI can configure local/SMB source settings *(non-secret fields; passwords are
      device-only by §15.6 design)*
- [x] Diagnostic export works and is redacted
- [ ] Android TV / remote-only setup path **demonstrated on a device** — not yet run
- [x] End-to-end security test proving unauthenticated requests cannot read config or
      control the frame (§22.5) — `WebConfigServerSecurityTest`

## Remaining work for Phase 1.5

- **Demonstrate remote-only setup on Android TV hardware** (the one §22.3 item that
  cannot be satisfied from code alone — see DPAD_WEB_PARITY.md).
- Optional: expose overlay position/opacity and config import/export in the web UI.
