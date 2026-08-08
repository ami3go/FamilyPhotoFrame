package com.example.familyphotoframe.data.settings

import com.example.familyphotoframe.util.toHexString
import java.security.MessageDigest
import java.util.Locale

/**
 * Defines when a stored credential is safe to reuse and how device-local secret
 * references are generated. Paths/folders are deliberately excluded from the scope:
 * changing a folder on the same account must not force the user to re-enter a password.
 */
object CredentialPolicy {
    const val WEATHER_API_KEY_REF = "cred_weather_api"

    fun sameSmbScope(a: SmbSettings?, b: SmbSettings): Boolean = a != null &&
        a.host.trim().equals(b.host.trim(), ignoreCase = true) &&
        a.share.trim('/').equals(b.share.trim('/'), ignoreCase = true) &&
        a.user.trim() == b.user.trim() &&
        a.domain.trim().equals(b.domain.trim(), ignoreCase = true)

    fun sameSynologyHost(a: SynologySettings?, b: SynologySettings): Boolean = a != null &&
        normalizeBaseUrl(a.baseUrl) == normalizeBaseUrl(b.baseUrl)

    fun sameSynologyScope(a: SynologySettings?, b: SynologySettings): Boolean =
        sameSynologyHost(a, b) && a?.user?.trim() == b.user.trim()

    fun sameWebDavHost(a: WebDavSettings?, b: WebDavSettings): Boolean = a != null &&
        normalizeBaseUrl(a.baseUrl) == normalizeBaseUrl(b.baseUrl)

    /**
     * WebDAV credential scope is host + user. The DAV root is excluded along with the
     * folder: on Nextcloud the root is derived from the account, so treating a root
     * change as a new account would force a needless password re-entry.
     */
    fun sameWebDavScope(a: WebDavSettings?, b: WebDavSettings): Boolean =
        sameWebDavHost(a, b) && a?.user?.trim() == b.user.trim()

    fun webDavRef(settings: WebDavSettings): String = stableRef(
        prefix = "cred_dav_",
        material = normalizeBaseUrl(settings.baseUrl) + "\u0000" + settings.user.trim(),
    )

    fun smbRef(settings: SmbSettings): String = stableRef(
        prefix = "cred_smb_",
        material = listOf(
            settings.host.trim().lowercase(Locale.ROOT),
            settings.share.trim('/').lowercase(Locale.ROOT),
            settings.domain.trim().lowercase(Locale.ROOT),
            settings.user.trim(),
        ).joinToString("\u0000"),
    )

    fun synologyRef(settings: SynologySettings): String = stableRef(
        prefix = "cred_syn_",
        material = normalizeBaseUrl(settings.baseUrl) + "\u0000" + settings.user.trim(),
    )

    private fun normalizeBaseUrl(value: String): String =
        value.trim().trimEnd('/').lowercase(Locale.ROOT)

    private fun stableRef(prefix: String, material: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .toHexString()
        return prefix + digest.take(24)
    }
}
