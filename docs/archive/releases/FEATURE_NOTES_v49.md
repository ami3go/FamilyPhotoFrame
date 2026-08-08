# Feature Notes v49 — Discovered Folder Selection and iPhone Photos

## Restored discovered-folder selection

- The grouped-settings refactor had moved the folder selector into **Playback**.
- The selector now appears in **Photos**, after source and indexing filter controls.
- Every discovered folder is shown with a real checkbox and indexed photo count.
- Empty selection continues to mean “play all folders”.
- The **Play all folders** reset action remains available when a subset is selected.

## iPhone photo indexing defaults

- Default include globs now contain `*.heic` and `*.heif`.
- Supported extension and MIME checks now accept HEIC/HEIF, including sequence MIME types.
- Existing installations using the exact legacy default filter are migrated automatically.
- Custom include filters are preserved without modification.
- Imported legacy configurations receive the same non-destructive migration.

## Compatibility note

Indexing and decoding are separate capabilities. HEIC/HEIF files are now discovered by
all sources, while successful display still depends on the Android device's available
image decoder. Decode failures continue through the existing safe suppression/fallback
path instead of crashing playback.
