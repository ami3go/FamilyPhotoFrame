package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.settings.OnThisDaySettings
import com.example.familyphotoframe.data.settings.PlaylistSettings
import com.example.familyphotoframe.domain.onthisday.OnThisDaySelection
import com.example.familyphotoframe.domain.schedule.OnThisDaySchedule
import com.example.familyphotoframe.domain.schedule.RescanSchedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The "On this day" memory interlude (docs/FPF-FEAT-ON-THIS-DAY-001.md): settings, the
 * scheduled trigger, and the on-demand preview. It plays through a bounded playlist
 * override, which the playlist schedule watcher reverts when it expires.
 */
internal class OnThisDayActions(
    host: SlideshowFeatureHost,
    private val currentPlaybackSourceIds: () -> List<String>,
) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics
    private val engine = services.engine
    private var onThisDayJob: Job? = null

    fun setOnThisDayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { it.copy(onThisDay = it.onThisDay.copy(enabled = enabled)) }
        }
    }

    fun setOnThisDayTimesPerDay(timesPerDay: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(onThisDay = it.onThisDay.copy(timesPerDay = timesPerDay.coerceIn(1, 12)))
            }
        }
    }

    fun setOnThisDayDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(onThisDay = it.onThisDay.copy(durationMinutes = minutes.coerceIn(1, 60)))
            }
        }
    }

    fun setOnThisDayMinYearsAgo(years: Int) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(onThisDay = it.onThisDay.copy(minYearsAgo = years.coerceIn(0, 100)))
            }
        }
    }

    /** Deliberately excludes lastTriggeredEpochMs — see rescanConfigSig's own comment. */
    fun onThisDayConfigSig(cfg: OnThisDaySettings?): String =
        cfg?.let { "${it.enabled}|${it.timesPerDay}|${it.durationMinutes}|${it.minYearsAgo}" }.orEmpty()

    fun restartOnThisDayWatcher(cfg: OnThisDaySettings) {
        onThisDayJob?.cancel()
        if (!cfg.enabled) return
        onThisDayJob = viewModelScope.launch {
            while (isActive) {
                runCatching { maybeAutoTriggerOnThisDay() }
                delay(ON_THIS_DAY_POLL_MS)
            }
        }
    }

    private suspend fun maybeAutoTriggerOnThisDay() {
        val current = services.settings.settings.first()
        val cfg = current.onThisDay
        if (!cfg.enabled) return
        // Quiet hours: the slideshow itself is paused, so firing now would just be
        // consumed silently — wait for the next window after waking instead (§5.5).
        if (_state.value.asleep) return
        // Never preempts an active override (§0.3) — skip this occurrence entirely
        // rather than deferring it; the next window gets its own chance.
        val playlists = current.playlists
        val overrideActive = playlists.manualOverrideUntilEpochMs == Long.MAX_VALUE ||
            playlists.manualOverrideUntilEpochMs > System.currentTimeMillis()
        if (overrideActive) return
        val due = OnThisDaySchedule.isDue(
            nowMinutes = nowMinutes(),
            timesPerDay = cfg.timesPerDay,
            minutesSinceLastTrigger = RescanSchedule.minutesSince(
                cfg.lastTriggeredEpochMs.takeIf { it > 0L },
                System.currentTimeMillis(),
            ),
        )
        if (!due) return
        triggerOnThisDay(preview = false)
    }

    /**
     * On-demand trigger for an eventual Settings/web "Preview now" button (Phase 4 of
     * docs/FPF-FEAT-ON-THIS-DAY-001.md) — bypasses the enabled/schedule checks so it
     * works even while the feature is off, matching [previewFolderOnce]'s calling
     * convention (fire-and-forget from a button, notice surfaced via [transientNotice]).
     */
    fun previewOnThisDay() {
        viewModelScope.launch {
            triggerOnThisDay(preview = true)?.let { message ->
                _state.update { it.copy(transientNotice = message) }
            }
        }
    }

    /**
     * Assembles today's memory pool and activates it via the same bounded playlist
     * override that manual playlist activation already uses (`playPlaylist`) — the
     * existing `restartPlaylistScheduleWatcher` loop automatically reverts once the
     * override expires, so no separate revert logic is needed here.
     *
     * @param preview true for the on-demand "Preview now" action, which bypasses the
     * enabled/schedule checks that already ran (or didn't) before this is called.
     * @return null on success, or a message explaining why nothing happened.
     */
    suspend fun triggerOnThisDay(preview: Boolean): String? {
        val current = services.settings.settings.first()
        val cfg = current.onThisDay
        if (!preview && !cfg.enabled) return "On this day is disabled"
        val today = java.time.LocalDate.now()
        val monthDay = "%02d-%02d".format(today.monthValue, today.dayOfMonth)
        val candidates = services.photoDao.onThisDayCandidates(
            sourceIds = currentPlaybackSourceIds(),
            monthDay = monthDay,
            maxFailures = current.temporarilySuppressAfterDecodeFailures,
            allowHeif = if (services.allowHeifPlayback) 1 else 0,
            limit = ON_THIS_DAY_CANDIDATE_LIMIT,
        )
        val selected = OnThisDaySelection.select(
            candidates = candidates,
            currentYear = today.year,
            minYearsAgo = cfg.minYearsAgo,
            maxYears = ON_THIS_DAY_MAX_YEARS,
        )
        if (selected.isEmpty()) {
            diagnostics.log(DiagnosticsLog.Category.ENGINE, "ON_THIS_DAY_SKIPPED_EMPTY")
            return if (preview) "No memories match today's date yet" else null
        }
        engine.setOnThisDayPool(selected.map { it.id })
        val now = System.currentTimeMillis()
        services.settings.update { latest ->
            latest.copy(
                playlists = latest.playlists.copy(
                    activePlaylistId = PlaylistSettings.PLAYLIST_ON_THIS_DAY,
                    manualOverrideUntilEpochMs = now + cfg.durationMinutes.coerceIn(1, 60) * 60_000L,
                ),
                onThisDay = latest.onThisDay.copy(lastTriggeredEpochMs = now),
            )
        }
        val years = selected.map {
            java.time.Instant.ofEpochMilli(it.dateTakenEpochMs).atZone(java.time.ZoneId.systemDefault()).year
        }
        diagnostics.log(
            DiagnosticsLog.Category.ENGINE, "ON_THIS_DAY_TRIGGERED",
            "years" to years.size.toString(),
            "preview" to preview.toString(),
        )
        _state.update {
            it.copy(transientNotice = "Showing memories from " + years.joinToString(", "))
        }
        return null
    }

    private companion object {
        /** "On this day" windows are short and frequent — poll more often than rescan. */
        const val ON_THIS_DAY_POLL_MS = 30_000L
        /** Safety cap on rows fetched before year-grouping; a real day rarely nears this. */
        const val ON_THIS_DAY_CANDIDATE_LIMIT = 500
        /** Matches docs/FPF-FEAT-ON-THIS-DAY-001.md §9's proposed default. */
        const val ON_THIS_DAY_MAX_YEARS = 6
    }
}
