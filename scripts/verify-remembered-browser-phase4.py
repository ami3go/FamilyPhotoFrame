#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
server = (ROOT / 'app/src/main/java/com/example/familyphotoframe/web/WebConfigServer.kt').read_text()
web_dir = ROOT / 'app/src/main/java/com/example/familyphotoframe/web'
assets = ''.join((web_dir / n).read_text() for n in ['WebUiAssets.kt', 'WebUiCss.kt', 'WebUiScript.kt'])
setup = (ROOT / 'app/src/main/java/com/example/familyphotoframe/web/SetupPage.kt').read_text()
settings_dir = ROOT / 'app/src/main/java/com/example/familyphotoframe/ui/settings'
settings = ''.join(p.read_text() for p in sorted(settings_dir.glob('Settings*.kt')))
vm = (ROOT / 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt').read_text()
state = (ROOT / 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowUiState.kt').read_text()
controller = (ROOT / 'app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt').read_text()

checks = {
    'server': [
        'requireStepUpForSensitiveActions', 'rotationGraceSeconds',
        '/api/v1/security/remembered-browsers/revoke-others',
        '/api/v1/security/remembered-browsers/revoke-all',
        'security.verifyPin',
    ],
    'assets': [
        'rememberedSecurityCard', 'loadRememberedSecurity', 'saveRememberedPolicy',
        'revokeRememberedBrowser', 'revokeRememberedOthers', 'revokeRememberedAll',
        'requestStepUp', 'data-remember-revoke', 'Maximum browsers',
    ],
    'setup': ['id="stepUpDialog"', 'type="password"', 'id="stepUpPin"'],
    'settings': [
        'RememberedBrowserSettings', 'Allow browsers to be remembered',
        'Keep remembered browsers when generating a new PIN',
        'Revoke all', 'vm.revokeRememberedBrowser',
    ],
    'vm': [
        'refreshRememberedBrowsers', 'setRememberedBrowsersEnabled',
        'setRememberedAllowForever', 'setRememberedDefaultExpiry',
        'revokeAllRememberedBrowsers', 'regenerateWebPin(keepRememberedBrowsers',
    ],
    'state': ['data class RememberedBrowserUi', 'rememberedBrowserRecords'],
    'controller': ['revokeRememberedBrowserSessions', 'revokeAllWebSessions',
                   'regeneratePin(revokeRememberedBrowsers: Boolean = true)'],
}
texts = {'server': server, 'assets': assets, 'setup': setup, 'settings': settings,
         'vm': vm, 'state': state, 'controller': controller}
for name, markers in checks.items():
    for marker in markers:
        assert marker in texts[name], f'{name}: missing {marker}'

script_source = (web_dir / 'WebUiScript.kt').read_text()
start = script_source.index('    val VALUE: String = """') + len('    val VALUE: String = """')
end = script_source.index('\n""".trimIndent()', start)
with tempfile.NamedTemporaryFile('w', suffix='.js', delete=False) as f:
    f.write(script_source[start:end].lstrip('\n'))
    path = f.name
subprocess.run(['node', '--check', path], check=True)
print('Remembered-browser Phase 4 management verification passed')
