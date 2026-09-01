package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.familyphotoframe.data.diagnostics.BitmapLifecycleTracker
import com.example.familyphotoframe.ui.render.ImageBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/** Low-memory-safe soft backdrop for portrait fallback rendering. */
@Composable
internal fun BlurredBackdrop(
    model: Any,
    imageLoader: ImageLoader,
    photoId: Long,
    reclaimer: LegacyBitmapReclaimer,
    bitmapLifecycleTracker: BitmapLifecycleTracker,
    onOutOfMemory: () -> Unit,
    contentAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val backdropState = remember(photoId, model) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoId, model) {
        // Keep ownership until the Default-dispatcher result has reached Compose state.
        // Otherwise prompt cancellation can discard a freshly allocated output Bitmap.
        val undelivered = AtomicReference<Bitmap?>(null)
        try {
            val produced = withContext(Dispatchers.Default) {
                var decoded: Bitmap? = null
                var decodedBytes = 0L
                try {
                    val request = ImageRequest.Builder(context)
                        .data(model)
                        .size(ImageBlur.MAX_DIMENSION_PX)
                        .allowHardware(false)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                    val result = imageLoader.execute(request)
                    val bitmap = (result as? SuccessResult)?.drawable as? BitmapDrawable
                    bitmap?.bitmap?.let { small ->
                        decoded = small
                        decodedBytes = small.trackedAllocationBytes()
                        bitmapLifecycleTracker.recordAllocation(
                            BitmapLifecycleTracker.Kind.TEMPORARY,
                            decodedBytes,
                        )
                        val width = small.width
                        val height = small.height
                        val pixels = IntArray(width * height)
                        small.getPixels(pixels, 0, width, 0, 0, width, height)
                        Bitmap.createBitmap(
                            ImageBlur.boxBlur(pixels, width, height),
                            width,
                            height,
                            Bitmap.Config.ARGB_8888,
                        ).also { output ->
                            bitmapLifecycleTracker.recordAllocation(
                                BitmapLifecycleTracker.Kind.GENERATED,
                                output.trackedAllocationBytes(),
                            )
                            undelivered.set(output)
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (_: OutOfMemoryError) {
                    onOutOfMemory()
                    null // The frame's solid background remains visible.
                } catch (_: Exception) {
                    null
                } finally {
                    // This decoder result is only an input to the app-created blur and
                    // is never handed to Compose, so immediate recycle is safe on every API.
                    decoded?.let { temporary ->
                        bitmapLifecycleTracker.recordRelease(
                            BitmapLifecycleTracker.Kind.TEMPORARY,
                            decodedBytes,
                        )
                        if (!temporary.isRecycled) temporary.recycle()
                    }
                }
            }
            backdropState.value = produced
            undelivered.compareAndSet(produced, null)
        } finally {
            undelivered.getAndSet(null)?.let { escaped ->
                bitmapLifecycleTracker.recordRelease(
                    BitmapLifecycleTracker.Kind.GENERATED,
                    escaped.trackedAllocationBytes(),
                )
                if (!escaped.isRecycled) escaped.recycle()
            }
        }
    }

    // Key the cleanup to the state holder, not its current value. If the composable is
    // removed after allocation but before a recomposition, onDispose still sees and
    // retires the bitmap that the producer placed in the holder.
    DisposableEffect(backdropState) {
        onDispose { backdropState.value?.let(reclaimer::retireDisplayBitmap) }
    }

    // The delayed API-21–25 reclaimer waits for old Canvas display lists before recycle.
    backdropState.value?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.85f * contentAlpha.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
