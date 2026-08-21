package com.example.familyphotoframe.data.diagnostics

/** Explicit severity stored with every schema-v2 event. */
enum class DiagnosticSeverity { DEBUG, INFO, WARN, ERROR, FATAL }

/** Component that initiated the action represented by an event. */
enum class DiagnosticOrigin {
    APP, ANDROID_UI, WEB_UI, SCHEDULER, RECOVERY, SYSTEM, INTERNAL,
}

/** Durable stream selected by the catalog, never by string matching at the sink. */
enum class DiagnosticStream { STANDARD, BULK }

/** Bounded event-volume policy. Implemented by [DiagnosticsLog]. */
enum class DiagnosticRatePolicy {
    NONE,
    STATE_CHANGE,
    SCAN_PROGRESS,
    BRIGHTNESS_CHANGE,
    DECODE_FAILURE_AGGREGATE,
    PREVIEW_HIT_AGGREGATE,
}

/**
 * One registered diagnostics contract.
 *
 * Field names are an allowlist. Unknown fields are removed before an event reaches the
 * in-memory ring or either durable stream, so every diagnostics surface sees the same
 * privacy-safe record.
 */
data class DiagnosticEventSpec(
    val code: String,
    val category: DiagnosticsLog.Category,
    val severity: DiagnosticSeverity,
    val stream: DiagnosticStream,
    val permittedFields: Set<String>,
    val ratePolicy: DiagnosticRatePolicy = DiagnosticRatePolicy.NONE,
    val operationRequired: Boolean = false,
    val crashEnvelopeAllowed: Boolean = false,
)

/** Required trigger propagated through source refresh, apply, health and scan work. */
enum class SourceRefreshTrigger {
    INITIAL_SETTINGS_LOAD,
    FIRST_RUN_CONFIGURATION,
    REBUILD_ANDROID_UI,
    REBUILD_WEB_UI,
    SOURCE_SETTINGS_CHANGED,
    CREDENTIAL_UPDATED,
    CONFIG_IMPORTED,
    ENCRYPTED_BUNDLE_IMPORTED,
    FILTERS_CHANGED,
    SCHEDULED_RESCAN,
    RECOVERY_PROMOTION,
    PERIODIC_HEALTH_MONITOR,
    LOCAL_UPLOAD_CHANGED,
}

/** Operation metadata passed across asynchronous boundaries. */
data class DiagnosticContext(
    val origin: DiagnosticOrigin = DiagnosticOrigin.INTERNAL,
    val operationId: String? = null,
    val parentOperationId: String? = null,
)

/**
 * Central event catalog for production diagnostics.
 *
 * Registration is deliberately explicit. Category, severity, stream, field allowlist,
 * volume policy and crash eligibility are materialized in each [DiagnosticEventSpec];
 * web and offline consumers never infer them from a code suffix.
 */
object DiagnosticEventCatalog {
    private val appFields = setOf(
        "action", "appVersion", "versionCode", "buildType", "sdkInt", "deviceModel",
        "processId", "heapUsedKb", "heapMaxKb", "nativeHeapKb", "memoryClassMb",
        "lowRam", "imageCacheKb", "imageCacheMaxKb",
        "retainedBytes", "queueCapacity", "queueDepth", "dropped", "droppedTotal",
        "sinkStatus", "errorClass", "errorCode", "outcome", "reason", "trigger",
        "durationMs", "count", "hits", "misses", "stream", "sessionToken", "browserToken", "photoToken",
        "playlistToken", "policy", "expiry",
        "port", "bindCategory", "revision", "format", "sizeBytes", "status",
        "exceptionClass", "rootCauseClass", "threadName", "mainThread", "crashOrigin",
        "previousSessionId", "lastSequence", "checksumValid", "crashAtEpochMs",
        "crashElapsedRealtimeMs", "exitReasonCode", "exitTimestampMs", "descriptionCode",
        "exitReason", "importance", "pssKb", "rssKb", "correlatedEnvelope",
        "evidenceIncomplete", "retentionStatus", "fieldsDropped", "flushTimeoutMs",
        "standardBytes", "bulkBytes", "standardGenerations", "bulkGenerations",
        "lastWriteEpochMs", "lastFlushEpochMs", "crashEnvelopeBytes", "surface",
        "engineState", "presentationToken", "sourceKind", "layout", "transitionCode",
        "activeOperationId", "activeOperationStage", "configRevision", "indexedCount",
        "refreshToken", "stage", "debuggerAttached", "stallDurationMs", "frameCount",
        "fileIndex", "fileCount", "importKind", "validationResult", "rollbackAvailable",
        "unknownCode", "preparedSlideCount", "renderedSlideCount", "decodedBitmapCount",
        "appBitmapCount", "activeDecodedBytes", "pendingDisposals", "memoryProtectionLevel",
        "oomCount", "workerLimit", "queueLimit", "uploadedPhotosPreserved",
        "processStartKind", "currentElapsedRealtimeMs", "previousElapsedRealtimeMs",
        "estimatedBootEpochMs", "previousEstimatedBootEpochMs", "bootEpochDeltaMs",
        "previousMarkerAgeMs",
    )

