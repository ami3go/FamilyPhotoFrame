package com.example.familyphotoframe.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable configuration bundle for SAF export/import (spec §7.0
 * `saf_config_import_export`). Written to a user-chosen document so a frame can be
 * backed up, cloned, or restored after a factory reset.
 *
 * Secrets are **never** included (Contract Rule 5). NAS passwords and the weather API
 * key live only in the Keystore, and even the references that point at them are stripped
 * on export — a reference is meaningless on another device. Importing a protected source
 * therefore requires re-entering its password unless the same credential scope is already
 * configured on this device.
 */
@Serializable
data class ConfigBundle(
    val kind: String = KIND,
    val bundleVersion: Int = BUNDLE_VERSION,
    val exportedAtEpochMs: Long = 0L,
    val appVersion: String = "",
    val settings: AppSettings = AppSettings(),
) {
    companion object {
        const val KIND = "family_photo_frame_config"
        const val BUNDLE_VERSION = 1
    }
}

/** Outcome of parsing an imported document, with a user-facing reason on failure. */
sealed interface ImportResult {
    data class Ok(val bundle: ConfigBundle) : ImportResult
    data class Failed(val reason: Reason) : ImportResult

    enum class Reason { NOT_JSON, NOT_A_FRAME_CONFIG, TOO_NEW, TOO_LARGE, EMPTY }
}

object ConfigTransfer {

    const val MAX_IMPORT_BYTES: Int = BoundedTextInput.MAX_IMPORT_BYTES

    private val json = Json {
        ignoreUnknownKeys = true      // tolerate configs from newer minor builds
        encodeDefaults = true
        prettyPrint = true
    }

    /** Serialize current settings for export, with all credential references stripped. */
    fun export(settings: AppSettings, appVersion: String, nowEpochMs: Long): String {
        val bundle = ConfigBundle(
            exportedAtEpochMs = nowEpochMs,
            appVersion = appVersion,
            settings = redact(settings),
        )
        return json.encodeToString(ConfigBundle.serializer(), bundle)
    }

    /**
     * Remove anything that points at a secret.
     *
     * Every credential-bearing source must be handled here. This deliberately does NOT
     * early-return on a null `smb`: doing so would silently export a Synology
     * `credentialRef` untouched once a second source type existed.
     */
    fun redact(settings: AppSettings): AppSettings = settings.copy(
        source = settings.source.copy(
            smb = settings.source.smb?.copy(credentialRef = ""),
            synology = settings.source.synology?.copy(credentialRef = ""),
            webdav = settings.source.webdav?.copy(credentialRef = ""),
        ),
        weather = settings.weather.copy(apiKeyRef = ""),
    )

    /** Parse and validate an exported document. */
    fun parse(text: String): ImportResult {
        if (text.isBlank()) return ImportResult.Failed(ImportResult.Reason.EMPTY)
        // File imports enforce the exact UTF-8 byte limit while reading. This character
        // guard also protects non-file callers before the JSON parser allocates a tree.
        if (text.length > MAX_IMPORT_BYTES) return ImportResult.Failed(ImportResult.Reason.TOO_LARGE)
        val bundle = runCatching { json.decodeFromString(ConfigBundle.serializer(), text) }
            .getOrElse { return ImportResult.Failed(ImportResult.Reason.NOT_JSON) }

        if (bundle.kind != ConfigBundle.KIND) {
            return ImportResult.Failed(ImportResult.Reason.NOT_A_FRAME_CONFIG)
        }
        if (bundle.bundleVersion > ConfigBundle.BUNDLE_VERSION) {
            return ImportResult.Failed(ImportResult.Reason.TOO_NEW)
        }
        return ImportResult.Ok(
            bundle.copy(settings = bundle.settings.withCurrentDefaults()),
        )
    }

    /**
     * Merge an imported configuration onto the device's [current] settings.
     *
     * Imported values win, except for secret references. A NAS reference is preserved
     * only when the imported server/account has the same credential scope already
     * configured here. The weather key reference is preserved only for the same endpoint.
     * Any other case clears the reference rather than attaching an old secret to a new target.
     */
    fun merge(current: AppSettings, imported: AppSettings): AppSettings {
        val mergedSmb = imported.source.smb?.let { importedSmb ->
            val currentSmb = current.source.smb
            val sameScope = CredentialPolicy.sameSmbScope(currentSmb, importedSmb)
            importedSmb.copy(credentialRef = if (sameScope) currentSmb?.credentialRef.orEmpty() else "")
        }
        val mergedSynology = imported.source.synology?.let { importedSyn ->
            val currentSyn = current.source.synology
            val sameScope = CredentialPolicy.sameSynologyScope(currentSyn, importedSyn)
            val sameHost = CredentialPolicy.sameSynologyHost(currentSyn, importedSyn)
            importedSyn.copy(
                credentialRef = if (sameScope) currentSyn?.credentialRef.orEmpty() else "",
                // A pinned certificate belongs to a host, not an account. Preserve only
                // a pin that THIS device already approved for the same host; never accept
                // a pin claimed by the imported file.
                pinnedCertSha256 = if (sameHost) currentSyn?.pinnedCertSha256 else null,
            )
        }
        val mergedWebDav = imported.source.webdav?.let { importedDav ->
            val currentDav = current.source.webdav
            importedDav.copy(
                credentialRef = if (CredentialPolicy.sameWebDavScope(currentDav, importedDav)) {
                    currentDav?.credentialRef.orEmpty()
                } else "",
                // As for Synology: a pin belongs to a host this device approved, and is
                // never accepted from the imported file.
                pinnedCertSha256 = if (CredentialPolicy.sameWebDavHost(currentDav, importedDav)) {
                    currentDav?.pinnedCertSha256
                } else null,
            )
        }
        val sameWeatherEndpoint = current.weather.endpointBaseUrl.trim().trimEnd('/')
            .equals(imported.weather.endpointBaseUrl.trim().trimEnd('/'), ignoreCase = true)
        val mergedWeather = imported.weather.copy(
            apiKeyRef = if (sameWeatherEndpoint) current.weather.apiKeyRef else "",
        )
        return imported.copy(
            source = imported.source.copy(
                smb = mergedSmb, synology = mergedSynology, webdav = mergedWebDav,
            ),
            weather = mergedWeather,
        )
    }

    /** True when the merged config points at a source whose stored password is missing. */
    fun needsPasswordReentry(merged: AppSettings): Boolean = when (merged.source.kind) {
        ActiveSourceKind.SMB -> merged.source.smb?.credentialRef.isNullOrEmpty()
        ActiveSourceKind.SYNOLOGY -> merged.source.synology?.credentialRef.isNullOrEmpty()
        ActiveSourceKind.WEBDAV -> merged.source.webdav?.credentialRef.isNullOrEmpty()
        else -> false
    }

    /** Suggested export filename, e.g. `photo-frame-config-20260724.json`. */
    fun suggestedFileName(dateStamp: String): String = "photo-frame-config-$dateStamp.json"
}
