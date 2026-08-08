package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceRuntimeSignatureTest {

    private val smb = SmbSettings(
        host = "nas.local",
        share = "photos",
        user = "frame",
        credentialRef = "smb-ref",
    )
    private val synology = SynologySettings(
        baseUrl = "https://nas.local:5001",
        folderPath = "/photo",
        user = "frame",
        credentialRef = "syn-ref",
    )

    @Test
    fun failedSynologyToSmbSwitchHasDifferentRuntimeIdentity() {
        val failedSynology = AppSettings(
            source = ActiveSource(
                kind = ActiveSourceKind.SYNOLOGY,
                synology = synology,
                smb = smb,
            )
        )
        val configuredSmb = failedSynology.copy(
            source = failedSynology.source.copy(kind = ActiveSourceKind.SMB)
        )

        assertNotEquals(
            SourceRuntimeSignature.of(failedSynology),
            SourceRuntimeSignature.of(configuredSmb),
        )
    }

    @Test
    fun changingMergedSecondarySourceTriggersReactivation() {
        val merged = AppSettings(
            source = ActiveSource(
                kind = ActiveSourceKind.SMB,
                alsoPlay = setOf(ActiveSourceKind.SYNOLOGY),
                smb = smb,
                synology = synology,
            )
        )
        val edited = merged.copy(
            source = merged.source.copy(
                synology = synology.copy(folderPath = "/photo/Family")
            )
        )

        assertNotEquals(SourceRuntimeSignature.of(merged), SourceRuntimeSignature.of(edited))
    }

    @Test
    fun changingUnusedSourceDoesNotInterruptActiveSource() {
        val smbOnly = AppSettings(
            source = ActiveSource(
                kind = ActiveSourceKind.SMB,
                smb = smb,
                synology = synology,
            )
        )
        val editedUnusedSource = smbOnly.copy(
            source = smbOnly.source.copy(
                synology = synology.copy(folderPath = "/photo/Unused")
            )
        )

        assertEquals(
            SourceRuntimeSignature.of(smbOnly),
            SourceRuntimeSignature.of(editedUnusedSource),
        )
    }

    @Test
    fun scanFilterChangeTriggersReactivation() {
        val before = AppSettings(source = ActiveSource(kind = ActiveSourceKind.SMB, smb = smb))
        val after = before.copy(
            filters = before.filters.copy(excludeFolders = before.filters.excludeFolders + "Screenshots")
        )

        assertNotEquals(SourceRuntimeSignature.of(before), SourceRuntimeSignature.of(after))
    }
}
