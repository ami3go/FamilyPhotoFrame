package com.example.familyphotoframe.data.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

data class ProcessExitEvidence(
    val fingerprint: String,
    val reasonCode: Int,
    val reasonName: String,
    val timestampMs: Long,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val descriptionCode: String,
    val correlatedEnvelope: Boolean,
)

/** API-30+ historical process-exit reader with committed deduplication. */
class ProcessExitInspector(private val app: Application) {
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun inspect(correlatedEnvelope: Boolean): ProcessExitEvidence? {
        if (Build.VERSION.SDK_INT < 30) return null
        return Api30.read(app, correlatedEnvelope)
            ?.takeUnless { it.fingerprint == preferences.getString(KEY_LAST_FINGERPRINT, null) }
    }

    fun markReported(evidence: ProcessExitEvidence): Boolean =
        preferences.edit().putString(KEY_LAST_FINGERPRINT, evidence.fingerprint).commit()

    @RequiresApi(30)
    private object Api30 {
        fun read(app: Application, correlatedEnvelope: Boolean): ProcessExitEvidence? {
            val manager = app.getSystemService(ActivityManager::class.java) ?: return null
            val exit = manager.getHistoricalProcessExitReasons(app.packageName, 0, MAX_RESULTS)
                .maxByOrNull { it.timestamp } ?: return null
            val fingerprint = "${exit.timestamp}:${exit.reason}:${exit.pid}"
            return ProcessExitEvidence(
                fingerprint = fingerprint,
                reasonCode = exit.reason,
                reasonName = ProcessExitReasonMapper.name(exit.reason),
                timestampMs = exit.timestamp,
                importance = exit.importance,
                pssKb = exit.pss.coerceAtLeast(0L),
                rssKb = exit.rss.coerceAtLeast(0L),
                descriptionCode = ProcessExitReasonMapper.descriptionCode(exit.description),
                correlatedEnvelope = correlatedEnvelope,
            )
        }
    }

    private companion object {
        const val PREFERENCES = "diagnostics_process_exit"
        const val KEY_LAST_FINGERPRINT = "last_fingerprint"
        const val MAX_RESULTS = 8
    }
}
