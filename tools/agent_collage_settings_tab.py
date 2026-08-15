from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError(f'{label}: expected exactly one occurrence, found {text.count(old)}')
    return text.replace(old, new, 1)

# Android settings navigation.
path = 'app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsScreen.kt'
text = read(path)
text = replace_once(text, '    PLAYBACK,\n    DISPLAY,', '    PLAYBACK,\n    COLLAGE,\n    DISPLAY,', 'settings enum')
text = replace_once(text,
    '        SettingsPage.PLAYBACK -> stringResource(R.string.settings_playback)\n        SettingsPage.DISPLAY ->',
    '        SettingsPage.PLAYBACK -> stringResource(R.string.settings_playback)\n        SettingsPage.COLLAGE -> stringResource(R.string.settings_portrait_collage)\n        SettingsPage.DISPLAY ->',
    'settings title')
text = replace_once(text,
    '            SettingsPage.PLAYBACK -> PlaybackSettings(state, vm)\n            SettingsPage.DISPLAY -> DisplaySettings(state, vm)',
    '            SettingsPage.PLAYBACK -> PlaybackSettings(state, vm) { onOpenPage(SettingsPage.COLLAGE) }\n            SettingsPage.COLLAGE -> CollageSettings(state, vm)\n            SettingsPage.DISPLAY -> DisplaySettings(state, vm)',
    'settings content')
text = replace_once(text,
    '    SettingsGroupEntry(stringResource(R.string.settings_playback)) { onOpenPage(SettingsPage.PLAYBACK) }\n    SettingsGroupEntry(stringResource(R.string.settings_display))',
    '    SettingsGroupEntry(stringResource(R.string.settings_playback)) { onOpenPage(SettingsPage.PLAYBACK) }\n    SettingsGroupEntry(stringResource(R.string.settings_portrait_collage)) { onOpenPage(SettingsPage.COLLAGE) }\n    SettingsGroupEntry(stringResource(R.string.settings_display))',
    'settings root group')
write(path, text)

# Playback keeps only a summary + shortcut.
path = 'app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsPlaybackSections.kt'
text = read(path)
text = replace_once(text,
    'internal fun PlaybackSettings(state: SlideshowUiState, vm: SlideshowViewModel) {',
    'internal fun PlaybackSettings(state: SlideshowUiState, vm: SlideshowViewModel, onOpenCollage: () -> Unit) {',
    'playback signature')
start = text.index('    SettingsSectionCard(stringResource(R.string.settings_portrait_collage)) {')
end = text.index('    if (state.configurableKinds.isNotEmpty()) {', start)
summary = '''    SettingsSectionCard(stringResource(R.string.settings_portrait_collage)) {
        val collage = state.portraitCollage
        val modeLabel = when (collage.mode) {
            PortraitCollageMode.OFF -> stringResource(R.string.settings_collage_off)
            PortraitCollageMode.AUTOMATIC -> stringResource(R.string.settings_collage_auto)
            PortraitCollageMode.ALWAYS_TWO -> stringResource(R.string.settings_collage_always_two)
            PortraitCollageMode.PREFER_THREE -> stringResource(R.string.settings_collage_prefer_three)
        }
        val orientationLabel = when (collage.orientationFilter) {
            CollageOrientationFilter.ANY -> stringResource(R.string.settings_collage_orientation_any)
            CollageOrientationFilter.PORTRAIT_ONLY -> stringResource(R.string.settings_collage_orientation_vertical)
            CollageOrientationFilter.LANDSCAPE_ONLY -> stringResource(R.string.settings_collage_orientation_horizontal)
            CollageOrientationFilter.SAME_AS_ANCHOR -> stringResource(R.string.settings_collage_orientation_match)
        }
        val scaleLabel = when (collage.scaleMode) {
            CollageScaleMode.CROP -> stringResource(R.string.settings_collage_scaling_crop)
            CollageScaleMode.FIT -> stringResource(R.string.settings_collage_scaling_fit)
        }
        val alignmentLabel = when (collage.alignment) {
            CollageAlignment.CENTER -> stringResource(R.string.settings_collage_center)
            CollageAlignment.TOP -> stringResource(R.string.settings_collage_top)
            CollageAlignment.BOTTOM -> stringResource(R.string.settings_collage_bottom)
            CollageAlignment.LEFT -> stringResource(R.string.settings_collage_left)
            CollageAlignment.RIGHT -> stringResource(R.string.settings_collage_right)
        }
        Text(
            stringResource(
                R.string.settings_collage_summary_format,
                modeLabel,
                collage.maxPhotosClamped,
                orientationLabel,
                scaleLabel,
                alignmentLabel,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        OutlinedButton(onClick = onOpenCollage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_collage_configure))
        }
    }

'''
text = text[:start] + summary + text[end:]
write(path, text)

