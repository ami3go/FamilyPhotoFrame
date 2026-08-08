#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
setup = (ROOT / 'app/src/main/java/com/example/familyphotoframe/web/SetupPage.kt').read_text()
web_dir = ROOT / 'app/src/main/java/com/example/familyphotoframe/web'
assets = ''.join((web_dir / n).read_text() for n in ['WebUiAssets.kt', 'WebUiCss.kt', 'WebUiScript.kt'])

required_setup = [
    'Do not remember — this browser session only',
    'value="ONE_HOUR"', 'value="ONE_DAY"', 'value="ONE_WEEK"',
    'value="ONE_MONTH"', 'value="ONE_YEAR"', 'value="FOREVER"', 'value="CUSTOM"',
    'id="rememberAmount"', 'id="rememberUnit"', 'id="browserLabel"',
    'id="foreverConfirm"', 'id="rememberExpiryPreview"',
    'Use remembered access only on a trusted private network.',
]
required_js = [
    "var REMEMBER_KEY='fpf.rememberedCredential'",
    "var REMEMBER_ID_KEY='fpf.rememberedBrowserId'",
    'BroadcastChannel', "window.addEventListener('storage'",
    'customRememberSeconds', 'Custom expiry must be at least 10 minutes.',
    'Confirm that this browser will remain trusted until it is revoked.',
    'exchangeRememberedCredential', 'rotatedRememberedCredential',
    "cache:'no-store'", 'logoutAndForget', 'clearRemembered',
    'data-action="logout-forget"',
    'REMEMBER_LOCK_KEY', 'releaseExchangeLock',
]
for marker in required_setup:
    assert marker in setup, f'missing setup marker: {marker}'
for marker in required_js:
    assert marker in assets, f'missing JS marker: {marker}'

script_source = (web_dir / 'WebUiScript.kt').read_text()
start = script_source.index('    val VALUE: String = """') + len('    val VALUE: String = """')
end = script_source.index('\n""".trimIndent()', start)
js = script_source[start:end].lstrip('\n')
with tempfile.NamedTemporaryFile('w', suffix='.js', delete=False) as f:
    f.write(js)
    js_path = f.name
subprocess.run(['node', '--check', js_path], check=True)
print('Remembered-browser Phase 3 browser UI verification passed')
