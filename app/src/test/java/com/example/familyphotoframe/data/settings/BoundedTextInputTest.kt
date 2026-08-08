package com.example.familyphotoframe.data.settings

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedTextInputTest {
    @Test fun readsAtTheConfiguredLimit() {
        val bytes = ByteArray(1024) { 'a'.code.toByte() }
        assertEquals("a".repeat(1024), BoundedTextInput.readUtf8(ByteArrayInputStream(bytes), 1024))
    }

    @Test(expected = ImportTooLargeException::class)
    fun rejectsOneBytePastTheConfiguredLimit() {
        BoundedTextInput.readUtf8(ByteArrayInputStream(ByteArray(1025)), 1024)
    }
}
