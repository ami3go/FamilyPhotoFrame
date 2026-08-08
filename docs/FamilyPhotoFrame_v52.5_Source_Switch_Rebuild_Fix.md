# FamilyPhotoFrame v52.5 — source-switch and rebuild fix

## Fixed

- Switching from an unsuccessful Synology File Station setup to SMB now cancels the
  obsolete Synology activation instead of waiting for it in the settings collector.
- **Rebuild photo list** now reads the latest persisted source configuration. It no
  longer re-applies a stale UI snapshot that can still identify Synology after SMB has
  already been saved.
- A source/configuration change cancels obsolete index and background hash work so the
  new SMB scan is not queued behind an old remote scan.
- Repeated rebuild requests for the same configuration continue to share the active
  physical scan.
- Replacing a source password under the same credential reference explicitly refreshes
  the source, even when the non-secret settings object is otherwise unchanged.
- A source flow that aborts without a final completion event now reports an incomplete
  scan instead of silently returning zero. Partial remote scans preserve the previous
  indexed rows and never reconcile away photos from temporarily unreachable folders.
- Runtime source identity now includes the configuration of merged co-primary sources,
  scan filters, playlist source restrictions, and unreachable-source policy.
- The on-device rebuild action now gives immediate visible confirmation.

## Regression coverage

- Added unit and executable pure-Kotlin checks for failed Synology → SMB switching,
  merged secondary-source edits, unused-source edits, and scan-filter changes.
- Extended the static consistency gate to reject the former stale-snapshot rebuild
  wiring.
- Offline Kotlin parsing, pure-logic checks, Room SQL validation, migration replay,
  web UI JavaScript parsing, folder-shuffle simulation, diagnostics endurance, and
  engine/persistence type checks pass.

## Hardware validation still required

Run the following against a real NAS before distribution:

1. Save deliberately invalid Synology credentials and wait for the failure result.
2. Configure a working SMB share and press **Use this NAS**.
3. Confirm the indexing counter starts and SMB photos replace fallback samples.
4. Press **Rebuild photo list** twice during the scan and confirm only one physical SMB
   enumeration is recorded.
