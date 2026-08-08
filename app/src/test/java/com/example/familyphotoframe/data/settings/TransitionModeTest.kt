package com.example.familyphotoframe.data.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionModeTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun serializerWritesStableLowercaseIdentifier() {
        val encoded = json.encodeToString(TransitionModeSerializer, TransitionMode.GENTLE_ZOOM_IN)
        assertEquals("\"gentle_zoom_in\"", encoded)
    }

    @Test
    fun serializerReadsLegacyEnumNames() {
        assertEquals(
            TransitionMode.CROSSFADE,
            json.decodeFromString(TransitionModeSerializer, "\"CROSSFADE\""),
        )
        assertEquals(
            TransitionMode.HORIZONTAL_GLIDE,
            json.decodeFromString(TransitionModeSerializer, "\"SLIDE\""),
        )
    }

    @Test
    fun unknownValueFallsBackToCrossfade() {
        assertEquals(
            TransitionMode.CROSSFADE,
            json.decodeFromString(TransitionModeSerializer, "\"future_effect\""),
        )
    }

    @Test
    fun allTenEffectsHaveStableIdentifiers() {
        assertEquals(10, TransitionMode.selectableValues.size)
        assertEquals(10, TransitionMode.selectableValues.map { it.storageValue }.toSet().size)
        assertEquals(TransitionMode.DEPTH_FADE, TransitionMode.fromStorage("depth_fade"))
        assertEquals(TransitionMode.SOFT_FOCUS_FADE, TransitionMode.fromStorage("SOFT_FOCUS_FADE"))
    }

    @Test
    fun selectionModeSerializerUsesStableIdentifiers() {
        val encoded = json.encodeToString(
            TransitionSelectionModeSerializer,
            TransitionSelectionMode.AMBIENT_RANDOM,
        )
        assertEquals("\"ambient_random\"", encoded)
        assertEquals(
            TransitionSelectionMode.FIXED,
            json.decodeFromString(TransitionSelectionModeSerializer, "\"future_mode\""),
        )
    }
}
