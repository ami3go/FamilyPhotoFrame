package com.example.familyphotoframe.data.settings

import com.example.familyphotoframe.util.SupportedFormats
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * How a photo is fitted to the screen (spec §10.4).
 * `FIT_BLUR` fills the letterbox with a heavily downsampled blur of the same photo
 * (spec §10.2 forbids full-resolution blur).
 */
enum class AspectMode { FIT_COLOR, FILL_CROP, FIT_BLUR }

/**
 * Schedule settings unrelated to display brightness plus legacy quiet-hours fields.
 *
 * The sleep/brightness fields are retained only so installations created before the
 * unified brightness automation can be migrated without losing their timetable. New
 * runtime code must use [BrightnessAutomationSettings] as the single source of truth.
 */
@Serializable
data class ScheduleSettings(
    val sleepEnabled: Boolean = false,
    val sleepStart: String = "23:00",
    val sleepEnd: String = "07:00",
    val brightnessDay: Float = 1.0f,
    val brightnessNight: Float = 0.3f,

    /**
     * Automatic index refresh (spec §20). **Off by default**: a rescan touches the NAS,
     * and a frame that starts doing unexpected network work after an update would be a
     * poor surprise.
     */
    val autoRescanEnabled: Boolean = false,
    /** Local time of day to refresh, `"HH:mm"`. Defaults to the small hours. */
    val autoRescanAt: String = "03:30",
    /**
     * ISO weekdays (1 = Monday) the refresh may run on, comma separated. Empty means
     * never, which is why enabling the feature seeds all seven days.
     */
    val autoRescanDays: String = "1,2,3,4,5,6,7",
    /**
     * When the scheduled rescan last completed. Persisted so a run missed while the
     * device was off is not repeated on every check once it comes back.
     */
    val lastAutoRescanAtEpochMs: Long = 0L,
)

/**
 * Weather overlay (spec §11). **Disabled by default.**
 *
 * Coordinates are entered by the user rather than read from the device, so no location
 * permission is ever requested. The endpoint is configurable and the API key lives in
 * the Keystore (referenced by [apiKeyRef], never stored here) because Open-Meteo's free
 * endpoint is licensed for non-commercial use only — see WEATHER_LICENSING.md.
 */
@Serializable
data class WeatherSettings(
    val enabled: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val units: com.example.familyphotoframe.data.weather.TemperatureUnits =
        com.example.familyphotoframe.data.weather.TemperatureUnits.CELSIUS,
    val endpointBaseUrl: String = "https://api.open-meteo.com/v1/forecast",
    val apiKeyRef: String = "",
    val refreshMinutes: Int = 30,
    /** Shown but marked stale beyond this age. */
    val staleAfterMinutes: Int = 90,
    /** Hidden entirely beyond this age. */
    val maxStaleHours: Int = 6,
) {
    val refreshMinutesClamped: Int get() = refreshMinutes.coerceIn(10, 24 * 60)
    val staleAfterMs: Long get() = staleAfterMinutes.coerceIn(15, 24 * 60) * 60_000L
    val maxStaleMs: Long get() = maxStaleHours.coerceIn(1, 72) * 3_600_000L

    /** 0,0 is in the ocean and almost certainly means "not configured yet". */
    val hasValidCoordinates: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
}

/**
 * How the next photo is chosen (spec §9.6).
 *
 * [LEAST_RECENT_RANDOM] is the original MVP mode: sample randomly inside a window of
 * least-recently-shown photos. It is cheap and needs no state, but cannot promise that
 * every photo is shown before any repeats.
 *
 * [SHUFFLE_NO_REPEAT] is a global photo bag: every displayable photo exactly once
 * per cycle, regardless of folder. [FOLDER_BALANCED_SHUFFLE] first shuffles folders
 * without replacement and then consumes each folder's independent photo bag.
 *
 * The date modes play in EXIF date-taken order (photos with no date-taken sort last, by
 * file-modified time) so a frame can walk a trip or a year in sequence.
 */
enum class SelectionMode {
    /** Stable indexed path order; repeats only after reaching the end. */
    SEQUENTIAL,
    /** Legacy pre-v52 random mode; migrated once to [SHUFFLE_NO_REPEAT]. */
    LEAST_RECENT_RANDOM,
    SHUFFLE_NO_REPEAT,
    FOLDER_BALANCED_SHUFFLE,
    DATE_TAKEN_NEWEST,
    DATE_TAKEN_OLDEST,
    /**
     * System-managed only — the pool is an explicit id list pushed by
     * `SlideshowViewModel`'s "on this day" trigger, not a SQL predicate. Never valid as
     * a directly user-chosen value for the global selection mode (see
     * [WebSettingsPatchApplier][com.example.familyphotoframe.web.WebSettingsPatchApplier]);
     * only reachable via the built-in `PlaylistSettings.PLAYLIST_ON_THIS_DAY` playlist,
     * which is not user-selectable either (docs/FPF-FEAT-ON-THIS-DAY-001.md §4.2).
     */
    ON_THIS_DAY,
}

