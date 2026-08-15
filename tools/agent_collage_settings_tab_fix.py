from pathlib import Path

path = Path('app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsCollageSections.kt')
text = path.read_text(encoding='utf-8')
old = 'Text(stringResource(R.string.settings_cancel))'
if text.count(old) != 1:
    raise RuntimeError(f'expected one reset cancel label, found {text.count(old)}')
path.write_text(text.replace(old, 'Text("Cancel")', 1), encoding='utf-8')
print('Collage settings cancel label corrected.')
