package com.example.familyphotoframe.domain.engine

import com.example.familyphotoframe.data.settings.DecodeColorDepth
import org.junit.Assert.assertEquals
import org.junit.Test

class DecodeColorPolicyTest {
    private val smallHeap = 96L * 1024L * 1024L
    private val roomyHeap = 512L * 1024L * 1024L

    private fun choose(
        preference: DecodeColorDepth,
        heap: Long,
        level: PlaybackMemoryLevel = PlaybackMemoryLevel.NORMAL,
    ) = DecodeColorPolicy.choose(preference, heap, level)

    @Test fun autoEconomisesOnTheSmallHeapsOldTabletsProvide() {
        assertEquals(DecodeColorChoice.RGB_565, choose(DecodeColorDepth.AUTO, smallHeap))
    }

    @Test fun autoKeepsFullColourWhereTheHeapCanCarryIt() {
        assertEquals(DecodeColorChoice.ARGB_8888, choose(DecodeColorDepth.AUTO, roomyHeap))
    }

    @Test fun autoFollowsTheGuardDownOnALargeHeap() {
        PlaybackMemoryLevel.entries
            .filter { it != PlaybackMemoryLevel.NORMAL }
            .forEach { level ->
                assertEquals(
                    "level $level should economise",
                    DecodeColorChoice.RGB_565,
                    choose(DecodeColorDepth.AUTO, roomyHeap, level),
                )
            }
    }

    @Test fun theLowHeapBoundaryMatchesTheGuardsOwnThreshold() {
        val boundary = PlaybackMemoryPolicy.LOW_HEAP_MAX_BYTES
        assertEquals(DecodeColorChoice.RGB_565, choose(DecodeColorDepth.AUTO, boundary))
        assertEquals(DecodeColorChoice.ARGB_8888, choose(DecodeColorDepth.AUTO, boundary + 1))
    }

    @Test fun anUnreadableHeapSizeNeverSilentlyDegradesTheFrame() {
        assertEquals(DecodeColorChoice.ARGB_8888, choose(DecodeColorDepth.AUTO, 0L))
        assertEquals(DecodeColorChoice.ARGB_8888, choose(DecodeColorDepth.AUTO, -1L))
    }

    @Test fun explicitPreferencesOverrideBothHeapAndPressure() {
        assertEquals(DecodeColorChoice.ARGB_8888, choose(DecodeColorDepth.FULL, smallHeap))
        assertEquals(
            DecodeColorChoice.ARGB_8888,
            choose(DecodeColorDepth.FULL, smallHeap, PlaybackMemoryLevel.CRITICAL),
        )
        assertEquals(DecodeColorChoice.RGB_565, choose(DecodeColorDepth.LOW_MEMORY, roomyHeap))
    }

    @Test fun savingModeHalvesEveryDecodedPixel() {
        assertEquals(4, DecodeColorPolicy.bytesPerPixel(DecodeColorChoice.ARGB_8888))
        assertEquals(2, DecodeColorPolicy.bytesPerPixel(DecodeColorChoice.RGB_565))
    }
}
