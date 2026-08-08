package com.example.familyphotoframe.data.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Lightweight battery/charger snapshot used by long-running stability evidence.
 *
 * `ACTION_BATTERY_CHANGED` is a sticky system broadcast, so querying it once per existing
 * runtime-sample minute needs no receiver lifecycle and no permission. BatteryManager's
 * current/charge-counter properties are API 21, matching the application's minimum SDK;
 * OEMs may omit those optional properties, in which case the fields are simply absent.
 */
internal class BatteryTelemetry(private val appContext: Context) {
    fun fields(): Map<String, String> {
        val battery = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return mapOf("batteryTelemetryStatus" to "UNAVAILABLE")

        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val temperatureDeciC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val present = battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        return linkedMapOf<String, String>().apply {
            put("batteryTelemetryStatus", "OK")
            if (level >= 0 && scale > 0) {
                put("batteryLevelPct", ((level * 100) / scale).coerceIn(0, 100).toString())
            }
            put("batteryStatus", statusCode(status))
            put("powerSource", powerSourceCode(plugged))
            put("batteryPresent", present.toString())
            put("batteryHealth", healthCode(health))
            if (voltageMv >= 0) put("batteryVoltageMv", voltageMv.toString())
            if (temperatureDeciC != Int.MIN_VALUE) {
                put("batteryTempDeciC", temperatureDeciC.toString())
            }
            readIntProperty(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let {
                put("batteryCurrentUa", it.toString())
            }
            readIntProperty(manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.let {
                put("batteryChargeCounterUah", it.toString())
            }
        }
    }

    private fun readIntProperty(manager: BatteryManager?, property: Int): Int? {
        val value = runCatching { manager?.getIntProperty(property) }.getOrNull() ?: return null
        return value.takeUnless { it == Int.MIN_VALUE }
    }

    private fun statusCode(value: Int): String = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
        BatteryManager.BATTERY_STATUS_FULL -> "FULL"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
        else -> "UNKNOWN"
    }

    private fun powerSourceCode(value: Int): String {
        if (value == 0) return "NONE"
        val sources = buildList {
            if (value and BatteryManager.BATTERY_PLUGGED_AC != 0) add("AC")
            if (value and BatteryManager.BATTERY_PLUGGED_USB != 0) add("USB")
            if (value and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add("WIRELESS")
        }
        return sources.joinToString("+").ifEmpty { "OTHER" }
    }

    private fun healthCode(value: Int): String = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
        BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
        BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
        else -> "UNKNOWN"
    }
}
