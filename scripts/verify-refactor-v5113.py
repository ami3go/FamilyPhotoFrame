#!/usr/bin/env python3
"""Behavior-preserving legacy-refactor contract for v51.3."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
read = lambda rel: (root / rel).read_text(encoding="utf-8")
lines = lambda rel: len(read(rel).splitlines())

settings = read("app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt")
web_assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")
web_server = read("app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt")
controller = read("app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt")
main = read("app/src/main/java/com/example/familyphotoframe/MainActivity.kt")
gradle = read("app/build.gradle.kts")

# Runtime transition model contains only real effects; compatibility is deserialization-only.
transition_block = settings[settings.index("enum class TransitionMode"):settings.index("enum class MotionMode")]
assert "NONE(" not in transition_block
assert "SLIDE(" not in transition_block
assert 'normalized.equals("none"' in transition_block
assert 'normalized.equals("slide"' in transition_block
assert ".resolved()" not in "".join(
    p.read_text(encoding="utf-8")
    for p in (root / "app/src/main/java").rglob("*.kt")
)

# Embedded assets are split and the old facade remains stable.
assert lines("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt") <= 20
assert "WebUiCss.VALUE" in web_assets
assert "WebUiScript.VALUE" in web_assets
assert (root / "app/src/main/java/com/example/familyphotoframe/web/WebUiCss.kt").exists()
assert (root / "app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt").exists()

# Web routing and configuration responsibilities are explicit modules.
for marker in (
    "private fun publicRoute(",
    "private fun compatibilityRoute(",
    "private fun rememberedBrowserRoute(",
    "private fun versionedReadRoute(",
    "private fun versionedWriteRoute(",
    "private fun uploadRoute(",
):
    assert marker in web_server, marker
assert "abstract class WebBackendAdapter" in web_server
assert "WebSettingsPatchApplier" in controller
assert "WebSettingsJson" in controller
assert (root / "app/src/main/java/com/example/familyphotoframe/web/WebSettingsPatchApplier.kt").exists()
assert (root / "app/src/main/java/com/example/familyphotoframe/web/WebSettingsJson.kt").exists()

# Settings and slideshow composition are split by responsibility.
assert lines("app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsScreen.kt") <= 400
for name in (
    "SettingsPhotoSources.kt", "SettingsPlaybackSections.kt", "SettingsScheduleSections.kt",
    "SettingsDisplaySections.kt", "SettingsDeviceSections.kt", "SettingsBackupSections.kt",
    "SettingsCommon.kt",
):
    assert (root / "app/src/main/java/com/example/familyphotoframe/ui/settings" / name).exists(), name
assert lines("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt") <= 1200
assert (root / "app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowTouchControls.kt").exists()
assert (root / "app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowPreparation.kt").exists()

# Deprecated Android 5 system-UI calls are isolated behind one compatibility owner.
immersive = read("app/src/main/java/com/example/familyphotoframe/ImmersiveModeController.kt")
assert "ImmersiveModeController(" in main
assert "activity = this" in main
assert "diagnostics = services.diagnostics" in main
assert "SYSTEM_UI_FLAG_IMMERSIVE_STICKY" not in main
assert "SYSTEM_UI_FLAG_IMMERSIVE_STICKY" in immersive
assert "@Suppress(\"DEPRECATION\")" in immersive
assert lines("app/src/main/java/com/example/familyphotoframe/MainActivity.kt") <= 360
assert (root / "app/src/main/java/com/example/familyphotoframe/ActivityDiagnosticsController.kt").exists()

# Historical evidence is retained but no longer clutters the repository root.
archive = root / "docs/archive"
assert (archive / "README.md").exists()
assert len(list(archive.rglob("*.md"))) >= 40
assert len(list(root.glob("*.md"))) <= 22

# Refactor release metadata and cache revision.
assert "versionCode = 33" in gradle
assert 'versionName = "0.12.13-prerelease"' in gradle
assert 'const val REVISION: String = "v52150"' in web_assets

print("v51.3 legacy refactor contract: PASS")
