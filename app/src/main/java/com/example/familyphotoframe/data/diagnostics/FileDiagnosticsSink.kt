package com.example.familyphotoframe.data.diagnostics

import java.io.File
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.util.Collections

/**
 * Durable diagnostics: appends entries to a rotating JSONL file in app-private storage.
 *
 * The in-memory ring buffer in [DiagnosticsLog] is fine for "what just happened", but it
 * dies with the process. Every open item on the Phase 1 gate is the opposite of that: a
 * 24-hour NAS offline/online cycle, auto-start after reboot, heap growth over a rolling
 * 6-hour window, "no crash". Evidence for those has to outlive restarts, so it has to hit
 * disk.
 *
 * Deliberately plain `java.io` and no Android imports, so it can be type-checked and unit
 * tested offline like the rest of the domain layer; the caller supplies the directory
 * (`context.filesDir`).
 *
 * Concurrency: every public method is synchronized. Diagnostics are low-frequency
 * (slides, health changes, periodic samples), so a lock costs nothing measurable and is
 * far easier to reason about than a lock-free writer that could interleave half-lines.
 */
class FileDiagnosticsSink(
    private val dir: File,
    /** Rotate once the active file passes this size. */
    private val maxBytes: Long = 2L * 1024 * 1024,
    /**
     * Rotated generations to keep. Two plus the active file spans a multi-day soak at
     * observed volumes while staying bounded — this is a photo frame, possibly on a
     * device with very little free space, so unbounded logging is not an option.
     */
    private val keepGenerations: Int = 2,
) {
    data class Snapshot(
        val retainedBytes: Long,
        val retainedGenerations: Int,
        val rotations: Long,
        val lastSuccessfulWriteEpochMs: Long,
        val lastAppendErrorClass: String?,
    )

    private val lock = Any()
    private var rotations: Long = 0L

    @Volatile var lastSuccessfulWriteEpochMs: Long = 0L
        private set

    /** Set once a write fails, so a broken sink degrades quietly instead of spamming. */
    @Volatile var lastError: String? = null
        private set

    private val active: File get() = File(dir, ACTIVE_NAME)

    fun append(line: String) = synchronized(lock) {
        try {
            if (!dir.exists()) dir.mkdirs()
            val f = active
            if (f.exists() && f.length() >= maxBytes) rotate()
            // Append mode: an abrupt kill loses at most the final partial line, and JSONL
            // means every earlier line is still readable.
            f.appendText(line + "\n")
            lastSuccessfulWriteEpochMs = System.currentTimeMillis()
            lastError = null
        } catch (e: IOException) {
            lastError = e.javaClass.simpleName
            // The async writer owns failure isolation. Propagate after recording state so
            // it can emit one DIAGNOSTICS_SINK_FAILED marker and detect recovery.
            throw e
        } catch (e: SecurityException) {
            lastError = e.javaClass.simpleName
            throw e
        }
    }

    /** Open oldest-to-newest file descriptors without loading their contents into heap. */
    fun openRetainedStream(): InputStream = synchronized(lock) {
        val opened = ArrayList<InputStream>()
        try {
            for (i in keepGenerations downTo 1) {
                val f = File(dir, "$BASE.$i.jsonl")
                if (f.exists()) opened += FileInputStream(f)
            }
            if (active.exists()) opened += FileInputStream(active)
            if (opened.isEmpty()) ByteArrayInputStream(ByteArray(0))
            else SequenceInputStream(Collections.enumeration(opened))
        } catch (e: IOException) {
            opened.forEach { runCatching { it.close() } }
            lastError = e.javaClass.simpleName
            ByteArrayInputStream(ByteArray(0))
        } catch (e: SecurityException) {
            opened.forEach { runCatching { it.close() } }
            lastError = e.javaClass.simpleName
            ByteArrayInputStream(ByteArray(0))
        }
    }

    fun totalBytes(): Long = synchronized(lock) {
        totalBytesLocked()
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            retainedBytes = totalBytesLocked(),
            retainedGenerations = retainedFileCountLocked(),
            rotations = rotations,
            lastSuccessfulWriteEpochMs = lastSuccessfulWriteEpochMs,
            lastAppendErrorClass = lastError,
        )
    }

    private fun totalBytesLocked(): Long {
        var total = if (active.exists()) active.length() else 0L
        for (i in 1..keepGenerations) {
            val f = File(dir, "$BASE.$i.jsonl")
            if (f.exists()) total += f.length()
        }
        return total
    }

    private fun retainedFileCountLocked(): Int {
        var count = if (active.exists()) 1 else 0
        for (i in 1..keepGenerations) {
            if (File(dir, "$BASE.$i.jsonl").exists()) count++
        }
        return count
    }

    fun clear() = synchronized(lock) {
        active.delete()
        for (i in 1..keepGenerations) File(dir, "$BASE.$i.jsonl").delete()
        rotations = 0L
        lastSuccessfulWriteEpochMs = 0L
        lastError = null
        Unit
    }

    /** Shift generations up and start a new active file; drops the oldest. */
    private fun rotate() {
        rotations++
        File(dir, "$BASE.$keepGenerations.jsonl").delete()
        for (i in keepGenerations - 1 downTo 1) {
            val from = File(dir, "$BASE.$i.jsonl")
            if (from.exists()) from.renameTo(File(dir, "$BASE.${i + 1}.jsonl"))
        }
        active.renameTo(File(dir, "$BASE.1.jsonl"))
    }

    private companion object {
        const val BASE = "diagnostics"
        const val ACTIVE_NAME = "$BASE.jsonl"
    }
}
