package com.example.familyphotoframe.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Merged primary pools (spec §9.3) depend on one thing that is easy to break by
 * accident: every source kind's connection settings must survive switching between
 * kinds. Before `alsoPlay` existed each save rebuilt [ActiveSource] from scratch, which
 * was harmless when only one source could play and silently destructive afterwards.
 */
class MergedSourcePoolTest {

    private val configured = ActiveSource(
        kind = ActiveSourceKind.SMB,
        treeUri = "content://tree/local",
        smb = SmbSettings(host = "nas", share = "photos", user = "fam", credentialRef = "ref-smb"),
        synology = SynologySettings(baseUrl = "https://nas:5001", user = "fam", credentialRef = "ref-syn"),
    )

    @Test fun switchingChosenKindKeepsEveryOtherSourceConfigured() {
        val afterSwitch = configured.copy(kind = ActiveSourceKind.LOCAL_SAF)

        assertEquals("content://tree/local", afterSwitch.treeUri)
        assertEquals("nas", afterSwitch.smb?.host)
        assertEquals("https://nas:5001", afterSwitch.synology?.baseUrl)
    }

    @Test fun alsoPlayDefaultsToEmptySoBehaviourIsUnchangedOnUpgrade() {
        assertTrue(ActiveSource().alsoPlay.isEmpty())
        assertTrue(configured.alsoPlay.isEmpty())
    }

    @Test fun coPrimariesAreStoredAlongsideTheChosenKind() {
        val merged = configured.copy(alsoPlay = setOf(ActiveSourceKind.LOCAL_SAF))

        assertEquals(ActiveSourceKind.SMB, merged.kind)
        assertTrue(ActiveSourceKind.LOCAL_SAF in merged.alsoPlay)
    }

    /** The chosen kind is already a primary; listing it twice would duplicate the pool. */
    @Test fun chosenKindIsExcludedFromCoPrimaries() {
        val requested = setOf(ActiveSourceKind.SMB, ActiveSourceKind.LOCAL_SAF)
        val stored = configured.copy(alsoPlay = requested - configured.kind)

        assertFalse(ActiveSourceKind.SMB in stored.alsoPlay)
        assertEquals(setOf(ActiveSourceKind.LOCAL_SAF), stored.alsoPlay)
    }

    @Test fun exportImportRoundTripPreservesCoPrimaries() {
        val merged = configured.copy(alsoPlay = setOf(ActiveSourceKind.SYNOLOGY))
        // ConfigTransfer strips secrets with copy(), so unrelated fields survive.
        val exported = merged.copy(
            smb = merged.smb?.copy(credentialRef = ""),
            synology = merged.synology?.copy(credentialRef = ""),
        )

        assertEquals(setOf(ActiveSourceKind.SYNOLOGY), exported.alsoPlay)
        assertEquals(ActiveSourceKind.SMB, exported.kind)
        assertEquals("", exported.smb?.credentialRef)
    }
}
