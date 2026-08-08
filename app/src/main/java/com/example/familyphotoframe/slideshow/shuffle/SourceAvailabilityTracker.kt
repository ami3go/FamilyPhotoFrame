package com.example.familyphotoframe.slideshow.shuffle

/** Pure bounded source-level outage/backoff policy; callers own actual connection checks. */
class SourceAvailabilityTracker(
    private val backoffMs: List<Long> = listOf(30_000L, 120_000L, 300_000L, 900_000L),
) {
    data class State(
        val unavailable: Boolean = false,
        val consecutiveFailures: Int = 0,
        val retryAtEpochMs: Long = 0L,
        val reason: String? = null,
    )

    private val states = linkedMapOf<String, State>()

    fun failure(sourceId: String, reason: String?, now: Long): State {
        val previous = states[sourceId] ?: State()
        val failures = previous.consecutiveFailures + 1
        val delay = backoffMs[(failures - 1).coerceAtMost(backoffMs.lastIndex)]
        return State(true, failures, now + delay, reason).also { states[sourceId] = it }
    }

    fun recovered(sourceId: String): State = State().also { states[sourceId] = it }

    fun state(sourceId: String): State = states[sourceId] ?: State()
    fun unavailableSourceIds(): Set<String> = states.filterValues { it.unavailable }.keys
    fun shouldRetry(sourceId: String, now: Long): Boolean = state(sourceId).let { it.unavailable && now >= it.retryAtEpochMs }
}