/**
 * What to play when a remote (SMB/Synology) primary is unreachable (spec §9.3
 * `on_unreachable`).
 *
 * [FALLBACK_SAMPLES] is the conservative original behaviour: show the bundled sample
 * photos. [STALE_CACHE] instead keeps playing the NAS photos whose bytes are already in
 * [com.example.familyphotoframe.data.cache.MediaCache], which is almost always what a
 * family actually wants from a photo frame during a router reboot — the frame keeps
 * showing family photos rather than visibly degrading to stock images. It falls through
 * to the samples anyway when the cache holds nothing for that source.
 */
enum class UnreachablePolicy { FALLBACK_SAMPLES, STALE_CACHE }

/**
 * Fixed slideshow transition effect. Persisted values use stable lower-case IDs.
 *
 * Historical values are translated by [fromStorage] at the serialization boundary, so
 * runtime code only handles the ten effects that can actually be selected and rendered.
 */
@Serializable(with = TransitionModeSerializer::class)
enum class TransitionMode(
    val storageValue: String,
    val durationMultiplier: Float,
) {
    CROSSFADE("crossfade", 1.00f),
    SOFT_DISSOLVE("soft_dissolve", 1.35f),
    GENTLE_ZOOM_IN("gentle_zoom_in", 1.15f),
    GENTLE_ZOOM_OUT("gentle_zoom_out", 1.15f),
    HORIZONTAL_GLIDE("horizontal_glide", 1.00f),
    VERTICAL_GLIDE("vertical_glide", 1.00f),
    DEPTH_FADE("depth_fade", 1.10f),
    KEN_BURNS_HANDOFF("ken_burns_handoff", 1.50f),
    SOFT_REVEAL("soft_reveal", 1.20f),
    SOFT_FOCUS_FADE("soft_focus_fade", 1.25f),
    ;

    val isOpacityOnly: Boolean
        get() = this == CROSSFADE || this == SOFT_DISSOLVE

    val isMotionHeavy: Boolean
        get() = this == HORIZONTAL_GLIDE || this == VERTICAL_GLIDE || this == KEN_BURNS_HANDOFF

    companion object {
        /** Every effect exposed by Android and web settings. */
        val selectableValues: List<TransitionMode> = entries

        /** Curated passive-playback pool; mask/blur effects remain fixed-only. */
        val ambientRandomValues: List<TransitionMode> = listOf(
            CROSSFADE,
            SOFT_DISSOLVE,
            GENTLE_ZOOM_IN,
            GENTLE_ZOOM_OUT,
            HORIZONTAL_GLIDE,
            VERTICAL_GLIDE,
            DEPTH_FADE,
            KEN_BURNS_HANDOFF,
        )

        fun fromStorage(value: String): TransitionMode? {
            val normalized = value.trim()
            return when {
                normalized.equals("none", ignoreCase = true) -> CROSSFADE
                normalized.equals("slide", ignoreCase = true) -> HORIZONTAL_GLIDE
                else -> entries.firstOrNull {
                    it.storageValue.equals(normalized, ignoreCase = true) ||
                        it.name.equals(normalized, ignoreCase = true)
                }
            }
        }
    }
}


/** Whether playback uses one fixed effect or the curated weighted random selector. */
@Serializable(with = TransitionSelectionModeSerializer::class)
enum class TransitionSelectionMode(val storageValue: String) {
    FIXED("fixed"),
    AMBIENT_RANDOM("ambient_random"),
    ;

    companion object {
        fun fromStorage(value: String): TransitionSelectionMode? {
            val normalized = value.trim()
            return entries.firstOrNull {
                it.storageValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            }
        }
    }
}

object TransitionSelectionModeSerializer : KSerializer<TransitionSelectionMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransitionSelectionMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TransitionSelectionMode) {
        encoder.encodeString(value.storageValue)
    }

    override fun deserialize(decoder: Decoder): TransitionSelectionMode =
        TransitionSelectionMode.fromStorage(decoder.decodeString()) ?: TransitionSelectionMode.FIXED
}

object TransitionModeSerializer : KSerializer<TransitionMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TransitionMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TransitionMode) {
        encoder.encodeString(value.storageValue)
    }

    override fun deserialize(decoder: Decoder): TransitionMode =
        TransitionMode.fromStorage(decoder.decodeString()) ?: TransitionMode.CROSSFADE
}

/** Optional continuous motion applied to the displayed photo (spec §4 Phase 2). */
enum class MotionMode { NONE, KEN_BURNS }

