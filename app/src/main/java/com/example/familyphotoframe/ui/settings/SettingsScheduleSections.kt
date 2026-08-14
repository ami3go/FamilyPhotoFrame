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
import kotlin.math.roundToInt

/** Refactored settings sections: PlaylistScheduleSection, ScheduleSettings, AutoRescanSection. */
@Composable
internal fun PlaylistScheduleSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    SettingsSectionCard("Scheduled playlist switching") {
    ToggleRow("Enable playlist schedule", state.playlistScheduleEnabled, vm::setPlaylistScheduleEnabled)
    state.activePlaylistRuleName?.let {
        Text("Active rule: $it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 14.sp)
    }
    state.playlistScheduleRules.forEach { rule ->
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${rule.startTime}–${rule.endTime} · ${state.playlists.firstOrNull { it.id == rule.playlistId }?.name ?: rule.playlistId}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                )
            }
            TextButton(
                onClick = { vm.deletePlaylistScheduleRule(rule.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        }
    }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("20:00") }
    var playlistId by remember(state.activePlaylistId) { mutableStateOf(state.activePlaylistId) }
    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Rule name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it.take(5) }, label = { Text("Start HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(end, { end = it.take(5) }, label = { Text("End HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Text("Playlist", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 14.sp)
    state.playlists.filter { it.enabled }.forEach { playlist ->
        FilterChip(
            selected = playlistId == playlist.id,
            onClick = { playlistId = playlist.id },
            label = { Text(playlist.name) },
        )
    }
    OutlinedButton(
        onClick = { vm.addPlaylistScheduleRule(name, playlistId, start, end) },
        enabled = state.playlists.any { it.id == playlistId },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add schedule rule") }
    OutlinedButton(onClick = vm::cancelPlaylistOverride, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel manual playlist override")
    }
    }
}
@Composable
internal fun ScheduleSettings(state: SlideshowUiState, vm: SlideshowViewModel) {
    PlaylistScheduleSection(state, vm)
    OnThisDaySection(state, vm)
    AutoRescanSection(state = state, vm = vm)
}

/**
 * Periodic memory interlude — see docs/FPF-FEAT-ON-THIS-DAY-001.md. Single-photo
 * playback only for now (Phase 3 adds the multi-year collage); "Preview now" works
 * regardless of the enable toggle, matching the folder-preview button elsewhere.
 */
@Composable
internal fun OnThisDaySection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val cfg = state.onThisDay
    var timesPerDay by remember(cfg.timesPerDay) { mutableStateOf(cfg.timesPerDay.toString()) }
    var durationMinutes by remember(cfg.durationMinutes) { mutableStateOf(cfg.durationMinutes.toString()) }
    var minYearsAgo by remember(cfg.minYearsAgo) { mutableStateOf(cfg.minYearsAgo.toString()) }

    SettingsSectionCard("On this day") {
        Text(
            "Periodically shows photos taken on today's date in previous years, then " +
                "returns to normal playback.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
        ToggleRow("Enable on this day", cfg.enabled, vm::setOnThisDayEnabled)
        if (cfg.enabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timesPerDay,
                    onValueChange = {
                        timesPerDay = it.filter(Char::isDigit).take(2)
                        timesPerDay.toIntOrNull()?.let(vm::setOnThisDayTimesPerDay)
                    },
                    label = { Text("Times per day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = {
                        durationMinutes = it.filter(Char::isDigit).take(2)
                        durationMinutes.toIntOrNull()?.let(vm::setOnThisDayDurationMinutes)
                    },
                    label = { Text("Minutes each") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = minYearsAgo,
                onValueChange = {
                    minYearsAgo = it.filter(Char::isDigit).take(3)
                    minYearsAgo.toIntOrNull()?.let(vm::setOnThisDayMinYearsAgo)
                },
                label = { Text("Minimum years ago (0 counts this year)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(onClick = vm::previewOnThisDay, modifier = Modifier.fillMaxWidth()) {
            Text("Preview now")
        }
    }
}
@Composable
internal fun AutoRescanSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val schedule = state.schedule
    var at by remember(schedule.autoRescanAt) { mutableStateOf(schedule.autoRescanAt) }
    val days = remember(schedule.autoRescanDays) {
        RescanSchedule.parseDays(schedule.autoRescanDays)
    }

    SettingsSectionCard(stringResource(R.string.settings_autorescan)) {
    ToggleRow(
        stringResource(R.string.settings_autorescan_enable),
        schedule.autoRescanEnabled,
        vm::setAutoRescanEnabled,
    )

    if (schedule.autoRescanEnabled) {
        Text(
            stringResource(R.string.settings_autorescan_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp,
        )
        OutlinedTextField(
            value = at,
            onValueChange = { at = it; vm.setAutoRescanAt(it) },
            label = { Text(stringResource(R.string.settings_autorescan_at)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel(stringResource(R.string.settings_autorescan_days))
        val dayLabels = listOf(
            R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
            R.string.day_fri, R.string.day_sat, R.string.day_sun,
        )
        // Two rows so seven chips fit a narrow frame without clipping.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in 1..4) {
                    FilterChip(
                        selected = d in days,
                        onClick = { vm.setAutoRescanDay(d, d !in days) },
                        label = { Text(stringResource(dayLabels[d - 1])) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in 5..7) {
                    FilterChip(
                        selected = d in days,
                        onClick = { vm.setAutoRescanDay(d, d !in days) },
                        label = { Text(stringResource(dayLabels[d - 1])) },
                    )
                }
                OutlinedButton(onClick = { vm.setAutoRescanDays(RescanSchedule.everyDay()) }) {
                    Text(stringResource(R.string.settings_autorescan_everyday))
                }
            }
        }
        if (days.isEmpty()) {
            // Enabled with no days selected would never run; say so rather than letting
            // it look like a broken feature.
            Text(
                stringResource(R.string.settings_autorescan_nodays),
                color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
            )
        }
    }
    }
}
