package com.example.familyphotoframe.ui.slideshow

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageScaleMode

internal fun CollageScaleMode.toContentScale(): ContentScale = when (this) {
    CollageScaleMode.CROP -> ContentScale.Crop
    CollageScaleMode.FIT -> ContentScale.Fit
}
internal fun CollageAlignment.toComposeAlignment(): Alignment = when (this) {
    CollageAlignment.CENTER -> Alignment.Center
    CollageAlignment.TOP -> Alignment.TopCenter
    CollageAlignment.BOTTOM -> Alignment.BottomCenter
    CollageAlignment.LEFT -> Alignment.CenterStart
    CollageAlignment.RIGHT -> Alignment.CenterEnd
}
internal fun CollageBackground.toArgb(appBackgroundColorArgb: Long): Int = when (this) {
    CollageBackground.APP_BACKGROUND -> appBackgroundColorArgb.toInt()
    CollageBackground.BLACK -> android.graphics.Color.BLACK
    CollageBackground.WHITE -> android.graphics.Color.WHITE
}
internal fun CollageBackground.toComposeColor(appBackgroundColorArgb: Long): Color = Color(toArgb(appBackgroundColorArgb))
