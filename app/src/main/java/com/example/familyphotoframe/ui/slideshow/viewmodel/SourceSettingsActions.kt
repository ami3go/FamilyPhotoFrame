package com.example.familyphotoframe.ui.slideshow.viewmodel

import android.net.Uri
import android.os.SystemClock
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.SourceRefreshTrigger
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.settings.ActiveSource
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.CredentialPolicy
import com.example.familyphotoframe.data.settings.SmbSettings
import com.example.familyphotoframe.data.settings.SourceRuntimeSignature
import com.example.familyphotoframe.data.settings.SynologySettings
import com.example.familyphotoframe.data.settings.WebDavSettings
import com.example.familyphotoframe.data.source.CertPinning
import com.example.familyphotoframe.data.source.PhotoSource
import com.example.familyphotoframe.data.source.SmbConnection
import com.example.familyphotoframe.data.source.SmbCredentials
import com.example.familyphotoframe.data.source.SourceHealth
import com.example.familyphotoframe.data.source.SynologyApi
import com.example.familyphotoframe.data.source.SynologyConnection
import com.example.familyphotoframe.data.source.SynologyCredentials
import com.example.familyphotoframe.data.source.WebDavApi
import com.example.familyphotoframe.data.source.WebDavConnection
import com.example.familyphotoframe.data.source.WebDavCredentials
import com.example.familyphotoframe.domain.engine.SourceStatusPolicy
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Source connection settings: saving and testing SMB, Synology and WebDAV sources,
 * Synology certificate approval, and choosing the primary and co-primary sources.
 * Saving goes through [updateSettingsAndRefresh] so the source pipeline re-activates.
 * Results land in [SlideshowUiState.smbTestResult] or as transient notices.
 */
