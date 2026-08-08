# FamilyPhotoFrame — Remembered Browser Pairing

**Document ID:** FPF-FEAT-TRUSTED-BROWSER-001  
**Version:** 1.1  
**Status:** Implementation-ready  
**Target baseline:** FamilyPhotoFrame v51.0 or later  
**Minimum supported OS:** Android 5.0 / API 21  
**Primary use case:** A wall-mounted frame remembers a previously paired PC or phone browser and does not request the PIN again until the selected trust period expires.

---

## 1. Objective

Extend the existing web pairing flow with an optional **Remember this browser** feature.

After a user enters the correct PIN, the frame may remember that browser for:

- Do not remember — this browser session only
- 1 hour
- 1 day
- 1 week
- 1 month
- 1 year
- Forever — until manually revoked
- Custom duration

The implementation must preserve the distinction between:

```text
short-lived active web session
```

and:

```text
long-lived remembered-browser credential
```

The browser must never store the PIN.  
The frame must never persist the raw remembered credential.

---

## 2. Terminology

### 2.1 Active web session

A short-lived authenticated session used for ordinary API requests.

Properties:

```text
Idle timeout: 30 minutes
Absolute lifetime: 12 hours
Maximum active sessions globally: 8
Maximum active sessions per remembered browser: 2
```

Rules:

- stored in browser `sessionStorage`;
- memory-only on the frame;
- carries the normal session token and CSRF token;
- expires after the idle timeout;
- always expires at the absolute lifetime;
- is recreated from a remembered credential when permitted;
- is never stored in Android backup or portable backup.

### 2.2 Remembered browser

A persistent authorization record representing one browser profile.

Properties:

- represented by a random opaque credential;
- stored persistently by the browser;
- stored only as an HMAC hash on the frame;
- has an absolute expiry or no expiry for Forever;
- can be revoked independently;
- can create new short-lived active sessions;
- cannot directly call normal settings, upload, playback, backup, or maintenance APIs.

### 2.3 Session-only option

The UI label shall be:

```text
Do not remember — this browser session only
```

This means:

- no remembered-browser record is created;
- the current pairing creates only an active session;
- closing the browser session requires PIN entry next time;
- this option does not limit other users or sessions.

---

## 3. Pairing user experience

## 3.1 Pairing form

Extend the existing PIN page:

```text
Enter the PIN shown on the frame

PIN:
[________]

Remember this browser:
[ Do not remember — this browser session only ▼ ]

Browser name:
[ Aleksu Vostro ]

[ Pair ]
```

Expiry choices:

```text
Do not remember — this browser session only
1 hour
1 day
1 week
1 month
1 year
Forever — until manually revoked
Custom
```

Default:

```text
Do not remember — this browser session only
```

## 3.2 Browser label

The label is optional.

If omitted, generate a non-sensitive label such as:

```text
Chrome browser
Firefox browser
Mobile browser
```

Validation:

```text
Minimum length: 1 character when supplied
Maximum length: 64 characters
Control characters: rejected
HTML: escaped before display
```

The label is display metadata only and is not a security identity.

## 3.3 Custom expiry

When `Custom` is selected:

```text
Remember for: [ 30 ] [ Days ▼ ]
```

Allowed units:

```text
Minutes
Hours
Days
Weeks
Months
Years
```

Bounds:

```text
Minimum: 10 minutes
Maximum: policy-configured, hard maximum 10 years
```

Show the calculated result:

```text
This browser will remain paired until 28 August 2026, 21:45.
```

The frame timezone is used for display.  
UTC epoch time is used for validation and persistence.

## 3.4 Forever

Selecting Forever must show a warning:

```text
This browser will remain trusted until you revoke it,
reset the frame, clear application data, or uninstall the app.
```

Requirements:

- require an explicit confirmation checkbox;
- require PIN confirmation again when policy requires step-up;
- reject Forever when Android policy disables it;
- reject Forever by default while the server uses plain HTTP.

Persistence:

```text
expiresAtEpochMs = null
```

## 3.5 Successful pairing messages

Session only:

```text
Paired for this browser session.
```

Persistent:

```text
This browser is remembered until 28 August 2026.
```

Forever:

```text
This browser is remembered until you revoke it.
```

---

## 4. Automatic sign-in flow

When the web UI loads:

```text
Load page
→ check active session
→ if active session is valid, continue
→ otherwise check remembered-browser credential
→ exchange credential for a fresh active session
→ if accepted, continue without PIN
→ if expired, revoked, invalid, or rejected, clear local credential
→ show PIN page
```

