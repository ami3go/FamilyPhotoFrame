#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
settings = (root / 'app/src/main/java/com/example/familyphotoframe/data/settings/AppSettings.kt').read_text()
screen = ''.join(p.read_text() for p in sorted((root / 'app/src/main/java/com/example/familyphotoframe/ui/slideshow').glob('Slideshow*.kt')))
renderer = (root / 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/SlideshowTransitionRenderer.kt').read_text()
model = (root / 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/transition/TransitionModel.kt').read_text()
web_dir = root / 'app/src/main/java/com/example/familyphotoframe/web'
web = ''.join((web_dir / n).read_text() for n in ['SetupPage.kt', 'WebUiAssets.kt', 'WebUiCss.kt', 'WebUiScript.kt'])

required = [
    'CROSSFADE', 'SOFT_DISSOLVE', 'GENTLE_ZOOM_IN', 'GENTLE_ZOOM_OUT',
    'HORIZONTAL_GLIDE', 'VERTICAL_GLIDE',
]
for effect in required:
    assert effect in settings, f'missing persisted effect {effect}'
    assert f'TransitionMode.{effect}' in renderer, f'missing renderer branch {effect}'

for stable in [
    'crossfade', 'soft_dissolve', 'gentle_zoom_in', 'gentle_zoom_out',
    'horizontal_glide', 'vertical_glide',
]:
    assert stable in settings, f'missing stable storage id {stable}'
    assert stable in web, f'missing web option {stable}'

assert 'TransitionModeSerializer' in settings
assert 'TransitionState.Preparing' in screen
assert 'TransitionState.Ready' in screen
assert 'TransitionState.Animating' in screen
assert 'TransitionState.Committed' in screen
assert 'SlideshowTransitionRenderer(' in screen
assert 'AnimatedContent(' not in screen, 'legacy implicit transition path still active'
assert 'TRANSITION_SELECTED' in screen
assert 'TRANSITION_STARTED' in screen
assert 'TRANSITION_COMPLETED' in screen
assert 'TransitionTiming.COLD_START_DURATION_MS' in screen
assert 'onRendered(' in screen
assert screen.index('transitionProgress.animateTo(') < screen.index('onRendered(', screen.index('transitionProgress.animateTo('))
assert 'MIN_BASE_DURATION_MS = 300' in model
assert 'MAX_BASE_DURATION_MS = 2_000' in model
print('transition phases 1-2 source verification passed')
