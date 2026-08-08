package com.example.familyphotoframe.domain.schedule

import com.example.familyphotoframe.data.settings.PlaylistScheduleRule
import java.util.Calendar

/** Pure, API-21-compatible playlist schedule evaluation. */
object PlaylistSchedule {
    data class Match(val rule: PlaylistScheduleRule, val reason: String)

    fun activeRule(
        rules: List<PlaylistScheduleRule>,
        nowEpochMs: Long,
        timeZoneId: String = java.util.TimeZone.getDefault().id,
    ): Match? {
        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone(timeZoneId)).apply {
            timeInMillis = nowEpochMs
        }
        val isoDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return rules.asSequence()
            .filter { it.enabled && isoDay in it.daysOfWeek }
            .filter { dateMatches(it, calendar) }
            .filter { timeMatches(it, nowMinutes) }
            .sortedWith(
                compareByDescending<PlaylistScheduleRule> { it.priority }
                    .thenByDescending { dateSpecificity(it) }
                    .thenByDescending { it.updatedAtEpochMs }
                    .thenBy { it.id },
            )
            .firstOrNull()
            ?.let { Match(it, "priority=${it.priority};weekday=$isoDay") }
    }

    fun minutesUntilBoundary(rules: List<PlaylistScheduleRule>, nowEpochMs: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val candidates = rules.filter { it.enabled }.flatMap { rule ->
            listOfNotNull(SleepSchedule.parseMinutes(rule.startTime), SleepSchedule.parseMinutes(rule.endTime))
        }
        if (candidates.isEmpty()) return 60
        return candidates.minOf { target ->
            val delta = (target - now + SleepSchedule.MINUTES_PER_DAY) % SleepSchedule.MINUTES_PER_DAY
            if (delta == 0) 1 else delta
        }.coerceIn(1, 60)
    }

    private fun timeMatches(rule: PlaylistScheduleRule, nowMinutes: Int): Boolean {
        val start = SleepSchedule.parseMinutes(rule.startTime) ?: return false
        val end = SleepSchedule.parseMinutes(rule.endTime) ?: return false
        return if (start == end) true else SleepSchedule.isAsleep(nowMinutes, start, end)
    }

    private fun dateMatches(rule: PlaylistScheduleRule, calendar: Calendar): Boolean {
        val today = "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
        return (rule.startDateIso == null || today >= rule.startDateIso) &&
            (rule.endDateIso == null || today <= rule.endDateIso)
    }

    private fun dateSpecificity(rule: PlaylistScheduleRule): Int =
        (if (rule.startDateIso != null) 1 else 0) + (if (rule.endDateIso != null) 1 else 0)
}
