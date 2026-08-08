# Code Review Follow-up — v37

**Scope:** independently verify the v36 fixes described in `CODE_REVIEW.md`, look for
additional major issues, and close the one concrete, code-adjacent gap that review
explicitly flagged as unresolved (stale docs/version). No hardware, real SMB/Synology
server, or Gradle/Android SDK was available in this environment either.

## Verification of the v36 claims

Ran both offline gates fresh in this session:

- `python3 scripts/check-consistency.py` → all checks passed (Room v5 migration chain
  1→2→3→4→5 intact, string resources resolved, callback references resolved).
- `./scripts/verify-pure-logic.sh` → downloaded Kotlin 2.0.21 from GitHub releases,
  parsed all main sources with no syntax errors, and ran the full pure-logic suite —
  **all assertions passed**, including the remote-cache-routing and credential-identity
  checks central to the Synology fix.

Manually re-read (not just trusted the summary) the five files most central to the
critical fix and confirmed the code matches the description:

- `BuiltInSourceIds.requiresMediaCache` correctly includes both `smb` and `synology`.
- `SlideshowViewModel` holds `activeRemoteSource: PhotoSource?` (not SMB-typed) and
  checks `src.id.value == display.sourceId` before handing it to `MediaCache`.
- `CredentialPolicy` scopes SMB by host/share/user/domain and Synology by host/user,
  excluding path/folder, with stable SHA-256 references.
- `ConfigTransfer` and `PortableBundle` strip/re-scope credential references correctly
  on export, import, and merge; version-2 bundle fields are nullable for v1 compat.
- `WebServerController` clears `credentialRef` (and the Synology cert pin on host
  change) whenever the account/host actually changes, and never accepts a password,
  OTP, or cert pin over the web API.
- `SynologyFileStationSource` / `UrlConnectionHttpClient`: the returned `InputStream`
  disconnects its `HttpURLConnection` on `close()`, and `CancellationException` is
  re-thrown everywhere rather than mapped to a `SourceError`.

No regressions or contradictions found in this pass.

## Issue found and fixed this pass

**Documentation/version drift (the "Remaining major release risks" item).**
`README.md`, `KNOWN_LIMITATIONS.md`, and `versionName` still described a Phase-0,
local-only build, even though `KNOWN_LIMITATIONS.md` itself already went on to
document Phase 1/1.5/2 behavior (SMB, Synology, web panel, weather, boot autostart) in
later sections — the file contradicted itself. This is misleading for anyone deciding
what's safe to rely on, so it's a real (if non-crash) issue, not cosmetic-only. Fixed:

- `README.md` rewritten to describe the actual current feature set (SMB, Synology,
  `MediaCache`, web panel, weather, boot autostart, plain + encrypted backup) and to
  state plainly that the project is not yet release-validated; also corrected the
  stated minimum API level (actual `minSdk` is 21, README said 24).
- `KNOWN_LIMITATIONS.md` top section rewritten to drop the false "not implemented yet"
  claims that the rest of the same file already disproved; the genuine remaining gaps
  (Phase 1/1.5/2 caveats, API 21–23 tier, unminified/debug-signed release, etc.) were
  left as-is since they're still accurate.
- `versionName` bumped from `0.1.0-phase0` to `0.4.0-prerelease` to stop advertising a
  phase that's three phases behind the actual code.

## Still not verifiable here

Unchanged from `CODE_REVIEW.md`: no Gradle/Android SDK distribution is reachable from
this sandbox (only `github.com`-family hosts are allowlisted, not
`services.gradle.org`/`dl.google.com`), so a real `./gradlew assembleDebug` /
`connectedDebugAndroidTest` has still never run, and there is still no real SMB or
Synology server to validate against. The gates and hardware checklist at the bottom of
`CODE_REVIEW.md` are the right next step and are unchanged by this pass.
