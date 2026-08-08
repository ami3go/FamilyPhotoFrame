package com.example.familyphotoframe.platform

import android.content.Intent
import com.example.familyphotoframe.App
import com.example.familyphotoframe.MainActivity
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import kotlinx.coroutines.flow.first

/**
 * Decides what to do on device boot (spec §13.3). API-aware and defensive: launching
 * an Activity from a boot broadcast is restricted on Android 10+ (background activity
 * start limits), so the attempt is wrapped and the outcome logged rather than crashing.
 * If blocked, the user must enable the OEM auto-start/battery exemption — surfaced in
 * setup guidance and the known-limitations doc.
 */
class BootStartupCoordinator(private val app: App) {

    suspend fun onBootCompleted() {
        val diag = app.services.diagnostics
        val settings = app.services.settings.settings.first()
        if (!settings.autoStartOnBoot) {
            diag.log(DiagnosticsLog.Category.APP, "BOOT_AUTOSTART_DISABLED")
            return
        }
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            diag.log(DiagnosticsLog.Category.APP, "BOOT_AUTOSTART_LAUNCHED")
        } catch (e: Exception) {
            diag.log(
                DiagnosticsLog.Category.APP,
                "BOOT_AUTOSTART_BLOCKED",
                "errorClass" to e.javaClass.simpleName,
            )
        }
    }
}
