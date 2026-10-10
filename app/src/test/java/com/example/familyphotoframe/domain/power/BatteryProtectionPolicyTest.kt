package com.example.familyphotoframe.domain.power

import com.example.familyphotoframe.domain.power.BatteryProtectionPolicy.Action
import com.example.familyphotoframe.domain.power.BatteryProtectionPolicy.DisableCause
import com.example.familyphotoframe.domain.power.BatteryProtectionPolicy.Input
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryProtectionPolicyTest {
    @Test fun disablesAtUpperCapacityBound() {
        val result = BatteryProtectionPolicy.decide(input(level = 75, charging = true))
        assertEquals(Action.DISABLE_CHARGING, result.action)
        assertEquals(DisableCause.CAPACITY, result.disableCause)
    }

    @Test fun capacityHysteresisResumesAtLowerBound() {
        val result = BatteryProtectionPolicy.decide(
            input(level = 60, charging = false, owned = true, cause = DisableCause.CAPACITY),
        )
        assertEquals(Action.ENABLE_CHARGING, result.action)
    }

    @Test fun temperatureCutoffUsesASeparateReleaseBand() {
        assertEquals(
            Action.DISABLE_CHARGING,
            BatteryProtectionPolicy.decide(input(level = 50, temperature = 420, charging = true)).action,
        )
        assertEquals(
            Action.KEEP,
            BatteryProtectionPolicy.decide(
                input(
                    level = 50,
                    temperature = 390,
                    charging = false,
                    owned = true,
                    cause = DisableCause.TEMPERATURE,
                ),
            ).action,
        )
        assertEquals(
            Action.ENABLE_CHARGING,
            BatteryProtectionPolicy.decide(
                input(
                    level = 50,
                    temperature = 380,
                    charging = false,
                    owned = true,
                    cause = DisableCause.TEMPERATURE,
                ),
            ).action,
        )
    }

    @Test fun unpluggingOrDisablingFeatureReleasesOnlyAppOwnedState() {
        assertEquals(
            Action.ENABLE_CHARGING,
            BatteryProtectionPolicy.decide(
                input(plugged = false, charging = false, owned = true),
            ).action,
        )
        assertEquals(
            Action.KEEP,
            BatteryProtectionPolicy.decide(
                input(enabled = false, charging = false, owned = false),
            ).action,
        )
    }

    @Test fun doesNotOverrideAnExternalChargeDisable() {
        val result = BatteryProtectionPolicy.decide(
            input(level = 40, charging = false, owned = false),
        )
        assertEquals(Action.KEEP, result.action)
        assertEquals("externally_disabled", result.reason)
    }

    private fun input(
        enabled: Boolean = true,
        plugged: Boolean? = true,
        level: Int? = 50,
        temperature: Int? = 300,
        charging: Boolean? = true,
        owned: Boolean = false,
        cause: DisableCause? = null,
    ) = Input(enabled, plugged, level, temperature, charging, owned, cause)
}
