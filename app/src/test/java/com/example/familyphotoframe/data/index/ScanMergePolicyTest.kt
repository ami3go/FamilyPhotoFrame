package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.data.db.PhotoItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanMergePolicyTest {
    private fun row(stableId: String, indexedAt: Long = 2L) = PhotoItemEntity(
        id = 0,
        stableId = stableId,
        sourceId = "smb",
        normalizedPath = "album/photo.heic",
        folderName = "album",
        fileName = "photo.heic",
        mimeType = "image/heic",
        sizeBytes = 100,
        fileModifiedEpochMs = 1,
        openToken = "token",
        indexedAtEpochMs = indexedAt,
    )

    @Test fun sameContentPreservesRuntimeAndBackfilledState() {
        val old = row("same", 1).copy(
            id = 42,
            isHidden = true,
            isFavorite = true,
            lastShownAtEpochMs = 123,
            decodeFailureCount = 1_000_000,
            lastDecodeFailureAtEpochMs = 456,
            cacheKey = "same",
            width = 4032,
            height = 3024,
            caption = "kept",
            exifScannedAtEpochMs = 789,
        )
        val merged = ScanMergePolicy.merge(row("same"), old)
        assertEquals(42, merged.id)
        assertEquals(true, merged.isHidden)
        assertEquals(true, merged.isFavorite)
        assertEquals(1_000_000, merged.decodeFailureCount)
        assertEquals("same", merged.cacheKey)
        assertEquals(4032, merged.width)
        assertEquals("kept", merged.caption)
        assertEquals(789L, merged.exifScannedAtEpochMs)
    }

    @Test fun replacedBytesResetContentDependentStateOnly() {
        val old = row("old", 1).copy(
            id = 42,
            isHidden = true,
            isFavorite = true,
            decodeFailureCount = 1_000_000,
            lastDecodeFailureAtEpochMs = 456,
            cacheKey = "old",
            width = 4032,
            caption = "old caption",
            exifScannedAtEpochMs = 789,
        )
        val merged = ScanMergePolicy.merge(row("new"), old)
        assertEquals(42, merged.id)
        assertEquals(true, merged.isHidden)
        assertEquals(true, merged.isFavorite)
        assertEquals(0, merged.decodeFailureCount)
        assertNull(merged.lastDecodeFailureAtEpochMs)
        assertNull(merged.cacheKey)
        assertNull(merged.width)
        assertNull(merged.caption)
        assertNull(merged.exifScannedAtEpochMs)
    }
}