    private val engineFields = setOf(
        "action", "reason", "trigger", "outcome", "sourceKind", "sourceToken",
        "folderToken", "presentationToken", "playlistToken", "photoToken", "anchorToken",
        "layout", "transitionCode", "selectionMode", "poolSize", "primaryCount",
        "fallbackCount", "count", "found", "active", "paused", "asleep", "favorite",
        "cachedOnly", "failures", "stage", "durationMs", "elapsedMs", "decodeMs",
        "renderMs", "intervalMs", "members", "candidateCount", "reducedMotion",
        "performanceClass", "frameCount", "slowFrames", "frozenFrames", "p50Ms",
        "p95Ms", "p99Ms", "maxMs", "cycle", "remaining", "eligible", "inserted",
        "removed", "deferred", "recovered", "exhausted", "attempt", "errorClass",
        "errorCode", "format", "width", "height", "motionMode", "amplitude",
        "periodMs", "periodToken", "level", "mode", "offset", "revision",
        "activeBits", "historyCleared", "sizeBytes", "years", "preview",
        "sameFolderCandidateCount", "portraitCandidateCount", "squareCandidateCount",
        "landscapeCandidateCount", "evaluatedTwoPhotoCombinations",
        "evaluatedThreePhotoCombinations", "evaluatedLayoutCount", "folderTier",
        "orientationTier", "anchorOrientation", "screenCoverage", "averageCropLoss", "maximumCropLoss",
        "timeDistanceScore", "recentPenalty", "decisionReason", "actualDurationMs",
        "collageMode", "collageOrientationFilter", "collageLayoutPreference",
        "configuredMaxCollagePhotos", "memoryMaxCollagePhotos",
        "effectiveMaxCollagePhotos", "threePhotoEvaluationAllowed",
        "threePhotoEvaluationPerformed", "threePhotoSkipReason", "rawCandidateCount",
        "metadataCandidateCount", "localProbeCount", "localProbeBudgetSkippedCount",
        "remoteProbeCount", "remoteProbeSuccessCount", "remoteProbeFailureCount",
        "remoteProbeBudgetSkippedCount", "remoteProbeByteLimit", "remoteUnknownSkippedCount",
        "probeBudgetSkippedCount",
    )

    private val sourceFields = setOf(
        "sourceKind", "sourceToken", "trigger", "configRevision", "refreshToken",
        "stage", "durationMs", "outcome", "errorClass", "errorCode", "reason",
        "healthState", "timeoutMs", "attempt", "waitMs", "cached", "poolSize",
        "found", "recoveryState", "completionState", "cancellationReason",
        "supersededByOperationId", "coalescedWithOperationId", "isChosen",
        "credentialChanged", "certificateState",
    )

    private val scanFields = setOf(
        "sourceKind", "sourceToken", "trigger", "configRevision", "refreshToken",
        "stage", "durationMs", "found", "total", "errors", "exifMisses",
        "reconciled", "completionState", "cancellationReason", "errorClass",
        "errorCode", "reason", "scanOwnerOperationId", "coalescedWithOperationId",
        "supersededByOperationId", "indexed", "failed", "remaining", "batch",
        "progressBucket", "includeSubfolders",
    )

