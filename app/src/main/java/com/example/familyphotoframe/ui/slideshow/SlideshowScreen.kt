package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.example.familyphotoframe.R
import com.example.familyphotoframe.data.settings.AspectMode
import com.example.familyphotoframe.data.settings.CollageGap
import com.example.familyphotoframe.data.settings.CollageScaleMode
import com.example.familyphotoframe.data.settings.PortraitCollageMode
import com.example.familyphotoframe.data.settings.PortraitFallback
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.domain.engine.DecodeFailureStage
import com.example.familyphotoframe.data.settings.DecodeResolution
import com.example.familyphotoframe.domain.engine.DecodeColorPolicy
import com.example.familyphotoframe.domain.engine.DeviceMemoryTier
import com.example.familyphotoframe.domain.engine.DeviceMemoryTierPolicy
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.domain.engine.CollageLayout
import com.example.familyphotoframe.domain.engine.PhotoOrientation
import com.example.familyphotoframe.domain.engine.PlaybackMemoryState
import com.example.familyphotoframe.domain.engine.PlaybackMemoryLevel
import com.example.familyphotoframe.domain.engine.PortraitCollagePolicy
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import com.example.familyphotoframe.data.settings.TransitionMode
import com.example.familyphotoframe.data.settings.TransitionSelectionMode
import com.example.familyphotoframe.ui.render.PanelMotionProfile
import com.example.familyphotoframe.ui.render.PanelMotionStore
import com.example.familyphotoframe.ui.render.PortraitPanelMotion
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
import com.example.familyphotoframe.web.WebPreviewFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * The full-screen slideshow surface (spec §10). Crossfades between the current and
 * (preloaded) next photo, applies the chosen aspect mode, and overlays clock/date/
 * folder. Non-photo surfaces (first run, recovery, empty index) present clear,
 * D-pad-reachable actions (spec §8, frontend-design: empty states invite action).
 *
 * Only the current and next prepared presentations are retained (spec §10.2
 * memory rule). Single photos decode to display size; collage members decode
 * directly to their destination tile size before the transition begins.
 */
