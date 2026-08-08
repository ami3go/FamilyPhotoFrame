# Support Codes

Stable, user-facing recovery codes (spec §17.4). These strings are **stable** — they
are logged to the redacted diagnostic buffer and shown (as plain language) to the
user, so support can map a screenshot or report to a known condition without ever
exposing a stack trace, file path, or secret.

| Code | Condition | What the user sees | Recovery offered |
|---|---|---|---|
| `SAF_PERMISSION_REVOKED` | The persisted folder grant is no longer held (reboot, restore, or revoke). | "Access to your folder was lost. Choose the folder again to repair it." | Re-pick folder, or use samples. |
| `SAF_FOLDER_MISSING` | The folder was moved, renamed, deleted, or its volume unmounted. | "The chosen folder was moved, renamed, or removed. Choose a folder again." | Re-pick folder, or use samples. |
| `SAF_PROVIDER_ERROR` | The documents provider returned an error/null cursor. | "This device's file provider returned an error. Try choosing the folder again." | Re-pick folder, or use samples. |
| `SAF_UNAVAILABLE` | The source is queryable but yielded nothing usable. | (Same provider-error message.) | Re-pick folder, or use samples. |
| *(picker cancelled)* | User backed out of the system folder picker. | "No folder was chosen. Pick a folder to start the slideshow." | Choose folder / use samples. |
| *(empty index)* | Folder is valid but contains no supported images. | "No photos found in that folder. Choose a different folder." | Re-pick folder, or use samples. |

Diagnostics never include secrets, full private paths, EXIF GPS, or photo bytes
(spec §17.2). The redacted bundle is viewable from **Settings → Diagnostics**.

### Reserved for later phases

`SMB_AUTH_FAILED`, `SMB_HOST_UNREACHABLE`, `SMB_SHARE_NOT_FOUND`, `SMB_TIMEOUT`,
`SMB_PROTOCOL_ERROR` — the `SourceError` taxonomy is already declared in code so these
codes are stable when the SMB source ships.
