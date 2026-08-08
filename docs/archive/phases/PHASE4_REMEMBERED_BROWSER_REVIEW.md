# Remembered Browser — Phase 4 Review

## Scope

Android and web management, policy controls, browser listing, selected/current/all revocation, step-up PIN, and PIN-reset behavior.

## Review findings and corrections

1. **The private policy endpoint returned only public fields.** Corrected to return step-up, rotation, grace, count, and maximum-expiry policy fields to authenticated clients.
2. **Step-up PIN entry through a browser prompt would expose digits as plain text.** Corrected with a dedicated password dialog.
3. **Android owner controls could revoke trust records but leave active web sessions alive.** Corrected with explicit controller methods that revoke sessions tied to a record or all sessions.
4. **Generating a new pairing PIN had no explicit trust decision.** Corrected with a default-off “Keep remembered browsers” option; the safe default revokes all trust.
5. **Maximum browser count could silently evict an existing browser.** The backend continues to reject new persistent trust at the limit; management exposes explicit revocation instead.

## Gate result

PASS at source, JavaScript syntax, Kotlin syntax, SQL, and migration-contract level. Revocation is immediate and policy changes require the current PIN in the web UI.