/** How portrait photos are grouped on a landscape frame. */
enum class PortraitCollageMode { OFF, AUTOMATIC, ALWAYS_TWO, PREFER_THREE }

/** Fallback used when a portrait photo has no compatible same-folder companions. */
enum class PortraitFallback { BLURRED_BACKGROUND, SOLID_BACKGROUND, CROP_TO_FILL }

/** Which photo orientations are allowed to participate in a collage. */
enum class CollageOrientationFilter { ANY, PORTRAIT_ONLY, LANDSCAPE_ONLY, SAME_AS_ANCHOR }
/** How the optimizer may arrange selected photos. */
enum class CollageLayoutPreference { AUTO, COLUMNS, ROWS, FEATURED }
/** Whether a tile crops to fill or keeps the full photo visible. */
enum class CollageScaleMode { CROP, FIT }
/** Focal placement used for crop anchoring and fitted-image positioning. */
enum class CollageAlignment { CENTER, TOP, BOTTOM, LEFT, RIGHT }
/** Background for tile letterboxing and visible gaps. */
enum class CollageBackground { APP_BACKGROUND, BLACK, WHITE }
/** Visual gap between collage tiles. */
enum class CollageGap { NONE, SMALL, MEDIUM, LARGE }

@Serializable
data class PortraitCollageSettings(
    val mode: PortraitCollageMode = PortraitCollageMode.AUTOMATIC,
    val maxPhotos: Int = 3,
    val fallback: PortraitFallback = PortraitFallback.BLURRED_BACKGROUND,
    val gap: CollageGap = CollageGap.SMALL,
    val orientationFilter: CollageOrientationFilter = CollageOrientationFilter.ANY,
    val layoutPreference: CollageLayoutPreference = CollageLayoutPreference.AUTO,
    val scaleMode: CollageScaleMode = CollageScaleMode.CROP,
    val alignment: CollageAlignment = CollageAlignment.CENTER,
    val background: CollageBackground = CollageBackground.APP_BACKGROUND,
    val cornerRadiusDp: Int = 0,
    /** Subtle continuous motion on equal three-photo portrait frames. */
    val animateThreePhotoFrames: Boolean = true,
) {
    val maxPhotosClamped: Int get() = maxPhotos.coerceIn(2, 3)
    val cornerRadiusDpClamped: Int get() = cornerRadiusDp.coerceIn(0, 32)
}

/** 9-grid overlay anchor (spec §11). */
enum class OverlayPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
}

/** Which source currently feeds the slideshow (SAF folder, bundled samples, or SMB/NAS). */
enum class ActiveSourceKind { NONE, LOCAL_SAF, SAMPLES, SMB, SYNOLOGY, WEBDAV }

/** Non-secret SMB connection settings. The password is NOT here — it lives in the Keystore SecretStore under [credentialRef]. */
@Serializable
data class SmbSettings(
    val host: String = "",
    val share: String = "",
    val path: String = "",
    val user: String = "",
    val domain: String = "",
    val credentialRef: String = "",
)

/**
 * Non-secret Synology File Station settings (ROADMAP.md network photo-app sources).
 * The password lives in the Keystore SecretStore under [credentialRef], never here.
 *
 * A 2FA one-time code is deliberately **not** a field: it is valid for seconds, so
 * persisting it is useless. It is supplied once at setup to establish a session; see
 * KNOWN_LIMITATIONS.md for what that means for 2FA-enabled accounts on a frame that
 * runs for weeks.
 */
@Serializable
data class SynologySettings(
    val baseUrl: String = "",
    val folderPath: String = "/photo",
    val user: String = "",
    val credentialRef: String = "",
    /** Request server-generated thumbnails rather than full-res originals. */
    val useThumbnails: Boolean = true,
    val thumbnailSize: String = "large",
    /**
     * SHA-256 fingerprint of a self-signed certificate the user explicitly approved.
     * Not a secret (it is public information the server presents to anyone), so unlike
     * the password it lives here rather than in the Keystore — but it IS security-
     * relevant, so it is only ever written through an explicit user approval action.
     */
    val pinnedCertSha256: String? = null,
)

/**
 * Non-secret WebDAV / Nextcloud settings. The password (or Nextcloud app password)
 * lives in the Keystore SecretStore under [credentialRef], never here.
 *
 * [rootPath] is the DAV endpoint (`/remote.php/dav/files/<user>` on Nextcloud) and
 * [folderPath] is the folder below it to play. They are separate so changing the folder
 * does not require re-deriving — or re-typing — the endpoint.
 */
@Serializable
data class WebDavSettings(
    val baseUrl: String = "",
    val rootPath: String = "",
    val folderPath: String = "",
    val user: String = "",
    val credentialRef: String = "",
    /** See [SynologySettings.pinnedCertSha256]; public information, but security-relevant. */
    val pinnedCertSha256: String? = null,
)

