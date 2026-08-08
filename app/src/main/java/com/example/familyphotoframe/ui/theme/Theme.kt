package com.example.familyphotoframe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The frame is always a dark, photo-first surface (spec frontend-design: the
 * content is the photograph; chrome stays out of the way). We therefore use a
 * single dark scheme regardless of system setting so the screen never flashes a
 * light background between photos.
 */
private val FrameInk = Color(0xFFEDE9E1)
private val FrameAmber = Color(0xFFE3B23C)
private val FrameSurface = Color(0xFF14130F)
private val FrameSurface2 = Color(0xFF1E1C16)
private val FrameBlack = Color(0xFF000000)

private val FrameColors = darkColorScheme(
    primary = FrameAmber,
    onPrimary = FrameBlack,
    secondary = FrameAmber,
    background = FrameBlack,
    onBackground = FrameInk,
    surface = FrameSurface,
    onSurface = FrameInk,
    surfaceVariant = FrameSurface2,
    onSurfaceVariant = FrameInk,
)

private val FrameTypography = Typography(
    // Large, legible-at-distance clock styling reused via MaterialTheme.
    displayLarge = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Light),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FamilyPhotoFrameTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FrameColors,
        typography = FrameTypography,
        content = content,
    )
}
