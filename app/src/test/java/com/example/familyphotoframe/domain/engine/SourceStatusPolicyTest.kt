package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.ActiveSource
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.data.settings.SmbSettings
import com.example.familyphotoframe.data.settings.SynologySettings
import com.example.familyphotoframe.data.settings.WebDavSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceStatusPolicyTest {
    private val fallbackId = "fallback"

    private fun idFor(kind: ActiveSourceKind): String? = when (kind) {
        ActiveSourceKind.LOCAL_SAF -> "local_saf"
        ActiveSourceKind.SMB -> "smb"
        ActiveSourceKind.SYNOLOGY -> "synology"
        ActiveSourceKind.WEBDAV -> "webdav"
        ActiveSourceKind.SAMPLES, ActiveSourceKind.NONE -> null
    }

    private fun statuses(
        source: ActiveSource,
        unavailable: Set<String> = emptySet(),
        stale: Boolean = false,
        counts: Map<String, Int> = emptyMap(),
    ) = SourceStatusPolicy.statuses(
        source = source,
        unavailableSourceIds = unavailable,
        stalePlayback = stale,
        indexedPhotos = counts,
        sourceIdFor = ::idFor,
        fallbackSourceId = fallbackId,
    ).associateBy { it.kind }

    private val configuredEverywhere = ActiveSource(
        kind = ActiveSourceKind.SYNOLOGY,
        treeUri = "content://tree/photos",
        smb = SmbSettings(host = "nas.local", share = "photo"),
        synology = SynologySettings(baseUrl = "https://nas.local:5001", folderPath = "/photo"),
        webdav = WebDavSettings(baseUrl = "https://cloud.local"),
    )

    @Test fun rolesFollowTheChosenPrimaryAndMergedSources() {
        val source = configuredEverywhere.copy(alsoPlay = setOf(ActiveSourceKind.SMB))
        val byKind = statuses(source)
        assertEquals(SourceRole.PRIMARY, byKind.getValue(ActiveSourceKind.SYNOLOGY).role)
        assertEquals(SourceRole.ALSO_PLAYING, byKind.getValue(ActiveSourceKind.SMB).role)
        assertEquals(SourceRole.CONFIGURED_IDLE, byKind.getValue(ActiveSourceKind.WEBDAV).role)
        assertEquals(SourceRole.CONFIGURED_IDLE, byKind.getValue(ActiveSourceKind.LOCAL_SAF).role)
        assertEquals(SourceRole.FALLBACK, byKind.getValue(ActiveSourceKind.SAMPLES).role)
    }

    @Test fun anUnconfiguredKindIsReportedAsSuchAndOffersNoActions() {
        val byKind = statuses(ActiveSource(kind = ActiveSourceKind.SAMPLES))
        val smb = byKind.getValue(ActiveSourceKind.SMB)
        assertEquals(SourceRole.NOT_CONFIGURED, smb.role)
        assertFalse(smb.configured)
        assertFalse(smb.canBecomePrimary)
        assertFalse(smb.canAlsoPlay)
    }

    @Test fun pooledSourcesReportReachability() {
        val source = configuredEverywhere.copy(alsoPlay = setOf(ActiveSourceKind.SMB))
        val byKind = statuses(source, unavailable = setOf("smb"))
        assertEquals(SourceAvailability.OK, byKind.getValue(ActiveSourceKind.SYNOLOGY).availability)
        assertEquals(SourceAvailability.UNAVAILABLE, byKind.getValue(ActiveSourceKind.SMB).availability)
        // Idle sources are not health-checked, so claiming they are reachable would lie.
        assertEquals(SourceAvailability.UNKNOWN, byKind.getValue(ActiveSourceKind.WEBDAV).availability)
    }

    @Test fun anUnreachablePrimaryOnCachedBytesReadsAsCachedNotDead() {
        val byKind = statuses(configuredEverywhere, unavailable = setOf("synology"), stale = true)
        assertEquals(SourceAvailability.USING_CACHE, byKind.getValue(ActiveSourceKind.SYNOLOGY).availability)
    }

    @Test fun aDemotedCoPrimaryIsOfflineEvenWhileTheFrameShowsCache() {
        val source = configuredEverywhere.copy(alsoPlay = setOf(ActiveSourceKind.SMB))
        val byKind = statuses(source, unavailable = setOf("smb"), stale = true)
        assertEquals(SourceAvailability.UNAVAILABLE, byKind.getValue(ActiveSourceKind.SMB).availability)
    }

    @Test fun samplesAreNeverOfferedAsACoPrimary() {
        val samples = statuses(configuredEverywhere).getValue(ActiveSourceKind.SAMPLES)
        assertFalse(samples.canAlsoPlay)
        assertTrue(samples.canBecomePrimary)
    }

    @Test fun thePrimaryIsNotOfferedAsItsOwnCoPrimaryOrPrimaryAgain() {
        val primary = statuses(configuredEverywhere).getValue(ActiveSourceKind.SYNOLOGY)
        assertFalse(primary.canBecomePrimary)
        assertFalse(primary.canAlsoPlay)
    }

    @Test fun countsAreAttachedPerSourceIncludingTheSamplesRow() {
        val byKind = statuses(
            configuredEverywhere,
            counts = mapOf("synology" to 12_480, "smb" to 3_021, fallbackId to 8),
        )
        assertEquals(12_480, byKind.getValue(ActiveSourceKind.SYNOLOGY).indexedPhotos)
        assertEquals(3_021, byKind.getValue(ActiveSourceKind.SMB).indexedPhotos)
        assertEquals(8, byKind.getValue(ActiveSourceKind.SAMPLES).indexedPhotos)
        assertEquals(0, byKind.getValue(ActiveSourceKind.WEBDAV).indexedPhotos)
    }

    @Test fun detailsAreNonSecretConnectionSummaries() {
        val byKind = statuses(
            configuredEverywhere.copy(
                smb = SmbSettings(
                    host = "nas.local", share = "photo", path = "family",
                    user = "frame", domain = "WORKGROUP", credentialRef = "secret-ref",
                )
            )
        )
        val smbDetail = byKind.getValue(ActiveSourceKind.SMB).detail
        assertEquals("nas.local/photo/family", smbDetail)
        assertFalse(smbDetail.contains("frame"))
        assertFalse(smbDetail.contains("secret-ref"))
    }

    @Test fun samplesAsPrimaryAreShownAsThePrimaryRatherThanAFallback() {
        val byKind = statuses(ActiveSource(kind = ActiveSourceKind.SAMPLES))
        val samples = byKind.getValue(ActiveSourceKind.SAMPLES)
        assertEquals(SourceRole.PRIMARY, samples.role)
        assertFalse(samples.canBecomePrimary)
    }

    @Test fun everyKindIsListedOnceInASignificantOrder() {
        val statuses = SourceStatusPolicy.statuses(
            source = configuredEverywhere,
            unavailableSourceIds = emptySet(),
            stalePlayback = false,
            indexedPhotos = emptyMap(),
            sourceIdFor = ::idFor,
            fallbackSourceId = fallbackId,
        )
        assertEquals(SourceStatusPolicy.orderedKinds, statuses.map { it.kind })
        assertEquals(statuses.size, statuses.distinctBy { it.kind }.size)
    }
}
