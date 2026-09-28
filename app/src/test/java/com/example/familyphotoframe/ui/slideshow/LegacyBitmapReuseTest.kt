package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import coil.size.Scale
import org.junit.Assert.assertEquals
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
    fun poolReturnsSmallestCompatibleRetiredBitmap() {
        val pool = LegacyBitmapReusePool(enabled = true, maxCount = 4, maxBytes = 4_000_000)
        val large = Bitmap.createBitmap(800, 600, Bitmap.Config.RGB_565)
        val small = Bitmap.createBitmap(400, 300, Bitmap.Config.RGB_565)
        assertTrue(pool.offer(large))
        assertTrue(pool.offer(small))

        val reused = pool.take(200_000, Bitmap.Config.RGB_565)

        assertSame(small, reused)
        assertEquals(1, pool.snapshot().hits)
        assertEquals(1, pool.snapshot().count)
    }

    @Test
    fun poolRejectsWrongConfigAndDisabledTier() {
        val enabled = LegacyBitmapReusePool(enabled = true)
        enabled.offer(Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565))
        assertNull(enabled.take(10_000, Bitmap.Config.ARGB_8888))

        val disabled = LegacyBitmapReusePool(enabled = false)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        assertTrue(!disabled.offer(bitmap))
        assertNull(disabled.take(1, Bitmap.Config.RGB_565))
    }

    @Test
    fun poolEvictsSmallestAllocationWhenByteBudgetIsFull() {
        val large = Bitmap.createBitmap(300, 100, Bitmap.Config.RGB_565)
        val medium = Bitmap.createBitmap(250, 100, Bitmap.Config.RGB_565)
        val small = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        val retainedBytes = large.allocationByteCount.toLong() + medium.allocationByteCount
        val pool = LegacyBitmapReusePool(
            enabled = true,
            maxCount = 4,
            maxBytes = retainedBytes,
        )

        assertTrue(pool.offer(large))
        assertTrue(pool.offer(small))
        assertTrue(pool.offer(medium))

        val snapshot = pool.snapshot()
        assertEquals(2, snapshot.count)
        assertEquals(retainedBytes, snapshot.bytes)
        assertEquals(1, snapshot.evictions)
        assertTrue(small.isRecycled)
        assertSame(large, pool.take(55_000, Bitmap.Config.RGB_565))
        assertSame(medium, pool.take(45_000, Bitmap.Config.RGB_565))
    }

    @Test
    fun poolDoesNotEvictTheOnlyBufferForANewConfig() {
        val large565 = Bitmap.createBitmap(300, 100, Bitmap.Config.RGB_565)
        val small565 = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        val argb = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val retainedBytes = large565.allocationByteCount.toLong() + argb.allocationByteCount
        val pool = LegacyBitmapReusePool(
            enabled = true,
            maxCount = 4,
            maxBytes = retainedBytes,
        )

        assertTrue(pool.offer(large565))
        assertTrue(pool.offer(small565))
        assertTrue(pool.offer(argb))

        assertTrue(small565.isRecycled)
        assertSame(argb, pool.take(1, Bitmap.Config.ARGB_8888))
        assertSame(large565, pool.take(1, Bitmap.Config.RGB_565))
    }

    @Test
    fun warmedPoolRetainsDemandWeightedSizeClassesWithinTheSameByteCap() {
        val small = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        val medium = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val large = Bitmap.createBitmap(512, 768, Bitmap.Config.ARGB_8888)
        val retainedBytes = small.allocationByteCount.toLong() +
            medium.allocationByteCount.toLong()
        assertEquals(retainedBytes, large.allocationByteCount.toLong())
        val pool = LegacyBitmapReusePool(
            enabled = true,
            maxCount = 4,
            maxBytes = retainedBytes,
        )
        repeat(100) {
            assertNull(pool.take(small.allocationByteCount.toLong(), Bitmap.Config.ARGB_8888))
        }
        repeat(28) {
            assertNull(pool.take(medium.allocationByteCount.toLong(), Bitmap.Config.ARGB_8888))
        }

        assertTrue(pool.offer(small))
        assertTrue(pool.offer(medium))
        assertTrue(!pool.offer(large))

        val snapshot = pool.snapshot()
        assertEquals(2, snapshot.count)
        assertEquals(retainedBytes, snapshot.bytes)
        assertEquals(1, snapshot.adaptiveTrims)
        assertEquals(128, snapshot.requestBuckets.split('+').sumOf(String::toLong))
        assertSame(
            medium,
            pool.take(medium.allocationByteCount.toLong(), Bitmap.Config.ARGB_8888),
        )
        assertSame(
            small,
            pool.take(small.allocationByteCount.toLong(), Bitmap.Config.ARGB_8888),
        )
        assertTrue(large.isRecycled)
    }

    @Test
    fun defaultPoolRetainsSixCommonCollageBuffersWithinExpandedByteBudget() {
        val pool = LegacyBitmapReusePool(enabled = true)
        val buffers = List(6) {
            Bitmap.createBitmap(750, 800, Bitmap.Config.RGB_565)
        }

        buffers.forEach { assertTrue(pool.offer(it)) }

        val snapshot = pool.snapshot()
        assertEquals(6, snapshot.count)
        assertEquals(7_200_000L, snapshot.bytes)
        assertEquals(0, snapshot.evictions)
        buffers.forEach { assertTrue(!it.isRecycled) }
    }

    @Test
    fun defaultPoolStillEnforcesSixBufferBound() {
        val pool = LegacyBitmapReusePool(enabled = true)
        val buffers = List(7) {
            Bitmap.createBitmap(750, 800, Bitmap.Config.RGB_565)
        }

        buffers.forEach(pool::offer)

        val snapshot = pool.snapshot()
        assertEquals(6, snapshot.count)
        assertTrue(snapshot.bytes <= 8L * 1024L * 1024L)
        assertEquals(1, snapshot.evictions)
        assertEquals(1, buffers.count(Bitmap::isRecycled))
    }

    @Test
    fun pressureImmediatelyShrinksExpandedPoolAndNormalOnlyRestoresBudget() {
        val pool = LegacyBitmapReusePool(enabled = true)
        val buffers = List(6) {
            Bitmap.createBitmap(750, 800, Bitmap.Config.RGB_565)
        }
        buffers.forEach { assertTrue(pool.offer(it)) }

        pool.setMemoryPressureConstrained(true)

        val pressured = pool.snapshot()
        assertEquals(4L * 1024L * 1024L, pressured.budgetBytes)
        assertTrue(pressured.bytes <= pressured.budgetBytes)
        assertEquals(1L, pressured.pressureTrims)
        assertTrue(buffers.any(Bitmap::isRecycled))

        pool.setMemoryPressureConstrained(false)

        val recovered = pool.snapshot()
        assertEquals(8L * 1024L * 1024L, recovered.budgetBytes)
        assertEquals(pressured.bytes, recovered.bytes)
        assertEquals(1L, recovered.pressureTrims)
    }

    @Test
    fun sizingUsesPowerOfTwoSampleAndReservesPreDensityStorage() {
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
        assertEquals(1_500_000L, sizing.requiredAllocationBytes)
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

        assertNotNull(sizing)
        assertEquals(4, sizing.sampleSize)
        assertEquals(0.8, sizing.densityScale, 0.0001)
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
