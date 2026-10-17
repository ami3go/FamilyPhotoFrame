package com.example.familyphotoframe.platform.power

import android.os.Build
import com.example.familyphotoframe.data.diagnostics.DiagnosticContext
import com.example.familyphotoframe.data.diagnostics.DiagnosticOrigin
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the V80's vendor GPS daemon (`gnssCG2000`) stopped.
 *
 * On the V80 that init service leaks native memory continuously (measured 18–34 MB/h,
 * with or without location enabled) until, after one to two days, Android's low-memory
 * killer removes every app including the foreground frame. The frame never uses device
 * location, so the service is stopped through init; it comes back only on reboot, which
 * this guard catches at the next process start. Identity-guarded like battery care: any
 * other device exits without touching a service.
 */
internal class VendorGpsServiceGuard(
    private val settings: SettingsRepository,
    private val diagnostics: DiagnosticsLog,
    private val scope: CoroutineScope,
    private val runner: RootCommandRunner = RootCommandRunner(),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    enum class Result { STOPPED_NOW, ALREADY_STOPPED, STARTED, WRONG_DEVICE, UNAVAILABLE, FAILED }

    private var lastReportedFailure: Result? = null
    private var previouslyEnabled: Boolean? = null

    fun start() {
        if (sdkInt != Build.VERSION_CODES.LOLLIPOP_MR1) return
        scope.launch {
            settings.settings
                .map { it.stopLeakingGpsService }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    val wasEnabled = previouslyEnabled
                    previouslyEnabled = enabled
                    if (!enabled) {
                        // Turning the option off returns the device to its stock state; a
                        // process that starts with it already off touches nothing.
                        if (wasEnabled == true) report(execute(START_COMMAND), trigger = "disabled")
                        return@collectLatest
                    }
                    delay(STARTUP_DELAY_MS)
                    var failures = 0
                    while (true) {
                        val result = execute(STOP_COMMAND)
                        report(result, trigger = "check")
                        if (result == Result.WRONG_DEVICE) return@collectLatest
                        failures = if (result == Result.UNAVAILABLE || result == Result.FAILED) failures + 1 else 0
                        delay(nextCheckDelayMs(failures))
                    }
                }
        }
    }

    suspend fun execute(command: String): Result = when (val outcome = runner.run(command)) {
        RootCommandRunner.Outcome.Unavailable, RootCommandRunner.Outcome.TimedOut -> Result.UNAVAILABLE
        is RootCommandRunner.Outcome.Exited -> classify(outcome.exitCode, outcome.output)
    }

    private fun report(result: Result, trigger: String) {
        val code = when (result) {
            Result.STOPPED_NOW -> "VENDOR_GPS_SERVICE_STOPPED"
            Result.STARTED -> "VENDOR_GPS_SERVICE_STARTED"
            Result.ALREADY_STOPPED, Result.WRONG_DEVICE -> null
            Result.UNAVAILABLE, Result.FAILED ->
                // One record per distinct failure, not one per hourly check.
                if (lastReportedFailure == result) null else "VENDOR_GPS_SERVICE_CHECK_FAILED"
        }
        lastReportedFailure = result.takeIf { it == Result.UNAVAILABLE || it == Result.FAILED }
        code ?: return
        diagnostics.logEvent(
            code,
            mapOf("result" to result.name.lowercase(), "trigger" to trigger),
            DiagnosticContext(origin = DiagnosticOrigin.SYSTEM),
        )
    }

    companion object {
        const val SERVICE = "gnssCG2000"
        const val STARTUP_DELAY_MS = 30_000L
        const val CHECK_INTERVAL_MS = 60L * 60_000L

        /**
         * Root is briefly unavailable right after boot (SuperSU's daemon still starting) and
         * after an app update until SuperSU re-grants it; meanwhile the daemon keeps leaking,
         * so a failed check retries within minutes rather than waiting an hour.
         */
        private val FAILURE_RETRY_DELAYS_MS = longArrayOf(2L * 60_000L, 5L * 60_000L, 15L * 60_000L)

        internal fun nextCheckDelayMs(consecutiveFailures: Int): Long =
            if (consecutiveFailures <= 0) CHECK_INTERVAL_MS
            else FAILURE_RETRY_DELAYS_MS.getOrElse(consecutiveFailures - 1) { CHECK_INTERVAL_MS }

        private const val STATE = "\$(getprop init.svc.$SERVICE)"
        /** Prints 1 when it stopped a running service, 0 when it was already stopped. */
        const val STOP_COMMAND = RootCommandRunner.V80_IDENTITY_GUARD +
            "if [ \"$STATE\" = \"running\" ]; then stop $SERVICE; sleep 1; " +
            "[ \"$STATE\" = \"running\" ] && exit 1; echo 1; else echo 0; fi"
        /** Prints 2 once the service is running again. */
        const val START_COMMAND = RootCommandRunner.V80_IDENTITY_GUARD +
            "[ \"$STATE\" = \"running\" ] || start $SERVICE; echo 2"

        internal fun classify(exitCode: Int, output: String): Result {
            if (exitCode == RootCommandRunner.IDENTITY_MISMATCH_EXIT) return Result.WRONG_DEVICE
            if (exitCode != 0) return Result.FAILED
            return when (output.lineSequence().map(String::trim).lastOrNull { it.isNotEmpty() }) {
                "1" -> Result.STOPPED_NOW
                "0" -> Result.ALREADY_STOPPED
                "2" -> Result.STARTED
                else -> Result.FAILED
            }
        }
    }
}
