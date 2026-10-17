package com.example.familyphotoframe.ui.slideshow.viewmodel

import android.os.SystemClock
import com.example.familyphotoframe.data.diagnostics.DiagnosticsLog
import com.example.familyphotoframe.data.diagnostics.diagnosticToken
import com.example.familyphotoframe.domain.engine.DecodeFailure
import com.example.familyphotoframe.domain.engine.DisplayPhoto
import com.example.familyphotoframe.ui.slideshow.PreparationTransferUpdate
import com.example.familyphotoframe.ui.slideshow.PreparedBitmapInventory
import com.example.familyphotoframe.ui.slideshow.SlideshowUiState
import com.example.familyphotoframe.ui.slideshow.Surface
import com.example.familyphotoframe.ui.slideshow.transition.TransitionEvent
import com.example.familyphotoframe.web.WebPreviewFrame

/**
 * Callbacks from the slideshow renderer: presentation and preparation stages, render
 * acknowledgements, transition and collage diagnostics, bitmap inventory, and recovery
 * from a decode OutOfMemoryError. Each forwards to the engine, the memory guard, the
 * runtime diagnostics state, or the diagnostics log.
 */
internal class PresentationReporter(host: SlideshowFeatureHost) {
    private val services = host.services
    private val _state = host.state
    private val engine = services.engine

    fun onCollageCandidateFailure(failure: DecodeFailure) {
        if (failure.exceptionClass == "OutOfMemoryError") {
            // Do not count a healthy candidate as failed or consume its shuffle
            // reservation merely because the process heap was exhausted.
            recoverFromDecodeOom(failure)
            return
        }
        engine.reportCollageCandidateFailure(failure)
    }

    fun recoverFromDecodeOom(failure: DecodeFailure) {
        if (failure.exceptionClass != "OutOfMemoryError") return
        recoverFromPresentationOom(failure.photoId, failure.reason ?: "decode_allocation")
    }

    fun onRecoverablePresentationOom(photo: DisplayPhoto, reason: String) {
        recoverFromPresentationOom(photo.id, reason)
    }

    private fun recoverFromPresentationOom(photoId: Long, trigger: String) {
        val previousProtection = services.playbackMemoryGuard.snapshot()
        val protection = services.playbackMemoryGuard.recordDecodeOom(SystemClock.elapsedRealtime())
        services.diagnosticRuntimeState.updateBitmapInventory(
            services.diagnosticRuntimeState.snapshot().bitmaps.copy(
                memoryProtectionLevel = protection.level.name,
                oomCount = protection.totalOomCount,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )
        val cache = services.imageLoader.memoryCache
        val before = cache?.size ?: 0
        cache?.clear()
        services.webServer.clearPreview()
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY,
            "DECODE_OOM_RECOVERY",
            "trigger" to trigger,
            "presentationToken" to diagnosticToken(photoId.toString(), "presentation"),
            "imageCacheBeforeKb" to (before / 1024L).toString(),
            "webPreviewCleared" to "true",
            "memoryProtectionLevel" to protection.level.name,
            "pressurePercent" to protection.pressurePercent.toString(),
            "circuitOpenMs" to protection.circuitRemainingMs(SystemClock.elapsedRealtime()).toString(),
            "preloadAllowed" to protection.allowNextPreload.toString(),
            "maxCollagePhotos" to protection.maxCollagePhotos.toString(),
            "targetScalePercent" to (protection.decodeScale * 100f).toInt().toString(),
            "oomCount" to protection.totalOomCount.toString(),
        )
        if (protection.level != previousProtection.level ||
            protection.decisionVersion != previousProtection.decisionVersion
        ) {
            services.diagnostics.log(
                DiagnosticsLog.Category.MEMORY,
                "MEMORY_PROTECTION_CHANGED",
                "trigger" to "decode_oom",
                "previousLevel" to previousProtection.level.name,
                "memoryProtectionLevel" to protection.level.name,
                "pressurePercent" to protection.pressurePercent.toString(),
                "preloadAllowed" to protection.allowNextPreload.toString(),
                "maxCollagePhotos" to protection.maxCollagePhotos.toString(),
                "targetScalePercent" to (protection.decodeScale * 100f).toInt().toString(),
                "oomCount" to protection.totalOomCount.toString(),
            )
        }
    }

