package com.example.familyphotoframe.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Typed DataStore serializer for [AppSettings] backed by kotlinx.serialization JSON.
 *
 * Using a typed DataStore (not Preferences DataStore) keeps a single, unambiguous
 * runtime config object (spec §3 "avoid ambiguous DataStore-vs-JSON runtime split").
 * On corruption it returns defaults instead of crashing (Contract Rule 1: survive
 * bad state). The same [json] format is reused for import/export later.
 */
object AppSettingsSerializer : Serializer<AppSettings> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    override val defaultValue: AppSettings = AppSettings().withCurrentDefaults()

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            json.decodeFromString(AppSettings.serializer(), BoundedTextInput.readUtf8(input))
                .withCurrentDefaults()
        } catch (e: SerializationException) {
            throw CorruptionException("Settings file is unreadable", e)
        } catch (e: ImportTooLargeException) {
            throw CorruptionException("Settings file is oversized", e)
        }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}

class SettingsRepository(context: Context) {

    private val dataStore: DataStore<AppSettings> = DataStoreFactory.create(
        serializer = AppSettingsSerializer,
        corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler {
            AppSettings().withCurrentDefaults() // recover to current defaults; never crash
        },
        produceFile = { File(context.filesDir, "datastore/app_settings.json") },
    )

    val settings: Flow<AppSettings> = dataStore.data

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData(transform)
    }

    /** One atomic DataStore write for the complete folder selection. Empty means all. */
    suspend fun setSelectedFolders(folderKeys: Set<String>) {
        val normalized = folderKeys.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        dataStore.updateData { current ->
            if (current.selectedFolders == normalized) current
            else current.copy(selectedFolders = normalized)
        }
    }
}
