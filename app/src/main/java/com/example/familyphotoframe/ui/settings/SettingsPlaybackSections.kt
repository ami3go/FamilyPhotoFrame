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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.example.familyphotoframe.data.settings.CollageGap
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
internal fun PlaybackSettings(state: SlideshowUiState, vm: SlideshowViewModel) {
    var pendingShuffleReset by remember { mutableStateOf<ShuffleResetAction?>(null) }
    PlaylistSection(state, vm)
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
    Text(
        stringResource(R.string.settings_interval),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    IntervalStepper(
        seconds = state.intervalSecondsForUi,
        onChange = vm::setIntervalSeconds,
    )

    Text(
        stringResource(R.string.settings_aspect),
        color = Color.White.copy(alpha = 0.75f),
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
        color = Color.White.copy(alpha = 0.75f),
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
            color = Color.White.copy(alpha = 0.75f),
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
            color = Color.White.copy(alpha = 0.6f),
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
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )

    ToggleRow(
        stringResource(R.string.settings_motion_kenburns),
        state.motion == MotionMode.KEN_BURNS,
    ) { enabled -> vm.setMotion(if (enabled) MotionMode.KEN_BURNS else MotionMode.NONE) }
    Text(
        stringResource(R.string.settings_motion_hint),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )

    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    SectionLabel(stringResource(R.string.settings_portrait_collage))
    Text(
        stringResource(R.string.settings_portrait_collage_hint),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.portraitCollage.mode == PortraitCollageMode.OFF,
                onClick = { vm.setPortraitCollageMode(PortraitCollageMode.OFF) },
                label = { Text(stringResource(R.string.settings_collage_off)) },
            )
            FilterChip(
                selected = state.portraitCollage.mode == PortraitCollageMode.AUTOMATIC,
                onClick = { vm.setPortraitCollageMode(PortraitCollageMode.AUTOMATIC) },
                label = { Text(stringResource(R.string.settings_collage_auto)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.portraitCollage.mode == PortraitCollageMode.ALWAYS_TWO,
                onClick = { vm.setPortraitCollageMode(PortraitCollageMode.ALWAYS_TWO) },
                label = { Text(stringResource(R.string.settings_collage_always_two)) },
            )
            FilterChip(
                selected = state.portraitCollage.mode == PortraitCollageMode.PREFER_THREE,
                onClick = { vm.setPortraitCollageMode(PortraitCollageMode.PREFER_THREE) },
                label = { Text(stringResource(R.string.settings_collage_prefer_three)) },
            )
        }
    }

    Text(
        stringResource(R.string.settings_collage_max),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = state.portraitCollage.maxPhotosClamped == 2,
            onClick = { vm.setPortraitCollageMaxPhotos(2) },
            label = { Text(stringResource(R.string.settings_collage_two)) },
        )
        FilterChip(
            selected = state.portraitCollage.maxPhotosClamped == 3,
            onClick = { vm.setPortraitCollageMaxPhotos(3) },
            label = { Text(stringResource(R.string.settings_collage_three)) },
        )
    }

    ToggleRow(
        label = stringResource(R.string.settings_collage_animate_three),
        checked = state.portraitCollage.animateThreePhotoFrames,
        onChange = vm::setAnimateThreePhotoFrames,
    )
    Text(
        stringResource(R.string.settings_collage_animate_three_hint),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )

    Text(
        stringResource(R.string.settings_collage_fallback),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.portraitCollage.fallback == PortraitFallback.BLURRED_BACKGROUND,
                onClick = { vm.setPortraitFallback(PortraitFallback.BLURRED_BACKGROUND) },
                label = { Text(stringResource(R.string.settings_collage_fallback_blur)) },
            )
            FilterChip(
                selected = state.portraitCollage.fallback == PortraitFallback.SOLID_BACKGROUND,
                onClick = { vm.setPortraitFallback(PortraitFallback.SOLID_BACKGROUND) },
                label = { Text(stringResource(R.string.settings_collage_fallback_solid)) },
            )
        }
        FilterChip(
            selected = state.portraitCollage.fallback == PortraitFallback.CROP_TO_FILL,
            onClick = { vm.setPortraitFallback(PortraitFallback.CROP_TO_FILL) },
            label = { Text(stringResource(R.string.settings_collage_fallback_crop)) },
        )
    }

    Text(
        stringResource(R.string.settings_collage_gap),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = state.portraitCollage.gap == CollageGap.NONE,
            onClick = { vm.setCollageGap(CollageGap.NONE) },
            label = { Text(stringResource(R.string.settings_collage_gap_none)) },
        )
        FilterChip(
            selected = state.portraitCollage.gap == CollageGap.SMALL,
            onClick = { vm.setCollageGap(CollageGap.SMALL) },
            label = { Text(stringResource(R.string.settings_collage_gap_small)) },
        )
        FilterChip(
            selected = state.portraitCollage.gap == CollageGap.MEDIUM,
            onClick = { vm.setCollageGap(CollageGap.MEDIUM) },
            label = { Text(stringResource(R.string.settings_collage_gap_medium)) },
        )
    }

    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    if (state.configurableKinds.isNotEmpty()) {
        SectionLabel(stringResource(R.string.settings_also_play))
        Text(
            stringResource(R.string.settings_also_play_hint),
            color = Color.White.copy(alpha = 0.6f),
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

    SectionLabel(stringResource(R.string.settings_selection_mode))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = state.selectionMode == SelectionMode.SEQUENTIAL,
                onClick = { vm.setSelectionMode(SelectionMode.SEQUENTIAL) },
                label = { Text(stringResource(R.string.selection_sequential)) },
            )
            FilterChip(
                selected = state.selectionMode == SelectionMode.LEAST_RECENT_RANDOM,
                onClick = { vm.setSelectionMode(SelectionMode.LEAST_RECENT_RANDOM) },
                label = { Text(stringResource(R.string.selection_least_recent)) },
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
                color = Color.White.copy(alpha = 0.6f),
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
                color = Color.White.copy(alpha = 0.75f),
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
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            Text(
                stringResource(
                    R.string.shuffle_photo_counts,
                    progress.pendingPhotos,
                    progress.quarantinedPhotos,
                ),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            Text(
                if (progress.unavailableSourceCount == 0) {
                    stringResource(R.string.shuffle_source_available)
                } else {
                    stringResource(R.string.shuffle_source_unavailable, progress.unavailableSourceCount)
                },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            progress.activeReservationAgeMs?.let { age ->
                Text(
                    stringResource(R.string.shuffle_reservation_age, formatShuffleDuration(age)),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            val never = stringResource(R.string.shuffle_never)
            Text(
                stringResource(R.string.shuffle_last_commit, formatShuffleTime(progress.lastCommitEpochMs, never)),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            Text(
                stringResource(
                    R.string.shuffle_last_reconciliation,
                    formatShuffleTime(progress.lastReconciliationEpochMs, never),
                ),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            Text(
                stringResource(R.string.shuffle_last_recovery, formatShuffleTime(progress.lastRecoveryEpochMs, never)),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            if (progress.unavailableSourceCount > 0) {
                Text(stringResource(R.string.shuffle_recommend_source), color = Color(0xffffcc80), fontSize = 13.sp)
            }
            if (progress.quarantinedPhotos > 0) {
                Text(stringResource(R.string.shuffle_recommend_quarantine), color = Color(0xffffcc80), fontSize = 13.sp)
            }
            if (progress.lastRecoveryEpochMs != null) {
                Text(stringResource(R.string.shuffle_recommend_recovery), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            }
            progress.currentFolderKey?.substringAfter('\u001f')?.let { folder ->
                Text(
                    stringResource(R.string.shuffle_current_folder, folder),
                    color = Color.White.copy(alpha = 0.6f),
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
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pendingShuffleReset = ShuffleResetAction.ACTIVE }) {
                    Text(stringResource(R.string.shuffle_reset_active))
                }
                OutlinedButton(onClick = { pendingShuffleReset = ShuffleResetAction.ALL }) {
                    Text(stringResource(R.string.shuffle_reset_all))
                }
            }
            TextButton(onClick = { pendingShuffleReset = ShuffleResetAction.ALL_WITH_HISTORY }) {
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
                Button(onClick = {
                    when (action) {
                        ShuffleResetAction.ACTIVE -> vm.resetActiveShuffleProgress()
                        ShuffleResetAction.ALL -> vm.resetAllShuffleProgress(clearHistory = false)
                        ShuffleResetAction.ALL_WITH_HISTORY -> vm.resetAllShuffleProgress(clearHistory = true)
                    }
                    pendingShuffleReset = null
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingShuffleReset = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    SectionLabel(stringResource(R.string.settings_curation))
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
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )

    SectionLabel(stringResource(R.string.settings_on_unreachable))
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

/**
 * Folders discovered by the indexer are configured with the photo sources and scan
 * filters. An empty stored selection means "all folders"; unchecking one folder
 * materializes the remaining folders as the explicit selection.
 */
@Composable
internal fun PlaylistSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    SectionLabel("Playlists")
    Text(
        "Active: ${state.activePlaylistName}",
        color = Color.White.copy(alpha = 0.75f),
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
                TextButton(onClick = { vm.deletePlaylist(playlist.id) }) { Text("Delete") }
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