    internal fun onBitmapInventory(
        inventory: PreparedBitmapInventory,
        protection: com.example.familyphotoframe.domain.engine.PlaybackMemoryState,
    ) {
        services.diagnosticRuntimeState.updateBitmapInventory(
            com.example.familyphotoframe.data.diagnostics.DiagnosticRuntimeState.BitmapInventory(
                preparedSlideCount = inventory.preparedSlideCount,
                renderedSlideCount = inventory.renderedSlideCount,
                decodedBitmapCount = inventory.decodedBitmapCount,
                appBitmapCount = inventory.appBitmapCount,
                activeDecodedBytes = inventory.activeDecodedBytes,
                pendingDisposals = inventory.pendingDisposals,
                oldestPendingDisposalStartedElapsedMs =
                    inventory.oldestPendingDisposalStartedElapsedMs,
                memoryProtectionLevel = protection.level.name,
                oomCount = protection.totalOomCount,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    fun onMemoryCleanup(pendingDisposals: Int, heapBeforeKb: Long, heapAfterKb: Long) {
        services.diagnostics.log(
            DiagnosticsLog.Category.MEMORY,
            "MEMORY_CLEANUP_REQUESTED",
            "trigger" to "decode_circuit_open",
            "pendingDisposals" to pendingDisposals.toString(),
            "gcRequested" to "true",
            "heapBeforeKb" to heapBeforeKb.toString(),
            "heapAfterKb" to heapAfterKb.toString(),
            "freedKb" to (heapBeforeKb - heapAfterKb).coerceAtLeast(0L).toString(),
            "memoryProtectionLevel" to services.playbackMemoryGuard.snapshot().level.name,
        )
    }
    suspend fun onPrepared(
        anchorId: Long,
        memberIds: List<Long>,
        layout: String?,
    ): Boolean {
        val committed = engine.commitPrepared(anchorId, memberIds, layout)
        onPresentationStage(
            anchorId,
            if (committed) "ENGINE_COMMITTED" else "ENGINE_COMMIT_REJECTED",
            committed,
        )
        return committed
    }

    fun onPresentationStage(anchorId: Long, stage: String, active: Boolean) {
        val photo = _state.value.engine.current?.takeIf { it.id == anchorId }
        engine.reportPresentationStage(anchorId, stage)
        // Keep normal stage transitions in the bounded in-memory trace.  Persisting every
        // intermediate stage rotated the bulk log during an otherwise healthy soak; timeout
        // events now carry the current stage plus the exact preparation substage instead.
        services.runtimeBreadcrumbs.record(
            operation = "PRESENTATION",
            stage = stage,
            active = active,
            presentationToken = diagnosticToken(anchorId.toString(), "presentation"),
            sourceKind = photo?.sourceId?.let(::diagnosticSourceKind) ?: "NONE",
        )
    }

    internal fun onPreparationAttemptStage(
        anchorId: Long,
        selectionGeneration: Long,
        stage: String,
        active: Boolean,
    ) {
        val photo = _state.value.engine.current?.takeIf { it.id == anchorId }
        engine.reportPresentationStage(anchorId, stage, selectionGeneration)
        services.runtimeBreadcrumbs.record(
            operation = "PRESENTATION",
            stage = stage,
            active = active,
            presentationToken = diagnosticToken(anchorId.toString(), "presentation"),
            sourceKind = photo?.sourceId?.let(::diagnosticSourceKind) ?: "NONE",
        )
    }

    fun onPreparationSubstage(anchorId: Long, selectionGeneration: Long, substage: String) {
        engine.reportPreparationSubstage(anchorId, selectionGeneration, substage)
    }

    /** Forward only the current selected presentation's bounded cache-transfer trace. */
    internal fun onPreparationTransferUpdate(
        anchorId: Long,
        selectionGeneration: Long,
        update: PreparationTransferUpdate,
    ) {
        engine.reportPreparationTransfer(
            anchorId = anchorId,
            expectedSelectionGeneration = selectionGeneration,
            state = update.state.name,
            copiedBytes = update.copiedBytes,
            expectedBytes = update.expectedBytes,
            deadlineMs = update.deadlineMs,
            streamCloseSucceeded = update.streamCloseSucceeded,
        )
    }

    /**
     * A selected preparation can be cancelled by a display configuration change while
     * the engine still waits for its acknowledgement.  This is a recoverable UI hand-off
     * abort, not a decode failure: keep the current file eligible and let the engine pick
     * the next presentation immediately.
     */
    fun onPreparationCancelled(anchorId: Long, selectionGeneration: Long) {
        val photo = _state.value.engine.current?.takeIf { it.id == anchorId }
        engine.reportPreparationCancelled(anchorId, selectionGeneration)
        services.runtimeBreadcrumbs.record(
            operation = "PRESENTATION",
            stage = "PREPARE_CANCELLED",
            active = false,
            presentationToken = diagnosticToken(anchorId.toString(), "presentation"),
            sourceKind = photo?.sourceId?.let(::diagnosticSourceKind) ?: "NONE",
        )
    }

    fun onPreparationWatchdogTimeout(anchorId: Long, selectionGeneration: Long) {
        engine.reportPreparationWatchdogTimeout(anchorId, selectionGeneration)
    }

    fun onRendered(
        anchorId: Long,
        memberIds: List<Long>,
        dataSources: List<String?>,
        layout: String?,
    ) {
        engine.reportRendered(anchorId, memberIds, dataSources, layout)
        services.diagnosticRuntimeState.updatePlayback { current ->
            current.copy(
                presentationToken = diagnosticToken(anchorId.toString(), "presentation"),
                layout = layout ?: if (memberIds.size <= 1) "SINGLE" else "COLLAGE_${memberIds.size}",
            )
        }
        onPresentationStage(anchorId, "RENDERED", false)
    }

    fun onWebPreviewReady(requestId: Long, frame: WebPreviewFrame) {
        services.webServer.publishPreview(requestId, frame)
    }

    fun onWebPreviewFailed(requestId: Long, reason: String) {
        services.webServer.failPreview(requestId, reason)
    }

    fun onTransitionEvent(event: TransitionEvent) {
        services.diagnosticRuntimeState.updatePlayback { current ->
            current.copy(transitionCode = event.resolvedEffect.ifBlank { event.configuredEffect })
        }
        services.diagnostics.log(
            DiagnosticsLog.Category.ENGINE, event.code,
            "configuredMode" to event.configuredMode,
            "configuredEffect" to event.configuredEffect,
            "resolvedEffect" to event.resolvedEffect,
            "outgoingPresentationToken" to event.outgoingId?.let {
                diagnosticToken(it.toString(), "presentation")
            }.orEmpty(),
            "incomingPresentationToken" to
                diagnosticToken(event.incomingId.toString(), "presentation"),
            "durationMs" to event.durationMs.toString(),
            "actualDurationMs" to event.actualDurationMs?.toString().orEmpty(),
            "direction" to event.direction,
            "reason" to event.reason.orEmpty(),
            "fallbackUsed" to event.fallbackUsed.toString(),
            "frameCount" to event.frameCount?.toString().orEmpty(),
            "slowFrameCount" to event.slowFrameCount?.toString().orEmpty(),
            "maximumFrameMs" to event.maximumFrameMs?.toString().orEmpty(),
            "startLatencyMs" to event.startLatencyMs?.toString().orEmpty(),
            "preparedSlideCount" to event.preparedSlideCount?.toString().orEmpty(),
            "activeDecodedBytes" to event.activeDecodedBytes?.toString().orEmpty(),
            "transitionGeneration" to event.transitionGeneration.toString(),
            "hostGeneration" to event.hostGeneration.toString(),
            "cancellationInitiator" to event.cancellationInitiator.orEmpty(),
        )
        when (event.code) {
            "TRANSITION_SELECTED", "TRANSITION_STARTED" ->
                onPresentationStage(event.incomingId, event.code, true)
            "TRANSITION_COMPLETED", "TRANSITION_CANCELLED" ->
                onPresentationStage(event.incomingId, event.code, false)
        }
    }

    fun publishDiagnosticPlayback(
        state: SlideshowUiState,
        layoutOverride: String? = null,
        transitionOverride: String? = null,
    ) {
        val currentPhoto = state.engine.current
        services.diagnosticRuntimeState.updatePlayback { previous ->
            previous.copy(
                surface = when (state.surface) {
                    Surface.Loading -> "LOADING"
                    Surface.FirstRun -> "FIRST_RUN"
                    is Surface.Recovery -> "RECOVERY"
                    Surface.EmptyIndex -> "EMPTY_INDEX"
                    Surface.Playing -> if (state.blackScreen) "BLACK_SCREEN" else "PLAYING"
                },
                engineState = state.engine.state.name,
                presentationToken = currentPhoto?.let {
                    diagnosticToken(it.stableId.ifBlank { it.id.toString() }, "presentation")
                }.orEmpty(),
                sourceKind = currentPhoto?.sourceId?.let(::diagnosticSourceKind) ?: "NONE",
                layout = layoutOverride ?: previous.layout,
                transitionCode = transitionOverride ?: previous.transitionCode,
            )
        }
    }

    fun onCollageEvent(
        event: String,
        anchorId: Long,
        photoIds: List<Long> = emptyList(),
        layout: String? = null,
        prepareMs: Long? = null,
        decodedBytes: Long? = null,
        reason: String? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        val fields = mapOf(
            "anchorToken" to diagnosticToken(anchorId.toString(), "photo"),
            "presentationToken" to diagnosticToken(photoIds.joinToString(","), "presentation"),
            "members" to photoIds.size.toString(),
            "layout" to layout.orEmpty(),
            "durationMs" to prepareMs?.toString().orEmpty(),
            "sizeBytes" to decodedBytes?.toString().orEmpty(),
            "reason" to reason.orEmpty(),
        ) + details
        services.diagnostics.log(DiagnosticsLog.Category.ENGINE, event, "", fields)
    }
}
