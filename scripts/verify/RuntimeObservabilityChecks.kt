import java.io.File
import java.util.concurrent.atomic.AtomicLong

fun runRuntimeObservabilityChecks() {
    println("-- runtime observability --")
    val clock = AtomicLong(1_000L)
    val resources = RuntimeResourceTracker(clock::get)
    val smbContext = resources.openSmbContext()
    val smbOne = resources.openSmbStream()
    val smbTwo = resources.openSmbStream()
    val transfer = resources.startMediaTransfer()
    clock.set(1_250L)
    val active = resources.snapshot()
    check("two active SMB streams", 2, active.activeSmbStreams)
    check("SMB peak retained", 2, active.peakSmbStreams)
    check("oldest SMB age", 250L, active.oldestSmbStreamAgeMs)
    check("one active SMB context", 1, active.activeSmbContexts)
    check("oldest SMB context age", 250L, active.oldestSmbContextAgeMs)
    check("one active media transfer", 1, active.activeMediaTransfers)
    smbOne.close(); smbOne.close(); smbTwo.close(); transfer.close(); smbContext.close(); smbContext.close()
    val closed = resources.snapshot()
    check("idempotent SMB close", 2L, closed.smbStreamsClosed)
    check("media transfer finished", 1L, closed.mediaTransfersFinished)
    check("idempotent SMB context close", 1L, closed.smbContextsClosed)

    val bitmaps = BitmapLifecycleTracker()
    bitmaps.recordAllocation(BitmapLifecycleTracker.Kind.DECODED, 100L)
    bitmaps.recordAllocation(BitmapLifecycleTracker.Kind.GENERATED, 40L)
    bitmaps.recordAllocation(BitmapLifecycleTracker.Kind.TEMPORARY, 10L)
    bitmaps.recordRelease(BitmapLifecycleTracker.Kind.TEMPORARY, 10L)
    val activeBitmaps = bitmaps.snapshot()
    check("bitmap active ownership count", 2, activeBitmaps.activeCount)
    check("bitmap active ownership bytes", 140L, activeBitmaps.activeBytes)
    check("bitmap peak count", 3, activeBitmaps.peakActiveCount)
    bitmaps.recordRelease(BitmapLifecycleTracker.Kind.DECODED, 100L)
    bitmaps.recordRelease(BitmapLifecycleTracker.Kind.GENERATED, 40L)
    check("bitmap ownership balances", 0, bitmaps.snapshot().activeCount)
    bitmaps.recordRelease(BitmapLifecycleTracker.Kind.GENERATED, 1L)
    check("bitmap release underflow visible", 1L, bitmaps.snapshot().releaseUnderflowCount)

    val boundedTracker = RuntimeResourceTracker { 100L }
    val boundedLeases = List(1_025) { boundedTracker.openSmbStream() }
    val saturated = boundedTracker.snapshot()
    check("bounded tracker retains exact active count", 1_025, saturated.activeSmbStreams)
    check("bounded tracker declares timestamp saturation", true, saturated.smbTrackingSaturated)
    boundedLeases.forEach(AutoCloseable::close)
    check("bounded tracker retains exact close count", 1_025L, boundedTracker.snapshot().smbStreamsClosed)

    class MemoryStorage : PersistentRuntimeBreadcrumbs.Storage {
        var value: PersistentRuntimeBreadcrumbs.Breadcrumb? = null
        var syncWrites = 0
        override fun read() = value
        override fun write(value: PersistentRuntimeBreadcrumbs.Breadcrumb, synchronous: Boolean): Boolean {
            this.value = value
            if (synchronous) syncWrites++
            return true
        }
    }
    val storage = MemoryStorage()
    val breadcrumbs = PersistentRuntimeBreadcrumbs(storage, { 5_000L }, { 900L })
    breadcrumbs.attachSession("session-a")
    val breadcrumb = breadcrumbs.record(
        "presentation", "prepare_started", true,
        "presentation_0123456789abcdef01234567", "smb",
    )
    check("breadcrumb stage sanitized", "PREPARE_STARTED", breadcrumb.stage)
    check("breadcrumb flush succeeds", true, breadcrumbs.flush())
    check("breadcrumb synchronous boundary", 1, storage.syncWrites)
    check("breadcrumb restored", breadcrumb, PersistentRuntimeBreadcrumbs(storage).persisted())

    val root = kotlin.io.path.createTempDirectory("fpf-procfs").toFile()
    try {
        val fd = File(root, "fd").apply { mkdirs() }
        val task = File(root, "task").apply { mkdirs() }
        repeat(4) { File(fd, it.toString()).createNewFile() }
        repeat(3) { File(task, it.toString()).createNewFile() }
        val process = ProcfsResourceSampler(fd, task).sample()
        check("procfs file descriptors", 4, process.openFileDescriptorCount)
        check("procfs threads", 3, process.threadCount)
    } finally {
        root.deleteRecursively()
    }

    val heapFields = mapOf(
        "dalvikPssKb" to "1", "nativePssKb" to "2", "otherPssKb" to "3",
        "openFdCount" to "4", "threadCount" to "5", "smbActiveStreams" to "1",
        "smbActiveContexts" to "1", "smbContextsCreated" to "1",
        "bitmapTrackedActiveCount" to "2", "bitmapTrackedActiveBytes" to "140",
        "oldestPendingDisposalAgeMs" to "0", "mediaActiveTransfers" to "1",
    )
    val sanitizedHeap = DiagnosticsJsonl.sanitizeForEvent(
        DiagnosticEventCatalog.require("HEAP_SAMPLE"), heapFields, "",
    )
    check("runtime fields survive schema", heapFields, sanitizedHeap.fields)

    val transitionFields = mapOf(
        "configuredMode" to "fixed", "configuredEffect" to "crossfade",
        "resolvedEffect" to "crossfade", "transitionGeneration" to "7",
        "hostGeneration" to "2", "slowFrameCount" to "1",
        "maximumFrameMs" to "24", "startLatencyMs" to "8",
        "cancellationInitiator" to "NEW_PRESENTATION",
    )
    val sanitizedTransition = DiagnosticsJsonl.sanitizeForEvent(
        DiagnosticEventCatalog.require("TRANSITION_COMPLETED"), transitionFields, "",
    )
    check("transition fields survive schema", transitionFields, sanitizedTransition.fields)
}
