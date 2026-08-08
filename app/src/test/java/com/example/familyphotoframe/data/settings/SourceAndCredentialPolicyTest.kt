package com.example.familyphotoframe.data.settings

import com.example.familyphotoframe.data.source.BuiltInSourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAndCredentialPolicyTest {

    @Test fun onlyRemoteSourcesRequireMediaCache() {
        assertTrue(BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.SMB))
        assertTrue(BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.SYNOLOGY))
        assertFalse(BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.LOCAL_SAF))
        assertFalse(BuiltInSourceIds.requiresMediaCache(BuiltInSourceIds.FALLBACK))
    }

    @Test fun smbCredentialReferenceIsStableButAccountScoped() {
        val original = SmbSettings(
            host = "NAS.local", share = "/Photos/", path = "Family",
            user = "frame", domain = "WORKGROUP",
        )
        val equivalent = original.copy(host = "nas.LOCAL", share = "Photos", path = "Other")
        assertTrue(CredentialPolicy.sameSmbScope(original, equivalent))
        assertEquals(CredentialPolicy.smbRef(original), CredentialPolicy.smbRef(equivalent))
        assertFalse(CredentialPolicy.smbRef(original) == CredentialPolicy.smbRef(original.copy(user = "other")))
        assertFalse(CredentialPolicy.smbRef(original) == CredentialPolicy.smbRef(original.copy(domain = "OTHER")))
    }

    @Test fun synologyCredentialReferenceIsStableButAccountScoped() {
        val original = SynologySettings(baseUrl = "https://NAS.local/", user = "frame")
        val equivalent = original.copy(baseUrl = "https://nas.LOCAL", folderPath = "/photo/Other")
        assertTrue(CredentialPolicy.sameSynologyScope(original, equivalent))
        assertEquals(CredentialPolicy.synologyRef(original), CredentialPolicy.synologyRef(equivalent))
        assertFalse(
            CredentialPolicy.synologyRef(original) ==
                CredentialPolicy.synologyRef(original.copy(user = "other")),
        )
    }
}
