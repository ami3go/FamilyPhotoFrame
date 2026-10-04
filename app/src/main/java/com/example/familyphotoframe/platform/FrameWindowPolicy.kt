package com.example.familyphotoframe.platform

import android.view.Window
import android.view.WindowManager

/**
 * Window policy for an unattended photo frame.
 *
 * These legacy flags are deliberately used on every supported Android version because the
 * reference V80 runs API 22. They wake the panel, keep it awake, show the frame over keyguard,
 * and dismiss only a non-secure keyguard. Android still protects a PIN, pattern, or password;
 * the application never attempts to bypass secure credentials.
 */
@Suppress("DEPRECATION") // Required for the API-22 frame; modern Activity helpers start at API 27.
internal object FrameWindowPolicy {
    const val unattendedFrameFlags: Int =
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD

    fun apply(window: Window) {
        window.addFlags(unattendedFrameFlags)
    }
}
