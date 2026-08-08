#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
vm=(root/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt').read_text()
settings_dir=root/'app/src/main/java/com/example/familyphotoframe/ui/settings'
ui=''.join(p.read_text() for p in sorted(settings_dir.glob('Settings*.kt')))
state=(root/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowUiState.kt').read_text()
checks={
 'merged local upload plan':'SourcePoolPolicy.planFor(primaryPoolIds' in vm,
 'effective favorite collage':'if (_state.value.favoritesOnly)' in vm,
 'playlist watcher':'restartPlaylistScheduleWatcher' in vm,
 'playlist android UI':'internal fun PlaylistSection' in ui,
 'schedule android UI':'internal fun PlaylistScheduleSection' in ui,
 'active playlist state':'activePlaylistId' in state,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('FAIL: '+', '.join(failed))
print('Phase 3 playlist/schedule contract passed')