@Composable
fun SlideshowScreen(
    vm: SlideshowViewModel,
    imageLoader: ImageLoader,
    onOpenSettings: () -> Unit,
    onOpenPhotoSources: () -> Unit,
    modifier: Modifier = Modifier,
    localThumbnailCache: com.example.familyphotoframe.data.cache.LocalThumbnailCache? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val memoryProtection by vm.memoryProtection.collectAsStateWithLifecycle()
    val hostActive by vm.hostActive.collectAsStateWithLifecycle()
    val hostGeneration by vm.hostGeneration.collectAsStateWithLifecycle()
    val bg = Color(state.backgroundColorArgb.toInt())
    val controlsScope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsTimeout by remember { mutableStateOf<Job?>(null) }
    var curationMode by remember { mutableStateOf<CurationMode?>(null) }

    fun showControls() {
        controlsVisible = true
        controlsTimeout?.cancel()
        controlsTimeout = controlsScope.launch {
            delay(4_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, curationMode) {
        vm.setInteractionHold(controlsVisible || curationMode != null)
    }
    DisposableEffect(Unit) {
        onDispose {
            controlsTimeout?.cancel()
            vm.setInteractionHold(false)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        // Decode target = physical display size (spec §10.2). The preload and the
        // display request MUST use the same size, otherwise Coil derives different
        // memory-cache keys and the preloaded bitmap is silently never reused.
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val targetW = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        val targetH = with(density) { configuration.screenHeightDp.dp.roundToPx() }


        when (val surface = state.surface) {
            is Surface.Loading -> { /* black; first photo paints shortly */ }
            is Surface.FirstRun -> FirstRunContent(
                onChoose = vm::requestPickFolder,
                onRemote = onOpenPhotoSources,
                onSamples = vm::useSamples,
            )
            is Surface.Recovery -> MessageCard(
                message = surface.message,
                onChoose = vm::requestPickFolder,
                onRemote = onOpenPhotoSources,
                onSamples = vm::useSamples,
            )
            is Surface.EmptyIndex -> MessageCard(
                message = stringRes(R.string.msg_index_empty),
                onChoose = vm::requestPickFolder,
                onRemote = onOpenPhotoSources,
                onSamples = vm::useSamples,
            )
            is Surface.Playing -> PlayingContent(
                state = state,
                imageLoader = imageLoader,
                resolveModel = vm::resolveModel,
                loadCollageCandidates = vm::portraitCollageCandidates,
                probeRemoteDimensions = vm::probeRemoteCollageDimensions,
                onDecodeFailure = vm::onDecodeFailure,
                onCollageCandidateFailure = vm::onCollageCandidateFailure,
                onCollageEvent = vm::onCollageEvent,
                onRecoverableOom = vm::onRecoverablePresentationOom,
                onTransitionEvent = vm::onTransitionEvent,
                onPrepared = vm::onPrepared,
                onRendered = vm::onRendered,
                shouldGeneratePreview = vm::shouldGenerateWebPreview,
                onPreviewReady = vm::onWebPreviewReady,
                onVisiblePresentationChanged = vm::onVisiblePresentationChanged,
                onBitmapInventory = vm::onBitmapInventory,
                onMemoryCleanup = vm::onMemoryCleanup,
                onMotionDiagnostic = vm::logThreePhotoMotion,
                manualNavigationActive = controlsVisible,
                memoryProtection = memoryProtection,
                hostActive = hostActive,
                hostGeneration = hostGeneration,
                hostPlaybackToken = vm::hostPlaybackToken,
                isHostPlaybackTokenCurrent = vm::isHostPlaybackTokenCurrent,
                targetW = targetW,
                targetH = targetH,
                localThumbnailCache = localThumbnailCache,
            )
        }

        // Indexing progress (non-blocking; shown over whatever is on screen).
        state.indexingFound?.let { found ->
            StatusPill(text = stringRes(R.string.msg_indexing, found), align = Alignment.TopCenter)
        }

        if (state.engine.paused) {
            // Says how to undo it: the previous label just read "Paused", which left the
            // frame looking stuck with no visible way out.
            StatusPill(text = stringRes(R.string.status_paused), align = Alignment.TopEnd)
        }

        // Curation feedback (spec §9.4): a star confirms the photo is favourited without
        // needing the info panel open.
        if (state.engine.current?.isFavorite == true) {
            StatusPill(text = "\u2605", align = Alignment.TopStart)
        }

        // Says plainly why family photos are still playing with the NAS offline
        // (spec §9.3), rather than leaving it to look like nothing is wrong.
        if (state.stalePlayback) {
            StatusPill(text = stringRes(R.string.msg_stale_playback), align = Alignment.BottomStart)
        }

        if (state.showInfo) {
            InfoBanner(photo = state.engine.current)
            // Only meaningful in the cycle-based modes, which set a non-zero total.
            if (state.engine.cycleTotal > 0) {
                StatusPill(
                    text = stringRes(
                        R.string.status_cycle_progress,
                        state.engine.cycleShown,
                        state.engine.cycleTotal,
                    ),
                    align = Alignment.BottomEnd,
                )
            }
        }

        if (state.blackScreen) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        // Keep the full-screen gesture detector as a sibling behind the controls.
        // A parent detector participates in the same pointer sequence as child Buttons
        // and can swallow their click on older Compose/Android combinations. Keying on
        // controlsVisible also prevents the detector from retaining a stale captured
        // visibility value after the first tap opens the controls.
        if (state.surface is Surface.Playing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(state.blackScreen, controlsVisible) {
                        detectTapGestures(
                            onTap = {
                                if (state.blackScreen) vm.temporaryWake()
                                else if (controlsVisible) controlsVisible = false
                                else showControls()
                            },
                            onDoubleTap = {
                                if (!controlsVisible) vm.onTogglePause("double_tap")
                            },
                            onLongPress = {
                                controlsVisible = false
                                onOpenSettings()
                            },
                        )
                    },
            )
        }

        if (controlsVisible && state.surface is Surface.Playing) {
            TouchNavigationOverlay(
                photos = state.visiblePresentationPhotos.ifEmpty { listOfNotNull(state.engine.current) },
                onPrevious = { showControls(); vm.onPrevious() },
                onNext = { showControls(); vm.onNext() },
                onFavorite = { photos ->
                    if (photos.size == 1) {
                        vm.setFavoriteForPhotos(listOf(photos.first().id), !photos.first().isFavorite)
                        showControls()
                    } else curationMode = CurationMode.Favorite(photos)
                },
                onHide = { photos ->
                    if (photos.size == 1) {
                        controlsVisible = false
                        vm.hidePhotos(listOf(photos.first().id))
                    } else curationMode = CurationMode.Hide(photos)
                },
            )
        }

        if (state.undoHiddenPhotoIds.isNotEmpty()) {
            UndoHideBar(
                count = state.undoHiddenPhotoIds.size,
                onUndo = vm::undoLastHide,
            )
            LaunchedEffect(state.undoHiddenPhotoIds) {
                delay(8_000)
                vm.clearHideUndo()
            }
        }

        when (val mode = curationMode) {
            is CurationMode.Favorite -> FavoriteSelectionDialog(
                photos = mode.photos,
                onDismiss = { curationMode = null; showControls() },
                onApply = { changes ->
                    changes.groupBy({ it.second }, { it.first }).forEach { (favorite, ids) ->
                        vm.setFavoriteForPhotos(ids, favorite)
                    }
                    curationMode = null
                    showControls()
                },
            )
            is CurationMode.Hide -> HideSelectionDialog(
                photos = mode.photos,
                onDismiss = { curationMode = null; showControls() },
                onApply = { ids ->
                    curationMode = null
                    controlsVisible = false
                    vm.hidePhotos(ids)
                },
            )
            null -> Unit
        }

        state.transientNotice?.let { notice ->
            StatusPill(text = notice, align = Alignment.BottomCenter)
            LaunchedEffect(notice) {
                kotlinx.coroutines.delay(4000)
                vm.clearTransientNotice()
            }
        }
    }
}


@Composable
private fun PlayingContent(
    state: SlideshowUiState,
    imageLoader: ImageLoader,
    resolveModel: suspend (DisplayPhoto) -> PhotoModelResolution,
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
    onPrepared: suspend (Long, List<Long>, String?) -> Boolean,
    onRendered: (Long, List<Long>, List<String?>, String?) -> Unit,
    shouldGeneratePreview: () -> Boolean,
    onPreviewReady: (WebPreviewFrame) -> Unit,
    onVisiblePresentationChanged: (List<DisplayPhoto>) -> Unit,
    onBitmapInventory: (PreparedBitmapInventory, PlaybackMemoryState) -> Unit,
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
    val softFocusNeeded = memoryProtection.allowSoftFocus &&
        state.transitionSelectionMode == TransitionSelectionMode.FIXED &&
            state.transition == TransitionMode.SOFT_FOCUS_FADE &&
            !state.transitionReduceMotion

    val reclaimer = remember {
        LegacyBitmapReclaimer(
            sdkInt = Build.VERSION.SDK_INT,
            handler = Handler(Looper.getMainLooper()),
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
    val registry = remember {
        PreparedSlideRegistry(onRetired = reclaimer::retire)
    }
    DisposableEffect(registry) {
        onDispose { registry.clear() }
    }
    val preparationMutex = remember { Mutex() }
    val transitionProgress = remember { Animatable(1f) }
    val transitionSelector = remember {
        TransitionSelector(Random(SystemClock.elapsedRealtimeNanos()))
    }
    val performanceController = remember { TransitionPerformanceController() }

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
    val permanentlyFailedCandidates = remember { mutableSetOf<Long>() }

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

    fun publishBitmapInventory() {
        onBitmapInventory(
            registry.inventory(
                renderedHandles = setOfNotNull(
                    committedHandle,
                    outgoingHandle,
                    incomingHandle,
                ),
                pendingDisposals = reclaimer.pendingBitmapCount(),
            ),
            memoryProtection,
        )
    }

    // Serialize current/next preparation. This prevents startup races from selecting the
    // same collage companions while still giving the full dwell interval to prepare next.
    suspend fun build(photo: DisplayPhoto, fastManual: Boolean = false): PrepareSlideResult {
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
        val memoryMaxPhotos = memoryProtection.maxCollagePhotos
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
            prepareSoftFocusBlur = softFocusNeeded && !fastManual,
            allowBlurredBackground = memoryProtection.allowBlurredBackdrop && !fastManual,
            colorChoice = DecodeColorPolicy.choose(
                preference = state.decodeColorDepth,
                heapMaxBytes = Runtime.getRuntime().maxMemory(),
                level = memoryProtection.level,
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
            onRecoverableOom = { failure ->
                onRecoverableOom(photo, failure.reason ?: "slide_preparation_allocation")
            },
            onCollageCandidateFailure = { failure ->
                if (failure.permanent) permanentlyFailedCandidates += failure.photoId
                onCollageCandidateFailure(failure)
            },
            onCollageEvent = onCollageEvent,
        )
        }

        // A manual skip has higher priority than speculative collage preload.
        // Use a single-anchor decode when no prepared target exists instead of
        // queueing behind remote collage probes and companion decodes.
        return if (fastManual) prepare() else preparationMutex.withLock { prepare() }
    }

    // A complete presentation becomes a candidate, but does not replace the currently
    // visible presentation until the explicit transition coordinator commits it.
    LaunchedEffect(
        selected?.id,
        targetW,
        targetH,
        state.portraitCollage,
        softFocusNeeded,
        memoryProtection.decisionVersion,
        hostActive,
        hostGeneration,
    ) {
        var uncommittedHandle: PreparedSlideHandle? = null
        try {
            val photo = selected ?: return@LaunchedEffect
            val fastManual = manualNavigationActive && committedHandle != null
            val lifecycleToken = hostPlaybackToken() ?: return@LaunchedEffect
            if (!hostActive || !isHostPlaybackTokenCurrent(lifecycleToken)) return@LaunchedEffect
            if (!memoryProtection.allowSelectedDecode) return@LaunchedEffect
            if (incomingHandle == null) {
                transitionState = TransitionState.Preparing(
                    currentPresentationId = slide(committedHandle)?.anchor?.id,
                    requestedPresentationId = photo.id,
                )
            }
            val cachedHandle = registry.latest(photo.id)
            val existingHandle = cachedHandle?.takeIf { handle ->
                handle == committedHandle ||
                    (fastManual && slide(handle) != null) ||
                    slide(handle)?.let { !softFocusNeeded || it.transitionBlurredBitmap != null } == true
            }
            val handle = existingHandle ?: when (val result = build(photo, fastManual = fastManual)) {
                is PrepareSlideResult.Ready -> {
                    // Some Android 5/network decode calls are not cooperatively cancellable.
                    // Revalidate after the blocking boundary before admitting their bitmap
                    // graph to Compose-visible state.
                    if (!currentCoroutineContext().isActive ||
                        !isHostPlaybackTokenCurrent(lifecycleToken)
                    ) {
                        registry.retireUnowned(result.slide)
                        publishBitmapInventory()
                        return@LaunchedEffect
                    }
                    registry.put(result.slide).also {
                        uncommittedHandle = it
                    }
                }
                is PrepareSlideResult.Failed -> {
                    if (!currentCoroutineContext().isActive ||
                        !isHostPlaybackTokenCurrent(lifecycleToken)
                    ) return@LaunchedEffect
                    onDecodeFailure(result.failure)
                    return@LaunchedEffect
                }
            }
            val preparedSlide = slide(handle) ?: return@LaunchedEffect
            if (!currentCoroutineContext().isActive ||
                !isHostPlaybackTokenCurrent(lifecycleToken)
            ) return@LaunchedEffect
            val preparedCommitted = onPrepared(
                preparedSlide.anchor.id,
                preparedSlide.photos.map { it.id },
                (preparedSlide as? PreparedSlide.Collage)?.layout?.name,
            )
            if (!currentCoroutineContext().isActive ||
                !isHostPlaybackTokenCurrent(lifecycleToken)
            ) return@LaunchedEffect
            if (!preparedCommitted) {
                if (handle != committedHandle) registry.remove(handle)
                uncommittedHandle = null
                publishBitmapInventory()
                return@LaunchedEffect
            }
            candidateHandle = handle
            manualCandidateHandle = if (fastManual) handle else null
            // The Compose-visible handle now owns this registry entry.
            uncommittedHandle = null
            publishBitmapInventory()
        } finally {
            // Cancellation can occur while onPrepared suspends. No snapshot handle owns a
            // freshly inserted slide at that point, so retire it rather than leak its
            // bitmap graph in the ordinary registry.
            uncommittedHandle?.let(registry::remove)
        }
    }

    // Prepare the following presentation immediately and keep the committed slide visible
    // until all single/collage members have decoded successfully.
    LaunchedEffect(
        next?.id,
        targetW,
        targetH,
        state.portraitCollage,
        softFocusNeeded,
        memoryProtection.decisionVersion,
        manualNavigationActive,
        hostActive,
        hostGeneration,
    ) {
        val photo = next ?: return@LaunchedEffect
        val prewarmManual = manualNavigationActive && committedHandle != null
        val lifecycleToken = hostPlaybackToken()
        if (!hostActive || lifecycleToken == null ||
            !isHostPlaybackTokenCurrent(lifecycleToken) || !memoryProtection.allowNextPreload
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
        when (val result = build(photo, fastManual = prewarmManual)) {
            is PrepareSlideResult.Ready -> {
                if (!currentCoroutineContext().isActive ||
                    !isHostPlaybackTokenCurrent(lifecycleToken)
                ) {
                    registry.retireUnowned(result.slide)
                    publishBitmapInventory()
                    return@LaunchedEffect
                }
                registry.put(result.slide)
                publishBitmapInventory()
            }
            is PrepareSlideResult.Failed -> {
                if (!currentCoroutineContext().isActive ||
                    !isHostPlaybackTokenCurrent(lifecycleToken)
                ) return@LaunchedEffect
                if (result.failure.exceptionClass == "OutOfMemoryError") {
                    onRecoverableOom(photo, "next_preload_allocation")
                }
            }
        }
    }

    // Drop any next-slide allocation immediately when pressure changes the decision.
    // After an actual OOM, request one GC only after strong references have been pruned.
    LaunchedEffect(memoryProtection.decisionVersion) {
        if (!memoryProtection.allowNextPreload) {
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
            // transition runs, so this is the only place that can report it rendered.
            // Skipping this call left the engine's waitingForRender wait blocked with no
            // timeout, freezing playback until an external command (e.g. a keypress)
            // broke the deadlock by chance.
            onRendered(
                target.anchor.id,
                target.photos.map { it.id },
                target.dataSources,
                (target as? PreparedSlide.Collage)?.layout?.name,
            )
            return@LaunchedEffect
        }

        val previousHandle = committedHandle
        val previous = slide(previousHandle)
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

        val frameSampler = TransitionFrameSampler()
        var firstFrameAtNs: Long? = null
        var completed = false
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
        } finally {
            val metrics = frameSampler.summary()
            val actualDurationMs =
                (SystemClock.elapsedRealtimeNanos() - animationStartedAtNs).coerceAtLeast(0L) / 1_000_000L
            val startLatencyMs = firstFrameAtNs?.let { first ->
                (first - readyAtNs).coerceAtLeast(0L) / 1_000_000L
            }
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
                            if (memoryProtection.allowNextPreload) {
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
                    if (memoryProtection.allowNextPreload) {
                        next?.id?.let { registry.latest(it) }
                    } else null,
                )
                registry.retain(keep)
                publishBitmapInventory()
            }
        }
    }

    // Phase 3 web preview: compose one bounded JPEG from the already-decoded committed
    // presentation. This is deliberately separate from the transition critical path and
    // never reopens SMB or source files.
    LaunchedEffect(
        committedHandle,
        state.aspectMode,
        state.backgroundColorArgb,
        state.portraitCollage,
        targetW,
        targetH,
        memoryProtection.level,
    ) {
        val committedSlide = slide(committedHandle) ?: return@LaunchedEffect
        if (!memoryProtection.allowWebPreview || !shouldGeneratePreview()) return@LaunchedEffect
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
        if (frame != null) onPreviewReady(frame)
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
                    allowDisplayMotion = true,
                    motionStore = motionStore,
                    memoryProtection = memoryProtection,
                    reclaimer = reclaimer,
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

@Composable
internal fun PreparedCollage(
    prepared: PreparedSlide.Collage,
    gap: CollageGap,
    state: SlideshowUiState,
    allowDisplayMotion: Boolean,
    motionStore: PanelMotionStore,
    onMotionDiagnostic: (List<Long>, String) -> Unit,
) {
    val gapDp = when (gap) {
        CollageGap.NONE -> 0.dp
        CollageGap.SMALL -> 4.dp
        CollageGap.MEDIUM -> 8.dp
        CollageGap.LARGE -> 16.dp
    }
    val tileScale = state.portraitCollage.scaleMode.toContentScale()
    val tileAlignment = state.portraitCollage.alignment.toComposeAlignment()
    val tileBackground = state.portraitCollage.background.toComposeColor(state.backgroundColorArgb)
    val tileShape = RoundedCornerShape(state.portraitCollage.cornerRadiusDpClamped.dp)

    // Task §1: motion is defined for the three-equal-portrait-panel layout only.
    val threePanel = prepared.layout == CollageLayout.THREE_COLUMNS && prepared.tiles.size == 3
    val animationScale = rememberSystemAnimationScale()
    val profile = if (!threePanel || state.portraitCollage.scaleMode == CollageScaleMode.FIT) {
        PanelMotionProfile.OFF
    } else {
        PortraitPanelMotion.profileFor(state.portraitCollage.animateThreePhotoFrames, animationScale)
    }

    // Seeded on the anchor so a given frame always animates identically (task §7) and the
    // paths survive recomposition and activity recreation (task §11).
    val slideId = prepared.anchor.id
    val durationMillis = state.intervalSecondsForUi.coerceIn(3, 600) * 1000
    var fallbackReason: String? = null
    val entry = motionStore.entryFor(slideId, "$profile|$durationMillis") {
        when {
            !threePanel -> {
                fallbackReason = "not_three_panel_layout"
                null
            }
            state.portraitCollage.scaleMode == CollageScaleMode.FIT -> {
                fallbackReason = "fit_scale_mode"
                null
            }
            profile == PanelMotionProfile.OFF -> {
                fallbackReason = "motion_disabled"
                null
            }
            else -> runCatching { PortraitPanelMotion.pathsForFrame(slideId, durationMillis, profile) }
                .onFailure { fallbackReason = "path_generation_failed: ${it.javaClass.simpleName}" }
                .getOrNull()   // Task §15: a failed path falls back to a static frame.
        }
    }
    val paths = entry.paths

    // Task §16: one entry per frame describing panel assignment, presets and limits.
    // compareAndSet ensures this fires exactly once across all composition slots that
    // simultaneously render this slide (committed frame, outgoing frame in the transition
    // renderer, and any recomposition of the same slot). See PanelMotionStore.Entry.described.
    LaunchedEffect(slideId, paths) {
        if (entry.described.compareAndSet(false, true)) {
            onMotionDiagnostic(
                prepared.photos.map { it.id },
                if (paths == null) {
                    "static fallback: ${fallbackReason ?: "unknown"}"
                } else {
                    PortraitPanelMotion.describeFrame(paths)
                },
            )
        }
    }

    /**
     * Drive the motion only while this slide is the live frame (task §10 step 7-9).
     *
     * ## How this satisfies task §11's four manual-navigation requirements
     *
     * - **Cancel the active animation safely**: `animating` going false — paused, off
     *   screen, or `allowDisplayMotion=false` while a transition owns the frame — changes
     *   this effect's key, which cancels the running [Animatable.animateTo] coroutine.
     *   This is Compose's ordinary effect cancellation; nothing custom is needed.
     * - **Preserve the current transform until the transition begins**: cancellation only
     *   stops the *coroutine*. [entry]'s `progress` value lives in [PanelMotionStore], not
     *   in the coroutine, so it is untouched by the cancellation and the `graphicsLayer`
     *   below keeps reading whatever value it stopped at.
     * - **Release references to the outgoing frame**: handled separately by
     *   [PanelMotionStore.retain], called once per composition from the slide list that is
     *   actually on screen.
     * - **Start new motion only after the next frame is ready**: paths are built as soon
     *   as a slide is first composed, even while it is only a transition preview with
     *   `allowDisplayMotion=false` — but the coroutine that actually advances `progress`
     *   only runs once `animating` is true, which requires the slide to be the committed
     *   frame. Pre-building paths early is what keeps the first visible frame free of a
     *   generation stall; not animating until committed is what task §10 step 7 requires.
     *
     * ## Screen off, backgrounding, and activity recreation (task §11, §14)
     *
     * No extra handling is added for these: `setContent` installs a lifecycle-aware
     * `Recomposer` that cancels running effects — this one included — once the activity
     * drops below `STARTED`, and Android delivers no animation frame callbacks to a window
     * that is not visible regardless. Both stop CPU/GPU work without this code needing to
     * poll visibility itself. On resume the effect restarts and reads the same "remaining"
     * calculation below, so motion continues from where it paused rather than restarting.
     */
    val animating = allowDisplayMotion && paths != null && !state.engine.paused
    LaunchedEffect(slideId, animating, durationMillis) {
        if (animating) {
            onMotionDiagnostic(prepared.photos.map { it.id }, "animation start: slide=$slideId")
            val remaining = ((1f - entry.progress.value) * durationMillis).toInt().coerceAtLeast(0)
            try {
                entry.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remaining, easing = LinearEasing),
                )
                onMotionDiagnostic(prepared.photos.map { it.id }, "animation completion: slide=$slideId")
            } catch (e: CancellationException) {
                onMotionDiagnostic(prepared.photos.map { it.id }, "animation cancellation: slide=$slideId")
                throw e
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().background(tileBackground),
        horizontalArrangement = Arrangement.spacedBy(gapDp),
    ) {
        prepared.tiles.forEachIndexed { index, tile ->
            val imageBitmap = remember(tile.bitmap) { tile.bitmap.asImageBitmap() }
            val path = paths?.getOrNull(index)
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = tileScale,
                alignment = tileAlignment,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(tileBackground, tileShape)
                    .clip(tileShape)
                    // Clip to the panel so transformed pixels never bleed into a neighbour.
                    .clipToBounds()
                    .then(
                        if (path == null) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer {
                                // Deferred read: the transform updates on the render thread
                                // without recomposing or allocating per frame (task §14).
                                val frame = PortraitPanelMotion.frameAt(path, entry.progress.value)
                                scaleX = frame.scale
                                scaleY = frame.scale
                                translationX = frame.translateXFraction * size.width
                                translationY = frame.translateYFraction * size.height
                            }
                        }
                    ),
            )
        }
    }
}

/**
 * System animator duration scale (task §13), or -1 when it cannot be read.
 *
 * Read once per composition rather than observed: the frame lasts seconds and the user
 * changing this mid-slide does not warrant a listener.
 */
@Composable
private fun rememberSystemAnimationScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(-1f)
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, arg: Int): String = androidx.compose.ui.res.stringResource(id, arg)

@Composable
private fun stringRes(id: Int, a1: Int, a2: Int): String =
    androidx.compose.ui.res.stringResource(id, a1, a2)
