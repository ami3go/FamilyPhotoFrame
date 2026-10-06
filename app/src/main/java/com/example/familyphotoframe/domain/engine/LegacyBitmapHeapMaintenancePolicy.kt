package com.example.familyphotoframe.domain.engine

/** One narrowly-scoped maintenance action for old ART heaps. */
enum class LegacyBitmapHeapMaintenanceAction { NONE, REQUEST_GC }

/**
 * Primitive-only state for [LegacyBitmapHeapMaintenancePolicy].
 *
 * No bitmap or Android object is retained here. The policy observes the process-wide logical
 * ownership counters and asks old ART for collection only after a large amount of bitmap memory
 * has already been retired by the slideshow.
 */
data class LegacyBitmapHeapMaintenanceState(
    val startedAtElapsedMs: Long = -1L,
    val baselineHeapUsedBytes: Long = 0L,
    val releasedBytesAtLastRequest: Long = 0L,
    val lastRequestElapsedMs: Long = -1L,
    val consecutiveHighSamples: Int = 0,
)

data class LegacyBitmapHeapMaintenanceDecision(
    val state: LegacyBitmapHeapMaintenanceState,
    val action: LegacyBitmapHeapMaintenanceAction,
    val heapGrowthBytes: Long = 0L,
    val retiredBytesSinceRequest: Long = 0L,
)

/**
 * Bounded bitmap-churn maintenance for API 21-25 low-memory playback.
 *
 * On these releases decoded bitmap pixels live in the managed heap. The API-22 V80 evidence
 * shows correct logical ownership but several GiB of allocation/recycle traffic, followed by a
 * rising Java-heap floor and Dalvik PSS. A normal ownership release does not force old ART to
 * collect the now-unreachable wrappers/backing storage, so the process can retain a high heap
 * floor despite having only two or three live presentation bitmaps.
 *
 * This is deliberately not a periodic GC timer. Collection is eligible only after:
 *  - the two-hour warmup used by the authoritative accelerated gate;
 *  - at least 64 MiB of *released* bitmap traffic;
 *  - three consecutive one-minute samples at least 4 MiB above the warmup floor;
 *  - no pending legacy recycle and no active media transfer;
 *  - at least twenty minutes since the previous request.
 *
 * The V80 build-68 run confirmed that every tracked allocation mapped exactly to one rendered
 * photo member and every retired presentation balanced ownership, while Dalvik PSS correlated
 * 0.965 with cumulative decoded pixels. Collection every 64-80 minutes reclaimed 5-10 MiB each
 * time, but allowed old ART to recommit a higher managed-heap floor between requests.
 * Sixty-four MiB is roughly one third of the observed hourly churn; the twenty-minute floor
 * stays twice the qualification gate's ten-minute minimum while collecting before the next
 * allocator expansion.
 *
 * Modern Android, standard-memory devices, OOM recovery, and ordinary low heap occupancy are
 * untouched. The caller executes the request off the main looper and records durable evidence.
 */
object LegacyBitmapHeapMaintenancePolicy {
    const val MIN_SDK = 21
    const val MAX_SDK = 25
    const val BASELINE_START_MS = 30L * 60_000L
    const val WARMUP_MS = 2L * 60L * 60_000L
    const val MIN_RETIRED_BYTES = 64L * 1024L * 1024L
    const val MIN_HEAP_GROWTH_BYTES = 4L * 1024L * 1024L
    const val MIN_REQUEST_INTERVAL_MS = 20L * 60_000L
    const val REQUIRED_HIGH_SAMPLES = 3
    const val MAX_ACTIVE_BITMAP_COUNT = 4
    const val MAX_ACTIVE_BITMAP_BYTES = 4L * 1024L * 1024L
    const val MAX_CRITICAL_HEAP_OCCUPANCY_PERCENT = 70

