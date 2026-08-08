# Type-checking without an Android toolchain (v41)

## The step this was blocking

Every previous round ended with the same caveat: *"only syntax has been checked, never
types."* That caveat was doing a lot of load-bearing work — by v40 there were roughly 400
lines of new engine, DAO and ViewModel code whose call sites had never been verified by a
compiler. The next step was to remove as much of that caveat as possible.

## A real build is genuinely impossible here — now measured, not assumed

I had been asserting this from the network configuration. Tested directly:

| Host | Needed for | Result |
| --- | --- | --- |
| `services.gradle.org` | Gradle distribution | **403** |
| `dl.google.com` | Android SDK, AGP, androidx | **403** |
| `repo1.maven.org` | Maven Central | **403** |
| `github.com` | Kotlin compiler | 200 |

So `./gradlew assembleDebug` cannot run here, and that is now a measured fact.

## What turned out to be possible anyway

Very little of the risky code actually needs Android:

- **Room contributes only annotations.** Code generation is not needed to type-check
  *call sites* — a stubbed `@Query`/`@Dao` is enough for the compiler to verify that
  `dao.displayableIds(...)` matches its declaration.
- **kotlinx-coroutines ships inside the Kotlin compiler distribution** already downloaded
  by the existing harness (`kotlinc/lib/kotlinx-coroutines-core-jvm.jar`).
- **`androidx.exifinterface` is used through two getters and a few tag constants.**
- `DiagnosticsLog`, the domain classes and the source layer are plain Kotlin.

`scripts/verify-engine-types.sh` therefore compiles the **real** engine, DAO, entity,
indexer, Exif extractor, WebDAV/Synology API mappers, schedule and utils against stub
annotations. It now runs automatically as part of `verify-pure-logic.sh`.

Newly type-checked (previously parse-only):

`SlideshowEngine`, `PhotoDao`, `PhotoItemEntity`, `FolderSummary`, `EngineState`,
`SourcePoolPolicy`, `RecoveryPolicy`, `PlaybackQueue`, `LeastRecentRandom`, `Indexer`,
`ExifExtractor`, `PhotoSource`, `WebDavApi`, `SynologyApi`, `SleepSchedule`, `Glob`,
`StableId`, `BuiltInSourceIds`, `DiagnosticsLog`.

## The check was verified to actually fail

A harness that always passes is worse than none. I deliberately broke a DAO call to the
exact shape of bug this round's query changes could have introduced — dropping the two
new folder-filter arguments — and confirmed:

```
SlideshowEngine.kt:370:73: error: no value passed for parameter 'allFolders'.
SlideshowEngine.kt:370:73: error: no value passed for parameter 'folders'.
TYPE CHECK FAILED
```

Restored, it passes. The same technique confirmed the `CAPABILITIES.md` guard last round.

## What this does and does not prove

**Proves:** the engine and persistence layer compile; every `dao.*` call site matches its
declaration in argument count and type; the new `EngineUiModel` fields, `PlaybackQueue`
integration and `SourcePoolPolicy` plumbing type-check against the real declarations.

**Does not prove:**

- **Room's generated SQL is valid.** The annotations are stubs, so the `@Query` strings
  are never parsed. The four folder-filtered queries and `folderSummaries` remain
  unverified as SQL — including my assumption about `IN ()` and the sentinel padding.
  Only a real Room annotation processor settles that.
- **Nothing in the UI or settings layer is covered.** `SlideshowViewModel`,
  `SettingsScreen`, `SlideshowScreen` and the whole Compose surface still have only been
  parsed — and the ViewModel is where the largest untyped refactor still lives. The
  settings layer is excluded because the kotlinx-serialization *runtime* is unavailable.
- Nothing about runtime behaviour on a device, or against a real NAS.

So the honest status improved but did not flip: the highest-risk *logic* is now compiled
and executed, while the highest-risk *integration* (ViewModel, Room SQL) is not.
`./gradlew --no-configuration-cache clean testDebugUnitTest assembleDebug` remains the
necessary gate.
