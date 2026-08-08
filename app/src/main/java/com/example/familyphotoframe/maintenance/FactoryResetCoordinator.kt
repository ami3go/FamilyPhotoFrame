package com.example.familyphotoframe.maintenance

import androidx.room.RoomDatabase
import com.example.familyphotoframe.data.cache.MediaCache
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.SettingsRepository
import com.example.familyphotoframe.web.WebUploadManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Application-lifetime, single-flight factory reset.
 *
 * Room owns one transaction for every table through [RoomDatabase.clearAllTables].
 * DataStore and cache files cannot share that SQLite transaction, so the complete
 * reset is deliberately idempotent and awaited. A caller disappearing does not cancel
 * the work because the deferred belongs to this coordinator's supervisor scope.
 * Completed local-upload photo files are preserved; only sessions/staging, indexes,
 * settings, credentials, trust records, shuffle state and caches are removed.
 */
class FactoryResetCoordinator(
    private val database: RoomDatabase,
    private val settings: SettingsRepository,
    private val mediaCache: MediaCache,
    private val uploadManager: WebUploadManager,
    private val diagnostics: DiagnosticsLog,
    private val io: CoroutineDispatcher,
    private val clearMemoryCache: () -> Unit,
    private val clearPreview: () -> Unit,
) {
    data class Outcome(
        val uploadedPhotosPreserved: Boolean = true,
    )

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val flightLock = Any()
    private var inFlight: Deferred<Outcome>? = null

    suspend fun reset(): Outcome {
        val work = synchronized(flightLock) {
            inFlight ?: scope.async { performReset() }.also { inFlight = it }
        }
        // Once the destructive operation is admitted, a browser disconnect must not
        // leave it half-applied merely because the request coroutine was cancelled.
        return withContext(NonCancellable) { work.await() }
    }

    private suspend fun performReset(): Outcome {
        val operation = diagnostics.operations.start("FACTORY_RESET", DiagnosticOrigin.WEB_UI)
        diagnostics.logEvent(
            "WEB_FACTORY_RESET_STARTED",
            mapOf(
                "stage" to "QUIESCE",
                "uploadedPhotosPreserved" to "true",
            ),
            operation.context(),
        )
        var stage = "QUIESCE"
        return try {
            uploadManager.quiesceForFactoryReset()

            stage = "CACHE_CLEAR"
            mediaCache.clear()
            clearMemoryCache()
            clearPreview()

            stage = "ROOM_TRANSACTION"
            withContext(io) {
                // Room implements this as one transaction and clears every current table,
                // including photos, shuffle state, credentials and remembered browsers.
                database.clearAllTables()
            }

            stage = "SETTINGS_RESET"
            settings.update { AppSettings() }

            diagnostics.logEvent(
                "WEB_FACTORY_RESET_COMPLETED",
                mapOf(
                    "stage" to "TERMINAL",
                    "outcome" to "COMPLETED",
                    "uploadedPhotosPreserved" to "true",
                ),
                operation.context(),
            )
            Outcome()
        } catch (error: Exception) {
            diagnostics.logEvent(
                "WEB_FACTORY_RESET_FAILED",
                mapOf(
                    "stage" to stage,
                    "outcome" to "FAILED",
                    "errorClass" to error.javaClass.simpleName.ifBlank { "UNKNOWN" },
                    "uploadedPhotosPreserved" to "true",
                ),
                operation.context(),
            )
            throw error
        } finally {
            diagnostics.operations.finish(operation.operationId)
        }
    }
}
