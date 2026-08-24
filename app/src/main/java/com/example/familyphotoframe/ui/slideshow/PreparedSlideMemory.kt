package com.example.familyphotoframe.ui.slideshow

import android.graphics.Bitmap
import android.os.Handler
import java.util.Collections
import java.util.IdentityHashMap

/** Small Compose-safe identity; bitmap-bearing slides stay outside snapshot state. */
internal data class PreparedSlideHandle(val value: Long)

internal data class PreparedBitmapInventory(
    val preparedSlideCount: Int,
    val renderedSlideCount: Int,
    val decodedBitmapCount: Int,
    val appBitmapCount: Int,
    val activeDecodedBytes: Long,
    val pendingDisposals: Int,
)

/**
 * Ordinary bounded registry for decoded presentations.
 *
 * Compose observes only [PreparedSlideHandle] values. This prevents historical snapshot
 * records from retaining 5–20 MiB bitmap graphs after a transition on Android 5.x.
 */
internal class PreparedSlideRegistry(
    private val onRetired: (PreparedSlide) -> Unit,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    init {
        require(maxEntries >= 2) { "registry must hold at least outgoing and incoming slides" }
    }

    private val slides = LinkedHashMap<PreparedSlideHandle, PreparedSlide>()
    private val latestByAnchor = HashMap<Long, PreparedSlideHandle>()
    private var nextHandle = 1L

    val size: Int get() = slides.size

    fun get(handle: PreparedSlideHandle?): PreparedSlide? = handle?.let(slides::get)

    fun latest(
        anchorId: Long,
        predicate: (PreparedSlide) -> Boolean = { true },
    ): PreparedSlideHandle? = latestByAnchor[anchorId]?.takeIf { handle ->
        slides[handle]?.let(predicate) == true
    }

    fun put(
        slide: PreparedSlide,
        protectedHandles: Set<PreparedSlideHandle> = emptySet(),
    ): PreparedSlideHandle {
        val handle = PreparedSlideHandle(nextHandle++)
        slides[handle] = slide
        latestByAnchor[slide.anchor.id] = handle
        trimToCapacity(protectedHandles + handle)
        return handle
    }

    /**
     * Prefer retiring speculative/old entries. If a caller ever protects more handles
     * than the invariant permits, oldest-first retirement still enforces the hard heap
     * bound instead of trusting a comment while bitmap graphs grow without limit.
     */
    private fun trimToCapacity(protectedHandles: Set<PreparedSlideHandle>) {
        while (slides.size > maxEntries) {
            val victim = slides.keys.firstOrNull { it !in protectedHandles }
                ?: slides.keys.first()
            remove(victim)
        }
    }

    fun remove(handle: PreparedSlideHandle?) {
        if (handle == null) return
        val removed = slides.remove(handle) ?: return
        repairAnchorIndex(removed.anchor.id, handle)
        onRetired(removed)
    }

    /** Retire a decoded result that became stale before it was admitted to the registry. */
    fun retireUnowned(slide: PreparedSlide) {
        onRetired(slide)
    }

    fun retain(handles: Set<PreparedSlideHandle>) {
        slides.keys.filterNot(handles::contains).toList().forEach(::remove)
    }

    fun clear() {
        slides.keys.toList().forEach(::remove)
    }

    fun photoIds(): Set<Long> = buildSet {
        slides.values.forEach { slide -> slide.photos.forEach { add(it.id) } }
    }

    fun inventory(
        renderedHandles: Set<PreparedSlideHandle>,
        pendingDisposals: Int,
    ): PreparedBitmapInventory {
        val decoded = identityBitmapSet()
        val appOwned = identityBitmapSet()
        slides.values.forEach { slide ->
            decoded.addAll(slide.decodedBitmaps())
            slide.transitionBlurredBitmap?.let(appOwned::add)
        }
        val all = identityBitmapSet().apply {
            addAll(decoded)
            addAll(appOwned)
        }
        return PreparedBitmapInventory(
            preparedSlideCount = slides.size,
            renderedSlideCount = renderedHandles.count(slides::containsKey),
            decodedBitmapCount = decoded.size,
            // Every distinct bitmap in this registry is application-owned until its
            // slide is retired. The previous counter included only generated blur
            // bitmaps, so ordinary decoded presentations misleadingly reported zero.
            appBitmapCount = all.size,
            activeDecodedBytes = all.sumOf { it.safeAllocationBytesForInventory() },
            pendingDisposals = pendingDisposals.coerceAtLeast(0),
        )
    }

    private fun repairAnchorIndex(anchorId: Long, removed: PreparedSlideHandle) {
        if (latestByAnchor[anchorId] != removed) return
        val replacement = slides.entries.lastOrNull { it.value.anchor.id == anchorId }?.key
        if (replacement == null) latestByAnchor.remove(anchorId)
        else latestByAnchor[anchorId] = replacement
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 4
    }
}

