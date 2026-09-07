package com.example.familyphotoframe.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialCachePolicyTest {
    @Test fun acceptsTheStableIdCacheKeyShape() {
        assertTrue(PartialCachePolicy.ownsFileName("0123456789abcdef0123456789abcdef.part"))
        assertTrue(PartialCachePolicy.ownsFileName("ABCDEF0123456789ABCDEF0123456789.part"))
    }

    @Test fun rejectsOtherFilesAndWrongHashLengths() {
        assertFalse(PartialCachePolicy.ownsFileName("0123456789abcdef.part"))
        assertFalse(
            PartialCachePolicy.ownsFileName(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.part"
            )
        )
        assertFalse(PartialCachePolicy.ownsFileName("0123456789abcdef0123456789abcdef.jpg"))
    }
}
