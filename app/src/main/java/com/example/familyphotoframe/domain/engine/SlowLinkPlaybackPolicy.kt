package com.example.familyphotoframe.domain.engine

/** Pure state machine for switching a single remote source to cache-backed playback. */
object SlowLinkPlaybackPolicy {
    const val DEADLINE_WINDOW_MS = 5L * 60_000L
    const val DEADLINES_TO_ENTER = 3
    const val MIN_CACHED_PHOTOS = 2
    const val HEALTHY_COMPLETIONS_TO_EXIT = 3
    const val DEADLINE_FREE_EXIT_MS = 10L * 60_000L

    data class State(
        val active: Boolean = false,
        val recentDeadlinesMs: List<Long> = emptyList(),
        val lastDeadlineMs: Long = 0L,
        val completionsSinceDeadline: Int = 0,
    )

    fun onProgressDeadline(state: State, nowMs: Long, cachedPhotos: Int): State {
        val recent = (state.recentDeadlinesMs + nowMs)
            .filter { nowMs - it <= DEADLINE_WINDOW_MS }
            .takeLast(DEADLINES_TO_ENTER)
        return state.copy(
            active = state.active ||
                (recent.size >= DEADLINES_TO_ENTER && cachedPhotos >= MIN_CACHED_PHOTOS),
            recentDeadlinesMs = recent,
            lastDeadlineMs = nowMs,
            completionsSinceDeadline = 0,
        )
    }

    fun onRemoteCompletion(state: State, nowMs: Long): State {
        val completions = if (state.completionsSinceDeadline == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            state.completionsSinceDeadline + 1
        }
        val mayExit = state.active && completions >= HEALTHY_COMPLETIONS_TO_EXIT &&
            nowMs - state.lastDeadlineMs >= DEADLINE_FREE_EXIT_MS
        return state.copy(
            active = if (mayExit) false else state.active,
            completionsSinceDeadline = completions,
            recentDeadlinesMs = if (mayExit) emptyList() else state.recentDeadlinesMs,
        )
    }

    fun onIdleTimer(state: State, nowMs: Long): State {
        val mayExit = state.active &&
            state.completionsSinceDeadline >= HEALTHY_COMPLETIONS_TO_EXIT &&
            nowMs - state.lastDeadlineMs >= DEADLINE_FREE_EXIT_MS
        return if (mayExit) state.copy(active = false, recentDeadlinesMs = emptyList()) else state
    }
}
