package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.example.familyphotoframe.data.cache.MediaTransferPolicy
import com.example.familyphotoframe.data.diagnostics.BitmapLifecycleTracker
import com.example.familyphotoframe.data.diagnostics.NativeAllocationStageTracker
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.data.settings.DecodeResolution
import com.example.familyphotoframe.data.settings.NativeMemoryHilMode
import com.example.familyphotoframe.domain.engine.DecodeColorPolicy
import com.example.familyphotoframe.domain.engine.DeviceMemoryTier
import com.example.familyphotoframe.domain.engine.DeviceMemoryTierPolicy
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.domain.engine.PlaybackMemoryState
import com.example.familyphotoframe.domain.engine.PlaybackMemoryLevel
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import com.example.familyphotoframe.ui.render.rememberPanelMotionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import com.example.familyphotoframe.ui.slideshow.transition.SlideshowTransitionRenderer
import com.example.familyphotoframe.ui.slideshow.transition.ResolvedTransition
import com.example.familyphotoframe.ui.slideshow.transition.TransitionEvent
import com.example.familyphotoframe.ui.slideshow.transition.TransitionFrameSampler
import com.example.familyphotoframe.ui.slideshow.transition.TransitionCancellationAction
import com.example.familyphotoframe.ui.slideshow.transition.TransitionCancellationPolicy
import com.example.familyphotoframe.ui.slideshow.transition.TransitionPerformanceController
import com.example.familyphotoframe.ui.slideshow.transition.TransitionPerformanceStateChange
import com.example.familyphotoframe.ui.slideshow.transition.TransitionSelector
import com.example.familyphotoframe.ui.slideshow.transition.TransitionState
import com.example.familyphotoframe.ui.slideshow.transition.TransitionTiming
import com.example.familyphotoframe.web.WebPreviewCaptureRequest
import com.example.familyphotoframe.web.WebPreviewFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * The playing surface: prepares the selected and next presentations, runs the explicit
 * transition between them, and reports every stage back through the callbacks. Only
 * small handles live in Compose state; decoded bitmaps stay in [PreparedSlideRegistry].
 */
