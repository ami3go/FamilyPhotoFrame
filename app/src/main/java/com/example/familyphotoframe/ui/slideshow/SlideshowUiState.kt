package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.OverlaySettings
import com.example.familyphotoframe.domain.engine.EngineUiModel

/**
 * What the slideshow surface is currently showing. The engine drives photo content;
 * these cases cover the non-photo surfaces the UI must present (spec §9.1 states and
 * §8 recovery): first run, a recoverable source problem, or an empty index.
 */
sealed interface Surface {
    data object Loading : Surface
    data object FirstRun : Surface
    data class Recovery(val message: String) : Surface
    data object EmptyIndex : Surface
    data object Playing : Surface
}




data class RememberedBrowserUi(
    val id: String,
    val label: String,
    val browserSummary: String = "",
    val osSummary: String = "",
    val createdAtEpochMs: Long = 0L,
    val lastUsedAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long? = null,
    val revoked: Boolean = false,
)

data class FrameHealthSummary(
    val level: String = "OK",
    val headline: String = "Checking frame health…",
    val totalPhotos: Int = 0,
    val eligiblePhotos: Int = 0,
    val hiddenPhotos: Int = 0,
    val favoritePhotos: Int = 0,
    val failedPhotos: Int = 0,
    val localUploadPhotos: Int = 0,
    val freeStorageBytes: Long = 0L,
    val recommendations: List<String> = emptyList(),
)

/**
 * Single immutable state object the slideshow screen renders (spec §3 unidirectional
 * state). Everything the UI needs is here; the UI sends intents back to the ViewModel.
 */
