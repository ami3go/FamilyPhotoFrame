package com.example.familyphotoframe.platform.power

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal root bridge for the one charge-control node verified on the V80.
 *
 * Commands are compile-time constants. Every command checks the device identity before
 * reading or writing, and no endpoint, setting value, or external input is interpolated.
 */
internal class RootChargeController {
    enum class State { ENABLED, DISABLED, THROTTLED }

    sealed interface Result {
        data class Success(val state: State) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun read(): Result = execute(READ_COMMAND)

    suspend fun write(enabled: Boolean): Result = execute(
        if (enabled) ENABLE_COMMAND else DISABLE_COMMAND,
    )

    private suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        val process = runCatching {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        }.getOrElse { return@withContext Result.Failure("root_unavailable") }

        val deadlineNanos = System.nanoTime() + COMMAND_TIMEOUT_MS * 1_000_000L
        var exitCode: Int? = null
        while (System.nanoTime() < deadlineNanos) {
            exitCode = runCatching { process.exitValue() }.getOrNull()
            if (exitCode != null) break
            Thread.sleep(POLL_INTERVAL_MS)
        }
        if (exitCode == null) {
            process.destroy()
            return@withContext Result.Failure("root_timeout")
        }
        val output = runCatching { process.inputStream.bufferedReader().use { it.readText() } }
            .getOrDefault("")
            .lineSequence()
            .map(String::trim)
            .lastOrNull { it == "0" || it == "1" || it == "2" }
        if (exitCode != 0) {
            return@withContext Result.Failure(
                when (exitCode) {
                    IDENTITY_MISMATCH_EXIT -> "wrong_device"
                    NODE_UNAVAILABLE_EXIT -> "charge_control_unavailable"
                    EXTERNAL_CONTROL_EXIT -> "charge_control_busy"
                    else -> "root_command_failed"
                },
            )
        }
        when (output) {
            "1" -> Result.Success(State.ENABLED)
            "0" -> Result.Success(State.DISABLED)
            "2" -> Result.Success(State.THROTTLED)
            else -> Result.Failure("invalid_charge_state")
        }
    }

    companion object {
        // First use can include SuperSU's user-approval dialog. This runs on Dispatchers.IO,
        // so allow enough time for that one-time authorization without blocking the UI.
        const val COMMAND_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 50L
        const val IDENTITY_MISMATCH_EXIT = 41
        const val NODE_UNAVAILABLE_EXIT = 42
        const val EXTERNAL_CONTROL_EXIT = 43
        const val NODE = "/sys/class/power_supply/dollar_cove_charger/charge_control_limit"
        const val MAX_NODE =
            "/sys/class/power_supply/dollar_cove_charger/charge_control_limit_max"
        const val IDENTITY_GUARD =
            "serial=\$(getprop ro.serialno); boot=\$(getprop ro.boot.serialno); " +
                "{ [ \"\$serial\" = \"Type1000123456\" ] || " +
                "[ \"\$boot\" = \"Type1000123456\" ]; } || exit 41; " +
                "[ -r \"$NODE\" ] && [ -r \"$MAX_NODE\" ] || exit 42; "
        const val READ_VALUES =
            "limit=\$(cat \"$NODE\"); max=\$(cat \"$MAX_NODE\"); " +
                "[ \"\$max\" -ge 2 ] || exit 42; target=\$((max - 1)); "
        const val CLASSIFY =
            "if [ \"\$limit\" = \"\$target\" ]; then echo 0; " +
                "elif [ \"\$limit\" = \"0\" ]; then echo 1; else echo 2; fi"
        const val WRITE_GUARD =
            IDENTITY_GUARD + READ_VALUES
        const val READ_COMMAND = IDENTITY_GUARD + READ_VALUES + CLASSIFY
        // The V80's Intel charging framework immediately rewrites enable_charging. Its
        // charge-control limit is the durable API the framework itself respects: max - 1 is
        // the driver's disable-charging state and zero is normal. Any other state belongs to
        // thermal control, so the guarded writes refuse to override it. The kernel publishes
        // the scalar as 0444; root temporarily exposes owner-write and restores 0444 after
        // verified read-back. Android 5.1's toolbox has `echo` but no `printf`.
        const val ENABLE_COMMAND =
            WRITE_GUARD + "[ \"\$limit\" = \"\$target\" ] || exit 43; " +
                "chmod 0644 \"$NODE\" || exit 42; echo 0 > \"$NODE\"; result=\$?; " +
                "actual=\$(cat \"$NODE\"); chmod 0444 \"$NODE\" || exit 42; " +
                "[ \"\$result\" = \"0\" ] && [ \"\$actual\" = \"0\" ] || exit 1; echo 1"
        const val DISABLE_COMMAND =
            WRITE_GUARD + "[ \"\$limit\" = \"0\" ] || exit 43; " +
                "chmod 0644 \"$NODE\" || exit 42; echo \"\$target\" > \"$NODE\"; " +
                "result=\$?; actual=\$(cat \"$NODE\"); chmod 0444 \"$NODE\" || exit 42; " +
                "[ \"\$result\" = \"0\" ] && [ \"\$actual\" = \"\$target\" ] || exit 1; echo 0"
    }
}
