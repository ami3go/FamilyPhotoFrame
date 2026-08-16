package com.example.familyphotoframe.ui.settings

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

        // Only meaningful with a filter other than "Any"; the policy ignores it otherwise.
        ToggleRow(
            label = stringResource(R.string.settings_collage_orientation_fill),
            checked = collage.fillWithOtherOrientations,
            onChange = vm::setCollageFillWithOtherOrientations,
        )
        Text(
            stringResource(R.string.settings_collage_orientation_fill_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
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
                    Text("Cancel")
                }
            },
        )
    }
}
