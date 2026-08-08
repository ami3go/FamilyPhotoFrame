# FamilyPhotoFrame v50.6.5 — Immersive Mode and Settings Back Button

## Fixed

- Immersive fullscreen is no longer applied only once during Activity creation.
- Android 5/6 legacy immersive-sticky flags and AndroidX system-bar hiding are applied together.
- Fullscreen is restored after resume, focus changes, configuration changes, system document pickers, dialogs, and transient system-bar gestures.
- The launch theme starts fullscreen to avoid an initial status-bar flash.
- The root Settings page now contains a prominent **Back to slideshow** button.
- Subpage Back continues to return to the Settings group list; pressing Back from the root returns to the slideshow.

## Compatibility

- `minSdk` remains API 21 / Android 5.0.
- No privileged permissions were added.