    private val memoryFields = setOf(
        "trigger", "reason", "level", "response", "previousLevel", "memoryProtectionLevel",
        "heapUsedKb", "heapMaxKb", "heapBeforeKb", "heapAfterKb", "freedKb",
        "nativeHeapKb", "pssKb",
        "rssKb", "imageCacheKb", "imageCacheMaxKb", "imageCacheBeforeKb", "beforeKb",
        "webPreviewCleared", "uptimeSec", "pressurePercent", "surface", "engineState",
        "presentationToken", "sourceKind", "layout", "transitionCode", "circuitOpenMs",
        "oomCount", "preloadAllowed", "maxCollagePhotos", "targetScalePercent",
        "preparedSlideCount", "renderedSlideCount", "decodedBitmapCount", "appBitmapCount",
        "activeDecodedBytes", "pendingDisposals", "gcRequested",
        "durationMs", "errorClass", "outcome",
        "batteryTelemetryStatus", "batteryLevelPct", "batteryStatus", "powerSource",
        "batteryPresent", "batteryHealth", "batteryVoltageMv", "batteryTempDeciC",
        "batteryCurrentUa", "batteryChargeCounterUah",
    )

    private val lifecycleFields = setOf(
        "activityToken", "activityState", "surface", "previousSurface", "engineState",
        "previousEngineState", "reason", "orientation", "sizeClass", "densityBucket",
        "interactive", "systemUiFlags", "retryCount", "durationMs", "legacyPath",
    )

    private val cacheFields = setOf(
        "action", "reason", "trigger", "count", "sizeBytes", "beforeKb", "afterKb",
        "sourceKind", "presentationToken", "hits", "misses", "durationMs",
    )

    private val decodeFields = setOf(
        "sourceKind", "presentationToken", "photoToken", "stage", "format",
        "errorClass", "errorCode", "permanent", "sourceLevel", "failures", "count",
        "firstEpochMs", "lastEpochMs", "durationMs", "width", "height", "layout",
    )

    private val lifecycleCodes = setOf(
        "ACTIVITY_CREATED", "ACTIVITY_STARTED", "ACTIVITY_RESUMED", "ACTIVITY_PAUSED",
        "ACTIVITY_STOPPED", "ACTIVITY_DESTROYED", "SLIDESHOW_SURFACE_CHANGED",
        "ENGINE_STATE_CHANGED", "SCREEN_INTERACTIVE_CHANGED", "DISPLAY_CONFIGURATION_CHANGED",
        "IMMERSIVE_REQUESTED", "IMMERSIVE_APPLIED", "IMMERSIVE_LOST",
        "IMMERSIVE_RETRY_SCHEDULED", "IMMERSIVE_RESTORED", "IMMERSIVE_EXITED_BY_USER",
    )

    private val sourceOperationCodes = setOf(
        "REBUILD_REQUESTED", "SOURCE_REFRESH_REQUESTED", "SOURCE_APPLY_QUEUED",
        "SOURCE_APPLY_COALESCED", "SOURCE_APPLY_SUPERSEDED", "SOURCE_APPLY_STARTED",
        "SOURCE_POOL_CONFIGURED", "SOURCE_REFRESH_COMPLETED", "SOURCE_REFRESH_CANCELLED",
        "SOURCE_REFRESH_FAILED", "SOURCE_TEST_STARTED", "SOURCE_TEST_COMPLETED",
        "SOURCE_TEST_FAILED", "SOURCE_HEALTH_CHECK_STARTED", "SOURCE_HEALTH_CHECK_COMPLETED",
        "SOURCE_HEALTH_CHECK_FAILED", "SOURCE_RECOVERY_STARTED", "SOURCE_RECOVERED",
        "SOURCE_RECOVERY_CANCELLED", "SOURCE_RECOVERY_PROMOTION_ABORTED",
        "SOURCE_UNAVAILABLE", "SOURCE_BACKOFF", "SOURCE_BACKOFF_EXHAUSTED",
        "SOURCE_RECOVERY_REQUIRED", "SOURCE_EARLY_PLAYBACK_STARTED",
    )

    private val scanOperationCodes = setOf(
        "SCAN_STARTED", "SCAN_PROGRESS", "SCAN_COALESCED", "SCAN_SUPERSEDED",
        "SCAN_COMPLETED", "SCAN_ABORTED", "SCAN_RECONCILIATION_COMPLETED",
        "SCAN_RECONCILIATION_SKIPPED",
    )