@Composable
internal fun PlayingContent(
    state: SlideshowUiState,
    imageLoader: ImageLoader,
    resolveModel: PhotoModelResolver,
    loadCollageCandidates: suspend (DisplayPhoto, Int) -> List<DisplayPhoto>,
    probeRemoteDimensions: suspend (DisplayPhoto) -> Pair<Int, Int>?,
    onDecodeFailure: (DecodeFailure) -> Unit,
    onCollageCandidateFailure: (DecodeFailure) -> Unit,
    onCollageEvent: (
        event: String,
        anchorId: Long,
        photoIds: List<Long>,
        layout: String?,
        prepareMs: Long?,
        decodedBytes: Long?,
        reason: String?,
        details: Map<String, String>,
    ) -> Unit,
    onRecoverableOom: (DisplayPhoto, String) -> Unit,
    onTransitionEvent: (TransitionEvent) -> Unit,
    onPresentationStage: (Long, String, Boolean) -> Unit,
    onPreparationAttemptStage: (Long, Long, String, Boolean) -> Unit,
    onPreparationSubstage: (Long, Long, String) -> Unit,
    onPreparationTransferUpdate: (Long, Long, PreparationTransferUpdate) -> Unit,
    onPreparationCancelled: (Long, Long) -> Unit,
    onPreparationWatchdogTimeout: (Long, Long) -> Unit,
    onPrepared: suspend (Long, List<Long>, String?) -> Boolean,
    onRendered: (Long, List<Long>, List<String?>, String?) -> Unit,
    previewCaptureRequest: WebPreviewCaptureRequest?,
    onPreviewReady: (Long, WebPreviewFrame) -> Unit,
    onPreviewFailed: (Long, String) -> Unit,
    onVisiblePresentationChanged: (List<DisplayPhoto>) -> Unit,
    onBitmapInventory: (PreparedBitmapInventory, PlaybackMemoryState) -> Unit,
    bitmapLifecycleTracker: BitmapLifecycleTracker,
    nativeStageTracker: NativeAllocationStageTracker,
    onMemoryCleanup: (Int, Long, Long) -> Unit,
    /** Task §16: panel-motion detail for one three-photo frame. */
    onMotionDiagnostic: (List<Long>, String) -> Unit,
    manualNavigationActive: Boolean,
    memoryProtection: PlaybackMemoryState,
    hostActive: Boolean,
    hostGeneration: Long,
    hostPlaybackToken: () -> Long?,
    isHostPlaybackTokenCurrent: (Long) -> Boolean,
    targetW: Int,
    targetH: Int,
    localThumbnailCache: com.example.familyphotoframe.data.cache.LocalThumbnailCache? = null,
) {
    val context = LocalContext.current
    val selected = state.engine.current
    val next = state.engine.next
    // Corners, background, alignment and panel motion restyle tiles that are already decoded;
    // only the remaining collage options change what a preparation produces.
    val collagePreparationKey = state.portraitCollage.copy(
        alignment = com.example.familyphotoframe.data.settings.CollageAlignment.CENTER,
        background = com.example.familyphotoframe.data.settings.CollageBackground.APP_BACKGROUND,
        cornerRadiusDp = 0,
        animateThreePhotoFrames = true,
    )
    val softFocusNeeded = state.nativeMemoryHilMode == NativeMemoryHilMode.NORMAL &&
        memoryProtection.allowSoftFocus &&
        state.transitionSelectionMode == TransitionSelectionMode.FIXED &&
            state.transition == TransitionMode.SOFT_FOCUS_FADE &&
            !state.transitionReduceMotion

    val pendingDisposalsState = remember {
        mutableStateOf(PendingBitmapDisposals(count = 0, oldestStartedAtElapsedMs = 0L))
    }
    val reclaimer = remember(bitmapLifecycleTracker, pendingDisposalsState) {
        LegacyBitmapReclaimer(
            sdkInt = Build.VERSION.SDK_INT,
            handler = Handler(Looper.getMainLooper()),
            lifecycleTracker = bitmapLifecycleTracker,
            onPendingChanged = { pendingDisposalsState.value = it },
        )
    }
    // None of this state is keyed on targetW/targetH: a screen rotation changes those
    // values (portrait/landscape swap width and height) without recreating the Activity
    // (configChanges="orientation|screenSize|..." in the manifest), so keying on them
    // used to discard every prepared bitmap and in-flight transition on every rotation —
    // forcing a full re-decode, or for remote sources a full re-fetch, of the photo
    // already on screen (observed as a spurious TRANSITION_SELECTED reason=cold_start
    // immediately after a logged DISPLAY_CONFIGURATION_CHANGED). build() and the
    // preload effect below both read targetW/targetH directly and live, so a resize
    // still decodes new work at the correct size — it just no longer nukes what was
    // already prepared to do it.
    val registry = remember(memoryProtection.lowMemoryTier, reclaimer) {
        PreparedSlideRegistry(
            onRetired = reclaimer::retire,
            maxEntries = if (memoryProtection.lowMemoryTier) {
                PreparedSlideRegistry.LOW_MEMORY_MAX_ENTRIES
            } else {
                PreparedSlideRegistry.DEFAULT_MAX_ENTRIES
            },
        )
    }
    DisposableEffect(registry) {
        onDispose { registry.clear() }
    }
    val preparationMutex = remember { Mutex() }

    /**
     * The next presentation's preparation, owned outside its LaunchedEffect. When the dwell
     * ends before a slow remote preload finishes, the selected effect awaits this job instead
     * of cancelling it and starting over against the shorter selected deadline — which used to
     * discard partially transferred bytes and force a single-photo fallback.
     */
    val compositionScope = rememberCoroutineScope()
    val preloadScope = remember(compositionScope) {
        CoroutineScope(compositionScope.coroutineContext + SupervisorJob(compositionScope.coroutineContext[Job]))
    }
    val inflightPreload = remember { InflightPreload() }
    val latestSelectedId = rememberUpdatedState(selected?.id)
    val latestNextId = rememberUpdatedState(next?.id)
    val transitionProgress = remember { Animatable(1f) }
    val transitionSelector = remember {
        TransitionSelector(Random(SystemClock.elapsedRealtimeNanos()))
    }
    val performanceController = remember { TransitionPerformanceController() }
    val transitionGenerationCounter = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    /**
     * Photo ids that failed permanently (e.g. HEIC on Android 5.1 with no HEIF decoder).
     *
     * The engine's [reportCollageCandidateFailure] writes the suppression to the database
     * asynchronously via its own coroutine scope, so there is a race: the next
     * [collageCandidates] query can run before the write commits and would find the same
     * photo eligible again. This in-memory set closes that race by excluding permanently
     * failed photos immediately and synchronously, without waiting for the async write.
     * It is scoped to [PlayingContent]'s composition lifetime, so it is reset on activity
     * recreation — but permanent suppressions written to the database survive across
     * sessions and make the in-memory set redundant after the first scan cycle anyway.
     */
    val permanentlyFailedCandidates = remember { BoundedLongSet(capacity = 512) }

    // Snapshot state stores only tiny handles. Decoded bitmap graphs live in [registry].
    var candidateHandle by remember { mutableStateOf<PreparedSlideHandle?>(null) }
    var committedHandle by remember { mutableStateOf<PreparedSlideHandle?>(null) }
    var outgoingHandle by remember { mutableStateOf<PreparedSlideHandle?>(null) }
    var incomingHandle by remember { mutableStateOf<PreparedSlideHandle?>(null) }
    var manualCandidateHandle by remember { mutableStateOf<PreparedSlideHandle?>(null) }
    var transitionState by remember {
        mutableStateOf<TransitionState>(TransitionState.Idle)
    }
    var activeTransition by remember {
        mutableStateOf(ResolvedTransition(TransitionMode.CROSSFADE))
    }

    fun slide(handle: PreparedSlideHandle?): PreparedSlide? = registry.get(handle)

    fun protectedSlideHandles(): Set<PreparedSlideHandle> = setOfNotNull(
        candidateHandle,
        committedHandle,
        outgoingHandle,
        incomingHandle,
        manualCandidateHandle,
    )

    fun publishBitmapInventory() {
        onBitmapInventory(
            registry.inventory(
                renderedHandles = setOfNotNull(
                    committedHandle,
                    outgoingHandle,
                    incomingHandle,
                ),
                pendingDisposals = pendingDisposalsState.value,
            ),
            memoryProtection,
        )
    }

    // Delayed recycle completion is independent of the slide registry. Republish when
    // that queue changes so diagnostics do not retain a stale non-zero disposal count.
    LaunchedEffect(pendingDisposalsState.value) {
        publishBitmapInventory()
    }

    // Serialize current/next preparation. This prevents startup races from selecting the
    // same collage companions while still giving the full dwell interval to prepare next.
    suspend fun build(
        photo: DisplayPhoto,
        fastManual: Boolean = false,
        onPreparationSubstage: ((String) -> Unit)? = null,
        onTransferUpdate: ((PreparationTransferUpdate) -> Unit)? = null,
        selectedTransferDeadlineMonotonicMs: Long? = null,
    ): PrepareSlideResult {
        suspend fun prepare(): PrepareSlideResult {
        // Two independent scales: a static one that keeps a small-memory frame from ever
        // decoding more pixels than it can see the point of, and the guard's reactive one
        // that steps down further once pressure has already appeared.
        val baselineScale = when (state.decodeResolution) {
            DecodeResolution.FULL_PANEL -> 1f
            DecodeResolution.REDUCED ->
                DeviceMemoryTierPolicy.decodeScaleFor(DeviceMemoryTier.LOW, targetW, targetH)
            DecodeResolution.AUTO ->
                DeviceMemoryTierPolicy.decodeScaleFor(state.memoryTier, targetW, targetH)
        }
        val effectiveScale = baselineScale * memoryProtection.decodeScale
        val decodeW = (targetW * effectiveScale).roundToInt().coerceAtLeast(1)
        val decodeH = (targetH * effectiveScale).roundToInt().coerceAtLeast(1)
        val configuredMaxPhotos = state.portraitCollage.maxPhotosClamped
        val memoryMaxPhotos = if (state.nativeMemoryHilMode.forceSinglePhoto) {
            1
        } else {
            memoryProtection.maxCollagePhotos
        }
        val maxPhotos = minOf(configuredMaxPhotos, memoryMaxPhotos)
        return prepareSlide(
            context = context,
            photo = photo,
            imageLoader = imageLoader,
            resolveModel = resolveModel,
            loadCollageCandidates = loadCollageCandidates,
            probeRemoteDimensions = probeRemoteDimensions,
            // A memory-driven limit is passed through as the limit it is, not laundered
            // into mode OFF: prepareSlide's own guard branch then records why the frame
            // dropped to a single photo, which "the user turned collages off" would not.
            collageMode = if (fastManual) PortraitCollageMode.OFF else state.portraitCollage.mode,
            maxCollagePhotos = maxPhotos,
            configuredMaxCollagePhotos = configuredMaxPhotos,
            memoryMaxCollagePhotos = memoryMaxPhotos,
            portraitFallback = state.portraitCollage.fallback,
            collageGap = state.portraitCollage.gap,
            collageOrientationFilter = state.portraitCollage.orientationFilter,
            collageFillWithOtherOrientations = state.portraitCollage.fillWithOtherOrientations,
            collageLayoutPreference = state.portraitCollage.layoutPreference,
            collageCropsTiles = state.portraitCollage.scaleMode == CollageScaleMode.CROP,
            prepareSoftFocusBlur = softFocusNeeded && !fastManual,
            allowBlurredBackground = state.nativeMemoryHilMode == NativeMemoryHilMode.NORMAL &&
                memoryProtection.allowBlurredBackdrop && !fastManual,
            colorChoice = DecodeColorPolicy.choose(
                preference = state.decodeColorDepth,
                heapMaxBytes = Runtime.getRuntime().maxMemory(),
                level = memoryProtection.level,
                lowMemoryTier = memoryProtection.lowMemoryTier,
            ),
            excludedIds = buildSet {
                addAll(registry.photoIds())
                selected?.id?.let(::add)
                next?.id?.let(::add)
                addAll(permanentlyFailedCandidates)
            },
            targetW = decodeW,
            targetH = decodeH,
            localThumbnailCache = localThumbnailCache,
            localThumbnailCacheProtectedStableIds = setOfNotNull(selected?.stableId, next?.stableId),
            bitmapLifecycleTracker = bitmapLifecycleTracker,
            nativeStageTracker = nativeStageTracker,
            onRecoverableOom = { failure ->
                onRecoverableOom(photo, failure.reason ?: "slide_preparation_allocation")
            },
            onCollageCandidateFailure = { failure ->
                if (failure.permanent) permanentlyFailedCandidates += failure.photoId
                onCollageCandidateFailure(failure)
            },
            onCollageEvent = onCollageEvent,
            modelResolutionRequest = ModelResolutionRequest(
                priority = if (onTransferUpdate != null) {
                    ModelResolutionPriority.SELECTED_PRESENTATION
                } else {
                    ModelResolutionPriority.BACKGROUND_PRELOAD
                },
                selectedTransferDeadlineMonotonicMs = selectedTransferDeadlineMonotonicMs,
            ),
            onPreparationSubstage = { stage -> onPreparationSubstage?.invoke(stage) },
            onPreparationTransferUpdate = { update -> onTransferUpdate?.invoke(update) },
        )
        }

        // A manual skip has higher priority than speculative collage preload.
        // Use a single-anchor decode when no prepared target exists instead of
        // queueing behind remote collage probes and companion decodes.
        if (fastManual) {
            onPreparationSubstage?.invoke("PREPARATION_ENTERED")
            return prepare()
        }
        onPreparationSubstage?.invoke("PREPARATION_MUTEX_WAIT")
        return preparationMutex.withLock {
            onPreparationSubstage?.invoke("PREPARATION_ENTERED")
            prepare()
        }
    }

    // A complete presentation becomes a candidate, but does not replace the currently
    // visible presentation until the explicit transition coordinator commits it.
    LaunchedEffect(
        selected?.id,
        state.engine.selectionGeneration,
        targetW,
        targetH,
        collagePreparationKey,
        softFocusNeeded,
        state.nativeMemoryHilMode,
        memoryProtection.decisionVersion,
        hostActive,
        hostGeneration,
    ) {
        var uncommittedHandle: PreparedSlideHandle? = null
        val photo = selected ?: return@LaunchedEffect
        val selectionGeneration = state.engine.selectionGeneration
        val selectedTransferDeadlineMonotonicMs = presentationMonotonicNowMs() +
            MediaTransferPolicy.SELECTED_PRESENTATION_DEADLINE_MS
        try {
            val fastManual = manualNavigationActive && committedHandle != null
            val lifecycleToken = hostPlaybackToken() ?: return@LaunchedEffect
            if (!hostActive || !isHostPlaybackTokenCurrent(lifecycleToken)) return@LaunchedEffect
            if (!memoryProtection.allowSelectedDecode) return@LaunchedEffect
            onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARE_STARTED", true)
            if (incomingHandle == null) {
                transitionState = TransitionState.Preparing(
                    currentPresentationId = slide(committedHandle)?.anchor?.id,
                    requestedPresentationId = photo.id,
                )
            }
            fun reusable(handle: PreparedSlideHandle?): PreparedSlideHandle? = handle?.takeIf {
                it == committedHandle ||
                    (fastManual && slide(it) != null) ||
                    slide(it)?.let { prepared -> !softFocusNeeded || prepared.transitionBlurredBitmap != null } == true
            }
            var existingHandle = reusable(registry.latest(photo.id))
            if (existingHandle == null) {
                inflightPreload.pendingFor(photo.id)?.let { pending ->
                    onPreparationSubstage(photo.id, selectionGeneration, "PRELOAD_JOIN")
                    val remainingMs = (selectedTransferDeadlineMonotonicMs - presentationMonotonicNowMs())
                        .coerceAtLeast(0L)
                    val joined = withTimeoutOrNull(remainingMs) {
                        try {
                            pending.await()
                        } catch (retired: CancellationException) {
                            // Rethrows when this effect (or the timeout) is what was cancelled.
                            currentCoroutineContext().ensureActive()
                            null
                        }
                    }
                    if (joined == null) inflightPreload.cancel(photo.id) else inflightPreload.forget(photo.id, pending)
                    existingHandle = reusable(joined ?: registry.latest(photo.id))
                }
            }
            val handle = existingHandle ?: when (val result = build(
                photo,
                fastManual = fastManual,
                onPreparationSubstage = { stage ->
                    onPreparationSubstage(photo.id, selectionGeneration, stage)
                },
                onTransferUpdate = { update ->
                    onPreparationTransferUpdate(photo.id, selectionGeneration, update)
                },
                selectedTransferDeadlineMonotonicMs = selectedTransferDeadlineMonotonicMs,
            )) {
                is PrepareSlideResult.Ready -> {
                    // Some Android 5/network decode calls are not cooperatively cancellable.
                    // Revalidate after the blocking boundary before admitting their bitmap
                    // graph to Compose-visible state.
                    if (!currentCoroutineContext().isActive ||
                        !isHostPlaybackTokenCurrent(lifecycleToken)
                    ) {
                        registry.retireUnowned(result.slide)
                        onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARE_STALE", false)
                        publishBitmapInventory()
                        return@LaunchedEffect
                    }
                    registry.put(result.slide, protectedSlideHandles()).also {
                        uncommittedHandle = it
                    }
                }
                is PrepareSlideResult.Failed -> {
                    if (!currentCoroutineContext().isActive ||
                        !isHostPlaybackTokenCurrent(lifecycleToken)
                    ) {
                        onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARE_STALE", false)
                        return@LaunchedEffect
                    }
                    onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARE_FAILED", false)
                    onDecodeFailure(result.failure)
                    return@LaunchedEffect
                }
            }
            val preparedSlide = slide(handle) ?: run {
                onPreparationAttemptStage(
                    photo.id,
                    selectionGeneration,
                    "PREPARED_SLIDE_MISSING",
                    false,
                )
                return@LaunchedEffect
            }
            if (!currentCoroutineContext().isActive ||
                !isHostPlaybackTokenCurrent(lifecycleToken)
            ) {
                onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARE_STALE", false)
                return@LaunchedEffect
            }
            val preparedCommitted = onPrepared(
                preparedSlide.anchor.id,
                preparedSlide.photos.map { it.id },
                (preparedSlide as? PreparedSlide.Collage)?.layout?.name,
            )
            if (!currentCoroutineContext().isActive ||
                !isHostPlaybackTokenCurrent(lifecycleToken)
            ) {
                onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARE_STALE", false)
                return@LaunchedEffect
            }
            if (!preparedCommitted) {
                if (handle != committedHandle) registry.remove(handle)
                uncommittedHandle = null
                onPreparationAttemptStage(
                    photo.id,
                    selectionGeneration,
                    "PREPARED_COMMIT_REJECTED",
                    false,
                )
                publishBitmapInventory()
                return@LaunchedEffect
            }
            candidateHandle = handle
            manualCandidateHandle = if (fastManual) handle else null
            // The Compose-visible handle now owns this registry entry.
            uncommittedHandle = null
            onPreparationAttemptStage(photo.id, selectionGeneration, "PREPARED", true)
            publishBitmapInventory()
        } catch (cancelled: CancellationException) {
            // A configuration/dimension change can cancel this effect while the engine
            // still awaits the same selected photo.  Report the cancellation through its
            // dedicated recovery path so it advances immediately instead of waiting for
            // the 30-second render-ack fallback.  Stale cancellations are rejected by
            // the engine after a newer selection has replaced this photo.
            onPreparationCancelled(photo.id, selectionGeneration)
            throw cancelled
        } finally {
            // Cancellation can occur while onPrepared suspends. No snapshot handle owns a
            // freshly inserted slide at that point, so retire it rather than leak its
            // bitmap graph in the ordinary registry.
            uncommittedHandle?.let(registry::remove)
        }
    }

    // A selected slide must not wait for the engine's broader render-ack timeout merely
    // because preparation is wedged.  The engine verifies that this photo is still current
    // and still pre-render before advancing, so a late effect cannot skip a newer slide.
    LaunchedEffect(
        selected?.id,
        state.engine.selectionGeneration,
        hostActive,
        hostGeneration,
    ) {
        val photo = selected ?: return@LaunchedEffect
        val selectionGeneration = state.engine.selectionGeneration
        if (!hostActive || hostPlaybackToken() == null) return@LaunchedEffect
        delay(com.example.familyphotoframe.domain.engine.RenderAckTimeoutPolicy.PREPARATION_WATCHDOG_TIMEOUT_MS)
        onPreparationWatchdogTimeout(photo.id, selectionGeneration)
    }

    // Prepare the following presentation immediately and keep the committed slide visible
    // until all single/collage members have decoded successfully.  Key this work to the
    // selected anchor too: when a new current slide is chosen, an older speculative preload
    // is cancelled before it can keep the shared preparation mutex ahead of the visible work.
    LaunchedEffect(
        next?.id,
        selected?.id,
        targetW,
        targetH,
        collagePreparationKey,
        softFocusNeeded,
        state.nativeMemoryHilMode,
        memoryProtection.decisionVersion,
        manualNavigationActive,
        hostActive,
        hostGeneration,
    ) {
        val photo = next ?: return@LaunchedEffect
        val prewarmManual = manualNavigationActive && committedHandle != null
        val lifecycleToken = hostPlaybackToken()
        if (!hostActive || lifecycleToken == null ||
            !isHostPlaybackTokenCurrent(lifecycleToken) ||
                state.nativeMemoryHilMode.disablePreload || !memoryProtection.allowNextPreload
        ) {
            registry.latest(photo.id)?.takeIf { handle ->
                handle !in setOfNotNull(
                    candidateHandle,
                    committedHandle,
                    outgoingHandle,
                    incomingHandle,
                )
            }?.let(registry::remove)
            publishBitmapInventory()
            return@LaunchedEffect
        }
        // Skip rebuilding if a valid prepared slide already exists. The bitmap is owned
        // by the PreparedSlide, not held in Coil's in-memory cache, so it survives an
        // IMAGE_CACHE_CLEARED event — rebuilding would re-decode identical bytes for no gain
        // and would show up in diagnostics as a second COLLAGE_PRELOAD_STARTED for the same
        // anchor (observed as 304/544 wasted preloads in the §22.2 soak log).
        val existingHandle = registry.latest(photo.id)?.takeIf { handle ->
            slide(handle)?.let {
                prewarmManual || !softFocusNeeded || it.transitionBlurredBitmap != null
            } == true
        }
        if (existingHandle != null) return@LaunchedEffect
        val pending = inflightPreload.pendingFor(photo.id) ?: preloadScope.async {
            // The job admits its own result, so a selected effect that joins it and this
            // effect never both own one bitmap graph.
            when (val result = build(photo, fastManual = prewarmManual)) {
                is PrepareSlideResult.Ready -> {
                    if (!isActive || !isHostPlaybackTokenCurrent(lifecycleToken)) {
                        registry.retireUnowned(result.slide)
                        publishBitmapInventory()
                        null
                    } else {
                        registry.put(result.slide, protectedSlideHandles()).also {
                            publishBitmapInventory()
                        }
                    }
                }
                is PrepareSlideResult.Failed -> {
                    if (isActive && isHostPlaybackTokenCurrent(lifecycleToken) &&
                        result.failure.exceptionClass == "OutOfMemoryError"
                    ) {
                        onRecoverableOom(photo, "next_preload_allocation")
                    }
                    null
                }
            }
        }.also { inflightPreload.track(photo.id, it) }
        try {
            pending.await()
        } catch (cancelled: CancellationException) {
            // The shared job was retired elsewhere while this effect is still live.
            if (currentCoroutineContext().isActive) return@LaunchedEffect
            // Keep the work while this photo is still next (the effect merely restarted) or
            // has just been selected (the selected effect joins it); otherwise retire it.
            if (latestSelectedId.value != photo.id && latestNextId.value != photo.id) {
                inflightPreload.cancel(photo.id)
            }
            throw cancelled
        }
        inflightPreload.forget(photo.id, pending)
    }

    // Drop any next-slide allocation immediately when pressure changes the decision.
    // After an actual OOM, request one GC only after strong references have been pruned.
    LaunchedEffect(memoryProtection.decisionVersion, state.nativeMemoryHilMode) {
        if (!memoryProtection.allowNextPreload || state.nativeMemoryHilMode.disablePreload) {
            registry.retain(
                setOfNotNull(
                    candidateHandle,
                    committedHandle,
                    outgoingHandle,
                    incomingHandle,
                )
            )
            publishBitmapInventory()
        }
        if (memoryProtection.level == PlaybackMemoryLevel.CIRCUIT_OPEN) {
            val pendingBeforeGc = reclaimer.pendingBitmapCount()
            val runtime = Runtime.getRuntime()
            val beforeKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
            if (pendingBeforeGc > 0) delay(reclaimer.reclaimDelayMs + 50L)
            withContext(Dispatchers.Default) { Runtime.getRuntime().gc() }
            val afterKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
            onMemoryCleanup(pendingBeforeGc, beforeKb, afterKb)
        }
    }

    LaunchedEffect(
        candidateHandle,
        committedHandle,
        outgoingHandle,
        incomingHandle,
        memoryProtection.level,
    ) {
        publishBitmapInventory()
    }

    // Exactly one animation job exists because this LaunchedEffect owns the sole
    // Animatable. A new prepared target cancels the previous job; the NonCancellable
    // commit below snaps the active incoming slide to its valid final state first.
    LaunchedEffect(
        candidateHandle,
        selected?.id,
        state.engine.paused,
        state.transitionSelectionMode,
        state.transition,
        state.transitionReduceMotion,
        state.transitionDurationMs,
        state.nativeMemoryHilMode,
        hostActive,
        hostGeneration,
    ) {
        val lifecycleToken = hostPlaybackToken() ?: return@LaunchedEffect
        if (!hostActive || !isHostPlaybackTokenCurrent(lifecycleToken)) return@LaunchedEffect
        val targetHandle = candidateHandle ?: return@LaunchedEffect
        val target = slide(targetHandle) ?: return@LaunchedEffect
        if (state.engine.paused && committedHandle != null) {
            transitionState = TransitionState.Paused(slide(committedHandle)?.anchor?.id)
            return@LaunchedEffect
        }
        if (committedHandle == targetHandle) {
            transitionState = TransitionState.Committed(target.anchor.id)
            // The reselected candidate is already the visible slide (a candidate pool
            // with one eligible photo reselects the same handle on every advance) — no
            // transition runs, so acknowledge it when this effect is restarted. The
            // engine has the matching same-photo guard for the case where Compose sees
            // no changed effect key and therefore does not restart this block at all.
            onRendered(
                target.anchor.id,
                target.photos.map { it.id },
                target.dataSources,
                (target as? PreparedSlide.Collage)?.layout?.name,
            )
            return@LaunchedEffect
        }

        if (state.nativeMemoryHilMode == NativeMemoryHilMode.SINGLE_PHOTO_INSTANT) {
            val previousInstantHandle = committedHandle
            val operation = nativeStageTracker.start(NativeAllocationStageTracker.Stage.TRANSITION)
            try {
                transitionProgress.snapTo(1f)
                committedHandle = targetHandle
                outgoingHandle = null
                incomingHandle = null
                activeTransition = ResolvedTransition(
                    TransitionMode.CROSSFADE,
                    fallbackReason = "native_hil_instant",
                )
                transitionState = TransitionState.Committed(target.anchor.id)
                // Let Compose submit one frame so the measured stage includes any Skia upload
                // caused by this bitmap, while still retaining no outgoing transition frame.
                withFrameNanos { it }
                if (!isHostPlaybackTokenCurrent(lifecycleToken) ||
                    candidateHandle != targetHandle || selected?.id != target.anchor.id
                ) {
                    committedHandle = previousInstantHandle
                    transitionState = slide(previousInstantHandle)?.let {
                        TransitionState.Committed(it.anchor.id)
                    } ?: TransitionState.Idle
                    operation.finish(NativeAllocationStageTracker.Outcome.CANCELLED)
                    publishBitmapInventory()
                    return@LaunchedEffect
                }
                onRendered(
                    target.anchor.id,
                    target.photos.map { it.id },
                    target.dataSources,
                    null,
                )
                onPresentationStage(target.anchor.id, "NATIVE_HIL_INSTANT_COMMIT", false)
                if (manualCandidateHandle == targetHandle) manualCandidateHandle = null
                registry.retain(setOf(targetHandle))
                operation.finish(NativeAllocationStageTracker.Outcome.COMPLETED)
                publishBitmapInventory()
            } catch (cancelled: CancellationException) {
                if (committedHandle == targetHandle) {
                    committedHandle = previousInstantHandle
                    transitionState = slide(previousInstantHandle)?.let {
                        TransitionState.Committed(it.anchor.id)
                    } ?: TransitionState.Idle
                }
                operation.finish(NativeAllocationStageTracker.Outcome.CANCELLED)
                throw cancelled
            } catch (error: Throwable) {
                if (committedHandle == targetHandle) {
                    committedHandle = previousInstantHandle
                    transitionState = slide(previousInstantHandle)?.let {
                        TransitionState.Committed(it.anchor.id)
                    } ?: TransitionState.Idle
                }
                operation.finish(NativeAllocationStageTracker.Outcome.FAILED)
                throw error
            }
            return@LaunchedEffect
        }

        val previousHandle = committedHandle
        val previous = slide(previousHandle)
        val transitionGeneration = transitionGenerationCounter.incrementAndGet()
        val manualNavigation = targetHandle == manualCandidateHandle
        val supportedEffects = TransitionMode.selectableValues.toMutableSet().apply {
            if (previous?.transitionBlurredBitmap == null || target.transitionBlurredBitmap == null) {
                remove(TransitionMode.SOFT_FOCUS_FADE)
            }
        }
        val resolved = if (previous == null) {
            ResolvedTransition(TransitionMode.CROSSFADE, fallbackReason = "cold_start")
        } else if (manualNavigation) {
            ResolvedTransition(TransitionMode.CROSSFADE, fallbackReason = "manual_navigation")
        } else if (state.nativeMemoryHilMode == NativeMemoryHilMode.SINGLE_PHOTO_CROSSFADE) {
            ResolvedTransition(TransitionMode.CROSSFADE, fallbackReason = "native_hil_single_crossfade")
        } else {
            transitionSelector.select(
                mode = state.transitionSelectionMode,
                configuredEffect = state.transition,
                supportedEffects = supportedEffects,
                reducedMotion = state.transitionReduceMotion,
                forceCrossfade = performanceController.isLowPerformanceMode ||
                    memoryProtection.forceSimpleTransition,
            )
        }
        val durationMs = if (previous == null) {
            TransitionTiming.COLD_START_DURATION_MS
        } else if (manualNavigation) {
            120
        } else {
            TransitionTiming.resolvedDurationMs(state.transitionDurationMs, resolved.effect)
        }
        fun event(
            code: String,
            reason: String? = null,
            metrics: com.example.familyphotoframe.ui.slideshow.transition.TransitionFrameMetrics? = null,
            startLatencyMs: Long? = null,
            actualDurationMs: Long? = null,
        ) = TransitionEvent(
            code = code,
            configuredMode = state.transitionSelectionMode.storageValue,
            configuredEffect = state.transition.storageValue,
            resolvedEffect = resolved.effect.storageValue,
            outgoingId = previous?.anchor?.id,
            incomingId = target.anchor.id,
            durationMs = durationMs,
            actualDurationMs = actualDurationMs,
            direction = resolved.direction.name.lowercase(),
            reason = reason,
            fallbackUsed = resolved.fallbackUsed,
            frameCount = metrics?.frameCount,
            slowFrameCount = metrics?.slowFrameCount,
            maximumFrameMs = metrics?.maximumFrameMs,
            startLatencyMs = startLatencyMs,
            preparedSlideCount = registry.size,
            activeDecodedBytes = (previous?.decodedBytes ?: 0L) + target.decodedBytes,
            transitionGeneration = transitionGeneration,
            hostGeneration = hostGeneration,
            cancellationInitiator = if (code == "TRANSITION_CANCELLED") {
                when (reason) {
                    "host_stopped" -> "HOST_LIFECYCLE"
                    "superseded" -> "NEW_PRESENTATION"
                    "paused" -> "ENGINE_PAUSE"
                    "reconfigured" -> "CONFIGURATION_CHANGE"
                    else -> "UNKNOWN"
                }
            } else null,
        )

        val readyAtNs = SystemClock.elapsedRealtimeNanos()
        transitionState = TransitionState.Ready(
            outgoingPresentationId = previous?.anchor?.id,
            incomingPresentationId = target.anchor.id,
            effect = resolved.effect,
        )
        onTransitionEvent(event("TRANSITION_SELECTED", resolved.fallbackReason))
        if (previous != null && resolved.fallbackUsed) {
            onTransitionEvent(event("TRANSITION_FALLBACK", resolved.fallbackReason))
        }

        outgoingHandle = previousHandle
        incomingHandle = targetHandle
        activeTransition = resolved
        transitionProgress.snapTo(0f)
        transitionState = TransitionState.Animating(
            outgoingPresentationId = previous?.anchor?.id,
            incomingPresentationId = target.anchor.id,
            effect = resolved.effect,
            progress = 0f,
        )
        onTransitionEvent(event("TRANSITION_STARTED"))

        val stageOperation = nativeStageTracker.start(NativeAllocationStageTracker.Stage.TRANSITION)
        val frameSampler = TransitionFrameSampler()
        var firstFrameAtNs: Long? = null
        var completed = false
        var stageOutcome = NativeAllocationStageTracker.Outcome.CANCELLED
        val animationStartedAtNs = SystemClock.elapsedRealtimeNanos()
        try {
            val firstFrameNs = withFrameNanos { it }
            firstFrameAtNs = firstFrameNs
            frameSampler.record(firstFrameNs)
            transitionProgress.snapTo(0f)
            while (currentCoroutineContext().isActive) {
                val frameAtNs = withFrameNanos { it }
                frameSampler.record(frameAtNs)
                val progress = TransitionTiming.progressForElapsedNanos(
                    elapsedNanos = (frameAtNs - firstFrameNs).coerceAtLeast(0L),
                    durationMs = durationMs,
                )
                transitionProgress.snapTo(progress)
                if (progress >= 1f) break
            }
            completed = true
            stageOutcome = NativeAllocationStageTracker.Outcome.COMPLETED
        } catch (cancelled: CancellationException) {
            stageOutcome = NativeAllocationStageTracker.Outcome.CANCELLED
            throw cancelled
        } catch (error: Throwable) {
            stageOutcome = NativeAllocationStageTracker.Outcome.FAILED
            throw error
        } finally {
            val metrics = frameSampler.summary()
            val actualDurationMs =
                (SystemClock.elapsedRealtimeNanos() - animationStartedAtNs).coerceAtLeast(0L) / 1_000_000L
            val startLatencyMs = firstFrameAtNs?.let { first ->
                (first - readyAtNs).coerceAtLeast(0L) / 1_000_000L
            }
            try {
                withContext(NonCancellable) {
                // onStop cancels this effect, but the historical implementation committed
                // from NonCancellable anyway. Keep a prepared candidate for resume while
                // refusing to make it visible or report it rendered in an old generation.
                if (!isHostPlaybackTokenCurrent(lifecycleToken)) {
                    transitionProgress.snapTo(1f)
                    outgoingHandle = null
                    incomingHandle = null
                    transitionState = TransitionState.Ready(
                        outgoingPresentationId = previous?.anchor?.id,
                        incomingPresentationId = target.anchor.id,
                        effect = resolved.effect,
                    )
                    onTransitionEvent(
                        event(
                            "TRANSITION_CANCELLED",
                            "host_stopped",
                            metrics,
                            startLatencyMs,
                            actualDurationMs,
                        )
                    )
                    publishBitmapInventory()
                    return@withContext
                }
                val cancellation = TransitionCancellationPolicy.decide(
                    completed = completed,
                    targetStillDesired = candidateHandle == targetHandle &&
                        selected?.id == target.anchor.id,
                    paused = state.engine.paused,
                )
                if (cancellation.action == TransitionCancellationAction.KEEP_OUTGOING) {
                    // A newer candidate cancelled this animation. The historical path
                    // snapped the obsolete target visible from NonCancellable, producing
                    // two-frame "transitions" and briefly rendering stale selections.
                    // Keep the last committed frame and let the new candidate start its
                    // own transition instead.
                    transitionProgress.snapTo(1f)
                    outgoingHandle = null
                    incomingHandle = null
                    transitionState = if (previous != null) {
                        TransitionState.Committed(previous.anchor.id)
                    } else {
                        TransitionState.Preparing(
                            currentPresentationId = null,
                            requestedPresentationId = selected?.id ?: target.anchor.id,
                        )
                    }
                    onTransitionEvent(
                        event(
                            "TRANSITION_CANCELLED",
                            cancellation.reason,
                            metrics,
                            startLatencyMs,
                            actualDurationMs,
                        )
                    )
                    registry.retain(
                        setOfNotNull(
                            committedHandle,
                            candidateHandle,
                            if (memoryProtection.allowNextPreload &&
                                !state.nativeMemoryHilMode.disablePreload
                            ) {
                                next?.id?.let { registry.latest(it) }
                            } else null,
                        )
                    )
                    if (manualCandidateHandle == targetHandle) manualCandidateHandle = null
                    publishBitmapInventory()
                    return@withContext
                }

                transitionProgress.snapTo(1f)
                committedHandle = targetHandle
                outgoingHandle = null
                incomingHandle = null
                transitionState = TransitionState.Committed(target.anchor.id)

                // The engine's full dwell interval starts only after the incoming
                // presentation is completely visible, not while it is still animating.
                onRendered(
                    target.anchor.id,
                    target.photos.map { it.id },
                    target.dataSources,
                    (target as? PreparedSlide.Collage)?.layout?.name,
                )
                if (manualCandidateHandle == targetHandle) manualCandidateHandle = null
                onTransitionEvent(
                    event(
                        if (completed) "TRANSITION_COMPLETED" else "TRANSITION_CANCELLED",
                        if (completed) null else cancellation.reason,
                        metrics,
                        startLatencyMs,
                        actualDurationMs,
                    )
                )

                if (completed && previous != null) {
                    val decision = performanceController.record(metrics)
                    if (decision.warning) {
                        onTransitionEvent(event("TRANSITION_PERFORMANCE_WARNING", "frame_budget", metrics))
                    }
                    when (decision.stateChange) {
                        TransitionPerformanceStateChange.ENTERED_LOW_PERFORMANCE ->
                            onTransitionEvent(event("TRANSITION_LOW_PERFORMANCE_ENTERED", "three_of_five_slow", metrics))
                        TransitionPerformanceStateChange.EXITED_LOW_PERFORMANCE ->
                            onTransitionEvent(event("TRANSITION_LOW_PERFORMANCE_EXITED", "cooldown_complete", metrics))
                        TransitionPerformanceStateChange.NONE -> Unit
                    }
                }

                val keep = setOfNotNull(
                    committedHandle,
                    candidateHandle,
                    selected?.id?.let { registry.latest(it) },
                    if (memoryProtection.allowNextPreload &&
                        !state.nativeMemoryHilMode.disablePreload
                    ) {
                        next?.id?.let { registry.latest(it) }
                    } else null,
                )
                registry.retain(keep)
                publishBitmapInventory()
                }
            } finally {
                stageOperation.finish(stageOutcome)
            }
        }
    }

    // Compose one bounded JPEG only for an explicit browser request. This remains separate
    // from the transition critical path and never reopens SMB or source files.
    LaunchedEffect(
        previewCaptureRequest?.id,
        committedHandle,
        state.aspectMode,
        state.backgroundColorArgb,
        state.portraitCollage,
        targetW,
        targetH,
        memoryProtection.level,
    ) {
        val request = previewCaptureRequest ?: return@LaunchedEffect
        val committedSlide = slide(committedHandle)
        if (committedSlide == null) {
            onPreviewFailed(request.id, "NO_COMMITTED_FRAME")
            return@LaunchedEffect
        }
        if (!memoryProtection.allowWebPreview) {
            onPreviewFailed(request.id, "MEMORY_PROTECTED")
            return@LaunchedEffect
        }
        val frame = createWebPreviewFrame(
            slide = committedSlide,
            aspectMode = state.aspectMode,
            backgroundColorArgb = state.backgroundColorArgb,
            collageGap = state.portraitCollage.gap,
            collageScaleMode = state.portraitCollage.scaleMode,
            collageAlignment = state.portraitCollage.alignment,
            collageBackground = state.portraitCollage.background,
            collageCornerRadiusDp = state.portraitCollage.cornerRadiusDpClamped,
            transition = activeTransition.effect.storageValue,
            targetW = targetW,
            targetH = targetH,
            onOutOfMemory = {
                onRecoverableOom(committedSlide.anchor, "web_preview_allocation")
            },
        )
        if (frame != null) onPreviewReady(request.id, frame)
        else onPreviewFailed(request.id, "CAPTURE_FAILED")
    }

    LaunchedEffect(committedHandle) {
        onVisiblePresentationChanged(slide(committedHandle)?.photos.orEmpty())
    }

    Box(Modifier.fillMaxSize()) {
        val committed = slide(committedHandle)
        val outgoing = slide(outgoingHandle)
        val activeIncoming = slide(incomingHandle)
        // Held above both render call sites so a slide keeps its motion progress when it
        // becomes the outgoing frame, and pruned so nothing is retained for frames that
        // have left the screen (task §10, §14).
        val motionStore = rememberPanelMotionStore()
        val liveSlideIds = setOfNotNull(
            outgoing?.anchor?.id,
            activeIncoming?.anchor?.id,
            committed?.anchor?.id,
        )
        LaunchedEffect(liveSlideIds) { motionStore.retain(liveSlideIds) }

        if (activeIncoming != null && transitionState is TransitionState.Animating) {
            SlideshowTransitionRenderer(
                outgoing = outgoing,
                incoming = activeIncoming,
                transition = activeTransition,
                progress = transitionProgress.value,
                render = { slide ->
                    PreparedPhotoFrame(
                        prepared = slide,
                        state = state,
                        imageLoader = imageLoader,
                        allowDisplayMotion = false,
                        motionStore = motionStore,
                        memoryProtection = memoryProtection,
                        reclaimer = reclaimer,
                        bitmapLifecycleTracker = bitmapLifecycleTracker,
                        onRecoverableOom = onRecoverableOom,
                        onMotionDiagnostic = onMotionDiagnostic,
                    )
                },
                renderBlurred = { slide -> PreparedTransitionBlur(slide) },
            )
        } else {
            committed?.let {
                PreparedPhotoFrame(
                    prepared = it,
                    state = state,
                    imageLoader = imageLoader,
                    allowDisplayMotion = state.nativeMemoryHilMode == NativeMemoryHilMode.NORMAL,
                    motionStore = motionStore,
                    memoryProtection = memoryProtection,
                    reclaimer = reclaimer,
                    bitmapLifecycleTracker = bitmapLifecycleTracker,
                    onRecoverableOom = onRecoverableOom,
                    onMotionDiagnostic = onMotionDiagnostic,
                )
            }
        }

        val visiblePhoto = committed?.anchor
        val exif = if (visiblePhoto?.id == state.engine.current?.id) state.currentPhotoExif else null
        com.example.familyphotoframe.ui.overlay.OverlayLayer(
            overlays = state.overlays,
            folderName = visiblePhoto?.folderName.orEmpty(),
            weatherText = state.weatherText,
            photoDateTakenEpochMs = visiblePhoto?.dateTakenEpochMs ?: exif?.dateTakenEpochMs,
            caption = visiblePhoto?.caption ?: exif?.caption,
            gpsLat = visiblePhoto?.gpsLat ?: exif?.gpsLat,
            gpsLon = visiblePhoto?.gpsLon ?: exif?.gpsLon,
        )

        state.performanceReadout?.let { readout ->
            Text(
                text = readout,
                color = Color.Green,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
        }
    }
}

/** Single in-flight next-presentation preparation; main-thread confined like its callers. */
private class InflightPreload {
    private var photoId: Long? = null
    private var job: Deferred<PreparedSlideHandle?>? = null

    fun pendingFor(id: Long): Deferred<PreparedSlideHandle?>? =
        job?.takeIf { photoId == id && !it.isCancelled }

    fun track(id: Long, deferred: Deferred<PreparedSlideHandle?>) {
        if (photoId != id) job?.cancel()
        photoId = id
        job = deferred
    }

    fun cancel(id: Long) {
        if (photoId != id) return
        job?.cancel()
        photoId = null
        job = null
    }

    fun forget(id: Long, deferred: Deferred<PreparedSlideHandle?>) {
        if (photoId == id && job === deferred) {
            photoId = null
            job = null
        }
    }
}
