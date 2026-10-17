#!/usr/bin/env python3
"""Offline contract checks for FPF-FEAT-WEBUI-001 phases 1-5."""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "app/src/main/java/com/example/familyphotoframe/web"
UI = (WEB / "WebUiAssets.kt").read_text(encoding="utf-8")
CSS_SOURCE = (WEB / "WebUiCss.kt").read_text(encoding="utf-8")
JS_SOURCE = (WEB / "WebUiScript.kt").read_text(encoding="utf-8")
HTML = (WEB / "SetupPage.kt").read_text(encoding="utf-8")
SERVER = (WEB / "WebConfigServer.kt").read_text(encoding="utf-8")
DOWNLOAD_NAMING = (WEB / "DiagnosticsDownloadNaming.kt").read_text(encoding="utf-8")
CONTROLLER = (WEB / "WebServerController.kt").read_text(encoding="utf-8")
PREVIEW = (WEB / "WebPreview.kt").read_text(encoding="utf-8")
SLIDESHOW = "".join(p.read_text(encoding="utf-8") for p in sorted((ROOT / "app/src/main/java/com/example/familyphotoframe/ui/slideshow").glob("Slideshow*.kt")))
PREVIEW_RENDERER = (ROOT / "app/src/main/java/com/example/familyphotoframe/ui/slideshow/WebPreviewRenderer.kt").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

errors: list[str] = []
notes: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)

def extract(source: str, name: str) -> str:
    marker = 'val VALUE: String = """\n'
    start = source.find(marker)
    require(start >= 0, f"missing {name} asset")
    if start < 0:
        return ""
    start += len(marker)
    end = source.find('\n""".trimIndent()', start)
    require(end >= 0, f"unterminated {name} asset")
    return source[start:end] if end >= 0 else ""

css = extract(CSS_SOURCE, "CSS")
js = extract(JS_SOURCE, "JS")

tabs = ["overview", "photos", "playback", "collage", "display", "schedule", "device", "web", "diagnostics", "backup", "about"]
for tab in tabs:
    require(f'id="tab-{tab}"' in HTML, f"missing HTML tab: {tab}")
    require(re.search(rf"['\"]{tab}['\"]", js) is not None, f"missing JS navigation entry: {tab}")

# Device and Web control are separate pages since the 2026-08 settings regrouping.
require("['web','Web control','⚙']" in js, "Web control route missing")
require("['device','Device','▤']" in js, "Device route missing")
require("pageTitle('Web control','Web access, pairing, and remembered-browser security')" in js, "Web control page heading missing")

require('app-${WebUiAssets.REVISION}.js' in HTML, "UI JavaScript must use a revisioned asset URL")
require('app-${WebUiAssets.REVISION}.css' in HTML, "UI CSS must use a revisioned asset URL")
require('val REVISION: String by lazy' in UI and 'WebUiCss.VALUE.hashCode()' in UI, "expected content-derived web asset revision")
require('max-age=31536000, immutable' in SERVER, "revisioned assets must be immutable")
require('no-cache, must-revalidate' in SERVER, "legacy asset URLs must be revalidated")
require("max-width:899px" in css and "max-width:599px" in css, "missing tablet/mobile breakpoints")
require(":focus-visible" in css, "missing visible keyboard focus")
require("prefers-reduced-motion" in css, "missing browser reduced-motion styling")
require("min-height:44px" in css, "touch-target minimum is not enforced")
require(".settings-row>*,.setting-control>*{min-width:0;max-width:100%}" in css, "shared settings controls can overflow their card")
# Hairline groups (2026-08-17) replaced the multi-column card grids with one readable
# column, so cards can no longer be squeezed narrower than their controls.
require(".card-grid{display:block}" in css, "generic cards must use the single-column group layout")
require(".display-layout{display:block}" in css, "Display tab must use the single-column group layout")
require(".display-layout .brightness-period-row{grid-template-columns:1fr" in css, "brightness period labels and controls can collide")
require(".brightness-period-control{display:grid;grid-template-columns:" in css, "brightness period controls lack a bounded sub-grid")
require(".brightness-period-control select{grid-column:1/-1}" in css, "brightness period action does not stack on narrow phones")
require("'<div class=\"display-layout\"><div class=\"stack\">'+fit+overlays+'</div><div class=\"stack\">'+screen+brightness" in js, "Display cards are not arranged in two stable columns")
require("brightness-period-row" in js and "brightness-period-control" in js, "brightness period responsive hooks are missing")

require(".photos-grid{display:block}" in css, "Photos page must use the single-column group layout")
require(".photos-grid .card{width:100%;height:auto" in css, "Photos cards must keep natural height")
require("'<div class=\"photos-grid\"><div id=\"sourceStatusHost\">'+sourceStatusCard()+'</div>'+source+folders+synology+webdav+filters" in js, "Photos cards are not arranged in the reviewed order")
require("photos-source-card" in js and "photos-folders-card" in js and "photos-filters-card" in js and "photos-maintenance-card" in js, "Photos card layout roles are incomplete")
require(".playback-layout{display:block}" in css and ".playback-column{display:block}" in css, "Playback must use the single-column group layout")
require(".playback-column .card{width:100%;height:auto" in css, "Playback cards must keep natural height")
require("left.className='playback-column'" in js and "right.className='playback-column'" in js, "Playback cards are not reflowed into independent columns")
require("playback-timing-card" in js and "playback-order-card" in js and "playback-collage-card" in js and "playback-transitions-card" in js, "Playback card layout roles are incomplete")
require(".playback-layout .interval-control input[type=number]{grid-column:1/-1;grid-row:2" in css, "Playback interval control lacks narrow-screen overflow protection")