    private val standardAppCodes = setOf(
        "APP_CREATE", "SESSION_START", "RUNTIME_CONTEXT_READY", "BOOT_AUTOSTART",
        "BOOT_AUTOSTART_BLOCKED", "BOOT_AUTOSTART_DISABLED", "BOOT_AUTOSTART_LAUNCHED",
        "CONFIG_IMPORTED", "CONFIG_IMPORT_REJECTED", "BUNDLE_IMPORTED",
        "BUNDLE_IMPORT_REJECTED", "DIAGNOSTICS_CLEARED", "DIAGNOSTICS_QUEUE_OVERFLOW",
        "DIAGNOSTICS_SINK_FAILED", "DIAGNOSTICS_SINK_RECOVERED", "DIAGNOSTICS_FLUSH_TIMEOUT",
        "DIAGNOSTICS_UNKNOWN_EVENT", "UNCAUGHT_EXCEPTION", "PREVIOUS_UNCAUGHT_EXCEPTION",
        "PREVIOUS_CRASH_EVIDENCE", "MAIN_THREAD_STALL_STARTED", "MAIN_THREAD_STALL_ESCALATED",
        "MAIN_THREAD_STALL_RECOVERED", "PREVIOUS_ANR_EVIDENCE", "PROCESS_EXIT_RECORDED",
        "REMEMBERED_BROWSERS_KEPT_AFTER_PIN_RESET", "REMEMBERED_BROWSER_CLOCK_ROLLBACK",
        "REMEMBERED_BROWSER_CREATED", "REMEMBERED_BROWSER_EXPIRED", "REMEMBERED_BROWSER_KEY_LOST",
        "REMEMBERED_BROWSER_POLICY_CHANGED", "REMEMBERED_BROWSER_REVOKED",
        "REMEMBERED_BROWSER_REVOKE_ALL", "REMEMBERED_BROWSER_REVOKE_OTHERS",
        "REMEMBERED_BROWSER_TOKEN_REPLAYED", "REMEMBERED_BROWSER_TOKEN_ROTATED",
        "TEMPORARY_WAKE_STARTED", "WEATHER_FETCH_FAILED", "WEATHER_OK", "WEB_BACKUP_IMPORTED",
        "WEB_BACKUP_ROLLED_BACK", "WEB_FACTORY_RESET", "WEB_FACTORY_RESET_STARTED",
        "WEB_FACTORY_RESET_COMPLETED", "WEB_FACTORY_RESET_FAILED", "WEB_NO_LAN", "WEB_STARTED",
        "WEB_START_FAILED", "WEB_STOPPED", "WEB_SUPPRESSION_CLEARED", "WEB_UNHIDE_ALL",
        "DECODE_SUPPRESSION_EXPIRED",
        "WEB_CONNECTION_REJECTED", "WEB_UPLOADS_QUIESCED_FOR_FACTORY_RESET",
        "WEB_UPLOAD_FILE_STARTED", "WEB_UPLOAD_INDEX_COMPLETED", "WEB_UPLOAD_SESSION_CANCELLED",
        "WEB_UPLOAD_SESSION_COMPLETED", "WEB_UPLOAD_SESSION_CREATED", "WEB_UPLOAD_SESSION_EXPIRED",
        "WEB_UPLOAD_FILE_CANCELLED", "WEB_UPLOAD_FILE_COMPLETED", "WEB_UPLOAD_FILE_DUPLICATE",
        "WEB_UPLOAD_FILE_REJECTED", "WEB_PLAYLIST_ACTION", "WEB_PLAYLIST_SCHEDULE_ACTION",
        "WEB_CONTROL", "WEB_ERROR", "WEB_MAINTENANCE_ACTION", "WEB_PAIRED",
        "WEB_PAIR_LOCKED", "WEB_PAIR_REJECTED", "REMEMBERED_BROWSER_SESSION_CREATED",
        "CONFIG_EXPORTED", "CONFIG_EXPORT_FAILED", "CONFIG_IMPORT_READ_FAILED",
        "CONFIG_IMPORT_TOO_LARGE",
        "PREVIEW_HIT_SUMMARY",
    )

