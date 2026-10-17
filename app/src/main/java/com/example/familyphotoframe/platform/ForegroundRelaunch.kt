package com.example.familyphotoframe.platform

import android.content.Context

/**
 * Persisted "the slideshow should be on screen" intent.
 *
 * A low-memory kill of the foreground frame delivers no onStop, so the flag survives it.
 * When something else (an alarm broadcast) later restarts the process without the
 * activity, the frame reopens itself instead of leaving the device on its home screen. A
 * user who leaves the frame while the screen is on clears the flag, so that choice holds.
 */
internal class ForegroundIntentStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun markShowing() = prefs.edit().putBoolean(KEY_SHOWING, true).apply()
    fun markLeft() = prefs.edit().putBoolean(KEY_SHOWING, false).apply()
    fun wasShowing(): Boolean = prefs.getBoolean(KEY_SHOWING, false)

    private companion object {
        const val PREFERENCES = "foreground_intent"
        const val KEY_SHOWING = "slideshow_should_be_visible"
    }
}

internal object ForegroundRelaunchPolicy {
    /** Grace for a launcher, boot or alarm start to bring the activity up on its own. */
    const val RELAUNCH_CHECK_DELAY_MS = 8_000L

    /**
     * A device reboot is owned by the boot auto-start setting, which may be deliberately
     * off; only a restart within the same boot after an unrequested death reopens the frame.
     */
    fun shouldRelaunch(
        wasShowing: Boolean,
        deviceReboot: Boolean,
        activityStartedThisProcess: Boolean,
    ): Boolean = wasShowing && !deviceReboot && !activityStartedThisProcess

    /** Screen-off stops are not a choice to leave; only an interactive stop is. */
    fun stopMeansUserLeft(screenInteractive: Boolean, changingConfigurations: Boolean): Boolean =
        screenInteractive && !changingConfigurations
}
