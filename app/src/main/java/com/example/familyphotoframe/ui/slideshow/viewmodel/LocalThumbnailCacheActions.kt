package com.example.familyphotoframe.ui.slideshow.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Settings, maintenance and eager rebuild of the on-device photo cache
 * (docs/FPF-FEAT-LOCAL-THUMBNAIL-CACHE-001.md). A rebuild yields to playback and pauses
 * whenever the memory guard stops allowing preloads.
 */
internal class LocalThumbnailCacheActions(host: SlideshowFeatureHost) {
    private val services = host.services
    private val appContext = host.appContext
    private val viewModelScope = host.viewModelScope
    private val _state = host.state
    private val memoryProtection = services.playbackMemoryGuard.state

    private var localThumbnailRebuildJob: Job? = null

    fun setLocalThumbnailCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            services.settings.update {
                it.copy(localThumbnailCache = it.localThumbnailCache.copy(enabled = enabled))
            }
        }
    }

    fun setLocalThumbnailCacheMaxGiB(gib: Int) {
        val bytes = gib.coerceAtLeast(1).toLong() * 1024L * 1024L * 1024L
        viewModelScope.launch {
            services.settings.update {
                it.copy(localThumbnailCache = it.localThumbnailCache.copy(maxBytes = bytes))
            }
        }
    }

    /** Populates the usage/effective-max display fields; call when the settings card opens. */
    fun refreshLocalThumbnailCacheInfo() {
        viewModelScope.launch {
            val usage = services.localThumbnailCache.currentSizeBytes()
            val requested = services.settings.settings.first().localThumbnailCache.maxBytes
            val effectiveMax = com.example.familyphotoframe.data.cache.LocalThumbnailCache
                .clampMaxBytes(requested, appContext, usage)
            _state.update {
                it.copy(
                    localThumbnailCacheUsageBytes = usage,
                    localThumbnailCacheEffectiveMaxBytes = effectiveMax,
                )
            }
        }
    }

    fun cleanLocalThumbnailCache() {
        localThumbnailRebuildJob?.cancel()
        viewModelScope.launch {
            services.localThumbnailCache.clear()
            refreshLocalThumbnailCacheInfo()
            _state.update { it.copy(transientNotice = "Local photo cache cleared") }
        }
    }

    /**
     * Clears the cache, then eagerly walks the local photo index and populates it —
     * unlike [cleanLocalThumbnailCache], which just clears and lets the cache repopulate
     * lazily as photos are shown again. Paged and yields between photos so it competes
     * as little as possible with live playback decode, and pauses entirely rather than
     * fights the existing memory-pressure circuit breaker for the same budget.
     */
    fun rebuildLocalThumbnailCache() {
        localThumbnailRebuildJob?.cancel()
        localThumbnailRebuildJob = viewModelScope.launch {
            services.localThumbnailCache.clear()
            services.localThumbnailCache.rebuildInProgress = true
            services.localThumbnailCache.rebuildCount = 0
            _state.update {
                it.copy(localThumbnailCacheRebuildInProgress = true, localThumbnailCacheRebuildCount = 0)
            }
            try {
                val metrics = appContext.resources.displayMetrics
                val targetW = metrics.widthPixels.coerceAtLeast(1)
                val targetH = metrics.heightPixels.coerceAtLeast(1)
                var offset = 0
                var populated = 0
                while (isActive) {
                    if (!memoryProtection.value.allowNextPreload) {
                        delay(2_000)
                        continue
                    }
                    val batch = services.photoDao.localThumbnailRebuildCandidates(limit = 25, offset = offset)
                    if (batch.isEmpty()) break
                    offset += batch.size
                    for (candidate in batch) {
                        ensureActive()
                        if (!memoryProtection.value.allowNextPreload) break
                        val protectedKeys = setOfNotNull(
                            _state.value.engine.current?.stableId,
                            _state.value.engine.next?.stableId,
                        )
                        val model: Any = if (candidate.openToken.startsWith("content://")) {
                            Uri.parse(candidate.openToken)
                        } else {
                            File(candidate.openToken)
                        }
                        val bitmap = decodeForThumbnailRebuild(model, targetW, targetH)
                        if (bitmap != null) {
                            try {
                                services.localThumbnailCache.put(
                                    candidate.stableId, targetW, targetH, bitmap, protectedKeys,
                                )
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                            populated++
                            services.localThumbnailCache.rebuildCount = populated
                            _state.update { it.copy(localThumbnailCacheRebuildCount = populated) }
                        }
                        yield()
                    }
                }
            } finally {
                services.localThumbnailCache.rebuildInProgress = false
                _state.update { it.copy(localThumbnailCacheRebuildInProgress = false) }
                refreshLocalThumbnailCacheInfo()
            }
        }
    }

    private suspend fun decodeForThumbnailRebuild(model: Any, targetW: Int, targetH: Int): Bitmap? = try {
        val request = coil.request.ImageRequest.Builder(appContext)
            .data(model)
            .size(targetW, targetH)
            .allowHardware(false)
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
            .build()
        when (val result = services.imageLoader.execute(request)) {
            is coil.request.SuccessResult ->
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            else -> null
        }
    } catch (c: CancellationException) {
        throw c
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}
