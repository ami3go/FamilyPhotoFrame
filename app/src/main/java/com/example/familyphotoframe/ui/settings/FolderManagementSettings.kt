package com.example.familyphotoframe.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.db.FolderSummary
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.SlideshowViewModel

/** Virtualized folder picker: only visible rows enter composition. */
@Composable
internal fun FolderManagementSettings(
    state: SlideshowUiState,
    vm: SlideshowViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var folders by remember { mutableStateOf<List<FolderSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf(state.selectedFolders) }

    // Selection changes update checkboxes only; Room is queried once on entry or refresh.
    LaunchedEffect(reload) {
        loading = true
        folders = vm.availableFolders()
        loading = false
    }
    LaunchedEffect(state.selectedFolders) { selection = state.selectedFolders }

    val visibleFolders = remember(folders, search) {
        val query = search.trim()
        if (query.isEmpty()) folders else folders.filter { folder ->
            folder.displayPath.contains(query, ignoreCase = true) ||
                folder.sourceId.contains(query, ignoreCase = true)
        }
    }

    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsHeader(
                title = stringResource(R.string.settings_folders),
                showBackArrow = true,
                onBack = onBack,
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.settings_folders_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selection.isEmpty()) stringResource(R.string.settings_folders_all)
                    else stringResource(
                        R.string.settings_folders_selected_of_total,
                        FolderSelectionPolicy.selectedCount(folders, selection),
                        folders.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { reload++ }) {
                    Text(stringResource(R.string.settings_folders_refresh))
                }
                if (selection.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        selection = emptySet()
                        vm.setSelectedFolders(emptySet())
                    }) {
                        Text(stringResource(R.string.settings_folders_reset))
                    }
                }
            }

            when {
                loading -> Text(stringResource(R.string.settings_folders_loading), color = MaterialTheme.colorScheme.onSurface)
                folders.isEmpty() -> Text(
                    stringResource(R.string.settings_folders_none),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                visibleFolders.isEmpty() -> Text(
                    stringResource(R.string.settings_folders_no_match),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleFolders, key = { it.selectionKey }) { folder ->
                        Column(Modifier.fillMaxWidth()) {
                            FolderCheckboxRow(
                                label = "${folder.displayPath} · ${folder.sourceId} (${folder.photoCount})",
                                checked = FolderSelectionPolicy.isSelected(folder, selection),
                            ) { checked ->
                                val next = FolderSelectionPolicy.toggle(
                                    folders, selection, folder.selectionKey, checked,
                                )
                                selection = next
                                vm.setSelectedFolders(next)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.previewFolderOnce(folder) }) {
                                    Text(stringResource(R.string.folder_action_preview_once))
                                }
                                TextButton(onClick = { vm.useFolderInActivePlaylist(folder) }) {
                                    Text(stringResource(R.string.folder_action_use_in_playlist))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
