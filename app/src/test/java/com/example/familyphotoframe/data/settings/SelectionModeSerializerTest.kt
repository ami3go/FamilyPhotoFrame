package com.example.familyphotoframe.data.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionModeSerializerTest {
    private val json = Json

    @Test
    fun legacyLeastRecentRandomDecodesToNoRepeatWithoutRuntimeEnumValue() {
        val decoded = json.decodeFromString(SelectionModeSerializer, "\"LEAST_RECENT_RANDOM\"")
        assertEquals(SelectionMode.SHUFFLE_NO_REPEAT, decoded)
        assertFalse(SelectionMode.entries.any { it.name == "LEAST_RECENT_RANDOM" })
    }

    @Test
    fun canonicalSerializationNeverWritesLegacyName() {
        val encoded = json.encodeToString(SelectionModeSerializer, SelectionMode.SHUFFLE_NO_REPEAT)
        assertTrue(encoded.contains("SHUFFLE_NO_REPEAT"))
        assertFalse(encoded.contains("LEAST_RECENT_RANDOM"))
    }
}
