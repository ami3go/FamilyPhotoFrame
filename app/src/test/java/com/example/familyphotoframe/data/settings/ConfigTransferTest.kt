package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies SAF config export/import (spec §7.0) and, most importantly, that nothing
 * pointing at a secret ever leaves the device (Contract Rule 5).
 */
class ConfigTransferTest {

    private fun smbSettings() = AppSettings(
        intervalSeconds = 42,
        aspectMode = AspectMode.FILL_CROP,
        source = ActiveSource(
            kind = ActiveSourceKind.SMB,
            displayName = "nas/Photos",
            smb = SmbSettings(
                host = "192.168.1.10", share = "Photos", path = "2024",
                user = "frame", domain = "WORKGROUP", credentialRef = "cred_smb_secret_ref",
            ),
        ),
    )

    @Test fun export_stripsCredentialReference() {
        val text = ConfigTransfer.export(smbSettings(), "1.0", 123L)
        assertFalse("credential ref must not be exported", text.contains("cred_smb_secret_ref"))
        // Non-secret connection details are still there, so a restore is useful.
        assertTrue(text.contains("192.168.1.10"))
        assertTrue(text.contains("Photos"))
    }

    @Test fun exportThenParse_roundTripsSettings() {
        val text = ConfigTransfer.export(smbSettings(), "1.0", 123L)
        val parsed = ConfigTransfer.parse(text)
        assertTrue(parsed is ImportResult.Ok)
        val bundle = (parsed as ImportResult.Ok).bundle
        assertEquals(42, bundle.settings.intervalSeconds)
        assertEquals(AspectMode.FILL_CROP, bundle.settings.aspectMode)
        assertEquals("frame", bundle.settings.source.smb?.user)
        assertEquals("", bundle.settings.source.smb?.credentialRef)
    }

    @Test fun parse_rejectsNonJson() {
        assertEquals(
            ImportResult.Reason.NOT_JSON,
            (ConfigTransfer.parse("not json at all") as ImportResult.Failed).reason,
        )
    }

    @Test fun parse_rejectsEmpty() {
        assertEquals(
            ImportResult.Reason.EMPTY,
            (ConfigTransfer.parse("   ") as ImportResult.Failed).reason,
        )
    }

    @Test fun parse_rejectsOversizedTextBeforeJsonDecoding() {
        assertEquals(
            ImportResult.Reason.TOO_LARGE,
            (ConfigTransfer.parse("x".repeat(ConfigTransfer.MAX_IMPORT_BYTES + 1)) as ImportResult.Failed).reason,
        )
    }

    @Test fun parse_rejectsForeignJson() {
        val alien = """{"kind":"some_other_app","bundleVersion":1}"""
        assertEquals(
            ImportResult.Reason.NOT_A_FRAME_CONFIG,
            (ConfigTransfer.parse(alien) as ImportResult.Failed).reason,
        )
    }

    @Test fun parse_rejectsNewerBundleVersion() {
        val future = """{"kind":"family_photo_frame_config","bundleVersion":99}"""
        assertEquals(
            ImportResult.Reason.TOO_NEW,
            (ConfigTransfer.parse(future) as ImportResult.Failed).reason,
        )
    }

    @Test fun merge_keepsCredentialWhenSameShareIsAlreadyConfigured() {
        val current = smbSettings()                       // has a stored credential ref
        val imported = ConfigTransfer.redact(smbSettings())
        val merged = ConfigTransfer.merge(current, imported)
        assertEquals("cred_smb_secret_ref", merged.source.smb?.credentialRef)
        assertFalse(ConfigTransfer.needsPasswordReentry(merged))
    }

    @Test fun merge_clearsCredentialWhenConnectionDiffers() {
        val current = smbSettings()
        val imported = ConfigTransfer.redact(
            smbSettings().let {
                it.copy(source = it.source.copy(smb = it.source.smb!!.copy(host = "10.0.0.5")))
            }
        )
        val merged = ConfigTransfer.merge(current, imported)
        assertEquals("", merged.source.smb?.credentialRef)
        assertTrue("importing a different NAS must force re-entry", ConfigTransfer.needsPasswordReentry(merged))
    }

    @Test fun merge_ontoFrameWithNoSmbForcesReentry() {
        val merged = ConfigTransfer.merge(AppSettings(), ConfigTransfer.redact(smbSettings()))
        assertEquals("", merged.source.smb?.credentialRef)
        assertTrue(ConfigTransfer.needsPasswordReentry(merged))
    }