The remembered credential must never be used as the normal bearer token.

---

## 5. Token generation, hashing, and rotation

## 5.1 Credential generation

Generate 32 random bytes:

```kotlin
val rawCredential = ByteArray(32)
SecureRandom().nextBytes(rawCredential)
```

Encode for transport with URL-safe Base64 without padding.

The returned credential must contain enough information to locate the record without exposing secrets. Recommended format:

```text
<record-id>.<random-secret>
```

The record ID is non-secret.  
The random secret is the credential.

## 5.2 Server-side storage

Store:

```text
HMAC-SHA-256(serverSecret, recordId || "." || randomSecret)
```

Never store:

- raw random secret;
- full raw credential;
- PIN;
- active session token;
- CSRF token.

Credential verification must use constant-time comparison.

## 5.3 Rotation after successful exchange

Every successful remembered-credential exchange must rotate the credential.

Flow:

```text
valid remembered credential received
→ create fresh active session
→ create a new random remembered credential
→ persist new HMAC hash
→ mark previous hash as grace-valid for 30 seconds
→ return new remembered credential
```

Grace period:

```text
30 seconds
```

Purpose:

- allow two tabs opened at nearly the same time;
- avoid one tab invalidating another immediately;
- support browser restart races.

## 5.4 Reuse detection

After the grace period, reuse of an old rotated credential is suspicious.

Required behavior:

```text
old credential reused after grace period
→ revoke remembered-browser record
→ revoke active sessions created from that record
→ emit security diagnostic
→ require PIN pairing again
```

The external API response must remain generic and must not reveal whether the token was unknown, revoked, expired, or replayed.

---

## 6. Multiple-tab and browser-profile behavior

`localStorage` is shared by all tabs in one browser profile.  
`sessionStorage` is unique to each tab.

Rules:

- one remembered credential is shared by all tabs in the same browser profile;
- each tab may obtain its own active session;
- each active session counts toward the per-browser session limit;
- maximum two active sessions per remembered browser;
- a third active session request revokes or replaces the oldest active session for that remembered browser;
- expired sessions are purged before issuing a new one.

Browser synchronization:

- use `BroadcastChannel` where supported;
- fall back to the `storage` event;
- only one tab should perform remembered-token exchange at a time;
- other tabs wait for the updated rotated credential;
- timeout the coordination lock after 10 seconds;
- never store the PIN in inter-tab messages.

---

## 7. Browser storage

## 7.1 Current HTTP implementation

Until HTTPS is available:

```text
Active session → sessionStorage
Remembered credential → localStorage
```

Keys:

```text
fpf.activeSession
fpf.csrf
fpf.rememberedCredential
fpf.rememberedBrowserId
fpf.rememberedCredentialUpdatedAt
```

## 7.2 Future HTTPS implementation

Preferred final design:

```text
HttpOnly
Secure
SameSite=Strict
Path=/
```

The remembered credential should move to a secure cookie when encrypted transport is implemented.

## 7.3 Cache protection

Pairing and remembered-session exchange responses must include:

```text
Cache-Control: no-store
Pragma: no-cache
Expires: 0
```

The remembered credential must never appear in:

- URL;
- query parameters;
- browser history;
- referrer;
- diagnostics;
- server access logs;
- error messages;
- QR-code URLs.

---

## 8. Plain-HTTP restrictions

Because the current web server uses plain HTTP:

```text
Remembered browsers: disabled by default
Forever: disabled by default
Android-side enable: required
Trusted-LAN warning: required
```

Warning text:

```text
Remembered browser credentials can be intercepted on an untrusted network.
Enable this feature only on a trusted private LAN.
```

Public-release policy:

- persistent remembered access remains off by default;
- Forever remains off by default;
- enabling persistent access requires confirmation on the Android device;
- unrestricted public use requires encrypted transport.

---

## 9. Exchange endpoint security

Endpoint:

```text
POST /api/v1/security/session/from-remembered
```

Requirements:

- POST only;
- `Content-Type: application/json`;
- maximum body size: 8 KiB;
- no CORS;
- validate Host;
- validate Origin when supplied;
- reject credentials in URL/query;
- per-client rate limit;
- global emergency rate limit;
- constant-time verification;
- generic authentication failure;
- no response caching;
- no automatic credential renewal after failure;
- no normal CSRF requirement because no active session exists yet.

