package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.db.FolderSummary
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.settings.PlaylistSettings
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Curating what plays (spec §9.4): folder selection and one-off folder previews,
 * favourites, and hiding or restoring photos.
 */
internal class PhotoLibraryActions(
    host: SlideshowFeatureHost,
    private val currentPlaybackSourceIds: () -> List<String>,
) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics
    private val engine = services.engine

    /**
     * Folders available to choose from, for the current playback sources.
     *
     * Read on demand rather than kept in UI state: the list only matters while the
     * picker is open, and it changes with every rescan.
     */
    suspend fun availableFolders(): List<FolderSummary> =
        runCatching { services.photoDao.folderSummaries(currentPlaybackSourceIds()) }
            .getOrDefault(emptyList())

    /** Replace the complete folder selection with exactly one DataStore transaction. */
    fun setSelectedFolders(folderKeys: Set<String>) {
        viewModelScope.launch { services.settings.setSelectedFolders(folderKeys) }
    }

    /** Show one temporary photo from an exact direct folder without mutating shuffle queues. */
    fun previewFolderOnce(folder: FolderSummary) {
        viewModelScope.launch { previewFolderOnceByKey(folder.selectionKey, folder) }
    }

    suspend fun previewFolderOnceByKey(
        folderKey: String,
        knownFolder: FolderSummary? = null,
    ): String? {
        val folder = knownFolder ?: availableFolders().firstOrNull { it.selectionKey == folderKey }
            ?: return "Folder is not available"
        engine.previewFolderOnce(folder.selectionKey)
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "FOLDER_PREVIEW_REQUESTED",
            "folderToken" to diagnosticToken(folder.selectionKey, "folder"),
        )
        _state.update {
            it.copy(transientNotice = appContext.getString(R.string.msg_folder_preview_once, folder.displayPath))
        }
        return null
    }

    /**
     * Restrict the active playlist to this exact source/direct-directory key. Built-in
     * playlists use the global folder selection; user playlists own their folder rules.
     * Either path changes eligibility and therefore produces a new shuffle scope.
     */
    fun useFolderInActivePlaylist(folder: FolderSummary) {
        viewModelScope.launch { useFolderInActivePlaylistByKey(folder.selectionKey, folder) }
    }

    suspend fun useFolderInActivePlaylistByKey(
        folderKey: String,
        knownFolder: FolderSummary? = null,
    ): String? {
        val folder = knownFolder ?: availableFolders().firstOrNull { it.selectionKey == folderKey }
            ?: return "Folder is not available"
        services.settings.update { current ->
            val activeId = current.playlists.activePlaylistId
            if (activeId in PlaylistSettings.BUILT_IN_IDS) {
                current.copy(selectedFolders = setOf(folder.selectionKey))
            } else {
                val now = System.currentTimeMillis()
                current.copy(
                    playlists = current.playlists.copy(
                        playlists = current.playlists.playlists.map { playlist ->
                            if (playlist.id == activeId) {
                                playlist.copy(folderNames = setOf(folder.selectionKey), updatedAtEpochMs = now)
                            } else playlist
                        }
                    )
                )
            }
        }
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "PLAYLIST_FOLDER_APPLIED",
            "playlistToken" to diagnosticToken(_state.value.activePlaylistId, "playlist"),
            "folderToken" to diagnosticToken(folder.selectionKey, "folder"),
        )
        _state.update {
            it.copy(transientNotice = appContext.getString(R.string.msg_folder_used_in_playlist, folder.displayPath))
        }
        return null
    }

    /** Clear the filter — play everything again. */
    fun clearFolderSelection() {
        setSelectedFolders(emptySet())
    }

    /**
     * Turn favourites-only playback on or off.
     *
     * Refuses to switch on with an empty favourites set: the engine would find no
     * primary photo, silently drop to the sample pool, and look like a bug rather than
     * a setting. Telling the user why is cheaper than letting them debug it.
     */
    fun setFavoritesOnly(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val pools = currentPlaybackSourceIds()
                val favorites = runCatching { services.photoDao.favoriteCount(pools) }.getOrDefault(0)
                if (favorites == 0) {
                    _state.update {
                        it.copy(transientNotice = appContext.getString(R.string.msg_no_favorites))
                    }
                    return@launch
                }
            }
            services.settings.update { it.copy(favoritesOnly = enabled) }
        }
    }

    /** Star or un-star the photo on screen (D-pad, web, or touch). */
    fun toggleFavorite() = engine.toggleFavorite()

    fun setInteractionHold(held: Boolean) = engine.setInteractionHold(held)

    fun setFavoriteForPhotos(ids: List<Long>, favorite: Boolean) {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return
        viewModelScope.launch {
            services.photoDao.setFavorites(distinct, favorite)
            engine.reflectFavoriteState(distinct.toSet(), favorite)
            _state.update { state ->
                state.copy(
                    visiblePresentationPhotos = state.visiblePresentationPhotos.map { photo ->
                        if (photo.id in distinct) photo.copy(isFavorite = favorite) else photo
                    },
                    transientNotice = if (favorite) "Added to favorites" else "Removed from favorites",
                )
            }
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                if (favorite) "PHOTO_FAVORITE_ADDED" else "PHOTO_FAVORITE_REMOVED",
                "presentationToken" to diagnosticToken(distinct.joinToString(","), "presentation"),
                "members" to distinct.size.toString(),
            )
            if (!favorite && _state.value.favoritesOnly &&
                _state.value.engine.current?.id in distinct
            ) engine.next()
        }
    }

    fun hidePhotos(ids: List<Long>) {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return
        viewModelScope.launch {
            services.photoDao.setHiddenBatch(distinct, true)
            engine.invalidatePlaybackPool()
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTO_EXCLUDED",
                "presentationToken" to diagnosticToken(distinct.joinToString(","), "presentation"),
                "members" to distinct.size.toString(),
            )
            _state.update {
                it.copy(
                    undoHiddenPhotoIds = distinct,
                    transientNotice = if (distinct.size == 1) "Photo hidden from slideshow" else "${distinct.size} photos hidden from slideshow",
                )
            }
            engine.next()
        }
    }

    fun undoLastHide() {
        val ids = _state.value.undoHiddenPhotoIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            services.photoDao.setHiddenBatch(ids, false)
            engine.invalidatePlaybackPool()
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTO_EXCLUSION_UNDONE",
                "presentationToken" to diagnosticToken(ids.joinToString(","), "presentation"),
                "members" to ids.size.toString(),
            )
            _state.update { it.copy(undoHiddenPhotoIds = emptyList(), transientNotice = "Photos restored") }
        }
    }

    fun clearHideUndo() {
        _state.update { it.copy(undoHiddenPhotoIds = emptyList()) }
    }

    /** Hide the photo on screen from all future playback; undone by [unhideAllPhotos]. */
    fun hideCurrentPhoto() {
        engine.hideCurrent()
        _state.update { it.copy(transientNotice = appContext.getString(R.string.msg_photo_hidden)) }
    }

    /** Restore every hidden photo — the escape hatch for an accidental hide. */
    fun unhideAllPhotos() {
        viewModelScope.launch {
            val hidden = runCatching { services.photoDao.hiddenCount() }.getOrDefault(0)
            services.photoDao.unhideAll()
            engine.invalidatePlaybackPool()
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PHOTOS_UNHIDDEN",
                "count" to hidden.toString(),
            )
            _state.update {
                it.copy(transientNotice = appContext.getString(R.string.msg_photos_unhidden, hidden))
            }
            engine.next()
        }
    }
}