# Dedicated Android Collage screen.
write('app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsCollageSections.kt', '''package com.example.familyphotoframe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel
import kotlin.math.roundToInt

@Composable
internal fun CollageSettings(state: SlideshowUiState, vm: SlideshowViewModel) {
    val collage = state.portraitCollage
    var showResetConfirmation by remember { mutableStateOf(false) }

    SettingsSectionCard(stringResource(R.string.settings_collage_selection)) {
        Text(
            stringResource(R.string.settings_portrait_collage_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
        Text(stringResource(R.string.settings_collage_mode))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(collage.mode == PortraitCollageMode.OFF, { vm.setPortraitCollageMode(PortraitCollageMode.OFF) }, { Text(stringResource(R.string.settings_collage_off)) })
                FilterChip(collage.mode == PortraitCollageMode.AUTOMATIC, { vm.setPortraitCollageMode(PortraitCollageMode.AUTOMATIC) }, { Text(stringResource(R.string.settings_collage_auto)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(collage.mode == PortraitCollageMode.ALWAYS_TWO, { vm.setPortraitCollageMode(PortraitCollageMode.ALWAYS_TWO) }, { Text(stringResource(R.string.settings_collage_always_two)) })
                FilterChip(collage.mode == PortraitCollageMode.PREFER_THREE, { vm.setPortraitCollageMode(PortraitCollageMode.PREFER_THREE) }, { Text(stringResource(R.string.settings_collage_prefer_three)) })
            }
        }

        Text(stringResource(R.string.settings_collage_max))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(collage.maxPhotosClamped == 2, { vm.setPortraitCollageMaxPhotos(2) }, { Text(stringResource(R.string.settings_collage_two)) })
            FilterChip(collage.maxPhotosClamped == 3, { vm.setPortraitCollageMaxPhotos(3) }, { Text(stringResource(R.string.settings_collage_three)) })
        }

        Text(stringResource(R.string.settings_collage_orientation))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.orientationFilter == CollageOrientationFilter.ANY, { vm.setCollageOrientationFilter(CollageOrientationFilter.ANY) }, { Text(stringResource(R.string.settings_collage_orientation_any)) })
            FilterChip(collage.orientationFilter == CollageOrientationFilter.PORTRAIT_ONLY, { vm.setCollageOrientationFilter(CollageOrientationFilter.PORTRAIT_ONLY) }, { Text(stringResource(R.string.settings_collage_orientation_vertical)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.orientationFilter == CollageOrientationFilter.LANDSCAPE_ONLY, { vm.setCollageOrientationFilter(CollageOrientationFilter.LANDSCAPE_ONLY) }, { Text(stringResource(R.string.settings_collage_orientation_horizontal)) })
            FilterChip(collage.orientationFilter == CollageOrientationFilter.SAME_AS_ANCHOR, { vm.setCollageOrientationFilter(CollageOrientationFilter.SAME_AS_ANCHOR) }, { Text(stringResource(R.string.settings_collage_orientation_match)) })
        }
    }

    SettingsSectionCard(stringResource(R.string.settings_collage_layout_fitting)) {
        Text(stringResource(R.string.settings_collage_layout))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.layoutPreference == CollageLayoutPreference.AUTO, { vm.setCollageLayoutPreference(CollageLayoutPreference.AUTO) }, { Text(stringResource(R.string.settings_collage_layout_auto)) })
            FilterChip(collage.layoutPreference == CollageLayoutPreference.COLUMNS, { vm.setCollageLayoutPreference(CollageLayoutPreference.COLUMNS) }, { Text(stringResource(R.string.settings_collage_layout_columns)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.layoutPreference == CollageLayoutPreference.ROWS, { vm.setCollageLayoutPreference(CollageLayoutPreference.ROWS) }, { Text(stringResource(R.string.settings_collage_layout_rows)) })
            FilterChip(collage.layoutPreference == CollageLayoutPreference.FEATURED, { vm.setCollageLayoutPreference(CollageLayoutPreference.FEATURED) }, { Text(stringResource(R.string.settings_collage_layout_featured)) })
        }

        Text(stringResource(R.string.settings_collage_scaling))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.scaleMode == CollageScaleMode.CROP, { vm.setCollageScaleMode(CollageScaleMode.CROP) }, { Text(stringResource(R.string.settings_collage_scaling_crop)) })
            FilterChip(collage.scaleMode == CollageScaleMode.FIT, { vm.setCollageScaleMode(CollageScaleMode.FIT) }, { Text(stringResource(R.string.settings_collage_scaling_fit)) })
        }

        Text(stringResource(R.string.settings_collage_alignment))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.alignment == CollageAlignment.LEFT, { vm.setCollageAlignment(CollageAlignment.LEFT) }, { Text(stringResource(R.string.settings_collage_left)) })
            FilterChip(collage.alignment == CollageAlignment.CENTER, { vm.setCollageAlignment(CollageAlignment.CENTER) }, { Text(stringResource(R.string.settings_collage_center)) })
            FilterChip(collage.alignment == CollageAlignment.RIGHT, { vm.setCollageAlignment(CollageAlignment.RIGHT) }, { Text(stringResource(R.string.settings_collage_right)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.alignment == CollageAlignment.TOP, { vm.setCollageAlignment(CollageAlignment.TOP) }, { Text(stringResource(R.string.settings_collage_top)) })
            FilterChip(collage.alignment == CollageAlignment.BOTTOM, { vm.setCollageAlignment(CollageAlignment.BOTTOM) }, { Text(stringResource(R.string.settings_collage_bottom)) })
        }
    }

    SettingsSectionCard(stringResource(R.string.settings_collage_appearance)) {
        Text(stringResource(R.string.settings_collage_background))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(collage.background == CollageBackground.APP_BACKGROUND, { vm.setCollageBackground(CollageBackground.APP_BACKGROUND) }, { Text(stringResource(R.string.settings_collage_background_frame)) })
            FilterChip(collage.background == CollageBackground.BLACK, { vm.setCollageBackground(CollageBackground.BLACK) }, { Text(stringResource(R.string.settings_collage_background_black)) })
            FilterChip(collage.background == CollageBackground.WHITE, { vm.setCollageBackground(CollageBackground.WHITE) }, { Text(stringResource(R.string.settings_collage_background_white)) })
        }

        Text(stringResource(R.string.settings_collage_gap))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(collage.gap == CollageGap.NONE, { vm.setCollageGap(CollageGap.NONE) }, { Text(stringResource(R.string.settings_collage_gap_none)) })
            FilterChip(collage.gap == CollageGap.SMALL, { vm.setCollageGap(CollageGap.SMALL) }, { Text(stringResource(R.string.settings_collage_gap_small)) })
            FilterChip(collage.gap == CollageGap.MEDIUM, { vm.setCollageGap(CollageGap.MEDIUM) }, { Text(stringResource(R.string.settings_collage_gap_medium)) })
            FilterChip(collage.gap == CollageGap.LARGE, { vm.setCollageGap(CollageGap.LARGE) }, { Text(stringResource(R.string.settings_collage_gap_large)) })
        }

        Text(stringResource(R.string.settings_collage_corner_radius, collage.cornerRadiusDpClamped))
        Slider(
            value = collage.cornerRadiusDpClamped.toFloat(),
            onValueChange = { vm.setCollageCornerRadiusDp(it.roundToInt()) },
            valueRange = 0f..32f,
            steps = 7,
        )
    }

    SettingsSectionCard(stringResource(R.string.settings_collage_behavior)) {
        ToggleRow(
            label = stringResource(R.string.settings_collage_animate_three),
            checked = collage.animateThreePhotoFrames,
            onChange = vm::setAnimateThreePhotoFrames,
        )
        Text(
            stringResource(R.string.settings_collage_animate_three_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )

        Text(stringResource(R.string.settings_collage_fallback))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(collage.fallback == PortraitFallback.BLURRED_BACKGROUND, { vm.setPortraitFallback(PortraitFallback.BLURRED_BACKGROUND) }, { Text(stringResource(R.string.settings_collage_fallback_blur)) })
                FilterChip(collage.fallback == PortraitFallback.SOLID_BACKGROUND, { vm.setPortraitFallback(PortraitFallback.SOLID_BACKGROUND) }, { Text(stringResource(R.string.settings_collage_fallback_solid)) })
            }
            FilterChip(collage.fallback == PortraitFallback.CROP_TO_FILL, { vm.setPortraitFallback(PortraitFallback.CROP_TO_FILL) }, { Text(stringResource(R.string.settings_collage_fallback_crop)) })
        }
    }

    OutlinedButton(onClick = { showResetConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_collage_reset))
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.settings_collage_reset)) },
            text = { Text(stringResource(R.string.settings_collage_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    vm.resetPortraitCollageSettings()
                }) { Text(stringResource(R.string.settings_collage_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}
''')

