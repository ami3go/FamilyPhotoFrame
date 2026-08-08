# Manual Test Checklist — Phase 0 acceptance (spec §22.1)

Run on at least one phone and, if available, one Android TV / box with a remote.
Each row maps to a Phase 0 acceptance criterion.

## Source selection & persistence

- [ ] First launch shows the "Choose a folder" screen with **Choose folder** and
      **Use sample photos**.
- [ ] Choosing a folder starts the slideshow from that folder.
- [ ] **Kill and relaunch the app** — it resumes the same folder **without** re-prompting
      (persisted SAF grant).
- [ ] Reboot the device, relaunch — folder still works (grant survived reboot).

## Indexing without blocking the UI

- [ ] Point at a folder with **≥ 1000 images**. The UI stays responsive; the
      "Looking through your folder… N photos found" indicator climbs while photos
      already begin showing.
- [ ] No ANR dialog appears during indexing.

## Permission gate (the headline Phase 0 guarantee)

- [ ] Build release: `./gradlew assembleRelease`.
- [ ] Open `app/build/reports/permissions/release.txt` → **RESULT: PASS**, zero
      declared permissions, none of the forbidden broad media/storage permissions.
- [ ] (Optional) Inspect the merged manifest
      `app/build/intermediates/merged_manifests/release/AndroidManifest.xml` — no
      `READ_MEDIA_*`, `*_EXTERNAL_STORAGE`, `ACCESS_MEDIA_LOCATION`, `QUERY_ALL_PACKAGES`.

## Recovery paths (each shows a clear message, no crash)

- [ ] **Cancel the picker** → "No folder was chosen…" and the chooser remains usable.
- [ ] **Revoke access**: Settings → Apps → this app → clear the granted URI (or pick a
      folder on removable media and unmount it) → app shows "Access to your folder was
      lost…" with re-pick / use-samples.
- [ ] **Move/rename/delete the folder** out from under the app → "The chosen folder was
      moved, renamed, or removed…".
- [ ] **Pick a blocked location** (e.g. a storage root the provider rejects) → handled
      with a recovery message, not a crash.

## App-private fallback works with no permissions

- [ ] Choose **Use sample photos** on a fresh install → bundled images play with no
      permission prompt.
- [ ] Sample mode survives relaunch.

## Display & controls

- [ ] Photos crossfade smoothly.
- [ ] On a throttled/slow SMB connection, the currently visible photo remains on screen
      until the next photo is fully decoded; no 1–2 second black frame appears.
- [ ] With a 3-second interval and a deliberately slow next-photo download, the new photo
      still remains visible for a full interval after it appears (loading time is not
      subtracted from display time).
- [ ] With portrait collage set to **Automatic**, a landscape photo displays alone;
      portrait photos use two or three columns only with photos from the exact same folder.
- [ ] Throttle SMB while a three-photo collage is prepared: the previous slide remains
      visible until every collage tile is decoded; no partial or black presentation appears.
- [ ] Use a folder containing only one portrait photo: the configured blurred/solid/crop
      fallback is used without borrowing a photo from another folder.
- [ ] Change collage mode, maximum count, fallback, and gap in both Android settings and
      the paired web panel; the next prepared presentation uses the new values.
- [ ] **Fit** shows the whole photo with a colored letterbox; **Fill** crops to fill —
      toggling in settings takes effect.
- [ ] Clock / date / folder overlays appear per settings and at the chosen positions;
      clock format respects 24-hour toggle.
- [ ] **D-pad**: Right/Left change photo, OK pauses/resumes, Up shows info, Down hides,
      Menu (or long-press OK) opens settings, Back leaves settings.
- [ ] Settings controls are reachable and operable by D-pad with a visible focus ring.

## Screen-on

- [ ] While the slideshow is in the foreground, the screen does **not** dim/sleep on
      its normal timeout.

## Stability (12-hour soak)

- [ ] Run the slideshow continuously for **≥ 12 hours** on a large folder.
- [ ] No crash, no ANR, no OutOfMemory.
- [ ] Memory stays flat (only current+next prepared presentations held; collage tiles decode at tile size) — check Android Studio
      Profiler shows no upward leak trend.

## StrictMode (debug)

- [ ] Run the **debug** build, exercise indexing + slideshow + settings.
- [ ] Logcat shows **no** StrictMode disk/network-on-main-thread violations and no
      leaked-closable warnings.

## Automated tests (supporting evidence)

- [ ] `./gradlew testDebugUnitTest` passes (StableId, Glob, least-recent-random,
      settings round-trip).
- [ ] `./gradlew connectedDebugAndroidTest` passes (Room DAO ordering, reconcile,
      unique-conflict, decode-failure suppression).

## Phase 1 acceptance (§22.2) — device procedure

Needs a real SMB share (e.g. Samba on the host; from the emulator use host `10.0.2.2`).

1. **Setup**: choose a SAF folder (or set the SMB share as the primary). Add the NAS in
   Settings → NAS/SMB, Test, then Use this NAS.
2. **Primary plays**: with the NAS reachable, confirm NAS photos display and that the
   status is `PLAYING_PRIMARY` (diagnostics). Confirm no per-slide rescan (index count is
   stable; slides change without a network list each time).
3. **NAS down → fallback**: stop the NAS (or disconnect). Within a backoff cycle the
   slideshow should switch to the bundled samples (`SMB_LOST`, `PLAYING_FALLBACK`) with no
   crash and no permanent blank screen.
4. **NAS up → recovery**: restart the NAS. After a health check the slideshow should
   resume NAS photos (`SMB_RECOVERED`, `PLAYING_PRIMARY`).
5. **Credentials**: confirm the SMB password is not present in any log/diagnostic export
   and that removing/forgetting it forces re-entry.
6. **D-pad**: run all of the above with a remote/keyboard only.
7. **Auto-start**: enable Device → Start on boot; reboot on **two** API levels and confirm
   the slideshow returns or a clear recoverable state shows (check `BOOT_AUTOSTART_*`).
8. **Soak**: 24h run cycling the NAS offline/online ~every 10 min — no crash, no permanent
   blank. Watch heap over a rolling 6h window; growth should stay < 5%.

## Remote source switch and rebuild regression

1. Enter invalid Synology File Station credentials and press **Use this NAS**.
2. Before or after the Synology failure result, enter a valid SMB configuration and
   press **Use this NAS**.
3. Confirm SMB indexing begins without waiting for another Synology retry and that SMB
   photos become the primary slideshow pool.
4. Press **Rebuild photo list** while SMB is active. Confirm the visible indexing count
   starts at zero and increases, and diagnostics show `SCAN_START` for SMB—not Synology.
5. Press **Rebuild photo list** again during that scan. Confirm diagnostics contain a
   coalesced request rather than a second simultaneous SMB enumeration.
