# Remembered Browser — Manual Acceptance Test

## Build

```bash
chmod +x gradlew
./gradlew clean testDebugUnitTest installDebug
```

## Required devices

- Android 5/API 21 emulator or device
- Huawei Android 6/API 23 frame
- Desktop browser with two tabs
- Optional API 36 emulator for target-behavior regression

## Core test

1. Enable the web server on the frame.
2. Enable Remembered browsers in Android Settings.
3. Keep Forever disabled initially.
4. Pair a PC with `1 hour` and a browser label.
5. Close and reopen the browser; verify no PIN is requested.
6. Restart the frame; reload the PC page; verify automatic restoration.
7. Open two tabs simultaneously; verify both load and the credential remains valid.
8. Select `Sign out`; reload; verify automatic restoration.
9. Select `Sign out and forget this browser`; verify the PIN is required.

## Expiry presets

Test session-only, 1 hour, 1 day, 1 week, 1 month, 1 year, custom, and Forever. Confirm:

- session-only does not survive browser closure;
- fixed choices display the correct absolute expiry;
- Forever requires confirmation and is only offered when locally enabled;
- custom values below ten minutes are rejected;
- values above frame policy are rejected.

## Management

1. Pair two browser profiles.
2. Verify both appear in Android and web management.
3. Revoke the non-current profile; verify it cannot restore a session.
4. Revoke all other browsers; verify the current browser remains active.
5. Revoke all with PIN step-up; verify every browser requires the PIN.
6. Generate a new pairing PIN with the default option; verify remembered trust is revoked.
7. Repeat while selecting Keep remembered browsers; verify persistent trust survives but active sessions are recreated.

## Security and recovery

- Move the device clock back by more than ten minutes; remembered exchange must fail and require PIN.
- Change timezone only; valid trust must remain valid.
- Clear application data; all trust must disappear.
- Restore a settings backup to another frame; no browser should remain trusted.
- Verify remembered credentials do not appear in URLs, browser history, diagnostics, or exported support data.
- Use only a trusted LAN because the current server transport is plain HTTP.
