package com.example.familyphotoframe.ui.slideshow.viewmodel

import android.net.Uri
import android.os.Build
import com.example.familyphotoframe.BuildConfig
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.diagnostics.DiagnosticsBundleContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.SourceRefreshTrigger
import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.BoundedTextInput
import com.example.familyphotoframe.data.settings.ConfigTransfer
import com.example.familyphotoframe.data.settings.CredentialPolicy
import com.example.familyphotoframe.data.settings.ImportResult
import com.example.familyphotoframe.data.settings.ImportTooLargeException
import com.example.familyphotoframe.data.settings.PortableBundle
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel.FileOp
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel.FileRequest
import com.example.familyphotoframe.web.DiagnosticsDownloadNaming
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuration backup and restore (spec §7.0): plain JSON export/import, encrypted
 * portable bundles that carry the source passwords, and the diagnostics support bundle.
 * The Activity owns the document pickers; a [FileRequest] asks it to open one.
 */
internal class ConfigTransferActions(
    host: SlideshowFeatureHost,
    private val _fileRequests: MutableSharedFlow<FileRequest>,
    private val updateSettingsAndRefresh:
        suspend (SourceRefreshTrigger, (AppSettings) -> AppSettings) -> Unit,
) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics

    /** Memory-only: held between the user typing it and the SAF callback completing. */
    @Volatile private var pendingPassphrase: String? = null

    fun requestExportConfig() {
        _fileRequests.tryEmit(
            FileRequest(FileOp.EXPORT_CONFIG, ConfigTransfer.suggestedFileName(dateStamp()))
        )
    }

    fun requestImportConfig() {
        _fileRequests.tryEmit(FileRequest(FileOp.IMPORT_CONFIG, ""))
    }

    /**
     * Export an encrypted bundle that DOES include the NAS password (spec §14.4).
     * The passphrase is held in memory only for the duration of the file operation and
     * is never persisted, logged, or placed in UI state.
     */
    fun requestExportEncrypted(passphrase: String) {
        if (passphrase.isBlank()) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_passphrase_required)) }
            return
        }
        pendingPassphrase = passphrase
        _fileRequests.tryEmit(
            FileRequest(FileOp.EXPORT_ENCRYPTED, PortableBundle.suggestedFileName(dateStamp()))
        )
    }

    fun requestImportEncrypted(passphrase: String) {
        if (passphrase.isBlank()) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_passphrase_required)) }
            return
        }
        pendingPassphrase = passphrase
        _fileRequests.tryEmit(FileRequest(FileOp.IMPORT_ENCRYPTED, ""))
    }

    fun requestExportSupportBundle() {
        _fileRequests.tryEmit(
            FileRequest(FileOp.EXPORT_SUPPORT, DiagnosticsDownloadNaming.fileName(System.currentTimeMillis()))
        )
    }

    /** Write the chosen document. [uri] is null when the user cancelled. */
    fun onExportTargetChosen(uri: Uri?, op: FileOp) {
        if (uri == null) {
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_picker_cancelled)) }
            return
        }
        viewModelScope.launch {
            if (op == FileOp.EXPORT_SUPPORT) {
                val current = services.settings.settings.first()
                val context = DiagnosticsBundleContext(
                    appVersion = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    buildType = BuildConfig.BUILD_TYPE,
                    sdkInt = Build.VERSION.SDK_INT,
                    deviceModel = Build.MODEL,
                    abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                    sourceKind = current.source.kind.name,
                    indexedCount = services.photoDao.count().toLong(),
                    runtime = services.diagnosticRuntimeState.snapshot(),
                )
                val ok = withContext(services.dispatchers.io) {
                    runCatching {
                        appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            diagnostics.openDurableBundle(context).use { input -> input.copyTo(output) }
                        } ?: error("no output stream")
                    }.isSuccess
                }
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    if (ok) "CONFIG_EXPORTED" else "CONFIG_EXPORT_FAILED",
                    "format" to "diagnostics-jsonl",
                )
                _state.update {
                    it.copy(transientNotice = appContext.getString(if (ok) R.string.msg_export_ok else R.string.msg_export_failed))
                }
                return@launch
            }
            val text = when (op) {
                FileOp.EXPORT_CONFIG -> ConfigTransfer.export(
                    settings = services.settings.settings.first(),
                    appVersion = BuildConfig.VERSION_NAME,
                    nowEpochMs = System.currentTimeMillis(),
                )
                FileOp.EXPORT_ENCRYPTED -> sealEncryptedBundle()
                else -> null
            }
            if (text == null) {
                pendingPassphrase = null
                _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_export_failed)) }
                return@launch
            }
            val ok = withContext(services.dispatchers.io) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("no output stream")
                }.isSuccess
            }
            pendingPassphrase = null
            val msg = if (ok) R.string.msg_export_ok else R.string.msg_export_failed
            diagnostics.log(
                DiagnosticsLog.Category.APP,
                if (ok) "CONFIG_EXPORTED" else "CONFIG_EXPORT_FAILED",
                op.name,
            )
            _state.update { it.copy(transientNotice = appContext.getString(msg)) }
        }
    }

    /** Read and apply a previously exported configuration (plain or encrypted). */
    fun onImportSourceChosen(uri: Uri?, op: FileOp = FileOp.IMPORT_CONFIG) {
        if (uri == null) {
            pendingPassphrase = null
            _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_picker_cancelled)) }
            return
        }
        viewModelScope.launch {
            val readResult = withContext(services.dispatchers.io) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use {
                        BoundedTextInput.readUtf8(it)
                    } ?: throw IOException("no input stream")
                }
            }
            val text = readResult.getOrElse { error ->
                pendingPassphrase = null
                val oversized = error is ImportTooLargeException
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    if (oversized) "CONFIG_IMPORT_TOO_LARGE" else "CONFIG_IMPORT_READ_FAILED",
                    error.javaClass.simpleName,
                )
                _state.update {
                    it.copy(
                        transientNotice = appContext.getString(
                            if (oversized) R.string.msg_import_too_large else R.string.msg_import_read_failed,
                        )
                    )
                }
                return@launch
            }
            if (op == FileOp.IMPORT_ENCRYPTED) {
                applyEncryptedBundle(text)
                return@launch
            }
            when (val parsed = withContext(services.dispatchers.default) { ConfigTransfer.parse(text) }) {
                is ImportResult.Failed -> {
                    diagnostics.log(
                        DiagnosticsLog.Category.APP,
                        "CONFIG_IMPORT_REJECTED",
                        "reason" to parsed.reason.name,
                    )
                    _state.update {
                        it.copy(transientNotice = appContext.getString(importFailureRes(parsed.reason)))
                    }
                }
                is ImportResult.Ok -> {
                    var needsPassword = false
                    updateSettingsAndRefresh(SourceRefreshTrigger.CONFIG_IMPORTED) { current ->
                        val merged = ConfigTransfer.merge(current, parsed.bundle.settings)
                        needsPassword = ConfigTransfer.needsPasswordReentry(merged)
                        merged
                    }
                    diagnostics.log(DiagnosticsLog.Category.APP, "CONFIG_IMPORTED", "")
                    val msg = if (needsPassword) R.string.msg_import_ok_needs_password else R.string.msg_import_ok
                    _state.update { it.copy(transientNotice = appContext.getString(msg)) }
                }
            }
        }
    }

    /** Build the encrypted bundle, reading all portable secrets back out of the Keystore. */
    private suspend fun sealEncryptedBundle(): String? {
        val passphrase = pendingPassphrase ?: return null
        val settings = services.settings.settings.first()
        suspend fun reveal(ref: String?): String? =
            ref?.takeIf { it.isNotBlank() }?.let { services.secretStore.reveal(it) }
        val payload = PortableBundle.Payload(
            settings = settings,
            smbPassword = reveal(settings.source.smb?.credentialRef),
            synologyPassword = reveal(settings.source.synology?.credentialRef),
            webDavPassword = reveal(settings.source.webdav?.credentialRef),
            weatherApiKey = reveal(settings.weather.apiKeyRef),
        )
        return withContext(services.dispatchers.default) {
            PortableBundle.seal(
                payload = payload,
                passphrase = passphrase,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }

    /** Decrypt and apply a portable bundle, re-sealing its secrets under this device's Keystore. */
    private suspend fun applyEncryptedBundle(text: String) {
        val passphrase = pendingPassphrase
        pendingPassphrase = null
        if (passphrase == null) return

        when (val opened = withContext(services.dispatchers.default) {
            PortableBundle.open(text, passphrase)
        }) {
            is PortableBundle.OpenResult.Failed -> {
                diagnostics.log(
                    DiagnosticsLog.Category.APP,
                    "BUNDLE_IMPORT_REJECTED",
                    "reason" to opened.reason.name,
                )
                _state.update {
                    it.copy(transientNotice = appContext.getString(bundleFailureRes(opened.reason)))
                }
            }
            is PortableBundle.OpenResult.Ok -> {
                val payload = opened.payload
                val imported = payload.settings
                val smb = imported.source.smb
                val syn = imported.source.synology
                val dav = imported.source.webdav

                val smbRef = if (smb != null && !payload.smbPassword.isNullOrEmpty()) {
                    CredentialPolicy.smbRef(smb).also {
                        services.secretStore.store(it, "smb_password", payload.smbPassword)
                    }
                } else ""
                val synRef = if (syn != null && !payload.synologyPassword.isNullOrEmpty()) {
                    CredentialPolicy.synologyRef(syn).also {
                        services.secretStore.store(it, "synology_password", payload.synologyPassword)
                    }
                } else ""
                val davRef = if (dav != null && !payload.webDavPassword.isNullOrEmpty()) {
                    CredentialPolicy.webDavRef(dav).also {
                        services.secretStore.store(it, "webdav_password", payload.webDavPassword)
                    }
                } else ""
                val weatherRef = if (!payload.weatherApiKey.isNullOrEmpty()) {
                    CredentialPolicy.WEATHER_API_KEY_REF.also {
                        services.secretStore.store(it, "weather_api_key", payload.weatherApiKey)
                    }
                } else ""

                updateSettingsAndRefresh(SourceRefreshTrigger.ENCRYPTED_BUNDLE_IMPORTED) {
                    imported.copy(
                        source = imported.source.copy(
                            smb = smb?.copy(credentialRef = smbRef),
                            synology = syn?.copy(credentialRef = synRef),
                            webdav = dav?.copy(credentialRef = davRef),
                        ),
                        weather = imported.weather.copy(apiKeyRef = weatherRef),
                    )
                }
                diagnostics.log(DiagnosticsLog.Category.APP, "BUNDLE_IMPORTED", "")
                _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_bundle_import_ok)) }
            }
        }
    }

    private fun bundleFailureRes(reason: PortableBundle.OpenResult.Reason): Int = when (reason) {
        PortableBundle.OpenResult.Reason.WRONG_PASSPHRASE_OR_CORRUPT -> R.string.msg_bundle_wrong_passphrase
        PortableBundle.OpenResult.Reason.TOO_NEW -> R.string.msg_import_too_new
        PortableBundle.OpenResult.Reason.EMPTY_PASSPHRASE -> R.string.msg_passphrase_required
        PortableBundle.OpenResult.Reason.TOO_LARGE -> R.string.msg_import_too_large
        PortableBundle.OpenResult.Reason.UNSAFE_PARAMETERS,
        PortableBundle.OpenResult.Reason.UNSUPPORTED_ALGORITHM -> R.string.msg_bundle_unsafe
        else -> R.string.msg_bundle_invalid
    }

    private fun importFailureRes(reason: ImportResult.Reason): Int = when (reason) {
        ImportResult.Reason.TOO_NEW -> R.string.msg_import_too_new
        ImportResult.Reason.TOO_LARGE -> R.string.msg_import_too_large
        ImportResult.Reason.EMPTY, ImportResult.Reason.NOT_JSON,
        ImportResult.Reason.NOT_A_FRAME_CONFIG -> R.string.msg_import_invalid
    }

    private fun dateStamp(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d%02d%02d".format(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }
}
