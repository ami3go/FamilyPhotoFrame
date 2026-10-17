# FamilyPhotoFrame architecture ownership

This document defines the current responsibility boundaries after the v51.3 legacy
refactor. New code should extend the owning module instead of rebuilding a second path
inside an activity, screen, or server controller.

## Android application shell

- `MainActivity.kt` — lifecycle, SAF launchers, top-level slideshow/settings navigation,
  sensor registration, and key dispatch.
- `ImmersiveModeController.kt` — the complete Android 5+ fullscreen compatibility path.
  Deprecated decor flags are intentionally confined to this file.

## Slideshow UI

- `SlideshowScreen.kt` — screen shell: surfaces, status pills, gestures, and curation
  dialogs.
- `PlayingContent.kt` — prepared-presentation coordination: selected/next preparation,
  the explicit transition, and render acknowledgement. Its remembered handles are shared
  by every effect in it, so it stays one composable.
- `PreparedCollage.kt` — collage tile layout, panel motion, and decoration.
- `SlideshowTouchControls.kt` — touch navigation, favorite/hide selection, and undo UI.
- `SlideshowPreparation.kt` — image resolution, decoding, collage preparation, and
  app-owned bitmap lifecycle.
- `SlideshowViewModel.kt` — owns the UI state, lifecycle, and the source pipeline
  (activation, indexing, health checks, and recovery), whose state is shared too widely
  to split as a pure move. Every other feature area is delegated to a collaborator in
  `ui/slideshow/viewmodel/`; the ViewModel keeps one-line delegators so callers never
  depend on the collaborators:
  - `SourceSettingsActions` — SMB/Synology/WebDAV save and test, certificate approval,
    primary and co-primary sources.
  - `PlaybackPhotoAccess` — Coil model resolution through MediaCache, remote bounds
    probes, and collage candidates.
  - `PresentationReporter` — renderer callbacks, transition/collage diagnostics, and
    decode-OOM recovery.
  - `PhotoLibraryActions` — folder selection and previews, favourites, hide/unhide.
  - `PlaylistActions`, `OnThisDayActions`, `AutoRescanScheduler`, `BrightnessActions`,
    `WeatherActions` — settings plus their wall-clock watchers.
  - `ConfigTransferActions`, `WebControlActions`, `LocalThumbnailCacheActions`.
  - `SlideshowFeatureHost` — the services, context, scope, and UI state a collaborator
    may use. Collaborators never reach back into the ViewModel except through callbacks
    passed at construction.

## Settings UI

- `SettingsScreen.kt` — page navigation and settings shell only.
- `SettingsPhotoSources.kt` — local, SMB, Synology, WebDAV, filters, and folder selection.
- `SettingsPlaybackSections.kt` — playback, transition, collage, and playlist controls.
- `SettingsScheduleSections.kt` — playlist schedules and automatic rescans.
- `SettingsDisplaySections.kt` — overlays, brightness, night mode, and display behaviour.
- `SettingsDeviceSections.kt` — web control, security, remembered browsers, and device
  maintenance.
- `SettingsBackupSections.kt` — backup/import/export controls.
- `SettingsCommon.kt` — shared Compose controls and formatting helpers.

## Embedded web server

- `WebConfigServer.kt` — HTTP transport adapter, authentication boundary, and route-group
  dispatch. Routes are grouped as public, compatibility, remembered-browser, versioned
  read, versioned write, and upload routes.
- `WebServerController.kt` — server lifecycle and backend orchestration.
- `WebSettingsPatchApplier.kt` — settings JSON parsing, validation, credential-scope
  protection, and persistence.
- `WebSettingsJson.kt` — redacted settings-to-JSON projection.
- `WebUiAssets.kt` — stable asset facade and cache revision only.
- `WebUiCss.kt` / `WebUiScript.kt` — independently reviewable browser assets.
- `WebBackend` — strict production contract. `WebBackendAdapter` is only for deliberately
  partial test doubles.

## Compatibility boundaries

- Historical transition values (`none`, `slide`) are translated only by
  `TransitionMode.fromStorage`; they are not runtime transition enum values.
- Android 5/6 immersive decor flags live only in `ImmersiveModeController`.
- API 21/22 Keystore compatibility remains intentionally isolated in the secret-store
  implementation; it must not be removed while `minSdk = 21`.
- Historical release and phase evidence lives under `docs/archive/` and is not the
  current implementation source of truth.

## Change rules

1. Do not add a second settings parser to `WebServerController`.
2. Do not embed large CSS or JavaScript strings back into `WebUiAssets`.
3. Do not put source I/O or bitmap decoding into touch controls or Compose settings.
4. Do not add default placeholder implementations to the production `WebBackend`.
5. Keep compatibility conversion at serialization/platform boundaries, not in runtime
   selectors and renderers.
6. Update verification scripts when responsibility moves so checks follow production
   code rather than stale file locations.