# ViewModel reset-to-default intent.
path = 'app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt'
text = read(path)
text = replace_once(text,
    'import com.example.familyphotoframe.data.settings.PortraitCollageMode\n',
    'import com.example.familyphotoframe.data.settings.PortraitCollageMode\nimport com.example.familyphotoframe.data.settings.PortraitCollageSettings\n',
    'collage settings import')
needle = '    fun setCollageCornerRadiusDp(radiusDp: Int) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(cornerRadiusDp = radiusDp.coerceIn(0, 32))) } }\n'
replacement = needle + '    fun resetPortraitCollageSettings() = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = PortraitCollageSettings()) } }\n'
text = replace_once(text, needle, replacement, 'collage reset intent')
write(path, text)

# Strings: dedicated sections, summary, reset, and generic wording now that landscape collages are supported.
path = 'app/src/main/res/values/strings.xml'
text = read(path)
text = replace_once(text,
    '    <string name="settings_portrait_collage_hint">Combine 2–3 photos into one frame. Choose orientation, layout, fit or crop, alignment, spacing, background, and rounded corners.</string>\n',
    '    <string name="settings_portrait_collage_hint">Combine 2–3 photos into one frame. Choose orientation, layout, fit or crop, alignment, spacing, background, and rounded corners.</string>\n'
    '    <string name="settings_collage_selection">Selection</string>\n'
    '    <string name="settings_collage_layout_fitting">Layout &amp; fitting</string>\n'
    '    <string name="settings_collage_appearance">Appearance</string>\n'
    '    <string name="settings_collage_behavior">Motion &amp; fallback</string>\n'
    '    <string name="settings_collage_mode">Collage mode</string>\n'
    '    <string name="settings_collage_configure">Configure collage</string>\n'
    '    <string name="settings_collage_summary_format">%1$s · max %2$d · %3$s · %4$s · %5$s</string>\n'
    '    <string name="settings_collage_reset">Reset collage settings</string>\n'
    '    <string name="settings_collage_reset_message">Reset every collage option to its default value?</string>\n'
    '    <string name="settings_collage_reset_confirm">Reset</string>\n',
    'collage strings')