Recommended limits:

```text
Per client: 20 exchange attempts per 10 minutes
Global emergency limit: 200 attempts per 10 minutes
```

On limit exceeded:

```text
HTTP 429
Retry-After: bounded value
```

Do not globally lock legitimate clients out for a long fixed period.

---

## 10. Active-session behavior

Recommended values:

```text
Idle timeout: 30 minutes
Absolute lifetime: 12 hours
Maximum active sessions globally: 8
Maximum active sessions per remembered browser: 2
```

When creating a session from a remembered credential:

1. purge expired active sessions;
2. validate remembered record;
3. validate expiry;
4. validate revocation;
5. validate clock state;
6. enforce per-browser session limit;
7. enforce global session limit;
8. issue new session and CSRF token;
9. rotate remembered credential.

The active session remains subject to normal CSRF protection.

---

## 11. Step-up authentication

Remembered access must not remove PIN checks for high-risk operations.

Require recent PIN confirmation for:

- factory reset;
- portable backup import;
- revoke all remembered browsers;
- change pairing PIN;
- change remembered-browser policy;
- enable Forever;
- increase maximum expiry;
- increase maximum remembered-browser count;
- change security mode;
- enable web upload when required by policy.

Recommended elevation lifetime:

```text
5 minutes
```

---

## 12. PIN reset and regeneration

Changing or regenerating the PIN must show:

```text
What should happen to remembered browsers?

(•) Revoke all remembered browsers
( ) Keep remembered browsers
```

Default:

```text
Revoke all remembered browsers
```

If the user keeps remembered browsers:

- require explicit confirmation;
- record a security diagnostic;
- keep existing trust expiry unchanged.

Factory reset always revokes and deletes all remembered browsers.

---

## 13. Clock and timezone handling

## 13.1 Storage

Persist:

```text
createdAtEpochMs
lastUsedAtEpochMs
expiresAtEpochMs
lastTrustedWallClockEpochMs
```

Timezone is display metadata only.

## 13.2 Backward clock detection

If the device wall clock moves backwards by more than:

```text
10 minutes
```

then:

```text
remembered-browser exchange
→ fail closed
→ require PIN pairing
→ keep records for owner review
→ emit diagnostic
```

Timezone-only changes must not invalidate trust.

## 13.3 Forward clock movement

If the clock moves forward:

- expiry may occur earlier;
- expired trust must not be restored automatically when the clock is moved back;
- owner must pair again.

## 13.4 Month and year calculation

Use calendar arithmetic:

```text
1 month → plus one calendar month
1 year  → plus one calendar year
```

Do not approximate:

```text
1 month = 30 days
1 year = 365 days
```

Test end-of-month and leap-year behavior.

---

## 14. Android 5 Keystore strategy

Android 5/API 21 does not provide the same symmetric-key Keystore APIs as API 23+.

Use two explicit implementations.

## 14.1 API 23 and later

Preferred:

- generate a Keystore-backed HMAC-SHA-256 key where supported;
- or generate an AES-GCM wrapping key and store a random 32-byte HMAC secret encrypted at rest;
- use `KeyGenParameterSpec`;
- no export of key material.

## 14.2 API 21–22

Required compatible path:

1. generate an RSA key pair in Android Keystore using `KeyPairGeneratorSpec`;
2. generate a random 32-byte HMAC server secret;
3. encrypt/wrap that secret using the Keystore RSA public key;
4. store only the wrapped ciphertext in private app storage;
5. unwrap into memory when needed;
6. use the unwrapped secret for HMAC-SHA-256;
7. clear temporary byte arrays where practical.

Use a reviewed API-21-compatible RSA wrapping mode.

No plaintext fallback is permitted.

## 14.3 Key loss or unwrap failure

If the Keystore key or wrapped server secret cannot be recovered:

```text
disable remembered-browser exchange
→ revoke all remembered-browser records
→ clear active sessions derived from them
→ require PIN pairing again
→ show owner-facing error
```

Do not silently generate a new key while preserving old trust records.

---

## 15. Android settings

Add:

```text
Settings → Web server → Remembered browsers
```

Controls:

- Allow browsers to be remembered
- Default expiry
- Maximum allowed expiry
- Allow Forever
- Maximum remembered browsers
- Require PIN again for sensitive actions
- View remembered browsers
- Revoke all remembered browsers

Recommended defaults:

