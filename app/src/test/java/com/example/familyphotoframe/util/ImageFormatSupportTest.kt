package com.example.familyphotoframe.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFormatSupportTest {
    @Test fun detectsHeifByExtensionCaseInsensitively() {
        assertTrue(ImageFormatSupport.isHeif("IMG_1234.HEIC", null))
        assertTrue(ImageFormatSupport.isHeif("portrait.heif", null))
        assertFalse(ImageFormatSupport.isHeif("portrait.jpg", null))
    }

    @Test fun detectsHeifByMimeTypeWhenExtensionIsMissing() {
        assertTrue(ImageFormatSupport.isHeif("asset", "image/heic"))
        assertTrue(ImageFormatSupport.isHeif("asset.bin", "image/heif-sequence"))
    }

    @Test fun blocksHeifBeforeAndroidEight() {
        assertFalse(ImageFormatSupport.isPlatformDecodable("IMG_1.HEIC", null, 23))
        assertFalse(ImageFormatSupport.isPlatformDecodable("IMG_1.heif", null, 25))
        assertTrue(ImageFormatSupport.isPlatformDecodable("IMG_1.heic", null, 26))
        assertFalse(ImageFormatSupport.supportsPlatformHeif(22))
        assertTrue(ImageFormatSupport.supportsPlatformHeif(26))
        assertFalse(
            ImageFormatSupport.playbackCapabilitySignature(22) ==
                ImageFormatSupport.playbackCapabilitySignature(26)
        )
    }

    @Test fun ordinaryImagesRemainPlayableOnOldFrames() {
        listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").forEach { ext ->
            assertTrue(ImageFormatSupport.isPlatformDecodable("image.$ext", null, 21))
        }
    }
}
