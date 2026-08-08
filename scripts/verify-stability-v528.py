#!/usr/bin/env python3
"""Static integration contract for all six v52.8 stability-hardening phases."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


checks: dict[str, bool] = {}

build = read("app/build.gradle.kts")
assets = read("app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt")
services = read("app/src/main/java/com/example/familyphotoframe/ServiceLocator.kt")
preparation = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowPreparation.kt")
screen = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowScreen.kt")
backdrop = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowBackdrop.kt")
app = read("app/src/main/java/com/example/familyphotoframe/App.kt")
sampler = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/RuntimeSampler.kt")
diagnostics = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/DiagnosticsLog.kt")
sink = read("app/src/main/java/com/example/familyphotoframe/data/diagnostics/FileDiagnosticsSink.kt")
web_server = read("app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt")
view_model = read("app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt")
bounded_input = read("app/src/main/java/com/example/familyphotoframe/data/settings/BoundedTextInput.kt")
portable = read("app/src/main/java/com/example/familyphotoframe/data/settings/PortableBundle.kt")
webdav_api = read("app/src/main/java/com/example/familyphotoframe/data/source/WebDavApi.kt")
webdav_source = read("app/src/main/java/com/example/familyphotoframe/data/source/WebDavPhotoSource.kt")
source_contract = read("app/src/main/java/com/example/familyphotoframe/data/source/PhotoSource.kt")
indexer = read("app/src/main/java/com/example/familyphotoframe/data/index/Indexer.kt")
folder_ui = read("app/src/main/java/com/example/familyphotoframe/ui/settings/FolderManagementSettings.kt")
photo_settings = read("app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsPhotoSources.kt")
settings_repo = read("app/src/main/java/com/example/familyphotoframe/data/settings/SettingsRepository.kt")
shuffle = read("app/src/main/java/com/example/familyphotoframe/slideshow/shuffle/ShuffleRepository.kt")

checks.update({
    "current release metadata": "versionCode = 33" in build and "0.12.13-prerelease" in build,
    "web cache revision": 'REVISION: String = "v52150"' in assets,
    "heap-aware Coil cache": "ImageMemoryBudget.bytesForHeap" in services and "maxSizeBytes(cacheBytes)" in services,
    "prepared images bypass cache": "memoryCachePolicy(CachePolicy.DISABLED)" in preparation,
    "blur bypasses cache and catches OOM": "memoryCachePolicy(CachePolicy.DISABLED)" in backdrop and "catch (_: OutOfMemoryError)" in backdrop,
    "ten-second pressure checks": "PRESSURE_CHECK_INTERVAL_MS = 10_000L" in sampler and "clearPreview()" in app,
    "bounded asynchronous diagnostics": "ArrayBlockingQueue" in diagnostics and "AsyncDurableWriter" in diagnostics,
    "streamed diagnostics export": "fun openDurableBundle(" in diagnostics and "openRetainedStream" in sink,
    "chunked diagnostics HTTP response": "newChunkedResponse" in web_server and "diagnosticsBundle(): InputStream" in web_server,
    "four-MiB incremental import": "MAX_IMPORT_BYTES: Int = 4 * 1024 * 1024" in bounded_input and "BoundedTextInput.readUtf8" in view_model,
    "no unbounded ViewModel readBytes": "readBytes()" not in view_model,
    "import work dispatched off UI": "services.dispatchers.io" in view_model and "services.dispatchers.default" in view_model,
    "portable KDF and cipher bounds": "MIN_KDF_ITERATIONS" in portable and "MAX_KDF_ITERATIONS" in portable and "UNSAFE_PARAMETERS" in portable,
    "streamed bounded PROPFIND": "PropfindListing" in webdav_api and "MAX_PROPFIND_BYTES" in webdav_api and "MAX_RESPONSE_BYTES" in webdav_api,
    "WebDAV bodies always scoped": webdav_source.count(".body.use") + webdav_source.count("res.body.use") >= 2,
    "local source classification": "enum class SourceType(val isLocal: Boolean)" in source_contract,
    "remote EXIF skipped": "ExifScanPolicy.LOCAL_ONLY -> sourceType.isLocal" in indexer,
    "lazy searchable folder picker": "LazyColumn" in folder_ui and "items(visibleFolders" in folder_ui and "settings_folders_search" in folder_ui,
    "eager folder section removed": "DiscoveredFoldersSection(" not in photo_settings,
    "atomic folder settings write": "suspend fun setSelectedFolders" in settings_repo and "dataStore.updateData" in settings_repo,
    "deleted shuffle keys forgotten": "restoredScopesLogged.forget(scopeKey)" in shuffle,
    "shuffle tracking bounded": "MAX_RESTORED_SCOPE_LOG_KEYS = 128" in shuffle,
})

failed = [name for name, passed in checks.items() if not passed]
for name, passed in checks.items():
    print(f"  {'PASS' if passed else 'FAIL'}  {name}")
if failed:
    raise SystemExit(f"v52.8 stability contract failed: {', '.join(failed)}")
print(f"v52.8 stability contract: PASS ({len(checks)} checks)")
