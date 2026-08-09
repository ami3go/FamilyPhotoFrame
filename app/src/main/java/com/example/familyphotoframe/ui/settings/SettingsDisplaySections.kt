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
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel
import com.example.familyphotoframe.web.QrCodes
import kotlin.math.roundToInt

/** Refactored settings sections: BrightnessAutomationSection, HealthDashboardSection, formatBytes, DisplaySettings, WeatherSection. */
@Composable
internal fun BrightnessAutomationSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val settings = state.brightnessAutomation
    SettingsSectionCard("Brightness and night mode") {
    Text(
        "Current brightness: ${(state.screenBrightness * 100).roundToInt()}%" +
            (state.activeBrightnessPeriodId?.let { " · $it" } ?: ""),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 14.sp,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrightnessMode.entries.forEach { mode ->
            FilterChip(
                selected = settings.mode == mode,
                onClick = { vm.setBrightnessMode(mode) },
                label = { Text(mode.name.lowercase().replace('_', ' ')) },
            )
        }
    }
    Text("Manual/day brightness", color = MaterialTheme.colorScheme.onSurface)
    Slider(
        value = settings.manualBrightness,
        onValueChange = vm::setManualBrightness,
        valueRange = 0.01f..1f,
    )
    if (settings.mode != BrightnessMode.MANUAL) {
        settings.periods.forEachIndexed { index, period ->
            var start by remember(period.startTime) { mutableStateOf(period.startTime) }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    start,
                    {
                        val candidate = it.take(5)
                        start = candidate
                        if (candidate.length == 5 && SleepSchedule.parseMinutes(candidate) != null) {
                            vm.setBrightnessPeriod(index, candidate, period.brightness, period.action)
                        }
                    },
                    label = { Text("Start") },
                    singleLine = true, modifier = Modifier.widthIn(max = 120.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text("${(period.brightness * 100).roundToInt()}% · ${period.action.name.lowercase().replace('_', ' ')}", color = MaterialTheme.colorScheme.onSurface)
                    Slider(
                        value = period.brightness,
                        onValueChange = { vm.setBrightnessPeriod(index, start, it, period.action) },
                        valueRange = 0.01f..1f,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NightAction.entries.forEach { action ->
                    FilterChip(
                        selected = period.action == action,
                        onClick = { vm.setBrightnessPeriod(index, start, period.brightness, action) },
                        label = { Text(action.name.lowercase().replace('_', ' ')) },
                    )
                }
            }
        }
    }
    if (settings.mode == BrightnessMode.AMBIENT || settings.mode == BrightnessMode.SCHEDULED_AMBIENT) {
        Text(
            if (state.ambientSensorAvailable) "Ambient sensor: ${state.ambientLux?.roundToInt() ?: 0} lux"
            else "Ambient light sensor is not available; scheduled/manual brightness is used.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 14.sp,
        )
    }
    OutlinedButton(onClick = vm::temporaryWake, modifier = Modifier.fillMaxWidth()) {
        Text("Wake frame temporarily")
    }
    }
}
@Composable
internal fun HealthDashboardSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val h = state.health
    Text(h.headline, color = when (h.level) {
        "CRITICAL" -> Color(0xFFFF8A80)
        "WARNING" -> Color(0xFFFFD180)
        else -> Color(0xFFA5D6A7)
    }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Photos: ${h.eligiblePhotos} playable / ${h.totalPhotos} indexed", color = MaterialTheme.colorScheme.onSurface)
    Text("Favorites: ${h.favoritePhotos} · Hidden: ${h.hiddenPhotos} · Failed: ${h.failedPhotos}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    Text("Local uploads: ${h.localUploadPhotos}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    Text("Free storage: ${formatBytes(h.freeStorageBytes)}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    Text("Playlist: ${state.activePlaylistName}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    state.activePlaylistRuleName?.let { Text("Schedule rule: $it", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) }
    h.recommendations.forEach { Text("• $it", color = Color(0xFFFFD180), fontSize = 14.sp) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::rebuildIndex, modifier = Modifier.weight(1f)) { Text("Scan now") }
        OutlinedButton(onClick = vm::unhideAllPhotos, modifier = Modifier.weight(1f)) { Text("Unhide all") }
    }
    OutlinedButton(onClick = vm::requestExportSupportBundle, modifier = Modifier.fillMaxWidth()) {
        Text("Export support report")
    }
}
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GiB".format(bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes.toDouble() / (1024.0 * 1024.0))
    else -> "${bytes / 1024L} KiB"
}
@Composable
internal fun DisplaySettings(state: SlideshowUiState, vm: SlideshowViewModel) {
    BrightnessAutomationSection(state, vm)

    SettingsSectionCard(stringResource(R.string.settings_overlays)) {
    OpacityStepper(state.overlays.opacity, vm::setOverlayOpacity)

    ToggleRow(stringResource(R.string.settings_clock), state.overlays.clockShow, vm::setShowClock)
    if (state.overlays.clockShow) {
        PositionRow(state.overlays.clockPosition, vm::setClockPosition)
        ToggleRow(stringResource(R.string.settings_clock_24h), state.overlays.clock24h, vm::setClock24h)
    }
    ToggleRow(stringResource(R.string.settings_date), state.overlays.dateShow, vm::setShowDate)
    if (state.overlays.dateShow) PositionRow(state.overlays.datePosition, vm::setDatePosition)
    ToggleRow(stringResource(R.string.settings_folder_overlay), state.overlays.folderShow, vm::setShowFolder)
    if (state.overlays.folderShow) PositionRow(state.overlays.folderPosition, vm::setFolderPosition)
    ToggleRow(stringResource(R.string.settings_photo_date), state.overlays.photoDateShow, vm::setShowPhotoDate)
    if (state.overlays.photoDateShow) PositionRow(state.overlays.photoDatePosition, vm::setPhotoDatePosition)
    ToggleRow(stringResource(R.string.settings_caption), state.overlays.captionShow, vm::setShowCaption)
    if (state.overlays.captionShow) PositionRow(state.overlays.captionPosition, vm::setCaptionPosition)
    ToggleRow(stringResource(R.string.settings_location), state.overlays.locationShow, vm::setShowLocation)
    if (state.overlays.locationShow) PositionRow(state.overlays.locationPosition, vm::setLocationPosition)
    }

    WeatherSection(state = state, vm = vm)
}
@Composable
internal fun WeatherSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val weather = state.weather
    var lat by remember(weather.latitude) { mutableStateOf(if (weather.hasValidCoordinates) weather.latitude.toString() else "") }
    var lon by remember(weather.longitude) { mutableStateOf(if (weather.hasValidCoordinates) weather.longitude.toString() else "") }
    var endpoint by remember(weather.endpointBaseUrl) { mutableStateOf(weather.endpointBaseUrl) }
    var apiKey by remember { mutableStateOf("") }

    SettingsSectionCard(stringResource(R.string.settings_weather)) {
    ToggleRow(stringResource(R.string.settings_weather_enable), weather.enabled, vm::setWeatherEnabled)

    if (weather.enabled) {
        Text(
            stringResource(R.string.settings_weather_hint),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp,
        )
        // CC BY 4.0 attribution (WEATHER_LICENSING.md). Shown here — where the weather
        // feature is actually presented to the user — rather than on the live TV
        // overlay itself, which would mean a permanent on-screen credit line sitting
        // over family photos for as long as weather is on. The same attribution also
        // appears in the Notices screen.
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri(ThirdPartyLicenses.WEATHER_ATTRIBUTION_URL) }) {
            Text(stringResource(R.string.settings_weather_attribution), fontSize = 12.sp)
        }
        OutlinedTextField(
            value = lat, onValueChange = { lat = it },
            label = { Text(stringResource(R.string.settings_weather_lat)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = lon, onValueChange = { lon = it },
            label = { Text(stringResource(R.string.settings_weather_lon)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { vm.setWeatherLocation(lat, lon) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_weather_save_location)) }

        SectionLabel(stringResource(R.string.settings_weather_units))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = weather.units == TemperatureUnits.CELSIUS,
                onClick = { vm.setWeatherUnits(TemperatureUnits.CELSIUS) },
                label = { Text("\u00B0C") },
            )
            FilterChip(
                selected = weather.units == TemperatureUnits.FAHRENHEIT,
                onClick = { vm.setWeatherUnits(TemperatureUnits.FAHRENHEIT) },
                label = { Text("\u00B0F") },
            )
        }

        Text(
            stringResource(R.string.settings_weather_licence),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it; vm.setWeatherEndpoint(it) },
            label = { Text(stringResource(R.string.settings_weather_endpoint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; vm.setWeatherApiKey(it) },
            label = { Text(stringResource(R.string.settings_weather_apikey)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        ToggleRow(
            stringResource(R.string.settings_weather_show_overlay),
            state.overlays.weatherShow,
            vm::setWeatherOverlayShow,
        )
        if (state.overlays.weatherShow) {
            PositionRow(state.overlays.weatherPosition, vm::setWeatherPosition)
        }
    }
    }
}
