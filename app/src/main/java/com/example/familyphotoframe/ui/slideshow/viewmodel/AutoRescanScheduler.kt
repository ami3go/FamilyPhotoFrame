package com.example.familyphotoframe.ui.slideshow.viewmodel

import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.SourceRefreshTrigger
import com.example.familyphotoframe.data.settings.ScheduleSettings
import com.example.familyphotoframe.domain.schedule.RescanSchedule
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The scheduled automatic index refresh (spec §20): its settings and its watcher. */
internal class AutoRescanScheduler(
    host: SlideshowFeatureHost,
    /** Runs a source refresh through the ViewModel's source pipeline. */
    private val refreshSource: suspend (SourceRefreshTrigger, DiagnosticOrigin, Boolean) -> Unit,
) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val diagnostics = services.diagnostics
    var rescanScheduleJob: Job? = null

    /**
     * Enabling with no weekdays selected would silently never run, so switching it on
     * seeds every day; the user can then deselect.
     */
    fun setAutoRescanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update { s ->
                val days = RescanSchedule.parseDays(s.schedule.autoRescanDays)
                s.copy(
                    schedule = s.schedule.copy(
                        autoRescanEnabled = enabled,
                        autoRescanDays = if (enabled && days.isEmpty()) {
                            RescanSchedule.formatDays(RescanSchedule.everyDay())
                        } else {
                            s.schedule.autoRescanDays
                        },
                    )
                )
            }
        }
    }

    /** Accepts partial input while typing; only a valid `HH:mm` is stored. */
    fun setAutoRescanAt(text: String) {
        viewModelScope.launch {
            val minutes = SleepSchedule.parseMinutes(text) ?: return@launch
            services.settings.update {
                it.copy(schedule = it.schedule.copy(
                    autoRescanAt = SleepSchedule.formatMinutes(minutes),
                ))
            }
        }
    }

    fun setAutoRescanDay(isoDay: Int, selected: Boolean) {
        viewModelScope.launch {
            services.settings.update { s ->
                val days = RescanSchedule.parseDays(s.schedule.autoRescanDays)
                val next = if (selected) days + isoDay else days - isoDay
                s.copy(schedule = s.schedule.copy(autoRescanDays = RescanSchedule.formatDays(next)))
            }
        }
    }

    fun setAutoRescanDays(days: Set<Int>) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(schedule = it.schedule.copy(
                    autoRescanDays = RescanSchedule.formatDays(days),
                ))
            }
        }
    }

    /**
     * Watches for the scheduled automatic index refresh (spec §20).
     *
     * Polls the wall clock rather than sleeping until the exact moment, for the same
     * reason as the sleep watcher: a timer does not survive a reboot or a suspended
     * device, whereas re-deriving "is it due?" from the clock and the last-run timestamp
     * is correct however the device behaved in between.
     */
    fun rescanConfigSig(schedule: ScheduleSettings?): String =
        schedule?.let { "${it.autoRescanEnabled}|${it.autoRescanAt}|${it.autoRescanDays}" } ?: ""

    fun restartRescanWatcher(schedule: ScheduleSettings) {
        rescanScheduleJob?.cancel()
        val days = RescanSchedule.parseDays(schedule.autoRescanDays)
        val at = SleepSchedule.parseMinutes(schedule.autoRescanAt)
        if (!schedule.autoRescanEnabled || at == null || days.isEmpty()) return

        rescanScheduleJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val due = RescanSchedule.isDue(
                    nowMinutes = nowMinutes(),
                    nowDayOfWeek = java.time.LocalDate.now().dayOfWeek.value,
                    atMinutes = at,
                    days = days,
                    // Read fresh rather than captured: the watcher writes this value, and
                    // a captured copy would go stale the moment it fired once.
                    minutesSinceLastRun = RescanSchedule.minutesSince(
                        services.settings.settings.first()
                            .schedule.lastAutoRescanAtEpochMs.takeIf { it > 0L },
                        now,
                    ),
                )
                if (due) {
                    diagnostics.log(
                        DiagnosticsLog.Category.SCAN, "AUTO_RESCAN_START",
                        "at" to schedule.autoRescanAt,
                        "days" to RescanSchedule.formatDays(days),
                    )
                    // Persist before scanning: if the scan crashes or the device dies
                    // mid-way, the slot is still consumed rather than retried in a loop.
                    services.settings.update {
                        it.copy(schedule = it.schedule.copy(lastAutoRescanAtEpochMs = now))
                    }
                    try {
                        requestSourceRefresh(
                            trigger = SourceRefreshTrigger.SCHEDULED_RESCAN,
                            origin = DiagnosticOrigin.SCHEDULER,
                            awaitCompletion = true,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        diagnostics.log(
                            DiagnosticsLog.Category.SCAN, "AUTO_RESCAN_FAILED",
                            "error" to error.javaClass.simpleName,
                        )
                    }
                    diagnostics.log(DiagnosticsLog.Category.SCAN, "AUTO_RESCAN_DONE")
                    // Step past this slot so the same window cannot re-trigger.
                    delay(RESCAN_SETTLE_MS)
                } else {
                    delay(RESCAN_POLL_MS)
                }
            }
        }
    }

    private suspend fun requestSourceRefresh(
        trigger: SourceRefreshTrigger,
        origin: DiagnosticOrigin,
        awaitCompletion: Boolean,
    ) = refreshSource(trigger, origin, awaitCompletion)

    private companion object {
        /**
         * Poll interval for the rescan schedule. One minute matches the granularity of a
         * "HH:mm" setting; anything finer would burn wakeups for no visible benefit.
         */
        const val RESCAN_POLL_MS = 60_000L

        /** Step clear of the slot just served so it cannot re-trigger. */
        const val RESCAN_SETTLE_MS = 5L * 60_000L
    }
}
