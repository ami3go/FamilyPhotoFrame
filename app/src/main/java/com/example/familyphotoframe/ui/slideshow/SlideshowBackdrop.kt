package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.familyphotoframe.ui.render.ImageBlur
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Low-memory-safe soft backdrop for portrait fallback rendering. */
@Composable
internal fun BlurredBackdrop(
    model: Any,
    imageLoader: ImageLoader,
    photoId: Long,
    reclaimer: LegacyBitmapReclaimer,
    onOutOfMemory: () -> Unit,
) {
    val context = LocalContext.current
    val backdrop by produceState<Bitmap?>(null, photoId, model) {
        value = withContext(Dispatchers.Default) {
            var decoded: Bitmap? = null
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
                    val width = small.width
                    val height = small.height
                    val pixels = IntArray(width * height)
                    small.getPixels(pixels, 0, width, 0, 0, width, height)
                    Bitmap.createBitmap(
                        ImageBlur.boxBlur(pixels, width, height),
                        width,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: OutOfMemoryError) {
                onOutOfMemory()
                null // The frame's solid background remains visible.
            } catch (_: Exception) {
                null
            } finally {
                // This 64 px decoder result is only an input to the app-created blur and
                // is never handed to Compose, so immediate recycle is safe on every API.
                decoded?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    DisposableEffect(backdrop) {
        val rendered = backdrop
        onDispose { rendered?.let(reclaimer::retireDisplayBitmap) }
    }

    // The delayed API-21–25 reclaimer waits for old Canvas display lists before recycle.
    backdrop?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.85f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
