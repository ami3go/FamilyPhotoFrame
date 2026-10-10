package com.example.familyphotoframe.platform.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.familyphotoframe.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Wakes the process for a bounded battery-protection evaluation. */
internal class BatteryProtectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? App ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.evaluateBatteryProtection("alarm")
            } finally {
                pending.finish()
            }
        }
    }
}
