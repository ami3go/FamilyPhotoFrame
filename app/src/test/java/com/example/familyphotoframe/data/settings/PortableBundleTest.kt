package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Encrypted portable bundle (spec §14.4, §22.4 "encrypted portable bundle imports on
 * another device with passphrase").
 *
 * The security properties that matter: the password is unreadable without the
 * passphrase, tampering is detected, and a wrong passphrase is indistinguishable from
 * a corrupt file.
 */
class PortableBundleTest {

    private val settings = AppSettings(
        intervalSeconds = 30,
        source = ActiveSource(
            kind = ActiveSourceKind.SMB,
            displayName = "nas/Photos",
            smb = SmbSettings("192.168.1.10", "Photos", "2024", "frame", "WORKGROUP", "cred_ref"),
        ),
    )
    private val payload = PortableBundle.Payload(
        settings = settings,
        smbPassword = "hunter2!\u00e9",
        synologyPassword = "synology-secret",
        weatherApiKey = "weather-secret",
    )

    private fun seal(passphrase: String = "correct horse battery") =
        PortableBundle.seal(payload, passphrase, nowEpochMs = 1_000L)!!

    // ---- round trip ----

    @Test fun sealThenOpenRestoresPayloadIncludingPassword() {
        val opened = PortableBundle.open(seal(), "correct horse battery")
        assertTrue(opened is PortableBundle.OpenResult.Ok)
        val result = (opened as PortableBundle.OpenResult.Ok).payload
        assertEquals("hunter2!\u00e9", result.smbPassword)
        assertEquals("synology-secret", result.synologyPassword)
        assertEquals("weather-secret", result.weatherApiKey)
        assertEquals(30, result.settings.intervalSeconds)
        assertEquals("192.168.1.10", result.settings.source.smb?.host)
    }

    @Test fun passwordIsNotRecoverableFromTheFile() {
        val text = seal()
        assertFalse("plaintext SMB password must not appear", text.contains("hunter2"))
        assertFalse("plaintext Synology password must not appear", text.contains("synology-secret"))
        assertFalse("plaintext API key must not appear", text.contains("weather-secret"))
        assertFalse("host is inside the ciphertext too", text.contains("192.168.1.10"))
    }

    // ---- passphrase handling ----

    @Test fun wrongPassphraseFails() {
        val opened = PortableBundle.open(seal(), "wrong passphrase")
        assertEquals(
            PortableBundle.OpenResult.Reason.WRONG_PASSPHRASE_OR_CORRUPT,
            (opened as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun blankPassphraseIsRefusedBothWays() {
        assertNull(PortableBundle.seal(payload, "   ", 0L))
        assertEquals(
            PortableBundle.OpenResult.Reason.EMPTY_PASSPHRASE,
            (PortableBundle.open(seal(), "") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    // ---- integrity ----

    @Test fun tamperedCiphertextIsDetected() {
        val text = seal()
        // Flip one character of the base64 ciphertext; GCM's tag must reject it.
        val marker = "\"ciphertext\": \""
        val at = text.indexOf(marker) + marker.length
        val ch = if (text[at] == 'A') 'B' else 'A'
        val tampered = text.substring(0, at) + ch + text.substring(at + 1)
        val opened = PortableBundle.open(tampered, "correct horse battery")
        assertTrue(opened is PortableBundle.OpenResult.Failed)
    }

    @Test fun wrongPassphraseAndTamperingAreIndistinguishable() {
        // Reporting them differently would tell an attacker when the passphrase was right.
        val wrong = PortableBundle.open(seal(), "nope") as PortableBundle.OpenResult.Failed
        val marker = "\"ciphertext\": \""
        val text = seal()
        val at = text.indexOf(marker) + marker.length
        val tampered = text.substring(0, at) + (if (text[at] == 'A') 'B' else 'A') + text.substring(at + 1)
        val corrupt = PortableBundle.open(tampered, "correct horse battery") as PortableBundle.OpenResult.Failed
        assertEquals(wrong.reason, corrupt.reason)
    }

    // ---- envelope metadata (spec §14.4 requires it to be recorded) ----

    @Test fun envelopeCarriesKdfAndCipherMetadata() {
        val text = seal()
        listOf("kdf", "algorithm", "salt", "iterations", "nonce", "tagBits", "bundleVersion").forEach {
            assertTrue("envelope must record $it", text.contains(it))
        }
    }

    @Test fun eachExportUsesFreshSaltAndNonce() {
        // Reusing a GCM nonce under the same key would be a critical failure.
        assertNotEquals(seal(), seal())
    }

    @Test fun kdfParametersAreNotWeakened() {
        assertTrue(PortableBundle.KDF_ITERATIONS >= 100_000)
        assertEquals("AES/GCM/NoPadding", PortableBundle.CIPHER_ALGORITHM)
    }

    // ---- rejection of foreign files ----

    @Test fun rejectsNonBundleJson() {
        assertEquals(
            PortableBundle.OpenResult.Reason.NOT_A_BUNDLE,
            (PortableBundle.open("""{"kind":"something_else"}""", "pw") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun rejectsGarbage() {
        assertEquals(
            PortableBundle.OpenResult.Reason.NOT_A_BUNDLE,
            (PortableBundle.open("not json", "pw") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun rejectsNewerBundleVersion() {
        val future = """{"kind":"family_photo_frame_portable","bundleVersion":99}"""
        assertEquals(
            PortableBundle.OpenResult.Reason.TOO_NEW,
            (PortableBundle.open(future, "pw") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun rejectsExcessiveKdfWorkBeforeDerivingAKey() {
        val hostile = seal().replace(
            "\"iterations\": 120000",
            "\"iterations\": ${PortableBundle.MAX_KDF_ITERATIONS + 1}",
        )
        assertEquals(
            PortableBundle.OpenResult.Reason.UNSAFE_PARAMETERS,
            (PortableBundle.open(hostile, "correct horse battery") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun rejectsInvalidKeyAndTagSizes() {
        val invalidKey = seal().replace("\"keyBits\": 256", "\"keyBits\": 128")
        val invalidTag = seal().replace("\"tagBits\": 128", "\"tagBits\": 96")
        listOf(invalidKey, invalidTag).forEach { hostile ->
            assertEquals(
                PortableBundle.OpenResult.Reason.UNSAFE_PARAMETERS,
                (PortableBundle.open(hostile, "correct horse battery") as PortableBundle.OpenResult.Failed).reason,
            )
        }
    }

    @Test fun rejectsUnsupportedKdfBeforeCryptography() {
        val hostile = seal().replace("PBKDF2WithHmacSHA1", "PBKDF2WithHmacSHA999")
        assertEquals(
            PortableBundle.OpenResult.Reason.UNSUPPORTED_ALGORITHM,
            (PortableBundle.open(hostile, "correct horse battery") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun rejectsOversizedEnvelopeBeforeJsonDecoding() {
        val hostile = "x".repeat(PortableBundle.MAX_ENVELOPE_CHARS + 1)
        assertEquals(
            PortableBundle.OpenResult.Reason.TOO_LARGE,
            (PortableBundle.open(hostile, "pw") as PortableBundle.OpenResult.Failed).reason,
        )
    }

    @Test fun opensVersion1BundleWithoutVersion2SecretFields() {
        // Fixed fixture produced by the v1 schema: payload only had settings+smbPassword.
        val legacy = """{"kind":"family_photo_frame_portable","bundleVersion":1,"exportedAtEpochMs":123,"kdf":{"algorithm":"PBKDF2WithHmacSHA1","salt":"AAECAwQFBgcICQoLDA0ODw==","iterations":120000,"keyBits":256},"cipher":{"algorithm":"AES/GCM/NoPadding","nonce":"AAECAwQFBgcICQoL","tagBits":128},"ciphertext":"Hj1ct5YP0rtwISwLrfPX50YSREFhxxx09uHIfLLKT770TEtosL3/aXRJIolRLzu8FddcxQQ1tXYjQZgUXqWhl9SVYUK4SMqzvHldPeYqUAHz"}"""
        val opened = PortableBundle.open(legacy, "legacy-passphrase") as PortableBundle.OpenResult.Ok
        assertEquals(27, opened.payload.settings.intervalSeconds)
        assertEquals("legacy-secret", opened.payload.smbPassword)
        assertNull(opened.payload.synologyPassword)
        assertNull(opened.payload.weatherApiKey)
    }

    @Test fun payloadWithoutPasswordRoundTrips() {
        val noSecret = PortableBundle.Payload(settings = AppSettings(), smbPassword = null)
        val text = PortableBundle.seal(noSecret, "pw", 0L)!!
        val opened = PortableBundle.open(text, "pw") as PortableBundle.OpenResult.Ok
        assertNull(opened.payload.smbPassword)
        assertNull(opened.payload.synologyPassword)
        assertNull(opened.payload.weatherApiKey)
    }

    @Test fun suggestedFileNameIsStable() {
        assertEquals("photo-frame-backup-20260724.fpfbundle", PortableBundle.suggestedFileName("20260724"))
    }

    // ---- base64 helper (hand-rolled because java.util.Base64 is API 26+) ----

    @Test fun base64RoundTripsAllByteValues() {
        val bytes = ByteArray(256) { it.toByte() }
        assertTrue(PortableBundle.Base64.decode(PortableBundle.Base64.encode(bytes)).contentEquals(bytes))
    }

    @Test fun base64HandlesEveryPaddingCase() {
        for (n in 0..8) {
            val b = ByteArray(n) { (it * 31).toByte() }
            assertTrue("length $n", PortableBundle.Base64.decode(PortableBundle.Base64.encode(b)).contentEquals(b))
        }
    }
}
