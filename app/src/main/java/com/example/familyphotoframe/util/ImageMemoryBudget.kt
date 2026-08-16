package com.example.familyphotoframe.util

/** Heap-aware bounds for reusable decoded images on low-memory photo frames. */
object ImageMemoryBudget {
    const val MIN_BYTES: Long = 8L * 1024L * 1024L
    const val MAX_BYTES: Long = 16L * 1024L * 1024L

    /**
     * Slide decodes deliberately bypass this cache (they own their bitmaps and would
     * otherwise be retained twice), so on a small heap most of the ceiling would serve
     * only settings thumbnails and folder previews. Those are small and re-decode
     * cheaply, which is a far better trade there than reserving megabytes for them.
     */
    const val LOW_TIER_MIN_BYTES: Long = 3L * 1024L * 1024L
    const val LOW_TIER_MAX_BYTES: Long = 6L * 1024L * 1024L
    private const val HEAP_PERCENT = 10L
    private const val LOW_TIER_HEAP_PERCENT = 5L

    /**
     * Reserve a slice of the Java heap for Coil, bounded to a useful but safe range.
     * The result is an [Int] because Coil 2.x accepts bytes through `maxSizeBytes(Int)`.
     */
    fun bytesForHeap(maxHeapBytes: Long, lowMemoryTier: Boolean = false): Int {
        val percent = if (lowMemoryTier) LOW_TIER_HEAP_PERCENT else HEAP_PERCENT
        val floor = if (lowMemoryTier) LOW_TIER_MIN_BYTES else MIN_BYTES
        val ceiling = if (lowMemoryTier) LOW_TIER_MAX_BYTES else MAX_BYTES
        val proportional = if (maxHeapBytes <= 0L) floor else maxHeapBytes / 100L * percent
        return proportional.coerceIn(floor, ceiling).toInt()
    }
}
