package com.example.familyphotoframe.slideshow.shuffle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleEligibilitySnapshotTest {
    private fun folder(sourceId: String, dir: String = "a") =
        EligibleFolder(FolderKey(sourceId, dir), emptyList())

    @Test
    fun healthySourceIsServiceable() {
        val snapshot = ShuffleEligibilitySnapshot(1, listOf(folder("nas")))
        assertTrue(snapshot.hasServiceableFolder)
    }

    @Test
    fun allSourcesExhaustedOrDeferredIsNotServiceable() {
        val snapshot = ShuffleEligibilitySnapshot(
            revision = 1,
            folders = listOf(folder("nas"), folder("nas", "b"), folder("usb")),
            temporarilyUnavailableSourceIds = setOf("usb"),
            exhaustedUnavailableSourceIds = setOf("nas"),
        )
        assertFalse(snapshot.hasServiceableFolder)
    }

    @Test
    fun oneHealthySourceAmongUnavailableOnesIsServiceable() {
        val snapshot = ShuffleEligibilitySnapshot(
            revision = 1,
            folders = listOf(folder("nas"), folder("local")),
            exhaustedUnavailableSourceIds = setOf("nas"),
        )
        assertTrue(snapshot.hasServiceableFolder)
    }

    @Test
    fun emptySnapshotIsNotServiceable() {
        assertFalse(ShuffleEligibilitySnapshot(1, emptyList()).hasServiceableFolder)
    }
}
