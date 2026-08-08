# Five features — v40

Weighted deliberately toward reducing risk rather than adding to it: the previous round
left a ~150-line `SlideshowViewModel` refactor that had only ever been *parsed*, never
type-checked. Two of the five items below exist to close that gap.

## 1. `SourcePoolPolicy` — the merged-pool rules, made executable

The v39 merged-pool logic lived inline in the ViewModel, tangled with coroutines, Room
and Android URIs, so in this environment it could only be syntax-checked. It is now a
pure object: `initialPlan`, `planFor`, `afterPromote`, `afterDemote`.

The offline harness **compiles and runs** pure files, so these rules are now genuinely
type-checked and executed — 14 assertions covering the cases that matter, including the
regression that a recovering source must *rejoin* the pool rather than replace it.
`SourcePoolPolicyTest` mirrors them for Gradle.

This is the single most valuable change here: it converts the riskiest part of the last
round from "reads correct" to "runs correct".

## 2. Folder ("album") selection

Play from chosen folders only. `folderSummaries` returns each folder with a photo count
from the existing index — one grouped query, no extra walk of the NAS.

- **Empty means all folders**, the default and the prior behaviour, so an upgrade cannot
  silently narrow what a frame shows.
- First deselection expands "all" into an explicit list minus that folder, so the toggle
  does what it looks like it does.
- SQLite cannot parse `IN ()`, so the four selection queries take an `allFolders` flag
  alongside the list, and the list is padded with a never-matching sentinel when off.
- Web parity via `selectedFolders`; blank clears the filter.

## 3. Cycle progress indicator

`PlaybackQueue` already tracked pool size and remaining-in-cycle; nothing surfaced it.
The info panel now shows "37 / 412 this round" in the cycle-based modes, and reports
nothing in windowed-random mode, which has no cycle. Fallback picks deliberately do not
overwrite the primary pool's readout.

## 4. Web parity for the new controls

`selectedFolders` and `alsoPlay` join the redacted config and the apply path, validated
as a group so one bad value cannot half-apply. Unchanged: no password, 2FA code, or
certificate pin is ever accepted over the web.

## 5. `CAPABILITIES.md` — a manifest the build verifies

Tooling, not a user feature, and the most useful thing in this commit.

`README.md`, `KNOWN_LIMITATIONS.md` and `ROADMAP.md` have each described this project
inaccurately more than once. **I was misled twice**: I proposed implementing QR pairing,
R8/minification, release signing, WebDAV, folder filters and recovery-policy tests — all
already shipped. That is wasted work caused by trusting prose.

`CAPABILITIES.md` maps each capability to a `path::symbol` that must exist, and
`check-consistency.py` verifies all 22 rows on every run. Deleting a capability or
mistyping a symbol fails the build — confirmed by deliberately corrupting a row and
watching it fail. Prose can drift silently; this cannot.

Its "Not implemented" section is the honest remaining list: Synology Photos albums,
QuickConnect, Immich, two-sources-of-the-same-kind, reverse geocoding.

## What I chose not to do

**An Immich source.** It is the obvious next feature and I deliberately skipped it.
Adding a third network protocol I cannot run, on top of a refactor that has never been
compiled, would have produced volume rather than progress — and would have made the
eventual first real build harder to debug, because failures from two unverified layers
would arrive together.

## Verification

`check-consistency.py` (now including the capability manifest), Kotlin parse of all main
sources, and the full pure-logic harness pass. New: 14 `SourcePoolPolicy` assertions in
the harness, plus `SourcePoolPolicyTest`.

Two mistakes were caught by tooling rather than review:

- appending `FolderSummary` to `PhotoItemEntity.kt` made the schema checker read its
  fields as two unmigrated v5 columns — a genuine upgrade crash had it shipped. It now
  lives in its own file.
- `stringRes` had no two-argument overload for the progress string.

**Still unverified.** No Gradle build, no device, no NAS. The v39 ViewModel refactor is
now *less* exposed — its decisions are tested — but the surrounding Android code, and the
new Room queries (`folderSummaries`, the four folder-filtered selection queries) remain
unchecked by any real annotation processor. `./gradlew --no-configuration-cache clean
testDebugUnitTest assembleDebug` is still the necessary next gate.
