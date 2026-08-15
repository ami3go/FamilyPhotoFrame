#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def rd(p): return (R/p).read_text(encoding='utf-8')
def wr(p,s): (R/p).write_text(s,encoding='utf-8')
def rep(s,a,b,label):
    if s.count(a)!=1: raise RuntimeError(f'{label}: {s.count(a)} matches')
    return s.replace(a,b,1)

p='app/src/main/java/com/example/familyphotoframe/ui/settings/SettingsPlaybackSections.kt'; s=rd(p)
s=rep(s,'import com.example.familyphotoframe.data.settings.CollageGap\n','import com.example.familyphotoframe.data.settings.CollageAlignment\nimport com.example.familyphotoframe.data.settings.CollageBackground\nimport com.example.familyphotoframe.data.settings.CollageGap\nimport com.example.familyphotoframe.data.settings.CollageLayoutPreference\nimport com.example.familyphotoframe.data.settings.CollageOrientationFilter\nimport com.example.familyphotoframe.data.settings.CollageScaleMode\n','imports')
needle='''    ToggleRow(
        stringResource(R.string.settings_collage_animate_three),'''
controls='''    Text(stringResource(R.string.settings_collage_orientation))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.orientationFilter == CollageOrientationFilter.ANY, { vm.setCollageOrientationFilter(CollageOrientationFilter.ANY) }, { Text(stringResource(R.string.settings_collage_orientation_any)) })
        FilterChip(state.portraitCollage.orientationFilter == CollageOrientationFilter.PORTRAIT_ONLY, { vm.setCollageOrientationFilter(CollageOrientationFilter.PORTRAIT_ONLY) }, { Text(stringResource(R.string.settings_collage_orientation_vertical)) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.orientationFilter == CollageOrientationFilter.LANDSCAPE_ONLY, { vm.setCollageOrientationFilter(CollageOrientationFilter.LANDSCAPE_ONLY) }, { Text(stringResource(R.string.settings_collage_orientation_horizontal)) })
        FilterChip(state.portraitCollage.orientationFilter == CollageOrientationFilter.SAME_AS_ANCHOR, { vm.setCollageOrientationFilter(CollageOrientationFilter.SAME_AS_ANCHOR) }, { Text(stringResource(R.string.settings_collage_orientation_match)) })
    }
    Text(stringResource(R.string.settings_collage_layout))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.layoutPreference == CollageLayoutPreference.AUTO, { vm.setCollageLayoutPreference(CollageLayoutPreference.AUTO) }, { Text(stringResource(R.string.settings_collage_layout_auto)) })
        FilterChip(state.portraitCollage.layoutPreference == CollageLayoutPreference.COLUMNS, { vm.setCollageLayoutPreference(CollageLayoutPreference.COLUMNS) }, { Text(stringResource(R.string.settings_collage_layout_columns)) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.layoutPreference == CollageLayoutPreference.ROWS, { vm.setCollageLayoutPreference(CollageLayoutPreference.ROWS) }, { Text(stringResource(R.string.settings_collage_layout_rows)) })
        FilterChip(state.portraitCollage.layoutPreference == CollageLayoutPreference.FEATURED, { vm.setCollageLayoutPreference(CollageLayoutPreference.FEATURED) }, { Text(stringResource(R.string.settings_collage_layout_featured)) })
    }
    Text(stringResource(R.string.settings_collage_scaling))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.scaleMode == CollageScaleMode.CROP, { vm.setCollageScaleMode(CollageScaleMode.CROP) }, { Text(stringResource(R.string.settings_collage_scaling_crop)) })
        FilterChip(state.portraitCollage.scaleMode == CollageScaleMode.FIT, { vm.setCollageScaleMode(CollageScaleMode.FIT) }, { Text(stringResource(R.string.settings_collage_scaling_fit)) })
    }
    Text(stringResource(R.string.settings_collage_alignment))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.alignment == CollageAlignment.LEFT, { vm.setCollageAlignment(CollageAlignment.LEFT) }, { Text(stringResource(R.string.settings_collage_left)) })
        FilterChip(state.portraitCollage.alignment == CollageAlignment.CENTER, { vm.setCollageAlignment(CollageAlignment.CENTER) }, { Text(stringResource(R.string.settings_collage_center)) })
        FilterChip(state.portraitCollage.alignment == CollageAlignment.RIGHT, { vm.setCollageAlignment(CollageAlignment.RIGHT) }, { Text(stringResource(R.string.settings_collage_right)) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.alignment == CollageAlignment.TOP, { vm.setCollageAlignment(CollageAlignment.TOP) }, { Text(stringResource(R.string.settings_collage_top)) })
        FilterChip(state.portraitCollage.alignment == CollageAlignment.BOTTOM, { vm.setCollageAlignment(CollageAlignment.BOTTOM) }, { Text(stringResource(R.string.settings_collage_bottom)) })
    }
    Text(stringResource(R.string.settings_collage_background))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.portraitCollage.background == CollageBackground.APP_BACKGROUND, { vm.setCollageBackground(CollageBackground.APP_BACKGROUND) }, { Text(stringResource(R.string.settings_collage_background_frame)) })
        FilterChip(state.portraitCollage.background == CollageBackground.BLACK, { vm.setCollageBackground(CollageBackground.BLACK) }, { Text(stringResource(R.string.settings_collage_background_black)) })
        FilterChip(state.portraitCollage.background == CollageBackground.WHITE, { vm.setCollageBackground(CollageBackground.WHITE) }, { Text(stringResource(R.string.settings_collage_background_white)) })
    }
    Text(stringResource(R.string.settings_collage_corner_radius, state.portraitCollage.cornerRadiusDpClamped))
    Slider(value = state.portraitCollage.cornerRadiusDpClamped.toFloat(), onValueChange = { vm.setCollageCornerRadiusDp(it.roundToInt()) }, valueRange = 0f..32f, steps = 7)

'''
s=rep(s,needle,controls+needle,'controls')
s=rep(s,'''        FilterChip(
            selected = state.portraitCollage.gap == CollageGap.MEDIUM,
            onClick = { vm.setCollageGap(CollageGap.MEDIUM) },
            label = { Text(stringResource(R.string.settings_collage_gap_medium)) },
        )
''','''        FilterChip(
            selected = state.portraitCollage.gap == CollageGap.MEDIUM,
            onClick = { vm.setCollageGap(CollageGap.MEDIUM) },
            label = { Text(stringResource(R.string.settings_collage_gap_medium)) },
        )
        FilterChip(
            selected = state.portraitCollage.gap == CollageGap.LARGE,
            onClick = { vm.setCollageGap(CollageGap.LARGE) },
            label = { Text(stringResource(R.string.settings_collage_gap_large)) },
        )
''','large gap')
wr(p,s)

