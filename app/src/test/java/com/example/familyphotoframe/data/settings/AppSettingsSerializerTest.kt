package com.example.familyphotoframe.data.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppSettingsSerializerTest {

    @Test
    fun roundTrip_preservesValues() = runBlocking {
        val original = AppSettings(
            intervalSeconds = 42,
            aspectMode = AspectMode.FILL_CROP,
            overlays = OverlaySettings(clock24h = false, folderShow = false),
            source = ActiveSource(
                kind = ActiveSourceKind.LOCAL_SAF,
                treeUri = "content://tree/abc",
                displayName = "Family",
            ),
        )
        val out = ByteArrayOutputStream()
        AppSettingsSerializer.writeTo(original, out)
        val restored = AppSettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
        // readFrom normalises via withCurrentDefaults(); writeTo does not. Comparing against
        // the normalised original tests serialization fidelity rather than that asymmetry.
        assertEquals(original.withCurrentDefaults(), restored)
    }

    @Test
    fun defaultValue_hasSchemaVersion2() {
        assertEquals(2, AppSettingsSerializer.defaultValue.schemaVersion)
    }

    @Test
    fun intervalClamp_isApplied() {
        assertEquals(3, AppSettings(intervalSeconds = 1).intervalSecondsClamped)
        assertEquals(600, AppSettings(intervalSeconds = 9999).intervalSecondsClamped)
        assertEquals(20, AppSettings(intervalSeconds = 20).intervalSecondsClamped)
    }

    @Test
    fun unknownKeys_areIgnored_notCrash() = runBlocking {
        val json = """{"schemaVersion":2,"intervalSeconds":10,"somethingNew":123}"""
        val restored = AppSettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertEquals(10, restored.intervalSeconds)
    }

    @Test
    fun defaults_includeIphoneHeicAndHeif() {
        val includes = AppSettings().filters.includeGlobs
        assertTrue("*.heic" in includes)
        assertTrue("*.heif" in includes)
    }

    @Test
    fun legacyDefaultFilters_gainIphoneFormats() = runBlocking {
        val json = """
            {
              "schemaVersion": 2,
              "filters": {
                "includeGlobs": ["*.jpg", "*.jpeg", "*.png", "*.webp"]
              }
            }
        """.trimIndent()

        val restored = AppSettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))

        assertTrue("*.heic" in restored.filters.includeGlobs)
        assertTrue("*.heif" in restored.filters.includeGlobs)
    }

    @Test
    fun customFilters_areNotOverwrittenByDefaultMigration() = runBlocking {
        val json = """
            {
              "schemaVersion": 2,
              "filters": {
                "includeGlobs": ["holiday-*.jpg"]
              }
            }
        """.trimIndent()

        val restored = AppSettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))

        assertEquals(listOf("holiday-*.jpg"), restored.filters.includeGlobs)
        assertFalse("*.heic" in restored.filters.includeGlobs)
    }

    @Test
    fun legacySettingsGainSafeTransitionDefaults() = runBlocking {
        val restored = AppSettingsSerializer.readFrom(
            ByteArrayInputStream("""{"schemaVersion":2,"transition":"SLIDE"}""".toByteArray()),
        )
        assertEquals(TransitionSelectionMode.FIXED, restored.transitionSelectionMode)
        assertEquals(TransitionMode.HORIZONTAL_GLIDE, restored.transition)
        assertFalse(restored.transitionReduceMotion)
        assertEquals(900, restored.transitionDurationMs)
    }
    @Test
    fun newInstallDefaultsToFolderBalancedShuffle() {
        assertEquals(SelectionMode.FOLDER_BALANCED_SHUFFLE, AppSettings().selectionMode)
    }

    @Test
    fun legacyRandomMigratesOnceToGlobalNoRepeat() = runBlocking {
        val restored = AppSettingsSerializer.readFrom(
            ByteArrayInputStream(
                """{"schemaVersion":2,"selectionMode":"LEAST_RECENT_RANDOM"}""".toByteArray(),
            ),
        )
        assertEquals(SelectionMode.SHUFFLE_NO_REPEAT, restored.selectionMode)
        assertEquals(1, restored.playbackOrderMigrationVersion)
        assertEquals(
            SelectionMode.SHUFFLE_NO_REPEAT,
            restored.withCurrentDefaults().selectionMode,
        )
    }

    @Test
    fun explicitSequentialAndExistingModesAreNotMigrated() {
        assertEquals(
            SelectionMode.SEQUENTIAL,
            AppSettings(selectionMode = SelectionMode.SEQUENTIAL).withCurrentDefaults().selectionMode,
        )
        assertEquals(
            SelectionMode.FOLDER_BALANCED_SHUFFLE,
            AppSettings(selectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE).withCurrentDefaults().selectionMode,
        )
    }

}
