package com.example.familyphotoframe.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.db.FolderSummary
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.FilterSettings
import com.example.familyphotoframe.data.settings.MotionMode
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.data.settings.OverlayPosition
import com.example.familyphotoframe.data.settings.PlaybackInterval
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import com.example.familyphotoframe.data.settings.UnreachablePolicy
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.data.settings.UploadDuplicatePolicy
import com.example.familyphotoframe.data.weather.TemperatureUnits
import com.example.familyphotoframe.domain.schedule.RescanSchedule
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel
import com.example.familyphotoframe.web.QrCodes
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private fun formatShuffleTime(epochMs: Long?, never: String): String =
    epochMs?.takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: never

private fun formatShuffleDuration(ageMs: Long): String {
    val seconds = (ageMs.coerceAtLeast(0L) / 1_000L)
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3_600L -> "${seconds / 60L}m ${seconds % 60L}s"
        else -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m"
    }
}

private enum class ShuffleResetAction { ACTIVE, ALL, ALL_WITH_HISTORY }

/** Refactored settings sections: TransitionChip, PlaybackSettings, PlaylistSection. */
@Composable
internal fun TransitionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
@Composable
internal fun PlaybackSettings(state: SlideshowUiState, vm: SlideshowViewModel, onOpenCollage: () -> Unit) {
    var pendingShuffleReset by remember { mutableStateOf<ShuffleResetAction?>(null) }
    PlaylistSection(state, vm)
    SettingsSectionCard("Timing & transitions") {
    Text(
        stringResource(R.string.settings_interval),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    IntervalStepper(
        seconds = state.intervalSecondsForUi,
        onChange = vm::setIntervalSeconds,
    )

    Text(
        stringResource(R.string.settings_aspect),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = state.aspectMode == AspectMode.FIT_COLOR,
            onClick = { vm.setAspectMode(AspectMode.FIT_COLOR) },
            label = { Text(stringResource(R.string.settings_aspect_fit)) },
        )
        FilterChip(
            selected = state.aspectMode == AspectMode.FILL_CROP,
            onClick = { vm.setAspectMode(AspectMode.FILL_CROP) },
            label = { Text(stringResource(R.string.settings_aspect_fill)) },
        )
        FilterChip(
            selected = state.aspectMode == AspectMode.FIT_BLUR,
            onClick = { vm.setAspectMode(AspectMode.FIT_BLUR) },
            label = { Text(stringResource(R.string.settings_aspect_blur)) },
        )
    }

    Text(
        stringResource(R.string.settings_transition_mode),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TransitionChip(
            selected = state.transitionSelectionMode == TransitionSelectionMode.FIXED,
            label = stringResource(R.string.settings_transition_mode_fixed),
        ) { vm.setTransitionSelectionMode(TransitionSelectionMode.FIXED) }
        TransitionChip(
            selected = state.transitionSelectionMode == TransitionSelectionMode.AMBIENT_RANDOM,
            label = stringResource(R.string.settings_transition_mode_ambient),
        ) { vm.setTransitionSelectionMode(TransitionSelectionMode.AMBIENT_RANDOM) }
    }

    if (state.transitionSelectionMode == TransitionSelectionMode.FIXED) {
        Text(
            stringResource(R.string.settings_transition),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransitionChip(
                    selected = state.transition == TransitionMode.CROSSFADE,
                    label = stringResource(R.string.settings_transition_crossfade),
                ) { vm.setTransition(TransitionMode.CROSSFADE) }
                TransitionChip(
                    selected = state.transition == TransitionMode.SOFT_DISSOLVE,
                    label = stringResource(R.string.settings_transition_soft_dissolve),
                ) { vm.setTransition(TransitionMode.SOFT_DISSOLVE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransitionChip(
                    selected = state.transition == TransitionMode.GENTLE_ZOOM_IN,
                    label = stringResource(R.string.settings_transition_zoom_in),
                ) { vm.setTransition(TransitionMode.GENTLE_ZOOM_IN) }
                TransitionChip(
                    selected = state.transition == TransitionMode.GENTLE_ZOOM_OUT,
                    label = stringResource(R.string.settings_transition_zoom_out),
                ) { vm.setTransition(TransitionMode.GENTLE_ZOOM_OUT) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransitionChip(
                    selected = state.transition == TransitionMode.HORIZONTAL_GLIDE,
                    label = stringResource(R.string.settings_transition_horizontal_glide),
                ) { vm.setTransition(TransitionMode.HORIZONTAL_GLIDE) }
                TransitionChip(
                    selected = state.transition == TransitionMode.VERTICAL_GLIDE,
                    label = stringResource(R.string.settings_transition_vertical_glide),
                ) { vm.setTransition(TransitionMode.VERTICAL_GLIDE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransitionChip(
                    selected = state.transition == TransitionMode.DEPTH_FADE,
                    label = stringResource(R.string.settings_transition_depth_fade),
                ) { vm.setTransition(TransitionMode.DEPTH_FADE) }
                TransitionChip(
                    selected = state.transition == TransitionMode.KEN_BURNS_HANDOFF,
                    label = stringResource(R.string.settings_transition_ken_burns_handoff),
                ) { vm.setTransition(TransitionMode.KEN_BURNS_HANDOFF) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransitionChip(
                    selected = state.transition == TransitionMode.SOFT_REVEAL,
                    label = stringResource(R.string.settings_transition_soft_reveal),
                ) { vm.setTransition(TransitionMode.SOFT_REVEAL) }
                TransitionChip(
                    selected = state.transition == TransitionMode.SOFT_FOCUS_FADE,
                    label = stringResource(R.string.settings_transition_soft_focus),
                ) { vm.setTransition(TransitionMode.SOFT_FOCUS_FADE) }
            }
        }
    } else {
        Text(
            stringResource(R.string.settings_transition_ambient_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
    }

    TransitionDurationControl(
        durationMs = state.transitionDurationMs,
        onChange = vm::setTransitionDurationMs,
    )
    ToggleRow(
        stringResource(R.string.settings_transition_reduce_motion),
        state.transitionReduceMotion,
        vm::setTransitionReduceMotion,
    )
    Text(
        stringResource(R.string.settings_transition_reduce_motion_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )

    ToggleRow(
        stringResource(R.string.settings_motion_kenburns),
        state.motion == MotionMode.KEN_BURNS,
    ) { enabled -> vm.setMotion(if (enabled) MotionMode.KEN_BURNS else MotionMode.NONE) }
    Text(
        stringResource(R.string.settings_motion_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    }

    SettingsSectionCard(stringResource(R.string.settings_portrait_collage)) {
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

    if (state.configurableKinds.isNotEmpty()) {
        SettingsSectionCard(stringResource(R.string.settings_also_play)) {
        Text(
            stringResource(R.string.settings_also_play_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
        for (kind in state.configurableKinds) {
            ToggleRow(
                stringResource(
                    when (kind) {
                        ActiveSourceKind.LOCAL_SAF -> R.string.source_kind_local
                        ActiveSourceKind.SMB -> R.string.source_kind_smb
                        ActiveSourceKind.SYNOLOGY -> R.string.source_kind_synology
                        else -> R.string.source_kind_webdav
                    },
                ),
                kind in state.alsoPlay,
            ) { enabled -> vm.setAlsoPlay(kind, enabled) }
        }
        }
    }

    SettingsSectionCard(stringResource(R.string.settings_selection_mode)) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.selectionMode == SelectionMode.SEQUENTIAL,
                onClick = { vm.setSelectionMode(SelectionMode.SEQUENTIAL) },
                label = { Text(stringResource(R.string.selection_sequential)) },
            )
            FilterChip(
                selected = state.selectionMode == SelectionMode.SHUFFLE_NO_REPEAT,
                onClick = { vm.setSelectionMode(SelectionMode.SHUFFLE_NO_REPEAT) },
                label = { Text(stringResource(R.string.selection_shuffle_no_repeat)) },
            )
            FilterChip(
                selected = state.selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE,
                onClick = { vm.setSelectionMode(SelectionMode.FOLDER_BALANCED_SHUFFLE) },
                label = { Text(stringResource(R.string.selection_folder_balanced)) },
            )
        }
        if (state.selectionMode == SelectionMode.FOLDER_BALANCED_SHUFFLE) {
            val progress = state.engine.shuffleProgress
            Text(
                stringResource(R.string.selection_folder_balanced_description),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Text(
                stringResource(
                    R.string.shuffle_progress_summary,
                    progress.folderResolved,
                    progress.folderTotal,
                    progress.photoResolved,
                    progress.photoTotal,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                fontSize = 14.sp,
            )
            Text(
                stringResource(
                    R.string.shuffle_folder_counts,
                    progress.eligibleFolderCount,
                    progress.foldersPresented,
                    progress.foldersPending,
                    progress.foldersSkipped,
                    progress.foldersRemoved,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            Text(
                stringResource(
                    R.string.shuffle_photo_counts,
                    progress.pendingPhotos,
                    progress.quarantinedPhotos,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            Text(
                if (progress.unavailableSourceCount == 0) {
                    stringResource(R.string.shuffle_source_available)
                } else {
                    stringResource(R.string.shuffle_source_unavailable, progress.unavailableSourceCount)
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            progress.activeReservationAgeMs?.let { age ->
                Text(
                    stringResource(R.string.shuffle_reservation_age, formatShuffleDuration(age)),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            val never = stringResource(R.string.shuffle_never)
            Text(
                stringResource(R.string.shuffle_last_commit, formatShuffleTime(progress.lastCommitEpochMs, never)),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            Text(
                stringResource(
                    R.string.shuffle_last_reconciliation,
                    formatShuffleTime(progress.lastReconciliationEpochMs, never),
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            Text(
                stringResource(R.string.shuffle_last_recovery, formatShuffleTime(progress.lastRecoveryEpochMs, never)),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            if (progress.unavailableSourceCount > 0) {
                Text(stringResource(R.string.shuffle_recommend_source), color = Color(0xffffcc80), fontSize = 13.sp)
            }
            if (progress.quarantinedPhotos > 0) {
                Text(stringResource(R.string.shuffle_recommend_quarantine), color = Color(0xffffcc80), fontSize = 13.sp)
            }
            if (progress.lastRecoveryEpochMs != null) {
                Text(stringResource(R.string.shuffle_recommend_recovery), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 13.sp)
            }
            progress.currentFolderKey?.substringAfter('\u001f')?.let { folder ->
                Text(
                    stringResource(R.string.shuffle_current_folder, folder),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
            if (progress.foldersSkipped > 0 || progress.quarantinedPhotos > 0) {
                Text(
                    stringResource(
                        R.string.shuffle_health_summary,
                        progress.foldersSkipped,
                        progress.quarantinedPhotos,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DestructiveButton(
                    text = stringResource(R.string.shuffle_reset_active),
                    onClick = { pendingShuffleReset = ShuffleResetAction.ACTIVE },
                )
                DestructiveButton(
                    text = stringResource(R.string.shuffle_reset_all),
                    onClick = { pendingShuffleReset = ShuffleResetAction.ALL },
                )
            }
            TextButton(
                onClick = { pendingShuffleReset = ShuffleResetAction.ALL_WITH_HISTORY },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.shuffle_reset_all_history))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.selectionMode == SelectionMode.DATE_TAKEN_NEWEST,
                onClick = { vm.setSelectionMode(SelectionMode.DATE_TAKEN_NEWEST) },
                label = { Text(stringResource(R.string.selection_date_newest)) },
            )
            FilterChip(
                selected = state.selectionMode == SelectionMode.DATE_TAKEN_OLDEST,
                onClick = { vm.setSelectionMode(SelectionMode.DATE_TAKEN_OLDEST) },
                label = { Text(stringResource(R.string.selection_date_oldest)) },
            )
        }
    }
    }

    pendingShuffleReset?.let { action ->
        val clearHistory = action == ShuffleResetAction.ALL_WITH_HISTORY
        AlertDialog(
            onDismissRequest = { pendingShuffleReset = null },
            title = { Text(stringResource(R.string.shuffle_reset_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (clearHistory) R.string.shuffle_reset_confirm_history
                        else R.string.shuffle_reset_confirm,
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (action) {
                            ShuffleResetAction.ACTIVE -> vm.resetActiveShuffleProgress()
                            ShuffleResetAction.ALL -> vm.resetAllShuffleProgress(clearHistory = false)
                            ShuffleResetAction.ALL_WITH_HISTORY -> vm.resetAllShuffleProgress(clearHistory = true)
                        }
                        pendingShuffleReset = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingShuffleReset = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SettingsSectionCard(stringResource(R.string.settings_curation)) {
    ToggleRow(
        stringResource(R.string.settings_favorites_only),
        state.favoritesOnly,
        vm::setFavoritesOnly,
    )
    OutlinedButton(onClick = vm::unhideAllPhotos) {
        Text(stringResource(R.string.action_unhide_all))
    }
    Text(
        stringResource(R.string.settings_curation_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    }

    SettingsSectionCard(stringResource(R.string.settings_on_unreachable)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = state.onUnreachable == UnreachablePolicy.FALLBACK_SAMPLES,
            onClick = { vm.setOnUnreachable(UnreachablePolicy.FALLBACK_SAMPLES) },
            label = { Text(stringResource(R.string.unreachable_samples)) },
        )
        FilterChip(
            selected = state.onUnreachable == UnreachablePolicy.STALE_CACHE,
            onClick = { vm.setOnUnreachable(UnreachablePolicy.STALE_CACHE) },
            label = { Text(stringResource(R.string.unreachable_stale_cache)) },
        )
    }
    }
}

/**
 * Folders discovered by the indexer are configured with the photo sources and scan
 * filters. An empty stored selection means "all folders"; unchecking one folder
 * materializes the remaining folders as the explicit selection.
 */
@Composable
internal fun PlaylistSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    SettingsSectionCard("Playlists") {
    Text(
        "Active: ${state.activePlaylistName}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    state.playlists.filter { it.enabled }.forEach { playlist ->
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = playlist.id == state.activePlaylistId,
                onClick = { vm.playPlaylist(playlist.id) },
                label = { Text(playlist.name) },
                modifier = Modifier.weight(1f),
            )
            if (playlist.id !in com.example.familyphotoframe.data.settings.PlaylistSettings.BUILT_IN_IDS) {
                TextButton(
                    onClick = { vm.deletePlaylist(playlist.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            }
        }
    }
    var playlistName by remember { mutableStateOf("") }
    OutlinedTextField(
        value = playlistName,
        onValueChange = { playlistName = it.take(80) },
        label = { Text("New playlist name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                vm.createPlaylist(playlistName)
                playlistName = ""
            },
            enabled = playlistName.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) { Text("Create from current selection") }
        OutlinedButton(
            onClick = {
                vm.createPlaylist(playlistName.ifBlank { "Local uploads" }, localUploadsOnly = true)
                playlistName = ""
            },
            modifier = Modifier.weight(1f),
        ) { Text("Local uploads") }
    }
    }
}
