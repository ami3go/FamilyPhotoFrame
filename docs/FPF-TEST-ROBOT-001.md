# FamilyPhotoFrame — Robot Framework Automated Regression & Stability Suite

**Document ID:** FPF-TEST-ROBOT-001
**Version:** 1.0
**Status:** Draft — scope proposal, not yet implemented
**Target baseline:** main @ `fd0adf4` or later
**Framework:** Robot Framework + `RequestsLibrary` (API) + `Browser` or `SeleniumLibrary` (web UI)
**Primary target surface:** the on-device web server (`web/WebConfigServer.kt` and friends) — pairing, settings, playback control, diagnostics, backup/restore, uploads

---

## 1. Objective

Add a repeatable, CI-runnable Robot Framework suite that black-box exercises every function reachable through the frame's web server, and layers a long-running endurance mode on top of it to catch stability regressions (memory growth, error-rate creep, server restarts) before a production release.

This suite **adds to**, and does not replace, the project's existing test layers:

- JVM unit tests (`app/src/test/`, 325 tests as of `fd0adf4`) — pure logic.
- Instrumented tests (`app/src/androidTest/`) — Room, cache, Keystore, shuffle, on real Android.
- `MANUAL_TEST_CHECKLIST.md` — device/NAS procedures that need real hardware.
- `scripts/verify-*` — offline pure-logic and contract checks.

## 2. Scope boundary (read this before estimating effort)

The user framing was "test all functions and stability." Robot Framework is genuinely strong for one class of functionality here and cannot reach the other. Splitting it explicitly avoids a false sense of total coverage.

### 2.1 In scope — everything reachable over HTTP

- Pairing/QR flow (`WebSecurity`, `QrCodes.kt`, `WebServerController.pairingUrl`).
- Settings CRUD across every group in `FPF-FEAT-WEBUI-001` §6.2 (Photos, Playback, Display, Schedule, Device), including revision-conflict and validation-error paths.
- Playback control endpoints (play/pause/next/previous/restart-interval).
- Remembered-browser list/revoke/expiry.
- Bulk upload (`/api/v1/uploads/*`) create/status/file/complete/cancel, including the size/content-type/chunked-encoding guards fixed in `fd0adf4`.
- Diagnostics event API, pagination, export.
- Backup export/import round trip, including credential redaction and version tolerance.
- Maintenance actions (rescan, clear cache, factory reset) as HTTP-triggered, verified via diagnostics/status readback.
- Security regressions: CSRF/Host header rejection, unauthenticated write rejection, the chunked-body size-cap bypass and remembered-browser ownership gap identified and fixed in this review cycle.

### 2.2 Out of scope for this suite — needs different tooling, not silently dropped

| Area | Why Robot Framework can't cover it | What already covers it / should |
|---|---|---|
| Native Compose slideshow rendering, Ken Burns/transitions, D-pad input | No web surface; needs on-device UI interaction | Existing `androidTest/` + Espresso/UI Automator (extend there, not here) |
| Real SMB/WebDAV/Synology NAS behavior | Needs actual NAS hardware/credentials | `MANUAL_TEST_CHECKLIST.md` Phase 1 procedure; flagged unvalidated in `KNOWN_LIMITATIONS.md` |
| True on-device memory soak (API 21–22, 100 MiB heap) | Needs a physical low-RAM device profiled with Android Studio | `docs/API22_MEMORY_SOAK_TEST.md` manual procedure |
| Boot-autostart OS behavior | OS/OEM background-start restrictions, not exercised via HTTP | Manual device reboot procedure already in the checklist |

If full native-UI automation is wanted later, that's an `AppiumLibrary` addition on top of the existing Espresso suite, not a Robot Framework web test — worth a separate, later document rather than folding it in here.

## 3. Prerequisites

