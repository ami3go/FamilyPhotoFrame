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
