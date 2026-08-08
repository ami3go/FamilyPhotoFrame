package com.example.familyphotoframe.slideshow.shuffle

import com.example.familyphotoframe.data.settings.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShuffleScopeKeyFactoryTest {
    private fun inputs(
        sources: List<String> = listOf("local"),
        folders: List<String> = listOf("Family"),
        favoritesOnly: Boolean = false,
        cachedOnly: Boolean = false,
    ) = ShuffleScopeKeyFactory.EligibilityInputs(
        playlistId = "playlist-1",
        sourceIds = sources,
        selectedFolders = folders,
        favoritesOnly = favoritesOnly,
        cachedOnly = cachedOnly,
        maxDecodeFailures = 3,
    )

    @Test fun runtimeCacheMode_doesNotChangeEligibilityRevision() {
        assertEquals(
            ShuffleScopeKeyFactory.eligibilityRevision(inputs(cachedOnly = false)),
            ShuffleScopeKeyFactory.eligibilityRevision(inputs(cachedOnly = true)),
        )
    }

    @Test fun eligibilityChanges_changeRevision() {
        val baseline = ShuffleScopeKeyFactory.eligibilityRevision(inputs())
        assertNotEquals(baseline, ShuffleScopeKeyFactory.eligibilityRevision(inputs(sources = listOf("nas"))))
        assertNotEquals(baseline, ShuffleScopeKeyFactory.eligibilityRevision(inputs(folders = listOf("Travel"))))
        assertNotEquals(baseline, ShuffleScopeKeyFactory.eligibilityRevision(inputs(favoritesOnly = true)))
        assertNotEquals(
            baseline,
            ShuffleScopeKeyFactory.eligibilityRevision(
                inputs().copy(formatCapabilitySignature = "heif-excluded")
            ),
        )
    }

    @Test fun playbackModes_haveIndependentScopes() {
        val revision = ShuffleScopeKeyFactory.eligibilityRevision(inputs())
        val global = ShuffleScopeKeyFactory.scope("playlist-1", revision, SelectionMode.SHUFFLE_NO_REPEAT, "primary")
        val folders = ShuffleScopeKeyFactory.scope("playlist-1", revision, SelectionMode.FOLDER_BALANCED_SHUFFLE, "primary")
        assertNotEquals(global.scopeKey, folders.scopeKey)
    }
}
