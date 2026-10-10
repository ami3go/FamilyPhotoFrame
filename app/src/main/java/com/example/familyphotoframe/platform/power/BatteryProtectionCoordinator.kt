package com.example.familyphotoframe.platform.power

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.familyphotoframe.data.diagnostics.BatteryTelemetry
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.settings.SettingsRepository
import com.example.familyphotoframe.domain.power.BatteryProtectionPolicy
import com.example.familyphotoframe.domain.power.BatteryProtectionPolicy.Action
import com.example.familyphotoframe.domain.power.BatteryProtectionPolicy.DisableCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates persisted settings, battery telemetry, the root bridge, and wake-up alarms. */
internal class BatteryProtectionCoordinator(
    context: Context,
    private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsLog,
    private val scope: CoroutineScope,
    private val controller: RootChargeController = RootChargeController(),
) {
    private val appContext = context.applicationContext
    private val telemetry = BatteryTelemetry(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    fun start() {
        scope.launch {
            settings.settings
                .map { it.batteryProtectionEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) evaluateAndSchedule("setting_enabled")
                    else releaseAndCancel()
                }
        }
    }

    suspend fun evaluateAndSchedule(trigger: String) {
        mutex.withLock {
            val enabled = settings.settings.first().batteryProtectionEnabled
            if (!enabled) {
                releaseOwnedState("feature_disabled")
                cancelAlarm()
                return
            }

            val reading = telemetry.snapshot()
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP_MR1) {
                logFailure("BATTERY_PROTECTION_UNAVAILABLE", trigger, "unsupported_android_version", reading)
                cancelAlarm()
                return
            }
            var owned = preferences.getBoolean(KEY_APP_OWNS_DISABLED_STATE, false)
            var previousCause = preferences.getString(KEY_DISABLE_CAUSE, null)
                ?.let { runCatching { DisableCause.valueOf(it) }.getOrNull() }
            val readResult = controller.read()
            val chargeState = (readResult as? RootChargeController.Result.Success)?.state
            if (readResult is RootChargeController.Result.Failure) {
                logFailure("BATTERY_PROTECTION_UNAVAILABLE", trigger, readResult.reason, reading)
                scheduleNext(owned)
                return
            }
            // Sysfs charge state normally resets on reboot. Do not keep stale ownership
            // after the kernel has already restored charging.
            if (owned && chargeState != RootChargeController.State.DISABLED) {
                preferences.edit()
                    .remove(KEY_APP_OWNS_DISABLED_STATE)
                    .remove(KEY_DISABLE_CAUSE)
                    .apply()
                owned = false
                previousCause = null
            }

            val decision = BatteryProtectionPolicy.decide(
                BatteryProtectionPolicy.Input(
                    enabled = true,
                    plugged = reading.plugged,
                    levelPercent = reading.levelPercent,
                    temperatureDeciC = reading.temperatureDeciC,
                    chargingEnabled = when (chargeState) {
                        RootChargeController.State.ENABLED -> true
                        RootChargeController.State.DISABLED,
                        RootChargeController.State.THROTTLED,
                        null -> false
                    },
                    appOwnsDisabledState = owned,
                    previousDisableCause = previousCause,
                ),
            )
            val result = when (decision.action) {
                Action.KEEP -> null
                Action.ENABLE_CHARGING -> controller.write(true)
                Action.DISABLE_CHARGING -> controller.write(false)
            }
            if (result is RootChargeController.Result.Failure) {
                logFailure("BATTERY_PROTECTION_FAILED", trigger, result.reason, reading)
            } else if (result is RootChargeController.Result.Success) {
                val nowOwned = decision.action == Action.DISABLE_CHARGING
                preferences.edit()
                    .putBoolean(KEY_APP_OWNS_DISABLED_STATE, nowOwned)
                    .apply {
                        if (nowOwned) putString(KEY_DISABLE_CAUSE, decision.disableCause?.name)
                        else remove(KEY_DISABLE_CAUSE)
                    }
                    .apply()
                diagnostics.logEvent(
                    if (nowOwned) "BATTERY_CHARGING_PAUSED" else "BATTERY_CHARGING_RESUMED",
                    eventFields(trigger, decision.reason, reading, nowOwned),
                    DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                )
            }
            diagnostics.logEvent(
                "BATTERY_PROTECTION_EVALUATED",
                eventFields(
                    trigger = trigger,
                    reason = decision.reason,
                    reading = reading,
                    owned = preferences.getBoolean(KEY_APP_OWNS_DISABLED_STATE, false),
                ) + mapOf("action" to decision.action.name.lowercase()),
                DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
            )
            scheduleNext(preferences.getBoolean(KEY_APP_OWNS_DISABLED_STATE, false))
        }
    }

    private suspend fun releaseAndCancel() = mutex.withLock {
        releaseOwnedState("feature_disabled")
        cancelAlarm()
    }

    private suspend fun releaseOwnedState(reason: String) {
        if (!preferences.getBoolean(KEY_APP_OWNS_DISABLED_STATE, false)) return
        val reading = telemetry.snapshot()
        when (val result = controller.write(true)) {
            is RootChargeController.Result.Success -> {
                preferences.edit()
                    .remove(KEY_APP_OWNS_DISABLED_STATE)
                    .remove(KEY_DISABLE_CAUSE)
                    .apply()
                diagnostics.logEvent(
                    "BATTERY_CHARGING_RESUMED",
                    eventFields("settings", reason, reading, false),
                    DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
                )
            }
            is RootChargeController.Result.Failure ->
                logFailure("BATTERY_PROTECTION_FAILED", "settings", result.reason, reading)
        }
    }

    private fun eventFields(
        trigger: String,
        reason: String,
        reading: BatteryTelemetry.Snapshot,
        owned: Boolean,
    ): Map<String, String> = buildMap {
        put("trigger", trigger)
        put("reason", reason)
        put("batteryTelemetryStatus", reading.status)
        reading.levelPercent?.let { put("batteryLevelPct", it.toString()) }
        reading.temperatureDeciC?.let { put("batteryTempDeciC", it.toString()) }
        reading.plugged?.let { put("powerConnected", it.toString()) }
        put("appOwnsDisabledState", owned.toString())
    }

    private fun logFailure(
        code: String,
        trigger: String,
        reason: String,
        reading: BatteryTelemetry.Snapshot,
    ) {
        diagnostics.logEvent(
            code,
            eventFields(trigger, reason, reading, preferences.getBoolean(KEY_APP_OWNS_DISABLED_STATE, false)),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
    }

    @Suppress("DEPRECATION")
    private fun scheduleNext(chargeDisabledByApp: Boolean) {
        val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + if (chargeDisabledByApp) {
            DISABLED_CHECK_INTERVAL_MS
        } else {
            ENABLED_CHECK_INTERVAL_MS
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent())
            } else {
                alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent())
            }
        }.onFailure {
            diagnostics.logEvent(
                "BATTERY_PROTECTION_FAILED",
                mapOf("trigger" to "schedule", "reason" to "alarm_schedule_failed"),
                DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
            )
        }
    }

    private fun cancelAlarm() {
        (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent())
    }

    private fun alarmIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            Intent(appContext, BatteryProtectionReceiver::class.java),
            flags,
        )
    }

    private companion object {
        const val PREFERENCES = "battery_charge_protection"
        const val KEY_APP_OWNS_DISABLED_STATE = "app_owns_disabled_state"
        const val KEY_DISABLE_CAUSE = "disable_cause"
        const val ALARM_REQUEST_CODE = 0x4250
        const val ENABLED_CHECK_INTERVAL_MS = 15L * 60_000L
        const val DISABLED_CHECK_INTERVAL_MS = 5L * 60_000L
    }
}