    @Test fun merge_hostComparisonIsCaseInsensitive() {
        val current = smbSettings()
        val imported = ConfigTransfer.redact(
            smbSettings().let {
                it.copy(source = it.source.copy(smb = it.source.smb!!.copy(host = "192.168.1.10", share = "photos")))
            }
        )
        val merged = ConfigTransfer.merge(current, imported)
        assertEquals("cred_smb_secret_ref", merged.source.smb?.credentialRef)
    }

    @Test fun merge_clearsCredentialWhenDomainDiffers() {
        val current = smbSettings()
        val imported = ConfigTransfer.redact(
            smbSettings().let {
                it.copy(source = it.source.copy(smb = it.source.smb!!.copy(domain = "OTHER")))
            }
        )
        val merged = ConfigTransfer.merge(current, imported)
        assertEquals("", merged.source.smb?.credentialRef)
    }

    @Test fun exportStripsWeatherApiKeyReference() {
        val configured = AppSettings(weather = WeatherSettings(apiKeyRef = "cred_weather_api"))
        val text = ConfigTransfer.export(configured, "1.0", 123L)
        assertFalse(text.contains("cred_weather_api"))
    }

    @Test fun nonSmbConfigNeedsNoPassword() {
        val local = AppSettings(source = ActiveSource(kind = ActiveSourceKind.SAMPLES))
        assertFalse(ConfigTransfer.needsPasswordReentry(ConfigTransfer.merge(AppSettings(), local)))
    }

    @Test fun suggestedFileNameIsStable() {
        assertEquals("photo-frame-config-20260724.json", ConfigTransfer.suggestedFileName("20260724"))
    }

    // ---- Synology (ROADMAP.md network photo-app sources) ----

    private fun synSettings(baseUrl: String = "https://nas.local:5001", user: String = "frame") =
        AppSettings(
            source = ActiveSource(
                kind = ActiveSourceKind.SYNOLOGY,
                displayName = baseUrl,
                synology = SynologySettings(
                    baseUrl = baseUrl, folderPath = "/photo", user = user,
                    credentialRef = "cred_syn_live",
                ),
            ),
        )

    /**
     * Regression guard. `redact` used to early-return when `source.smb` was null, which
     * meant a Synology credentialRef was exported untouched once a second credential-
     * bearing source type existed.
     */
    @Test fun redactStripsSynologyCredentialRef() {
        val redacted = ConfigTransfer.redact(synSettings())
        assertEquals("", redacted.source.synology?.credentialRef)
    }

    @Test fun synologyCredentialSurvivesRestoreOntoSameNas() {
        val merged = ConfigTransfer.merge(synSettings(), ConfigTransfer.redact(synSettings()))
        assertEquals("cred_syn_live", merged.source.synology?.credentialRef)
        assertFalse(ConfigTransfer.needsPasswordReentry(merged))
    }

    @Test fun synologyCredentialClearedWhenImportingDifferentNas() {
        val merged = ConfigTransfer.merge(
            synSettings(),
            ConfigTransfer.redact(synSettings(baseUrl = "https://other.local:5001")),
        )
        assertEquals("", merged.source.synology?.credentialRef)
        assertTrue("importing a different NAS must force re-entry", ConfigTransfer.needsPasswordReentry(merged))
    }

    /** The SMB path must keep working now that merge handles both source types. */
    @Test fun smbMergeStillWorksAlongsideSynology() {
        val merged = ConfigTransfer.merge(smbSettings(), ConfigTransfer.redact(smbSettings()))
        assertFalse(ConfigTransfer.needsPasswordReentry(merged))
    }

    /**
     * A hostile or stale config file must not be able to pre-approve a certificate for a
     * NAS this device has not itself trusted.
     */
    @Test fun importedConfigCannotGrantCertificateTrustForDifferentNas() {
        val hostile = AppSettings(
            source = ActiveSource(
                kind = ActiveSourceKind.SYNOLOGY,
                displayName = "https://attacker.local:5001",
                synology = SynologySettings(
                    baseUrl = "https://attacker.local:5001", folderPath = "/photo",
                    user = "frame", credentialRef = "", pinnedCertSha256 = "AA".repeat(32),
                ),
            ),
        )
        val merged = ConfigTransfer.merge(synSettings(), hostile)
        assertEquals(null, merged.source.synology?.pinnedCertSha256)
    }

    @Test fun pinnedCertificateSurvivesRestoreOntoSameNas() {
        val trusted = synSettings().let {
            it.copy(source = it.source.copy(
                synology = it.source.synology!!.copy(pinnedCertSha256 = "BB".repeat(32)),
            ))
        }
        val merged = ConfigTransfer.merge(trusted, ConfigTransfer.redact(trusted))
        assertEquals("BB".repeat(32), merged.source.synology?.pinnedCertSha256)
    }
}
