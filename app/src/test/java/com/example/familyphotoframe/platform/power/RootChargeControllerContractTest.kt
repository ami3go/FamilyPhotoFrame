package com.example.familyphotoframe.platform.power

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootChargeControllerContractTest {
    @Test fun rootAuthorizationWindowOutlivesSuperSuPromptCountdown() {
        assertTrue(RootChargeController.COMMAND_TIMEOUT_MS >= 20_000L)
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
}
