#!/usr/bin/env python3
from pathlib import Path
R=Path(__file__).resolve().parents[1]
p=R/'app/src/main/java/com/example/familyphotoframe/ui/slideshow/WebPreviewRenderer.kt'
p.write_text('''package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import android.graphics.Path
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.ui.render.ImageBlur
import com.example.familyphotoframe.web.WebPreviewFrame
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Builds one bounded web-preview JPEG from an already-owned prepared presentation. */
internal suspend fun createWebPreviewFrame(
    slide: PreparedSlide,
    aspectMode: AspectMode,
    backgroundColorArgb: Long,
    collageGap: CollageGap,
    collageScaleMode: CollageScaleMode = CollageScaleMode.CROP,
    collageAlignment: CollageAlignment = CollageAlignment.CENTER,
    collageBackground: CollageBackground = CollageBackground.APP_BACKGROUND,
    collageCornerRadiusDp: Int = 0,
    transition: String,
    targetW: Int,
    targetH: Int,
    onOutOfMemory: () -> Unit,
): WebPreviewFrame? = withContext(Dispatchers.Default) {
    var output: Bitmap? = null
    try {
        val sourceW = targetW.coerceAtLeast(1)
        val sourceH = targetH.coerceAtLeast(1)
        var width = minOf(960, sourceW)
        var height = (width.toLong() * sourceH / sourceW).toInt().coerceAtLeast(1)
        if (height > 540) {
            height = 540
            width = (height.toLong() * sourceW / sourceH).toInt().coerceAtLeast(1)
        }
        val previewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output = previewBitmap
        val canvas = android.graphics.Canvas(previewBitmap)
        canvas.drawColor(backgroundColorArgb.toInt())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)

        fun hOffset(extra: Int): Int = when (collageAlignment) {
            CollageAlignment.LEFT -> 0; CollageAlignment.RIGHT -> extra; else -> extra / 2
        }
        fun vOffset(extra: Int): Int = when (collageAlignment) {
            CollageAlignment.TOP -> 0; CollageAlignment.BOTTOM -> extra; else -> extra / 2
        }
        fun sourceCrop(bitmap: Bitmap, destination: android.graphics.RectF): android.graphics.Rect {
            val sourceAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            val destinationAspect = destination.width() / destination.height().coerceAtLeast(1f)
            return if (sourceAspect > destinationAspect) {
                val cropWidth = (bitmap.height * destinationAspect).roundToInt().coerceIn(1, bitmap.width)
                val left = hOffset(bitmap.width - cropWidth)
                android.graphics.Rect(left, 0, left + cropWidth, bitmap.height)
            } else {
                val cropHeight = (bitmap.width / destinationAspect).roundToInt().coerceIn(1, bitmap.height)
                val top = vOffset(bitmap.height - cropHeight)
                android.graphics.Rect(0, top, bitmap.width, top + cropHeight)
            }
        }
        fun drawCrop(bitmap: Bitmap, destination: android.graphics.RectF) =
            canvas.drawBitmap(bitmap, sourceCrop(bitmap, destination), destination, paint)
        fun drawFit(bitmap: Bitmap, destination: android.graphics.RectF) {
            val scale = minOf(destination.width()/bitmap.width.coerceAtLeast(1), destination.height()/bitmap.height.coerceAtLeast(1))
            val drawW=bitmap.width*scale; val drawH=bitmap.height*scale
            val spareX=destination.width()-drawW; val spareY=destination.height()-drawH
            val left=destination.left+when(collageAlignment){CollageAlignment.LEFT->0f;CollageAlignment.RIGHT->spareX;else->spareX/2f}
            val top=destination.top+when(collageAlignment){CollageAlignment.TOP->0f;CollageAlignment.BOTTOM->spareY;else->spareY/2f}
            canvas.drawBitmap(bitmap,null,android.graphics.RectF(left,top,left+drawW,top+drawH),paint)
        }
        fun drawTile(bitmap: Bitmap, destination: android.graphics.RectF) {
            val save=canvas.save()
            val radius=collageCornerRadiusDp.coerceIn(0,32).toFloat().coerceAtMost(minOf(destination.width(),destination.height())/2f)
            if(radius>0f){ val path=Path().apply{addRoundRect(destination,radius,radius,Path.Direction.CW)}; canvas.clipPath(path) }
            when(collageScaleMode){ CollageScaleMode.CROP->drawCrop(bitmap,destination); CollageScaleMode.FIT->drawFit(bitmap,destination) }
            canvas.restoreToCount(save)
        }
        fun drawPreviewBlur(bitmap: Bitmap, destination: android.graphics.RectF) {
            val scale=minOf(1f,180f/maxOf(bitmap.width,bitmap.height).coerceAtLeast(1))
            val sw=(bitmap.width*scale).roundToInt().coerceAtLeast(1); val sh=(bitmap.height*scale).roundToInt().coerceAtLeast(1)
            val small=Bitmap.createScaledBitmap(bitmap,sw,sh,true)
            try{
                val px=IntArray(sw*sh); small.getPixels(px,0,sw,0,0,sw,sh)
                val blurred=Bitmap.createBitmap(ImageBlur.boxBlur(px,sw,sh),sw,sh,Bitmap.Config.ARGB_8888)
                try{ drawCrop(blurred,destination); canvas.drawColor(android.graphics.Color.argb(50,0,0,0)) }
                finally{if(!blurred.isRecycled)blurred.recycle()}
            }finally{if(small!==bitmap&&!small.isRecycled)small.recycle()}
        }

        val full=android.graphics.RectF(0f,0f,width.toFloat(),height.toFloat())
        when(slide){
            is PreparedSlide.Single -> when(slide.aspectOverride?:aspectMode){
                AspectMode.FILL_CROP->drawCrop(slide.bitmap,full)
                AspectMode.FIT_COLOR->drawFit(slide.bitmap,full)
                AspectMode.FIT_BLUR->{drawPreviewBlur(slide.bitmap,full);drawFit(slide.bitmap,full)}
            }
            is PreparedSlide.Collage -> {
                canvas.drawColor(collageBackground.toArgb(backgroundColorArgb))
                val gapPx=when(collageGap){CollageGap.NONE->0f;CollageGap.SMALL->4f;CollageGap.MEDIUM->8f;CollageGap.LARGE->16f}
                val destinations=collageDestinationRects(slide.layout,width.toFloat(),height.toFloat(),gapPx)
                slide.tiles.zip(destinations).forEach{(tile,destination)->drawTile(tile.bitmap,destination)}
            }
        }
        val bytes=ByteArrayOutputStream().use{stream->if(!previewBitmap.compress(Bitmap.CompressFormat.JPEG,82,stream))return@withContext null;stream.toByteArray()}
        val photoIds=slide.photos.map{it.id}
        val type=when(slide){is PreparedSlide.Single->if(slide.aspectOverride!=null)"portrait_fallback" else "single";is PreparedSlide.Collage->"collage_${slide.tiles.size}"}
        val revisionKey=buildString{
            append(slide.anchor.id).append('|');append(photoIds.joinToString(",")).append('|');append(aspectMode.name).append('|')
            append(backgroundColorArgb).append('|');append(collageGap.name).append('|');append(collageScaleMode.name).append('|')
            append(collageAlignment.name).append('|');append(collageBackground.name).append('|');append(collageCornerRadiusDp).append('|')
            append((slide as? PreparedSlide.Collage)?.layout?.name.orEmpty()).append('|');append(width).append('x').append(height)
        }
        WebPreviewFrame(Integer.toHexString(revisionKey.hashCode()),bytes,slide.anchor.id,type,photoIds,slide.anchor.fileName,slide.anchor.sourceId,slide.anchor.folderName,transition,System.currentTimeMillis(),width,height)
    } catch(c:CancellationException){throw c}
      catch(_:OutOfMemoryError){onOutOfMemory();null}
      catch(_:Exception){null}
      finally{output?.takeIf{!it.isRecycled}?.recycle()}
}
''',encoding='utf-8')
print('collage web preview patch applied')