/** Preset selected when pairing a browser. */
@Serializable
enum class RememberExpiryMode {
    SESSION_ONLY, ONE_HOUR, ONE_DAY, ONE_WEEK, ONE_MONTH, ONE_YEAR, FOREVER, CUSTOM,
}

@Serializable
enum class CustomExpiryUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

/** Owner policy for persistent browser trust. Disabled by default while web transport is HTTP. */
@Serializable
data class RememberedBrowserPolicy(
    val enabled: Boolean = false,
    val defaultExpiry: RememberExpiryMode = RememberExpiryMode.SESSION_ONLY,
    /** Fixed-duration upper bound; calendar month/year presets are checked separately. */
    val maxExpirySeconds: Long = 366L * 24L * 60L * 60L,
    val allowForever: Boolean = false,
    val maxRememberedBrowsers: Int = 8,
    val requireStepUpForSensitiveActions: Boolean = true,
    val rotateOnExchange: Boolean = true,
    val rotationGraceSeconds: Int = 30,
) {
    fun normalized(): RememberedBrowserPolicy = copy(
        maxExpirySeconds = maxExpirySeconds.coerceIn(10L * 60L, 10L * 365L * 24L * 60L * 60L),
        maxRememberedBrowsers = maxRememberedBrowsers.coerceIn(1, 32),
        rotationGraceSeconds = rotationGraceSeconds.coerceIn(5, 120),
        defaultExpiry = when {
            !enabled -> RememberExpiryMode.SESSION_ONLY
            defaultExpiry == RememberExpiryMode.FOREVER && !allowForever -> RememberExpiryMode.SESSION_ONLY
            else -> defaultExpiry
        },
    )
}

/**
 * Embedded web setup server settings (spec §15.1, §20). Disabled by default —
 * the server never starts unless the user turns it on (spec §15.5).
 */
@Serializable
data class WebSettings(
    val enabled: Boolean = false,
    val port: Int = 8080,
    val requirePairing: Boolean = true,
    val showQrOnScreen: Boolean = true,
    val idleTimeoutMinutes: Int = 30,
    val rememberedBrowsers: RememberedBrowserPolicy = RememberedBrowserPolicy(),
) {
    val portClamped: Int get() = port.coerceIn(1024, 65535)
    val idleTimeoutMs: Long get() = idleTimeoutMinutes.coerceIn(1, 24 * 60) * 60_000L
}

@Serializable
data class OverlaySettings(
    val clockShow: Boolean = true,
    val clock24h: Boolean = true,
    val clockShowSeconds: Boolean = false,
    val clockPosition: OverlayPosition = OverlayPosition.BOTTOM_LEFT,
    val dateShow: Boolean = true,
    val datePosition: OverlayPosition = OverlayPosition.BOTTOM_LEFT,
    val folderShow: Boolean = true,
    val folderPosition: OverlayPosition = OverlayPosition.BOTTOM_RIGHT,
    val weatherShow: Boolean = true,
    val weatherPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,
    /**
     * Photo date-taken, caption, and location overlays (spec §11 Phase 2 overlays).
     * Off by default: unlike the clock/today's-date overlays above, these read
     * per-photo EXIF data that not every photo has, so they are opt-in rather than
     * showing blank/missing text for photos without it.
     */
    val photoDateShow: Boolean = false,
    val photoDatePosition: OverlayPosition = OverlayPosition.BOTTOM_LEFT,
    val captionShow: Boolean = false,
    val captionPosition: OverlayPosition = OverlayPosition.TOP_LEFT,
    /** Renders EXIF GPS as coordinates (spec §11); never reverse-geocoded over the network. */
    val locationShow: Boolean = false,
    val locationPosition: OverlayPosition = OverlayPosition.TOP_LEFT,
    val scrim: Boolean = true,
    val opacity: Float = 0.9f,
)

@Serializable
data class ActiveSource(
    val kind: ActiveSourceKind = ActiveSourceKind.NONE,
    /**
     * Additional source kinds to merge into the primary pool alongside [kind]
     * (spec §9.3 "merged pool of multiple primaries").
     *
     * Each kind's connection settings already live side by side in this object, so a
     * kind listed here is configured exactly the way it would be if it were [kind].
     * There is at most one source per kind, which is what keeps the built-in source ids
     * (`local_saf`, `smb`, `synology`, `webdav`) unambiguous — two SMB shares at once
     * would need per-source ids and is deliberately still out of scope.
     *
     * [ActiveSourceKind.NONE] and [ActiveSourceKind.SAMPLES] are ignored here: samples
     * are always the fallback pool, never a co-primary.
     */
    val alsoPlay: Set<ActiveSourceKind> = emptySet(),
    /** Persisted SAF tree URI string when [kind] == LOCAL_SAF; never a raw path, never a secret. */
    val treeUri: String? = null,
    val displayName: String = "",
    /** Non-secret SMB connection when [kind] == SMB. */
    val smb: SmbSettings? = null,
    /** Non-secret Synology File Station connection when [kind] == SYNOLOGY. */
    val synology: SynologySettings? = null,
    /** Non-secret WebDAV/Nextcloud connection when [kind] == WEBDAV. */
    val webdav: WebDavSettings? = null,
)