text = text.replace('Add slow, subtle movement to each panel when three portrait photos are shown together.', 'Add slow, subtle movement to each panel when three photos are shown together.')
text = text.replace('<string name="settings_collage_fallback">Single portrait fallback</string>', '<string name="settings_collage_fallback">Single-photo fallback</string>')
write(path, text)

# Web shell gets a dedicated tab.
path = 'app/src/main/java/com/example/familyphotoframe/web/SetupPage.kt'
text = read(path)
text = replace_once(text,
    '      <section id="tab-playback" class="tab-page hidden" data-tab="playback"></section>\n      <section id="tab-display"',
    '      <section id="tab-playback" class="tab-page hidden" data-tab="playback"></section>\n      <section id="tab-collage" class="tab-page hidden" data-tab="collage"></section>\n      <section id="tab-display"',
    'web collage shell')
write(path, text)

# Cache-bust web assets.
path = 'app/src/main/java/com/example/familyphotoframe/web/WebUiAssets.kt'
text = read(path)
text = replace_once(text, 'const val REVISION: String = "v52153"', 'const val REVISION: String = "v52154"', 'web revision')
write(path, text)

# Web navigation, dedicated page, Playback summary/shortcut, and reset.
path = 'app/src/main/java/com/example/familyphotoframe/web/WebUiScript.kt'
text = read(path)
text = replace_once(text,
    "var tabs=[['overview','Overview','⌂'],['photos','Photos','▧'],['playback','Playback','▶'],['display','Display','▣']",
    "var tabs=[['overview','Overview','⌂'],['photos','Photos','▧'],['playback','Playback','▶'],['collage','Collage','▦'],['display','Display','▣']",
    'web tab navigation')
