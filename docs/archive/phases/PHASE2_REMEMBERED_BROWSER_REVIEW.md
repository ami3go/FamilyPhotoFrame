# Remembered Browser — Phase 2 Review

## Scope

Persistent pairing request, remembered-session exchange, active-session limits, token rotation, replay detection, rate limiting, response caching, and revocation integration.

## Review findings and corrections

1. **Disabling remembered-browser policy did not initially block old credentials.** Corrected in the manager exchange path; disabled policy now fails closed and revokes associated active sessions.
2. **Active sessions were previously unbounded.** Added 30-minute idle timeout, 12-hour absolute lifetime, global limit 8, and per-browser limit 2.
3. **A long-lived remembered token could be used directly as an API session.** Corrected by allowing it only at the exchange endpoint, which mints a normal CSRF-protected active session.
4. **Rotation races across tabs needed controlled grace.** Added current/previous/retired HMAC slots and a 30-second grace period.
5. **Replay after grace needed a strong response.** Late replay revokes the remembered record and active sessions derived from it.

## Gate result

PASS. Remembered authentication, active-session limits, exchange endpoint, rotation, replay, and generic-failure contracts passed.