    private val engineCodes = setOf(
        "BRIGHTNESS_LEVEL_APPLIED", "BRIGHTNESS_UNCHANGED_HEARTBEAT",
        "FOLDER_CYCLE_STARTED", "FOLDER_DEFERRED", "FOLDER_DEFERRED_RELEASED",
        "FOLDER_INSERTED", "FOLDER_PRESENTED", "FOLDER_PREVIEW_ONCE",
        "FOLDER_PREVIEW_REJECTED", "FOLDER_PREVIEW_REQUESTED", "FOLDER_PREVIEW_RETURNED",
        "FOLDER_REMOVED", "FOLDER_RESERVED", "FOLDER_SKIPPED", "PANEL_MOTION",
        "PERF_SAMPLE", "PHOTOS_UNHIDDEN", "PHOTO_CONSUMED", "PHOTO_CYCLE_STARTED",
        "PHOTO_EXCLUDED", "PHOTO_EXCLUSION_UNDONE", "PHOTO_HIDDEN",
        "PHOTO_IDENTITY_RECONCILED", "PHOTO_INSERTED", "PHOTO_REMOVED", "PHOTO_RESERVED",
        "PLAYLIST_FOLDER_APPLIED", "PLAYLIST_SCHEDULE_SWITCHED", "PLAYLIST_STARTED",
        "POOL_CONFIGURED", "PRESENTATION_COMMITTED", "PRESENTATION_COMMIT_REJECTED",
        "PRESENTATION_PREPARED_COMMIT", "PRESENTATION_RELEASED", "PRESENTATION_RESERVED",
        "RESERVATION_RECOVERED", "SHUFFLE_HISTORY_CLEARED", "SHUFFLE_RECONCILED",
        "SHUFFLE_RESET", "SHUFFLE_SCOPE_CREATED", "SHUFFLE_SCOPE_RESTORED",
        "SHUFFLE_SELECTION_TIMING", "SHUFFLE_STARTUP_RECOVERY", "SLIDE_SELECTED",
        "SLIDE_RENDERED", "SLIDE_SHOWN", "TRANSITION_SELECTED", "TRANSITION_STARTED",
        "TRANSITION_COMPLETED", "TRANSITION_CANCELLED", "TRANSITION_FALLBACK",
        "TRANSITION_PERFORMANCE_WARNING",
        "ENGINE_PAUSED", "ENGINE_RESUMED", "ENGINE_SLEEP_ENTERED", "ENGINE_SLEEP_EXITED",
        "FAVORITE_CHANGED", "COLLAGE_CANDIDATE_QUARANTINED", "PHOTO_QUARANTINED",
        "COLLAGE_DOWNGRADED", "COLLAGE_FALLBACK_SINGLE", "COLLAGE_PRELOAD_STARTED",
        "COLLAGE_READY", "COLLAGE_RENDERED", "COLLAGE_SELECTION_EVALUATED",
        "FAVORITE_ADD", "FAVORITE_REMOVE",
        "FOLDER_RETRY", "PAUSE", "RESUME", "SLEEP_ENTER", "SLEEP_EXIT",
        "SLIDESHOW_CONTROLS_HOLD", "SLIDESHOW_CONTROLS_RELEASE",
        "PHOTO_FAVORITE_ADDED", "PHOTO_FAVORITE_REMOVED",
        "TRANSITION_LOW_PERFORMANCE_ENTERED", "TRANSITION_LOW_PERFORMANCE_EXITED",
        "ON_THIS_DAY_SKIPPED_EMPTY", "ON_THIS_DAY_TRIGGERED",
    )

    private val legacySourceCodes = setOf(
        "SAF_PROVIDER_ERROR", "SAF_SKIPPED", "SMB_UNAVAILABLE", "SOURCE_APPLY_FAILED",
        "SOURCE_STALE_CACHE_ACTIVE", "SOURCE_FALLBACK_ACTIVE",
        "SOURCE_HEALTH_RESULT_SUPERSEDED",
        "SYNOLOGY_CERT_PINNED", "SYNOLOGY_CERT_UNPINNED", "SYNOLOGY_UNAVAILABLE",
        "WEBDAV_UNAVAILABLE",
    )

    private val legacyScanCodes = setOf(
        "AUTO_RESCAN_DONE", "AUTO_RESCAN_FAILED", "AUTO_RESCAN_START",
        "CONTENT_HASH_BACKGROUND_PASS", "CONTENT_HASH_BACKOFF", "CONTENT_HASH_INDEX_COMPLETE",
        "EXIF_BACKFILL_MISS", "SCAN_DONE", "SCAN_ERROR", "SCAN_RECONCILE_SKIPPED", "SCAN_START",
    )

