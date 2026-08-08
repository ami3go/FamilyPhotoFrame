# Family Photo Frame v52.9 — Phase 5 review

## Scope

Phase 5 makes every diagnostic identity installation-specific and applies one privacy
boundary to in-memory entries, rotated files, bundles, web/on-device consumers, and
crash/ANR evidence.

## Implemented contracts

- A random 256-bit HMAC-SHA-256 key is generated once, stored in app-private
  preferences, installed before the first diagnostic event, and excluded from both
  Android backup/transfer rule sets.
- Typed identity tokens retain 12 digest bytes and remain stable on one installation
  while differing across installations.
- Catalog field allowlists are enforced before an entry reaches any surface.
- Forbidden keys are dropped. Paths, URIs, URLs, network addresses, hostnames, query
  strings, photo filenames, credential markers, GPS/EXIF text, Unicode/control content,
  and other unstructured private values are replaced with typed HMAC tokens.
- Variable `message` content is no longer retained; dynamic evidence uses structured
  fields.
- Slide selection/rendering, uploads, browser trust, playlists, folders, photos, source
  identities, and web-server binding events now use typed tokens or coarse categories.
- Crash/ANR state accepts only a typed presentation token and known source kind; stack
  source evidence accepts code filenames only.

## Review findings and fixes

- The former key blacklist did not protect a private URL supplied under an allowed key.
  Value-shape enforcement now runs at the common boundary.
- `SLIDE_SELECTED` and `SLIDE_RENDERED` still supplied raw folder names, photo IDs, and
  source IDs even though some were discarded by the v2 allowlist. The call sites now
  preserve privacy-safe correlation explicitly.
- Web startup included the host in a message. It now records only safe port and coarse
  private-network category.
- Upload filenames and browser/playlist identifiers are tokenized at their owners.
- Three remaining variable-message calls were converted to structured fields during
  review: boot launch failure, panel motion, and performance sampling.
- The engine type-check harness initially omitted the new pure policy dependency; the
  harness now compiles it with the real diagnostics and engine sources.

## Verification

- Static installation-key, backup-exclusion, call-site, web-binding, and boundary checks.
- Adversarial fixtures across memory, on-device rendering, standard/bulk rotation,
  streamed bundle, and crash envelope.
- Stable/split-install HMAC tests and typed-token shape tests.
- Pure-Kotlin suite, 95 Room queries, and all migrations pass.
- Engine and persistence sources pass the offline Kotlin type-check harness.

## Gate result

Phase 5 is accepted for integration. Android backup/restore and multi-process restart
behavior remain part of the final device qualification gate.
