package com.example.familyphotoframe.ui.slideshow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndeliveredOwnershipGuardTest {
    @Test
    fun closeReleasesArmedValueExactlyOnce() {
        val value = Any()
        val released = mutableListOf<Any>()
        val guard = UndeliveredOwnershipGuard<Any>(released::add)

        guard.arm(value)
        guard.close()
        guard.close()

        assertTrue(released.single() === value)
    }

    @Test
    fun deliveredValueIsNotReleasedByClose() {
        val value = Any()
        val released = mutableListOf<Any>()
        val guard = UndeliveredOwnershipGuard<Any>(released::add)

        guard.arm(value)

        assertTrue(guard.markDelivered(value))
        guard.close()
        assertTrue(released.isEmpty())
    }

    @Test
    fun deliveryUsesIdentityRatherThanEquality() {
        data class EqualValue(val id: Int)

        val armed = EqualValue(1)
        val equalButDifferent = EqualValue(1)
        val released = mutableListOf<EqualValue>()
        val guard = UndeliveredOwnershipGuard<EqualValue>(released::add)

        guard.arm(armed)

        assertFalse(guard.markDelivered(equalButDifferent))
        guard.close()
        assertTrue(released.single() === armed)
    }
}
