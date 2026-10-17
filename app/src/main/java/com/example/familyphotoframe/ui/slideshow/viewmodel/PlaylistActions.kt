package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.ServiceLocator
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.data.settings.PlaylistScheduleRule
import com.example.familyphotoframe.data.settings.PlaylistSettings
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.data.settings.SlideshowPlaylist
import com.example.familyphotoframe.domain.schedule.PlaylistOverrideWakePolicy
import com.example.familyphotoframe.domain.schedule.PlaylistSchedule
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Named playlists, manual overrides, and local-time playlist switching. */
internal class PlaylistActions(host: SlideshowFeatureHost) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics
    private val engine = services.engine
    var playlistScheduleJob: Job? = null

    fun playPlaylist(id: String, overrideMinutes: Int? = null) {
        viewModelScope.launch {
            services.settings.update { current ->
                if (current.playlists.playlists.none { it.id == id && it.enabled }) return@update current
                val now = System.currentTimeMillis()
                val until = when {
                    overrideMinutes == null && current.playlists.scheduleEnabled ->
                        now + PlaylistSchedule.minutesUntilBoundary(current.playlists.scheduleRules, now) * 60_000L
                    overrideMinutes == null -> 0L
                    overrideMinutes < 0 -> Long.MAX_VALUE
                    else -> now + overrideMinutes.coerceIn(1, 24 * 60) * 60_000L
                }
                current.copy(
                    playlists = current.playlists.copy(
                        activePlaylistId = id,
                        manualOverrideUntilEpochMs = until,
                    )
                )
            }
            diagnostics.log(
                DiagnosticsLog.Category.ENGINE,
                "PLAYLIST_STARTED",
                "playlistToken" to diagnosticToken(id, "playlist"),
            )
        }
    }

    fun createPlaylist(name: String, favoritesOnly: Boolean = false, localUploadsOnly: Boolean = false) {
        val clean = name.trim().take(80)
        if (clean.isBlank()) return
        viewModelScope.launch {
            services.settings.update { current ->
                if (current.playlists.playlists.any { it.name.equals(clean, ignoreCase = true) }) return@update current
                val now = System.currentTimeMillis()
                val playlist = SlideshowPlaylist(
                    id = "user_${java.util.UUID.randomUUID()}",
                    name = clean,
                    favoritesOnly = favoritesOnly,
                    localUploadsOnly = localUploadsOnly,
                    sourceIds = if (localUploadsOnly) setOf(ServiceLocator.SOURCE_LOCAL_UPLOADS) else emptySet(),
                    folderNames = current.selectedFolders,
                    selectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE,
                    intervalSeconds = current.intervalSecondsClamped,
                    transitionSelectionMode = current.transitionSelectionMode,
                    transition = current.transition,
                    collageMode = current.portraitCollage.mode,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                )
                current.copy(playlists = current.playlists.copy(playlists = current.playlists.playlists + playlist))
            }
        }
    }

    fun deletePlaylist(id: String) {
        if (id in PlaylistSettings.BUILT_IN_IDS) return
        viewModelScope.launch {
            services.settings.update { current ->
                val remaining = current.playlists.playlists.filterNot { it.id == id }
                val active = if (current.playlists.activePlaylistId == id) PlaylistSettings.PLAYLIST_ALL
                    else current.playlists.activePlaylistId
                current.copy(
                    playlists = current.playlists.copy(
                        playlists = remaining,
                        activePlaylistId = active,
                        scheduleRules = current.playlists.scheduleRules.filterNot { it.playlistId == id },
                    )
                )
            }
            engine.deletePlaylistShuffleState(id)
        }
    }

    fun setPlaylistScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(playlists = it.playlists.copy(scheduleEnabled = enabled)) }
        }
    }

    fun addPlaylistScheduleRule(
        name: String,
        playlistId: String,
        startTime: String,
        endTime: String,
        days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
        priority: Int = 0,
    ) {
        if (SleepSchedule.parseMinutes(startTime) == null || SleepSchedule.parseMinutes(endTime) == null) return
        viewModelScope.launch {
            services.settings.update { current ->
                if (current.playlists.playlists.none { it.id == playlistId }) return@update current
                val rule = PlaylistScheduleRule(
                    id = "rule_${java.util.UUID.randomUUID()}",
                    name = name.trim().ifBlank { "Playlist schedule" }.take(80),
                    playlistId = playlistId,
                    daysOfWeek = days,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                current.copy(playlists = current.playlists.copy(scheduleRules = current.playlists.scheduleRules + rule))
            }
        }
    }

    fun deletePlaylistScheduleRule(id: String) {
        viewModelScope.launch {
            services.settings.update { current ->
                current.copy(playlists = current.playlists.copy(
                    scheduleRules = current.playlists.scheduleRules.filterNot { it.id == id }
                ))
            }
        }
    }

    fun cancelPlaylistOverride() {
        viewModelScope.launch {
            services.settings.update { it.copy(playlists = it.playlists.copy(manualOverrideUntilEpochMs = 0L)) }
        }
    }

    fun playlistScheduleConfig(settings: PlaylistSettings?): String = settings?.let {
        "${it.scheduleEnabled}|${it.defaultPlaylistId}|${it.manualOverrideUntilEpochMs}|" +
            it.playlists.joinToString(",") { p -> "${p.id}:${p.enabled}" } + "|" +
            it.scheduleRules.joinToString(";") { r ->
                "${r.id}:${r.enabled}:${r.playlistId}:${r.daysOfWeek.sorted()}:${r.startTime}:${r.endTime}:" +
                    "${r.priority}:${r.startDateIso}:${r.endDateIso}:${r.updatedAtEpochMs}"
            }
    }.orEmpty()

    fun restartPlaylistScheduleWatcher() {
        playlistScheduleJob?.cancel()
        playlistScheduleJob = viewModelScope.launch {
            while (isActive) {
                val current = services.settings.settings.first()
                val config = current.playlists
                val now = System.currentTimeMillis()
                val overrideActive = config.manualOverrideUntilEpochMs == Long.MAX_VALUE ||
                    config.manualOverrideUntilEpochMs > now
                if (!overrideActive) {
                    // An expired manual override (e.g. from On This Day, or a timed
                    // playlist activation) must revert regardless of whether day/time
                    // schedule switching is separately enabled — otherwise a frame with
                    // scheduling off (the default) stays parked on the override's
                    // playlist forever once its window passes.
                    val match = if (config.scheduleEnabled) {
                        PlaylistSchedule.activeRule(config.scheduleRules, now)
                    } else {
                        null
                    }
                    val target = match?.rule?.playlistId ?: config.defaultPlaylistId
                    if (config.playlists.any { it.id == target && it.enabled } && target != config.activePlaylistId) {
                        services.settings.update { latest ->
                            latest.copy(playlists = latest.playlists.copy(activePlaylistId = target))
                        }
                        diagnostics.log(
                            DiagnosticsLog.Category.ENGINE,
                            "PLAYLIST_SCHEDULE_SWITCHED",
                            "playlistId" to target,
                            "ruleId" to match?.rule?.id.orEmpty(),
                        )
                    }
                    _state.update { it.copy(activePlaylistRuleName = match?.rule?.name) }
                } else {
                    _state.update { it.copy(activePlaylistRuleName = null) }
                }
                val scheduleDelayMs = PlaylistSchedule.minutesUntilBoundary(config.scheduleRules, now)
                    .coerceIn(1, 15).toLong() * 60_000L
                // A timed manual override (notably On This Day) is also a schedule
                // boundary. Without this, a five-minute interlude can remain active
                // until the watcher's next fifteen-minute wake and repeatedly select
                // its final image in the meantime.
                delay(
                    PlaylistOverrideWakePolicy.nextWakeDelayMs(
                        nowEpochMs = now,
                        scheduleDelayMs = scheduleDelayMs,
                        overrideUntilEpochMs = config.manualOverrideUntilEpochMs,
                    )
                )
            }
        }
    }
}
