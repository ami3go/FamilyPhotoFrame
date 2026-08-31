package com.example.familyphotoframe.data.settings

/**
 * Single compatibility/canonicalization boundary for persisted settings.
 *
 * Runtime code should not contain historical migration branches. Old schema values are
 * accepted here, converted once, and the returned object always obeys the current safe
 * bounds before it reaches the slideshow engine or network schedulers.
 */
internal object AppSettingsCanonicalizer {
    private const val BRIGHTNESS_AUTOMATION_MIGRATION_V1 = 1

    fun normalize(settings: AppSettings): AppSettings {
        val migrationPending =
            settings.brightnessAutomationMigrationVersion < BRIGHTNESS_AUTOMATION_MIGRATION_V1
        val shouldImportLegacySleep =
            migrationPending && settings.schedule.sleepEnabled &&
                settings.brightnessAutomation == BrightnessAutomationSettings()

        val migratedBrightness = if (shouldImportLegacySleep) {
            val dayBrightness = safeBrightness(settings.schedule.brightnessDay, 1f)
            val nightBrightness = safeBrightness(settings.schedule.brightnessNight, 0.3f)
            settings.brightnessAutomation.copy(
                mode = BrightnessMode.SCHEDULED,
                manualBrightness = dayBrightness,
                periods = listOf(
                    BrightnessPeriod(
                        id = "day",
                        startTime = safeClock(settings.schedule.sleepEnd, "07:00"),
                        brightness = dayBrightness,
                        action = NightAction.DIM_ONLY,
                    ),
                    BrightnessPeriod(
                        id = "night",
                        startTime = safeClock(settings.schedule.sleepStart, "23:00"),
                        brightness = nightBrightness,
                        action = NightAction.PAUSE_SLIDESHOW,
                    ),
                ),
            ).normalized()
        } else {
            settings.brightnessAutomation.normalized()
        }

        val migratedSchedule = if (migrationPending) {
            // Compatibility fields remain serializable for old backups/clients, but the
            // obsolete runtime switch is always disabled after migration.
            settings.schedule.copy(sleepEnabled = false)
        } else {
            settings.schedule
        }

        return settings.copy(
            intervalSeconds = PlaybackInterval.clamp(settings.intervalSeconds),
            transitionDurationMs = settings.transitionDurationMs.coerceIn(300, 2_000),
            portraitCollage = settings.portraitCollage.normalized(),
            temporarilySuppressAfterDecodeFailures =
                settings.temporarilySuppressAfterDecodeFailures.coerceAtLeast(1),
            brightnessAutomationMigrationVersion = BRIGHTNESS_AUTOMATION_MIGRATION_V1,
            schedule = migratedSchedule,
            filters = settings.filters.withCurrentDefaultFormats(),
            selectedFolders = normalizeFolderSelection(settings.selectedFolders),
            playlists = settings.playlists.withCurrentDefaults(),
            brightnessAutomation = migratedBrightness,
            web = settings.web.copy(
                rememberedBrowsers = settings.web.rememberedBrowsers.normalized(),
            ),
            webUpload = settings.webUpload.normalized(),
            localThumbnailCache = settings.localThumbnailCache.normalized(),
            onThisDay = settings.onThisDay.normalized(),
        )
    }

    private fun safeBrightness(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0.01f, 1f) else fallback

    private fun safeClock(value: String, fallback: String): String {
        val match = CLOCK.matchEntire(value.trim()) ?: return fallback
        val hour = match.groupValues[1].toIntOrNull() ?: return fallback
        val minute = match.groupValues[2].toIntOrNull() ?: return fallback
        if (hour !in 0..23 || minute !in 0..59) return fallback
        return "%02d:%02d".format(hour, minute)
    }

    private val CLOCK = Regex("^(\\d{1,2}):(\\d{2})$")
}
