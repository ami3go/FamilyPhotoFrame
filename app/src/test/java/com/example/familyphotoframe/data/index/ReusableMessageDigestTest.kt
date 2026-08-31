package com.example.familyphotoframe.data.index

import com.example.familyphotoframe.util.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

class ReusableMessageDigestTest {
    @Test
    fun createsProviderContextOnceAndResetsBetweenFiles() {
        var factoryCalls = 0
        val reusable = ReusableMessageDigest {
            factoryCalls++
            MessageDigest.getInstance("SHA-256")
        }

        val first = reusable.resetForNextHash().digest("first".toByteArray()).toHexString()
        val second = reusable.resetForNextHash().digest("second".toByteArray()).toHexString()

        assertEquals(1, factoryCalls)
        assertNotEquals(first, second)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest("second".toByteArray()).toHexString(),
            second,
        )
    }
}
