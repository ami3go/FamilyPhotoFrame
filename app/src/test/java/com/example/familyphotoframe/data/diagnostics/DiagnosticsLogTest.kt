package com.example.familyphotoframe.data.diagnostics

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsLogTest {
    @Test fun durableBundleStreamsQueuedEvents() {
        val root = Files.createTempDirectory("fpf-diagnostics").toFile()
        try {
            val log = DiagnosticsLog()
            log.attachSink(
                FileDiagnosticsSink(File(root, "events")),
                FileDiagnosticsSink(File(root, "slides")),
                "test-session",
            )
            log.log(DiagnosticsLog.Category.APP, "STREAM_TEST", "")

            val text = log.openDurableBundle().bufferedReader().use { it.readText() }
            assertTrue(text.contains("STREAM_TEST"))
            assertTrue(text.contains("test-session"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun boundedWriterRecordsQueueOverflow() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = Collections.synchronizedList(mutableListOf<String>())
        val root = Files.createTempDirectory("fpf-diagnostics-overflow").toFile()
        try {
            val log = DiagnosticsLog(writerQueueCapacity = 1) { _, line ->
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                written += line
            }
            log.attachSink(
                FileDiagnosticsSink(File(root, "events")),
                FileDiagnosticsSink(File(root, "slides")),
                "overflow-session",
            )
            log.log(DiagnosticsLog.Category.APP, "BLOCK_WRITER")
            assertTrue(started.await(2, TimeUnit.SECONDS))
            log.log(DiagnosticsLog.Category.APP, "FILL_QUEUE")
            log.log(DiagnosticsLog.Category.APP, "DROP_ONE")
            release.countDown()
            log.openDurableBundle().close() // wait for the blocked and queued writes
            log.log(DiagnosticsLog.Category.APP, "AFTER_OVERFLOW")
            log.openDurableBundle().close()

            assertTrue(written.any { it.contains("DIAGNOSTICS_QUEUE_OVERFLOW") })
        } finally {
            release.countDown()
            root.deleteRecursively()
        }
    }
}
