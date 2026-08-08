# FamilyPhotoFrame v51.1 — Remembered Browser Pairing

Version: `0.11.1-prerelease` (`versionCode 15`)

## Added

- Optional remembered-browser pairing with session-only, 1 hour, 1 day, 1 week, 1 month, 1 year, Forever, and custom expiry.
- Automatic browser restoration after tab/browser or frame restart.
- Rotating 256-bit remembered credentials stored only as HMAC hashes on the frame.
- Two-tab rotation grace and replay-triggered trust revocation.
- Browser labels, status, last-used time, expiry, and revocation controls.
- Android and web policy management.
- Explicit `Sign out` and `Sign out and forget this browser` actions.
- PIN step-up dialog for sensitive web policy and revoke-all actions.
- PIN regeneration defaults to revoking all remembered browsers, with an explicit local option to keep them.
- 30-minute active-session idle timeout, 12-hour absolute timeout, global limit 8, and per-browser limit 2.
- Per-client and global remembered-exchange throttling.
- Clock rollback, Keystore loss, backup exclusion, periodic cleanup, and Android 5 compatibility handling.

## Security defaults

- Remembered browsers are disabled by default.
- Session-only remains the default pairing choice.
- Forever is disabled by default.
- The UI warns that the current embedded server uses unencrypted HTTP and must be used only on a trusted LAN.
