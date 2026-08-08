#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/example/familyphotoframe/MainActivity.kt").read_text()
immersive = (root / "app/src/main/java/com/example/familyphotoframe/ImmersiveModeController.kt").read_text()
settings_dir = root / "app/src/main/java/com/example/familyphotoframe/ui/settings"
settings = "".join(p.read_text() for p in sorted(settings_dir.glob("Settings*.kt")))
theme = (root / "app/src/main/res/values/themes.xml").read_text()
gradle = (root / "app/build.gradle.kts").read_text()

required_main = [
    "SYSTEM_UI_FLAG_IMMERSIVE_STICKY",
    "SYSTEM_UI_FLAG_FULLSCREEN",
    "SYSTEM_UI_FLAG_HIDE_NAVIGATION",
    "FLAG_FULLSCREEN",
    "override fun onResume()",
    "override fun onPostResume()",
    "override fun onWindowFocusChanged",
    "override fun onConfigurationChanged",
    "setOnSystemUiVisibilityChangeListener",
    "onBackToSlideshow = ::returnToSlideshow",
]
for token in required_main:
    owner = immersive if token in {"SYSTEM_UI_FLAG_IMMERSIVE_STICKY", "SYSTEM_UI_FLAG_FULLSCREEN", "SYSTEM_UI_FLAG_HIDE_NAVIGATION", "FLAG_FULLSCREEN", "setOnSystemUiVisibilityChangeListener"} else main
    assert token in owner, token
assert "ImmersiveModeController(" in main
assert "immersiveMode.recover(" in main
assert "onBackToSlideshow: () -> Unit" in settings
assert "R.string.settings_back" in settings
assert "android:windowFullscreen" in theme
assert "versionCode = 33" in gradle
assert 'versionName = "0.12.13-prerelease"' in gradle
print("Fullscreen recovery and Settings back-button contract: PASS")