for label in ["Discovered folders", "pageTitle('Collage'", "Soft focus fade", "Automatic brightness and night mode", "Maintenance", "Event viewer", "Import configuration"]:
    require(label in js, f"missing grouped setting/feature: {label}")
for source in ["SMB source", "Synology File Station", "WebDAV / Nextcloud"]:
    require(source in js, f"missing source group: {source}")

for endpoint in [
    "/api/v1/status", "/api/v1/settings", "/api/v1/folders",
    "/api/v1/presentation/current", "/api/v1/preview",
    "/api/v1/playback/", "/api/v1/diagnostics/events",
    "/api/v1/backup/export", "/api/v1/backup/validate",
    "/api/v1/backup/import", "/api/v1/maintenance",
]:
    require(endpoint in SERVER or endpoint in js, f"missing endpoint contract: {endpoint}")

require("guarded(token, csrf)" in SERVER, "state-changing API lacks session+CSRF guard")
require("MAX_BODY_CHARS = 256 * 1024" in SERVER, "normal request-body limit missing")
require("MAX_BACKUP_CHARS = 1024 * 1024" in SERVER, "backup body limit missing")
require("Content-Security-Policy" in SERVER and "frame-ancestors 'none'" in SERVER, "CSP/frame protection missing")
require("X-Content-Type-Options" in SERVER and "X-Frame-Options" in SERVER, "security headers missing")
require("credentialRef" not in js and "smbPassword" not in js, "web asset references a secret field")

require("URL.createObjectURL(blob)" in js, "preview is not fetched as an authenticated blob")
require("if(retainedImage&&retainedOverlay)" in js, "status refresh does not preserve the live preview DOM")
require("Get picture" in js and "data-action=\"get-picture\"" in js, "on-demand preview button missing")
require("rawFetch('/api/v1/preview',{method:'POST'})" in js, "preview capture is not an explicit POST")
require("previewLoading" in js, "concurrent browser capture presses are not coalesced")
require("previewTimer" not in js, "automatic preview polling remains")
require("loadPresentation()},2000" not in js, "two-second presentation polling remains")
require("class WebPreviewStore" in PREVIEW, "shared preview store missing")
require("synchronized(lock)" in PREVIEW, "preview store is not synchronized")
require("requestCapture()" in PREVIEW and "completeCapture" in PREVIEW, "one-shot preview request state missing")
require("minOf(960" in PREVIEW_RENDERER and "height > 540" in PREVIEW_RENDERER, "preview dimensions are not bounded")
require("Bitmap.CompressFormat.JPEG" in PREVIEW_RENDERER, "preview is not compressed")
require("result.slide.decodedBytes" in SLIDESHOW, "prepared-slide diagnostics must unwrap PrepareSlideResult.Ready")
require("result.slide.photos.map { it.id }" in SLIDESHOW, "collage diagnostics must unwrap PrepareSlideResult.Ready")
require("WEB_PREVIEW_GENERATED" in CONTROLLER and "WEB_PREVIEW_CACHE_HIT" in CONTROLLER, "preview diagnostics missing")

require("coerceIn(1, 200)" in SERVER, "diagnostics page size is not bounded")
require("state.diagEvents=combined.filter" in js, "browser diagnostic rows are not identity-deduplicated")
for diagnostic_filter in ["diagSeverity", "diagCategory", "diagSession", "diagCode", "diagTrigger", "diagOperation", "diagOrigin", "diagSearch"]:
    require(diagnostic_filter in js, f"diagnostic filter missing: {diagnostic_filter}")
require("cursorExpired" in js and "nextCursor" in CONTROLLER, "stable diagnostics cursor recovery missing")
require("operationTimeline" in js and "operationTimeline" in CONTROLLER, "operation timeline missing")
require("Configuration backup and restore" in js, "backup export/import are not consolidated")
require("Web Control Server" in js and "web-server-card" in js, "Web Control Server card missing")
require("diagnostic-device-grid" in js and "diagnostic-maintenance-card" in js, "diagnostic device-card hierarchy missing")
require("topbar-signout" in HTML and "Sign out and forget this browser" in js, "sign-out actions are not in their requested locations")
require("FamilyPhotoFrame-diagnostics-" in DOWNLOAD_NAMING and "Content-Disposition" in SERVER, "timestamped diagnostics download contract missing")
require("rollback_backup" in CONTROLLER and "rollback-backup" in js, "backup rollback path missing")
require("confirmAction" in js and "factory-reset" in js, "destructive confirmation flow missing")
require("REVISION_CONFLICT" in SERVER and "settingsRevision" in CONTROLLER, "revision conflict handling missing")

require("versionCode = buildNumber" in BUILD, "expected versionCode derived from buildNumber")

size = len(css.encode()) + len(js.encode()) + len(HTML.encode())
notes.append(f"embedded web payload: {size / 1024:.1f} KiB")
require(size < 500 * 1024, "initial HTML/CSS/JS payload exceeds 500 KiB")

node = shutil.which("node")
if node and js:
    temp = Path("/tmp/fpf-web-ui-contract.js")
    temp.write_text(js, encoding="utf-8")
    result = subprocess.run([node, "--check", str(temp)], text=True, capture_output=True)
    require(result.returncode == 0, "JavaScript syntax check failed: " + (result.stderr.strip() or result.stdout.strip()))
    notes.append("JavaScript parsed by Node")
else:
    notes.append("Node unavailable; JavaScript syntax check skipped")

if errors:
    for error in errors:
        print(f"  ERROR: {error}")
    sys.exit(1)
for note in notes:
    print(f"  note: {note}")
print("  all web UI modernisation checks passed")
