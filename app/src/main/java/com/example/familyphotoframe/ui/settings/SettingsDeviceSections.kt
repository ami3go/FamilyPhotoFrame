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

/** Refactored settings sections: DeviceSettings, WebSection, RememberedBrowserSettings, formatRememberedTime. */
@Composable
internal fun DeviceSettings(state: SlideshowUiState, vm: SlideshowViewModel) {
    ToggleRow(stringResource(R.string.settings_autostart), state.autoStartOnBoot, vm::setAutoStartOnBoot)

    Text(
        stringResource(R.string.settings_perf),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Text(
        stringResource(R.string.settings_perf_hint),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    ToggleRow(
        stringResource(R.string.settings_perf_overlay),
        state.showPerformanceOverlay,
        vm::setShowPerformanceOverlay,
    )
    if (state.showPerformanceOverlay) {
        OutlinedButton(onClick = vm::capturePerformanceSample, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_perf_capture))
        }
    }
}
@Composable
internal fun WebSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    // The PIN/URL live on the server object, not in settings, so refresh on entry
    // and whenever the toggle changes.
    LaunchedEffect(state.web.enabled) {
        vm.refreshWebInfo()
        if (state.web.enabled) vm.refreshRememberedBrowsers()
    }

    ToggleRow(stringResource(R.string.web_enable), state.web.enabled, vm::setWebEnabled)

    if (!state.web.enabled) {
        Text(
            stringResource(R.string.web_off),
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
        )
        return
    }

    val url = state.webUrl
    val pin = state.webPin
    if (url == null || pin == null) {
        Text(
            stringResource(R.string.web_starting),
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
        )
    } else {
        Text(stringResource(R.string.web_hint), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(url, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)

        // Scan-to-pair QR (spec §15.3): encodes a single-use token so a phone can
        // pair without typing the PIN. Falls back silently to the PIN if encoding
        // is unavailable.
        state.webQrUrl?.let { qrUrl ->
            val qr = remember(qrUrl) { QrCodes.encode(qrUrl, QR_SIZE_PX) }
            if (qr != null) {
                Text(
                    stringResource(R.string.web_scan_hint),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.web_scan_hint),
                    modifier = Modifier.size(200.dp).padding(top = 4.dp),
                )
            }
        }
        Text(
            stringResource(R.string.web_pin_label),
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Grouped for readability at a distance, e.g. "1234 5678".
        Text(
            text = pin.chunked(4).joinToString(" "),
            color = Color(0xFFE3B23C), fontSize = 34.sp, fontWeight = FontWeight.Light,
        )
    }
    Text(
        stringResource(R.string.web_no_secrets),
        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    var keepRememberedOnPinReset by remember { mutableStateOf(false) }
    FolderCheckboxRow(
        "Keep remembered browsers when generating a new PIN",
        keepRememberedOnPinReset,
    ) { keepRememberedOnPinReset = it }
    Text(
        "The safest default is to revoke every remembered browser when the PIN changes.",
        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
    )
    OutlinedButton(
        onClick = { vm.regenerateWebPin(keepRememberedOnPinReset) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.webPinRegenerationInProgress,
    ) {
        Text(
            stringResource(
                if (state.webPinRegenerationInProgress) R.string.web_new_pin_working
                else R.string.web_new_pin,
            )
        )
    }
    RememberedBrowserSettings(state, vm)
}
@Composable
internal fun RememberedBrowserSettings(state: SlideshowUiState, vm: SlideshowViewModel) {
    val policy = state.web.rememberedBrowsers
    var maxBrowsers by remember(policy.maxRememberedBrowsers) {
        mutableStateOf(policy.maxRememberedBrowsers.toString())
    }
    var maxDays by remember(policy.maxExpirySeconds) {
        mutableStateOf((policy.maxExpirySeconds / (24L * 60L * 60L)).coerceAtLeast(1L).toString())
    }

    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(top = 12.dp))
    SectionLabel("Remembered browsers")
    Text(
        "Persistent browser access is disabled by default because this web server uses plain HTTP. Enable it only on a trusted private LAN.",
        color = Color(0xFFE3B23C), fontSize = 13.sp,
    )
    ToggleRow("Allow browsers to be remembered", policy.enabled, vm::setRememberedBrowsersEnabled)
    if (!policy.enabled) return

    ToggleRow("Allow Forever", policy.allowForever, vm::setRememberedAllowForever)
    Text("Default expiry", color = Color.White, fontSize = 16.sp)
    val expiryRows = listOf(
        listOf(
            com.example.familyphotoframe.data.settings.RememberExpiryMode.SESSION_ONLY to "Session",
            com.example.familyphotoframe.data.settings.RememberExpiryMode.ONE_HOUR to "1 hour",
            com.example.familyphotoframe.data.settings.RememberExpiryMode.ONE_DAY to "1 day",
            com.example.familyphotoframe.data.settings.RememberExpiryMode.ONE_WEEK to "1 week",
        ),
        listOf(
            com.example.familyphotoframe.data.settings.RememberExpiryMode.ONE_MONTH to "1 month",
            com.example.familyphotoframe.data.settings.RememberExpiryMode.ONE_YEAR to "1 year",
            com.example.familyphotoframe.data.settings.RememberExpiryMode.FOREVER to "Forever",
        ),
    )
    expiryRows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (mode, label) ->
                if (mode != com.example.familyphotoframe.data.settings.RememberExpiryMode.FOREVER || policy.allowForever) {
                    FilterChip(
                        selected = policy.defaultExpiry == mode,
                        onClick = { vm.setRememberedDefaultExpiry(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }

    OutlinedTextField(
        value = maxBrowsers,
        onValueChange = {
            maxBrowsers = it.filter(Char::isDigit).take(2)
            maxBrowsers.toIntOrNull()?.let(vm::setRememberedMaximumBrowsers)
        },
        label = { Text("Maximum remembered browsers (1–32)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = maxDays,
        onValueChange = {
            maxDays = it.filter(Char::isDigit).take(4)
            maxDays.toIntOrNull()?.let(vm::setRememberedMaximumExpiryDays)
        },
        label = { Text("Maximum expiry in days (1–3650)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::refreshRememberedBrowsers) { Text("Refresh") }
        OutlinedButton(onClick = vm::revokeAllRememberedBrowsers) { Text("Revoke all") }
    }
    if (state.rememberedBrowserRecords.isEmpty()) {
        Text("No browsers are remembered.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
    } else {
        state.rememberedBrowserRecords.forEach { browser ->
            Column(
                modifier = Modifier.fillMaxWidth().background(
                    Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp),
                ).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(browser.label, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    listOf(browser.browserSummary, browser.osSummary).filter(String::isNotBlank).joinToString(" on ")
                        .ifBlank { "Browser" },
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                )
                Text(
                    "Last used: ${formatRememberedTime(browser.lastUsedAtEpochMs)}",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                )
                Text(
                    "Expires: ${browser.expiresAtEpochMs?.let(::formatRememberedTime) ?: "Forever — until revoked"}",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                )
                OutlinedButton(onClick = { vm.revokeRememberedBrowser(browser.id) }) {
                    Text("Revoke")
                }
            }
        }
    }
}
internal fun formatRememberedTime(epochMs: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(epochMs))

/** On-screen QR size; large enough to scan across a room from a wall-mounted frame. */
private const val QR_SIZE_PX = 480

/**
 * Automatic index refresh (spec §20): a time of day plus the weekdays it may run on.
 *
 * Weekdays are chips rather than a multi-select dialog so each day is one D-pad press
 * (§12.2), matching the brightness presets below.
 */
