# Code Review — Family Photo Frame v36

**Review date:** 2026-07-25

**Scope:** major correctness, security, and long-running reliability issues only. No broad UI redesign or architectural rewrite was performed.

## Verdict

The project has a sound source/index/cache/engine separation and substantially more implemented functionality than its old Phase 0 documentation suggests. However, v36 contained one release-blocking defect: **Synology files could be indexed and selected but could not be displayed**. The remote playback path was hard-wired to SMB in two separate places.

That blocker and four closely related high-impact issues are fixed in this revision. The project is now suitable for a real Gradle build and hardware/NAS validation, but it is **not yet release-validated** because this review environment could not download the Gradle distribution or Android dependencies and had no real SMB or Synology server attached.

## Critical finding fixed

### Synology slideshow playback always resolved to `null`

The Synology scanner stores a NAS-relative `openToken`, for example `/photo/Family/a.jpg`. The display mapper previously marked only `smb://...` tokens as cache-backed. Consequently, Synology tokens were treated as local filesystem paths. In addition, `SlideshowViewModel` retained only an `activeSmbSource`, so even a Synology item explicitly marked for caching had no source available to download it.

The correction:

- centralizes built-in source IDs in `BuiltInSourceIds`;
- marks both SMB and Synology records as requiring `MediaCache`;
- retains the active remote `PhotoSource`, not an SMB-specific type;
- verifies that the source ID of the displayed record matches the active remote source before fetching;
- keeps local SAF and bundled fallback photos on the direct-load path.

## Five autonomous implementation steps

### 1. Repair remote photo playback

- Fixed Synology display routing through `MediaCache`.
- Removed `openToken`-format guessing from the engine; cache policy is now based on the indexed `sourceId`.
- Added pure-logic regression checks for SMB, Synology, SAF, and fallback routing.

### 2. Correct credential lifecycle and source identity

- Saving an edited source with an empty password field now preserves the existing secret when the server/share/account is unchanged.
- A changed named account cannot silently reuse or overwrite the previous credential.
- Documented anonymous SMB access remains possible when the username is blank.
- Credential references are now stable SHA-256-derived identifiers scoped by server, share, domain, and user for SMB, and by server and user for Synology. This prevents different accounts from colliding in the Keystore-backed secret table.
- Runtime source signatures now include all fields that change source behaviour, including account identity, credential reference, thumbnail policy, thumbnail size, and certificate pin.
- Plain configuration export now strips the weather API-key reference as well as NAS credential references.
- Plain configuration import reuses an existing secret only for the same credential scope.

### 3. Complete encrypted portable backup

Portable bundle version 2 now encrypts and restores:

- SMB password;
- Synology password;
- optional weather API key;
- all non-secret settings.

Imported secrets receive device-local credential references and are re-encrypted under the destination device's Android Keystore key. New payload fields are nullable with defaults, so version-1 bundles remain readable.

### 4. Correct and harden web configuration behaviour

- Web status now reports the Synology index instead of incorrectly reporting fallback-photo counts.
- Redacted configuration reports whether an SMB or Synology password is configured, without exposing either secret or its reference.
- Changing an SMB server/share/domain/user through the web clears the old credential reference.
- Changing a Synology server/user clears the old credential reference; changing the host also clears the old certificate pin.
- Synology is now supported by the saved-source connection test.
- Source IDs used by status and diagnostics now come from the same central policy as the index and engine.

### 5. Harden long-running Synology I/O and verification

- A streamed HTTP response now disconnects its `HttpURLConnection` when the caller closes the stream, preventing connection/socket accumulation during continuous slideshow operation.
- Synology error conversion no longer swallows `CancellationException`; coroutine cancellation propagates correctly through login, health checks, scanning, opening, and certificate probing.
- The static enum-exhaustiveness checker now reads real enum declarations instead of relying on stale hard-coded values.
- Added pure regression checks for remote cache routing and credential identity.

## Verification completed here

The following checks passed after the changes:

- `python3 scripts/check-consistency.py`
  - all string-resource references resolved;
  - ViewModel callback references resolved;
  - Room database version 5 has a contiguous `1→2→3→4→5` migration chain;
  - enum-switch consistency checks passed.
- `./scripts/verify-pure-logic.sh`
  - no Kotlin parser errors across main sources;
  - **110 pure-logic assertions passed**;
  - new source-routing and credential-policy checks passed.
- `git diff --check`
  - no whitespace or patch-format errors.

## Verification not possible in this environment

A full Android build was attempted, but the Gradle wrapper could not resolve `services.gradle.org` and no Gradle distribution/dependency cache was installed locally. Therefore this review does **not** claim that Android/Compose/Room/jcifs integration compiles or that the APK has been exercised.

Run these gates on the development machine before merging or installing:

```bash
./gradlew --no-configuration-cache clean testDebugUnitTest assembleDebug
./gradlew --no-configuration-cache connectedDebugAndroidTest
```

Then validate on a real frame:

1. SMB authenticated source, SMB guest source, and credential edit with password left blank.
2. Synology File Station source with thumbnails enabled and disabled.
3. Synology session expiry/re-authentication and NAS reboot recovery.
4. At least a 12-hour slideshow soak while monitoring memory, file descriptors, and network reconnection.
5. Encrypted backup round-trip onto a second device, confirming SMB, Synology, and weather secrets all work.

## Remaining major release risks

- Synology File Station has not been validated against real DSM hardware.
- SMB and Synology behaviour on API 21–23 remains unvalidated.
- The release variant is still debug-signed and unminified.
- `README.md`, `versionName`, and parts of `KNOWN_LIMITATIONS.md` still describe an older Phase 0/early-Phase state and should be reconciled before external release; they were not rewritten in this major-fix-only pass.

## Files central to this correction

- `data/source/BuiltInSourceIds.kt`
- `data/settings/CredentialPolicy.kt`
- `domain/engine/SlideshowEngine.kt`
- `ui/slideshow/SlideshowViewModel.kt`
- `data/source/SynologyFileStationSource.kt`
- `data/settings/ConfigTransfer.kt`
- `data/settings/PortableBundle.kt`
- `web/WebServerController.kt`
- `scripts/check-consistency.py`
- `scripts/verify-pure-logic.sh`
