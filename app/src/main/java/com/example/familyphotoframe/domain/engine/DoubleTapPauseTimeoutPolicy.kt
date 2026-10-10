package com.example.familyphotoframe.domain.engine

/** Canonical choices for automatically recovering from an accidental double-tap pause. */
internal object DoubleTapPauseTimeoutPolicy {
    const val NEVER = 0
    const val DEFAULT_MINUTES = 5
    val optionsMinutes: List<Int> = listOf(NEVER, 1, 5, 10, 15, 30, 60)

    fun normalize(minutes: Int): Int =
        minutes.takeIf { it in optionsMinutes } ?: DEFAULT_MINUTES

    fun delayMillis(minutes: Int): Long = normalize(minutes).toLong() * 60_000L
}

/** Generation guard that prevents an expired timer from resuming a later/manual pause. */
internal class DoubleTapPauseTimeoutState {
    data class Arm(val generation: Long, val timeoutMinutes: Int, val delayMillis: Long)

    private var generation = 0L
    private var armedGeneration: Long? = null
    private var pendingGeneration: Long? = null

    @Synchronized
    fun onToggle(nowPaused: Boolean, source: String, configuredMinutes: Int): Arm? {
        generation += 1L
        armedGeneration = null
        pendingGeneration = null
        val minutes = DoubleTapPauseTimeoutPolicy.normalize(configuredMinutes)
        if (!nowPaused || source != "double_tap" || minutes == DoubleTapPauseTimeoutPolicy.NEVER) {
            return null
        }
        armedGeneration = generation
        return Arm(generation, minutes, DoubleTapPauseTimeoutPolicy.delayMillis(minutes))
    }

    @Synchronized
    fun markElapsed(elapsedGeneration: Long): Boolean {
        if (armedGeneration != elapsedGeneration || generation != elapsedGeneration) return false
        pendingGeneration = elapsedGeneration
        return true
    }

    @Synchronized
    fun consumePending(commandGeneration: Long? = null, paused: Boolean): Boolean {
        val pending = pendingGeneration ?: return false
        if (commandGeneration != null && commandGeneration != pending) return false
        pendingGeneration = null
        armedGeneration = null
        if (!paused || pending != generation) return false
        generation += 1L
        return true
    }

    @Synchronized
    fun cancel() {
        generation += 1L
        armedGeneration = null
        pendingGeneration = null
    }
}
