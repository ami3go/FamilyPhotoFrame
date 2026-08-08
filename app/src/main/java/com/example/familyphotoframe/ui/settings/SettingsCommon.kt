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

/** Refactored settings sections: SectionLabel, ToggleRow, FolderCheckboxRow, OpacityStepper, PositionRow, TransitionDurationControl, IntervalStepper. */
@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = Color(0xFFE3B23C), fontSize = 14.sp, fontWeight = FontWeight.Medium)
}
@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A real checkbox row for the multi-select discovered-folder list. */
@Composable
internal fun FolderCheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 17.sp)
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Cycles an overlay through the 9-grid anchor (spec §11), left/right D-pad friendly —
 * each button press moves focus to an adjacent, single-press-actionable control, the
 * same pattern [IntervalStepper] uses for the slide interval.
 */
/** -10%/+10% stepper for the shared overlay text opacity (Phase 2 increment 7). */
@Composable
internal fun OpacityStepper(opacity: Float, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.settings_overlay_opacity), color = Color.White, fontSize = 17.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(opacity - 0.1f) }) { Text("\u2212") }
            Text(
                "${(opacity * 100).toInt()}%",
                color = Color.White, fontSize = 17.sp,
                modifier = Modifier.widthIn(min = 56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(onClick = { onChange(opacity + 0.1f) }) { Text("+") }
        }
    }
}
@Composable
internal fun PositionRow(
    position: OverlayPosition,
    onChange: (OverlayPosition) -> Unit,
) {
    val labels = stringArrayResource(R.array.overlay_positions)
    val values = OverlayPosition.values()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.settings_overlay_position),
            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(values[(position.ordinal - 1 + values.size) % values.size]) }) {
                Text("\u25C0")
            }
            Text(
                labels[position.ordinal],
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                modifier = Modifier.widthIn(min = 104.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(onClick = { onChange(values[(position.ordinal + 1) % values.size]) }) {
                Text("\u25B6")
            }
        }
    }
}
@Composable
internal fun TransitionDurationControl(durationMs: Int, onChange: (Int) -> Unit) {
    var localDuration by remember { mutableIntStateOf(durationMs.coerceIn(300, 2_000)) }
    LaunchedEffect(durationMs) { localDuration = durationMs.coerceIn(300, 2_000) }

    fun commit(value: Int) {
        val clamped = value.coerceIn(300, 2_000)
        localDuration = clamped
        onChange(clamped)
    }

    Text(
        stringResource(R.string.settings_transition_duration, localDuration),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 14.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TransitionChip(
            selected = localDuration == 600,
            label = stringResource(R.string.settings_transition_duration_fast),
        ) { commit(600) }
        TransitionChip(
            selected = localDuration == 900,
            label = stringResource(R.string.settings_transition_duration_normal),
        ) { commit(900) }
        TransitionChip(
            selected = localDuration == 1300,
            label = stringResource(R.string.settings_transition_duration_slow),
        ) { commit(1300) }
    }
    Slider(
        value = localDuration.toFloat(),
        onValueChange = { localDuration = ((it / 50f).roundToInt() * 50).coerceIn(300, 2_000) },
        onValueChangeFinished = { onChange(localDuration) },
        valueRange = 300f..2000f,
        steps = 33,
        modifier = Modifier.fillMaxWidth(),
    )
}
@Composable
internal fun IntervalStepper(seconds: Int, onChange: (Int) -> Unit) {
    var localSeconds by remember { mutableIntStateOf(PlaybackInterval.clamp(seconds)) }

    // Repository updates are asynchronous. Keep the control optimistic so each button
    // press is visible immediately, then reconcile when the persisted value is emitted.
    LaunchedEffect(seconds) {
        localSeconds = PlaybackInterval.clamp(seconds)
    }

    fun commit(value: Int) {
        val clamped = PlaybackInterval.clamp(value)
        localSeconds = clamped
        onChange(clamped)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(
            value = localSeconds.toFloat(),
            onValueChange = { localSeconds = PlaybackInterval.clamp(it.roundToInt()) },
            onValueChangeFinished = { onChange(localSeconds) },
            valueRange = PlaybackInterval.MIN_SECONDS.toFloat()..PlaybackInterval.MAX_SECONDS.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = { commit(PlaybackInterval.adjust(localSeconds, -PlaybackInterval.BUTTON_STEP_SECONDS)) },
            ) { Text("−${PlaybackInterval.BUTTON_STEP_SECONDS}s") }
            Text(
                "${localSeconds}s",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.widthIn(min = 72.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(
                onClick = { commit(PlaybackInterval.adjust(localSeconds, PlaybackInterval.BUTTON_STEP_SECONDS)) },
            ) { Text("+${PlaybackInterval.BUTTON_STEP_SECONDS}s") }
        }
        Text(
            stringResource(
                R.string.settings_interval_range,
                PlaybackInterval.MIN_SECONDS,
                PlaybackInterval.MAX_SECONDS,
            ),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
    }
}
