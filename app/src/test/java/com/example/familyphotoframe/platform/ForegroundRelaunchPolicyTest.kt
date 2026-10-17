package com.example.familyphotoframe.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRelaunchPolicyTest {
    @Test fun killedVisibleFrameRestartedByAnAlarmReopens() {
        assertTrue(ForegroundRelaunchPolicy.shouldRelaunch(wasShowing = true, deviceReboot = false, activityStartedThisProcess = false))
    }

    @Test fun deviceRebootIsLeftToTheBootAutoStartSetting() {
        assertFalse(ForegroundRelaunchPolicy.shouldRelaunch(wasShowing = true, deviceReboot = true, activityStartedThisProcess = false))
    }

    @Test fun anActivityThatAlreadyStartedIsNotLaunchedAgain() {
        assertFalse(ForegroundRelaunchPolicy.shouldRelaunch(wasShowing = true, deviceReboot = false, activityStartedThisProcess = true))
    }

    @Test fun aUserWhoLeftTheFrameIsRespected() {
        assertFalse(ForegroundRelaunchPolicy.shouldRelaunch(wasShowing = false, deviceReboot = false, activityStartedThisProcess = false))
    }

    @Test fun onlyAnInteractiveStopCountsAsLeaving() {
        assertTrue(ForegroundRelaunchPolicy.stopMeansUserLeft(screenInteractive = true, changingConfigurations = false))
        assertFalse(ForegroundRelaunchPolicy.stopMeansUserLeft(screenInteractive = false, changingConfigurations = false))
        assertFalse(ForegroundRelaunchPolicy.stopMeansUserLeft(screenInteractive = true, changingConfigurations = true))
    }
}