/**
 * User-editable scan filters (spec §20 `includeGlobs` / `excludeGlobs`).
 *
 * Stored as newline/comma-free single strings joined by "," in the UI and split here, so
 * the whole thing round-trips through plain-JSON config export and the web API without a
 * bespoke encoding.
 *
 * Defaults include iPhone HEIC/HEIF photos in addition to JPEG, PNG, and WEBP.
 */
@Serializable
data class FilterSettings(
    val includeGlobs: List<String> = SupportedFormats.defaultIncludeGlobs,
    val excludeGlobs: List<String> = listOf(".*", "Thumbs.db", "@eaDir/**"),
    val includeSubfolders: Boolean = true,
    /**
     * Folder names to skip entirely, matched against each folder name during traversal.
     * Kept separate from [excludeGlobs] because users think in terms of "don't show me
     * the Screenshots folder", not in terms of glob syntax, and because skipping a
     * folder prunes the whole subtree instead of filtering file-by-file.
     */
    val excludeFolders: List<String> = listOf("@eaDir"),
) {
    /** Blank entries are dropped so a trailing comma in the UI cannot match everything. */
    val cleanIncludes: List<String> get() = includeGlobs.map { it.trim() }.filter { it.isNotEmpty() }
    val cleanExcludes: List<String> get() = excludeGlobs.map { it.trim() }.filter { it.isNotEmpty() }
    val cleanExcludeFolders: List<String> get() =
        excludeFolders.map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Upgrade only the exact legacy default. User-customized include filters are left
     * untouched, while existing installations that never edited the field gain iPhone
     * photo indexing automatically.
     */
    fun withCurrentDefaultFormats(): FilterSettings {
        val normalized = cleanIncludes.map { it.lowercase() }.toSet()
        return if (normalized == LEGACY_DEFAULT_INCLUDE_GLOBS.toSet()) {
            copy(includeGlobs = SupportedFormats.defaultIncludeGlobs)
        } else {
            this
        }
    }

    companion object {
        private val LEGACY_DEFAULT_INCLUDE_GLOBS =
            listOf("*.jpg", "*.jpeg", "*.png", "*.webp")
    }
}


/** User-defined slideshow playlist. Built-in playlists use stable ids in [PlaylistSettings]. */
@Serializable
data class SlideshowPlaylist(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** Stable source ids. Empty means all currently configured sources. */
    val sourceIds: Set<String> = emptySet(),
    /** Exact source/direct-directory selection keys. Empty means all folders. */
    val folderNames: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    /** Restrict to photos uploaded through the frame's web/local upload library. */
    val localUploadsOnly: Boolean = false,
    val selectionMode: SelectionMode? = null,
    val intervalSeconds: Int? = null,
    val transitionSelectionMode: TransitionSelectionMode? = null,
    val transition: TransitionMode? = null,
    val collageMode: PortraitCollageMode? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    fun normalized(): SlideshowPlaylist = copy(
        id = id.trim().take(80),
        name = name.trim().take(80),
        sourceIds = sourceIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        folderNames = folderNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        intervalSeconds = intervalSeconds?.let(PlaybackInterval::clamp),
        transition = transition,
    )
}

/** Local-time rule that activates one playlist. ISO weekday 1 = Monday. */
@Serializable
data class PlaylistScheduleRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val playlistId: String,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startTime: String = "00:00",
    val endTime: String = "00:00",
    /** Higher number wins when rules overlap. */
    val priority: Int = 0,
    val startDateIso: String? = null,
    val endDateIso: String? = null,
    val updatedAtEpochMs: Long = 0L,
) {
    fun normalized(): PlaylistScheduleRule = copy(
        id = id.trim().take(80),
        name = name.trim().take(80),
        playlistId = playlistId.trim().take(80),
        daysOfWeek = daysOfWeek.filter { it in 1..7 }.toSet(),
        priority = priority.coerceIn(-1000, 1000),
    )
}