p='app/src/main/res/values/strings.xml'; s=rd(p)
s=s.replace('<string name="settings_portrait_collage">Portrait photo collage</string>','<string name="settings_portrait_collage">Collage</string>')
s=s.replace('<string name="settings_portrait_collage_hint">When a portrait photo is selected, combine it with portrait photos from the same folder. All images are preloaded before the transition.</string>','<string name="settings_portrait_collage_hint">Combine 2–3 photos into one frame. Choose orientation, layout, fit or crop, alignment, spacing, background, and rounded corners.</string>')
anchor='    <string name="settings_collage_off">Off</string>\n'
extra='''    <string name="settings_collage_orientation">Photo orientation</string>
    <string name="settings_collage_orientation_any">Any</string>
    <string name="settings_collage_orientation_vertical">Vertical only</string>
    <string name="settings_collage_orientation_horizontal">Horizontal only</string>
    <string name="settings_collage_orientation_match">Match selected photo</string>
    <string name="settings_collage_layout">Layout style</string>
    <string name="settings_collage_layout_auto">Auto</string>
    <string name="settings_collage_layout_columns">Columns</string>
    <string name="settings_collage_layout_rows">Rows</string>
    <string name="settings_collage_layout_featured">Featured</string>
    <string name="settings_collage_scaling">Photo scaling</string>
    <string name="settings_collage_scaling_crop">Fill &amp; crop</string>
    <string name="settings_collage_scaling_fit">Fit whole photo</string>
    <string name="settings_collage_alignment">Photo alignment</string>
    <string name="settings_collage_left">Left</string>
    <string name="settings_collage_center">Center</string>
    <string name="settings_collage_right">Right</string>
    <string name="settings_collage_top">Top</string>
    <string name="settings_collage_bottom">Bottom</string>
    <string name="settings_collage_background">Collage background</string>
    <string name="settings_collage_background_frame">Frame color</string>
    <string name="settings_collage_background_black">Black</string>
    <string name="settings_collage_background_white">White</string>
    <string name="settings_collage_corner_radius">Rounded corners: %1$d dp</string>
'''
s=rep(s,anchor,extra+anchor,'strings')
s=rep(s,'    <string name="settings_collage_gap_medium">Medium</string>\n','    <string name="settings_collage_gap_medium">Medium</string>\n    <string name="settings_collage_gap_large">Large</string>\n','large string')
wr(p,s)

p='app/src/main/java/com/example/familyphotoframe/ui/slideshow/SlideshowViewModel.kt'; s=rd(p)
s=rep(s,'import com.example.familyphotoframe.data.settings.CollageGap\n','import com.example.familyphotoframe.data.settings.CollageAlignment\nimport com.example.familyphotoframe.data.settings.CollageBackground\nimport com.example.familyphotoframe.data.settings.CollageGap\nimport com.example.familyphotoframe.data.settings.CollageLayoutPreference\nimport com.example.familyphotoframe.data.settings.CollageOrientationFilter\nimport com.example.familyphotoframe.data.settings.CollageScaleMode\n','vm imports')
needle='''    fun setCollageGap(gap: CollageGap) {
        viewModelScope.launch {
            services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(gap = gap)) }
        }
    }
'''
extra=needle+'''
    fun setCollageOrientationFilter(filter: CollageOrientationFilter) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(orientationFilter = filter)) } }
    fun setCollageLayoutPreference(preference: CollageLayoutPreference) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(layoutPreference = preference)) } }
    fun setCollageScaleMode(mode: CollageScaleMode) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(scaleMode = mode)) } }
    fun setCollageAlignment(alignment: CollageAlignment) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(alignment = alignment)) } }
    fun setCollageBackground(background: CollageBackground) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(background = background)) } }
    fun setCollageCornerRadiusDp(radiusDp: Int) = viewModelScope.launch { services.settings.update { it.copy(portraitCollage = it.portraitCollage.copy(cornerRadiusDp = radiusDp.coerceIn(0, 32))) } }
'''
s=rep(s,needle,extra,'vm setters'); wr(p,s)
print('collage Android settings patch applied')