```text
Allow browsers to be remembered: Off
Default expiry: Do not remember — this browser session only
Maximum allowed expiry: 1 year
Allow Forever: Off
Maximum remembered browsers: 8
Require PIN again for sensitive actions: On
```

Hard bounds:

```text
Minimum remembered-browser limit: 1
Maximum remembered-browser limit: 32
Maximum custom expiry: 10 years
```

---

## 16. Remembered-browser management UI

Display:

```text
Aleksu Vostro — Chrome on Linux
Created: 29 July 2026, 20:12
Last used: 29 July 2026, 21:35
Expires: 29 August 2026, 20:12
Status: Active
Current browser: Yes
[ Revoke ]
```

Available actions:

- Revoke current browser
- Revoke selected browser
- Revoke all other browsers
- Revoke all browsers

Revocation takes effect immediately.

The next exchange attempt returns a generic authentication failure and the browser clears local credentials.

---

## 17. Maximum remembered-browser behavior

When the configured limit is reached:

```text
Do not silently revoke an existing remembered browser.
```

Return:

```json
{
  "ok": false,
  "code": "REMEMBERED_BROWSER_LIMIT_REACHED",
  "message": "This frame already remembers the maximum number of browsers."
}
```

The PIN pairing may still create a session-only session.

UI:

```text
The frame remembers the maximum number of browsers.
[ Pair for this session ] [ Manage remembered browsers ]
```

---

## 18. Data model

Remembered-browser record:

```kotlin
data class RememberedBrowser(
    val id: String,
    val currentTokenHash: ByteArray,
    val previousTokenHash: ByteArray?,
    val previousTokenValidUntilEpochMs: Long?,
    val label: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val revokedAtEpochMs: Long?,
    val browserSummary: String?,
    val osSummary: String?,
    val lastTrustedWallClockEpochMs: Long,
)
```

Policy:

```kotlin
data class RememberedBrowserPolicy(
    val enabled: Boolean = false,
    val defaultExpiry: RememberExpiry = RememberExpiry.SessionOnly,
    val maxExpirySeconds: Long = 31_536_000L,
    val allowForever: Boolean = false,
    val maxRememberedBrowsers: Int = 8,
    val requireStepUpForSensitiveActions: Boolean = true,
    val rotateOnExchange: Boolean = true,
    val rotationGraceSeconds: Int = 30,
)
```

Expiry model:

```kotlin
sealed interface RememberExpiry {
    data object SessionOnly : RememberExpiry
    data object OneHour : RememberExpiry
    data object OneDay : RememberExpiry
    data object OneWeek : RememberExpiry
    data object OneMonth : RememberExpiry
    data object OneYear : RememberExpiry
    data object Forever : RememberExpiry
    data class Custom(
        val amount: Int,
        val unit: CustomExpiryUnit,
    ) : RememberExpiry
}
```

---

## 19. Persistence and backup policy

Preferred storage:

- Room table for remembered-browser records;
- DataStore for global policy;
- Keystore-backed HMAC secret.

Never include in portable backup:

- active sessions;
- remembered raw credentials;
- token hashes;
- remembered-browser records;
- wrapped HMAC server secret.

Android cloud backup and device transfer must exclude:

- remembered-browser table/records;
- active-session data;
- trust-key material.

Restoring settings on another frame must require all browsers to pair again.

---

## 20. API additions

### Pair with optional persistent trust

```text
POST /api/v1/security/pair
```

Preset request:

```json
{
  "pin": "12345678",
  "remember": {
    "mode": "ONE_MONTH",
    "label": "Aleksu Vostro",
    "confirmForever": false
  }
}
```

Custom request:

```json
{
  "pin": "12345678",
  "remember": {
    "mode": "CUSTOM",
    "amount": 45,
    "unit": "DAYS",
    "label": "Office PC"
  }
}
```

Response:

```json
{
  "ok": true,
  "data": {
    "sessionToken": "...",
    "csrfToken": "...",
    "rememberedCredential": "...",
    "rememberedBrowserId": "...",
    "expiresAt": "2026-08-29T18:00:00Z"
  }
}
```

For session-only mode, omit remembered fields.

### Exchange remembered credential

```text
POST /api/v1/security/session/from-remembered
```

Request:

```json
{
  "rememberedCredential": "record-id.random-secret"
}
```

Response:

```json
{
  "ok": true,
  "data": {
    "sessionToken": "...",
    "csrfToken": "...",
    "sessionExpiresAt": "...",
    "rotatedRememberedCredential": "record-id.new-random-secret"
  }
}
```