data class SlideshowUiState(
    val surface: Surface = Surface.Loading,
    val engine: EngineUiModel = EngineUiModel(),
    val aspectMode: AspectMode = AspectMode.FIT_COLOR,
    val transitionSelectionMode: com.example.familyphotoframe.data.settings.TransitionSelectionMode =
        com.example.familyphotoframe.data.settings.TransitionSelectionMode.FIXED,
    val transition: com.example.familyphotoframe.data.settings.TransitionMode =
        com.example.familyphotoframe.data.settings.TransitionMode.CROSSFADE,
    val transitionReduceMotion: Boolean = false,
    val motion: com.example.familyphotoframe.data.settings.MotionMode =
        com.example.familyphotoframe.data.settings.MotionMode.NONE,
    val portraitCollage: com.example.familyphotoframe.data.settings.PortraitCollageSettings =
        com.example.familyphotoframe.data.settings.PortraitCollageSettings(),
    val overlays: OverlaySettings = OverlaySettings(),
    val backgroundColorArgb: Long = 0xFF000000L,
    val transitionDurationMs: Int = 900,
    val intervalSecondsForUi: Int = 15,
    /** Non-null while a scan is in progress; the value is the running found count. */
    val indexingFound: Int? = null,
    /** Transient one-line notice (e.g. picker cancelled) shown briefly over content. */
    val transientNotice: String? = null,
    val showInfo: Boolean = false,
    /** Current SMB connection settings (non-secret), for prefilling the setup form. */
    val smb: com.example.familyphotoframe.data.settings.SmbSettings? = null,
    /** Current Synology connection settings (non-secret), for prefilling the setup form. */
    val synology: com.example.familyphotoframe.data.settings.SynologySettings? = null,
    /** Current WebDAV/Nextcloud connection settings (non-secret), for prefilling the form. */
    val webdav: com.example.familyphotoframe.data.settings.WebDavSettings? = null,
    /**
     * Fingerprint of an untrusted certificate awaiting the user's approval, or null when
     * there is nothing to approve (plain HTTP, unreachable, or already platform-trusted).
     */
    val synologyCertFingerprint: String? = null,
    /** Result text of the last "Test connection" action, or null. */
    val smbTestResult: String? = null,
    val autoStartOnBoot: Boolean = false,
    /** Embedded web setup server settings (spec §15). */
    val web: com.example.familyphotoframe.data.settings.WebSettings =
        com.example.familyphotoframe.data.settings.WebSettings(),
    /** Pairing PIN to show on the frame while the web server runs, or null. */
    val webPin: String? = null,
    /** True while PBKDF2/session revocation for a requested new pairing PIN runs off-main. */
    val webPinRegenerationInProgress: Boolean = false,
    /** LAN URL of the running web server, or null when it is off. */
    val webUrl: String? = null,
    /** One-time pairing URL rendered as an on-screen QR code, or null. */
    val webQrUrl: String? = null,
    /** Quiet-hours settings (spec §20 `schedule`). */
    val schedule: com.example.familyphotoframe.data.settings.ScheduleSettings =
        com.example.familyphotoframe.data.settings.ScheduleSettings(),
    /**
     * EXIF read at display time for the photo currently on screen, when it was not
     * available at index time (Phase 2 increment 8). The overlay layer prefers this over
     * the engine's [engine] snapshot, which was built before the backfill ran.
     */
    val currentPhotoExif: com.example.familyphotoframe.data.index.ExifMetadata? = null,
    /** Live frame-timing readout, non-null only while the performance overlay is on. */
    val performanceReadout: String? = null,
    /** Whether the performance overlay is enabled (§22.4 measurement aid). */
    val showPerformanceOverlay: Boolean = false,
    /** Rendered weather string, or null when unavailable/disabled (spec §11). */
    val weatherText: String? = null,
    val weather: com.example.familyphotoframe.data.settings.WeatherSettings =
        com.example.familyphotoframe.data.settings.WeatherSettings(),
    /** True while inside the quiet-hours window. */
    val asleep: Boolean = false,
    /** Window brightness to apply, 0..1 (never a system-wide setting). */
    val screenBrightness: Float = 1f,
    /** How the next photo is chosen (spec §9.6). */
    val selectionMode: com.example.familyphotoframe.data.settings.SelectionMode =
        com.example.familyphotoframe.data.settings.SelectionMode.FOLDER_BALANCED_SHUFFLE,
    /** Scan include/exclude filters (spec §20). */
    val filters: com.example.familyphotoframe.data.settings.FilterSettings =
        com.example.familyphotoframe.data.settings.FilterSettings(),
    /** Restrict playback to curated favourites (spec §9.4). */
    val favoritesOnly: Boolean = false,
    /** Folders playback is restricted to; empty means all folders (spec §9.4). */
    val selectedFolders: Set<String> = emptySet(),
    /** Extra source kinds merged into the primary pool alongside the chosen one. */
    val alsoPlay: Set<com.example.familyphotoframe.data.settings.ActiveSourceKind> = emptySet(),
    /** Source kinds that have usable connection settings, so can be merged in. */
    val configurableKinds: Set<com.example.familyphotoframe.data.settings.ActiveSourceKind> = emptySet(),
    /** Per-source role, reachability and indexed count for the source indicator. */
    val sourceStatuses: List<com.example.familyphotoframe.domain.engine.SourceStatus> = emptyList(),
    /** The primary source kind the user chose. */
    val activeSourceKind: com.example.familyphotoframe.data.settings.ActiveSourceKind =
        com.example.familyphotoframe.data.settings.ActiveSourceKind.NONE,
    /** What to play when a remote primary is unreachable (spec §9.3). */
    val onUnreachable: com.example.familyphotoframe.data.settings.UnreachablePolicy =
        com.example.familyphotoframe.data.settings.UnreachablePolicy.FALLBACK_SAMPLES,
    /**
     * True while the frame is showing cached photos from an unreachable remote source.
     * Surfaced so the UI can say so quietly rather than letting the user wonder why the
     * NAS "still works" while the network is down.
     */
    val stalePlayback: Boolean = false,
    /** The presentation actually committed on screen, including every collage member. */
    val visiblePresentationPhotos: List<com.example.familyphotoframe.domain.engine.DisplayPhoto> = emptyList(),
    /** Ids hidden by the latest touch action while its Undo affordance is active. */
    val undoHiddenPhotoIds: List<Long> = emptyList(),
    val playlists: List<com.example.familyphotoframe.data.settings.SlideshowPlaylist> =
        com.example.familyphotoframe.data.settings.PlaylistSettings.builtInPlaylists(),
    val activePlaylistId: String = com.example.familyphotoframe.data.settings.PlaylistSettings.PLAYLIST_ALL,
    val activePlaylistName: String = "All photos",
    val playlistScheduleEnabled: Boolean = false,
    val playlistScheduleRules: List<com.example.familyphotoframe.data.settings.PlaylistScheduleRule> = emptyList(),
    val activePlaylistRuleName: String? = null,
    val brightnessAutomation: com.example.familyphotoframe.data.settings.BrightnessAutomationSettings =
        com.example.familyphotoframe.data.settings.BrightnessAutomationSettings(),
    val activeBrightnessPeriodId: String? = null,
    val activeNightAction: com.example.familyphotoframe.data.settings.NightAction =
        com.example.familyphotoframe.data.settings.NightAction.DIM_ONLY,
    val temporaryWakeActive: Boolean = false,
    val blackScreen: Boolean = false,
    val ambientLux: Float? = null,
    val ambientSensorAvailable: Boolean = false,
    val health: FrameHealthSummary = FrameHealthSummary(),
    val webUpload: com.example.familyphotoframe.data.settings.WebUploadSettings =
        com.example.familyphotoframe.data.settings.WebUploadSettings(),
    val rememberedBrowserRecords: List<RememberedBrowserUi> = emptyList(),
    val localThumbnailCache: com.example.familyphotoframe.data.settings.LocalThumbnailCacheSettings =
        com.example.familyphotoframe.data.settings.LocalThumbnailCacheSettings(),
    /** Populated on demand by `refreshLocalThumbnailCacheInfo()`; null until first refresh. */
    val localThumbnailCacheUsageBytes: Long? = null,
    val localThumbnailCacheEffectiveMaxBytes: Long? = null,
    val localThumbnailCacheRebuildInProgress: Boolean = false,
    val localThumbnailCacheRebuildCount: Int = 0,
    /** Periodic "on this day" memory interlude (docs/FPF-FEAT-ON-THIS-DAY-001.md). */
    val onThisDay: com.example.familyphotoframe.data.settings.OnThisDaySettings =
        com.example.familyphotoframe.data.settings.OnThisDaySettings(),
)
