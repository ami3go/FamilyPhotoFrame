package com.example.familyphotoframe.ui.slideshow

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.domain.engine.CollageLayout
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.domain.engine.DecodeFailureStage
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Slideshow-level regression coverage for the decode -> classify -> optimize -> prepare
 * pipeline. Shuffle commit semantics remain covered by the existing shuffle repository
 * tests; these tests deliberately stop at the PreparedSlide ownership boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SlideshowPreparationIntegrationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val imageLoader = ImageLoader.Builder(context).build()
    private val sourceBitmaps = mutableListOf<Bitmap>()
    private val preparedBitmaps = mutableListOf<Bitmap>()

    @After
    fun tearDown() {
        preparedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        sourceBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        imageLoader.shutdown()
    }

    @Test
    fun preferThreeBuildsMixedSameFolderCollageFromDecodedOrientation() = runTest {
        val anchor = photo(1, "event/anchor.jpg")
        val portrait = photo(2, "event/portrait.jpg")
        val landscape = photo(3, "event/landscape.jpg")
        val models = mapOf(
            1L to bitmap(750, 1000),
            2L to bitmap(760, 1000),
            3L to bitmap(1600, 900),
        )
        val events = mutableListOf<String>()

        val result = prepare(
            anchor = anchor,
            candidates = listOf(portrait, landscape),
            models = models,
            mode = PortraitCollageMode.PREFER_THREE,
            events = events,
        )

        val slide = (result as PrepareSlideResult.Ready).slide
        preparedBitmaps += slide.allBitmaps()
        val collage = slide as PreparedSlide.Collage
        assertEquals(setOf(1L, 2L, 3L), collage.photos.map { it.id }.toSet())
        assertTrue(
            collage.layout == CollageLayout.FULL_LEFT_STACK_RIGHT ||
                collage.layout == CollageLayout.STACK_LEFT_FULL_RIGHT,
        )
        assertTrue(events.any { it.startsWith("COLLAGE_SELECTION_EVALUATED:") })
        assertTrue(events.any { it.startsWith("COLLAGE_READY:") })
    }

    @Test
    fun laterBetterCandidatesAreSelectedAndUnusedDecodedCandidatesAreReleased() = runTest {
        val anchor = photo(1, "event/anchor.jpg")
        val poorA = photo(2, "event/poor-a.jpg")
        val poorB = photo(3, "event/poor-b.jpg")
        val goodC = photo(4, "event/good-c.jpg")
        val goodD = photo(5, "event/good-d.jpg")
        val models = mapOf(
            1L to bitmap(660, 1000),
            2L to bitmap(420, 1000),
            3L to bitmap(480, 1000),
            4L to bitmap(680, 1000),
            5L to bitmap(700, 1000),
        )

        val result = prepare(
            anchor = anchor,
            candidates = listOf(poorA, poorB, goodC, goodD),
            models = models,
            mode = PortraitCollageMode.PREFER_THREE,
        )

        val slide = (result as PrepareSlideResult.Ready).slide
        preparedBitmaps += slide.allBitmaps()
        val collage = slide as PreparedSlide.Collage
        assertEquals(setOf(1L, 4L, 5L), collage.photos.map { it.id }.toSet())
        // Coil may return the original Bitmap or a resized allocation depending on
        // Robolectric/Coil internals, so ownership is verified structurally: only the
        // selected three decoded allocations survive in PreparedSlide.
        assertEquals(3, collage.decodedBitmaps().size)
    }

    @Test
    fun candidateFailureStillProducesBestRemainingTwoPhotoPresentation() = runTest {
        val anchor = photo(1, "event/anchor.jpg")
        val failed = photo(2, "event/broken.jpg")
        val companion = photo(3, "event/companion.jpg")
        val models = mapOf(
            1L to bitmap(750, 1000),
            3L to bitmap(760, 1000),
        )
        val candidateFailures = mutableListOf<Long>()

        val result = prepare(
            anchor = anchor,
            candidates = listOf(failed, companion),
            models = models,
            mode = PortraitCollageMode.PREFER_THREE,
            resolveFailureFor = setOf(2L),
            onCandidateFailure = { candidateFailures += it.photoId },
        )

        val slide = (result as PrepareSlideResult.Ready).slide
        preparedBitmaps += slide.allBitmaps()
        val collage = slide as PreparedSlide.Collage
        assertEquals(setOf(1L, 3L), collage.photos.map { it.id }.toSet())
        assertEquals(listOf(2L), candidateFailures)
    }

    @Test
    fun candidateOomFallsBackToSingleWithoutFailingAnchor() = runTest {
        val anchor = photo(1, "event/anchor.jpg")
        val oom = photo(2, "event/oom.jpg")
        val models = mapOf(1L to bitmap(750, 1000))

        val result = prepare(
            anchor = anchor,
            candidates = listOf(oom),
            models = models,
            mode = PortraitCollageMode.PREFER_THREE,
            resolveOomFor = setOf(2L),
        )

        val slide = (result as PrepareSlideResult.Ready).slide
        preparedBitmaps += slide.allBitmaps()
        assertTrue(slide is PreparedSlide.Single)
        assertEquals(1L, slide.anchor.id)
    }

    private suspend fun prepare(
        anchor: DisplayPhoto,
        candidates: List<DisplayPhoto>,
        models: Map<Long, Bitmap>,
        mode: PortraitCollageMode,
        events: MutableList<String> = mutableListOf(),
        resolveFailureFor: Set<Long> = emptySet(),
        resolveOomFor: Set<Long> = emptySet(),
        onCandidateFailure: (DecodeFailure) -> Unit = {},
    ): PrepareSlideResult = prepareSlide(
        context = context,
        photo = anchor,
        imageLoader = imageLoader,
        resolveModel = { display ->
            when (display.id) {
                in resolveOomFor -> PhotoModelResolution.Failed(
                    failure(display, "OutOfMemoryError", "test_oom"),
                )
                in resolveFailureFor -> PhotoModelResolution.Failed(
                    failure(display, "IOException", "test_decode_failure"),
                )
                else -> PhotoModelResolution.Ready(requireNotNull(models[display.id]))
            }
        },
        loadCollageCandidates = { _, _ -> candidates },
        collageMode = mode,
        maxCollagePhotos = 3,
        portraitFallback = PortraitFallback.SOLID_BACKGROUND,
        collageGap = CollageGap.NONE,
        prepareSoftFocusBlur = false,
        allowBlurredBackground = true,
        excludedIds = emptySet(),
        targetW = 1920,
        targetH = 1080,
        onRecoverableOom = {},
        onCollageCandidateFailure = onCandidateFailure,
        onCollageEvent = { event, _, photoIds, layout, _, _, reason ->
            events += "$event:${photoIds.joinToString(",")}:${layout.orEmpty()}:${reason.orEmpty()}"
        },
    )

    private fun failure(photo: DisplayPhoto, exception: String, reason: String) = DecodeFailure(
        photoId = photo.id,
        sourceId = photo.sourceId,
        fileExtension = "jpg",
        mimeType = photo.mimeType,
        stage = DecodeFailureStage.IMAGE_LOADER,
        exceptionClass = exception,
        reason = reason,
    )

    private fun photo(id: Long, path: String) = DisplayPhoto(
        id = id,
        openToken = path,
        isContentUri = false,
        fileName = path.substringAfterLast('/'),
        mimeType = "image/jpeg",
        folderName = path.substringBeforeLast('/'),
        stableId = "stable-$id",
        sourceId = "test-source",
        normalizedPath = path,
        fileModifiedEpochMs = id * 1_000L,
    )

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also(sourceBitmaps::add)
}
