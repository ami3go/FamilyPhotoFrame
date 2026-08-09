package com.example.familyphotoframe.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Photo-source setup; large folder selection is delegated to its virtualized screen. */
@Composable
internal fun PhotosSettings(
    state: SlideshowUiState,
    vm: SlideshowViewModel,
    onManageFolders: () -> Unit,
) {
    Text(
        stringResource(R.string.settings_source_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    Button(onClick = vm::requestPickFolder, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_choose_folder))
    }
    OutlinedButton(onClick = vm::requestPickFolder, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_repair_folder))
    }
    OutlinedButton(onClick = vm::useSamples, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_use_samples))
    }
    OutlinedButton(onClick = vm::rebuildIndex, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_rebuild_index))
    }

    // Keep remote source setup immediately below the basic local choices. First-run
    // navigation lands on this page, so a NAS user must not have to scroll through
    // unrelated upload controls before reaching the connection form.
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
    SmbSection(state = state, vm = vm)
    SynologySection(state = state, vm = vm)
    WebDavSection(state = state, vm = vm)
    WebUploadSettingsSection(state, vm)
    FiltersSection(state = state, vm = vm)
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    SectionLabel(stringResource(R.string.settings_folders))
    Text(
        if (state.selectedFolders.isEmpty()) stringResource(R.string.settings_folders_all)
        else stringResource(R.string.settings_folders_some, state.selectedFolders.size),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontSize = 14.sp,
    )
    OutlinedButton(onClick = onManageFolders, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_folders_manage))
    }
}
@Composable
internal fun WebUploadSettingsSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    SectionLabel("Web upload to frame")
    ToggleRow("Enable paired browser uploads", state.webUpload.enabled, vm::setWebUploadEnabled)
    Text(
        "Use only on a trusted private network. Uploaded photos are stored in the app-managed Local uploads library.",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 14.sp,
    )
    ToggleRow(
        "Allow uploads while slideshow is playing",
        state.webUpload.allowWhilePlaying,
        vm::setWebUploadAllowWhilePlaying,
    )
    Text("Duplicate policy", color = MaterialTheme.colorScheme.onSurface)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UploadDuplicatePolicy.entries.forEach { policy ->
            FilterChip(
                selected = state.webUpload.duplicatePolicy == policy,
                onClick = { vm.setWebUploadDuplicatePolicy(policy) },
                label = { Text(policy.name.lowercase().replace('_', ' ')) },
            )
        }
    }
    Text("Maximum file size", color = MaterialTheme.colorScheme.onSurface)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(25, 100, 250).forEach { sizeMiB ->
            FilterChip(
                selected = state.webUpload.maxFileBytes / (1024L * 1024L) == sizeMiB.toLong(),
                onClick = { vm.setWebUploadMaxFileMiB(sizeMiB) },
                label = { Text("$sizeMiB MiB") },
            )
        }
    }
}
@Composable
internal fun SynologySection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val syn = state.synology
    var url by remember(syn?.baseUrl) { mutableStateOf(syn?.baseUrl.orEmpty()) }
    var folder by remember(syn?.folderPath) { mutableStateOf(syn?.folderPath ?: "/photo") }
    var user by remember(syn?.user) { mutableStateOf(syn?.user.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var thumbs by remember(syn?.useThumbnails) { mutableStateOf(syn?.useThumbnails ?: true) }

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    SectionLabel(stringResource(R.string.settings_synology))
    Text(
        stringResource(R.string.settings_syn_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp,
    )
    OutlinedTextField(
        value = url, onValueChange = { url = it },
        label = { Text(stringResource(R.string.settings_syn_url)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = folder, onValueChange = { folder = it },
        label = { Text(stringResource(R.string.settings_syn_folder)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = user, onValueChange = { user = it },
        label = { Text(stringResource(R.string.settings_syn_user)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text(stringResource(R.string.settings_syn_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = otp, onValueChange = { otp = it },
        label = { Text(stringResource(R.string.settings_syn_otp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    ToggleRow(stringResource(R.string.settings_syn_thumbnails), thumbs) { thumbs = it }

    // ---- certificate trust (ROADMAP.md "HTTPS + certificate-trust choice") ----
    val pinned = syn?.pinnedCertSha256
    if (!pinned.isNullOrBlank()) {
        Text(
            stringResource(R.string.settings_syn_cert_pinned),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 13.sp,
        )
        Text(pinned, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 11.sp)
        OutlinedButton(onClick = vm::clearSynologyCertificate) {
            Text(stringResource(R.string.settings_syn_cert_forget))
        }
    }
    val offered = state.synologyCertFingerprint
    if (offered != null) {
        // The user must be able to compare this against DSM before approving, so the
        // fingerprint is shown in full rather than truncated.
        Text(
            stringResource(R.string.settings_syn_cert_prompt),
            color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
        )
        Text(offered, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
        Button(onClick = { vm.trustSynologyCertificate(offered) }) {
            Text(stringResource(R.string.settings_syn_cert_trust))
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { vm.testSynology(url, folder, user, password, otp) }) {
            Text(stringResource(R.string.settings_syn_test))
        }
        OutlinedButton(onClick = { vm.probeSynologyCertificate(url) }) {
            Text(stringResource(R.string.settings_syn_cert_check))
        }
        Button(onClick = { vm.saveSynology(url, folder, user, password, otp, thumbs) }) {
            Text(stringResource(R.string.settings_syn_save))
        }
    }
}

/**
 * Scan filters (spec §20). Comma-separated text rather than a chip editor: these are
 * edited rarely, and a text field is one focusable control on a D-pad instead of N.
 */
@Composable
internal fun FiltersSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val f = state.filters
    var includes by remember(f.includeGlobs) { mutableStateOf(f.includeGlobs.joinToString(", ")) }
    var excludes by remember(f.excludeGlobs) { mutableStateOf(f.excludeGlobs.joinToString(", ")) }
    var folders by remember(f.excludeFolders) { mutableStateOf(f.excludeFolders.joinToString(", ")) }
    var subfolders by remember(f.includeSubfolders) { mutableStateOf(f.includeSubfolders) }

    fun split(text: String) = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    SectionLabel(stringResource(R.string.settings_filters))
    Text(
        stringResource(R.string.settings_filter_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp,
    )
    OutlinedTextField(
        value = includes, onValueChange = { includes = it },
        label = { Text(stringResource(R.string.settings_filter_include)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = excludes, onValueChange = { excludes = it },
        label = { Text(stringResource(R.string.settings_filter_exclude)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = folders, onValueChange = { folders = it },
        label = { Text(stringResource(R.string.settings_filter_folders)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    ToggleRow(stringResource(R.string.settings_filter_subfolders), subfolders) { subfolders = it }
    Button(onClick = {
        vm.setFilters(
            FilterSettings(
                includeGlobs = split(includes),
                excludeGlobs = split(excludes),
                excludeFolders = split(folders),
                includeSubfolders = subfolders,
            )
        )
    }) {
        Text(stringResource(R.string.settings_filter_apply))
    }
}

/**
 * Nextcloud / generic WebDAV source. The DAV path is left blank by default and filled
 * in from the username, because the Nextcloud endpoint is derivable and most users have
 * never seen it; it stays editable for plain WebDAV mounts, which have no such convention.
 */
@Composable
internal fun WebDavSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val dav = state.webdav
    var url by remember(dav?.baseUrl) { mutableStateOf(dav?.baseUrl.orEmpty()) }
    var root by remember(dav?.rootPath) { mutableStateOf(dav?.rootPath.orEmpty()) }
    var folder by remember(dav?.folderPath) { mutableStateOf(dav?.folderPath.orEmpty()) }
    var user by remember(dav?.user) { mutableStateOf(dav?.user.orEmpty()) }
    var password by remember { mutableStateOf("") }

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    SectionLabel(stringResource(R.string.settings_dav))
    Text(
        stringResource(R.string.settings_dav_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp,
    )
    OutlinedTextField(
        value = url, onValueChange = { url = it },
        label = { Text(stringResource(R.string.settings_dav_url)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = user, onValueChange = { user = it },
        label = { Text(stringResource(R.string.settings_dav_user)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text(stringResource(R.string.settings_dav_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = root, onValueChange = { root = it },
        label = { Text(stringResource(R.string.settings_dav_root)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = folder, onValueChange = { folder = it },
        label = { Text(stringResource(R.string.settings_dav_folder)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { vm.testWebDav(url, root, folder, user, password) }) {
            Text(stringResource(R.string.settings_syn_test))
        }
        Button(onClick = { vm.saveWebDav(url, root, folder, user, password) }) {
            Text(stringResource(R.string.settings_dav_save))
        }
    }
}
@Composable
internal fun SmbSection(state: SlideshowUiState, vm: SlideshowViewModel) {
    val smb = state.smb
    var host by remember(smb) { mutableStateOf(smb?.host ?: "") }
    var share by remember(smb) { mutableStateOf(smb?.share ?: "") }
    var path by remember(smb) { mutableStateOf(smb?.path ?: "") }
    var user by remember(smb) { mutableStateOf(smb?.user ?: "") }
    var domain by remember(smb) { mutableStateOf(smb?.domain ?: "") }
    var password by remember(smb) { mutableStateOf("") }

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    SectionLabel(stringResource(R.string.settings_smb))
    SmbField(host, { host = it }, R.string.smb_host)
    SmbField(share, { share = it }, R.string.smb_share)
    SmbField(path, { path = it }, R.string.smb_path)
    SmbField(user, { user = it }, R.string.smb_user)
    SmbField(domain, { domain = it }, R.string.smb_domain)
    SmbField(password, { password = it }, R.string.smb_password, isPassword = true)

    state.smbTestResult?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { vm.testSmb(host, share, path, user, domain, password) }) {
            Text(stringResource(R.string.smb_test))
        }
        Button(onClick = { vm.saveSmb(host, share, path, user, domain, password) }) {
            Text(stringResource(R.string.smb_save))
        }
    }
}
@Composable
internal fun SmbField(
    value: String,
    onChange: (String) -> Unit,
    labelRes: Int,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