### Management

```text
GET  /api/v1/security/remembered-browsers
POST /api/v1/security/remembered-browsers/{id}/revoke
POST /api/v1/security/remembered-browsers/current/revoke
POST /api/v1/security/remembered-browsers/revoke-others
POST /api/v1/security/remembered-browsers/revoke-all
GET  /api/v1/security/remembered-browser-policy
POST /api/v1/security/remembered-browser-policy
```

All management writes require:

- active session;
- CSRF;
- revision protection;
- step-up PIN where required.

---

## 21. Validation

Reject:

- unsupported expiry mode;
- custom duration below minimum;
- custom duration above policy maximum;
- Forever when disallowed;
- Forever without explicit confirmation;
- empty or oversized label;
- malformed credential;
- unknown credential;
- expired credential;
- revoked credential;
- rotated credential replay after grace period;
- remembered-browser limit exceeded;
- clock rollback beyond tolerance;
- disabled remembered-browser policy.

External credential failures should use one generic response:

```json
{
  "ok": false,
  "code": "REMEMBERED_AUTH_FAILED",
  "message": "Remembered browser authorization is no longer valid."
}
```

Do not reveal whether the credential was unknown, revoked, expired, or replayed.

---

## 22. Browser logout behavior

Two actions:

### Log out

```text
clear active session and CSRF
→ retain remembered credential
→ browser may restore a new session later
```

### Log out and forget this browser

```text
revoke remembered-browser record
→ clear remembered credential
→ clear active session and CSRF
→ show PIN page
```

If the frame is unreachable:

- clear local credential immediately;
- explain that the frame-side record can be revoked later;
- do not queue an automatic remote revocation.

---

## 23. Diagnostics

Add bounded events:

```text
REMEMBERED_BROWSER_CREATED
REMEMBERED_BROWSER_SESSION_CREATED
REMEMBERED_BROWSER_TOKEN_ROTATED
REMEMBERED_BROWSER_TOKEN_REPLAYED
REMEMBERED_BROWSER_EXPIRED
REMEMBERED_BROWSER_REVOKED
REMEMBERED_BROWSER_REVOKE_ALL
REMEMBERED_BROWSER_REJECTED
REMEMBERED_BROWSER_POLICY_CHANGED
REMEMBERED_BROWSER_CLOCK_ROLLBACK
REMEMBERED_BROWSER_KEY_LOST
```

Never log:

- raw credential;
- credential hash;
- HMAC secret;
- PIN;
- active session token;
- CSRF token;
- full user-agent string where unnecessary.

---

# 24. Five-phase implementation plan

## Phase 1 — Policy, expiry, and persistence

Implement:

- clarified expiry model;
- remembered-browser policy;
- Room entity and DAO;
- non-destructive migration;
- API-21/API-23 Keystore strategies;
- HMAC service;
- calendar expiry calculator;
- clock rollback detector;
- cleanup logic;
- backup exclusions.

Review gate:

- raw credential never persisted;
- no plaintext secret fallback;
- month/year expiry tests pass;
- backward-clock behavior fails closed;
- API 21 key wrapping works;
- migration is non-destructive.

## Phase 2 — Pairing, exchange, and rotation

Implement:

- optional remember object in pairing;
- remembered credential creation;
- exchange endpoint;
- active-session creation;
- token rotation;
- 30-second previous-token grace;
- replay detection;
- session-cap integration;
- no-store response headers;
- rate limiting.

Review gate:

- remembered credential cannot call normal APIs directly;
- expired/revoked/replayed credentials fail;
- PIN is never stored;
- rotation works with two tabs;
- old-token replay revokes trust;
- body, Host, Origin, and rate limits pass.

## Phase 3 — Browser UI and multi-tab coordination

Implement:

- unambiguous session-only label;
- preset selector;
- custom duration fields;
- expiry preview;
- browser label;
- Forever confirmation;
- automatic session restoration;
- BroadcastChannel synchronization;
- storage-event fallback;
- logout;
- logout and forget;
- trusted-LAN warning.

Review gate:

- session-only remains default;
- only one tab rotates credential at a time;
- local credential updates propagate;
- rejected credential is cleared;
- responses are not cached;
- no credential appears in URL or logs.

## Phase 4 — Management, revocation, and PIN reset

Implement:

- Android management page;
- web management page;
- current-browser marker;
- revoke selected/current/others/all;
- remembered-browser policy;
- limit handling;
- PIN reset choice;
- step-up authentication;
- escaped labels.

Review gate:

- revocation is immediate;
- maximum limit never silently evicts trust;
- PIN reset defaults to revoke all;
- high-risk policy changes require PIN;
- current-browser identification is correct.

## Phase 5 — Hardening, compatibility, and endurance

Implement:

- restart recovery;
- Keystore-loss behavior;
- expiry cleanup;
- clock/timezone tests;
- multi-browser stress;
- API 21/API 23/API 36 matrix;
- diagnostics;
- security review;
- backup and restore verification;
- plain-HTTP policy validation.

Review gate:

- trust survives restart only while valid;
- clock rollback does not extend trust;
- Forever remains revocable;
- lost key invalidates trust safely;
- trust material is absent from all backups;
- API 21 behavior is verified;
- HTTP mode cannot silently enable persistent or Forever trust.

---

## 25. Tests

### Unit tests

- session-only;
- 1 hour;
- 1 day;
- 1 week;
- one calendar month;
- one calendar year;
- Forever;
- custom minutes/hours/days/weeks/months/years;
- minimum/maximum bounds;
- leap year;
- end-of-month;
- timezone change;
- 10-minute backward clock tolerance;
- backward movement beyond tolerance;
- HMAC verification;
- token rotation;
- grace-period acceptance;
- replay after grace;
- expired/revoked/malformed credential;
- browser-limit rejection;
- Keystore unwrap failure.

### API tests

- pair without persistence;
- pair with every preset;
- custom pairing;
- disallowed Forever;
- Forever without confirmation;
- valid remembered exchange;
- expired exchange;
- revoked exchange;
- replay exchange;
- body too large;
- invalid content type;
- invalid Host/Origin;
- rate limit;
- session cap;
- revoke current/selected/others/all;
- policy revision conflict;
- unauthorized management;
- CSRF rejection.

### Browser tests

- initial PIN pairing;
- reload;
- browser restart;
- frame restart;
- two simultaneous tabs;
- three tabs exceeding per-browser session limit;
- rotation synchronization;
- localStorage update propagation;
- expiry reached;
- remote revocation;
- logout only;
- logout and forget;
- browser limit;
- Forever confirmation;
- plain-HTTP warning.

### Device tests

- Android 5/API 21;
- Android 6/API 23;
- API 36;
- process restart;
- device reboot;
- clock forward;
- clock backward;
- timezone-only change;
- PIN reset;
- factory reset;
- backup/restore;
- Keystore key loss simulation.

---

## 26. Acceptance criteria

- The UI offers session-only, 1 hour, 1 day, 1 week, 1 month, 1 year, Forever, and Custom.
- The session-only label is unambiguous.
- Session-only remains the default.
- Active session idle timeout is 30 minutes.
- Active session absolute lifetime is 12 hours.
- Persistent trust survives browser and frame restart.
- Expired, revoked, replayed, or invalid trust requires PIN.
- The PIN is never stored in the browser.
- The raw remembered credential is never stored on the frame.
- Remembered credentials rotate after successful exchange.
- Multiple tabs do not invalidate each other unexpectedly.
- Reusing an old rotated credential after grace revokes trust.
- PIN reset defaults to revoking all remembered browsers.
- Clock rollback beyond 10 minutes fails closed.
- Forever requires explicit confirmation and remains revocable.
- Remembered browsers can be listed and revoked.
- Sensitive actions still require PIN step-up.
- Maximum remembered-browser count is configurable.
- API 21 Keystore handling has no plaintext fallback.
- Key loss invalidates trust safely.
- Trust records and keys are excluded from backups.
- Pairing and exchange responses are never cached.
- No credential appears in a URL, log, or diagnostic.
- Persistent trust is disabled by default over plain HTTP.
- Android 5/API 21 remains supported.

---

## 27. Review verdict

The task is now implementation-ready.

All previously ambiguous decisions are fixed:

1. active-session lifetime and limits;
2. remembered-token rotation and replay handling;
3. multi-tab synchronization;
4. plain-HTTP restrictions;
5. PIN reset behavior;
6. clock rollback protection;
7. concrete API 21/API 23 Keystore strategy;
8. exchange endpoint security;
9. Forever confirmation;
10. additional acceptance and failure criteria.

The core rule remains:

```text
long-lived remembered-browser credential
≠
short-lived active web session
```
