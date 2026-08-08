#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

./gradlew --no-configuration-cache clean testDebugUnitTest lintDebug assembleDebug

if command -v adb >/dev/null 2>&1 && adb devices | awk 'NR>1 && $2 == "device" { found=1 } END { exit !found }'; then
  ./gradlew --no-configuration-cache connectedDebugAndroidTest installDebug
else
  echo "No authorized Android device detected; connectedDebugAndroidTest/installDebug not run." >&2
  echo "Connect an API 21+ device and rerun this script to complete the device gate." >&2
  exit 2
fi
