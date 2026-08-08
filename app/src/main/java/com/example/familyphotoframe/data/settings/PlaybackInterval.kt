package com.example.familyphotoframe.data.settings

/** Canonical limits and button behavior for the slideshow interval. */
object PlaybackInterval {
    const val MIN_SECONDS: Int = 3
    const val MAX_SECONDS: Int = 600
    const val BUTTON_STEP_SECONDS: Int = 5

    fun clamp(seconds: Int): Int = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun adjust(seconds: Int, deltaSeconds: Int): Int =
        (seconds.toLong() + deltaSeconds.toLong())
            .coerceIn(MIN_SECONDS.toLong(), MAX_SECONDS.toLong())
            .toInt()
}