- A running debug build reachable on the test LAN (physical device or Android emulator with the web server's site-local bind requirement satisfied — emulator NAT may need a bridged/host-network config; confirm in Phase 1).
- Python 3.10+, `robotframework`, `robotframework-requests`, `robotframework-browser` (Playwright-backed) or `robotframework-seleniumlibrary`.
- A pairing bootstrap path for tests: either a documented debug-only pairing bypass, or a keyword that drives the real QR/PIN pairing flow once per suite and reuses the resulting session/remembered-browser token.
- CI runner capable of hosting an Android emulator (GitHub Actions `reactivecircus/android-emulator-runner` or equivalent) for the smoke/functional tiers; a persistent physical device for the long endurance tier is preferred but not required for the CI-nightly short endurance run.

## 4. Phased delivery plan

---

### Phase 1 — Harness, environment, and smoke suite

**Goal:** Stand up the Robot Framework project and prove it can drive the real server end to end, without yet asserting deep behavior.

**Deliverables:**
- `tests/robot/` project structure: `resources/` (keywords), `variables/` (per-environment config), `suites/`.
- An `ApiClient` resource wrapping `RequestsLibrary` with the app's `/api/v1/` envelope (`ok`/`revision`/`data` on success, `ok`/`code`/`message` on failure per `FPF-FEAT-WEBUI-001` §11.3).
- A pairing/session keyword usable by every other suite.
- One smoke test per endpoint group (status 200/`ok:true` only — no deep assertions yet).
- A CI job: boot emulator → install debug APK → wait for web server → run smoke suite → publish RF report as a build artifact.

**Gate 1 acceptance criteria:**
- Smoke suite passes headlessly in CI against a fresh debug install.
- Pairing keyword is reusable and does not require manual QR scanning in CI.
- RF HTML report is produced and attached as a CI artifact.

---

### Phase 2 — Functional coverage: settings & photos

**Goal:** Automate every settings group and its validation/conflict behavior.

**Covers:** source config, folder selection + persistence, indexing filters, scan trigger/coalescing, playback timing/order bounds, portrait collage modes, transition settings, display/overlay settings, schedule/quiet-hours, revision-conflict responses (`REVISION_CONFLICT`), structured field validation errors.

**Gate 2 acceptance criteria:**
- Every settings group listed in `FPF-FEAT-WEBUI-001` §6.2 has at least one positive test and one validation-rejection test.
- A stale-revision write is asserted to return `REVISION_CONFLICT` with the current revision.
- A setting written over the API is confirmed applied on the device side (via diagnostics/status readback), not just HTTP-200 taken on faith.

---

### Phase 3 — Functional coverage: pairing, security, uploads

**Goal:** Automate the security-sensitive surface, including regression tests for the specific gaps found and fixed in this review cycle.

**Covers:**
- QR/PIN pairing happy path and lockout-after-failed-PIN behavior.
- CSRF token requirement and rejection of mismatched/missing tokens.
- Host/Origin header enforcement.
- Unauthenticated write rejection across every mutating endpoint.
- Upload create → file (octet-stream, declared `Content-Length`) → complete/cancel, including rejection of oversized/undersized/mismatched-length bodies and unsupported content types.
- **Explicit regression tests for this session's fixes**, so a future revert is caught automatically:
  - `POST /api/pair` (or any JSON-body route) with `Transfer-Encoding: chunked` and no `Content-Length` must be rejected, not silently buffered.
  - Concurrent identical-photo cache resolution (via a controllable test fixture/mock source) must not corrupt the served file — this one may need an androidTest hook rather than pure HTTP, flag in Phase 3 review.
  - Remembered-browser revoke should be re-scoped to the owning session once that finding is actioned (currently only length/format validated) — write the test now as an expected-fail/`xfail` marker so it flips green when fixed.
- Remembered-browser list/revoke-one/revoke-all/revoke-others, including step-up PIN enforcement on the latter two.

**Gate 3 acceptance criteria:**
- Every security finding from this review cycle has a named, traceable test (pass or documented `xfail`).
- No mutating endpoint is reachable without a valid session/CSRF pair in the test suite's negative cases.

---

### Phase 4 — Functional coverage: diagnostics & backup

**Goal:** Automate the administrative surface.

**Covers:** diagnostics pagination/filtering/export, backup export (each scope: settings-only, +folders, diagnostics, full), backup import validation + rollback-on-partial-failure, factory reset (asserting the scope it claims — settings/credentials/remembered-browsers/index/cache — and separately tracking the diagnostics-key-rotation and residual-SharedPreferences gaps noted in the earlier stability review as either fixed assertions or documented `xfail`s).

**Gate 4 acceptance criteria:**
- A full backup → wipe (factory reset) → restore round trip is automated and asserts no settings field is silently dropped.
- Diagnostics export downloads and parses as valid JSONL with expected event schema fields.

---

### Phase 5 — Stability / endurance suite

**Goal:** Turn the functional suite into a sustained-load harness that watches for drift, reusing the metrics the app already exposes rather than inventing new instrumentation.

**Design:**
- A configurable-duration RF suite (`ENDURANCE_MINUTES` variable) that, in a loop:
  - churns settings writes, playback control calls, and preview/status polling at realistic multi-client rates;
  - samples `/api/v1/diagnostics` (or the device/status endpoint) for Java heap, native heap, process PSS, media-cache size, and cumulative API error count at a fixed interval;
  - asserts no monotonic upward trend in heap/PSS beyond a configured threshold over the run, no unhandled 5xx, and no server-restart event (`WEB_UI_DISCONNECTED`/reconnect count staying flat or bounded).
- Two run profiles:
  - **CI-nightly:** 2 hours, emulator-hosted, gates merges to `main` only softly (report, don't block, until proven stable).
  - **Pre-release:** 24 hours, physical device preferred, gates production release — this is the automated counterpart to the existing manual 24-hour endurance procedure in `MANUAL_TEST_CHECKLIST.md` / `FPF-FEAT-WEBUI-001` §9.2.G, and should capture the same metric list so the two procedures are directly comparable.

**Gate 5 acceptance criteria:**
- 2-hour CI run completes with no threshold breach on a clean baseline.
- 24-hour pre-release run is documented as a required step before tagging a production release, with its report archived alongside the release.

---

### Phase 6 — CI integration and release gate

**Goal:** Make the suite something the team actually runs, not a folder of scripts.

**Deliverables:**
- Fast subset (Phases 1–3, minus long endurance) wired into PR CI.
- Full functional suite (Phases 1–4) run nightly on `main`.
- Phase 5 pre-release endurance run as a manually-triggered or release-branch-triggered job.
- RF `output.xml`/`report.html`/`log.html` published as CI artifacts for every run.
- A short section added to this repo's release checklist: "Robot Framework functional suite green + endurance report attached" as a release gate, alongside the existing Gradle/manual gates.

**Gate 6 acceptance criteria:**
- A red Robot Framework functional run blocks merge (once the suite is proven stable enough to trust — allow an initial soft-fail bake-in period).
- The pre-release endurance report is a required, checked-in artifact reference for each tagged release.

---

## 5. Explicit non-goals

- This suite does not replace the hardware validation gates already tracked as outstanding in `KNOWN_LIMITATIONS.md` (real Synology/SMB hardware, API 21–22 device soak, boot-autostart per-OEM behavior).
- It does not test the native Compose rendering path, Ken Burns motion, or D-pad input — that remains Espresso/UI Automator territory in `androidTest/`.
- A clean endurance run demonstrates the **web/API layer** held up under sustained load; it is evidence toward, not proof of, whole-app stability. Say so in the release notes rather than overclaiming.

## 6. Traceability to prior review findings

| Finding (this review cycle) | Status | Suite coverage |
|---|---|---|
| `/api/pair` chunked-body size-cap bypass | Fixed in `fd0adf4` | Phase 3 regression test |
| `MediaCache` concurrent-download race | Fixed in `fd0adf4` | Needs androidTest hook, not pure HTTP — flag in Phase 3 |
| `MainThreadStallWatchdog` no exception isolation | Fixed in `fd0adf4` | Not HTTP-observable — out of scope for this suite |
| Remembered-browser revoke ownership gap | Open | Phase 3 `xfail`-then-flip test |
| Factory reset doesn't rotate diagnostics key | Open | Phase 4 `xfail`-then-flip test |
| Residual SharedPreferences outside reset scope | Open | Phase 4, documented as accepted or `xfail` |
| `ExifBackfiller` retries broken files forever | Open | Not HTTP-observable directly; could show up as a Phase 5 endurance signal (repeated slow requests) |

## 7. Open questions before implementation starts

1. Is a debug-only pairing bypass acceptable for CI, or must the suite drive the real QR/PIN flow every run?
2. Can CI host an Android emulator with the web server's site-local-bind requirement satisfied, or does Phase 1 need a persistent lab device instead?
3. Who owns the 24-hour pre-release run in practice — is it CI-scheduled against a lab device, or a manual trigger a release owner runs by hand?