@Serializable
data class PlaylistSettings(
    val playlists: List<SlideshowPlaylist> = builtInPlaylists(),
    val activePlaylistId: String = PLAYLIST_ALL,
    val defaultPlaylistId: String = PLAYLIST_ALL,
    val scheduleEnabled: Boolean = false,
    val scheduleRules: List<PlaylistScheduleRule> = emptyList(),
    /** 0 means no manual override; Long.MAX_VALUE means until explicitly cancelled. */
    val manualOverrideUntilEpochMs: Long = 0L,
) {
    fun withCurrentDefaults(): PlaylistSettings {
        val byId = LinkedHashMap<String, SlideshowPlaylist>()
        builtInPlaylists().forEach { byId[it.id] = it }
        playlists.map(SlideshowPlaylist::normalized)
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .forEach { if (it.id !in BUILT_IN_IDS) byId[it.id] = it }
        val all = byId.values.toList()
        val active = activePlaylistId.takeIf { id -> all.any { it.id == id } } ?: PLAYLIST_ALL
        val default = defaultPlaylistId.takeIf { id -> all.any { it.id == id } } ?: PLAYLIST_ALL
        return copy(
            playlists = all,
            activePlaylistId = active,
            defaultPlaylistId = default,
            scheduleRules = scheduleRules.map(PlaylistScheduleRule::normalized)
                .filter { it.id.isNotBlank() && it.playlistId.isNotBlank() },
        )
    }

    fun activePlaylist(): SlideshowPlaylist =
        playlists.firstOrNull { it.id == activePlaylistId } ?: builtInPlaylists().first()

    companion object {
        const val PLAYLIST_ALL = "builtin_all"
        const val PLAYLIST_FAVORITES = "builtin_favorites"
        const val PLAYLIST_RECENT_UPLOADS = "builtin_recent_uploads"
        /**
         * The "on this day" memory pool. Deliberately `enabled = false` below — unlike
         * the other built-ins, its photo list is an explicit id set assembled fresh
         * each trigger, not a stable filter, so it must never appear as a manually
         * pickable playlist (docs/FPF-FEAT-ON-THIS-DAY-001.md §4.2). Only
         * `SlideshowViewModel`'s dedicated trigger may activate it.
         */
        const val PLAYLIST_ON_THIS_DAY = "builtin_on_this_day"
        val BUILT_IN_IDS = setOf(PLAYLIST_ALL, PLAYLIST_FAVORITES, PLAYLIST_RECENT_UPLOADS, PLAYLIST_ON_THIS_DAY)

        fun builtInPlaylists(): List<SlideshowPlaylist> = listOf(
            SlideshowPlaylist(PLAYLIST_ALL, "All photos"),
            SlideshowPlaylist(PLAYLIST_FAVORITES, "Favorites", favoritesOnly = true),
            SlideshowPlaylist(
                PLAYLIST_RECENT_UPLOADS,
                "Recent uploads",
                sourceIds = setOf("local_uploads"),
                localUploadsOnly = true,
                selectionMode = SelectionMode.DATE_TAKEN_NEWEST,
            ),
            SlideshowPlaylist(
                PLAYLIST_ON_THIS_DAY,
                "On this day",
                enabled = false,
                selectionMode = SelectionMode.ON_THIS_DAY,
            ),
        )
    }
}

/**
 * Periodic "on this day" memory interlude (docs/FPF-FEAT-ON-THIS-DAY-001.md). The photo
 * pool itself is never stored here — it's assembled fresh at each trigger from
 * `PhotoDao.onThisDayCandidates` + `OnThisDaySelection`; this only holds the schedule
 * and behavior knobs.
 */
@Serializable
data class OnThisDaySettings(
    val enabled: Boolean = false,
    /** How many evenly spaced interludes per day (§0.2 default: 3). */
    val timesPerDay: Int = 3,
    /** How long each interlude plays before reverting (§0.2 default: 5). */
    val durationMinutes: Int = 5,
    /** Years newer than this many years ago are excluded (0 = "this year" counts). */
    val minYearsAgo: Int = 1,
    /** Reserved for Phase 3 (multi-year collage rendering); single-photo playback for now. */
    val collageMode: Boolean = true,
    /** Wall-clock bookkeeping for `OnThisDaySchedule`'s due/grace check. */
    val lastTriggeredEpochMs: Long = 0L,
) {
    fun normalized(): OnThisDaySettings = copy(
        timesPerDay = timesPerDay.coerceIn(1, 12),
        durationMinutes = durationMinutes.coerceIn(1, 60),
        minYearsAgo = minYearsAgo.coerceIn(0, 100),
    )
}

enum class BrightnessMode { MANUAL, SCHEDULED, AMBIENT, SCHEDULED_AMBIENT }
enum class NightAction { DIM_ONLY, PAUSE_SLIDESHOW, BLACK_SCREEN }

@Serializable
data class BrightnessPeriod(
    val id: String,
    val startTime: String,
    val brightness: Float,
    val action: NightAction = NightAction.DIM_ONLY,
) {
    fun normalized(): BrightnessPeriod = copy(
        id = id.trim().take(80),
        brightness = brightness.coerceIn(0.01f, 1f),
    )
}

