# Web setup server — security model

Implements the spec §15 web contract and the §15.5 threat model. This document is the
record required before any public beta that markets headless/wall-mounted setup.

## Posture

- **Off by default.** The server never starts unless the user enables it in Settings →
  Web setup. Disabling it stops the server and destroys all sessions and the PIN.
- **LAN-only.** The server binds to a single **site-local IPv4 address** of the device.
  If the device has no private network interface it refuses to start. There is no UPnP,
  no NAT traversal, no cloud relay, and no WAN exposure (spec §15.5).
- **Local setup surface, not remote management.** Intended for trusted home networks.

## Authentication (spec §15.2)

| Control | Implementation |
|---|---|
| Pairing | 8-digit PIN shown on the frame, **or** a scanned QR code carrying a single-use token |
| PIN storage | PBKDF2-HMAC-SHA1, 20 000 iterations, 128-bit random salt — the PIN itself is never persisted |
| PIN comparison | Constant-time (`MessageDigest.isEqual`) |
| Rate limiting | 5 failed attempts → 15-minute lockout; correct PINs are refused while locked |
| Session | 256-bit random token, required on every route except `/` and `/api/pair` |
| CSRF | Separate 256-bit per-session token required on **all** state-changing requests |
| Idle timeout | Configurable (default 30 min); expired sessions are purged on use |
| Re-pairing | Generating a new PIN invalidates every existing session |

**QR pairing (spec §15.3).** The frame can display a QR encoding
`http://<lan-ip>:<port>/pair?t=<token>`. The token is 256-bit random, **single use**,
expires after 5 minutes, is invalidated whenever the PIN is regenerated or the server
stops, and is subject to the same lockout as PIN attempts. The landing page immediately
POSTs it to `/api/pair` and strips it from the address bar (`history.replaceState`) so
it is not left in browser history or a shared URL. QR is *encode-only* — the app never
scans, so no camera permission is added.

Additional request filtering: `Host` must match the bound address; any cross-site
`Origin` is rejected; **CORS is never enabled**; responses are `no-store`; internal
errors return a generic message so stack traces never reach the network.

## Credential handling — §15.6 decision

Spec §15.6 forbids sending SMB passwords over cleartext HTTP and offers four options.
**This build implements option 4: device-only credential entry.**

- The web UI can edit only **non-secret** fields (host, share, path, user, domain, and
  slideshow/overlay settings).
- Passwords are typed **only on the frame itself** and stored in the Keystore
  `SecretStore`.
- There is deliberately **no API route that accepts a password.** `POST /api/source/test`
  tests the *already saved* source using the secret already on the device.
- `GET /api/config` returns a redacted view: no password, no `credentialRef` — only a
  boolean `smbPasswordSet`.

This keeps the release blocker in §15.6 closed without shipping unproven HTTPS or PAKE.
If web credential entry is ever wanted, option 1 (pairing-pinned local HTTPS) or option 2
(session-key handoff) must be implemented and reviewed first.

## Logging

Pairing events are recorded as codes only (`WEB_PAIRED`, `WEB_PAIR_REJECTED`,
`WEB_PAIR_LOCKED`, `WEB_CONTROL`, `WEB_STARTED`, `WEB_STOPPED`). PINs, session tokens,
CSRF tokens, and passwords are **never** written to the diagnostics ring buffer, and the
diagnostics export is redacted (spec §17.2).

## Known gaps (must close before public beta)

- **Traffic is plain HTTP.** Pairing and sessions are protected, but content is not
  encrypted; anyone on the LAN can observe non-secret config traffic. Acceptable only
  because no secret ever crosses the wire (§15.6 option 4).
- **No third-party penetration test.** §22.5's automated requirement is now met by
  `WebConfigServerSecurityTest`, which drives a **real running server over real HTTP**
  and proves unauthenticated clients cannot read config or control the frame, that CSRF
  is enforced, that forged/replayed tokens fail, and that the config response discloses
  no secrets. An independent human security review is still recommended before a paid
  release.


## Synology certificate pinning (Phase 2 increment 14)

The frame can trust one user-approved self-signed certificate per Synology source. This
is **not** a trust-all switch and must not be widened into one: `PinnedCertTrustManager`
falls back to a single exact SHA-256 leaf fingerprint only after normal platform
validation fails, so substituting any other certificate still fails the handshake.

Rules that must hold if this code is touched:

- A blank or null pin must never match anything (a missing pin is not permission).
- Fingerprints must be displayed in full; a truncated fingerprint cannot be compared.
- Approval must stay an explicit user action, never a side effect of testing or saving.
- Hostname verification stays at the platform default.
- `ConfigTransfer.merge` must never import a pin from a file — only preserve one this
  device already approved, on a matching connection.
- `/api/config` must never *accept* a pin over HTTP. It is exposed read-only so a remote
  admin can see what the frame trusts; making it writable would let anyone on the LAN pin
  a certificate of their choosing and defeat the feature entirely.
