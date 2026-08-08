package com.example.familyphotoframe.domain.schedule

/** Pure wall-clock selection for an ordered brightness timeline. */
object BrightnessTimeline {
    /**
     * Return the original list index of the period active at [nowMinutes].
     *
     * Invalid persisted/imported times are ignored.  If every period is invalid the
     * caller gets null and can safely fall back to manual brightness instead of losing
     * the scheduler coroutine to `last()` on an empty list.
     */
    fun activeIndex(startTimes: List<String>, nowMinutes: Int): Int? {
        val valid = startTimes.mapIndexedNotNull { index, text ->
            SleepSchedule.parseMinutes(text)?.let { minute -> minute to index }
        }.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
        if (valid.isEmpty()) return null
        val now = ((nowMinutes % SleepSchedule.MINUTES_PER_DAY) + SleepSchedule.MINUTES_PER_DAY) %
            SleepSchedule.MINUTES_PER_DAY
        return (valid.lastOrNull { it.first <= now } ?: valid.last()).second
    }
}
