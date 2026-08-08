package com.example.familyphotoframe.ui.settings

import com.example.familyphotoframe.data.db.FolderSummary

/** Pure empty-means-all selection rules shared by the lazy folder UI and its tests. */
internal object FolderSelectionPolicy {
    fun isSelected(folder: FolderSummary, selection: Set<String>): Boolean =
        selection.isEmpty() || folder.selectionKey in selection || folder.name in selection

    fun toggle(
        folders: List<FolderSummary>,
        selection: Set<String>,
        folderKey: String,
        selected: Boolean,
    ): Set<String> {
        val allKeys = folders.mapTo(linkedSetOf()) { it.selectionKey }
        if (folderKey !in allKeys) return selection
        val explicit = if (selection.isEmpty()) {
            allKeys.toMutableSet()
        } else {
            canonicalize(folders, selection).toMutableSet()
        }
        if (selected) explicit += folderKey else explicit -= folderKey

        // Empty and every known key both normalize to the persisted "all folders" state.
        return if (explicit.isEmpty() || allKeys.all { it in explicit }) emptySet() else explicit
    }

    fun selectedCount(folders: List<FolderSummary>, selection: Set<String>): Int =
        if (selection.isEmpty()) folders.size
        else folders.count { isSelected(it, selection) }

    private fun canonicalize(folders: List<FolderSummary>, selection: Set<String>): Set<String> {
        val knownKeys = folders.mapTo(hashSetOf()) { it.selectionKey }
        val keysByLegacyName = HashMap<String, MutableSet<String>>()
        folders.forEach { folder ->
            keysByLegacyName.getOrPut(folder.name) { linkedSetOf() }.add(folder.selectionKey)
        }
        return buildSet {
            selection.forEach { token ->
                when {
                    token in knownKeys -> add(token)
                    keysByLegacyName[token].isNullOrEmpty() -> add(token)
                    else -> addAll(keysByLegacyName.getValue(token))
                }
            }
        }
    }
}
