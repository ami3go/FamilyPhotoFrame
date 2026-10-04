package com.example.familyphotoframe.platform

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class FrameWindowPolicyTest {
    @Test
    fun `unattended frame wakes and dismisses only through public window flags`() {
        val expected = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD

        assertEquals(expected, FrameWindowPolicy.unattendedFrameFlags)
        assertTrue(
            FrameWindowPolicy.unattendedFrameFlags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
        )
    }
}
