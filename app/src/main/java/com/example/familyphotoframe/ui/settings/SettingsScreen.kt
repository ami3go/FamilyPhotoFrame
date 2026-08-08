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

/** Two-level settings destinations. [ROOT] contains group entries only. */
enum class SettingsPage {
    ROOT,
    PHOTOS,
    FOLDERS,
    PLAYBACK,
    DISPLAY,
    SCHEDULE,
    DEVICE,
    BACKUP,
    ABOUT,
}

/**
 * Android-style settings navigation. The root page contains only groups; every
 * individual setting lives in one submenu and writes through the ViewModel.
 */
@Composable
fun SettingsScreen(
    vm: SlideshowViewModel,
    diagnostics: DiagnosticsLog,
    page: SettingsPage,
    onOpenPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onBackToSlideshow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDiagnostics by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }

    val title = when (page) {
        SettingsPage.ROOT -> stringResource(R.string.settings_title)
        SettingsPage.PHOTOS -> stringResource(R.string.settings_source)
        SettingsPage.FOLDERS -> stringResource(R.string.settings_folders)
        SettingsPage.PLAYBACK -> stringResource(R.string.settings_playback)
        SettingsPage.DISPLAY -> stringResource(R.string.settings_display)
        SettingsPage.SCHEDULE -> stringResource(R.string.settings_schedule)
        SettingsPage.DEVICE -> stringResource(R.string.settings_device)
        SettingsPage.BACKUP -> stringResource(R.string.settings_backup)
        SettingsPage.ABOUT -> stringResource(R.string.settings_about)
    }

    if (page == SettingsPage.FOLDERS) {
        FolderManagementSettings(state = state, vm = vm, onBack = onBack, modifier = modifier)
    } else {
        SettingsPageLayout(
            title = title,
            showBackArrow = page != SettingsPage.ROOT,
            onBack = onBack,
            modifier = modifier,
        ) {
            when (page) {
            SettingsPage.ROOT -> {
                Button(
                    onClick = onBackToSlideshow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("← ${stringResource(R.string.settings_back)}", fontSize = 18.sp)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                SettingsGroupList(onOpenPage)
            }
            SettingsPage.PHOTOS -> PhotosSettings(state, vm) { onOpenPage(SettingsPage.FOLDERS) }
            SettingsPage.FOLDERS -> Unit
            SettingsPage.PLAYBACK -> PlaybackSettings(state, vm)
            SettingsPage.DISPLAY -> DisplaySettings(state, vm)
            SettingsPage.SCHEDULE -> ScheduleSettings(state, vm)
            SettingsPage.DEVICE -> DeviceSettings(state, vm)
            SettingsPage.BACKUP -> BackupSettings(vm)
            SettingsPage.ABOUT -> AboutSettings(
                onShowDiagnostics = { showDiagnostics = true },
                onShowNotices = { showNotices = true },
            )
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(diagnostics = diagnostics, onDismiss = { showDiagnostics = false })
    }
    if (showNotices) {
        NoticesDialog(onDismiss = { showNotices = false })
    }
}

@Composable
private fun SettingsPageLayout(
    title: String,
    showBackArrow: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0B0A08)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsHeader(title, showBackArrow, onBack)
            content()
        }
    }
}

@Composable
internal fun SettingsHeader(title: String, showBackArrow: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBackArrow) {
            TextButton(onClick = onBack) {
                Text(text = "←", color = Color.White, fontSize = 28.sp)
            }
        }
        Text(
            title,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun SettingsGroupList(onOpenPage: (SettingsPage) -> Unit) {
    SettingsGroupEntry(stringResource(R.string.settings_source)) { onOpenPage(SettingsPage.PHOTOS) }
    SettingsGroupEntry(stringResource(R.string.settings_playback)) { onOpenPage(SettingsPage.PLAYBACK) }
    SettingsGroupEntry(stringResource(R.string.settings_display)) { onOpenPage(SettingsPage.DISPLAY) }
    SettingsGroupEntry(stringResource(R.string.settings_schedule)) { onOpenPage(SettingsPage.SCHEDULE) }
    SettingsGroupEntry(stringResource(R.string.settings_device)) { onOpenPage(SettingsPage.DEVICE) }
    SettingsGroupEntry(stringResource(R.string.settings_backup)) { onOpenPage(SettingsPage.BACKUP) }
    SettingsGroupEntry(stringResource(R.string.settings_about)) { onOpenPage(SettingsPage.ABOUT) }
}

@Composable
private fun SettingsGroupEntry(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 19.sp)
            Text("›", fontSize = 28.sp)
        }
    }
}

@Composable
private fun AboutSettings(
    onShowDiagnostics: () -> Unit,
    onShowNotices: () -> Unit,
) {
    OutlinedButton(onClick = onShowDiagnostics, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_diagnostics))
    }
    OutlinedButton(onClick = onShowNotices, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_notices))
    }
}

@Composable
private fun DiagnosticsDialog(diagnostics: DiagnosticsLog, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
        title = { Text(stringResource(R.string.settings_diagnostics)) },
        text = {
            val text = remember { diagnostics.renderRedacted(last = 80) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(text, color = Color.White, fontSize = 11.sp)
            }
        },
    )
}

/**
 * Open-source notices (spec §19 / §19.1; see LGPL_COMPLIANCE.md and
 * WEATHER_LICENSING.md). Every entry links to its canonical full license text via the
 * system browser rather than embedding a copy — see `ThirdPartyLicenses.kt` for why.
 */
@Composable
private fun NoticesDialog(onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
        title = { Text(stringResource(R.string.settings_notices)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.settings_notices_weather_title),
                    color = Color(0xFFE3B23C), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.settings_notices_weather_body),
                    color = Color.White, fontSize = 13.sp,
                )
                TextButton(onClick = { uriHandler.openUri(ThirdPartyLicenses.WEATHER_ATTRIBUTION_URL) }) {
                    Text(ThirdPartyLicenses.WEATHER_ATTRIBUTION_URL, fontSize = 12.sp)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                ThirdPartyLicenses.entries.forEach { entry ->
                    Column {
                        Text(
                            "${entry.component} — ${entry.licenseName}",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        )
                        Text(entry.summary, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { uriHandler.openUri(entry.canonicalTextUrl) }) {
                                Text(stringResource(R.string.settings_notices_view_license), fontSize = 12.sp)
                            }
                            entry.projectUrl?.let { url ->
                                TextButton(onClick = { uriHandler.openUri(url) }) {
                                    Text(stringResource(R.string.settings_notices_view_source), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Synology File Station setup (ROADMAP.md network photo-app sources). Mirrors
 * [SmbSection]: plain fields, D-pad reachable, password masked, and the test result
 * reuses the shared `smbTestResult` slot so only one connection test is ever in flight.
 */
