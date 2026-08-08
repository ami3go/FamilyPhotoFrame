# v0.11.0 Manual Acceptance Test

Run after:

```bash
chmod +x gradlew
./gradlew clean testDebugUnitTest installDebug
```

## 1. Touch controls

1. Start the slideshow and wait for a committed single photo.
2. Tap once. Confirm Previous, Favorite, Hide, and Next appear.
3. Wait four seconds. Confirm controls disappear and automatic playback resumes.
4. Tap Favorite and confirm the heart changes without navigating.
5. Tap Hide, confirm the current photo remains until a replacement is prepared, then use Undo.
6. Repeat on a two/three-photo collage and confirm an explicit tile selector appears.
7. Press D-pad Left/Right/Down/Centre and verify navigation/action behaviour.

## 2. Playlists

1. Create a playlist from the current folders and playback settings.
2. Rename, duplicate, disable/enable, reorder, set default, and play it.
3. Verify All photos, Favorites, and Recent uploads cannot be deleted.
4. Verify an empty or unavailable playlist reports the condition without a blank frame.

## 3. Scheduled switching

1. Create two short test rules, including one crossing midnight.
2. Verify the highest-priority matching rule wins.
3. Start another playlist manually and verify the override lasts until the next boundary.
4. Change device time/timezone and confirm reevaluation.

## 4. Brightness and night mode

1. Test Manual and Scheduled modes.
2. Test Dim, Pause slideshow, and Black screen actions.
3. Touch during black-screen mode and verify temporary wake.
4. On a device with a light sensor, test Ambient and Scheduled + ambient without rapid oscillation.
5. Confirm fullscreen system bars still auto-hide after returning from Settings.

## 5. Health dashboard

1. Verify indexed/playable/favorite/hidden/local-upload counts.
2. Reduce free storage or use a test threshold and confirm plain-language warning.
3. Disconnect the active NAS and confirm source warning while the web UI remains available.
4. Export the support report and inspect it for passwords, tokens, and unredacted private URLs.

## 6. Bulk web upload

1. Enable web upload on the frame and pair a browser.
2. Select multiple JPEG/PNG/WebP files from a phone.
3. Repeat with desktop drag-and-drop.
4. Pause the queue. Confirm active files finish and no additional file starts.
5. Resume, cancel another batch, retry a deliberately failed item, and clear the queue.
6. Test Skip, Keep both, and Replace local duplicate policies.
7. Upload a renamed non-image, HEIC file, zero-byte file, and oversized file; confirm safe rejection.
8. Disconnect the browser and restart the app during separate test batches; confirm no `.part` file appears in playback.
9. Confirm completed photos appear in Local uploads and Recent uploads after batch indexing.
10. Disable “Allow while playing” and confirm a batch cannot start until playback is paused.

## 7. Endurance

Run at least 24 hours on the API 23 target frame with:

- 3-second slideshow interval;
- mixed single and collage presentations;
- Ambient Random transitions;
- two concurrent browser upload streams during part of the test;
- incremental indexing and source reconnects.

Pass conditions:

- no OOM, ANR, crash, or black loading frame;
- no incomplete file is indexed;
- memory remains bounded;
- storage reserve remains intact;
- fullscreen recovers after dialogs and focus changes.