    private val memoryCodes = setOf(
        "DECODE_OOM_RECOVERY", "HEAP_SAMPLE", "IMAGE_CACHE_CLEARED", "LOW_MEMORY", "TRIM_MEMORY",
        "MEMORY_PROTECTION_CHANGED", "MEMORY_CLEANUP_REQUESTED",
        "MEMORY_SELF_RECOVERY_GC", "MEMORY_PROCESS_RESTART_SCHEDULED",
        "MEMORY_PROCESS_RESTART_SUPPRESSED", "MEMORY_PROCESS_RESTART_FAILED",
        "MEMORY_PROCESS_RECOVERY_COMPLETED",
    )

    private val cacheCodes = setOf("WEB_CACHE_CLEARED")
    private val decodeCodes = setOf(
        "COLLAGE_CANDIDATE_FAILED", "DECODE_FAILED", "DECODE_RETRY", "DECODE_UNSUPPORTED",
        "DECODE_FAILURE_SUMMARY",
    )
    private val bulkAppCodes = setOf("WEB_PREVIEW_CACHE_HIT", "WEB_PREVIEW_GENERATED")

    private val bulkEngineCodes = setOf(
        "SLIDE_SELECTED", "SLIDE_RENDERED", "SLIDE_SHOWN", "TRANSITION_SELECTED",
        "TRANSITION_STARTED", "TRANSITION_COMPLETED", "TRANSITION_CANCELLED",
        "TRANSITION_FALLBACK", "TRANSITION_PERFORMANCE_WARNING", "SHUFFLE_SELECTION_TIMING",
        "FOLDER_RESERVED", "PHOTO_RESERVED", "PRESENTATION_RESERVED", "PHOTO_CONSUMED",
        "FOLDER_PRESENTED", "PRESENTATION_COMMITTED", "PRESENTATION_RELEASED",
        "PRESENTATION_PREPARED_COMMIT", "PANEL_MOTION", "COLLAGE_PRELOAD_STARTED",
        "COLLAGE_READY", "COLLAGE_RENDERED", "COLLAGE_SELECTION_EVALUATED",
        "COLLAGE_DOWNGRADED", "COLLAGE_FALLBACK_SINGLE",
    )

    private val errorCodes = setOf(
        "AUTO_RESCAN_FAILED", "BOOT_AUTOSTART_BLOCKED", "BUNDLE_IMPORT_REJECTED",
        "CONFIG_IMPORT_REJECTED", "DIAGNOSTICS_QUEUE_OVERFLOW", "DIAGNOSTICS_SINK_FAILED",
        "MAIN_THREAD_STALL_ESCALATED", "SCAN_ABORTED", "SOURCE_APPLY_FAILED",
        "SOURCE_REFRESH_FAILED", "SOURCE_TEST_FAILED", "SOURCE_HEALTH_CHECK_FAILED",
        "UNCAUGHT_EXCEPTION", "WEB_FACTORY_RESET_FAILED", "WEB_START_FAILED",
        "WEATHER_FETCH_FAILED", "DECODE_FAILED", "MEMORY_PROCESS_RESTART_FAILED",
    )

    private val fatalCodes = setOf(
        "UNCAUGHT_EXCEPTION", "PREVIOUS_UNCAUGHT_EXCEPTION", "PREVIOUS_CRASH_EVIDENCE",
    )
    private val warnCodes = setOf(
        "COLLAGE_CANDIDATE_FAILED", "DECODE_FAILURE_SUMMARY", "DECODE_OOM_RECOVERY",
        "DECODE_UNSUPPORTED", "DIAGNOSTICS_FLUSH_TIMEOUT",
        "LOW_MEMORY", "MAIN_THREAD_STALL_STARTED", "PREVIOUS_ANR_EVIDENCE",
        "PROCESS_EXIT_RECORDED", "SAF_PROVIDER_ERROR",
        "SCAN_ERROR", "SCAN_RECONCILIATION_SKIPPED", "SCAN_RECONCILE_SKIPPED",
        "SOURCE_UNAVAILABLE", "SOURCE_BACKOFF", "SOURCE_BACKOFF_EXHAUSTED",
        "SYNOLOGY_UNAVAILABLE", "WEBDAV_UNAVAILABLE", "SMB_UNAVAILABLE",
        "SOURCE_RECOVERY_PROMOTION_ABORTED", "TRANSITION_PERFORMANCE_WARNING",
        "WEB_CONNECTION_REJECTED", "MEMORY_PROCESS_RESTART_SCHEDULED",
        "MEMORY_PROCESS_RESTART_SUPPRESSED",
    )

