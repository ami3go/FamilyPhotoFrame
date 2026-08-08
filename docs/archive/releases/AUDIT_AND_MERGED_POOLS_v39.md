# Audit + merged primary pools (v39)

## The audit matters more than the feature

Asked for "the next 5 features", I re-read the code instead of my own earlier list. Most
of that list was already built. Of the 14 items I tabulated one turn earlier, these are
**already implemented** and were wrongly listed as missing:

| Listed as missing | Actual state |
| --- | --- |
| QR pairing for the web panel | `WebSecurity.issueQrToken`, `QrCodes.kt`, on-screen QR in Settings |
| Production signing + R8/minification | `signingConfigs` from `keystore.properties`/env; `isMinifyEnabled = true`, `isShrinkResources = true`, load-bearing `proguard-rules.pro` |
| Nextcloud / WebDAV source | `WebDavApi.kt`, `WebDavPhotoSource.kt`, `WebDavApiTest.kt`, wired through settings, VM, UI and web |
| Folder include/exclude filtering | `ScanOptions` + `ScanFilters` in settings, editable in the Settings screen |
| Recovery-loop testability | `RecoveryPolicy` extracted and covered by `RecoveryPolicyTest` |

The docs were the source of the error, and they had already been wrong once before (the
v37 pass fixed a `KNOWN_LIMITATIONS.md` that contradicted itself). **Treat the code as
the source of truth in this repo; treat every roadmap and limitations document as a
lagging indicator.** I have now been wrong twice by trusting them.

Genuinely remaining after the audit:

- **Merged primary pools** — implemented below.
- **Synology Photos (`SYNO.Foto`) albums** — blocked in practice: reverse-engineered,
  DSM-version-sensitive, and unverifiable without hardware.
- **QuickConnect relay** — `SourceError.QuickConnectUnavailable` exists but is never
  produced; needs a real relay to build against.
- **Immich source** — not started.
- **Album-as-selection-unit** — depends on a source that actually exposes albums.

Three of those four are hardware-blocked. I implemented the one that was not, rather
than starting a network source I could neither run nor test.

## Merged primary pools (spec §9.3)

A local folder and a NAS — or two NAS protocols — can now feed one slideshow.

- `ActiveSource.alsoPlay: Set<ActiveSourceKind>` holds the extra kinds. It defaults to
  empty, so an upgraded frame plays exactly what it played before.
- `applySource` was rewritten from a single `when` into **slot activation** plus pool
  assembly: `activateSlot` brings one kind online (build → health-check → index) and
  returns an `ActivatedSlot`; the pool is the union of the healthy ones.
- **A broken co-primary cannot take down the frame.** Only the *chosen* source may move
  the UI to an error or first-run surface; a co-primary that fails is logged and skipped.
  Playing fewer photos beats dropping a working source.
- **`reconfigurePool()` replaces `playPrimaryWithFallback`.** The recovery loop used to
  set the pool to the one source that recovered, which with several primaries would have
  evicted the others. Promote/demote now add/remove from `primaryPoolIds` and rebuild.
- **Stale-cache playback stays single-source.** It engages only when the pool is empty.
  With a healthy co-primary serving live photos, falling back to another source's stale
  bytes would be strictly worse.
- `activeRemoteSource` became `activeRemoteSources: Map<String, PhotoSource>`, because
  `resolveModel` must hand `MediaCache` the source that owns the displayed photo.
- One recovery loop per remote slot (`recoveryJobs`), all cancelled together.

### A latent bug this surfaced

Every settings save rebuilt `ActiveSource` from scratch:

```kotlin
source = ActiveSource(kind = SMB, smb = draft)   // wipes treeUri, synology, webdav
```

Harmless while only one source could play — destructive the moment sources merge, since
configuring SMB would silently erase the local folder and Synology settings. All five
sites now use `copy()`. `MergedSourcePoolTest` pins this, as it is invisible until
someone switches back.

The change signature also had to include `alsoPlay`; merging a source in changes the pool
without changing the chosen kind's own fields, so the frame would not have re-applied.

### Boundary: one source per kind

The built-in source ids (`local_saf`, `smb`, `synology`, `webdav`) are fixed constants,
so two SMB shares at once would collide on `smb`. Supporting that needs per-source
generated ids, which touches indexing, credential scoping, cache routing and the web
status shape. That is separate work, not a tweak, and is recorded in `ROADMAP.md`.

## Verification

`check-consistency.py`, Kotlin parse of all sources, and the full pure-logic harness all
pass. `MergedSourcePoolTest` was added for the settings semantics.

**Unverified, and the risk is higher than last time.** This refactor touched roughly 150
lines of `SlideshowViewModel` — collection-typed state, a new `when`-expression returning
a nullable data class, and coroutine bookkeeping — and **only its syntax has been
checked, never its types.** A parse check cannot catch a wrong inferred type or a
nullability mistake. Specifically unproven:

- that the whole ViewModel still type-checks;
- multi-source activation against any real source, let alone two at once;
- the promote/demote path with more than one primary;
- that `primaryPoolIds` is genuinely only mutated from the main thread under real
  recovery timing.

`./gradlew --no-configuration-cache clean testDebugUnitTest assembleDebug` is the next
gate and matters more for this change than for any previous one.