    fun evaluate(
        previous: LegacyBitmapHeapMaintenanceState,
        sdkInt: Int,
        lowMemoryTier: Boolean,
        memoryLevel: PlaybackMemoryLevel,
        memoryPressureSource: PlaybackMemoryPressureSource,
        oomCount: Long,
        nowElapsedMs: Long,
        heapUsedBytes: Long,
        heapMaxBytes: Long,
        bitmapReleasedBytes: Long,
        activeBitmapCount: Int,
        activeBitmapBytes: Long,
        pendingDisposals: Int,
        activeMediaTransfers: Int,
    ): LegacyBitmapHeapMaintenanceDecision {
        if (sdkInt !in MIN_SDK..MAX_SDK || !lowMemoryTier || heapMaxBytes <= 0L) {
            return LegacyBitmapHeapMaintenanceDecision(
                LegacyBitmapHeapMaintenanceState(),
                LegacyBitmapHeapMaintenanceAction.NONE,
            )
        }

        val now = nowElapsedMs.coerceAtLeast(0L)
        val used = heapUsedBytes.coerceIn(0L, heapMaxBytes)
        val released = bitmapReleasedBytes.coerceAtLeast(0L)
        val timeReset = previous.startedAtElapsedMs < 0L || now < previous.startedAtElapsedMs
        val startedAt = if (timeReset) now else previous.startedAtElapsedMs
        val ageMs = (now - startedAt).coerceAtLeast(0L)

        var baseline = if (timeReset) 0L else previous.baselineHeapUsedBytes
        if (ageMs >= BASELINE_START_MS && (ageMs <= WARMUP_MS || baseline <= 0L)) {
            baseline = if (baseline <= 0L) used else minOf(baseline, used)
        } else if (ageMs > WARMUP_MS && baseline > 0L) {
            baseline = minOf(baseline, used)
        }

        val releasedAtLastRequest = if (timeReset) released else {
            previous.releasedBytesAtLastRequest.coerceAtMost(released)
        }
        val retiredSinceRequest = (released - releasedAtLastRequest).coerceAtLeast(0L)
        val heapGrowth = if (baseline > 0L) (used - baseline).coerceAtLeast(0L) else 0L
        val high = ageMs >= WARMUP_MS &&
            oomCount == 0L &&
            heapGrowth >= MIN_HEAP_GROWTH_BYTES &&
            retiredSinceRequest >= MIN_RETIRED_BYTES
        val highSamples = if (high) {
            (if (timeReset) 0 else previous.consecutiveHighSamples)
                .plus(1).coerceAtMost(REQUIRED_HIGH_SAMPLES)
        } else {
            0
        }
        // A native-growth latch can legitimately remain CRITICAL while old ART's managed
        // heap is still well below danger.  Build-85 V80 evidence showed exactly that:
        // native PSS had flattened, but the retained CRITICAL latch blocked every eligible
        // maintenance collection while Dalvik PSS grew.  Permit that one source only when
        // managed-heap occupancy is comfortably below 70%; OOM and ownership fences still
        // apply. Other CRITICAL causes remain ineligible.
        val heapOccupancyPercent = ((used * 100L) / heapMaxBytes).coerceIn(0L, 100L).toInt()
        val safeMemoryLevel = memoryLevel == PlaybackMemoryLevel.NORMAL ||
            memoryLevel == PlaybackMemoryLevel.GUARDED ||
            (memoryLevel == PlaybackMemoryLevel.CRITICAL &&
                memoryPressureSource == PlaybackMemoryPressureSource.NATIVE_PSS_GROWTH &&
                heapOccupancyPercent <= MAX_CRITICAL_HEAP_OCCUPANCY_PERCENT)
        val safeBoundary = safeMemoryLevel &&
            activeBitmapCount in 0..MAX_ACTIVE_BITMAP_COUNT &&
            activeBitmapBytes in 0L..MAX_ACTIVE_BITMAP_BYTES &&
            pendingDisposals == 0 &&
            activeMediaTransfers == 0
        val previousRequest = if (timeReset) -1L else previous.lastRequestElapsedMs
        val rateLimited = previousRequest >= 0L &&
            now - previousRequest < MIN_REQUEST_INTERVAL_MS
        val request = highSamples >= REQUIRED_HIGH_SAMPLES && safeBoundary && !rateLimited

        val state = LegacyBitmapHeapMaintenanceState(
            startedAtElapsedMs = startedAt,
            baselineHeapUsedBytes = baseline,
            releasedBytesAtLastRequest = if (request) released else releasedAtLastRequest,
            lastRequestElapsedMs = if (request) now else previousRequest,
            consecutiveHighSamples = if (request) 0 else highSamples,
        )
        return LegacyBitmapHeapMaintenanceDecision(
            state = state,
            action = if (request) {
                LegacyBitmapHeapMaintenanceAction.REQUEST_GC
            } else {
                LegacyBitmapHeapMaintenanceAction.NONE
            },
            heapGrowthBytes = heapGrowth,
            retiredBytesSinceRequest = retiredSinceRequest,
        )
    }
}
