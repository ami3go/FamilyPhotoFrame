package com.example.familyphotoframe.domain.power

/**
 * Pure policy for the optional, rooted V80 charge limiter.
 *
 * The wide 60-75% band prevents rapid charge toggling. Temperature has its own wider
 * release band because repeatedly resuming a warm battery is worse than allowing the
 * charge level to fall for a while.
 */
internal object BatteryProtectionPolicy {
    const val STOP_CHARGING_PERCENT = 75
    const val RESUME_CHARGING_PERCENT = 60
    const val STOP_CHARGING_TEMPERATURE_DECI_C = 420
    const val RESUME_CHARGING_TEMPERATURE_DECI_C = 380

    enum class Action { KEEP, ENABLE_CHARGING, DISABLE_CHARGING }
    enum class DisableCause { CAPACITY, TEMPERATURE }

    data class Input(
        val enabled: Boolean,
        val plugged: Boolean?,
        val levelPercent: Int?,
        val temperatureDeciC: Int?,
        val chargingEnabled: Boolean?,
        val appOwnsDisabledState: Boolean,
        val previousDisableCause: DisableCause? = null,
    )

    data class Decision(
        val action: Action,
        val reason: String,
        val disableCause: DisableCause? = null,
    )

    fun decide(input: Input): Decision {
        if (!input.enabled) {
            return releaseOwnedState(input, "feature_disabled")
        }
        if (input.plugged == false) {
            return releaseOwnedState(input, "power_disconnected")
        }
        if (input.chargingEnabled == null) {
            return Decision(Action.KEEP, "charge_state_unavailable")
        }

        if (input.chargingEnabled) {
            if (input.temperatureDeciC != null &&
                input.temperatureDeciC >= STOP_CHARGING_TEMPERATURE_DECI_C
            ) {
                return Decision(
                    Action.DISABLE_CHARGING,
                    "temperature_high",
                    DisableCause.TEMPERATURE,
                )
            }
            if (input.levelPercent != null && input.levelPercent >= STOP_CHARGING_PERCENT) {
                return Decision(
                    Action.DISABLE_CHARGING,
                    "charge_upper_bound",
                    DisableCause.CAPACITY,
                )
            }
            return Decision(Action.KEEP, "inside_charge_band")
        }

        // Never take ownership of a charge state another tool or the kernel disabled.
        if (!input.appOwnsDisabledState) {
            return Decision(Action.KEEP, "externally_disabled")
        }
        if (input.levelPercent == null || input.plugged == null) {
            return Decision(Action.ENABLE_CHARGING, "telemetry_unavailable_fail_safe")
        }

        val temperature = input.temperatureDeciC
        if (temperature != null && temperature >= STOP_CHARGING_TEMPERATURE_DECI_C) {
            return Decision(
                Action.KEEP,
                "temperature_still_high",
                input.previousDisableCause ?: DisableCause.TEMPERATURE,
            )
        }
        if (input.previousDisableCause == DisableCause.TEMPERATURE &&
            (temperature == null || temperature > RESUME_CHARGING_TEMPERATURE_DECI_C)
        ) {
            return Decision(Action.KEEP, "temperature_hysteresis", DisableCause.TEMPERATURE)
        }
        if (input.levelPercent <= RESUME_CHARGING_PERCENT) {
            return Decision(Action.ENABLE_CHARGING, "charge_lower_bound")
        }
        return Decision(
            Action.KEEP,
            "inside_discharge_band",
            input.previousDisableCause ?: DisableCause.CAPACITY,
        )
    }

    private fun releaseOwnedState(input: Input, reason: String): Decision =
        if (input.appOwnsDisabledState && input.chargingEnabled == false) {
            Decision(Action.ENABLE_CHARGING, reason)
        } else {
            Decision(Action.KEEP, reason)
        }
}
