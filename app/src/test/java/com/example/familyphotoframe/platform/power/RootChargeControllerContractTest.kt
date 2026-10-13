package com.example.familyphotoframe.platform.power

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootChargeControllerContractTest {
    @Test fun runtimeRootCheckFailsClosedWithinReceiverBudget() {
        assertTrue(RootChargeController.COMMAND_TIMEOUT_MS <= 8_000L)
        assertTrue(RootChargeController.TERMINATION_GRACE_MS <= 1_000L)
    }

    @Test fun timedOutProcessIsDestroyedAndEveryPipeIsClosed() = runBlocking {
        val process = NeverExitingProcess()
        val result = RootChargeController(
            processFactory = { process },
            commandTimeoutMs = 1L,
            pollIntervalMs = 1L,
            terminationGraceMs = 1L,
        ).read()

        assertEquals(RootChargeController.Result.Failure("root_timeout"), result)
        assertTrue(process.destroyCalled)
        assertTrue(process.stdin.closed)
        assertTrue(process.stdout.closed)
        assertTrue(process.stderr.closed)
    }

    @Test fun api22CommandsUseVerifiedToolboxPrimitivesAndIdentityGuard() {
        val commands = listOf(
            RootChargeController.READ_COMMAND,
            RootChargeController.ENABLE_COMMAND,
            RootChargeController.DISABLE_COMMAND,
        )

        commands.forEach { command ->
            assertTrue(command.contains("getprop ro.serialno"))
            assertTrue(command.contains("getprop ro.boot.serialno"))
            assertTrue(command.contains("Type1000123456"))
            assertFalse(command.contains("printf"))
        }
        commands.forEach { command ->
            assertTrue(command.contains("charge_control_limit"))
            assertTrue(command.contains("charge_control_limit_max"))
        }
        assertTrue(RootChargeController.ENABLE_COMMAND.contains("echo 0"))
        assertTrue(RootChargeController.DISABLE_COMMAND.contains("echo \"\$target\""))
        listOf(
            RootChargeController.ENABLE_COMMAND,
            RootChargeController.DISABLE_COMMAND,
        ).forEach { command ->
            assertTrue(command.contains("chmod 0644"))
            assertTrue(command.contains("chmod 0444"))
            assertTrue(command.indexOf("chmod 0644") < command.indexOf("echo"))
            assertTrue(command.indexOf("chmod 0444") > command.indexOf("cat"))
        }
        assertFalse(RootChargeController.READ_COMMAND.contains("chmod"))
        assertTrue(RootChargeController.ENABLE_COMMAND.contains("exit 43"))
        assertTrue(RootChargeController.DISABLE_COMMAND.contains("exit 43"))
        assertTrue(RootChargeController.READ_COMMAND.contains("echo 2"))
    }

    private class NeverExitingProcess : Process() {
        val stdin = CloseAwareOutputStream()
        val stdout = CloseAwareInputStream()
        val stderr = CloseAwareInputStream()
        var destroyCalled = false

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = stderr
        override fun waitFor(): Int = throw InterruptedException("never exits")
        override fun exitValue(): Int = throw IllegalThreadStateException("still running")
        override fun destroy() {
            destroyCalled = true
        }
    }

    private class CloseAwareInputStream : ByteArrayInputStream(ByteArray(0)) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class CloseAwareOutputStream : ByteArrayOutputStream() {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
