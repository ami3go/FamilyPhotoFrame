package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import coil.size.Scale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyBitmapReuseTest {

    @Test
    fun arenaCreatesOnlySixStableSlotsAndSeventhDecodeWaitsForRetirement() = runBlocking {
        val arena = LegacyBitmapReusePool(enabled = true)
        val leased = List(6) {
            checkNotNull(arena.acquire(1_000_000L, Bitmap.Config.RGB_565))
        }
        val full = arena.snapshot()
        assertEquals(6, full.arenaAllocatedSlots)
        assertEquals(6, full.arenaActiveSlots)
        assertEquals(4, full.arenaStandardSlots)
        assertEquals(2, full.arenaLargeSlots)

        val seventh = async(start = CoroutineStart.UNDISPATCHED) {
            arena.acquire(1_000_000L, Bitmap.Config.RGB_565)
        }
        yield()
        assertFalse(seventh.isCompleted)
        assertEquals(1L, arena.snapshot().arenaWaits)

        assertTrue(arena.offer(leased.first()))
        assertSame(leased.first(), seventh.await())
        val resumed = arena.snapshot()
        assertEquals(6, resumed.arenaAllocatedSlots)
        assertEquals(6, resumed.arenaActiveSlots)
        assertEquals(6L, resumed.canonicalAllocations)
    }

    @Test
    fun arenaUsesTwoAndFourMiBCapacityClasses() = runBlocking {
        val arena = LegacyBitmapReusePool(enabled = true)

        val standard = arena.acquire(1_100_000L, Bitmap.Config.RGB_565)
        val large = arena.acquire(2_500_000L, Bitmap.Config.RGB_565)

        assertNotNull(standard)
        assertNotNull(large)
        assertEquals(2L * 1024L * 1024L, standard!!.allocationByteCount.toLong())
        assertEquals(4L * 1024L * 1024L, large!!.allocationByteCount.toLong())
        assertEquals(2, arena.snapshot().arenaAllocatedSlots)
        assertEquals(16L * 1024L * 1024L, arena.snapshot().budgetBytes)
    }

    @Test
    fun arenaNeverAdmitsForeignExactSizeBitmap() {
        val arena = LegacyBitmapReusePool(enabled = true)
        val foreign = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)

        assertFalse(arena.offer(foreign))
        assertEquals(0, arena.snapshot().arenaAllocatedSlots)
        assertFalse(foreign.isRecycled)
    }

    @Test
    fun retiredSlotCanChangePixelConfigWithoutChangingIdentityOrCapacity() = runBlocking {
        val arena = LegacyBitmapReusePool(enabled = true)
        val rgb = checkNotNull(arena.acquire(1_000_000L, Bitmap.Config.RGB_565))
        val capacity = rgb.allocationByteCount
        assertTrue(arena.offer(rgb))

        val argb = arena.acquire(1_000_000L, Bitmap.Config.ARGB_8888)

        assertSame(rgb, argb)
        assertEquals(Bitmap.Config.ARGB_8888, argb!!.config)
        assertEquals(capacity, argb.allocationByteCount)
        assertEquals(1, arena.snapshot().arenaAllocatedSlots)
    }

    @Test
    fun oversizedRequestFailsWithoutCreatingUnboundedBitmap() = runBlocking {
        val arena = LegacyBitmapReusePool(enabled = true)

        assertNull(arena.acquire(4L * 1024L * 1024L + 1L, Bitmap.Config.RGB_565))

        val snapshot = arena.snapshot()
        assertEquals(0, snapshot.arenaAllocatedSlots)
        assertEquals(1L, snapshot.arenaOversizedRequests)
        assertEquals(0L, snapshot.canonicalAllocations)
    }

    @Test
    fun pressureStateIsStickyWithoutReallocatingArena() = runBlocking {
        val arena = LegacyBitmapReusePool(enabled = true)
        val first = checkNotNull(arena.acquire(1_000_000L, Bitmap.Config.RGB_565))
        assertTrue(arena.offer(first))

        arena.setMemoryPressureConstrained(true)
        arena.setMemoryPressureConstrained(false)

        val snapshot = arena.snapshot()
        assertTrue(snapshot.pressureConstrained)
        assertEquals(1, snapshot.arenaAllocatedSlots)
        assertEquals(0L, snapshot.pressureTrims)
    }

    @Test
    fun disabledTierDoesNotAllocateArenaSlots() = runBlocking {
        val arena = LegacyBitmapReusePool(enabled = false)
        assertNull(arena.acquire(1L, Bitmap.Config.RGB_565))
        assertEquals(0, arena.snapshot().arenaAllocatedSlots)
    }

    @Test
    fun sizingUsesPowerOfTwoSampleAndReservesScaledOutputStorage() {
        val sizing = LegacyBitmapDecodeSizing.calculate(
            sourceWidth = 4000,
            sourceHeight = 3000,
            targetWidth = 800,
            targetHeight = 600,
            rotated = false,
            scale = Scale.FIT,
            bytesPerPixel = 2,
        )

        assertEquals(4, sizing.sampleSize)
        assertEquals(0.8, sizing.densityScale, 0.0001)
        assertEquals(960_000L, sizing.requiredAllocationBytes)
    }

    @Test
    fun sizingAccountsForExifRotation() {
        val sizing = LegacyBitmapDecodeSizing.calculate(
            sourceWidth = 3000,
            sourceHeight = 4000,
            targetWidth = 800,
            targetHeight = 600,
            rotated = true,
            scale = Scale.FIT,
            bytesPerPixel = 2,
        )

        assertEquals(4, sizing.sampleSize)
        assertEquals(0.8, sizing.densityScale, 0.0001)
        assertEquals(960_000L, sizing.requiredAllocationBytes)
    }

    @Test
    fun sizingReservesUpscaledOutputStorage() {
        val sizing = LegacyBitmapDecodeSizing.calculate(
            sourceWidth = 400,
            sourceHeight = 300,
            targetWidth = 800,
            targetHeight = 600,
            rotated = false,
            scale = Scale.FIT,
            bytesPerPixel = 2,
        )

        assertEquals(1, sizing.sampleSize)
        assertEquals(2.0, sizing.densityScale, 0.0001)
        assertEquals(960_000L, sizing.requiredAllocationBytes)
    }
}
