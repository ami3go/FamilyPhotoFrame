package com.example.familyphotoframe.slideshow.shuffle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FolderIdentityTest {
    @Test fun directParentDirectoryDefinesFolder() {
        assertEquals("Family/2025", FolderKey.fromIndexedPath("local", "/Family/2025/photo.jpg").canonicalRelativeDirectory)
        assertEquals(FolderKey.ROOT_DIRECTORY, FolderKey.fromIndexedPath("local", "photo.jpg").canonicalRelativeDirectory)
    }

    @Test fun equalPathsFromDifferentSourcesRemainDistinct() {
        val local = FolderKey("local", "Family/2025")
        val nas = FolderKey("nas", "Family/2025")
        assertNotEquals(local.storageKey(), nas.storageKey())
        assertEquals(local, FolderKey.parse(local.storageKey()))
    }
}
