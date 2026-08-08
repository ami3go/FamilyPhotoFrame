package com.example.familyphotoframe.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostLifecycleGateTest {
    @Test fun stopAndRestartNeverRevalidateOldWork() {
        val gate = HostLifecycleGate()
        assertNull(gate.tokenIfActive())

        val first = gate.start()
        assertTrue(gate.isCurrent(first))

        gate.stop()
        assertFalse(gate.isCurrent(first))
        assertNull(gate.tokenIfActive())

        val second = gate.start()
        assertTrue(second > first)
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}
