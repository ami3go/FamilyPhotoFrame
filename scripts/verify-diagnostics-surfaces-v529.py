#!/usr/bin/env python3
"""Static integration contract for Phase 7 diagnostics surfaces and web cleanup."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/familyphotoframe"
WEB = MAIN / "web"
DIAG = MAIN / "data/diagnostics"

bundle = (DIAG / "DiagnosticsBundle.kt").read_text()
log = (DIAG / "DiagnosticsLog.kt").read_text()
server = (WEB / "WebConfigServer.kt").read_text()
controller = (WEB / "WebServerController.kt").read_text()
script = (WEB / "WebUiScript.kt").read_text()
css = (WEB / "WebUiCss.kt").read_text()
assets = (WEB / "WebUiAssets.kt").read_text()
view_model = (MAIN / "ui/slideshow/SlideshowViewModel.kt").read_text()
activity = (MAIN / "MainActivity.kt").read_text()
analyzer = (ROOT / "scripts/diagnostics_analysis.py").read_text()
general = (ROOT / "scripts/analyze-diagnostics.py").read_text()
transition = (ROOT / "scripts/analyze-transition-diagnostics.py").read_text()

checks = {
    "machine-readable streamed bundle": all(token in bundle for token in ["bundleMetadata", "runtimeSnapshot", "diagnosticsHealth", "streamBoundary", "bundleEnd"]) and "SequenceInputStream" in log,
    "bundle never materializes retained files": "openRetainedStream" in log and "readText()" not in log[log.index("fun openDurableBundle"):log.index("fun flushDurable")],
    "stable identity cursor": "DiagnosticsPager.page" in controller and 'cursor = query(session, "cursor")' in server,
    "all structured filters": all(token in server for token in ["severity =", "category =", "sessionId =", "code =", "trigger =", "operationId =", "origin =", "search ="]),
    "operation timeline and health warnings": "operationTimeline" in controller and "diagnosticWarnings" in controller and "cursorExpired" in controller,
    "correct session summary": 'it.code == "SESSION_START"' in controller and "APP_SESSION_START" not in controller and 'put("crashes"' in controller and 'put("anrs"' in controller,
    "native export streams full bundle": "diagnostics.openDurableBundle(context).use" in view_model and "input.copyTo(output)" in view_model and 'CreateDocument("application/x-ndjson")' in activity,
    "analyzers support mixed schema": "SUPPORTED_SCHEMAS = {1, 2}" in analyzer and "operation_report" in analyzer and "privacy_violations" in analyzer,
    "analyzers emit JSON and Markdown": "write_reports" in general and "markdown_path.write_text" in transition and (ROOT / "scripts/fixtures/diagnostics-v1.jsonl").exists(),
    "About export removed": "function renderAbout" in script and "Memory and brightness recovery v52.15" in script and "<div class=\"button-row\"><button data-action=\"download-log\">Export diagnostics" not in script,
    "Backup combined": "Configuration backup and restore" in script and "backup-combined" in script,
    "diagnostic actions share top toolbar": 'class=\"event-actions\"' in script and all(label in script for label in ["Refresh", "Download JSON", "Load more", "Clear diagnostics"]),
    "timestamped authoritative download": "DiagnosticsDownloadNaming.fileName" in server and "Content-Disposition" in server and "Cache-Control" in server,
    "Web control hierarchy": "diagnostic-device-grid" in script and "web-server-card" in script and "remembered-section" in script,
    "Playback independent stacks": "playback-column" in script and ".playback-column{display:grid" in css and ".playback-layout{grid-template-columns:1fr}" in css,
    "web assets cache busted": 'REVISION: String = "v52150"' in assets,
    "four viewport render gate present": all(size in (ROOT / "scripts/verify-web-ui-v529.cjs").read_text() for size in ["1440,900", "1024,768", "800,1280", "390,844"]),
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise SystemExit("Phase 7 diagnostics surface contract failed: " + ", ".join(failed))
print(f"Phase 7 diagnostics surface contract: PASS ({len(checks)} checks)")
