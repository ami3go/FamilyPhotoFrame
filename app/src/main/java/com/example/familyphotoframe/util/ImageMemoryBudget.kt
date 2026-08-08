package com.example.familyphotoframe.util

/** Heap-aware bounds for reusable decoded images on low-memory photo frames. */
object ImageMemoryBudget {
    const val MIN_BYTES: Long = 8L * 1024L * 1024L
    const val MAX_BYTES: Long = 16L * 1024L * 1024L
    private const val HEAP_PERCENT = 10L

    /**
     * Reserve ten percent of the Java heap for Coil, bounded to a useful but safe range.
     * The result is an [Int] because Coil 2.x accepts bytes through `maxSizeBytes(Int)`.
     */
    fun bytesForHeap(maxHeapBytes: Long): Int {
        val proportional = if (maxHeapBytes <= 0L) MIN_BYTES else maxHeapBytes / 100L * HEAP_PERCENT
        return proportional.coerceIn(MIN_BYTES, MAX_BYTES).toInt()
    }
}
