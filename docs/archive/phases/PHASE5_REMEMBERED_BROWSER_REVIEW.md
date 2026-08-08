# Remembered Browser — Phase 5 Review

## Scope

Hardening, cleanup, backup boundaries, clock behavior, token replay, key loss, multi-session limits, compatibility checks, and full regression.

## Review findings and corrections

1. **Exchange throttling was only per client.** Added a bounded global emergency limit of 200 attempts per ten minutes while retaining the per-client limit of 20.
2. **Expired-record cleanup ran only once when the controller started observing settings.** Replaced it with a lifecycle-bound six-hour cleanup loop.
3. **Calendar month/year behavior lacked executable evidence.** Added end-of-month, leap-day, Forever-confirmation, and custom-minimum unit tests.
4. **Credential rotation, grace, replay, clock rollback, maximum count, and key-loss behavior lacked end-to-end manager tests.** Added an offline executable harness using the real manager and expiry implementation.
5. **Keystore loss revoked persistent records but could return a generic rejection without identifying which active sessions to close.** Corrected the manager result so the web security layer immediately revokes sessions derived from the affected browser.
6. **Active-session absolute and per-browser limits were not represented in the Gradle test suite.** Added unit tests to `WebSecurityTest`.
7. **Existing web verification expected the prior cached-asset revision.** Updated the contract to the new revision so browsers receive the remembered-browser UI instead of stale assets.

## Gate result

PASS at source, JavaScript, SQL, Room migration, pure Kotlin, manager runtime, web security, transition, diagnostics, and project-regression levels.

A real Android Gradle compilation remains a local-device gate because Gradle 8.9 is unavailable in the offline review environment.