internal class SourceSettingsActions(
    host: SlideshowFeatureHost,
    private val updateSettingsAndRefresh:
        suspend (SourceRefreshTrigger, (AppSettings) -> AppSettings) -> Unit,
    /** Hands a Synology 2FA code to the next login; it is never persisted. */
    private val setPendingSynologyOtp: (String?) -> Unit,
) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics

    /** Web API: health-check the saved source without activating it (spec §15.4). */
    suspend fun testSavedSource(): String {
        val s = services.settings.settings.first()
        val configRevision = diagnosticToken(SourceRuntimeSignature.of(s), "config")
        return when (s.source.kind) {
            ActiveSourceKind.SMB -> {
                val smb = s.source.smb ?: return appContext.getString(R.string.msg_smb_missing)
                val password = services.secretStore.reveal(smb.credentialRef) ?: ""
                val src = services.smbSource(
                    SmbConnection(smb.host, smb.share, smb.path),
                    SmbCredentials(smb.domain, smb.user, password),
                )
                // One-shot test: release the session instead of pooling it.
                try {
                    appContext.getString(smbHealthMessageRes(sourceTestWithDiagnostics(
                        src, "SMB", 8_000, DiagnosticOrigin.WEB_UI, configRevision,
                    )))
                } finally {
                    runCatching { src.shutdown() }
                }
            }
            ActiveSourceKind.SYNOLOGY -> {
                val syn = s.source.synology ?: return appContext.getString(R.string.msg_syn_error)
                val password = services.secretStore.reveal(syn.credentialRef) ?: ""
                val src = services.synologySource(
                    SynologyConnection(
                        baseUrl = syn.baseUrl,
                        folderPath = syn.folderPath,
                        useThumbnails = syn.useThumbnails,
                        thumbnailSize = syn.thumbnailSize,
                        pinnedCertSha256 = syn.pinnedCertSha256,
                    ),
                    SynologyCredentials(syn.user, password),
                )
                try {
                    appContext.getString(synologyHealthMessageRes(sourceTestWithDiagnostics(
                        src, "SYNOLOGY", 10_000, DiagnosticOrigin.WEB_UI, configRevision,
                    )))
                } finally {
                    runCatching { src.shutdown() }
                }
            }
            ActiveSourceKind.LOCAL_SAF -> {
                val uri = s.source.treeUri ?: return appContext.getString(R.string.msg_folder_missing)
                val saf = services.safSource(Uri.parse(uri))
                when (sourceTestWithDiagnostics(
                    saf, "LOCAL_SAF", 5_000, DiagnosticOrigin.WEB_UI, configRevision,
                )) {
                    is SourceHealth.Ok -> appContext.getString(R.string.web_source_ok)
                    is SourceHealth.NeedsPermission -> appContext.getString(R.string.msg_permission_revoked)
                    is SourceHealth.Missing -> appContext.getString(R.string.msg_folder_missing)
                    else -> appContext.getString(R.string.msg_provider_error)
                }
            }
            ActiveSourceKind.WEBDAV -> {
                val dav = s.source.webdav ?: return appContext.getString(R.string.msg_dav_error)
                val password = services.secretStore.reveal(dav.credentialRef) ?: ""
                val src = services.webDavSource(
                    WebDavConnection(
                        baseUrl = dav.baseUrl,
                        rootPath = dav.rootPath,
                        folderPath = dav.folderPath,
                        pinnedCertSha256 = dav.pinnedCertSha256,
                    ),
                    WebDavCredentials(dav.user, password),
                )
                appContext.getString(webDavHealthMessageRes(sourceTestWithDiagnostics(
                    src, "WEBDAV", 10_000, DiagnosticOrigin.WEB_UI, configRevision,
                )))
            }
            ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES ->
                appContext.getString(R.string.web_source_samples)
        }
    }

    private suspend fun sourceTestWithDiagnostics(
        source: PhotoSource,
        sourceKind: String,
        timeoutMs: Long,
        origin: DiagnosticOrigin,
        configRevision: String,
    ): SourceHealth {
        val operation = diagnostics.operations.start("SOURCE_TEST", origin)
        val started = SystemClock.elapsedRealtime()
        val base = mapOf(
            "sourceKind" to sourceKind,
            "trigger" to "SOURCE_TEST",
            "configRevision" to configRevision,
            "timeoutMs" to timeoutMs.toString(),
            "stage" to "TESTING",
        )
        diagnostics.logEvent("SOURCE_TEST_STARTED", base, operation.context())
        try {
            val health = source.healthCheck(timeoutMs)
            diagnostics.logEvent(
                if (health is SourceHealth.Ok) "SOURCE_TEST_COMPLETED" else "SOURCE_TEST_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "healthState" to healthStateCode(health),
                    "outcome" to if (health is SourceHealth.Ok) "HEALTHY" else "UNAVAILABLE",
                    "errorCode" to healthErrorCode(health),
                ),
                operation.context(),
            )
            return health
        } catch (cancelled: CancellationException) {
            diagnostics.logEvent(
                "SOURCE_TEST_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "outcome" to "CANCELLED",
                    "errorCode" to "CANCELLED",
                    "cancellationReason" to "CALLER_CANCELLED",
                ),
                operation.context(),
            )
            throw cancelled
        } catch (error: Exception) {
            diagnostics.logEvent(
                "SOURCE_TEST_FAILED",
                base + mapOf(
                    "durationMs" to (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L).toString(),
                    "outcome" to "FAILED",
                    "errorClass" to error.javaClass.simpleName,
                    "errorCode" to "SOURCE_TEST_EXCEPTION",
                ),
                operation.context(),
            )
            throw error
        } finally {
            diagnostics.operations.finish(operation.operationId)
        }
    }

    /** Save an SMB source (password encrypted via SecretStore) and make it active. */
    fun saveSmb(host: String, share: String, path: String, user: String, domain: String, password: String) {
        viewModelScope.launch {
            val draft = SmbSettings(
                host = host.trim(),
                share = share.trim().trim('/'),
                path = path.trim().trim('/'),
                user = user.trim(),
                domain = domain.trim(),
            )
            val current = services.settings.settings.first()
            val existing = current.source.smb
            val retainedRef = existing?.credentialRef.orEmpty()
                .takeIf { it.isNotBlank() && CredentialPolicy.sameSmbScope(existing, draft) }
            val ref = retainedRef ?: CredentialPolicy.smbRef(draft)
            val resolvedPassword = password.takeIf { it.isNotEmpty() }
                ?: retainedRef?.let { services.secretStore.reveal(it) }
            if (resolvedPassword == null && draft.user.isNotBlank()) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_password_required))
                }
                return@launch
            }
            val savedRef = if (resolvedPassword == null) "" else ref // anonymous/guest SMB
            if (resolvedPassword != null && (password.isNotEmpty() || retainedRef == null)) {
                services.secretStore.store(ref, "smb_password", resolvedPassword)
            }
            val trigger = if (password.isNotEmpty()) {
                SourceRefreshTrigger.CREDENTIAL_UPDATED
            } else {
                SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
            }
            updateSettingsAndRefresh(trigger) {
                it.copy(
                    source = it.source.copy(
                        kind = ActiveSourceKind.SMB,
                        displayName = "${draft.host}/${draft.share}",
                        smb = draft.copy(credentialRef = savedRef),
                    )
                )
            }
        }
    }

    /** Test an SMB connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testSmb(host: String, share: String, path: String, user: String, domain: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(smbTestResult = appContext.getString(R.string.msg_smb_testing)) }
            val draft = SmbSettings(
                host = host.trim(),
                share = share.trim().trim('/'),
                path = path.trim().trim('/'),
                user = user.trim(),
                domain = domain.trim(),
            )
            val existing = services.settings.settings.first().source.smb
            val effectivePassword = password.takeIf { it.isNotEmpty() }
                ?: existing?.credentialRef
                    ?.takeIf { it.isNotBlank() && CredentialPolicy.sameSmbScope(existing, draft) }
                    ?.let { services.secretStore.reveal(it) }
                    .orEmpty()
            val src = services.smbSource(
                SmbConnection(draft.host, draft.share, draft.path),
                SmbCredentials(draft.domain, draft.user, effectivePassword),
            )
            // A connection test is one-shot: close the session rather than leaving it
            // pooled, or every press of Test leaks a context.
            val res = try {
                smbHealthMessageRes(sourceTestWithDiagnostics(
                    src,
                    "SMB",
                    8_000,
                    DiagnosticOrigin.ANDROID_UI,
                    diagnosticToken(draft.toString(), "config"),
                ))
            } finally {
                runCatching { src.shutdown() }
            }
            _state.update { it.copy(smbTestResult = appContext.getString(res)) }
        }
    }

    /** User-facing message for an SMB [SourceHealth] (shared by activate + test). */
    private fun smbHealthMessageRes(health: SourceHealth): Int = when (health) {
        is SourceHealth.Ok -> R.string.msg_smb_ok
        is SourceHealth.NeedsPermission -> R.string.msg_smb_auth
        is SourceHealth.Missing -> R.string.msg_smb_missing
        is SourceHealth.Unavailable -> R.string.msg_smb_unreachable
        is SourceHealth.ProviderError -> R.string.msg_smb_error
    }

    fun clearSmbTestResult() = _state.update { it.copy(smbTestResult = null) }

    // ---- Synology File Station (ROADMAP.md network photo-app sources) ----

    /**
     * Save and activate a Synology source. [otpCode] is the optional 2FA one-time code;
     * it is kept in memory for the immediately following login only and never persisted.
     */
    fun saveSynology(
        baseUrl: String,
        folderPath: String,
        user: String,
        password: String,
        otpCode: String,
        useThumbnails: Boolean,
        pinnedCertSha256: String? = null,
    ) {
        viewModelScope.launch {
            val current = services.settings.settings.first()
            val existing = current.source.synology
            val host = SynologyApi.normalizeBaseUrl(baseUrl)
            val draft = SynologySettings(
                baseUrl = host,
                folderPath = folderPath.trim().ifBlank { "/photo" },
                user = user.trim(),
                useThumbnails = useThumbnails,
                thumbnailSize = existing?.thumbnailSize ?: "large",
                pinnedCertSha256 = if (CredentialPolicy.sameSynologyHost(existing, SynologySettings(baseUrl = host))) {
                    pinnedCertSha256 ?: existing?.pinnedCertSha256
                } else {
                    pinnedCertSha256?.takeIf(CertPinning::isValidSha256)
                },
            )
            val retainedRef = existing?.credentialRef.orEmpty()
                .takeIf { it.isNotBlank() && CredentialPolicy.sameSynologyScope(existing, draft) }
            val ref = retainedRef ?: CredentialPolicy.synologyRef(draft)
            val resolvedPassword = password.takeIf { it.isNotEmpty() }
                ?: retainedRef?.let { services.secretStore.reveal(it) }
            if (resolvedPassword.isNullOrEmpty()) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_password_required))
                }
                return@launch
            }
            if (password.isNotEmpty() || retainedRef == null) {
                services.secretStore.store(ref, "synology_password", resolvedPassword)
            }
            setPendingSynologyOtp(otpCode.trim().ifBlank { null })
            val trigger = if (password.isNotEmpty()) {
                SourceRefreshTrigger.CREDENTIAL_UPDATED
            } else {
                SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
            }
            updateSettingsAndRefresh(trigger) {
                it.copy(
                    source = it.source.copy(
                        kind = ActiveSourceKind.SYNOLOGY,
                        displayName = host,
                        synology = draft.copy(credentialRef = ref),
                    )
                )
            }
        }
    }

    /** Test a Synology connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testSynology(
        baseUrl: String,
        folderPath: String,
        user: String,
        password: String,
        otpCode: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(smbTestResult = appContext.getString(R.string.msg_syn_testing)) }
            val draft = SynologySettings(
                baseUrl = SynologyApi.normalizeBaseUrl(baseUrl),
                folderPath = folderPath.trim().ifBlank { "/photo" },
                user = user.trim(),
            )
            val existing = services.settings.settings.first().source.synology
            val effectivePassword = password.takeIf { it.isNotEmpty() }
                ?: existing?.credentialRef
                    ?.takeIf { it.isNotBlank() && CredentialPolicy.sameSynologyScope(existing, draft) }
                    ?.let { services.secretStore.reveal(it) }
                    .orEmpty()
            val src = services.synologySource(
                SynologyConnection(baseUrl = draft.baseUrl, folderPath = draft.folderPath),
                SynologyCredentials(draft.user, effectivePassword, otpCode.trim().ifBlank { null }),
            )
            val res = try {
                synologyHealthMessageRes(sourceTestWithDiagnostics(
                    src,
                    "SYNOLOGY",
                    10_000,
                    DiagnosticOrigin.ANDROID_UI,
                    diagnosticToken(draft.toString(), "config"),
                ))
            } finally {
                runCatching { src.shutdown() }
            }
            _state.update { it.copy(smbTestResult = appContext.getString(res)) }
        }
    }

    /**
     * Looks up the certificate the NAS presents so the user can approve it. Populates
     * [SlideshowUiState.synologyCertFingerprint]; null clears any previous offer.
     */
    fun probeSynologyCertificate(baseUrl: String) {
        viewModelScope.launch {
            val src = services.synologySource(
                SynologyConnection(baseUrl = SynologyApi.normalizeBaseUrl(baseUrl)),
                SynologyCredentials("", ""),
            )
            try {
                _state.update { it.copy(synologyCertFingerprint = src.probeCertificateFingerprint()) }
            } finally {
                runCatching { src.shutdown() }
            }
        }
    }

    /**
     * Records a certificate fingerprint the user has explicitly approved. Deliberately a
     * separate, explicit action rather than something the test/save flow does silently —
     * approving a certificate is a security decision and must be a deliberate one.
     */
    fun trustSynologyCertificate(fingerprint: String) {
        if (!CertPinning.isValidSha256(fingerprint)) return
        viewModelScope.launch {
            services.settings.update { s ->
                val syn = s.source.synology ?: return@update s
                s.copy(source = s.source.copy(synology = syn.copy(pinnedCertSha256 = fingerprint)))
            }
            _state.update { it.copy(synologyCertFingerprint = null) }
            diagnostics.log(DiagnosticsLog.Category.SOURCE, "SYNOLOGY_CERT_PINNED")
        }
    }

    /** Removes a previously approved certificate, returning to strict platform validation. */
    fun clearSynologyCertificate() {
        viewModelScope.launch {
            services.settings.update { s ->
                val syn = s.source.synology ?: return@update s
                s.copy(source = s.source.copy(synology = syn.copy(pinnedCertSha256 = null)))
            }
            diagnostics.log(DiagnosticsLog.Category.SOURCE, "SYNOLOGY_CERT_UNPINNED")
        }
    }

    /** User-facing message for a Synology [SourceHealth]; 2FA gets its own hint. */
    private fun synologyHealthMessageRes(health: SourceHealth): Int = when (health) {
        is SourceHealth.Ok -> R.string.msg_syn_ok
        is SourceHealth.NeedsPermission -> R.string.msg_syn_permission
        is SourceHealth.Missing -> R.string.msg_syn_folder_missing
        is SourceHealth.Unavailable -> R.string.msg_syn_unreachable
        is SourceHealth.ProviderError -> when (health.detail) {
            "two_factor_required" -> R.string.msg_syn_2fa
            "auth_failed" -> R.string.msg_syn_auth
            "CertUntrusted" -> R.string.msg_syn_cert
            "HostUnreachable" -> R.string.msg_syn_unreachable
            else -> R.string.msg_syn_error
        }
    }

    // ---- WebDAV / Nextcloud (ROADMAP.md other candidate sources) ----------------

    /**
     * Save and activate a WebDAV source. An empty [password] keeps the stored secret
     * when the host and account are unchanged, exactly as for SMB and Synology.
     *
     * When [rootPath] is blank the standard Nextcloud endpoint for [user] is assumed,
     * which is right for the overwhelmingly common case and still overridable for a
     * plain Apache mount.
     */
    fun saveWebDav(
        baseUrl: String,
        rootPath: String,
        folderPath: String,
        user: String,
        password: String,
        pinnedCertSha256: String? = null,
    ) {
        viewModelScope.launch {
            val current = services.settings.settings.first()
            val existing = current.source.webdav
            val host = WebDavApi.normalizeBaseUrl(baseUrl)
            val trimmedUser = user.trim()
            val draft = WebDavSettings(
                baseUrl = host,
                rootPath = rootPath.trim().ifBlank { WebDavApi.nextcloudFilesRoot(trimmedUser) },
                folderPath = WebDavApi.normalizePath(folderPath),
                user = trimmedUser,
                pinnedCertSha256 = if (CredentialPolicy.sameWebDavHost(existing, WebDavSettings(baseUrl = host))) {
                    pinnedCertSha256 ?: existing?.pinnedCertSha256
                } else {
                    pinnedCertSha256?.takeIf(CertPinning::isValidSha256)
                },
            )
            val retainedRef = existing?.credentialRef.orEmpty()
                .takeIf { it.isNotBlank() && CredentialPolicy.sameWebDavScope(existing, draft) }
            val ref = retainedRef ?: CredentialPolicy.webDavRef(draft)
            val resolvedPassword = password.takeIf { it.isNotEmpty() }
                ?: retainedRef?.let { services.secretStore.reveal(it) }
            if (resolvedPassword.isNullOrEmpty()) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_password_required))
                }
                return@launch
            }
            if (password.isNotEmpty() || retainedRef == null) {
                services.secretStore.store(ref, "webdav_password", resolvedPassword)
            }
            val trigger = if (password.isNotEmpty()) {
                SourceRefreshTrigger.CREDENTIAL_UPDATED
            } else {
                SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED
            }
            updateSettingsAndRefresh(trigger) {
                it.copy(
                    source = it.source.copy(
                        kind = ActiveSourceKind.WEBDAV,
                        displayName = host,
                        webdav = draft.copy(credentialRef = ref),
                    )
                )
            }
        }
    }

    /** Test a WebDAV connection without saving it; result shown in [SlideshowUiState.smbTestResult]. */
    fun testWebDav(
        baseUrl: String,
        rootPath: String,
        folderPath: String,
        user: String,
        password: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(smbTestResult = appContext.getString(R.string.msg_dav_testing)) }
            val trimmedUser = user.trim()
            val draft = WebDavSettings(
                baseUrl = WebDavApi.normalizeBaseUrl(baseUrl),
                rootPath = rootPath.trim().ifBlank { WebDavApi.nextcloudFilesRoot(trimmedUser) },
                folderPath = WebDavApi.normalizePath(folderPath),
                user = trimmedUser,
            )
            val existing = services.settings.settings.first().source.webdav
            val effectivePassword = password.takeIf { it.isNotEmpty() }
                ?: existing?.credentialRef
                    ?.takeIf { it.isNotBlank() && CredentialPolicy.sameWebDavScope(existing, draft) }
                    ?.let { services.secretStore.reveal(it) }
                    .orEmpty()
            val src = services.webDavSource(
                WebDavConnection(
                    baseUrl = draft.baseUrl,
                    rootPath = draft.rootPath,
                    folderPath = draft.folderPath,
                    pinnedCertSha256 = existing?.pinnedCertSha256,
                ),
                WebDavCredentials(draft.user, effectivePassword),
            )
            val res = webDavHealthMessageRes(sourceTestWithDiagnostics(
                src,
                "WEBDAV",
                10_000,
                DiagnosticOrigin.ANDROID_UI,
                diagnosticToken(draft.toString(), "config"),
            ))
            _state.update { it.copy(smbTestResult = appContext.getString(res)) }
        }
    }

    /** User-facing message for a WebDAV [SourceHealth]; "not WebDAV" gets its own hint. */
    private fun webDavHealthMessageRes(health: SourceHealth): Int = when (health) {
        is SourceHealth.Ok -> R.string.msg_dav_ok
        is SourceHealth.NeedsPermission -> R.string.msg_dav_permission
        is SourceHealth.Missing -> R.string.msg_dav_folder_missing
        is SourceHealth.Unavailable -> R.string.msg_dav_unreachable
        is SourceHealth.ProviderError -> when (health.detail) {
            "auth_failed" -> R.string.msg_dav_auth
            "not_webdav" -> R.string.msg_dav_not_webdav
            "CertUntrusted" -> R.string.msg_dav_cert
            "HostUnreachable" -> R.string.msg_dav_unreachable
            else -> R.string.msg_dav_error
        }
    }

    /**
     * Add or remove a co-primary source, so a local folder and a NAS (or two NAS
     * protocols) can feed one slideshow.
     *
     * Refuses to merge in a kind that has no connection settings yet: it would silently
     * contribute nothing, and "I ticked it and nothing happened" is a worse outcome than
     * being told to configure it first.
     */
    fun setAlsoPlay(kind: ActiveSourceKind, enabled: Boolean) {
        viewModelScope.launch {
            val current = services.settings.settings.first().source
            if (enabled && !isConfigured(kind, current)) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_not_configured))
                }
                return@launch
            }
            services.settings.update { s ->
                val next = if (enabled) s.source.alsoPlay + kind else s.source.alsoPlay - kind
                // The chosen source is already a primary; listing it again would just
                // duplicate it in the pool.
                s.copy(source = s.source.copy(alsoPlay = next - s.source.kind))
            }
        }
    }

    private fun isConfigured(kind: ActiveSourceKind, source: ActiveSource): Boolean =
        SourceStatusPolicy.isConfigured(kind, source)

    /**
     * Promote an already-configured source to primary without retyping its connection.
     * Samples are allowed here too, which is what the "use samples" button does.
     */
    fun setPrimarySource(kind: ActiveSourceKind) {
        viewModelScope.launch {
            val current = services.settings.settings.first().source
            if (kind == current.kind) return@launch
            if (kind != ActiveSourceKind.SAMPLES && !isConfigured(kind, current)) {
                _state.update {
                    it.copy(transientNotice = appContext.getString(R.string.msg_source_not_configured))
                }
                return@launch
            }
            updateSettingsAndRefresh(SourceRefreshTrigger.SOURCE_SETTINGS_CHANGED) { s ->
                s.copy(
                    source = s.source.copy(
                        kind = kind,
                        displayName = primaryDisplayName(kind, s.source),
                        // A primary listed again as a co-primary would duplicate the pool.
                        alsoPlay = s.source.alsoPlay - kind,
                    )
                )
            }
        }
    }

    private fun primaryDisplayName(kind: ActiveSourceKind, source: ActiveSource): String = when (kind) {
        // The SAF display name is captured when the folder is picked and cannot be rebuilt.
        ActiveSourceKind.LOCAL_SAF -> source.displayName
        ActiveSourceKind.SMB -> source.smb?.let { "${it.host}/${it.share}" }.orEmpty()
        ActiveSourceKind.SYNOLOGY -> source.synology?.baseUrl.orEmpty()
        ActiveSourceKind.WEBDAV -> source.webdav?.baseUrl.orEmpty()
        ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> ""
    }
}