@Serializable
data class BrightnessAutomationSettings(
    val mode: BrightnessMode = BrightnessMode.MANUAL,
    val manualBrightness: Float = 1f,
    val periods: List<BrightnessPeriod> = listOf(
        BrightnessPeriod("day", "07:00", 1f, NightAction.DIM_ONLY),
        BrightnessPeriod("night", "23:00", 0.08f, NightAction.BLACK_SCREEN),
    ),
    val ambientMinimum: Float = 0.08f,
    val ambientMaximum: Float = 1f,
    val temporaryWakeMinutes: Int = 15,
    val temporaryWakeUntilEpochMs: Long = 0L,
) {
    fun normalized(): BrightnessAutomationSettings = copy(
        manualBrightness = manualBrightness.coerceIn(0.01f, 1f),
        periods = periods.map(BrightnessPeriod::normalized).take(12),
        ambientMinimum = ambientMinimum.coerceIn(0.01f, 1f),
        ambientMaximum = ambientMaximum.coerceIn(ambientMinimum.coerceIn(0.01f, 1f), 1f),
        temporaryWakeMinutes = temporaryWakeMinutes.coerceIn(1, 240),
    )
}

enum class UploadDuplicatePolicy { SKIP, KEEP_BOTH, REPLACE_LOCAL }

@Serializable
data class WebUploadSettings(
    val enabled: Boolean = false,
    val maxFileBytes: Long = 100L * 1024L * 1024L,
    val maxBatchFiles: Int = 1_000,
    val duplicatePolicy: UploadDuplicatePolicy = UploadDuplicatePolicy.SKIP,
    val allowWhilePlaying: Boolean = true,
) {
    fun normalized(): WebUploadSettings = copy(
        maxFileBytes = maxFileBytes.coerceIn(1L * 1024L * 1024L, 500L * 1024L * 1024L),
        maxBatchFiles = maxBatchFiles.coerceIn(1, 1_000),
    )
}

/**
 * Persistent on-disk thumbnail cache for LOCAL (SAF/fallback) photos — see
 * `docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md`. [maxBytes] is the user's requested
 * budget; the *effective* ceiling is clamped against live free space at cache-usage
 * time by [com.example.familyphotoframe.data.cache.LocalThumbnailCache.clampMaxBytes],
 * since a fixed byte value here can't know how much space is actually free later.
 * Off by default: this is a new, not-yet-hardware-validated feature.
 */
@Serializable
data class LocalThumbnailCacheSettings(
    val enabled: Boolean = false,
    val maxBytes: Long = 1L * 1024L * 1024L * 1024L,
) {
    fun normalized(): LocalThumbnailCacheSettings = copy(
        maxBytes = maxBytes.coerceAtLeast(1L * 1024L * 1024L * 1024L),
    )
}

/**
 * Canonical runtime settings (spec §20 subset for Phase 0). [schemaVersion] is
 * carried so import/export can validate compatibility (spec §6.3, §14.1). Stored
 * via a typed DataStore; JSON is used only for import/export.
 */
