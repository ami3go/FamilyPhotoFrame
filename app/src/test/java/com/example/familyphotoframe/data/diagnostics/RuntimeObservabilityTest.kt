package com.example.familyphotoframe.data.diagnostics

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeObservabilityTest {
    @Test fun resourceLeasesAreIdempotentAndExposeAgeAndPeak() {
        val clock = AtomicLong(100L)
        val tracker = RuntimeResourceTracker(clock::get)
        val context = tracker.openSmbContext()
        val first = tracker.openSmbStream()
        val second = tracker.openSmbStream()
        val transfer = tracker.startMediaTransfer()
        clock.set(250L)

        val active = tracker.snapshot()
        assertEquals(2, active.activeSmbStreams)
        assertEquals(2, active.peakSmbStreams)
        assertEquals(150L, active.oldestSmbStreamAgeMs)
        assertEquals(1, active.activeSmbContexts)
        assertEquals(150L, active.oldestSmbContextAgeMs)
        assertEquals(1, active.activeMediaTransfers)

        first.close()
        first.close()
        second.close()
        transfer.close()
        context.close()
        context.close()
        val closed = tracker.snapshot()
        assertEquals(0, closed.activeSmbStreams)
        assertEquals(2L, closed.smbStreamsClosed)
        assertEquals(1L, closed.mediaTransfersFinished)
        assertEquals(0, closed.activeSmbContexts)
        assertEquals(1L, closed.smbContextsCreated)
        assertEquals(1L, closed.smbContextsClosed)
    }

    @Test fun bitmapOwnershipBalancesByKindWithoutRetainingObjects() {
        val tracker = BitmapLifecycleTracker()
        tracker.recordAllocation(BitmapLifecycleTracker.Kind.DECODED, 100L)
        tracker.recordAllocation(BitmapLifecycleTracker.Kind.GENERATED, 40L)
        tracker.recordAllocation(BitmapLifecycleTracker.Kind.TEMPORARY, 10L)
        tracker.recordRelease(BitmapLifecycleTracker.Kind.TEMPORARY, 10L)

        val active = tracker.snapshot()
        assertEquals(3L, active.allocations)
        assertEquals(1L, active.releases)
        assertEquals(2, active.activeCount)
        assertEquals(140L, active.activeBytes)
        assertEquals(1, active.decodedActiveCount)
        assertEquals(1, active.generatedActiveCount)
        assertEquals(0, active.temporaryActiveCount)
        assertEquals(3, active.peakActiveCount)
        assertEquals(150L, active.peakActiveBytes)

        tracker.recordRelease(BitmapLifecycleTracker.Kind.DECODED, 100L)
        tracker.recordRelease(BitmapLifecycleTracker.Kind.GENERATED, 40L)
        val balanced = tracker.snapshot()
        assertEquals(0, balanced.activeCount)
        assertEquals(0L, balanced.activeBytes)
        assertEquals(0L, balanced.releaseUnderflowCount)

        tracker.recordRelease(BitmapLifecycleTracker.Kind.GENERATED, 1L)
        assertEquals(1L, tracker.snapshot().releaseUnderflowCount)
    }

    @Test fun resourceTimestampTrackingIsBoundedWithoutLosingExactCounts() {
        val tracker = RuntimeResourceTracker { 100L }
        val leases = List(1_025) { tracker.openSmbStream() }

        val saturated = tracker.snapshot()
        assertEquals(1_025, saturated.activeSmbStreams)
        assertEquals(1_025, saturated.peakSmbStreams)
        assertEquals(1_025L, saturated.smbStreamsOpened)
        assertTrue(saturated.smbTrackingSaturated)

        leases.forEach(AutoCloseable::close)
        val closed = tracker.snapshot()
        assertEquals(0, closed.activeSmbStreams)
        assertEquals(1_025L, closed.smbStreamsClosed)
    }

    @Test fun breadcrumbSurvivesAStorageRoundTrip() {
        class MemoryStorage : PersistentRuntimeBreadcrumbs.Storage {
            var value: PersistentRuntimeBreadcrumbs.Breadcrumb? = null
            var synchronousWrites = 0
            override fun read() = value
            override fun write(value: PersistentRuntimeBreadcrumbs.Breadcrumb, synchronous: Boolean): Boolean {
                this.value = value
                if (synchronous) synchronousWrites++
                return true
            }
        }

        val storage = MemoryStorage()
        val breadcrumbs = PersistentRuntimeBreadcrumbs(storage, { 1_000L }, { 200L })
        breadcrumbs.attachSession("session-1")
        val value = breadcrumbs.record(
            operation = "presentation",
            stage = "prepare_started",
            active = true,
            presentationToken = "presentation_0123456789abcdef01234567",
            sourceKind = "smb",
        )
        assertTrue(value.active)
        assertEquals("PREPARE_STARTED", value.stage)
        assertTrue(breadcrumbs.flush())
        assertEquals(1, storage.synchronousWrites)

        val recovered = PersistentRuntimeBreadcrumbs(storage).persisted()
        assertEquals(value, recovered)
        assertFalse(recovered == null)
    }

    @Test fun procfsSamplerCountsOnlyDirectoryEntries() {
        val root = Files.createTempDirectory("fpf-procfs").toFile()
        try {
            val fd = root.resolve("fd").apply { mkdirs() }
            val task = root.resolve("task").apply { mkdirs() }
            repeat(3) { fd.resolve(it.toString()).createNewFile() }
            repeat(2) { task.resolve(it.toString()).createNewFile() }
            val sample = ProcfsResourceSampler(fd, task).sample()
            assertEquals(3, sample.openFileDescriptorCount)
            assertEquals(2, sample.threadCount)
        } finally {
            root.deleteRecursively()
        }
    }
}
