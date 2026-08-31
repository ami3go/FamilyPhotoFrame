package com.example.familyphotoframe.data.diagnostics

/**
 * Process-wide logical ownership counters for software bitmaps managed by the slideshow.
 *
 * The tracker deliberately stores only counts and byte totals, never Bitmap references.
 * A tracked allocation remains active from decode/generation until the slideshow retires
 * its final owner (or immediately recycles an uncommitted temporary result).
 */
class BitmapLifecycleTracker {
    enum class Kind { DECODED, GENERATED, TEMPORARY }

    data class Snapshot(
        val allocations: Long = 0L,
        val releases: Long = 0L,
        val allocatedBytes: Long = 0L,
        val releasedBytes: Long = 0L,
        val activeCount: Int = 0,
        val activeBytes: Long = 0L,
        val peakActiveCount: Int = 0,
        val peakActiveBytes: Long = 0L,
        val decodedAllocations: Long = 0L,
        val decodedActiveCount: Int = 0,
        val decodedActiveBytes: Long = 0L,
        val generatedAllocations: Long = 0L,
        val generatedActiveCount: Int = 0,
        val generatedActiveBytes: Long = 0L,
        val temporaryAllocations: Long = 0L,
        val temporaryActiveCount: Int = 0,
        val temporaryActiveBytes: Long = 0L,
        val releaseUnderflowCount: Long = 0L,
    )

    private data class Bucket(
        var allocations: Long = 0L,
        var releases: Long = 0L,
        var allocatedBytes: Long = 0L,
        var releasedBytes: Long = 0L,
        var activeCount: Int = 0,
        var activeBytes: Long = 0L,
    )

    private val lock = Any()
    private val decoded = Bucket()
    private val generated = Bucket()
    private val temporary = Bucket()
    private var peakActiveCount = 0
    private var peakActiveBytes = 0L
    private var releaseUnderflowCount = 0L

    fun recordAllocation(kind: Kind, byteCount: Long) = synchronized(lock) {
        val bytes = byteCount.coerceAtLeast(0L)
        val bucket = bucket(kind)
        bucket.allocations = saturatingIncrement(bucket.allocations)
        bucket.allocatedBytes = saturatingAdd(bucket.allocatedBytes, bytes)
        bucket.activeCount = if (bucket.activeCount == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            bucket.activeCount + 1
        }
        bucket.activeBytes = saturatingAdd(bucket.activeBytes, bytes)
        peakActiveCount = maxOf(peakActiveCount, activeCount())
        peakActiveBytes = maxOf(peakActiveBytes, activeBytes())
    }

    fun recordRelease(kind: Kind, byteCount: Long) = synchronized(lock) {
        val bytes = byteCount.coerceAtLeast(0L)
        val bucket = bucket(kind)
        if (bucket.activeCount == 0 || bytes > bucket.activeBytes) {
            releaseUnderflowCount = saturatingIncrement(releaseUnderflowCount)
        }
        bucket.releases = saturatingIncrement(bucket.releases)
        bucket.releasedBytes = saturatingAdd(bucket.releasedBytes, bytes)
        bucket.activeCount = (bucket.activeCount - 1).coerceAtLeast(0)
        bucket.activeBytes = (bucket.activeBytes - bytes).coerceAtLeast(0L)
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            allocations = saturatingSum(
                decoded.allocations,
                generated.allocations,
                temporary.allocations,
            ),
            releases = saturatingSum(decoded.releases, generated.releases, temporary.releases),
            allocatedBytes = saturatingSum(
                decoded.allocatedBytes,
                generated.allocatedBytes,
                temporary.allocatedBytes,
            ),
            releasedBytes = saturatingSum(
                decoded.releasedBytes,
                generated.releasedBytes,
                temporary.releasedBytes,
            ),
            activeCount = activeCount(),
            activeBytes = activeBytes(),
            peakActiveCount = peakActiveCount,
            peakActiveBytes = peakActiveBytes,
            decodedAllocations = decoded.allocations,
            decodedActiveCount = decoded.activeCount,
            decodedActiveBytes = decoded.activeBytes,
            generatedAllocations = generated.allocations,
            generatedActiveCount = generated.activeCount,
            generatedActiveBytes = generated.activeBytes,
            temporaryAllocations = temporary.allocations,
            temporaryActiveCount = temporary.activeCount,
            temporaryActiveBytes = temporary.activeBytes,
            releaseUnderflowCount = releaseUnderflowCount,
        )
    }

    private fun activeCount(): Int =
        (decoded.activeCount.toLong() + generated.activeCount + temporary.activeCount)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun activeBytes(): Long =
        saturatingSum(decoded.activeBytes, generated.activeBytes, temporary.activeBytes)

    private fun bucket(kind: Kind): Bucket = when (kind) {
        Kind.DECODED -> decoded
        Kind.GENERATED -> generated
        Kind.TEMPORARY -> temporary
    }

    private fun saturatingSum(first: Long, second: Long, third: Long): Long =
        saturatingAdd(saturatingAdd(first, second), third)

    private fun saturatingAdd(first: Long, second: Long): Long =
        if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

    private fun saturatingIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
}
