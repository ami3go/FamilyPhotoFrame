package com.example.familyphotoframe.web

import com.example.familyphotoframe.data.db.PhotoDao
import com.example.familyphotoframe.data.settings.ActiveSourceKind
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.BrightnessMode
import com.example.familyphotoframe.data.settings.CollageAlignment
import com.example.familyphotoframe.data.settings.CollageBackground
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageLayoutPreference
import com.example.familyphotoframe.data.settings.CollageOrientationFilter
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.CredentialPolicy
import com.example.familyphotoframe.data.settings.MotionMode
import com.example.familyphotoframe.data.settings.NightAction
import com.example.familyphotoframe.data.settings.OverlayPosition
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.data.settings.SelectionMode
import com.example.familyphotoframe.data.settings.SettingsRepository
import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import com.example.familyphotoframe.data.settings.UnreachablePolicy
import com.example.familyphotoframe.data.settings.UploadDuplicatePolicy
import com.example.familyphotoframe.data.source.BuiltInSourceIds
import com.example.familyphotoframe.data.source.SynologyApi
import com.example.familyphotoframe.domain.engine.SourceStatusPolicy
import com.example.familyphotoframe.domain.schedule.RescanSchedule
import com.example.familyphotoframe.domain.schedule.SleepSchedule
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Applies the non-secret web settings patch.
 *
 * Parsing, validation, credential-scope invalidation, and the atomic settings update are
 * kept together here so the HTTP controller only coordinates request/response behavior.
 */
