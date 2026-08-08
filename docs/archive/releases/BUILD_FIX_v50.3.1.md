# FamilyPhotoFrame v50.3.1 build fix

## Fixed

`SlideshowScreen.kt` imported `androidx.compose.foundation.layout.weight`.
With the project Compose BOM this resolved to an internal parent-data property and
caused `compileDebugKotlin` to fail. The import was removed; `Modifier.weight(1f)`
now resolves from the enclosing `RowScope`, as intended.

## Prevention

`scripts/check-consistency.py` now rejects the incompatible top-level `weight` import.

## Version

- versionCode: 5
- versionName: 0.10.3.1-prerelease