text = replace_once(text,
    'function renderAll(){renderOverview();renderPhotos();renderPlayback();renderDisplay();renderSchedule();renderDevice();renderDiagnosticsShell();renderBackup();renderAbout();bindDynamic();}',
    'function renderAll(){renderOverview();renderPhotos();renderPlayback();renderCollage();renderDisplay();renderSchedule();renderDevice();renderDiagnosticsShell();renderBackup();renderAbout();bindDynamic();}',
    'web render all')

collage_function = r'''function collageSummary(c){var mode={OFF:'Off',AUTOMATIC:'Automatic',ALWAYS_TWO:'Always 2',PREFER_THREE:'Prefer 3'}[c.portraitCollageMode]||'Automatic';var orientation={ANY:'Any orientation',PORTRAIT_ONLY:'Vertical only',LANDSCAPE_ONLY:'Horizontal only',SAME_AS_ANCHOR:'Match selected photo'}[c.portraitCollageOrientationFilter]||'Any orientation';var scale=(c.portraitCollageScaleMode||'CROP')==='FIT'?'Fit whole photo':'Fill & crop';var alignment=(c.portraitCollageAlignment||'CENTER').toLowerCase().replace('_',' ');return mode+' · max '+esc(c.portraitCollageMaxPhotos||3)+' · '+orientation+' · '+scale+' · '+alignment;}
function renderCollage(){var c=state.settings||{};var selection=card('Selection','Choose when collages are used and which photos may participate',row('Mode','',selectControl('portraitCollageMode',c.portraitCollageMode,[['OFF','Off'],['AUTOMATIC','Automatic'],['ALWAYS_TWO','Always use two'],['PREFER_THREE','Prefer three']],'portraitCollageMode'),'portraitCollageMode')+row('Maximum photos','',selectControl('portraitCollageMaxPhotos',c.portraitCollageMaxPhotos||3,[[2,'2'],[3,'3']],'portraitCollageMaxPhotos'),'portraitCollageMaxPhotos')+row('Photo orientation','Limit which photos can join a collage',selectControl('portraitCollageOrientationFilter',c.portraitCollageOrientationFilter||'ANY',[['ANY','Any'],['PORTRAIT_ONLY','Vertical only'],['LANDSCAPE_ONLY','Horizontal only'],['SAME_AS_ANCHOR','Match selected photo']],'portraitCollageOrientationFilter'),'portraitCollageOrientationFilter'),'collage-selection-card');var fitting=card('Layout & fitting','Arrange photos and control whether tiles crop or keep the whole image',row('Layout style','Choose equal rows/columns, featured layouts, or automatic',selectControl('portraitCollageLayoutPreference',c.portraitCollageLayoutPreference||'AUTO',[['AUTO','Automatic'],['COLUMNS','Columns'],['ROWS','Rows'],['FEATURED','Featured']],'portraitCollageLayoutPreference'),'portraitCollageLayoutPreference')+row('Photo scaling','Fit keeps the whole image visible; crop fills each tile',selectControl('portraitCollageScaleMode',c.portraitCollageScaleMode||'CROP',[['CROP','Fill & crop'],['FIT','Fit whole photo']],'portraitCollageScaleMode'),'portraitCollageScaleMode')+row('Photo alignment','Crop focus / fitted-image placement',selectControl('portraitCollageAlignment',c.portraitCollageAlignment||'CENTER',[['CENTER','Center'],['TOP','Top'],['BOTTOM','Bottom'],['LEFT','Left'],['RIGHT','Right']],'portraitCollageAlignment'),'portraitCollageAlignment'),'collage-fitting-card');var appearance=card('Appearance','Control framing around and between collage tiles',row('Background','Used for Fit letterboxing and gaps',selectControl('portraitCollageBackground',c.portraitCollageBackground||'APP_BACKGROUND',[['APP_BACKGROUND','Frame color'],['BLACK','Black'],['WHITE','White']],'portraitCollageBackground'),'portraitCollageBackground')+row('Tile gap','',selectControl('portraitCollageGap',c.portraitCollageGap||'SMALL',[['NONE','None'],['SMALL','Small'],['MEDIUM','Medium'],['LARGE','Large']],'portraitCollageGap'),'portraitCollageGap')+row('Rounded corners','0 to 32 dp',inputControl('portraitCollageCornerRadiusDp',c.portraitCollageCornerRadiusDp||0,'number','portraitCollageCornerRadiusDp',0,32,4),'portraitCollageCornerRadiusDp'),'collage-appearance-card');var behavior=card('Motion & fallback','Fine tune three-photo movement and what happens when a collage cannot be formed',row('Animate three-photo frames','Disabled automatically with Fit whole photo',toggleControl('portraitCollageAnimateThree',c.portraitCollageAnimateThree!==false,'portraitCollageAnimateThree'),'portraitCollageAnimateThree')+row('Single-photo fallback','When no compatible collage can be formed',selectControl('portraitCollageFallback',c.portraitCollageFallback,[['BLURRED_BACKGROUND','Blurred background'],['SOLID_BACKGROUND','Solid background'],['CROP_TO_FILL','Crop to fill']],'portraitCollageFallback'),'portraitCollageFallback')+'<div class="button-row"><button class="danger" data-action="reset-collage">Reset collage settings</button></div>','collage-behavior-card');el('tab-collage').innerHTML=pageTitle('Collage','Selection, layout, fitting, appearance, motion, and fallback')+'<div class="card-grid">'+selection+fitting+appearance+behavior+'</div>';}
'''
text = replace_once(text, 'function renderPlayback(){', collage_function + 'function renderPlayback(){', 'web collage renderer')
pattern = re.compile(r"var collage=card\('Collage'.*?,'playback-collage-card'\);var mode=", re.S)
replacement = "var collage=card('Collage','Collage-specific controls have their own page',row('Current setup','', '<span class=\"status-badge\">'+esc(collageSummary(c))+'</span>')+'<div class=\"button-row\"><button class=\"primary\" data-tab-jump=\"collage\">Open collage settings</button></div>','playback-collage-card');var mode="
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError(f'web playback collage summary: expected one replacement, got {count}')
text = text.replace("pageTitle('Playback','Timing, order, portrait collage, transitions, and playlists')", "pageTitle('Playback','Timing, order, transitions, collage summary, and playlists')", 1)
reset_helper = r'''async function resetCollageSettings(){var defaults={portraitCollageMode:'AUTOMATIC',portraitCollageMaxPhotos:3,portraitCollageOrientationFilter:'ANY',portraitCollageLayoutPreference:'AUTO',portraitCollageScaleMode:'CROP',portraitCollageAlignment:'CENTER',portraitCollageBackground:'APP_BACKGROUND',portraitCollageCornerRadiusDp:0,portraitCollageFallback:'BLURRED_BACKGROUND',portraitCollageGap:'SMALL',portraitCollageAnimateThree:true};if(await savePatch(defaults,'portraitCollageMode')){renderAll();switchTab('collage');toast('Collage settings reset')}}
'''
text = replace_once(text, 'function bindStatic(){', reset_helper + 'function bindStatic(){', 'web reset helper')
text = replace_once(text,
    "else if(a==='reset-filters')resetFilters();else if(a==='preview-transition')previewTransition();",
    "else if(a==='reset-filters')resetFilters();else if(a==='reset-collage')confirmAction('Reset collage settings','All collage controls will return to their defaults.',resetCollageSettings);else if(a==='preview-transition')previewTransition();",
    'web reset action')
write(path, text)

# Web UI contract follows the new navigation contract.
path = 'app/src/test/java/com/example/familyphotoframe/web/WebUiContractTest.kt'
text = read(path)
text = replace_once(text,
    'listOf("overview", "photos", "playback", "display", "schedule", "device", "diagnostics", "backup", "about")',
    'listOf("overview", "photos", "playback", "collage", "display", "schedule", "device", "diagnostics", "backup", "about")',
    'web contract tabs')
text = replace_once(text,
    '        assertTrue(js.contains("Fit whole photo"))\n',
    '        assertTrue(js.contains("Fit whole photo"))\n        assertTrue(js.contains("function renderCollage()"))\n        assertTrue(js.contains("data-tab-jump=\\\"collage\\\""))\n        assertTrue(js.contains("reset-collage"))\n        assertTrue(js.contains("Collage-specific controls have their own page"))\n',
    'web contract collage assertions')
write(path, text)

print('Collage settings navigation patch applied.')
