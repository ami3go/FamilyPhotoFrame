package com.example.familyphotoframe.platform.power

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bounded execution of one compile-time root shell command.
 *
 * Shared by the V80 root features so every `su` invocation fails closed within a short
 * budget and always releases its process: Android 5.1 keeps a native process-waiter thread
 * for each Process, and dropping one after destroy() can leave that waiter resident.
 */
internal class RootCommandRunner(
    private val processFactory: (String) -> Process = { command ->
        ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    },
    private val commandTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val terminationGraceMs: Long = DEFAULT_TERMINATION_GRACE_MS,
) {
    sealed interface Outcome {
        data class Exited(val exitCode: Int, val output: String) : Outcome
        data object Unavailable : Outcome
        data object TimedOut : Outcome
    }

    suspend fun run(command: String): Outcome = withContext(Dispatchers.IO) {
        val process = runCatching { processFactory(command) }
            .getOrElse { return@withContext Outcome.Unavailable }
        try {
            val exitCode = awaitExit(process, commandTimeoutMs)
            if (exitCode == null) {
                // Close the pipes and give the wrapper a bounded reap window before return.
                terminateAndReap(process)
                return@withContext Outcome.TimedOut
            }
            val output = runCatching { process.inputStream.bufferedReader().use { it.readText() } }
                .getOrDefault("")
            Outcome.Exited(exitCode, output)
        } finally {
            closeProcessStreams(process)
        }
    }

    private fun awaitExit(process: Process, timeoutMs: Long): Int? {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            runCatching { process.exitValue() }.getOrNull()?.let { return it }
            Thread.sleep(pollIntervalMs)
        }
        return runCatching { process.exitValue() }.getOrNull()
    }

    private fun terminateAndReap(process: Process) {
        closeProcessStreams(process)
        runCatching { process.destroy() }
        awaitExit(process, terminationGraceMs)
        closeProcessStreams(process)
    }

    private fun closeProcessStreams(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_TERMINATION_GRACE_MS = 1_000L
        const val DEFAULT_POLL_INTERVAL_MS = 50L

        /** Only the verified V80 may run any root command; exit 41 otherwise. */
        const val V80_IDENTITY_GUARD =
            "serial=\$(getprop ro.serialno); boot=\$(getprop ro.boot.serialno); " +
                "{ [ \"\$serial\" = \"Type1000123456\" ] || " +
                "[ \"\$boot\" = \"Type1000123456\" ]; } || exit 41; "
        const val IDENTITY_MISMATCH_EXIT = 41
    }
}
