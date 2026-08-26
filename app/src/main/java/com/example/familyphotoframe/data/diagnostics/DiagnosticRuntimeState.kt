package com.example.familyphotoframe.data.diagnostics

import java.util.concurrent.atomic.AtomicReference

/**
 * Lock-free, already-sanitized runtime state available to crash and ANR capture.
 *
 * Crash handling must not query Room, settings, Compose, image content, or a network
 * source. Normal runtime owners publish small categorical snapshots here instead.
 */
class DiagnosticRuntimeState {
    data class Memory(
        val heapUsedKb: Long = 0L,
        val heapMaxKb: Long = 0L,
        val nativeHeapKb: Long = 0L,
        val pssKb: Long = 0L,
        val imageCacheKb: Long = 0L,
        val sampledAtEpochMs: Long = 0L,
        val dalvikPssKb: Long = 0L,
        val nativePssKb: Long = 0L,
        val otherPssKb: Long = 0L,
        val systemAvailMemKb: Long = 0L,
        val systemThresholdKb: Long = 0L,
        val systemLowMemory: Boolean = false,
        /** `-1` means procfs was unavailable to this process. */
        val openFdCount: Int = -1,
        /** `-1` means procfs was unavailable to this process. */
        val threadCount: Int = -1,
        val activeSmbContexts: Int = 0,
        val oldestSmbContextAgeMs: Long = 0L,
        val peakSmbContexts: Int = 0,
        val smbContextsCreated: Long = 0L,
        val smbContextsClosed: Long = 0L,
        val smbContextTrackingSaturated: Boolean = false,
        val activeSmbStreams: Int = 0,
        val activeMediaTransfers: Int = 0,
        val oldestSmbStreamAgeMs: Long = 0L,
        val oldestMediaTransferAgeMs: Long = 0L,
        val peakSmbStreams: Int = 0,
        val smbStreamsOpened: Long = 0L,
        val smbStreamsClosed: Long = 0L,
        val smbTrackingSaturated: Boolean = false,
        val peakMediaTransfers: Int = 0,
        val mediaTransfersStarted: Long = 0L,
        val mediaTransfersFinished: Long = 0L,
        val mediaTrackingSaturated: Boolean = false,
        val bitmapTrackedAllocations: Long = 0L,
        val bitmapTrackedReleases: Long = 0L,
        val bitmapTrackedAllocatedBytes: Long = 0L,
        val bitmapTrackedReleasedBytes: Long = 0L,
        val bitmapTrackedActiveCount: Int = 0,
        val bitmapTrackedActiveBytes: Long = 0L,
        val bitmapTrackedPeakCount: Int = 0,
        val bitmapTrackedPeakBytes: Long = 0L,
        val bitmapDecodedAllocations: Long = 0L,
        val bitmapDecodedActiveCount: Int = 0,
        val bitmapDecodedActiveBytes: Long = 0L,
        val bitmapGeneratedAllocations: Long = 0L,
        val bitmapGeneratedActiveCount: Int = 0,
        val bitmapGeneratedActiveBytes: Long = 0L,
        val bitmapTemporaryAllocations: Long = 0L,
        val bitmapTemporaryActiveCount: Int = 0,
        val bitmapTemporaryActiveBytes: Long = 0L,
        val bitmapReleaseUnderflowCount: Long = 0L,
    )

    data class Playback(
        val surface: String = "LOADING",
        val engineState: String = "STARTING",
        val presentationToken: String = "",
        val sourceKind: String = "NONE",
        val layout: String = "NONE",
        val transitionCode: String = "NONE",
    )

    /** Bitmap ownership counters; never contains a Bitmap or a photo identifier. */
    data class BitmapInventory(
        val preparedSlideCount: Int = 0,
        val renderedSlideCount: Int = 0,
        val decodedBitmapCount: Int = 0,
        val appBitmapCount: Int = 0,
        val activeDecodedBytes: Long = 0L,
        val pendingDisposals: Int = 0,
        val oldestPendingDisposalStartedElapsedMs: Long = 0L,
        val memoryProtectionLevel: String = "NORMAL",
        val oomCount: Long = 0L,
        val updatedAtEpochMs: Long = 0L,
    )

    data class Snapshot(
        val memory: Memory = Memory(),
        val playback: Playback = Playback(),
        val bitmaps: BitmapInventory = BitmapInventory(),
    )

    private val state = AtomicReference(Snapshot())

    fun snapshot(): Snapshot = state.get()

    fun updateMemory(memory: Memory) {
        while (true) {
            val previous = state.get()
            if (state.compareAndSet(previous, previous.copy(memory = memory))) return
        }
    }

    fun updatePlayback(transform: (Playback) -> Playback) {
        while (true) {
            val previous = state.get()
            val updated = transform(previous.playback).bounded()
            if (state.compareAndSet(previous, previous.copy(playback = updated))) return
        }
    }

    fun updateBitmapInventory(inventory: BitmapInventory) {
        val bounded = inventory.copy(
            preparedSlideCount = inventory.preparedSlideCount.coerceIn(0, 16),
            renderedSlideCount = inventory.renderedSlideCount.coerceIn(0, 4),
            decodedBitmapCount = inventory.decodedBitmapCount.coerceIn(0, 64),
            appBitmapCount = inventory.appBitmapCount.coerceIn(0, 32),
            activeDecodedBytes = inventory.activeDecodedBytes.coerceAtLeast(0L),
            pendingDisposals = inventory.pendingDisposals.coerceIn(0, 64),
            oldestPendingDisposalStartedElapsedMs =
                inventory.oldestPendingDisposalStartedElapsedMs.coerceAtLeast(0L),
            memoryProtectionLevel = safeCode(inventory.memoryProtectionLevel, "UNKNOWN"),
            oomCount = inventory.oomCount.coerceAtLeast(0L),
        )
        while (true) {
            val previous = state.get()
            if (state.compareAndSet(previous, previous.copy(bitmaps = bounded))) return
        }
    }

    private fun Playback.bounded(): Playback = copy(
        surface = safeCode(surface, "UNKNOWN"),
        engineState = safeCode(engineState, "UNKNOWN"),
        presentationToken = DiagnosticPrivacyPolicy.protect("presentationToken", presentationToken).value,
        sourceKind = sourceKind.uppercase().takeIf { it in SAFE_SOURCE_KINDS } ?: "OTHER",
        layout = safeCode(layout, "UNKNOWN"),
        transitionCode = safeCode(transitionCode, "UNKNOWN"),
    )

    private fun safeCode(value: String, fallback: String): String =
        value.uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(48).ifEmpty { fallback }

    private companion object {
        val SAFE_SOURCE_KINDS = setOf(
            "NONE", "LOCAL_SAF", "FALLBACK", "SMB", "SYNOLOGY", "WEBDAV",
            "LOCAL_UPLOADS", "OTHER",
        )
    }
}