/** Small thread-safe race bridge; permanent failures themselves are persisted in Room. */
internal class BoundedLongSet(private val capacity: Int) : AbstractMutableSet<Long>() {
    private val lock = Any()
    private val values = LinkedHashMap<Long, Unit>(capacity, 0.75f, true)

    init {
        require(capacity > 0)
    }

    override val size: Int get() = synchronized(lock) { values.size }

    override fun iterator(): MutableIterator<Long> {
        val snapshot = synchronized(lock) { values.keys.toMutableList() }
        val iterator = snapshot.iterator()
        var last: Long? = null
        return object : MutableIterator<Long> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): Long = iterator.next().also { last = it }
            override fun remove() {
                val value = last ?: throw IllegalStateException("next() has not been called")
                synchronized(lock) { values.remove(value) }
                iterator.remove()
                last = null
            }
        }
    }

    override fun add(element: Long): Boolean = synchronized(lock) {
        val added = values.put(element, Unit) == null
        while (values.size > capacity) values.remove(values.keys.first())
        added
    }

    override fun contains(element: Long): Boolean = synchronized(lock) {
        // get() refreshes access order so repeatedly observed failures remain resident.
        values[element] != null
    }

    override fun remove(element: Long): Boolean = synchronized(lock) {
        values.remove(element) != null
    }

    override fun clear() = synchronized(lock) { values.clear() }
}

/**
 * Delayed recycle for rendered software bitmaps on API 21–25 only.
 *
 * A retired slide is already absent from the registry and every render handle. The
 * grace period lets the old Canvas display list age out before pixel memory is released,
 * avoiding both stale-reference retention and recycled-bitmap draw crashes.
 */
internal class LegacyBitmapReclaimer(
    private val sdkInt: Int,
    private val handler: Handler,
    private val graceMs: Long = DEFAULT_GRACE_MS,
) {
    private data class Pending(val bitmaps: List<Bitmap>, val runnable: Runnable)

    private val lock = Any()
    private val pending = IdentityHashMap<Any, Pending>()

    val reclaimDelayMs: Long get() =
        if (sdkInt in LEGACY_MIN_SDK..LEGACY_MAX_SDK) graceMs.coerceAtLeast(0L) else 0L

    fun retire(slide: PreparedSlide) = schedule(slide, slide.allBitmaps())

    fun retireDisplayBitmap(bitmap: Bitmap) = schedule(bitmap, listOf(bitmap))

    fun pendingBitmapCount(): Int = synchronized(lock) {
        val unique = identityBitmapSet()
        pending.values.forEach { unique.addAll(it.bitmaps) }
        unique.size
    }

    private fun schedule(owner: Any, bitmaps: List<Bitmap>) {
        if (sdkInt !in LEGACY_MIN_SDK..LEGACY_MAX_SDK) return
        val unique = identityBitmapSet().apply {
            bitmaps.filterNot { it.isRecycled }.forEach(::add)
        }.toList()
        if (unique.isEmpty()) return

        lateinit var task: Runnable
        task = Runnable {
            val toRecycle = synchronized(lock) {
                val current = pending[owner]
                if (current?.runnable !== task) emptyList()
                else pending.remove(owner)?.bitmaps.orEmpty()
            }
            toRecycle.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        val accepted = synchronized(lock) {
            if (pending.containsKey(owner)) false
            else {
                pending[owner] = Pending(unique, task)
                true
            }
        }
        if (accepted && !handler.postDelayed(task, graceMs.coerceAtLeast(0L))) {
            // A shutting-down looper can reject the delayed task. Do not leave its
            // bitmap graph permanently retained in [pending] in that case.
            task.run()
        }
    }

    private companion object {
        const val LEGACY_MIN_SDK = 21
        const val LEGACY_MAX_SDK = 25
        const val DEFAULT_GRACE_MS = 1_000L
    }
}

internal fun PreparedSlide.decodedBitmaps(): List<Bitmap> = when (this) {
    is PreparedSlide.Single -> listOf(bitmap)
    is PreparedSlide.Collage -> tiles.map { it.bitmap }
}

internal fun PreparedSlide.allBitmaps(): List<Bitmap> = buildList {
    addAll(decodedBitmaps())
    transitionBlurredBitmap?.let(::add)
}.distinctByIdentity()

private fun identityBitmapSet(): MutableSet<Bitmap> =
    Collections.newSetFromMap(IdentityHashMap<Bitmap, Boolean>())

private fun List<Bitmap>.distinctByIdentity(): List<Bitmap> =
    identityBitmapSet().also { it.addAll(this) }.toList()

private fun Bitmap.safeAllocationBytesForInventory(): Long =
    runCatching { allocationByteCount.toLong() }.getOrElse { byteCount.toLong() }
