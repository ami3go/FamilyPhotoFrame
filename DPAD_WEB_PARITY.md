# Setup parity audit — D-pad vs web

Spec §4 (Phase 1.5) requires that a headless/wall-mounted frame can be set up **either**
from the remote (D-pad only, no touchscreen) **or** from the web UI. This is the audit of
every setup action and how it is reachable from each.

Legend: ✅ available · ⛔ intentionally unavailable · ➖ not applicable

| Setup action | D-pad on device | Web UI | Notes |
|---|---|---|---|
| Choose photo folder (SAF) | ✅ button → system picker | ⛔ | The SAF picker is a system UI that must run on the device; a remote grant is not possible by design. |
| Repair / re-grant folder | ✅ | ⛔ | Same reason. |
| Use bundled samples | ✅ | ⛔ | Reachable on device; low value remotely. |
| Rebuild index | ✅ | ✅ `rescan` | |
| NAS host / share / path / user / domain | ✅ text fields | ✅ | Non-secret fields. |
| Synology address / folder / user / thumbnails | ✅ text fields | ✅ | Web can edit an existing source, not create one (same as SMB). |
| Synology password / 2FA code | ✅ on device only | ⛔ **by design** | Same rule as the NAS password: no secret crosses cleartext HTTP (spec §15.6). |
| Synology certificate approval | ✅ on device only | 👁 read-only **by design** | Not a secret, but a security decision — a human must compare the fingerprint against DSM. Accepting a pin over cleartext HTTP would let anyone on the LAN pin a certificate of their choosing. Web shows the current fingerprint only. |
| NAS password | ✅ on device only | ⛔ **by design** | Spec §15.6: no secret may cross cleartext HTTP. See WEB_SECURITY.md. |
| Test NAS connection | ✅ | ✅ `/api/source/test` | Web tests the *saved* source using the on-device secret. |
| Seconds per photo | ✅ | ✅ | |
| Aspect mode (fit / fill) | ✅ | ✅ | |
| Overlays: clock, 24h, date, folder, weather-show, photo-date, caption, location | ✅ | ✅ (clock/date/folder checkboxes; the rest via `/api/config`, same as `clock24h`) | Photo-date/caption/location/weather-show added Phase 2 increments 5–6. |
| Overlay position (9-grid) | ✅ cycle buttons per overlay | ➖ | Added Phase 2 increment 6 on-device; still not exposed in the web UI beyond defaults. |
| Overlay opacity | ✅ −/+ stepper (Settings → Overlays) | ✅ `overlayOpacity` via `/api/config` | Added Phase 2 increment 7. Floored at 10% — an overlay at 0% looks broken rather than intentionally hidden; use the show/hide toggle for that. |
| Start on boot | ✅ toggle | ✅ `autoStartOnBoot` | |
| Quiet hours on/off, start, end | ✅ | ✅ `sleepEnabled` / `sleepStart` / `sleepEnd` | Times validated as `HH:mm` on both paths. |
| Night brightness | ✅ presets | ➖ | Presets rather than a slider so it is D-pad operable. |
| Enable web setup | ✅ toggle | ⛔ | Deliberate: the server cannot be switched on remotely (spec §15.5). |
| Show new pairing PIN | ✅ | ➖ | Physical access is the trust anchor. |
| Pair a browser | ✅ shows PIN + QR | ✅ enter PIN / scan QR | |
| Next / previous / pause | ✅ D-pad keys | ✅ `/api/control` | |
| View status & diagnostics | ✅ info overlay | ✅ status + diagnostics download | |
| Export / import configuration | ✅ SAF document | ➖ | Device-side (spec §7.0); the web UI could gain it later. |
| Export support bundle | ✅ | ✅ diagnostics download | |

## Result

**No setup action is unreachable by D-pad.** Every item above is operable from the remote,
so a frame with no touchscreen can be fully configured on the device. That satisfies the
D-pad half of the Phase 1.5 requirement.

The web UI is deliberately a *subset*: it cannot grant a SAF folder (system picker),
cannot receive a password (§15.6), and cannot enable itself (§15.5). These are security
and platform constraints, not gaps to close.

## Outstanding

- **Not yet demonstrated on Android TV hardware.** All controls are standard focusable
  Compose components and Back is handled, but focus order, IME behaviour for the NAS text
  fields, and overscan have not been verified on a real TV/leanback device. Until that
  test matrix passes, TV must not be marketed as supported (Contract Rule 12).
- Text entry on a TV remote is inherently slow; the QR pairing path (Phase 1.5
  increment 2) exists partly to avoid typing the NAS details on-device at all.


## Update — overlay anchors, opacity, and playback selection

The web setup page now exposes every overlay anchor (9-grid), the shared text opacity,
and the scrim toggle, so overlay customization is no longer on-device-only. The new
playback controls (photo order, favourites-only, unreachable-NAS policy) shipped to both
surfaces at the same time.

Still on-device only, by design rather than omission:

- **Favouriting and hiding an individual photo.** These act on *the photo currently on
  screen*, which is a frame-side concept; the web page has no photo browser to act on.
  The web page can still turn favourites-only playback on and off.
- **NAS passwords, 2FA codes, and certificate approval** (spec §15.6) — unchanged.
