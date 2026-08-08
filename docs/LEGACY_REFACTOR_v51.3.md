# v51.3 legacy-code refactor

Version: `0.11.3-prerelease` (`versionCode 17`)

This release is a behaviour-preserving maintenance refactor. It removes obsolete runtime
transition aliases, splits large UI/web files by responsibility, makes the production web
backend contract strict, isolates Android 5 fullscreen compatibility, modernises offline
verification, and archives historical project evidence.

Notable corrections:

- fixed an invalid playlist-normalisation expression that was exposed by restored Kotlin
  type compilation;
- verification now uses an installed/configured Kotlin compiler before attempting a
  download;
- historical `none` and `slide` transitions are accepted only during deserialisation;
- all routes and existing compatibility aliases remain available;
- `minSdk` remains 21.

See `ARCHITECTURE.md` for current ownership boundaries.
