package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.settings.AppSettings
import com.example.familyphotoframe.data.settings.PlaylistSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Pure JSON projection for the non-secret web settings model. */
internal object WebSettingsJson {
    fun redactedConfig(
        settings: AppSettings,
        nextScheduleAction: String,
    ): JsonObject {
        val s = settings
        val dayPeriod = s.brightnessAutomation.periods.firstOrNull { it.id == "day" }
        val nightPeriod = s.brightnessAutomation.periods.firstOrNull { it.id == "night" }
        val scheduledBrightness = s.brightnessAutomation.mode.name.startsWith("SCHEDULED")
        return buildJsonObject {
            put("schemaVersion", s.schemaVersion)
            put("intervalSeconds", s.intervalSecondsClamped)
            put("aspectMode", s.aspectMode.name)
            put("transitionSelectionMode", s.transitionSelectionMode.storageValue)
            put("transition", s.transition.storageValue)
            put("transitionReduceMotion", s.transitionReduceMotion)
            put("motion", s.motion.name)
            put("decodeColorDepth", s.decodeColorDepth.name)
            put("decodeResolution", s.decodeResolution.name)
            put("cachePlaybackPool", s.cachePlaybackPool)
            put("showPerformanceOverlay", s.showPerformanceOverlay)
            put("portraitCollageMode", s.portraitCollage.mode.name)
            put("portraitCollageMaxPhotos", s.portraitCollage.maxPhotosClamped)
            put("portraitCollageFallback", s.portraitCollage.fallback.name)
            put("portraitCollageGap", s.portraitCollage.gap.name)
            put("portraitCollageOrientationFilter", s.portraitCollage.orientationFilter.name)
            put("portraitCollageFillOtherOrientations", s.portraitCollage.fillWithOtherOrientations)
            put("portraitCollageLayoutPreference", s.portraitCollage.layoutPreference.name)
            put("portraitCollageScaleMode", s.portraitCollage.scaleMode.name)
            put("portraitCollageAlignment", s.portraitCollage.alignment.name)
            put("portraitCollageBackground", s.portraitCollage.background.name)
            put("portraitCollageCornerRadiusDp", s.portraitCollage.cornerRadiusDpClamped)
            put("portraitCollageAnimateThree", s.portraitCollage.animateThreePhotoFrames)
            put("transitionDurationMs", s.transitionDurationMs)
            put("clockShow", s.overlays.clockShow)
            put("dateShow", s.overlays.dateShow)
            put("folderShow", s.overlays.folderShow)
            put("clock24h", s.overlays.clock24h)
            put("weatherShow", s.overlays.weatherShow)
            put("photoDateShow", s.overlays.photoDateShow)
            put("captionShow", s.overlays.captionShow)
            put("locationShow", s.overlays.locationShow)
            put("overlayOpacity", s.overlays.opacity)
            // Overlay anchors — the last on-device-only overlay control (DPAD_WEB_PARITY.md).
            put("clockPosition", s.overlays.clockPosition.name)
            put("datePosition", s.overlays.datePosition.name)
            put("folderPosition", s.overlays.folderPosition.name)
            put("weatherPosition", s.overlays.weatherPosition.name)
            put("photoDatePosition", s.overlays.photoDatePosition.name)
            put("captionPosition", s.overlays.captionPosition.name)
            put("locationPosition", s.overlays.locationPosition.name)
            put("overlayScrim", s.overlays.scrim)
            // Playback selection and curation (spec §9.3, §9.4, §9.6).
            put("alsoPlay", s.source.alsoPlay.joinToString(",") { it.name })
            put("selectionMode", s.selectionMode.name)
            put("favoritesOnly", s.favoritesOnly)
            put("selectedFolders", buildJsonArray { s.selectedFolders.sorted().forEach { add(JsonPrimitive(it)) } })
            put("selectedFoldersCsv", s.selectedFolders.sorted().joinToString(","))
            put("autoRescanEnabled", s.schedule.autoRescanEnabled)
            put("autoRescanAt", s.schedule.autoRescanAt)
            put("autoRescanDays", s.schedule.autoRescanDays)
            put("onUnreachable", s.onUnreachable.name)
            put("autoStartOnBoot", s.autoStartOnBoot)
            // Backward-compatible aliases for pre-unification web clients.
            put("sleepEnabled", scheduledBrightness)
            put("sleepStart", nightPeriod?.startTime ?: s.schedule.sleepStart)
            put("sleepEnd", dayPeriod?.startTime ?: s.schedule.sleepEnd)
            put("sourceKind", s.source.kind.name)
            put("smbHost", s.source.smb?.host ?: "")
            put("smbShare", s.source.smb?.share ?: "")
            put("smbPath", s.source.smb?.path ?: "")
            put("smbUser", s.source.smb?.user ?: "")
            put("smbDomain", s.source.smb?.domain ?: "")
            // Synology non-secret fields. The pinned certificate fingerprint is
            // exposed read-only: it is public information the server presents to
            // anyone, and showing it lets a remote admin confirm what the frame
            // currently trusts — but it is deliberately NOT accepted back on POST.
            put("synBaseUrl", s.source.synology?.baseUrl ?: "")
            put("synFolderPath", s.source.synology?.folderPath ?: "")
            put("synUser", s.source.synology?.user ?: "")
            put("synUseThumbnails", s.source.synology?.useThumbnails ?: true)
            put("synPinnedCert", s.source.synology?.pinnedCertSha256 ?: "")
            put("davBaseUrl", s.source.webdav?.baseUrl ?: "")
            put("davRootPath", s.source.webdav?.rootPath ?: "")
            put("davFolderPath", s.source.webdav?.folderPath ?: "")
            put("davUser", s.source.webdav?.user ?: "")
            put("davPasswordSet", !s.source.webdav?.credentialRef.isNullOrEmpty())
            put("smbPasswordSet", !s.source.smb?.credentialRef.isNullOrEmpty())
            put("synPasswordSet", !s.source.synology?.credentialRef.isNullOrEmpty())
            put("includeGlobs", buildJsonArray { s.filters.cleanIncludes.forEach { add(JsonPrimitive(it)) } })
            put("excludeGlobs", buildJsonArray { s.filters.cleanExcludes.forEach { add(JsonPrimitive(it)) } })
            put("excludeFolders", buildJsonArray { s.filters.cleanExcludeFolders.forEach { add(JsonPrimitive(it)) } })
            put("includeSubfolders", s.filters.includeSubfolders)
            put("brightnessDay", dayPeriod?.brightness ?: s.brightnessAutomation.manualBrightness)
            put("brightnessNight", nightPeriod?.brightness ?: s.schedule.brightnessNight)
            put("weatherEnabled", s.weather.enabled)
            put("weatherLatitude", s.weather.latitude)
            put("weatherLongitude", s.weather.longitude)
            put("weatherUnits", s.weather.units.name)
            put("weatherEndpoint", s.weather.endpointBaseUrl)
            put("weatherApiKeySet", s.weather.apiKeyRef.isNotBlank())
            put("webEnabled", s.web.enabled)
            put("webPort", s.web.portClamped)
            put("webIdleTimeoutMinutes", s.web.idleTimeoutMinutes)
            put("activePlaylistId", s.playlists.activePlaylistId)
            put("defaultPlaylistId", s.playlists.defaultPlaylistId)
            put("playlists", buildJsonArray {
                // ON_THIS_DAY is system-managed only (empty pool otherwise, see
                // SelectionMode.ON_THIS_DAY) — never shown as a manually pickable row.
                s.playlists.playlists
                    .filterNot { it.id == PlaylistSettings.PLAYLIST_ON_THIS_DAY }
                    .forEach { playlist ->
                        add(buildJsonObject {
                            put("id", playlist.id)
                            put("name", playlist.name)
                            put("enabled", playlist.enabled)
                            put("favoritesOnly", playlist.favoritesOnly)
                            put("localUploadsOnly", playlist.localUploadsOnly)
                            put("builtIn", playlist.id in PlaylistSettings.BUILT_IN_IDS)
                        })
                    }
            })
            put("playlistScheduleEnabled", s.playlists.scheduleEnabled)
            put("playlistScheduleRules", buildJsonArray {
                s.playlists.scheduleRules.forEach { rule ->
                    add(buildJsonObject {
                        put("id", rule.id)
                        put("name", rule.name)
                        put("playlistId", rule.playlistId)
                        put("startTime", rule.startTime)
                        put("endTime", rule.endTime)
                        put("priority", rule.priority)
                        put("enabled", rule.enabled)
                        put("daysOfWeek", buildJsonArray { rule.daysOfWeek.sorted().forEach { add(JsonPrimitive(it)) } })
                        rule.startDateIso?.let { put("startDate", it) }
                        rule.endDateIso?.let { put("endDate", it) }
                    })
                }
            })
            put("brightnessMode", s.brightnessAutomation.mode.name)
            put("manualBrightness", s.brightnessAutomation.manualBrightness)
            put("brightnessPeriods", buildJsonArray {
                s.brightnessAutomation.periods.forEach { period ->
                    add(buildJsonObject {
                        put("id", period.id)
                        put("startTime", period.startTime)
                        put("brightness", period.brightness)
                        put("action", period.action.name)
                    })
                }
            })
            put("webUploadEnabled", s.webUpload.enabled)
            put("webUploadMaxFileBytes", s.webUpload.maxFileBytes)
            put("webUploadDuplicatePolicy", s.webUpload.duplicatePolicy.name)
            put("webUploadAllowWhilePlaying", s.webUpload.allowWhilePlaying)
            put("localThumbnailCacheEnabled", s.localThumbnailCache.enabled)
            put("localThumbnailCacheMaxGiB", (s.localThumbnailCache.maxBytes / (1024L * 1024L * 1024L)).toInt())
            put("onThisDayEnabled", s.onThisDay.enabled)
            put("onThisDayTimesPerDay", s.onThisDay.timesPerDay)
            put("onThisDayDurationMinutes", s.onThisDay.durationMinutes)
            put("onThisDayMinYearsAgo", s.onThisDay.minYearsAgo)
            put("nextScheduleAction", nextScheduleAction)
        }
    }
}
