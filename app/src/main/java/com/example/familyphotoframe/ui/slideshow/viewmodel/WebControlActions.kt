package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.ui.slideshow.RememberedBrowserUi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Web control server settings, pairing PIN, and remembered-browser management (spec §15). */
internal class WebControlActions(host: SlideshowFeatureHost) {
    private val services = host.services
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val webPinRegenerationInFlight = AtomicBoolean(false)

    fun setWebEnabled(value: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(web = it.web.copy(enabled = value)) }
        }
    }


    fun refreshRememberedBrowsers() {
        viewModelScope.launch {
            val records = runCatching { services.rememberedBrowsers.list() }.getOrDefault(emptyList())
            _state.update { current ->
                current.copy(
                    rememberedBrowserRecords = records.map { row ->
                        RememberedBrowserUi(
                            id = row.id,
                            label = row.label,
                            browserSummary = row.browserSummary.orEmpty(),
                            osSummary = row.osSummary.orEmpty(),
                            createdAtEpochMs = row.createdAtEpochMs,
                            lastUsedAtEpochMs = row.lastUsedAtEpochMs,
                            expiresAtEpochMs = row.expiresAtEpochMs,
                            revoked = row.revokedAtEpochMs != null,
                        )
                    }
                )
            }
        }
    }

    private fun updateRememberedPolicy(
        transform: (com.example.familyphotoframe.data.settings.RememberedBrowserPolicy) ->
            com.example.familyphotoframe.data.settings.RememberedBrowserPolicy,
    ) {
        viewModelScope.launch {
            val current = services.rememberedBrowsers.policy()
            services.rememberedBrowsers.updatePolicy(transform(current).normalized())
            refreshRememberedBrowsers()
        }
    }

    fun setRememberedBrowsersEnabled(enabled: Boolean) =
        updateRememberedPolicy { it.copy(enabled = enabled) }

    fun setRememberedAllowForever(allowed: Boolean) =
        updateRememberedPolicy { it.copy(allowForever = allowed) }

    fun setRememberedDefaultExpiry(mode: com.example.familyphotoframe.data.settings.RememberExpiryMode) =
        updateRememberedPolicy { it.copy(defaultExpiry = mode) }

    fun setRememberedMaximumBrowsers(count: Int) =
        updateRememberedPolicy { it.copy(maxRememberedBrowsers = count.coerceIn(1, 32)) }

    fun setRememberedMaximumExpiryDays(days: Int) =
        updateRememberedPolicy {
            it.copy(maxExpirySeconds = days.coerceIn(1, 3650).toLong() * 24L * 60L * 60L)
        }

    fun revokeRememberedBrowser(id: String) {
        viewModelScope.launch {
            if (services.rememberedBrowsers.revoke(id)) {
                services.webServer.revokeRememberedBrowserSessions(id)
            }
            refreshRememberedBrowsers()
        }
    }

    fun revokeAllRememberedBrowsers() {
        viewModelScope.launch {
            services.rememberedBrowsers.revokeAll()
            services.webServer.revokeAllWebSessions()
            refreshRememberedBrowsers()
        }
    }

    fun setWebPort(port: Int) {
        viewModelScope.launch {
            services.settings.update { it.copy(web = it.web.copy(port = port)) }
        }
    }

    /** Invalidate web sessions and show a new pairing PIN (spec §15.2). */
    fun regenerateWebPin(keepRememberedBrowsers: Boolean = false) {
        // The PBKDF2 derivation is deliberately expensive. This intent is invoked from a
        // Compose button, so never do that work (or remembered-browser Room work) inline on
        // the main thread. The CAS also closes the brief window before Compose disables the
        // button, so repeated taps produce one new PIN rather than a queue of derivations.
        if (!webPinRegenerationInFlight.compareAndSet(false, true)) return
        _state.update { it.copy(webPinRegenerationInProgress = true) }
        viewModelScope.launch {
            try {
                val generated = try {
                    withContext(services.dispatchers.default) {
                        services.webServer.regeneratePin(
                            revokeRememberedBrowsers = !keepRememberedBrowsers,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                refreshWebInfo()
                refreshRememberedBrowsers()
                if (generated == null && services.webServer.url() != null) {
                    _state.update {
                        it.copy(transientNotice = "Couldn’t generate a new web PIN.")
                    }
                }
            } finally {
                webPinRegenerationInFlight.set(false)
                _state.update { it.copy(webPinRegenerationInProgress = false) }
            }
        }
    }

    /** Pull the current PIN/URL from the server into UI state. */
    fun refreshWebInfo() {
        _state.update {
            it.copy(
                webPin = services.webServer.visiblePin(),
                webUrl = services.webServer.url(),
                webQrUrl = services.webServer.qrPairingUrl(),
            )
        }
    }
}