@Serializable
data class AppSettings(
    val schemaVersion: Int = 2,
    val intervalSeconds: Int = 15,
    val transitionDurationMs: Int = 900,
    val aspectMode: AspectMode = AspectMode.FIT_COLOR,
    /**
     * Defaults stay on the cheapest options: spec §10.3 says fit-blur and motion are
     * Phase 2 features that must be profiled on the reference low-end device before
     * becoming defaults, so the user opts in.
     */
    val transitionSelectionMode: TransitionSelectionMode = TransitionSelectionMode.FIXED,
    val transition: TransitionMode = TransitionMode.CROSSFADE,
    val transitionReduceMotion: Boolean = false,
    val motion: MotionMode = MotionMode.NONE,
    /** Same-folder portrait collage playback for landscape displays. */
    val portraitCollage: PortraitCollageSettings = PortraitCollageSettings(),
    /** New installations use the task-recommended folder-balanced mode. */
    val selectionMode: SelectionMode = SelectionMode.FOLDER_BALANCED_SHUFFLE,
    /** One-time compatibility migration: legacy random becomes global no-repeat. */
    val playbackOrderMigrationVersion: Int = 0,
    /** Restrict playback to photos the user marked as favourites (spec §9.4 curation). */
    val favoritesOnly: Boolean = false,
    /**
     * Exact source/direct-directory selection keys to play (spec §6 and §21).
     * **Empty means every folder**, preserving upgrade behaviour. Legacy folder-name
     * values remain readable by DAO compatibility predicates and are replaced with
     * exact keys whenever the user edits the folder selection.
     */
    val selectedFolders: Set<String> = emptySet(),
    /** What to play when a remote primary is unreachable (spec §9.3). */
    val onUnreachable: UnreachablePolicy = UnreachablePolicy.FALLBACK_SAMPLES,
    val backgroundColorArgb: Long = 0xFF000000L,
    val overlays: OverlaySettings = OverlaySettings(),
    val source: ActiveSource = ActiveSource(),
    val decodeTimeoutMs: Long = 8_000,
    val temporarilySuppressAfterDecodeFailures: Int = 3,
    val autoStartOnBoot: Boolean = false,
    val web: WebSettings = WebSettings(),
    /** Scan include/exclude filters (spec §20). */
    val filters: FilterSettings = FilterSettings(),
    val schedule: ScheduleSettings = ScheduleSettings(),
    /** One-time migration marker for legacy ScheduleSettings sleep/dimming controls. */
    val brightnessAutomationMigrationVersion: Int = 0,
    val weather: WeatherSettings = WeatherSettings(),
    /** Named playback collections and local-time switching rules. */
    val playlists: PlaylistSettings = PlaylistSettings(),
    /** Window-level brightness automation; API 21 compatible and permission-free. */
    val brightnessAutomation: BrightnessAutomationSettings = BrightnessAutomationSettings(),
    /** Privileged local-library web upload policy. */
    val webUpload: WebUploadSettings = WebUploadSettings(),
    /** Persistent on-disk thumbnail cache for local (SAF/fallback) photos. */
    val localThumbnailCache: LocalThumbnailCacheSettings = LocalThumbnailCacheSettings(),
    /** Periodic "on this day" memory interlude (docs/FPF-FEAT-ON-THIS-DAY-001.md). */
    val onThisDay: OnThisDaySettings = OnThisDaySettings(),
    /**
     * Shows a live frame-timing readout for measuring the §22.4 performance budget on
     * the reference device. Off by default and not a user-facing feature — it exists so
     * the last open acceptance item can be measured rather than argued about.
     */
    val showPerformanceOverlay: Boolean = false,
) {
    val intervalSecondsClamped: Int get() = PlaybackInterval.clamp(intervalSeconds)

    /** Apply non-destructive defaults migrations after deserialization/import. */
    fun withCurrentDefaults(): AppSettings {
        val migratedSelection = if (
            playbackOrderMigrationVersion < PLAYBACK_ORDER_MIGRATION_V1 &&
            selectionMode == SelectionMode.LEAST_RECENT_RANDOM
        ) SelectionMode.SHUFFLE_NO_REPEAT else selectionMode

        // The old Schedule -> Sleep UI and the newer Display -> Brightness automation
        // used separate fields. Migrate the legacy timetable only when the newer model
        // is still untouched; otherwise the explicitly configured newer model wins.
        val migrationPending =
            brightnessAutomationMigrationVersion < BRIGHTNESS_AUTOMATION_MIGRATION_V1
        val shouldImportLegacySleep =
            migrationPending && schedule.sleepEnabled &&
                brightnessAutomation == BrightnessAutomationSettings()
        val migratedBrightness = if (shouldImportLegacySleep) {
            brightnessAutomation.copy(
                mode = BrightnessMode.SCHEDULED,
                manualBrightness = schedule.brightnessDay,
                periods = listOf(
                    BrightnessPeriod(
                        id = "day",
                        startTime = schedule.sleepEnd,
                        brightness = schedule.brightnessDay,
                        action = NightAction.DIM_ONLY,
                    ),
                    BrightnessPeriod(
                        id = "night",
                        startTime = schedule.sleepStart,
                        brightness = schedule.brightnessNight,
                        action = NightAction.PAUSE_SLIDESHOW,
                    ),
                ),
            ).normalized()
        } else {
            brightnessAutomation.normalized()
        }
        val migratedSchedule = if (migrationPending) {
            // Disable the legacy runtime switch after migration. The fields remain in
            // the schema for backward-compatible import/export only.
            schedule.copy(sleepEnabled = false)
        } else {
            schedule
        }

        return copy(
            selectionMode = migratedSelection,
            playbackOrderMigrationVersion = PLAYBACK_ORDER_MIGRATION_V1,
            brightnessAutomationMigrationVersion = BRIGHTNESS_AUTOMATION_MIGRATION_V1,
            schedule = migratedSchedule,
            filters = filters.withCurrentDefaultFormats(),
            transition = transition,
            transitionDurationMs = transitionDurationMs.coerceIn(300, 2_000),
            playlists = playlists.withCurrentDefaults(),
            brightnessAutomation = migratedBrightness,
            web = web.copy(rememberedBrowsers = web.rememberedBrowsers.normalized()),
            webUpload = webUpload.normalized(),
            localThumbnailCache = localThumbnailCache.normalized(),
            onThisDay = onThisDay.normalized(),
        )
    }

    companion object {
        private const val PLAYBACK_ORDER_MIGRATION_V1 = 1
        private const val BRIGHTNESS_AUTOMATION_MIGRATION_V1 = 1
    }
}
