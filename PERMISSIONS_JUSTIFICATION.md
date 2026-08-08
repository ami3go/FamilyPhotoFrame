# Permissions Justification

## Current posture (Phase 1): network permissions only

The merged manifest declares only these permissions — two network permissions for the
SMB/NAS feature plus an optional boot permission — and **no** broad media/storage
permissions. This is verified mechanically on every build by the `PermissionAuditTask`
(report at `app/build/reports/permissions/<variant>.txt`).

| Declared permission | Feature it maps to (spec §21.1) |
|---|---|
| `INTERNET` | SMB/NAS source reads bytes over the network into the app-private cache. |
| `ACCESS_NETWORK_STATE` | Source health / connectivity checks for NAS recovery. |
| `INTERNET` (web setup) | Also covers the embedded LAN setup server (spec §15); no additional permission is needed. |
| `RECEIVE_BOOT_COMPLETED` | Optional auto-start on boot when the user enables "Start on boot"; best-effort and API-guarded (spec §13.3). |

Neither is a media/storage permission, so the §22.1/§27.8 gate still passes. SAF
folder access, keep-screen-on, and app-private fallback continue to need no permission.

Why nothing is needed:

| Capability | How it's done without a permission |
|---|---|
| Read the user's photo folder | **Storage Access Framework** tree URI the user explicitly grants; persisted via `takePersistableUriPermission`. Scoped to that one folder. |
| Keep the screen on | `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` (a window flag, not a permission; no `WAKE_LOCK`). |
| Fallback / sample images | Bundled in `assets/`, copied into app-private internal storage. |
| Persist settings | App-private `DataStore` file in internal storage. |

### Explicitly avoided (and audited against)

The build **fails** if any of these appear in the final merged manifest, including via
a transitive library:

- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`
- `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`
- `ACCESS_MEDIA_LOCATION`
- `QUERY_ALL_PACKAGES`

This satisfies the §22.1 / §27.8 permission acceptance gate by construction: a frame
that uses SAF needs none of the broad media permissions, which keeps the Play Data
Safety story minimal and avoids the All-files-access review path.

## Future phases — permission, mapped to the feature that earns it

These are **not** declared now. Each is added only in the phase where its feature
ships and can pass acceptance:

| Permission | Phase / feature | Notes |
|---|---|---|
| `INTERNET` | SMB sources, web control panel, weather | Network access for jcifs-ng / NanoHTTPD / weather API. |
| `ACCESS_NETWORK_STATE` | SMB / weather | Detect connectivity for graceful degrade. |
| `INTERNET` (web setup) | Also covers the embedded LAN setup server (spec §15); no additional permission is needed. |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on boot | Frame relaunches after a power cycle. |
| `POST_NOTIFICATIONS` | Foreground/diagnostic notifications | Only if a foreground service is introduced. |

Broad media permissions remain **out of scope for the 1.0 release** (Contract Rule:
no broad media permissions in Release 1.0). SAF remains the primary local-access
mechanism.
