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

class VendorGpsServiceGuardTest {
    @Test fun everyCommandIsIdentityGuardedAndUsesInitServiceControl() {
        listOf(VendorGpsServiceGuard.STOP_COMMAND, VendorGpsServiceGuard.START_COMMAND).forEach { command ->
            assertTrue(command.startsWith(RootCommandRunner.V80_IDENTITY_GUARD))
            assertTrue(command.contains("Type1000123456"))
            assertTrue(command.contains("getprop init.svc.gnssCG2000"))
            assertFalse(command.contains("kill"))
        }
        assertTrue(VendorGpsServiceGuard.STOP_COMMAND.contains("stop gnssCG2000"))
        assertTrue(VendorGpsServiceGuard.START_COMMAND.contains("start gnssCG2000"))
    }

    @Test fun classifiesShellResults() {
        assertEquals(VendorGpsServiceGuard.Result.STOPPED_NOW, VendorGpsServiceGuard.classify(0, "1\n"))
        assertEquals(VendorGpsServiceGuard.Result.ALREADY_STOPPED, VendorGpsServiceGuard.classify(0, "0"))
        assertEquals(VendorGpsServiceGuard.Result.STARTED, VendorGpsServiceGuard.classify(0, "2\n"))
        assertEquals(VendorGpsServiceGuard.Result.WRONG_DEVICE, VendorGpsServiceGuard.classify(41, ""))
        // The service refused to stop.
        assertEquals(VendorGpsServiceGuard.Result.FAILED, VendorGpsServiceGuard.classify(1, ""))
        assertEquals(VendorGpsServiceGuard.Result.FAILED, VendorGpsServiceGuard.classify(0, "su: denied"))
    }

    @Test fun timedOutRootShellIsUnavailableAndReaped() = runBlocking {
        val process = NeverExitingProcess()
        val runner = RootCommandRunner(
            processFactory = { process },
            commandTimeoutMs = 1L,
            pollIntervalMs = 1L,
            terminationGraceMs = 1L,
        )
        assertEquals(RootCommandRunner.Outcome.TimedOut, runner.run(VendorGpsServiceGuard.STOP_COMMAND))
        assertTrue(process.destroyCalled)
        assertTrue(process.stdout.closed)
    }

    private class NeverExitingProcess : Process() {
        val stdout = CloseAware()
        var destroyCalled = false
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = stdout
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = throw InterruptedException("never exits")
        override fun exitValue(): Int = throw IllegalThreadStateException("still running")
        override fun destroy() { destroyCalled = true }
    }

    private class CloseAware : ByteArrayInputStream(ByteArray(0)) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
}
