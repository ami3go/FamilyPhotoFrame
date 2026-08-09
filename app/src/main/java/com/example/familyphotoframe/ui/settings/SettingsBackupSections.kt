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

/** Refactored settings sections: BackupSettings, EncryptedBackupSection. */
@Composable
internal fun BackupSettings(vm: SlideshowViewModel) {
    Text(
        stringResource(R.string.backup_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    OutlinedButton(onClick = vm::requestExportConfig, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_export))
    }
    OutlinedButton(onClick = vm::requestImportConfig, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_import))
    }
    OutlinedButton(onClick = vm::requestExportSupportBundle, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.backup_support))
    }
    EncryptedBackupSection(vm = vm)
}
@Composable
internal fun EncryptedBackupSection(vm: SlideshowViewModel) {
    // The passphrase stays local to this composable and is passed straight to the
    // ViewModel call; it is never placed in persisted settings or UI state (spec §14.4).
    var passphrase by remember { mutableStateOf("") }

    SectionLabel(stringResource(R.string.backup_encrypted))
    Text(
        stringResource(R.string.backup_encrypted_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp,
    )
    OutlinedTextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text(stringResource(R.string.backup_passphrase)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { vm.requestExportEncrypted(passphrase); passphrase = "" },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.backup_export_encrypted)) }
    OutlinedButton(
        onClick = { vm.requestImportEncrypted(passphrase); passphrase = "" },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.backup_import_encrypted)) }
}
