# FamilyPhotoFrame Web UI — Phase 5 Hardware and Endurance Test

Target: Huawei PLK-L01, Android 6 / API 23. Run this after `./gradlew clean installDebug`.

## Configuration

- Slideshow interval: 3 seconds.
- Portrait collage: Automatic, maximum 3.
- Transitions: Ambient Random.
- Web preview: Overview open with 2-second refresh.
- Open a second browser client for at least two hours.
- Include single images, two/three-image collages, slow SMB files, and unsupported HEIC files.

## 24-hour procedure

1. Pair both browser clients.
2. Leave Overview open on client A.
3. Open Diagnostics on client B and refresh periodically.
4. Change one safe setting from each client and confirm revision-conflict recovery.
5. Run a manual rescan twice; verify one physical scan is performed.
6. Disconnect/reconnect the LAN once.
7. Restart the web server once and the application once.
8. Export diagnostics and a settings backup.
9. Validate/import the backup, then test **Rollback last import**.
10. Continue until 24 hours have elapsed.

## Pass criteria

- No app crash, ANR, OOM, or unintended black slideshow frame.
- Preview never changes slideshow history or restarts the display interval.
- At most one preview-generation job; both clients reuse the same revision.
- No duplicate physical scan from repeated requests.
- Web UI remains responsive on phone and desktop.
- Hidden browser tabs reduce polling.
- No unbounded Java heap, native heap, PSS, preview, or browser-row growth.
- Settings remain synchronized after reconnect/restart.
- Unauthenticated and missing-CSRF writes are rejected.
- No password, credential reference, pairing token, or private cache path appears in exports.

## Evidence to retain

- `familyphotoframe-diagnostics.jsonl`
- Settings backup before and after import/rollback
- Browser console log from both clients
- Start/end Java heap, native heap, PSS, image-cache and media-cache sizes
- Preview generation/cache-hit counts
- API failure/reconnect counts
- Screen recording covering single-to-collage preview and navigation
