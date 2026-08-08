# Roadmap — deferred features

Forward-looking work that is intentionally **not** part of the current phase gates.
Items here are planned, not committed, and must not derail Phase 1 (§22.2) or the
Phase 1.5 web UI. Each new source type is expected to slot behind the existing
`PhotoSource` abstraction (health / streaming scan / `openStream`) so the engine,
`MediaCache`, and UI stay untouched.

## Network photo-app sources (Phase 2+)

Beyond raw file protocols (SMB, done in Phase 1), the frame can read from NAS/cloud
*photo apps* that expose albums, server-side thumbnails, and structured metadata. The
first and highest-value target is **Synology Photos**, with File Station as a lower-risk
stepping stone; Immich and Nextcloud/WebDAV are documented alternatives to consider on
the same track.

### Synology Photos source (planned, Phase 2)

**Why:** Synology has large share in the target (prosumer NAS) market, and a photo app
gives the frame three things SMB cannot: **albums** as the selection unit ("show the
*Family 2024* album" / "Favorites"), **server-side thumbnails/transcodes** (request a
right-sized JPEG the NAS already generated instead of pulling full-res originals or
HEIC/RAW the device may not decode), and **structured metadata** (date taken, GPS,
captions) without per-file EXIF parsing.

**Which API — sequence it in two steps:**
1. **File Station API first** (official, documented, stable). Gives server-side
   thumbnails + download + search over HTTP/S — most of the performance and HEIC win,
   with a supported API. Essentially "SMB over HTTP with server thumbnails," no albums.
2. **Synology Photos (`SYNO.Foto.*` / `SYNO.FotoTeam.*`) as a follow-on** for the album
   UX. Note this API is **unofficial / reverse-engineered** and varies across DSM
   versions, so it carries an ongoing compatibility/maintenance cost; adopt it only once
   the source type is proven and budget for version drift.

**Architecture fit (small blast radius):**
- Implement `SynologyPhotosSource : PhotoSource`.
- `healthCheck` → log in via `SYNO.API.Auth`, obtain a session id (sid); verify the
  chosen space/album/folder is reachable.
- `scan(...)` → page the album/folder listing into Room as `ScanEvent`s (streaming,
  cancellable — same contract as SMB); `openToken` is the item's API item id, not a URL
  with secrets.
- `openStream(item)` → fetch the **transcoded thumbnail / download** bytes for that item;
  those bytes flow through the existing `MediaCache` (single byte-owner) and the engine
  unchanged.
- New `SourceError` cases to add: `AuthFailed` (already exists), plus
  `TwoFactorRequired`, `CertUntrusted`, `SessionExpired`, `QuickConnectUnavailable`.

**Setup UI (D-pad + web):** host/port, HTTPS + certificate-trust choice, username,
password, optional 2FA one-time code, and space/album selection. QuickConnect id as an
optional alternative to host/port.

**Secrets & permissions:** password (and any long-lived token) go in the existing
Keystore `SecretStore`; the sid is treated as sensitive and never logged. Only
`INTERNET` is needed — already declared, no new broad permissions. The HTTP client is
written on the current stack (no new third-party dependency, so no additional LGPL-style
obligation like jcifs-ng).

**Risks / caveats:**
- `SYNO.Foto` is unofficial and version-sensitive → plan for DSM-version handling and
  graceful degradation to File Station or SMB if the Photos API shape changes.
- HTTPS with self-signed certs and QuickConnect relay add auth failure modes.
- Requires a real DSM device to validate; the protocol can't be meaningfully
  unit-tested — build a thin, mockable HTTP layer so the *mapping* logic can be tested
  without a live NAS.

**Acceptance sketch (when built):** log in (incl. 2FA) and list at least one album;
randomized slideshow from a Synology album with server thumbnails; primary/fallback and
recovery behave as for SMB (session-expiry → re-auth → resume); credentials/sid never
appear in diagnostics exports; D-pad operable; no new forbidden permissions.

## Other candidate sources (unscheduled)

- **Immich** — documented API, self-hosted; good durability if album-from-NAS is the goal.
- **Nextcloud / generic WebDAV** — standard protocol; albums via collections/tags.
- **Cloud (Google Photos, etc.)** — separate consideration; heavier auth/policy and
  quota concerns; out of scope for the appliance's local-first posture unless demanded.


## Status update — merged primary pools (implemented)

A local folder and a NAS (or two different NAS protocols) can now feed a single
slideshow: `ActiveSource.alsoPlay` holds the extra kinds, the ViewModel activates each
as a slot, and the engine receives the union of the healthy ones.

Deliberate boundaries:

- **One source per kind.** The built-in source ids (`local_saf`, `smb`, `synology`,
  `webdav`) are fixed, so two SMB shares at once would collide. Supporting that means
  per-source generated ids, which touches indexing, credential scoping, cache routing
  and the web status shape — a separate piece of work, not a tweak.
- **Stale-cache playback stays a single-source behaviour.** With a healthy co-primary
  still serving live photos, falling back to another source's stale bytes would be
  strictly worse; an unreachable source is simply dropped from the pool until its
  recovery loop promotes it back.
