package com.example.familyphotoframe.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.familyphotoframe.data.settings.OverlayPosition
import com.example.familyphotoframe.data.settings.OverlaySettings
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time/date/folder overlays (spec §11). Anchored on a 9-grid. A soft scrim sits
 * under the text so it stays legible over bright photos without darkening the
 * whole image (spec §11.4). The clock ticks once per minute unless seconds are
 * shown, to avoid waking the UI every second (spec §10.3 efficiency).
 */
@Composable
fun OverlayLayer(
    overlays: OverlaySettings,
    folderName: String,
    weatherText: String? = null,
    /** The currently displayed photo's EXIF date-taken, or null if unknown (spec §11). */
    photoDateTakenEpochMs: Long? = null,
    /** The currently displayed photo's EXIF caption, or null if absent/blank. */
    caption: String? = null,
    /** The currently displayed photo's EXIF GPS, or null if absent. Never reverse-geocoded. */
    gpsLat: Double? = null,
    gpsLon: Double? = null,
    modifier: Modifier = Modifier,
) {
    val weatherVisible = overlays.weatherShow && !weatherText.isNullOrBlank()
    val photoDateVisible = overlays.photoDateShow && photoDateTakenEpochMs != null
    val captionVisible = overlays.captionShow && !caption.isNullOrBlank()
    val locationVisible = overlays.locationShow && gpsLat != null && gpsLon != null
    val anyVisible = overlays.clockShow || overlays.dateShow ||
        (overlays.folderShow && folderName.isNotBlank()) || weatherVisible ||
        photoDateVisible || captionVisible || locationVisible
    if (!anyVisible) return

    val nowMs = rememberTickingClock(showSeconds = overlays.clockShowSeconds)
    val zone = remember { ZoneId.systemDefault() }
    val time = Instant.ofEpochMilli(nowMs).atZone(zone)

    val clockPattern = when {
        overlays.clock24h && overlays.clockShowSeconds -> "HH:mm:ss"
        overlays.clock24h -> "HH:mm"
        overlays.clockShowSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    val clockText = remember(clockPattern, nowMs) {
        DateTimeFormatter.ofPattern(clockPattern, Locale.getDefault()).format(time)
    }
    val dateText = remember(nowMs) {
        DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()).format(time)
    }
    val photoDateText = if (photoDateVisible) {
        remember(photoDateTakenEpochMs, zone) {
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
                .format(Instant.ofEpochMilli(photoDateTakenEpochMs!!).atZone(zone))
        }
    } else null
    val locationText = if (locationVisible) {
        remember(gpsLat, gpsLon) {
            com.example.familyphotoframe.data.index.ExifParsing.formatGpsCoordinate(gpsLat!!, gpsLon!!)
        }
    } else null

    Box(Modifier.fillMaxSize()) {
        // Group overlays by anchor so multiple items at one corner stack cleanly.
        val byPosition = buildMap<OverlayPosition, MutableList<OverlayPiece>> {
            if (overlays.clockShow) {
                getOrPut(overlays.clockPosition) { mutableListOf() }
                    .add(OverlayPiece(clockText, isPrimary = true))
            }
            if (overlays.dateShow) {
                getOrPut(overlays.datePosition) { mutableListOf() }
                    .add(OverlayPiece(dateText, isPrimary = false))
            }
            if (weatherVisible) {
                getOrPut(overlays.weatherPosition) { mutableListOf() }
                    .add(OverlayPiece(weatherText!!, isPrimary = false))
            }
            if (overlays.folderShow && folderName.isNotBlank()) {
                getOrPut(overlays.folderPosition) { mutableListOf() }
                    .add(OverlayPiece(folderName, isPrimary = false))
            }
            if (photoDateVisible) {
                getOrPut(overlays.photoDatePosition) { mutableListOf() }
                    .add(OverlayPiece(photoDateText!!, isPrimary = false))
            }
            if (captionVisible) {
                getOrPut(overlays.captionPosition) { mutableListOf() }
                    .add(OverlayPiece(caption!!, isPrimary = false, maxLines = 2))
            }
            if (locationVisible) {
                getOrPut(overlays.locationPosition) { mutableListOf() }
                    .add(OverlayPiece(locationText!!, isPrimary = false))
            }
        }

        byPosition.forEach { (position, pieces) ->
            OverlayCluster(
                position = position,
                pieces = pieces,
                opacity = overlays.opacity,
                modifier = modifier,
            )
        }
    }
}

