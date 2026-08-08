# Remembered Browser — Phase 1 Review

## Scope

Policy, expiry calculation, Room persistence, HMAC credential hashing, Keystore-backed secret storage, clock rollback, migration, and backup boundaries.

## Review findings and corrections

1. **Malformed or lost HMAC secret could leave trust records present but unusable.** Corrected with fail-closed revocation and `REMEMBERED_BROWSER_KEY_LOST` diagnostics before a fresh secret is created for future pairing.
2. **A one-year calendar period can span 366 days.** Corrected the default policy cap so leap-year calendar arithmetic is accepted.
3. **Raw browser credentials could have been tempting to persist for lookup.** The schema stores only current, previous, and retired HMAC hashes; raw credentials never enter Room.
4. **Month/year expiry could be approximated with fixed days.** Corrected by using `Calendar.add`, compatible with Android 5.
5. **Trust records needed a non-destructive migration.** Added and verified migration 5→6.

## Gate result

PASS. SQL, migration, persistence, hashing, expiry, key-loss, and API-21 compatibility contracts passed.
