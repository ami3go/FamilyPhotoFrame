package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPoolCachePolicyTest {

    private fun key(
        selectionMode: SelectionMode = SelectionMode.SHUFFLE_NO_REPEAT,
        sourceIds: List<String> = listOf("smb"),
        maxFailures: Int = 3,
        favoritesOnly: Boolean = false,
        cachedOnly: Boolean = false,
        allowHeif: Boolean = true,
        folders: List<String> = emptyList(),
    ) = PlaybackPoolCachePolicy.key(
        selectionMode, sourceIds, maxFailures, favoritesOnly, cachedOnly, allowHeif, folders,
    )

    @Test fun identicalInputsProduceTheSameKey() {
        assertEquals(key(), key())
    }

    /**
     * The whole risk of pool reuse is a filter change that does not move the key, so
     * every DAO input gets its own case rather than one combined assertion.
     */
    @Test fun everyQueryInputChangesTheKey() {
        val base = key()
        assertNotEquals(base, key(selectionMode = SelectionMode.DATE_TAKEN_NEWEST))
        assertNotEquals(base, key(sourceIds = listOf("smb", "local_saf")))
        assertNotEquals(base, key(maxFailures = 4))
        assertNotEquals(base, key(favoritesOnly = true))
        assertNotEquals(base, key(cachedOnly = true))
        assertNotEquals(base, key(allowHeif = false))
        assertNotEquals(base, key(folders = listOf("Holidays")))
    }

    @Test fun flagsAreNotConflatedWithOneAnother() {
        // Three adjacent booleans could collide if they were concatenated carelessly.
        assertNotEquals(key(favoritesOnly = true), key(cachedOnly = true))
        assertNotEquals(key(favoritesOnly = true), key(allowHeif = false))
        assertNotEquals(key(cachedOnly = true), key(allowHeif = false))
    }

    @Test fun sourceOrderIsPartOfTheKeyBecauseItIsPartOfThePoolOrder() {
        assertNotEquals(key(sourceIds = listOf("smb", "webdav")), key(sourceIds = listOf("webdav", "smb")))
    }

    @Test fun folderSelectionCannotBeConfusedWithASourceList() {
        assertNotEquals(
            key(sourceIds = listOf("a", "b"), folders = emptyList()),
            key(sourceIds = listOf("a"), folders = listOf("b")),
        )
    }

    @Test fun aFreshMatchingEntryIsReused() {
        assertTrue(PlaybackPoolCachePolicy.canReuse(true, "k", "k", ageMs = 1_000L, maxAgeMs = 60_000L))
    }

    @Test fun aDifferentOrMissingKeyIsNeverReused() {
        assertFalse(PlaybackPoolCachePolicy.canReuse(true, "old", "new", 0L, 60_000L))
        assertFalse(PlaybackPoolCachePolicy.canReuse(true, null, "new", 0L, 60_000L))
    }

    @Test fun theBackstopExpiresAnEntryNothingInvalidated() {
        assertFalse(PlaybackPoolCachePolicy.canReuse(true, "k", "k", ageMs = 60_000L, maxAgeMs = 60_000L))
        assertFalse(PlaybackPoolCachePolicy.canReuse(true, "k", "k", ageMs = 60_001L, maxAgeMs = 60_000L))
    }

    @Test fun aClockThatWentBackwardsDoesNotExtendTheEntryForever() {
        assertFalse(PlaybackPoolCachePolicy.canReuse(true, "k", "k", ageMs = -1L, maxAgeMs = 60_000L))
    }

    @Test fun disablingTheOptionRestoresTheQueryEveryAdvanceBehaviour() {
        assertFalse(PlaybackPoolCachePolicy.canReuse(false, "k", "k", 0L, 60_000L))
    }
}
