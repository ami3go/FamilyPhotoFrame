package com.example.familyphotoframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import com.example.familyphotoframe.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

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
    val webPreviewCaptureRequest by vm.webPreviewCaptureRequest.collectAsStateWithLifecycle()
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
    LaunchedEffect(webPreviewCaptureRequest?.id, state.surface) {
        val request = webPreviewCaptureRequest ?: return@LaunchedEffect
        if (state.surface != Surface.Playing) {
            vm.onWebPreviewFailed(request.id, "NO_COMMITTED_FRAME")
        }
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
                resolveModel = { photo, request, onPreparationSubstage, onTransferUpdate ->
                    vm.resolveModel(photo, request, onPreparationSubstage, onTransferUpdate)
                },
                loadCollageCandidates = vm::portraitCollageCandidates,
                probeRemoteDimensions = vm::probeRemoteCollageDimensions,
                onDecodeFailure = vm::onDecodeFailure,
                onCollageCandidateFailure = vm::onCollageCandidateFailure,
                onCollageEvent = vm::onCollageEvent,
                onRecoverableOom = vm::onRecoverablePresentationOom,
                onTransitionEvent = vm::onTransitionEvent,
                onPresentationStage = vm::onPresentationStage,
                onPreparationAttemptStage = vm::onPreparationAttemptStage,
                onPreparationSubstage = vm::onPreparationSubstage,
                onPreparationTransferUpdate = vm::onPreparationTransferUpdate,
                onPreparationCancelled = vm::onPreparationCancelled,
                onPreparationWatchdogTimeout = vm::onPreparationWatchdogTimeout,
                onPrepared = vm::onPrepared,
                onRendered = vm::onRendered,
                previewCaptureRequest = webPreviewCaptureRequest,
                onPreviewReady = vm::onWebPreviewReady,
                onPreviewFailed = vm::onWebPreviewFailed,
                onVisiblePresentationChanged = vm::onVisiblePresentationChanged,
                onBitmapInventory = vm::onBitmapInventory,
                bitmapLifecycleTracker = vm.bitmapLifecycleTracker,
                nativeStageTracker = vm.nativeAllocationStageTracker,
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
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, arg: Int): String = androidx.compose.ui.res.stringResource(id, arg)

@Composable
private fun stringRes(id: Int, a1: Int, a2: Int): String =
    androidx.compose.ui.res.stringResource(id, a1, a2)
