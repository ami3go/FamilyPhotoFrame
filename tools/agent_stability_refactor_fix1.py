from pathlib import Path

path = Path('app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsPlaybackSections.kt')
text = path.read_text(encoding='utf-8')
old = '''            FilterChip(
                selected = state.selectionMode == SelectionMode.LEAST_RECENT_RANDOM,
                onClick = { vm.setSelectionMode(SelectionMode.LEAST_RECENT_RANDOM) },
                label = { Text(stringResource(R.string.selection_least_recent)) },
            )
'''
if text.count(old) != 1:
    raise RuntimeError(f'expected one legacy selection chip, found {text.count(old)}')
path.write_text(text.replace(old, '', 1), encoding='utf-8')
print('Retired selection mode removed from Android settings UI.')