internal class WebSettingsPatchApplier(
    private val settings: SettingsRepository,
    private val photoDao: PhotoDao,
) {
    suspend fun apply(patch: JsonObject): String? {
        fun int(key: String) = patch[key]?.jsonPrimitive?.intOrNull
        fun long(key: String) = patch[key]?.jsonPrimitive?.longOrNull
        fun bool(key: String) = patch[key]?.jsonPrimitive?.booleanOrNull
        fun str(key: String) = patch[key]?.jsonPrimitive?.let { if (it.isString) it.content else null }
        fun float(key: String) = patch[key]?.jsonPrimitive?.doubleOrNull?.toFloat()
        fun strings(key: String): List<String>? {
            val value = patch[key] ?: return null
            return when (value) {
                is JsonArray -> value.mapNotNull { item ->
                    (item as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
                }
                is JsonPrimitive -> if (value.isString) {
                    value.content.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                } else null
                else -> null
            }
        }

        val brightnessPeriodsPatch = (patch["brightnessPeriods"] as? JsonArray)?.mapIndexed { index, item ->
            val obj = item as? JsonObject ?: return "brightnessPeriods[$index] must be an object"
            val id = obj["id"]?.jsonPrimitive?.content?.trim()?.take(80).orEmpty()
            val start = obj["startTime"]?.jsonPrimitive?.content.orEmpty()
            val brightness = obj["brightness"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                ?: return "brightnessPeriods[$index].brightness is required"
            val action = obj["action"]?.jsonPrimitive?.content.orEmpty()
            if (id.isBlank()) return "brightnessPeriods[$index].id is required"
            if (SleepSchedule.parseMinutes(start) == null) return "brightnessPeriods[$index].startTime must be HH:mm"
            if (brightness !in 0.01f..1f) return "brightnessPeriods[$index].brightness must be 0.01-1.0"
            if (NightAction.entries.none { it.name == action }) return "brightnessPeriods[$index].action is invalid"
            com.example.familyphotoframe.data.settings.BrightnessPeriod(id, start, brightness, NightAction.valueOf(action))
        }?.take(12)

        int("intervalSeconds")?.let { if (it !in 3..600) return "intervalSeconds must be 3-600" }
        str("aspectMode")?.let { v ->
            if (AspectMode.entries.none { it.name == v }) return "Unknown aspectMode"
        }
        str("transitionSelectionMode")?.let { v ->
            if (TransitionSelectionMode.fromStorage(v) == null) return "Unknown transitionSelectionMode"
        }
        str("transition")?.let { v ->
            if (TransitionMode.fromStorage(v) == null) return "Unknown transition"
        }
        int("transitionDurationMs")?.let {
            if (it !in 300..2000) return "transitionDurationMs must be 300-2000"
        }
        str("motion")?.let { v ->
            if (MotionMode.entries.none { it.name == v }) return "Unknown motion"
        }
        str("portraitCollageMode")?.let { v ->
            if (PortraitCollageMode.entries.none { it.name == v }) return "Unknown portraitCollageMode"
        }
        int("portraitCollageMaxPhotos")?.let {
            if (it !in 2..3) return "portraitCollageMaxPhotos must be 2 or 3"
        }
        str("portraitCollageFallback")?.let { v ->
            if (PortraitFallback.entries.none { it.name == v }) return "Unknown portraitCollageFallback"
        }
        str("portraitCollageGap")?.let { v ->
            if (CollageGap.entries.none { it.name == v }) return "Unknown portraitCollageGap"
        }
        str("portraitCollageOrientationFilter")?.let { v -> if (CollageOrientationFilter.entries.none { it.name == v }) return "Unknown portraitCollageOrientationFilter" }
        str("portraitCollageLayoutPreference")?.let { v -> if (CollageLayoutPreference.entries.none { it.name == v }) return "Unknown portraitCollageLayoutPreference" }
        str("portraitCollageScaleMode")?.let { v -> if (CollageScaleMode.entries.none { it.name == v }) return "Unknown portraitCollageScaleMode" }
        str("portraitCollageAlignment")?.let { v -> if (CollageAlignment.entries.none { it.name == v }) return "Unknown portraitCollageAlignment" }
        str("portraitCollageBackground")?.let { v -> if (CollageBackground.entries.none { it.name == v }) return "Unknown portraitCollageBackground" }
        int("portraitCollageCornerRadiusDp")?.let { if (it !in 0..32) return "portraitCollageCornerRadiusDp must be 0-32" }
        str("selectionMode")?.let { v ->
            // Historical LEAST_RECENT_RANDOM is accepted as an alias for the current
            // no-repeat mode; ON_THIS_DAY remains system-managed only.
            val parsed = SelectionMode.fromStorage(v)
            if (parsed == null || parsed == SelectionMode.ON_THIS_DAY) {
                return "Unknown selectionMode"
            }
        }
        str("onUnreachable")?.let { v ->
            if (UnreachablePolicy.entries.none { it.name == v }) return "Unknown onUnreachable"
        }
        strings("includeGlobs")?.let { if (it.isEmpty()) return "includeGlobs cannot be empty" }
        int("webPort")?.let { if (it !in 1024..65535) return "webPort must be 1024-65535" }
        int("webIdleTimeoutMinutes")?.let { if (it !in 1..1440) return "webIdleTimeoutMinutes must be 1-1440" }
        int("localThumbnailCacheMaxGiB")?.let { if (it < 1) return "localThumbnailCacheMaxGiB must be at least 1" }
        int("onThisDayTimesPerDay")?.let { if (it !in 1..12) return "onThisDayTimesPerDay must be 1-12" }
        int("onThisDayDurationMinutes")?.let { if (it !in 1..60) return "onThisDayDurationMinutes must be 1-60" }
        int("onThisDayMinYearsAgo")?.let { if (it !in 0..100) return "onThisDayMinYearsAgo must be 0-100" }
        str("activePlaylistId")?.let { id ->
            if (settings.settings.first().playlists.playlists.none { it.id == id && it.enabled }) return "Unknown activePlaylistId"
        }
        str("brightnessMode")?.let { v ->
            if (BrightnessMode.entries.none { it.name == v }) return "Unknown brightnessMode"
        }
        float("manualBrightness")?.let { if (it !in 0.01f..1f) return "manualBrightness must be 0.01-1.0" }
        str("webUploadDuplicatePolicy")?.let { v ->
            if (UploadDuplicatePolicy.entries.none { it.name == v }) return "Unknown webUploadDuplicatePolicy"
        }
        long("webUploadMaxFileBytes")?.let {
            if (it !in 1L * 1024L * 1024L..500L * 1024L * 1024L) {
                return "webUploadMaxFileBytes must be 1-500 MiB"
            }
        }
        // Validated before anything is written: a malformed time or day list must
        // not leave a schedule half-applied and firing at an unintended moment.
        str("autoRescanAt")?.let { v ->
            if (SleepSchedule.parseMinutes(v) == null) return "Bad autoRescanAt (use HH:mm)"
        }
        str("autoRescanDays")?.let { v ->
            val supplied = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (supplied.size != RescanSchedule.parseDays(v).size) {
                return "Bad autoRescanDays (use 1-7, 1=Monday)"
            }
        }
        // Switching the primary never carries connection details: only an already
        // configured source (or the bundled samples) can be promoted from a browser.
        str("sourceKind")?.let { v ->
            val kind = ActiveSourceKind.entries.firstOrNull { it.name == v }
                ?: return "Unknown sourceKind: $v"
            if (kind == ActiveSourceKind.NONE) return "sourceKind cannot be NONE"
            val stored = settings.settings.first().source
            if (kind != ActiveSourceKind.SAMPLES && !SourceStatusPolicy.isConfigured(kind, stored)) {
                return "$v is not configured yet"
            }
        }
        // Comma-separated kinds; blank means "no co-primaries". Validated as a group
        // so one bad name cannot leave a half-merged pool.
        str("alsoPlay")?.let { v ->
            val names = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            for (n in names) {
                val kind = ActiveSourceKind.entries.firstOrNull { it.name == n }
                    ?: return "Unknown alsoPlay source: $n"
                if (kind == ActiveSourceKind.NONE || kind == ActiveSourceKind.SAMPLES) {
                    return "$n cannot be a co-primary"
                }
            }
        }
        // Every overlay anchor is validated before anything is written, so a typo in
        // one field cannot leave the other overlays half-updated.
        for (key in OVERLAY_POSITION_KEYS) {
            str(key)?.let { v ->
                if (OverlayPosition.entries.none { it.name == v }) return "Unknown $key"
            }
        }

        LegacyBrightnessWebPatch.validationError(patch)?.let { return it }

        // Same guard as the on-device toggle: enabling favourites-only with nothing
        // favourited would leave the engine with an empty primary pool and quietly
        // drop the frame to sample photos, which reads as a bug from across the house.
        if (bool("favoritesOnly") == true) {
            val s = settings.settings.first()
            val pool = when (s.source.kind) {
                ActiveSourceKind.LOCAL_SAF -> BuiltInSourceIds.LOCAL_SAF
                ActiveSourceKind.SMB -> BuiltInSourceIds.SMB
                ActiveSourceKind.SYNOLOGY -> BuiltInSourceIds.SYNOLOGY
                ActiveSourceKind.WEBDAV -> BuiltInSourceIds.WEBDAV
                ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> BuiltInSourceIds.FALLBACK
            }
            if (photoDao.favoriteCount(listOf(pool)) == 0) {
                return "No favourites yet — star a photo on the frame first"
            }
        }

        settings.update { cur ->
            var next = LegacyBrightnessWebPatch.apply(cur, patch)
            int("intervalSeconds")?.let { next = next.copy(intervalSeconds = it) }
            int("transitionDurationMs")?.let { next = next.copy(transitionDurationMs = it.coerceIn(300, 2000)) }
            str("aspectMode")?.let { v -> next = next.copy(aspectMode = AspectMode.valueOf(v)) }
            str("transitionSelectionMode")?.let { v ->
                next = next.copy(
                    transitionSelectionMode = TransitionSelectionMode.fromStorage(v)
                        ?: TransitionSelectionMode.FIXED,
                )
            }
            str("transition")?.let { v -> next = next.copy(transition = TransitionMode.fromStorage(v) ?: TransitionMode.CROSSFADE) }
            bool("transitionReduceMotion")?.let { next = next.copy(transitionReduceMotion = it) }
            str("motion")?.let { v -> next = next.copy(motion = MotionMode.valueOf(v)) }
            str("portraitCollageMode")?.let { v ->
                next = next.copy(portraitCollage = next.portraitCollage.copy(
                    mode = PortraitCollageMode.valueOf(v),
                ))
            }
            int("portraitCollageMaxPhotos")?.let { v ->
                next = next.copy(portraitCollage = next.portraitCollage.copy(maxPhotos = v))
            }
            str("portraitCollageFallback")?.let { v ->
                next = next.copy(portraitCollage = next.portraitCollage.copy(
                    fallback = PortraitFallback.valueOf(v),
                ))
            }
            str("portraitCollageGap")?.let { v ->
                next = next.copy(portraitCollage = next.portraitCollage.copy(
                    gap = CollageGap.valueOf(v),
                ))
            }
            str("portraitCollageOrientationFilter")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(orientationFilter = CollageOrientationFilter.valueOf(v))) }
            bool("portraitCollageFillOtherOrientations")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(fillWithOtherOrientations = v)) }
            str("portraitCollageLayoutPreference")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(layoutPreference = CollageLayoutPreference.valueOf(v))) }
            str("portraitCollageScaleMode")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(scaleMode = CollageScaleMode.valueOf(v))) }
            str("portraitCollageAlignment")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(alignment = CollageAlignment.valueOf(v))) }
            str("portraitCollageBackground")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(background = CollageBackground.valueOf(v))) }
            int("portraitCollageCornerRadiusDp")?.let { v -> next = next.copy(portraitCollage = next.portraitCollage.copy(cornerRadiusDp = v)) }
            bool("portraitCollageAnimateThree")?.let { v ->
                next = next.copy(portraitCollage = next.portraitCollage.copy(
                    animateThreePhotoFrames = v,
                ))
            }
            bool("clockShow")?.let { next = next.copy(overlays = next.overlays.copy(clockShow = it)) }
            bool("dateShow")?.let { next = next.copy(overlays = next.overlays.copy(dateShow = it)) }
            bool("folderShow")?.let { next = next.copy(overlays = next.overlays.copy(folderShow = it)) }
            bool("clock24h")?.let { next = next.copy(overlays = next.overlays.copy(clock24h = it)) }
            bool("weatherShow")?.let { next = next.copy(overlays = next.overlays.copy(weatherShow = it)) }
            bool("photoDateShow")?.let { next = next.copy(overlays = next.overlays.copy(photoDateShow = it)) }
            bool("captionShow")?.let { next = next.copy(overlays = next.overlays.copy(captionShow = it)) }
            bool("locationShow")?.let { next = next.copy(overlays = next.overlays.copy(locationShow = it)) }
            float("overlayOpacity")?.let {
                next = next.copy(overlays = next.overlays.copy(opacity = it.coerceIn(0.1f, 1f)))
            }
            bool("overlayScrim")?.let { next = next.copy(overlays = next.overlays.copy(scrim = it)) }
            // Overlay anchors; already validated above, so valueOf cannot throw here.
            str("clockPosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(clockPosition = OverlayPosition.valueOf(v)))
            }
            str("datePosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(datePosition = OverlayPosition.valueOf(v)))
            }
            str("folderPosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(folderPosition = OverlayPosition.valueOf(v)))
            }
            str("weatherPosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(weatherPosition = OverlayPosition.valueOf(v)))
            }
            str("photoDatePosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(photoDatePosition = OverlayPosition.valueOf(v)))
            }
            str("captionPosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(captionPosition = OverlayPosition.valueOf(v)))
            }
            str("locationPosition")?.let { v ->
                next = next.copy(overlays = next.overlays.copy(locationPosition = OverlayPosition.valueOf(v)))
            }
            // Promote an already-configured source. Applied before alsoPlay so a patch
            // carrying both cannot leave the new primary listed as its own co-primary.
            str("sourceKind")?.let { v ->
                val kind = ActiveSourceKind.valueOf(v)
                next = next.copy(
                    source = next.source.copy(
                        kind = kind,
                        displayName = when (kind) {
                            ActiveSourceKind.LOCAL_SAF -> next.source.displayName
                            ActiveSourceKind.SMB ->
                                next.source.smb?.let { "${it.host}/${it.share}" }.orEmpty()
                            ActiveSourceKind.SYNOLOGY -> next.source.synology?.baseUrl.orEmpty()
                            ActiveSourceKind.WEBDAV -> next.source.webdav?.baseUrl.orEmpty()
                            ActiveSourceKind.NONE, ActiveSourceKind.SAMPLES -> ""
                        },
                        alsoPlay = next.source.alsoPlay - kind,
                    )
                )
            }
            str("alsoPlay")?.let { v ->
                val kinds = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .map { name -> ActiveSourceKind.entries.first { it.name == name } }
                    .toSet()
                next = next.copy(source = next.source.copy(alsoPlay = kinds - next.source.kind))
            }
            str("selectionMode")?.let { v ->
                SelectionMode.fromStorage(v)?.takeIf { it != SelectionMode.ON_THIS_DAY }?.let { mode ->
                    next = next.copy(selectionMode = mode)
                }
            }
            str("onUnreachable")?.let { v -> next = next.copy(onUnreachable = UnreachablePolicy.valueOf(v)) }
            bool("favoritesOnly")?.let { next = next.copy(favoritesOnly = it) }
            // Blank clears the filter, which means "play everything" — the same
            // meaning an empty set has on the frame.
            strings("selectedFolders")?.let { values ->
                next = next.copy(selectedFolders = values.toSet())
            }
            strings("includeGlobs")?.let { values ->
                next = next.copy(filters = next.filters.copy(includeGlobs = values))
            }
            strings("excludeGlobs")?.let { values ->
                next = next.copy(filters = next.filters.copy(excludeGlobs = values))
            }
            strings("excludeFolders")?.let { values ->
                next = next.copy(filters = next.filters.copy(excludeFolders = values))
            }
            bool("includeSubfolders")?.let { value ->
                next = next.copy(filters = next.filters.copy(includeSubfolders = value))
            }
            bool("webEnabled")?.let { value -> next = next.copy(web = next.web.copy(enabled = value)) }
            int("webPort")?.let { value -> next = next.copy(web = next.web.copy(port = value)) }
            int("webIdleTimeoutMinutes")?.let { value ->
                next = next.copy(web = next.web.copy(idleTimeoutMinutes = value))
            }
            str("activePlaylistId")?.let { value ->
                next = next.copy(playlists = next.playlists.copy(activePlaylistId = value))
            }
            bool("playlistScheduleEnabled")?.let { value ->
                next = next.copy(playlists = next.playlists.copy(scheduleEnabled = value))
            }
            str("brightnessMode")?.let { value ->
                next = next.copy(brightnessAutomation = next.brightnessAutomation.copy(mode = BrightnessMode.valueOf(value)))
            }
            float("manualBrightness")?.let { value ->
                next = next.copy(brightnessAutomation = next.brightnessAutomation.copy(manualBrightness = value))
            }
            brightnessPeriodsPatch?.let { periods ->
                next = next.copy(brightnessAutomation = next.brightnessAutomation.copy(periods = periods))
            }
            bool("webUploadEnabled")?.let { value ->
                next = next.copy(webUpload = next.webUpload.copy(enabled = value))
            }
            str("webUploadDuplicatePolicy")?.let { value ->
                next = next.copy(webUpload = next.webUpload.copy(duplicatePolicy = UploadDuplicatePolicy.valueOf(value)))
            }
            bool("webUploadAllowWhilePlaying")?.let { value ->
                next = next.copy(webUpload = next.webUpload.copy(allowWhilePlaying = value))
            }
            long("webUploadMaxFileBytes")?.let { value ->
                next = next.copy(webUpload = next.webUpload.copy(maxFileBytes = value))
            }
            bool("localThumbnailCacheEnabled")?.let { value ->
                next = next.copy(localThumbnailCache = next.localThumbnailCache.copy(enabled = value))
            }
            int("localThumbnailCacheMaxGiB")?.let { value ->
                next = next.copy(localThumbnailCache = next.localThumbnailCache.copy(
                    maxBytes = value.toLong() * 1024L * 1024L * 1024L,
                ))
            }
            bool("onThisDayEnabled")?.let { value ->
                next = next.copy(onThisDay = next.onThisDay.copy(enabled = value))
            }
            int("onThisDayTimesPerDay")?.let { value ->
                next = next.copy(onThisDay = next.onThisDay.copy(timesPerDay = value))
            }
            int("onThisDayDurationMinutes")?.let { value ->
                next = next.copy(onThisDay = next.onThisDay.copy(durationMinutes = value))
            }
            int("onThisDayMinYearsAgo")?.let { value ->
                next = next.copy(onThisDay = next.onThisDay.copy(minYearsAgo = value))
            }
            bool("autoRescanEnabled")?.let {
                next = next.copy(schedule = next.schedule.copy(autoRescanEnabled = it))
            }
            str("autoRescanAt")?.let { v ->
                // Non-null: validated above before any write happened.
                val m = SleepSchedule.parseMinutes(v)!!
                next = next.copy(
                    schedule = next.schedule.copy(autoRescanAt = SleepSchedule.formatMinutes(m)),
                )
            }
            str("autoRescanDays")?.let { v ->
                next = next.copy(
                    schedule = next.schedule.copy(
                        autoRescanDays = RescanSchedule.formatDays(RescanSchedule.parseDays(v)),
                    ),
                )
            }
            bool("autoStartOnBoot")?.let { next = next.copy(autoStartOnBoot = it) }

            // Non-secret SMB fields only; the password stays device-only (§15.6).
            // If the endpoint/account changes, clear the credential reference so an
            // old password is never sent to a newly entered server or user.
            val smb = next.source.smb
            if (smb != null) {
                var updated = smb.copy(
                    host = str("smbHost")?.trim() ?: smb.host,
                    share = str("smbShare")?.trim()?.trim('/') ?: smb.share,
                    path = str("smbPath")?.trim()?.trim('/') ?: smb.path,
                    user = str("smbUser")?.trim() ?: smb.user,
                    domain = str("smbDomain")?.trim() ?: smb.domain,
                )
                if (!CredentialPolicy.sameSmbScope(smb, updated)) {
                    updated = updated.copy(credentialRef = "")
                }
                if (updated != smb) {
                    next = next.copy(
                        source = next.source.copy(
                            displayName = if (next.source.kind == ActiveSourceKind.SMB) {
                                "${updated.host}/${updated.share}"
                            } else next.source.displayName,
                            smb = updated,
                        ),
                    )
                }
            }

            // Non-secret Synology fields only. Neither the password, the 2FA code,
            // nor the pinned certificate can be set from here: the first two are
            // secrets (§15.6), and the third is a security decision that must be
            // made on the frame after visually comparing the fingerprint against
            // DSM — accepting it over cleartext HTTP would let anyone on the LAN
            // silently pin a certificate of their choosing.
            val syn = next.source.synology
            if (syn != null) {
                var updatedSyn = syn.copy(
                    baseUrl = str("synBaseUrl")?.let(SynologyApi::normalizeBaseUrl) ?: syn.baseUrl,
                    folderPath = str("synFolderPath")?.trim()?.ifBlank { "/photo" } ?: syn.folderPath,
                    user = str("synUser")?.trim() ?: syn.user,
                    useThumbnails = bool("synUseThumbnails") ?: syn.useThumbnails,
                )
                if (!CredentialPolicy.sameSynologyHost(syn, updatedSyn)) {
                    updatedSyn = updatedSyn.copy(pinnedCertSha256 = null)
                }
                if (!CredentialPolicy.sameSynologyScope(syn, updatedSyn)) {
                    updatedSyn = updatedSyn.copy(credentialRef = "")
                }
                if (updatedSyn != syn) {
                    next = next.copy(
                        source = next.source.copy(
                            displayName = if (next.source.kind == ActiveSourceKind.SYNOLOGY) {
                                updatedSyn.baseUrl
                            } else next.source.displayName,
                            synology = updatedSyn,
                        ),
                    )
                }
            }

            val dav = next.source.webdav
            if (dav != null) {
                var updatedDav = dav.copy(
                    baseUrl = str("davBaseUrl")?.trim()?.trimEnd('/') ?: dav.baseUrl,
                    rootPath = str("davRootPath")?.trim() ?: dav.rootPath,
                    folderPath = str("davFolderPath")?.trim() ?: dav.folderPath,
                    user = str("davUser")?.trim() ?: dav.user,
                )
                if (!CredentialPolicy.sameWebDavHost(dav, updatedDav)) {
                    updatedDav = updatedDav.copy(pinnedCertSha256 = null)
                }
                if (!CredentialPolicy.sameWebDavScope(dav, updatedDav)) {
                    updatedDav = updatedDav.copy(credentialRef = "")
                }
                if (updatedDav != dav) {
                    next = next.copy(
                        source = next.source.copy(
                            displayName = if (next.source.kind == ActiveSourceKind.WEBDAV) {
                                updatedDav.baseUrl
                            } else next.source.displayName,
                            webdav = updatedDav,
                        ),
                    )
                }
            }
            next
        }
        return null
    }

    private companion object {
        val OVERLAY_POSITION_KEYS = listOf(
            "clockPosition", "datePosition", "folderPosition", "weatherPosition",
            "photoDatePosition", "captionPosition", "locationPosition",
        )
    }
}
