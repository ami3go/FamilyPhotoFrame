# FamilyPhotoFrame v50.6.4 — Playback Layout and Web Asset Cache Fix

## Root cause

The v50.6.3 source contained a two-column Playback CSS rule, but the web server served `/assets/app.css` and `/assets/app.js` with a one-hour public cache lifetime. Browsers could therefore show the new application version while continuing to use the previous four-column stylesheet and JavaScript.

## Corrections

- Added versioned web asset URLs: `/assets/app-v5064.css` and `/assets/app-v5064.js`.
- Versioned assets are immutable; legacy unversioned asset routes now require revalidation.
- Replaced the single Playback grid with two explicit rows:
  - Timing + Playback order
  - Portrait collage + Transitions
- Added a one-column breakpoint before either card becomes too narrow.
- Reordered the interval controls to `−5`, slider, `+5`, numeric seconds.
- Kept the numeric seconds field on a separate row on narrow phones.
- Added regression checks for asset revisioning and explicit Playback row structure.

## Version

- versionCode: 12
- versionName: 0.10.6.4-prerelease
