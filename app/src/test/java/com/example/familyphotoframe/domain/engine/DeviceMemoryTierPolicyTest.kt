package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMemoryTierPolicyTest {
    private val bigHeap = 512L * 1024L * 1024L
    private val smallHeap = 96L * 1024L * 1024L
    private val bigRam = 8L * 1024L * 1024L * 1024L
    private val smallRam = 2L * 1024L * 1024L * 1024L

    @Test fun aPlatformThatDeclaresItselfLowRamIsBelieved() {
        assertEquals(
            DeviceMemoryTier.LOW,
            DeviceMemoryTierPolicy.tier(lowRamFlagged = true, heapMaxBytes = bigHeap, totalRamBytes = bigRam),
        )
    }

    @Test fun eitherASmallHeapOrLittleTotalRamIsEnoughOnItsOwn() {
        assertEquals(
            DeviceMemoryTier.LOW,
            DeviceMemoryTierPolicy.tier(false, heapMaxBytes = smallHeap, totalRamBytes = bigRam),
        )
        assertEquals(
            DeviceMemoryTier.LOW,
            DeviceMemoryTierPolicy.tier(false, heapMaxBytes = bigHeap, totalRamBytes = smallRam),
        )
    }

    @Test fun aCapableDeviceKeepsFullQualityDefaults() {
        assertEquals(
            DeviceMemoryTier.STANDARD,
            DeviceMemoryTierPolicy.tier(false, heapMaxBytes = bigHeap, totalRamBytes = bigRam),
        )
    }

    @Test fun unreadableValuesDoNotByThemselvesDemoteADevice() {
        assertEquals(
            DeviceMemoryTier.STANDARD,
            DeviceMemoryTierPolicy.tier(false, heapMaxBytes = 0L, totalRamBytes = 0L),
        )
        assertEquals(
            DeviceMemoryTier.STANDARD,
            DeviceMemoryTierPolicy.tier(false, heapMaxBytes = bigHeap, totalRamBytes = -1L),
        )
    }

    @Test fun aCapableDeviceDecodesAtPanelResolution() {
        assertEquals(
            1f,
            DeviceMemoryTierPolicy.decodeScaleFor(DeviceMemoryTier.STANDARD, 1920, 1200),
            0.0001f,
        )
    }

    @Test fun aSmallPanelIsNotScaledEvenOnALowTierFrame() {
        // 1280x800 is 1.02 MP, already under the cap.
        assertEquals(
            1f,
            DeviceMemoryTierPolicy.decodeScaleFor(DeviceMemoryTier.LOW, 1280, 800),
            0.0001f,
        )
    }

    @Test fun aLargePanelIsCappedToTheDecodeBudget() {
        val scale = DeviceMemoryTierPolicy.decodeScaleFor(DeviceMemoryTier.LOW, 1920, 1200)
        assertTrue("expected scaling, got $scale", scale < 1f)
        val scaledPixels = (1920 * scale).toLong() * (1200 * scale).toLong()
        assertTrue(
            "scaled to $scaledPixels px, cap is ${DeviceMemoryTierPolicy.LOW_TIER_MAX_DECODE_PIXELS}",
            scaledPixels <= DeviceMemoryTierPolicy.LOW_TIER_MAX_DECODE_PIXELS,
        )
        // Still most of the panel: this is a memory trim, not a visible downgrade.
        assertTrue(scale > 0.7f)
    }

    @Test fun degenerateDimensionsNeverProduceAnInvalidScale() {
        assertEquals(1f, DeviceMemoryTierPolicy.decodeScaleFor(DeviceMemoryTier.LOW, 0, 1200), 0.0001f)
        assertEquals(1f, DeviceMemoryTierPolicy.decodeScaleFor(DeviceMemoryTier.LOW, 1920, -5), 0.0001f)
    }

    @Test fun theDiagnosticsRingIsSmallerOnALowTierFrame() {
        val low = DeviceMemoryTierPolicy.diagnosticsRingCapacity(DeviceMemoryTier.LOW)
        val standard = DeviceMemoryTierPolicy.diagnosticsRingCapacity(DeviceMemoryTier.STANDARD)
        assertTrue(low < standard)
        // Still enough history to diagnose a failure without opening the durable file.
        assertTrue(low >= 200)
    }
}
