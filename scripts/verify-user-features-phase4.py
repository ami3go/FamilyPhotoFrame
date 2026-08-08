#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
vm=(r/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt').read_text()
state=(r/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowUiState.kt').read_text()
activity=(r/'app/src/main/java/com/example/familyphotoframe/MainActivity.kt').read_text()
screen=''.join(p.read_text() for p in sorted((r/'app/src/main/java/com/example/familyphotoframe/ui/slideshow').glob('Slideshow*.kt')))
settings_dir=r/'app/src/main/java/com/example/familyphotoframe/ui/settings'
settings=''.join(p.read_text() for p in sorted(settings_dir.glob('Settings*.kt')))
web=(r/'app/src/main/java/com/example/familyphotoframe/web/WebServerController.kt').read_text()
checks={
 'brightness watcher':'restartBrightnessWatcher' in vm,
 'ambient smoothing':'previous * 0.8f' in vm,
 'temporary wake':'fun temporaryWake()' in vm,
 'health aggregation':'private suspend fun refreshHealth()' in vm,
 'black screen state':'val blackScreen' in state,
 'sensor lifecycle':activity.count('override fun onResume()')==1 and 'Sensor.TYPE_LIGHT' in activity,
 'touch wake':'if (state.blackScreen) vm.temporaryWake()' in screen,
 'brightness settings':'internal fun BrightnessAutomationSection' in settings,
 'health settings':'internal fun HealthDashboardSection' in settings,
 'web health':'put("healthHeadline"' in web,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('FAIL: '+', '.join(failed))
print('Phase 4 brightness/health contract passed')
