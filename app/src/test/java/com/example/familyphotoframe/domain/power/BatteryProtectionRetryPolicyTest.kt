package com.example.familyphotoframe.domain.power

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryProtectionRetryPolicyTest {
    @Test fun unavailableRootOpensTheCircuitProgressively() {
        assertEquals(
            BatteryProtectionRetryPolicy.FIRST_FAILURE_DELAY_MS,
            BatteryProtectionRetryPolicy.delayMs(1, appOwnsDisabledState = false),
        )
        assertEquals(
            BatteryProtectionRetryPolicy.SECOND_FAILURE_DELAY_MS,
            BatteryProtectionRetryPolicy.delayMs(2, appOwnsDisabledState = false),
        )
        assertEquals(
            BatteryProtectionRetryPolicy.OPEN_CIRCUIT_DELAY_MS,
            BatteryProtectionRetryPolicy.delayMs(3, appOwnsDisabledState = false),
        )
        assertEquals(
            BatteryProtectionRetryPolicy.OPEN_CIRCUIT_DELAY_MS,
            BatteryProtectionRetryPolicy.delayMs(100, appOwnsDisabledState = false),
        )
    }

    @Test fun ownedDisabledStateRetainsHourlyRecoveryCheck() {
        assertEquals(
            BatteryProtectionRetryPolicy.OWNED_STATE_MAX_DELAY_MS,
            BatteryProtectionRetryPolicy.delayMs(3, appOwnsDisabledState = true),
        )
    }
}
