#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
engine=(root/'app/src/main/java/com/example/familyphotoframe/domain/engine/SlideshowEngine.kt').read_text()
vm=(root/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt').read_text()
screen=''.join(p.read_text() for p in sorted((root/'app/src/main/java/com/example/familyphotoframe/ui/slideshow').glob('Slideshow*.kt')))
state=(root/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowUiState.kt').read_text()
checks=[
('interaction hold', 'setInteractionHold' in engine and 'interactionHold ||' in engine),
('committed presentation state', 'visiblePresentationPhotos' in state and 'onVisiblePresentationChanged' in screen),
('touch overlay', 'TouchNavigationOverlay' in screen and 'onTap =' in screen),
('collage favorite selection', 'FavoriteSelectionDialog' in screen and 'Choose favorites' in screen),
('collage hide selection', 'HideSelectionDialog' in screen and 'Original files are not deleted.' in screen),
('undo exclusion', 'undoLastHide' in vm and 'delay(8_000)' in screen),
('batch curation', 'setFavoriteForPhotos' in vm and 'hidePhotos(ids' in vm),
('bounded controls timeout', 'delay(4_000)' in screen),
]
failed=[name for name,ok in checks if not ok]
if failed: raise SystemExit('phase 2 failures: '+', '.join(failed))
print('phase 2 touch/curation contracts passed')