    private val specs: Map<String, DiagnosticEventSpec> = buildMap {
        fun register(
            codes: Set<String>,
            category: DiagnosticsLog.Category,
            fields: Set<String>,
            streamFor: (String) -> DiagnosticStream = { DiagnosticStream.STANDARD },
            rateFor: (String) -> DiagnosticRatePolicy = { DiagnosticRatePolicy.NONE },
            operationRequired: Boolean = false,
        ) {
            for (code in codes) {
                check(code !in this) { "Duplicate diagnostic event code: $code" }
                put(
                    code,
                    DiagnosticEventSpec(
                        code = code,
                        category = category,
                        severity = when (code) {
                            in fatalCodes -> DiagnosticSeverity.FATAL
                            in errorCodes -> DiagnosticSeverity.ERROR
                            in warnCodes -> DiagnosticSeverity.WARN
                            else -> DiagnosticSeverity.INFO
                        },
                        stream = streamFor(code),
                        permittedFields = fields,
                        ratePolicy = rateFor(code),
                        operationRequired = operationRequired,
                        crashEnvelopeAllowed = code in setOf(
                            "UNCAUGHT_EXCEPTION", "PREVIOUS_CRASH_EVIDENCE",
                            "MAIN_THREAD_STALL_STARTED", "MAIN_THREAD_STALL_ESCALATED",
                            "PREVIOUS_ANR_EVIDENCE", "PROCESS_EXIT_RECORDED",
                        ),
                    ),
                )
            }
        }

        register(standardAppCodes, DiagnosticsLog.Category.APP, appFields)
        register(bulkAppCodes, DiagnosticsLog.Category.APP, appFields, { DiagnosticStream.BULK }, {
            if (it == "WEB_PREVIEW_CACHE_HIT") DiagnosticRatePolicy.PREVIEW_HIT_AGGREGATE
            else DiagnosticRatePolicy.NONE
        })
        register(engineCodes, DiagnosticsLog.Category.ENGINE, engineFields, {
            if (it in bulkEngineCodes) DiagnosticStream.BULK else DiagnosticStream.STANDARD
        }, {
            when (it) {
                "BRIGHTNESS_LEVEL_APPLIED" -> DiagnosticRatePolicy.BRIGHTNESS_CHANGE
                else -> DiagnosticRatePolicy.NONE
            }
        })
        register(sourceOperationCodes, DiagnosticsLog.Category.SOURCE, sourceFields, operationRequired = true)
        register(legacySourceCodes - sourceOperationCodes, DiagnosticsLog.Category.SOURCE, sourceFields)
        register(scanOperationCodes, DiagnosticsLog.Category.SCAN, scanFields, rateFor = {
            if (it == "SCAN_PROGRESS") DiagnosticRatePolicy.SCAN_PROGRESS else DiagnosticRatePolicy.NONE
        }, operationRequired = true)
        register(legacyScanCodes - scanOperationCodes, DiagnosticsLog.Category.SCAN, scanFields)
        register(memoryCodes, DiagnosticsLog.Category.MEMORY, memoryFields)
        register(cacheCodes, DiagnosticsLog.Category.CACHE, cacheFields)
        register(decodeCodes, DiagnosticsLog.Category.DECODE, decodeFields, {
            if (it == "DECODE_FAILURE_SUMMARY") DiagnosticStream.STANDARD else DiagnosticStream.BULK
        }, {
            if (it == "DECODE_FAILURE_SUMMARY") DiagnosticRatePolicy.NONE
            else DiagnosticRatePolicy.DECODE_FAILURE_AGGREGATE
        })
        register(lifecycleCodes, DiagnosticsLog.Category.LIFECYCLE, lifecycleFields)
    }

    val all: Collection<DiagnosticEventSpec> get() = specs.values

    fun find(code: String): DiagnosticEventSpec? = specs[code]

    fun require(code: String): DiagnosticEventSpec = specs[code]
        ?: error("Unregistered diagnostic event code: $code")
}
