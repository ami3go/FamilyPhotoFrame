package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.FilterSettings
import com.example.familyphotoframe.data.settings.PlaylistScheduleRule
import com.example.familyphotoframe.data.settings.PlaylistSettings
import com.example.familyphotoframe.data.settings.SlideshowPlaylist
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSettingsJsonTest {
    @Test
    fun primitiveCollectionsAreProjectedAsJsonElements() {
        val settings = AppSettings(
            selectedFolders = setOf("Trip B", "Trip A"),
            filters = FilterSettings(
                includeGlobs = listOf("*.jpg", "*.png"),
                excludeGlobs = listOf("Thumbs.db"),
                excludeFolders = listOf("@eaDir"),
            ),
            playlists = PlaylistSettings(
                playlists = listOf(
                    SlideshowPlaylist(id = "custom", name = "Custom"),
                ),
                activePlaylistId = "custom",
                defaultPlaylistId = "custom",
                scheduleRules = listOf(
                    PlaylistScheduleRule(
                        id = "weekday",
                        name = "Weekdays",
                        playlistId = "custom",
                        daysOfWeek = setOf(5, 1, 3),
                    ),
                ),
            ),
        )

        val json = WebSettingsJson.redactedConfig(settings, nextScheduleAction = "none")

        assertEquals(
            listOf("Trip A", "Trip B"),
            json.getValue("selectedFolders").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("*.jpg", "*.png"),
            json.getValue("includeGlobs").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("Thumbs.db"),
            json.getValue("excludeGlobs").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("@eaDir"),
            json.getValue("excludeFolders").jsonArray.map { it.jsonPrimitive.content },
        )
        val firstRule = json.getValue("playlistScheduleRules").jsonArray.first()
        assertEquals(
            listOf(1, 3, 5),
            firstRule.jsonObject.getValue("daysOfWeek").jsonArray.map { it.jsonPrimitive.content.toInt() },
        )
    }
}
