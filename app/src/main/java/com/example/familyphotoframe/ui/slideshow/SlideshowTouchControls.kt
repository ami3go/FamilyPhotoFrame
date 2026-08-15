package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familyphotoframe.domain.engine.DisplayPhoto

/** Touch navigation, favourite, hide, and undo UI for the slideshow surface. */
internal sealed interface CurationMode {
    val photos: List<DisplayPhoto>
    data class Favorite(override val photos: List<DisplayPhoto>) : CurationMode
    data class Hide(override val photos: List<DisplayPhoto>) : CurationMode
}

/**
 * Large tablet-friendly touch controls presented as one coherent bottom dock.
 *
 * Keeping every action in the same row makes the overlay predictable when it appears,
 * while the translucent dock preserves enough contrast over both bright and dark photos.
 * The buttons deliberately use flexible width so the same layout scales across frame
 * resolutions without reintroducing edge-mounted hit targets.
 */
@Composable
internal fun TouchNavigationOverlay(
    photos: List<DisplayPhoto>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: (List<DisplayPhoto>) -> Unit,
    onHide: (List<DisplayPhoto>) -> Unit,
) {
    val allFavorite = photos.isNotEmpty() && photos.all { it.isFavorite }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlDockButton(
                    symbol = "‹",
                    label = "Previous",
                    description = "Previous presentation",
                    onClick = onPrevious,
                )
                ControlDockButton(
                    symbol = if (allFavorite) "★" else "☆",
                    label = if (allFavorite) "Favorited" else "Favorite",
                    description = if (allFavorite) "Remove favorite" else "Favorite presentation",
                    active = allFavorite,
                    onClick = { onFavorite(photos) },
                )
                ControlDockButton(
                    symbol = "⊘",
                    label = "Hide",
                    description = "Hide presentation",
                    onClick = { onHide(photos) },
                )
                ControlDockButton(
                    symbol = "›",
                    label = "Next",
                    description = "Next presentation",
                    onClick = onNext,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ControlDockButton(
    symbol: String,
    label: String,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(84.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.White.copy(alpha = if (active) 0.26f else 0.14f),
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(symbol, fontSize = 32.sp, lineHeight = 34.sp)
            Text(label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun FavoriteSelectionDialog(
    photos: List<DisplayPhoto>,
    onDismiss: () -> Unit,
    onApply: (List<Pair<Long, Boolean>>) -> Unit,
) {
    val values = remember(photos.map { it.id to it.isFavorite }) {
        mutableStateMapOf<Long, Boolean>().apply { photos.forEach { put(it.id, it.isFavorite) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose favorites") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                photos.forEach { photo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = values[photo.id] == true, onCheckedChange = { values[photo.id] = it })
                        Text(photo.fileName, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(photos.map { it.id to (values[it.id] == true) }) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun HideSelectionDialog(
    photos: List<DisplayPhoto>,
    onDismiss: () -> Unit,
    onApply: (List<Long>) -> Unit,
) {
    val selected = remember(photos.map { it.id }) { mutableStateMapOf<Long, Boolean>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hide photos from slideshow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Original files are not deleted.")
                photos.forEach { photo ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected[photo.id] == true, onCheckedChange = { selected[photo.id] = it })
                        Text(photo.fileName, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            val ids = selected.filterValues { it }.keys.toList()
            TextButton(onClick = { onApply(ids) }, enabled = ids.isNotEmpty()) { Text("Hide selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun UndoHideBar(count: Int, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color(0xDD202020), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (count == 1) "Photo hidden" else "$count photos hidden", color = Color.White)
        TextButton(onClick = onUndo) { Text("Undo") }
    }
}
