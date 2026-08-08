# Play Data Safety Worksheet (Phase 0)

A worksheet to fill the Google Play **Data safety** form (spec §18.1). Phase 0 is
local-only; the honest answers are almost entirely "no."

## Summary answers

| Play question | Answer (Phase 0) |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | Not applicable — no data leaves the device. |
| Do you provide a way for users to request data deletion? | Uninstalling removes all app data; settings has source reset. |

## Data inventory

| Data | Collected? | Shared? | Leaves device? | Where it lives |
|---|---|---|---|---|
| Photos the user selects | Read for display only | No | No | Read in-place from the SAF folder; only an index (path/size/dates) is stored app-private. |
| Photo metadata index (path, size, modified date) | Stored locally | No | No | Room DB in app-private storage. |
| App settings (interval, overlays, chosen folder URI) | Stored locally | No | No | App-private DataStore JSON. |
| NAS/SMB connection details (host, share, path, user, domain) | Stored locally | No | No | App-private Room config; no password here. |
| NAS/SMB password | Stored locally, encrypted | No | No | Android Keystore (AES-GCM); ciphertext only, never logged. Re-entry required after device restore. |
| Cached NAS image bytes | Stored locally | No | No | App-private LRU MediaCache; excluded from backup. |
| Web pairing PIN | Stored locally, hashed | No | No | PBKDF2 salted hash only; PIN itself never persisted or logged. Destroyed when the server is disabled. |
| Exported configuration file | Stored where the user chooses | No | No | User-initiated SAF export; contains no password and no credential reference. |
| Encrypted portable backup | Stored where the user chooses | No | No | User-initiated, passphrase-protected (AES-256-GCM, PBKDF2). **Contains the NAS password in encrypted form** — the only artifact that does. Useless without the passphrase. |
| Weather coordinates | **Sent to a third party** when weather is enabled | No | No | User-typed latitude/longitude sent to the configured weather endpoint each refresh. Off by default; no device location is read; no identifier is attached. See WEATHER_LICENSING.md. |
| Weather API key | Stored locally, encrypted | No | No | Android Keystore; never in the settings file or exports. |
| Web session / CSRF tokens | In memory only | No | No | Never persisted, never logged, never backed up; cleared on stop or re-pair. |
| Diagnostic log | In-memory, bounded, redacted | No | No | RAM ring buffer; not persisted; no secrets/paths/GPS/photo bytes. |
| Personal identifiers, location, contacts, etc. | **None** | No | No | Not collected. |

## Notes for the listing

- The app requests access to a **single user-chosen folder** via the system folder
  picker; it cannot see other files.
- No analytics, ads, crash-reporting SDKs, or trackers are included in Phase 0.
- EXIF GPS is never read into diagnostics or transmitted.

## Backup exclusions (privacy hygiene — spec §18.3)

Auto Backup and Device-to-device transfer are configured (`res/xml/backup_rules.xml`,
`res/xml/data_extraction_rules.xml`) to **exclude** the database, DataStore, caches,
and any future diagnostics/thumbnail/session/secret files, so private indexes don't
travel through cloud backup.
