# First real Gradle build — fixes

The first `./gradlew installDebug` failed during **configuration**, before any app code
was compiled.

## 1. `java.util.Properties` in build.gradle.kts (the reported failure)

```
Line 41: val keystoreProps = java.util.Properties().apply {
                                  ^ Unresolved reference: util
```

Inside a `build.gradle.kts`, the name `java` resolves to the **Java plugin extension**
(`JavaPluginExtension`), not the `java.*` package. So `java.util.Properties` is read as
"the `util` member of the java extension", which does not exist; `load(it)` then fails as
a knock-on.

Fixed by importing the type at the top of the file and using it unqualified:

```kotlin
import java.util.Properties
...
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { stream -> load(stream) }
    }
}
```

Checked: no other `.kts` file uses a fully-qualified `java.*` reference.

## 2. `state.source` in SettingsScreen.kt (found while checking, not yet reported)

Configuration failing meant Kotlin compilation had not started, so nothing in the UI
layer had been checked by the compiler yet. Reviewing the recently added UI against the
real declarations turned up a genuine error the build would have hit next:

```kotlin
LaunchedEffect(state.source, state.selectedFolders) { ... }   // no such field
```

`SlideshowUiState` has no `source` property. Re-keyed on fields that exist and that
actually signal the folder list may have changed:

```kotlin
LaunchedEffect(state.surface, state.indexingFound, state.selectedFolders) { ... }
```

Every other newly added UI reference was cross-checked against its declaration:
`configurableKinds`, `alsoPlay`, `selectedFolders`, `selectionMode`, `favoritesOnly`,
`onUnreachable`, `stalePlayback`, `engine.cycleTotal`; the `ToggleRow`, `SectionLabel` and
`StatusPill` signatures; the `stringRes` two-argument overload; the `KEYCODE_F` /
`KEYCODE_CHANNEL_UP` / `KEYCODE_BOOKMARK` constants (all present at minSdk 21); and the
`java.io.File`, `Alignment`, `OutlinedButton` and `kotlinx.serialization.json` imports.

## 3. Pure-logic harness broken by a misplaced file (self-inflicted, found by the suite)

`scripts/verify/DiagnosticsEndToEnd.kt` was picked up by the pure-logic harness, which
compiles that whole directory; it depends on `FileDiagnosticsSink`, which is not in the
pure set. Moved to `scripts/e2e/`, with a comment so it does not drift back.

## What to expect next

Configuration should now succeed and the build will move on to compiling Kotlin. **The UI
and ViewModel layer has never been compiled by anything** — the offline harness type-checks
the engine, DAO, indexer and settings model, but Compose and `SlideshowViewModel` are
beyond what can be stubbed without the Android SDK. `SlideshowViewModel` in particular
carries the largest refactor that no compiler has seen.

So expect more errors, most likely in:

- `SlideshowViewModel` (multi-source refactor, curation, folder selection);
- `SettingsScreen` / `SlideshowScreen` Compose code;
- Room's annotation processor, which will validate the `@Query` strings for real. The SQL
  itself is now checked against SQLite, and the migration chain is executed and diffed
  against the entities, so schema-level surprises should be few — but Room also checks
  projection/return-type agreement, which nothing offline can verify.

Send me the next error output and I will work through it.

---

# Round 2 — `compileDebugKotlin`

Configuration now succeeds; the failure moved into Kotlin compilation, as expected.

```
SlideshowViewModel.kt:200:20 'when' expression must be exhaustive.
Add the 'WEBDAV' branch or an 'else' branch.
```

## 1. Missing `WEBDAV` branch (the reported failure)

`testSavedSource()` — the web panel's "test connection" — handled SMB, Synology, local
SAF and samples, but not WebDAV. Added a branch that builds the saved WebDAV source and
reports its health through the existing `webDavHealthMessageRes` helper, mirroring the
Synology branch.

## 2. `currentPlaybackSourceIds()` ignored co-primaries (found alongside, not reported)

```kotlin
private fun currentPlaybackSourceIds(): List<String> = when (lastSettings?.source?.kind) { ... }
```

It returned the id of the **chosen** source only. Once merged pools existed, that was
wrong: counting favourites and listing folders would silently cover half a merged
library, so "Play favourites only" could refuse to enable while favourites existed on the
other source, and the folder picker would omit its folders. Now returns the chosen kind
plus everything in `alsoPlay`.

Not a compile error — it would have looked like a puzzling behavioural quirk on a device.

## 3. The consistency check that should have caught this, and why it didn't

`check_exhaustive_when()` exists precisely for this failure mode, and its own docstring
notes it was written after a non-exhaustive `when` reached a real build once before. It
missed this one.

The reason: it skipped any `when` block containing `else ->` anywhere in its text. The
`when` at line 200 contains a *nested* `when (saf.healthCheck(...))` that has its own
`else ->`, and that inner branch made the outer block look handled.

The detection is now brace-depth aware: only an `else ->` at the top level of the block
counts. Verified by deleting the WEBDAV branch again and confirming the check reports it:

```
SlideshowViewModel.kt:200 — non-exhaustive `when` over ActiveSourceKind
    (missing: WEBDAV). Add the branch or an `else`.
```

That is the more valuable outcome here — the compiler was always going to find the
missing branch; the check being blind to a whole shape of `when` would have kept costing
build round-trips.

## Still expected ahead

`SlideshowViewModel` and the Compose UI remain the least-verified code in the project,
and Room's annotation processor has not run yet. More errors are likely; send them over.
