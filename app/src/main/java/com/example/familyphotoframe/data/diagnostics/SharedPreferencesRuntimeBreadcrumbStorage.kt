package com.example.familyphotoframe.data.diagnostics

import android.content.Context
import android.content.SharedPreferences

/** Android app-private backend for [PersistentRuntimeBreadcrumbs]. */
class SharedPreferencesRuntimeBreadcrumbStorage(
    context: Context,
) : PersistentRuntimeBreadcrumbs.Storage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): PersistentRuntimeBreadcrumbs.Breadcrumb? {
        val sequence = preferences.getLong(KEY_SEQUENCE, 0L)
        if (sequence <= 0L) return null
        return PersistentRuntimeBreadcrumbs.Breadcrumb(
            sequence = sequence,
            sessionId = preferences.getString(KEY_SESSION_ID, "").orEmpty(),
            operation = preferences.getString(KEY_OPERATION, "UNKNOWN").orEmpty(),
            stage = preferences.getString(KEY_STAGE, "UNKNOWN").orEmpty(),
            active = preferences.getBoolean(KEY_ACTIVE, false),
            presentationToken = preferences.getString(KEY_PRESENTATION_TOKEN, "").orEmpty(),
            sourceKind = preferences.getString(KEY_SOURCE_KIND, "NONE").orEmpty(),
            updatedAtEpochMs = preferences.getLong(KEY_UPDATED_AT_EPOCH_MS, 0L),
            updatedElapsedRealtimeMs = preferences.getLong(KEY_UPDATED_ELAPSED_MS, 0L),
        )
    }

    override fun write(
        value: PersistentRuntimeBreadcrumbs.Breadcrumb,
        synchronous: Boolean,
    ): Boolean {
        val editor = preferences.edit()
            .putLong(KEY_SEQUENCE, value.sequence)
            .putString(KEY_SESSION_ID, value.sessionId)
            .putString(KEY_OPERATION, value.operation)
            .putString(KEY_STAGE, value.stage)
            .putBoolean(KEY_ACTIVE, value.active)
            .putString(KEY_PRESENTATION_TOKEN, value.presentationToken)
            .putString(KEY_SOURCE_KIND, value.sourceKind)
            .putLong(KEY_UPDATED_AT_EPOCH_MS, value.updatedAtEpochMs)
            .putLong(KEY_UPDATED_ELAPSED_MS, value.updatedElapsedRealtimeMs)
        return if (synchronous) editor.commit() else {
            editor.apply()
            true
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "runtime_breadcrumb_v1"
        const val KEY_SEQUENCE = "sequence"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_OPERATION = "operation"
        const val KEY_STAGE = "stage"
        const val KEY_ACTIVE = "active"
        const val KEY_PRESENTATION_TOKEN = "presentation_token"
        const val KEY_SOURCE_KIND = "source_kind"
        const val KEY_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms"
        const val KEY_UPDATED_ELAPSED_MS = "updated_elapsed_ms"
    }
}