/**
 * [maxLines] bounds free-form text (notably EXIF captions, which can be long and are
 * entirely user/camera supplied) so an overlay can never swamp the photo. Fixed-shape
 * pieces — clock, dates, folder, coordinates — are single-line by nature and use 1.
 */
private data class OverlayPiece(val text: String, val isPrimary: Boolean, val maxLines: Int = 1)

@Composable
private fun OverlayCluster(
    position: OverlayPosition,
    pieces: List<OverlayPiece>,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val alignment = position.toAlignment()
    val textAlign = position.toTextAlign()
    Box(Modifier.fillMaxSize().padding(32.dp).then(modifier), contentAlignment = alignment) {
        // Plain text overlays — no scrim/background frame.
        Column(
            horizontalAlignment = position.toHorizontalAlignment(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            pieces.forEach { piece ->
                Text(
                    text = piece.text,
                    color = Color.White.copy(alpha = opacity),
                    fontSize = if (piece.isPrimary) 56.sp else 20.sp,
                    fontWeight = if (piece.isPrimary) FontWeight.Light else FontWeight.Normal,
                    textAlign = textAlign,
                    maxLines = piece.maxLines,
                    overflow = TextOverflow.Ellipsis,
                    // Multi-line pieces are also width-capped so a long caption cannot
                    // stretch edge to edge across a wide TV panel.
                    modifier = if (piece.maxLines > 1) Modifier.fillMaxWidth(0.5f) else Modifier,
                )
            }
        }
    }
}

/**
 * A clock state that updates only as often as needed: every second when seconds
 * are displayed, otherwise aligned to the next minute boundary.
 */
@Composable
private fun rememberTickingClock(showSeconds: Boolean): Long {
    val state: MutableLongState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by state
    LaunchedEffect(showSeconds) {
        while (true) {
            now = System.currentTimeMillis()
            if (showSeconds) {
                delay(1000L - (now % 1000L))
            } else {
                // Sleep until the start of the next minute.
                delay(60_000L - (now % 60_000L))
            }
        }
    }
    return now
}

private fun OverlayPosition.toAlignment(): Alignment = when (this) {
    OverlayPosition.TOP_LEFT -> Alignment.TopStart
    OverlayPosition.TOP_CENTER -> Alignment.TopCenter
    OverlayPosition.TOP_RIGHT -> Alignment.TopEnd
    OverlayPosition.CENTER_LEFT -> Alignment.CenterStart
    OverlayPosition.CENTER -> Alignment.Center
    OverlayPosition.CENTER_RIGHT -> Alignment.CenterEnd
    OverlayPosition.BOTTOM_LEFT -> Alignment.BottomStart
    OverlayPosition.BOTTOM_CENTER -> Alignment.BottomCenter
    OverlayPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
}

private fun OverlayPosition.toTextAlign(): TextAlign = when (this) {
    OverlayPosition.TOP_LEFT, OverlayPosition.CENTER_LEFT, OverlayPosition.BOTTOM_LEFT -> TextAlign.Start
    OverlayPosition.TOP_CENTER, OverlayPosition.CENTER, OverlayPosition.BOTTOM_CENTER -> TextAlign.Center
    OverlayPosition.TOP_RIGHT, OverlayPosition.CENTER_RIGHT, OverlayPosition.BOTTOM_RIGHT -> TextAlign.End
}

private fun OverlayPosition.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    OverlayPosition.TOP_LEFT, OverlayPosition.CENTER_LEFT, OverlayPosition.BOTTOM_LEFT -> Alignment.Start
    OverlayPosition.TOP_CENTER, OverlayPosition.CENTER, OverlayPosition.BOTTOM_CENTER -> Alignment.CenterHorizontally
    OverlayPosition.TOP_RIGHT, OverlayPosition.CENTER_RIGHT, OverlayPosition.BOTTOM_RIGHT -> Alignment.End
}
