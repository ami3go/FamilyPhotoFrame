package com.example.familyphotoframe.domain.engine

/**
 * Generation gate for work whose result may only be committed while the slideshow host
 * remains in the same started lifecycle generation.
 *
 * Cancellation alone is not sufficient for legacy/network bitmap work: a blocking decoder can
 * return after its coroutine was cancelled.  A token captured before that work is therefore
 * revalidated before any prepared presentation becomes visible or advances playback state.
 */
internal class HostLifecycleGate {
    private var generation: Long = 0L
    private var active: Boolean = false

    @Synchronized
    fun start(): Long {
        generation++
        active = true
        return generation
    }

    @Synchronized
    fun stop(): Long {
        generation++
        active = false
        return generation
    }

    /** Current generation when started, otherwise null. */
    @Synchronized
    fun tokenIfActive(): Long? = generation.takeIf { active }

    /** True only if no stop/start boundary has happened since [token] was captured. */
    @Synchronized
    fun isCurrent(token: Long): Boolean = active && generation == token
}
