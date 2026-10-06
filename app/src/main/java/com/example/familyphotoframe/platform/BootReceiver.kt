package com.example.familyphotoframe.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.familyphotoframe.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives BOOT_COMPLETED and hands off to [BootStartupCoordinator]. Uses goAsync()
 * so the (suspend) settings read completes before the broadcast is released. The receiver
 * remains enabled so it can read the persisted preference; the coordinator launches only
 * when the user has turned on auto-start.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? App ?: return
        app.services.diagnostics.log(
            com.example.familyphotoframe.data.diagnostics.DiagnosticsLog.Category.APP,
            "BOOT_AUTOSTART",
            "sdkInt" to android.os.Build.VERSION.SDK_INT.toString(),
            "deviceModel" to "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}",
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                BootStartupCoordinator(app).onBootCompleted()
            } finally {
                pending.finish()
            }
        }
    }
}
