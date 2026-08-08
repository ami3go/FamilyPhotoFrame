# FamilyPhotoFrame v51.4.1 — Web settings JSON build fix

**Application version:** `0.11.4.1-prerelease` (`versionCode 19`)  
**Supersedes:** v51.4 / `0.11.4-prerelease`

## Failure reproduced

The v51.4 source failed during `:app:compileDebugKotlin` in
`web/WebSettingsJson.kt`. `buildJsonArray` exposes `add(JsonElement)` as its
member function. Because this file did not import the primitive `add` extension,
raw `String` and `Int` values in five arrays could not be resolved:

- `selectedFolders`
- `includeGlobs`
- `excludeGlobs`
- `excludeFolders`
- playlist rule `daysOfWeek`

## Correction

Each primitive is now explicitly wrapped with `JsonPrimitive` before it is added
to the JSON array. This avoids depending on an implicit extension import and is
compatible with the project's kotlinx-serialization JSON API.

A focused `WebSettingsJsonTest` now verifies that strings and weekday integers
are emitted as correctly ordered JSON primitive arrays.

## Verification

- Focused Kotlin type check with a JSON builder exposing only
  `add(JsonElement)`: PASS.
- Main-source structural consistency check: PASS.
- Room SQL preparation, 60 queries: PASS.
- Database migration replay, versions 1 through 6: PASS.
- Search for equivalent primitive-array calls without explicit conversion: PASS.

The isolated packaging environment still cannot download the Gradle 8.9 wrapper,
so the final Android build must be executed on a connected development machine:

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest installDebug
```
